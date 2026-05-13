package com.nendo.argosy.ui.screens.settings.delegates

import android.util.Log
import com.nendo.argosy.data.preferences.EmulatorDisplayTarget
import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.emulator.EmulatorDownloadManager
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.EmulatorUpdateManager
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.data.remote.github.EmulatorUpdateRepository
import com.nendo.argosy.data.remote.github.FetchReleaseResult
import com.nendo.argosy.data.local.entity.EmulatorUpdateEntity
import com.nendo.argosy.data.local.entity.EmulatorSaveConfigEntity
import com.nendo.argosy.data.repository.CoreVersionRepository
import com.nendo.argosy.data.repository.EmulatorConfigRepository
import com.nendo.argosy.data.repository.EmulatorSaveConfigRepository
import com.nendo.argosy.domain.usecase.game.ConfigureEmulatorUseCase
import com.nendo.argosy.libretro.LibretroCoreManager
import com.nendo.argosy.libretro.LibretroCoreRegistry
import kotlinx.coroutines.flow.Flow
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.core.emulator.EmulatorDownloadState
import com.nendo.argosy.ui.screens.settings.EmulatorUpdateModal
import com.nendo.argosy.data.sync.platform.PlatformSaveHandlerRegistry
import com.nendo.argosy.ui.screens.settings.LaunchArgsModalState
import com.nendo.argosy.ui.screens.settings.launchArgsModalRows
import com.nendo.argosy.ui.screens.settings.UpdateModalState
import com.nendo.argosy.ui.screens.settings.EmulatorPickerInfo
import com.nendo.argosy.ui.screens.settings.EmulatorState
import com.nendo.argosy.ui.screens.settings.EmulatorUpdateInfo
import com.nendo.argosy.ui.screens.settings.MemcardPickerInfo
import com.nendo.argosy.ui.screens.settings.PlatformEmulatorConfig
import com.nendo.argosy.ui.screens.settings.SavePathModalInfo
import com.nendo.argosy.ui.screens.settings.VariantOption
import com.nendo.argosy.ui.screens.settings.VariantPickerInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class BuiltinNavigationTarget {
    VIDEO_SETTINGS,
    CONTROLS_SETTINGS,
    CORE_MANAGEMENT,
    CORE_OPTIONS
}

