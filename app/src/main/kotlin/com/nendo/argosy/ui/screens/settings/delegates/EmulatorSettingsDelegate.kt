package com.nendo.argosy.ui.screens.settings.delegates

import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.emulator.EmulatorDownloadManager
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.EmulatorUpdateManager
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.data.local.dao.CoreVersionDao
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.EmulatorSaveConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.EmulatorUpdateEntity
import com.nendo.argosy.data.local.entity.EmulatorSaveConfigEntity
import com.nendo.argosy.domain.usecase.game.ConfigureEmulatorUseCase
import com.nendo.argosy.libretro.LibretroCoreManager
import com.nendo.argosy.libretro.LibretroCoreRegistry
import kotlinx.coroutines.flow.Flow
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.ui.screens.settings.EmulatorDownloadState
import com.nendo.argosy.ui.screens.settings.EmulatorPickerInfo
import com.nendo.argosy.ui.screens.settings.EmulatorSavePathInfo
import com.nendo.argosy.ui.screens.settings.EmulatorState
import com.nendo.argosy.ui.screens.settings.EmulatorUpdateInfo
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

enum class BuiltinNavigationTarget {
    VIDEO_SETTINGS,
    CONTROLS_SETTINGS,
    CORE_MANAGEMENT
}

class EmulatorSettingsDelegate @Inject constructor(
    private val emulatorDetector: EmulatorDetector,
    private val configureEmulatorUseCase: ConfigureEmulatorUseCase,
    private val soundManager: SoundFeedbackManager,
    private val emulatorSaveConfigDao: EmulatorSaveConfigDao,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val gameDao: GameDao,
    private val coreManager: LibretroCoreManager,
    private val coreVersionDao: CoreVersionDao,
    private val emulatorUpdateManager: EmulatorUpdateManager,
    private val emulatorDownloadManager: EmulatorDownloadManager
) {
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
                    assetSize = update.assetSize
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
                        downloadEmulatorUpdate(
                            emulatorId = updateInfo.emulatorId,
                            downloadUrl = updateInfo.downloadUrl,
                            assetName = updateInfo.assetName,
                            variant = null
                        )
                    } else {
                        onSetEmulator(info.platformId, info.platformSlug, emulator)
                        dismissEmulatorPicker()
                        onLoadSettings()
                    }
                }
                focusIndex >= downloadBaseIndex -> {
                    val downloadIndex = focusIndex - downloadBaseIndex
                    val emulator = info.downloadableEmulators.getOrNull(downloadIndex)
                    emulator?.downloadUrl?.let { _openUrlEvent.emit(it) }
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

    fun autoAssignAllEmulators(
        scope: CoroutineScope,
        onLoadSettings: suspend () -> Unit
    ) {
        scope.launch {
            val platforms = _state.value.platforms
            for (config in platforms) {
                if (!config.isUserConfigured && config.availableEmulators.isNotEmpty()) {
                    val preferred = emulatorDetector.getPreferredEmulator(config.platform.slug)
                    if (preferred != null) {
                        configureEmulatorUseCase.setForPlatform(config.platform.id, config.platform.slug, preferred)
                    }
                }
            }
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
            emulatorSaveConfigDao.upsert(
                EmulatorSaveConfigEntity(
                    emulatorId = emulatorId,
                    savePathPattern = path,
                    isAutoDetected = false,
                    isUserOverride = true,
                    lastVerifiedAt = Instant.now()
                )
            )
            onLoadSettings()
        }
    }

    fun resetEmulatorSavePath(
        scope: CoroutineScope,
        emulatorId: String,
        onLoadSettings: suspend () -> Unit
    ) {
        scope.launch {
            emulatorSaveConfigDao.delete(emulatorId)
            onLoadSettings()
        }
    }

    suspend fun getEmulatorSaveConfig(emulatorId: String): EmulatorSaveConfigEntity? {
        return emulatorSaveConfigDao.getByEmulator(emulatorId)
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
        return emulatorConfigDao.getPreferredExtension(platformId)?.ifEmpty { null }
    }

    suspend fun getAllEmulatorSavePaths(): List<EmulatorSavePathInfo> {
        val saveConfigs = emulatorSaveConfigDao.getAll()
        val installedEmulators = state.value.installedEmulators

        return saveConfigs.mapNotNull { config ->
            val emulator = installedEmulators.find { it.def.id == config.emulatorId }
            val emulatorName = emulator?.def?.displayName ?: return@mapNotNull null

            EmulatorSavePathInfo(
                emulatorId = config.emulatorId,
                emulatorName = emulatorName,
                savePath = config.savePathPattern,
                isCustom = config.isUserOverride
            )
        }
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

    fun observeCoreUpdateCount(): Flow<Int> = coreVersionDao.observeUpdateCount()

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

    fun observeEmulatorUpdateCount(): Flow<Int> = emulatorUpdateManager.updateCount

    fun observeDownloadProgress() = emulatorDownloadManager.downloadProgress

    fun updateEmulatorUpdatesAvailable(count: Int) {
        _state.update { it.copy(emulatorUpdatesAvailable = count) }
    }

    fun updatePlatformUpdatesAvailable(platformUpdates: Map<String, Int>) {
        _state.update { it.copy(platformUpdatesAvailable = platformUpdates) }
    }

    fun checkForEmulatorUpdates(scope: CoroutineScope) {
        scope.launch {
            emulatorUpdateManager.checkForUpdates()
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
}