class EmulatorSettingsDelegate @Inject constructor(
    private val emulatorDetector: EmulatorDetector,
    private val configureEmulatorUseCase: ConfigureEmulatorUseCase,
    private val soundManager: SoundFeedbackManager,
    private val emulatorSaveConfigRepository: EmulatorSaveConfigRepository,
    private val emulatorConfigRepo: EmulatorConfigRepository,
    private val coreManager: LibretroCoreManager,
    private val coreVersionRepository: CoreVersionRepository,
    private val emulatorUpdateManager: EmulatorUpdateManager,
    private val emulatorDownloadManager: EmulatorDownloadManager,
    private val emulatorUpdateRepository: EmulatorUpdateRepository,
    private val saveHandlerRegistry: PlatformSaveHandlerRegistry
) {
    companion object {
        private const val TAG = "EmulatorSettingsDelegate"
    }
    private val _state = MutableStateFlow(EmulatorState())
    val state: StateFlow<EmulatorState> = _state.asStateFlow()

    private val _openUrlEvent = MutableSharedFlow<String>()
    val openUrlEvent: SharedFlow<String> = _openUrlEvent.asSharedFlow()

    private val _launchSavePathPicker = MutableSharedFlow<Unit>()
    val launchSavePathPicker: SharedFlow<Unit> = _launchSavePathPicker.asSharedFlow()

    private val _builtinNavigationEvent = MutableSharedFlow<BuiltinNavigationTarget>()
    val builtinNavigationEvent: SharedFlow<BuiltinNavigationTarget> = _builtinNavigationEvent.asSharedFlow()

    fun updateState(newState: EmulatorState) {
        _state.value = newState
    }

    fun showEmulatorPicker(config: PlatformEmulatorConfig, scope: CoroutineScope) {
        scope.launch {
            val updates = emulatorUpdateManager.getUpdatesForPlatform(config.platform.slug)
            val updateMap = updates.associate { update ->
                update.emulatorId to EmulatorUpdateInfo(
                    emulatorId = update.emulatorId,
                    currentVersion = update.installedVersion,
                    latestVersion = update.latestVersion,
                    downloadUrl = update.downloadUrl,
                    assetName = update.assetName,
                    assetSize = update.assetSize,
                    installedVariant = update.installedVariant
                )
            }

            _state.update {
                it.copy(
                    showEmulatorPicker = true,
                    emulatorPickerInfo = EmulatorPickerInfo(
                        platformId = config.platform.id,
                        platformSlug = config.platform.slug,
                        platformName = config.platform.name,
                        installedEmulators = config.availableEmulators,
                        downloadableEmulators = config.downloadableEmulators,
                        selectedEmulatorName = config.selectedEmulator,
                        updates = updateMap
                    ),
                    emulatorPickerFocusIndex = 0,
                    emulatorPickerSelectedIndex = null
                )
            }
        }
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun dismissEmulatorPicker() {
        _state.update {
            it.copy(
                showEmulatorPicker = false,
                emulatorPickerInfo = null,
                emulatorPickerFocusIndex = 0,
                emulatorPickerSelectedIndex = null
            )
        }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun movePlatformSubFocus(delta: Int, maxIndex: Int): Boolean {
        val currentIndex = _state.value.platformSubFocusIndex
        val newIndex = (currentIndex + delta).coerceIn(0, maxIndex)
        if (newIndex != currentIndex) {
            _state.update { it.copy(platformSubFocusIndex = newIndex) }
            return true
        }
        return false
    }

    fun resetPlatformSubFocus() {
        _state.update { it.copy(platformSubFocusIndex = 0) }
    }

    fun moveEmulatorPickerFocus(delta: Int) {
        _state.update { state ->
            val info = state.emulatorPickerInfo ?: return@update state
            val hasInstalled = info.installedEmulators.isNotEmpty()
            val totalItems = (if (hasInstalled) 1 else 0) + info.installedEmulators.size + info.downloadableEmulators.size
            val maxIndex = (totalItems - 1).coerceAtLeast(0)
            val newIndex = (state.emulatorPickerFocusIndex + delta).coerceIn(0, maxIndex)
            state.copy(emulatorPickerFocusIndex = newIndex)
        }
    }

    fun handleEmulatorPickerItemTap(
        index: Int,
        scope: CoroutineScope,
        onSetEmulator: suspend (Long, String, InstalledEmulator?) -> Unit,
        onLoadSettings: suspend () -> Unit
    ) {
        val state = _state.value
        if (state.emulatorPickerSelectedIndex == index) {
            _state.update { it.copy(emulatorPickerFocusIndex = index) }
            confirmEmulatorPickerSelection(scope, onSetEmulator, onLoadSettings)
        } else {
            _state.update {
                it.copy(
                    emulatorPickerSelectedIndex = index,
                    emulatorPickerFocusIndex = index
                )
            }
            soundManager.play(SoundType.NAVIGATE)
        }
    }

    fun confirmEmulatorPickerSelection(
        scope: CoroutineScope,
        onSetEmulator: suspend (Long, String, InstalledEmulator?) -> Unit,
        onLoadSettings: suspend () -> Unit
    ) {
        scope.launch {
            val state = _state.value
            val info = state.emulatorPickerInfo ?: return@launch
            val focusIndex = state.emulatorPickerFocusIndex
            val installedCount = info.installedEmulators.size
            val hasInstalled = installedCount > 0
            val downloadBaseIndex = if (hasInstalled) 1 + installedCount else 0

            if (info.downloadState !is EmulatorDownloadState.Idle) return@launch

            when {
                hasInstalled && focusIndex == 0 -> {
                    onSetEmulator(info.platformId, info.platformSlug, null)
                    dismissEmulatorPicker()
                    onLoadSettings()
                }
                hasInstalled && focusIndex in 1..installedCount -> {
                    val emulator = info.installedEmulators[focusIndex - 1]
                    val updateInfo = info.updates[emulator.def.id]

                    if (updateInfo != null) {
                        // If no variant stored and emulator supports GitHub updates,
                        // fetch fresh to allow variant selection
                        if (updateInfo.installedVariant == null && emulator.def.releaseSource != null) {
                            fetchAndDownloadEmulator(emulator.def)
                        } else {
                            downloadEmulatorUpdate(
                                emulatorId = updateInfo.emulatorId,
                                downloadUrl = updateInfo.downloadUrl,
                                assetName = updateInfo.assetName,
                                variant = updateInfo.installedVariant
                            )
                        }
                    } else {
                        onSetEmulator(info.platformId, info.platformSlug, emulator)
                        dismissEmulatorPicker()
                        onLoadSettings()
                    }
                }
                focusIndex >= downloadBaseIndex -> {
                    val downloadIndex = focusIndex - downloadBaseIndex
                    val emulator = info.downloadableEmulators.getOrNull(downloadIndex) ?: return@launch

                    if (emulator.releaseSource != null) {
                        fetchAndDownloadEmulator(emulator)
                    } else {
                        emulator.downloadUrl?.let { _openUrlEvent.emit(it) }
                    }
                }
            }
        }
    }

    fun cycleCoreForPlatform(
        scope: CoroutineScope,
        config: PlatformEmulatorConfig,
        direction: Int,
        onLoadSettings: suspend () -> Unit
    ) {
        if (config.availableCores.isEmpty()) return
        scope.launch {
            val currentIndex = config.selectedCore?.let { selectedId ->
                config.availableCores.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 }
            } ?: 0
            val nextIndex = (currentIndex + direction + config.availableCores.size) % config.availableCores.size
            val nextCore = config.availableCores[nextIndex]
            configureEmulatorUseCase.setCoreForPlatform(config.platform.id, nextCore.id)
            onLoadSettings()
        }
    }

    fun setPlatformEmulator(
        scope: CoroutineScope,
        platformId: Long,
        platformSlug: String,
        emulator: InstalledEmulator?,
        onLoadSettings: suspend () -> Unit
    ) {
        scope.launch {
            configureEmulatorUseCase.setForPlatform(platformId, platformSlug, emulator)
            onLoadSettings()
        }
    }



    fun refreshEmulators() {
        // EmulatorDetector will refresh on next detectEmulators() call
        // No explicit refresh needed - the ViewModel calls loadSettings() after this
    }

    fun setEmulatorSavePath(
        scope: CoroutineScope,
        emulatorId: String,
        path: String,
        onLoadSettings: suspend () -> Unit
    ) {
        scope.launch {
            emulatorSaveConfigRepository.setSavePath(emulatorId, path)
            onLoadSettings()
        }
    }

    fun resetEmulatorSavePath(
        scope: CoroutineScope,
        emulatorId: String,
        onLoadSettings: suspend () -> Unit
    ) {
        scope.launch {
            emulatorSaveConfigRepository.resetSavePath(emulatorId)
            onLoadSettings()
        }
    }

    fun setEmulatorStatePath(
        scope: CoroutineScope,
        emulatorId: String,
        path: String,
        onLoadSettings: suspend () -> Unit
    ) {
        scope.launch {
            emulatorSaveConfigRepository.setStatePath(emulatorId, path)
            onLoadSettings()
        }
    }

    fun resetEmulatorStatePath(
        scope: CoroutineScope,
        emulatorId: String,
        onLoadSettings: suspend () -> Unit
    ) {
        scope.launch {
            emulatorSaveConfigRepository.resetStatePath(emulatorId)
            onLoadSettings()
        }
    }

    suspend fun getEmulatorSaveConfig(emulatorId: String): EmulatorSaveConfigEntity? {
        return emulatorSaveConfigRepository.getByEmulator(emulatorId)
    }

    fun showSavePathModal(
        emulatorId: String,
        emulatorName: String,
        platformName: String,
        savePath: String?,
        isUserOverride: Boolean
    ) {
        _state.update {
            it.copy(
                showSavePathModal = true,
                savePathModalInfo = SavePathModalInfo(
                    emulatorId = emulatorId,
                    emulatorName = emulatorName,
                    platformName = platformName,
                    savePath = savePath,
                    isUserOverride = isUserOverride
                ),
                savePathModalFocusIndex = 0,
                savePathModalButtonIndex = 0
            )
        }
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun dismissSavePathModal() {
        _state.update {
            it.copy(
                showSavePathModal = false,
                savePathModalInfo = null,
                savePathModalFocusIndex = 0,
                savePathModalButtonIndex = 0
            )
        }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun moveSavePathModalFocus(delta: Int) {
        _state.update { state ->
            val maxIndex = 0 // Only Save Path is focusable; State Path disabled until save states supported
            val newIndex = (state.savePathModalFocusIndex + delta).coerceIn(0, maxIndex)
            state.copy(savePathModalFocusIndex = newIndex)
        }
    }

    fun showLaunchArgsModal(state: LaunchArgsModalState) {
        _state.update {
            it.copy(
                showLaunchArgsModal = true,
                launchArgsModalState = state
            )
        }
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun dismissLaunchArgsModal() {
        _state.update {
            it.copy(
                showLaunchArgsModal = false,
                launchArgsModalState = null
            )
        }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun moveLaunchArgsFocus(delta: Int) {
        _state.update { state ->
            val modal = state.launchArgsModalState ?: return@update state
            val rows = launchArgsModalRows(modal)
            if (rows.isEmpty()) return@update state
            // Locked rows stay focusable so scroll doesn't jump past them.
            val newIndex = (modal.focusIndex + delta).coerceIn(0, rows.size - 1)
            state.copy(launchArgsModalState = modal.copy(focusIndex = newIndex))
        }
    }

    fun updateLaunchArgsOverride(override: com.nendo.argosy.data.local.entity.EmulatorLaunchArgsEntity?) {
        _state.update { state ->
            val modal = state.launchArgsModalState ?: return@update state
            state.copy(launchArgsModalState = modal.copy(override = override))
        }
    }

    fun showAppPickerModal(modalState: com.nendo.argosy.ui.screens.settings.AppPickerModalState) {
        _state.update {
            it.copy(
                showAppPickerModal = true,
                appPickerModalState = modalState
            )
        }
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun dismissAppPickerModal() {
        _state.update {
            it.copy(
                showAppPickerModal = false,
                appPickerModalState = null
            )
        }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun moveAppPickerFocus(delta: Int) {
        _state.update { state ->
            val modal = state.appPickerModalState ?: return@update state
            if (modal.apps.isEmpty()) return@update state
            val newIndex = (modal.focusIndex + delta).coerceIn(0, modal.apps.size - 1)
            state.copy(appPickerModalState = modal.copy(focusIndex = newIndex))
        }
    }

    fun moveSavePathModalButtonFocus(delta: Int) {
        _state.update { state ->
            val hasReset = state.savePathModalInfo?.isUserOverride == true
            val maxIndex = if (hasReset) 1 else 0 // 0 = Change, 1 = Reset (only if override exists)
            val newIndex = (state.savePathModalButtonIndex + delta).coerceIn(0, maxIndex)
            state.copy(savePathModalButtonIndex = newIndex)
        }
    }

    fun confirmSavePathModalSelection(
        scope: CoroutineScope,
        onResetSavePath: () -> Unit
    ) {
        val state = _state.value
        if (!state.showSavePathModal) return

        when (state.savePathModalButtonIndex) {
            0 -> {
                scope.launch { _launchSavePathPicker.emit(Unit) }
            }
            1 -> {
                onResetSavePath()
            }
        }
    }

    fun toggleLegacyMode(
        scope: CoroutineScope,
        config: PlatformEmulatorConfig,
        onLoadSettings: suspend () -> Unit
    ) {
        scope.launch {
            configureEmulatorUseCase.setUseFileUriForPlatform(
                config.platform.id,
                !config.useFileUri
            )
            onLoadSettings()
        }
    }

    fun cycleDisplayTarget(
        scope: CoroutineScope,
        config: PlatformEmulatorConfig,
        direction: Int,
        onLoadSettings: suspend () -> Unit
    ) {
        val entries = EmulatorDisplayTarget.entries
        val currentIndex = entries.indexOf(config.displayTarget)
        val nextIndex = (currentIndex + direction).mod(entries.size)
        val next = entries[nextIndex]
        scope.launch {
            configureEmulatorUseCase.setDisplayTargetForPlatform(
                config.platform.id,
                if (next == EmulatorDisplayTarget.TOP) null else next.name
            )
            onLoadSettings()
        }
    }

    fun changeExtensionForPlatform(
        scope: CoroutineScope,
        platformId: Long,
        newExtension: String,
        onLoadSettings: suspend () -> Unit
    ) {
        scope.launch {
            val extensionToStore = newExtension.ifEmpty { null }
            configureEmulatorUseCase.setExtensionForPlatform(platformId, extensionToStore)
            onLoadSettings()
        }
    }

    suspend fun getPreferredExtension(platformId: Long): String? {
        return emulatorConfigRepo.getPreferredExtension(platformId)
    }


    fun updateCoreCounts() {
        val installedCores = coreManager.getInstalledCores()
        val allCores = LibretroCoreRegistry.getAllCores()
        _state.update {
            it.copy(
                installedCoreCount = installedCores.size,
                totalCoreCount = allCores.size
            )
        }
    }

    fun observeCoreUpdateCount(): Flow<Int> = coreVersionRepository.observeUpdateCount()

    fun updateCoreUpdatesAvailable(count: Int) {
        _state.update { it.copy(coreUpdatesAvailable = count) }
    }

    fun navigateToBuiltinVideo(scope: CoroutineScope) {
        scope.launch {
            _builtinNavigationEvent.emit(BuiltinNavigationTarget.VIDEO_SETTINGS)
        }
    }

    fun navigateToBuiltinControls(scope: CoroutineScope) {
        scope.launch {
            _builtinNavigationEvent.emit(BuiltinNavigationTarget.CONTROLS_SETTINGS)
        }
    }

    fun navigateToCoreManagement(scope: CoroutineScope) {
        scope.launch {
            _builtinNavigationEvent.emit(BuiltinNavigationTarget.CORE_MANAGEMENT)
        }
    }

    fun navigateToCoreOptions(scope: CoroutineScope) {
        scope.launch {
            _builtinNavigationEvent.emit(BuiltinNavigationTarget.CORE_OPTIONS)
        }
    }

    fun observeAvailableUpdates(): Flow<List<com.nendo.argosy.data.local.entity.EmulatorUpdateEntity>> =
        emulatorUpdateManager.availableUpdates


    fun observeDownloadProgress() = emulatorDownloadManager.downloadProgress

    fun forceCheckEmulatorUpdates() {
        emulatorUpdateManager.forceCheck()
    }

    fun updateEmulatorUpdateVersions(versions: Map<String, String>) {
        _state.update { it.copy(emulatorUpdateVersions = versions) }
    }

    fun checkForEmulatorUpdates(scope: CoroutineScope) {
        scope.launch {
            emulatorUpdateManager.checkForUpdates()
        }
    }

    fun triggerUpdateForEmulator(emulatorId: String, scope: CoroutineScope) {
        val emulatorDef = EmulatorRegistry.getById(emulatorId) ?: return

        _state.update {
            it.copy(updateModal = EmulatorUpdateModal(
                emulatorId = emulatorId,
                emulatorName = emulatorDef.displayName
            ), updateModalFocusIndex = 0)
        }

        scope.launch {
            val update = emulatorUpdateManager.getUpdateForEmulator(emulatorId)
            if (update?.installedVariant != null) {
                startUpdateModalDownload(
                    emulatorId = update.emulatorId,
                    downloadUrl = update.downloadUrl,
                    assetName = update.assetName,
                    variant = update.installedVariant
                )
            } else {
                when (val result = emulatorUpdateRepository.fetchLatestRelease(emulatorDef)) {
                    is FetchReleaseResult.Success -> {
                        startUpdateModalDownload(
                            emulatorId = emulatorDef.id,
                            downloadUrl = result.downloadUrl,
                            assetName = result.assetName,
                            variant = result.variant
                        )
                    }
                    is FetchReleaseResult.MultipleVariants -> {
                        _state.update { state ->
                            val modal = state.updateModal ?: return@update state
                            state.copy(updateModal = modal.copy(
                                state = UpdateModalState.SelectVariant(result.variants.map { v ->
                                    VariantOption(
                                        variant = v.variant,
                                        downloadUrl = v.downloadUrl,
                                        assetName = v.assetName,
                                        fileSize = v.assetSize
                                    )
                                })
                            ), updateModalFocusIndex = 0)
                        }
                    }
                    is FetchReleaseResult.Error -> {
                        _state.update { state ->
                            val modal = state.updateModal ?: return@update state
                            state.copy(updateModal = modal.copy(state = UpdateModalState.Failed(result.message)))
                        }
                    }
                }
            }
        }
    }

    private fun startUpdateModalDownload(
        emulatorId: String,
        downloadUrl: String,
        assetName: String,
        variant: String?
    ) {
        if (!emulatorDownloadManager.canInstallPackages()) {
            emulatorDownloadManager.openInstallPermissionSettings()
            dismissUpdateModal()
            return
        }
        _state.update { state ->
            val modal = state.updateModal ?: return@update state
            state.copy(updateModal = modal.copy(state = UpdateModalState.Downloading(0f)))
        }
        emulatorDownloadManager.downloadAndInstall(
            emulatorId = emulatorId,
            downloadUrl = downloadUrl,
            assetName = assetName,
            variant = variant
        )
    }

    fun selectUpdateModalVariant() {
        val state = _state.value
        val modal = state.updateModal ?: return
        val variants = (modal.state as? UpdateModalState.SelectVariant)?.variants ?: return
        val variant = variants.getOrNull(state.updateModalFocusIndex) ?: return
        startUpdateModalDownload(
            emulatorId = modal.emulatorId,
            downloadUrl = variant.downloadUrl,
            assetName = variant.assetName,
            variant = variant.variant
        )
    }

    fun moveUpdateModalFocus(delta: Int) {
        _state.update { state ->
            val modal = state.updateModal ?: return@update state
            val variants = (modal.state as? UpdateModalState.SelectVariant)?.variants ?: return@update state
            val maxIndex = (variants.size - 1).coerceAtLeast(0)
            val newIndex = (state.updateModalFocusIndex + delta).coerceIn(0, maxIndex)
            state.copy(updateModalFocusIndex = newIndex)
        }
    }

    fun dismissUpdateModal() {
        _state.update { it.copy(updateModal = null, updateModalFocusIndex = 0) }
    }

    fun updateUpdateModalProgress(emulatorId: String, downloadState: EmulatorDownloadState) {
        _state.update { state ->
            val modal = state.updateModal ?: return@update state
            if (modal.emulatorId != emulatorId) return@update state
            val newState = when (downloadState) {
                is EmulatorDownloadState.Downloading -> UpdateModalState.Downloading(downloadState.progress)
                is EmulatorDownloadState.WaitingForInstall -> UpdateModalState.WaitingForInstall
                is EmulatorDownloadState.Installed -> UpdateModalState.Installed
                is EmulatorDownloadState.Failed -> UpdateModalState.Failed(downloadState.message)
                is EmulatorDownloadState.Idle -> return@update state
            }
            state.copy(updateModal = modal.copy(state = newState))
        }
    }

    fun downloadEmulatorUpdate(
        emulatorId: String,
        downloadUrl: String,
        assetName: String,
        variant: String?
    ) {
        if (!emulatorDownloadManager.canInstallPackages()) {
            emulatorDownloadManager.openInstallPermissionSettings()
            return
        }

        emulatorDownloadManager.downloadAndInstall(
            emulatorId = emulatorId,
            downloadUrl = downloadUrl,
            assetName = assetName,
            variant = variant
        )

        _state.update { state ->
            val info = state.emulatorPickerInfo ?: return@update state
            state.copy(
                emulatorPickerInfo = info.copy(
                    downloadState = EmulatorDownloadState.Downloading(0f),
                    downloadingEmulatorId = emulatorId
                )
            )
        }
    }

    private suspend fun fetchAndDownloadEmulator(emulator: com.nendo.argosy.data.emulator.EmulatorDef) {
        _state.update { state ->
            val info = state.emulatorPickerInfo ?: return@update state
            state.copy(
                emulatorPickerInfo = info.copy(
                    downloadState = EmulatorDownloadState.Downloading(0f),
                    downloadingEmulatorId = emulator.id
                )
            )
        }

        when (val result = emulatorUpdateRepository.fetchLatestRelease(emulator)) {
            is FetchReleaseResult.Success -> {
                Log.d(TAG, "Fetched release for ${emulator.id}: ${result.version}")
                downloadEmulatorUpdate(
                    emulatorId = emulator.id,
                    downloadUrl = result.downloadUrl,
                    assetName = result.assetName,
                    variant = result.variant
                )
            }
            is FetchReleaseResult.MultipleVariants -> {
                Log.d(TAG, "Multiple variants for ${emulator.id}: ${result.variants.size}")
                _state.update { state ->
                    val info = state.emulatorPickerInfo ?: return@update state
                    state.copy(
                        emulatorPickerInfo = info.copy(
                            downloadState = EmulatorDownloadState.Idle,
                            downloadingEmulatorId = null
                        )
                    )
                }
                showVariantPicker(
                    emulatorId = emulator.id,
                    emulatorName = emulator.displayName,
                    variants = result.variants.map { v ->
                        VariantOption(
                            variant = v.variant,
                            downloadUrl = v.downloadUrl,
                            assetName = v.assetName,
                            fileSize = v.assetSize
                        )
                    }
                )
            }
            is FetchReleaseResult.Error -> {
                Log.e(TAG, "Failed to fetch release for ${emulator.id}: ${result.message}")
                _state.update { state ->
                    val info = state.emulatorPickerInfo ?: return@update state
                    state.copy(
                        emulatorPickerInfo = info.copy(
                            downloadState = EmulatorDownloadState.Failed(result.message),
                            downloadingEmulatorId = emulator.id
                        )
                    )
                }
            }
        }
    }

    fun updatePickerDownloadState(emulatorId: String?, downloadState: EmulatorDownloadState) {
        _state.update { state ->
            val info = state.emulatorPickerInfo ?: return@update state
            state.copy(
                emulatorPickerInfo = info.copy(
                    downloadState = downloadState,
                    downloadingEmulatorId = emulatorId
                )
            )
        }
    }

    fun showVariantPicker(
        emulatorId: String,
        emulatorName: String,
        variants: List<VariantOption>
    ) {
        _state.update {
            it.copy(
                showVariantPicker = true,
                variantPickerInfo = VariantPickerInfo(
                    emulatorId = emulatorId,
                    emulatorName = emulatorName,
                    variants = variants
                ),
                variantPickerFocusIndex = 0
            )
        }
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun dismissVariantPicker() {
        _state.update {
            it.copy(
                showVariantPicker = false,
                variantPickerInfo = null,
                variantPickerFocusIndex = 0
            )
        }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun moveVariantPickerFocus(delta: Int) {
        _state.update { state ->
            val info = state.variantPickerInfo ?: return@update state
            val maxIndex = (info.variants.size - 1).coerceAtLeast(0)
            val newIndex = (state.variantPickerFocusIndex + delta).coerceIn(0, maxIndex)
            state.copy(variantPickerFocusIndex = newIndex)
        }
    }

    fun selectVariant() {
        val state = _state.value
        val info = state.variantPickerInfo ?: return
        val variant = info.variants.getOrNull(state.variantPickerFocusIndex) ?: return

        dismissVariantPicker()

        downloadEmulatorUpdate(
            emulatorId = info.emulatorId,
            downloadUrl = variant.downloadUrl,
            assetName = variant.assetName,
            variant = variant.variant
        )
    }

    suspend fun getUpdatesForPlatform(platformSlug: String): List<EmulatorUpdateEntity> {
        return emulatorUpdateManager.getUpdatesForPlatform(platformSlug)
    }

    suspend fun listPs2FolderMemcardsForEmulator(
        emulatorId: String,
        emulatorPackage: String?
    ): List<com.nendo.argosy.data.sync.platform.MemcardInfo> {
        val userConfig = emulatorSaveConfigRepository.getByEmulator(emulatorId)
        val basePathOverride = if (userConfig?.isUserOverride == true) userConfig.savePathPattern else null
        return saveHandlerRegistry.listPs2FolderMemcardsForEmulator(
            emulatorId = emulatorId,
            emulatorPackage = emulatorPackage,
            basePathOverride = basePathOverride
        )
    }

    fun showMemcardPicker(
        scope: CoroutineScope,
        emulatorId: String,
        emulatorName: String,
        emulatorPackage: String?,
        platformName: String
    ) {
        scope.launch {
            val cards = listPs2FolderMemcardsForEmulator(emulatorId, emulatorPackage)
            val userConfig = emulatorSaveConfigRepository.getByEmulator(emulatorId)
            val selectedPath = userConfig?.selectedMemcardPath
            val initialFocus = cards.indexOfFirst { it.path == selectedPath }.coerceAtLeast(0)
            _state.update {
                it.copy(
                    showMemcardPicker = true,
                    memcardPickerInfo = MemcardPickerInfo(
                        emulatorId = emulatorId,
                        emulatorName = emulatorName,
                        platformName = platformName,
                        cards = cards,
                        selectedCardPath = selectedPath
                    ),
                    memcardPickerFocusIndex = initialFocus
                )
            }
            soundManager.play(SoundType.OPEN_MODAL)
        }
    }

    fun dismissMemcardPicker() {
        _state.update {
            it.copy(
                showMemcardPicker = false,
                memcardPickerInfo = null,
                memcardPickerFocusIndex = 0
            )
        }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun moveMemcardPickerFocus(delta: Int) {
        _state.update { state ->
            val info = state.memcardPickerInfo ?: return@update state
            val maxIndex = (info.cards.size - 1).coerceAtLeast(0)
            val newIndex = (state.memcardPickerFocusIndex + delta).coerceIn(0, maxIndex)
            state.copy(memcardPickerFocusIndex = newIndex)
        }
    }

    fun confirmMemcardSelection(
        scope: CoroutineScope,
        cardPath: String,
        onLoadSettings: suspend () -> Unit
    ) {
        scope.launch {
            val emulatorId = _state.value.memcardPickerInfo?.emulatorId ?: return@launch
            emulatorSaveConfigRepository.setMemcardPath(emulatorId, cardPath)
            dismissMemcardPicker()
            onLoadSettings()
        }
    }

    fun clearMemcardSelection(
        scope: CoroutineScope,
        emulatorId: String,
        onLoadSettings: suspend () -> Unit
    ) {
        scope.launch {
            emulatorSaveConfigRepository.clearMemcardPath(emulatorId)
            onLoadSettings()
        }
    }
}

