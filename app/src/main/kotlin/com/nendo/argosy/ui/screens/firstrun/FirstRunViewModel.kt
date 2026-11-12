package com.nendo.argosy.ui.screens.firstrun

import android.provider.Settings
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import com.nendo.argosy.R
import androidx.lifecycle.viewModelScope
import android.app.Application
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.DeviceAuthOutcome
import com.nendo.argosy.data.remote.romm.RomMCapabilities
import com.nendo.argosy.data.remote.romm.RomMErrorKind
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.remote.ssl.UserCertStore
import com.nendo.argosy.data.remote.romm.pollDeviceAuthUntilResolved
import com.nendo.argosy.ui.screens.settings.RomMAuthMethod
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import com.nendo.argosy.libretro.LibretroCoreManager
import com.nendo.argosy.libretro.formatCoreDownloadError
import com.nendo.argosy.ui.input.GamepadInputHandler
import com.nendo.argosy.ui.input.HapticFeedbackManager
import com.nendo.argosy.ui.input.HapticPattern
import com.nendo.argosy.ui.components.PLATFORM_HEADER_COUNT
import com.nendo.argosy.ui.components.PLATFORM_HEADER_SEARCH
import com.nendo.argosy.ui.components.PLATFORM_HEADER_SORT
import com.nendo.argosy.data.setup.SetupStateRepository
import com.nendo.argosy.domain.setup.SetupRouter
import com.nendo.argosy.libretro.LibretroCoreRegistry
import com.nendo.argosy.util.PlatformFilterLogic
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FirstRunStep {
    WELCOME,
    ROMM_LOGIN,
    ROMM_SUCCESS,
    PERMISSIONS,
    ROM_PATH,
    IMAGE_CACHE,
    SAVE_SYNC,
    PLATFORM_SELECT,
    CORE_PROMPT,
    CORE_DOWNLOAD,
    COMPLETE
}

data class CoreDownloadState(
    val coreId: String,
    val displayName: String,
    val platforms: Set<String>,
    val status: CoreDownloadStatus = CoreDownloadStatus.PENDING,
    val errorMessage: String? = null
)

enum class CoreDownloadStatus {
    PENDING, DOWNLOADING, COMPLETE, FAILED
}

/**
 * A connection failure to show on the login steps. Either app-authored text, held as
 * [textRes] plus an optional [arg] so the render site resolves it in the user's locale,
 * or [serverMessage], which the server supplied and the app cannot translate.
 */
data class FirstRunError(
    @StringRes val textRes: Int? = null,
    val arg: String? = null,
    val serverMessage: String? = null
)

/**
 * A connection failure the user can act on reads as app-authored text, not as the server's.
 *
 * An untrusted chain surfaces from OkHttp as a validator message that names neither the cause
 * nor the remedy, so passing it through leaves the reader with no way to connect the failure to
 * the certificate they need to import.
 */
private fun RomMResult.Error.toFirstRunError(): FirstRunError = when (kind) {
    RomMErrorKind.UNTRUSTED_CERTIFICATE ->
        FirstRunError(textRes = R.string.firstrun_romm_error_untrusted_certificate)
    null -> FirstRunError(serverMessage = message)
}

data class FirstRunUiState(
    val currentStep: FirstRunStep = FirstRunStep.WELCOME,
    val focusedIndex: Int = 0,
    val rommUrl: String = "",
    val rommUrlCommitted: Boolean = false,
    val rommPairingCode: String = "",
    val rommAuthMethod: RomMAuthMethod = RomMAuthMethod.DEVICE,
    val rommDevicePairing: Boolean = false,
    val rommDeviceUserCode: String? = null,
    val rommDeviceVerificationUrl: String? = null,
    val rommSupportsDeviceAuth: Boolean = false,
    val rommBrowserMissing: Boolean = false,
    val isConnecting: Boolean = false,
    val connectionError: FirstRunError? = null,
    val importedCertCount: Int = 0,
    val rommGameCount: Int = 0,
    val rommPlatformCount: Int = 0,
    val romStoragePath: String? = null,
    val folderSelected: Boolean = false,
    val launchFolderPicker: Boolean = false,
    val imageCachePath: String? = null,
    val imageCacheFolderSelected: Boolean = false,
    val launchImageCachePicker: Boolean = false,
    val hasStoragePermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val hasUsageStatsPermission: Boolean = false,
    val usageStatsGrantable: Boolean = true,
    val requiredPermissionsGranted: Boolean = false,
    val saveSyncEnabled: Boolean = true,
    val rommFocusField: Int? = null,
    val platforms: List<PlatformEntity> = emptyList(),
    val platformsAll: List<PlatformEntity> = emptyList(),
    val platformFilterSortMode: PlatformFilterLogic.SortMode = PlatformFilterLogic.SortMode.DEFAULT,
    val platformFilterMode: PlatformFilterLogic.FilterMode = PlatformFilterLogic.FilterMode.ALL,
    val platformFilterSearchQuery: String = "",
    val platformHeaderFocused: Boolean = false,
    val platformHeaderIndex: Int = 0,
    val platformSearchActive: Boolean = false,
    val platformSortMenuOpen: Boolean = false,
    val platformSortMenuIndex: Int = 0,
    val platformButtonFocus: Int = 1,
    val missingCoreCount: Int = 0,
    val coreDownloads: List<CoreDownloadState> = emptyList(),
    val coreDownloadComplete: Boolean = false
)

@HiltViewModel
@Suppress("TooManyFunctions")
class FirstRunViewModel @Inject constructor(
    private val application: Application,
    private val preferencesRepository: UserPreferencesRepository,
    private val romMRepository: RomMRepository,
    private val platformRepository: PlatformRepository,
    private val setupStateRepository: SetupStateRepository,
    private val coreManager: LibretroCoreManager,
    private val gamepadInputHandler: GamepadInputHandler,
    private val hapticManager: HapticFeedbackManager,
    private val userCertStore: UserCertStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(FirstRunUiState())
    val uiState: StateFlow<FirstRunUiState> = _uiState.asStateFlow()

    private var devicePollJob: Job? = null

    init {
        viewModelScope.launch {
            userCertStore.certs.collect { certs ->
                _uiState.update { it.copy(importedCertCount = certs.size) }
            }
        }
        viewModelScope.launch {
            setupStateRepository.facts.collect { facts ->
                _uiState.update {
                    it.copy(
                        hasStoragePermission = facts.allFilesAccess,
                        hasNotificationPermission = facts.notifications,
                        hasUsageStatsPermission = facts.usageAccess,
                        usageStatsGrantable = facts.usageAccessGrantable,
                        requiredPermissionsGranted = SetupRouter.permissionsSatisfied(facts)
                    )
                }
            }
        }
        viewModelScope.launch { openAtFirstMissingItem() }
    }

    private suspend fun openAtFirstMissingItem() {
        val facts = setupStateRepository.facts.first()
        val prefs = preferencesRepository.userPreferences.first()
        val entry = entryStepFor(SetupRouter.derive(facts), facts)
        _uiState.update { it.copy(rommUrl = it.rommUrl.ifBlank { prefs.rommBaseUrl.orEmpty() }) }
        if (facts.librarySignedIn) loadLibrarySummary()
        if (entry != FirstRunStep.WELCOME) moveTo(entry)
    }

    private suspend fun loadLibrarySummary() {
        val summary = romMRepository.getLibrarySummary()
        if (summary !is RomMResult.Success) return
        val (platformCount, gameCount) = summary.data
        _uiState.update { it.copy(rommGameCount = gameCount, rommPlatformCount = platformCount) }
    }

    fun nextStep() {
        moveTo(stepAfter(_uiState.value.currentStep, _uiState.value.rommPlatformCount))
    }

    fun previousStep() {
        devicePollJob?.cancel()
        devicePollJob = null
        romMRepository.cancelDeviceAuth()
        _uiState.update { it.copy(rommUrlCommitted = false) }
        moveTo(stepBefore(_uiState.value.currentStep, _uiState.value.rommPlatformCount))
    }

    private fun moveTo(step: FirstRunStep) {
        _uiState.update { it.copy(currentStep = step, focusedIndex = initialFocusFor(step, it)) }
        enterStep(step)
    }

    private fun initialFocusFor(step: FirstRunStep, state: FirstRunUiState): Int = when (step) {
        FirstRunStep.IMAGE_CACHE -> if (state.imageCacheFolderSelected) 0 else 1
        FirstRunStep.SAVE_SYNC -> if (state.saveSyncEnabled) SAVE_SYNC_ON_INDEX else SAVE_SYNC_OFF_INDEX
        else -> 0
    }

    private fun enterStep(step: FirstRunStep) {
        when (step) {
            FirstRunStep.ROMM_SUCCESS -> viewModelScope.launch {
                if (_uiState.value.rommGameCount == 0) loadLibrarySummary()
            }
            FirstRunStep.PERMISSIONS -> refreshAllPermissions()
            FirstRunStep.ROM_PATH, FirstRunStep.IMAGE_CACHE, FirstRunStep.SAVE_SYNC ->
                viewModelScope.launch { showSavedValues() }
            FirstRunStep.PLATFORM_SELECT -> viewModelScope.launch { loadPlatforms(defaultSyncEnabled = false) }
            FirstRunStep.CORE_PROMPT -> viewModelScope.launch { checkMissingCores() }
            FirstRunStep.CORE_DOWNLOAD -> viewModelScope.launch { prepareCoreDownloads() }
            else -> {}
        }
    }

    private suspend fun showSavedValues() {
        val prefs = preferencesRepository.userPreferences.first()
        val saveSyncAnswered = setupStateRepository.facts.first().saveSyncAnswered
        _uiState.update { state ->
            val romPath = state.romStoragePath ?: prefs.romStoragePath?.takeIf { it.isNotBlank() }
            val cachePath = state.imageCachePath ?: prefs.imageCachePath?.takeIf { it.isNotBlank() }
            val saveSync = if (saveSyncAnswered) prefs.saveSyncEnabled else state.saveSyncEnabled
            val updated = state.copy(
                romStoragePath = romPath,
                folderSelected = romPath != null,
                imageCachePath = cachePath,
                imageCacheFolderSelected = cachePath != null,
                saveSyncEnabled = saveSync
            )
            updated.copy(focusedIndex = initialFocusFor(updated.currentStep, updated))
        }
    }

    private suspend fun loadPlatforms(defaultSyncEnabled: Boolean): List<PlatformEntity> {
        val all = when (val result = romMRepository.fetchAndStorePlatforms(defaultSyncEnabled)) {
            is RomMResult.Success -> result.data
            is RomMResult.Error -> platformRepository.observeAllPlatforms().first()
        }
        _uiState.update {
            it.copy(
                platformsAll = all,
                platforms = PlatformFilterLogic.filterAndSortPlatformEntities(
                    items = all,
                    searchQuery = it.platformFilterSearchQuery,
                    filterMode = it.platformFilterMode,
                    sortMode = it.platformFilterSortMode
                )
            )
        }
        return all
    }

    private suspend fun platformsForCoreCheck(): List<PlatformEntity> =
        _uiState.value.platformsAll.ifEmpty {
            loadPlatforms(defaultSyncEnabled = !showsPlatformSelect(_uiState.value.rommPlatformCount))
        }

    private suspend fun missingCores(): List<LibretroCoreRegistry.CoreInfo> {
        val enabledSlugs = platformsForCoreCheck().filter { it.syncEnabled }.map { it.slug }.toSet()
        return coreManager.getMissingCoresForPlatforms(enabledSlugs)
    }

    private suspend fun checkMissingCores() {
        val missing = missingCores()
        _uiState.update { it.copy(missingCoreCount = missing.size) }
    }

    fun skipCorePrompt() {
        moveTo(FirstRunStep.COMPLETE)
    }

    private suspend fun prepareCoreDownloads() {
        val coreDownloads = missingCores().map { core ->
            CoreDownloadState(
                coreId = core.coreId,
                displayName = core.displayName,
                platforms = core.platforms
            )
        }

        _uiState.update {
            it.copy(
                coreDownloads = coreDownloads,
                coreDownloadComplete = coreDownloads.isEmpty()
            )
        }

        if (coreDownloads.isNotEmpty()) {
            startCoreDownloads()
        }
    }

    private fun startCoreDownloads() {
        viewModelScope.launch {
            val cores = _uiState.value.coreDownloads.filter { it.status == CoreDownloadStatus.PENDING }
            for (core in cores) {
                _uiState.update { state ->
                    state.copy(
                        coreDownloads = state.coreDownloads.map {
                            if (it.coreId == core.coreId) it.copy(status = CoreDownloadStatus.DOWNLOADING)
                            else it
                        }
                    )
                }

                val result = coreManager.downloadCoreById(core.coreId)
                val newStatus = if (result.isSuccess) CoreDownloadStatus.COMPLETE else CoreDownloadStatus.FAILED
                val errorMessage = result.exceptionOrNull()?.let { formatCoreDownloadError(it) }

                _uiState.update { state ->
                    val updatedCores = state.coreDownloads.map {
                        if (it.coreId == core.coreId) it.copy(status = newStatus, errorMessage = errorMessage)
                        else it
                    }
                    val allComplete = updatedCores.all { it.status == CoreDownloadStatus.COMPLETE || it.status == CoreDownloadStatus.FAILED }
                    if (allComplete) gamepadInputHandler.blockInputFor(300)
                    state.copy(
                        coreDownloads = updatedCores,
                        coreDownloadComplete = allComplete
                    )
                }
            }
        }
    }

    fun retryCoreDownload(coreId: String) {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    coreDownloads = state.coreDownloads.map {
                        if (it.coreId == coreId) it.copy(status = CoreDownloadStatus.DOWNLOADING, errorMessage = null)
                        else it
                    },
                    coreDownloadComplete = false
                )
            }

            val result = coreManager.downloadCoreById(coreId)
            val newStatus = if (result.isSuccess) CoreDownloadStatus.COMPLETE else CoreDownloadStatus.FAILED
            val errorMessage = result.exceptionOrNull()?.let { formatCoreDownloadError(it) }

            _uiState.update { state ->
                val updatedCores = state.coreDownloads.map {
                    if (it.coreId == coreId) it.copy(status = newStatus, errorMessage = errorMessage)
                    else it
                }
                val allComplete = updatedCores.all { it.status == CoreDownloadStatus.COMPLETE || it.status == CoreDownloadStatus.FAILED }
                if (allComplete) gamepadInputHandler.blockInputFor(300)
                state.copy(
                    coreDownloads = updatedCores,
                    coreDownloadComplete = allComplete
                )
            }
        }
    }

    fun skipCoreDownloads() {
        nextStep()
    }

    fun togglePlatform(platformId: Long) {
        viewModelScope.launch {
            val currentAll = _uiState.value.platformsAll
            val platformIndex = currentAll.indexOfFirst { it.id == platformId }
            if (platformIndex == -1) return@launch

            val platform = currentAll[platformIndex]
            val newEnabled = !platform.syncEnabled
            platformRepository.updateSyncEnabled(platformId, newEnabled)

            val updatedAll = currentAll.toMutableList()
            updatedAll[platformIndex] = platform.copy(syncEnabled = newEnabled)

            _uiState.update { it.copy(platformsAll = updatedAll) }
            applyPlatformFilters()
        }
    }

    fun toggleAllPlatforms() {
        viewModelScope.launch {
            val currentAll = _uiState.value.platformsAll
            val allEnabled = currentAll.all { it.syncEnabled }
            val newState = !allEnabled

            currentAll.forEach { platform ->
                platformRepository.updateSyncEnabled(platform.id, newState)
            }

            val updatedAll = currentAll.map { it.copy(syncEnabled = newState) }

            _uiState.update { it.copy(platformsAll = updatedAll) }
            applyPlatformFilters()
        }
    }

    fun setPlatformFilterSortMode(mode: PlatformFilterLogic.SortMode) {
        _uiState.update { it.copy(platformFilterSortMode = mode) }
        applyPlatformFilters(resetFocus = true)
    }

    fun cyclePlatformFilterMode() {
        _uiState.update {
            val nextMode = when (it.platformFilterMode) {
                PlatformFilterLogic.FilterMode.ALL -> PlatformFilterLogic.FilterMode.HAS_GAMES
                PlatformFilterLogic.FilterMode.HAS_GAMES -> PlatformFilterLogic.FilterMode.ENABLED
                PlatformFilterLogic.FilterMode.ENABLED -> PlatformFilterLogic.FilterMode.ALL
            }
            it.copy(platformFilterMode = nextMode)
        }
        applyPlatformFilters(resetFocus = true)
    }

    fun setPlatformFilterSearchQuery(query: String) {
        _uiState.update { it.copy(platformFilterSearchQuery = query) }
        applyPlatformFilters(resetFocus = true)
    }

    fun openPlatformSearch() = _uiState.update {
        it.copy(platformHeaderFocused = true, platformHeaderIndex = PLATFORM_HEADER_SEARCH, platformSearchActive = true)
    }

    fun closePlatformSearch() = _uiState.update { it.copy(platformSearchActive = false) }

    fun openPlatformSortMenu() = _uiState.update {
        it.copy(
            platformHeaderFocused = true,
            platformHeaderIndex = PLATFORM_HEADER_SORT,
            platformSortMenuOpen = true,
            platformSortMenuIndex = PlatformFilterLogic.SortMode.entries.indexOf(it.platformFilterSortMode).coerceAtLeast(0)
        )
    }

    fun closePlatformSortMenu() = _uiState.update { it.copy(platformSortMenuOpen = false) }

    private fun applyPlatformFilters(resetFocus: Boolean = false) {
        val state = _uiState.value
        val allPlatforms = state.platformsAll

        val filtered = PlatformFilterLogic.filterAndSortPlatformEntities(
            items = allPlatforms,
            searchQuery = state.platformFilterSearchQuery,
            filterMode = state.platformFilterMode,
            sortMode = state.platformFilterSortMode
        )

        _uiState.update {
            it.copy(
                platforms = filtered,
                focusedIndex = if (resetFocus) 0 else it.focusedIndex.coerceIn(0, (filtered.size - 1).coerceAtLeast(0))
            )
        }
    }

    fun getMaxFocusIndex(): Int {
        val state = _uiState.value
        return when (state.currentStep) {
            FirstRunStep.WELCOME -> 0
            FirstRunStep.ROMM_LOGIN -> when {
                state.rommDevicePairing -> 2
                !state.rommUrlCommitted -> 3
                else -> if (state.rommSupportsDeviceAuth) 3 else 2
            }
            FirstRunStep.ROMM_SUCCESS -> 0
            FirstRunStep.PERMISSIONS -> 4
            FirstRunStep.ROM_PATH -> if (state.folderSelected) 1 else 0
            FirstRunStep.IMAGE_CACHE -> 1
            FirstRunStep.SAVE_SYNC -> SAVE_SYNC_NEXT_INDEX
            FirstRunStep.PLATFORM_SELECT -> state.platforms.size
            FirstRunStep.CORE_PROMPT -> 1
            FirstRunStep.CORE_DOWNLOAD -> 1
            FirstRunStep.COMPLETE -> 0
        }
    }

    fun moveFocus(delta: Int): Boolean {
        val state = _uiState.value
        if (state.currentStep == FirstRunStep.PLATFORM_SELECT) {
            when {
                state.platformSortMenuOpen -> {
                    _uiState.update {
                        it.copy(platformSortMenuIndex = (it.platformSortMenuIndex + delta).mod(PlatformFilterLogic.SortMode.entries.size))
                    }
                    return true
                }
                state.platformSearchActive -> return true
                state.platformHeaderFocused -> {
                    if (delta > 0) _uiState.update { it.copy(platformHeaderFocused = false, focusedIndex = 0) }
                    return true
                }
                delta < 0 && state.focusedIndex == 0 -> {
                    _uiState.update { it.copy(platformHeaderFocused = true) }
                    return true
                }
            }
        }
        val maxIndex = getMaxFocusIndex()
        val newIndex = (state.focusedIndex + delta).coerceIn(0, maxIndex)
        if (newIndex == state.focusedIndex) {
            if (delta != 0) hapticManager.vibrate(HapticPattern.BOUNDARY_HIT)
            return false
        }
        _uiState.update { it.copy(focusedIndex = newIndex) }
        return true
    }

    fun setFocusedIndex(index: Int) {
        _uiState.update { it.copy(focusedIndex = index) }
    }

    fun moveButtonFocus(delta: Int): Boolean {
        val state = _uiState.value
        if (state.currentStep != FirstRunStep.PLATFORM_SELECT) return false
        if (state.platformHeaderFocused && !state.platformSortMenuOpen && !state.platformSearchActive) {
            _uiState.update { it.copy(platformHeaderIndex = (it.platformHeaderIndex + delta).mod(PLATFORM_HEADER_COUNT)) }
            return true
        }
        if (state.focusedIndex < state.platforms.size) return false
        val newIndex = (state.platformButtonFocus + delta).coerceIn(0, 1)
        if (newIndex == state.platformButtonFocus) {
            if (delta != 0) hapticManager.vibrate(HapticPattern.BOUNDARY_HIT)
            return false
        }
        _uiState.update { it.copy(platformButtonFocus = newIndex) }
        return true
    }

    fun setRommFocusField(index: Int) {
        _uiState.update { it.copy(rommFocusField = index) }
    }

    fun clearRommFocusField() {
        _uiState.update { it.copy(rommFocusField = null) }
    }

    fun setRommUrl(url: String) {
        _uiState.update { it.copy(rommUrl = url, connectionError = null) }
    }

    fun setRommPairingCode(code: String) {
        _uiState.update { it.copy(rommPairingCode = code, connectionError = null) }
    }

    fun clearRommPairingCode() {
        _uiState.update { it.copy(rommPairingCode = "", connectionError = null) }
    }

    fun connectToRomm() {
        val state = _uiState.value
        if (state.rommAuthMethod == RomMAuthMethod.DEVICE) {
            startDevicePairing(state.rommUrl)
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, connectionError = null) }
            connectWithPairingCode(state.rommUrl, state.rommPairingCode)
        }
    }

    fun commitUrl() {
        val state = _uiState.value
        if (state.isConnecting || state.rommUrlCommitted) return
        val url = state.rommUrl
        if (url.isBlank()) return
        _uiState.update { it.copy(isConnecting = true, connectionError = null) }
        viewModelScope.launch {
            when (val result = romMRepository.probeServerVersion(url)) {
                is RomMResult.Success -> {
                    val supportsDeviceAuth = RomMCapabilities.from(result.data).supportsDeviceAuth
                    if (supportsDeviceAuth) {
                        _uiState.update {
                            it.copy(
                                rommUrlCommitted = true,
                                rommAuthMethod = RomMAuthMethod.DEVICE,
                                rommSupportsDeviceAuth = true
                            )
                        }
                        startDevicePairing(url)
                    } else {
                        _uiState.update {
                            it.copy(
                                isConnecting = false,
                                rommUrlCommitted = true,
                                rommAuthMethod = RomMAuthMethod.PAIRING_CODE,
                                rommSupportsDeviceAuth = false,
                                connectionError = null,
                                focusedIndex = 0
                            )
                        }
                    }
                }
                is RomMResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            connectionError = result.toFirstRunError()
                        )
                    }
                }
            }
        }
    }

    fun importCertificate(path: String) {
        viewModelScope.launch {
            val result = userCertStore.importFrom(path)
            if (result.isFailure) {
                _uiState.update {
                    it.copy(
                        connectionError =
                            FirstRunError(textRes = R.string.firstrun_romm_certificate_import_failed)
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(connectionError = null) }
        }
    }

    fun editUrl() {
        devicePollJob?.cancel()
        devicePollJob = null
        romMRepository.cancelDeviceAuth()
        _uiState.update {
            it.copy(
                rommUrlCommitted = false,
                rommDevicePairing = false,
                rommDeviceUserCode = null,
                rommDeviceVerificationUrl = null,
                rommBrowserMissing = false,
                isConnecting = false,
                connectionError = null,
                focusedIndex = 0
            )
        }
    }

    fun cancelDevicePairing() {
        devicePollJob?.cancel()
        devicePollJob = null
        romMRepository.cancelDeviceAuth()
        _uiState.update {
            it.copy(
                rommUrlCommitted = false,
                rommDevicePairing = false,
                rommDeviceUserCode = null,
                rommDeviceVerificationUrl = null,
                rommBrowserMissing = false,
                isConnecting = false,
                focusedIndex = 0
            )
        }
    }

    fun useManualPairingCode() {
        devicePollJob?.cancel()
        devicePollJob = null
        romMRepository.cancelDeviceAuth()
        _uiState.update {
            it.copy(
                rommAuthMethod = RomMAuthMethod.PAIRING_CODE,
                rommDevicePairing = false,
                rommDeviceUserCode = null,
                rommDeviceVerificationUrl = null,
                rommBrowserMissing = false,
                isConnecting = false,
                connectionError = null,
                focusedIndex = 0
            )
        }
    }

    fun useDevicePairing() {
        val state = _uiState.value
        if (state.isConnecting || !state.rommSupportsDeviceAuth) return
        _uiState.update {
            it.copy(
                rommAuthMethod = RomMAuthMethod.DEVICE,
                connectionError = null,
                focusedIndex = 0
            )
        }
        startDevicePairing(state.rommUrl)
    }

    fun setBrowserMissing(missing: Boolean) {
        _uiState.update { it.copy(rommBrowserMissing = missing) }
    }

    private fun startDevicePairing(url: String) {
        if (url.isBlank()) {
            _uiState.update {
                it.copy(connectionError = FirstRunError(textRes = R.string.firstrun_romm_url_error_blank))
            }
            return
        }
        devicePollJob?.cancel()
        devicePollJob = viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, connectionError = null) }
            when (val init = romMRepository.beginDeviceAuth(url)) {
                is RomMResult.Success -> {
                    val data = init.data
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            rommDevicePairing = true,
                            rommDeviceUserCode = data.userCode,
                            rommDeviceVerificationUrl = data.verificationPathComplete,
                            rommBrowserMissing = false,
                            connectionError = null,
                            focusedIndex = 0
                        )
                    }
                    pollForDeviceToken(data.deviceCode, data.interval, data.expiresIn)
                }
                is RomMResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            rommUrlCommitted = false,
                            rommDevicePairing = false,
                            connectionError = FirstRunError(serverMessage = init.message),
                            focusedIndex = 0
                        )
                    }
                }
            }
        }
    }

    private suspend fun pollForDeviceToken(deviceCode: String, interval: Int, expiresIn: Int) {
        val outcome = pollDeviceAuthUntilResolved(interval, expiresIn) {
            romMRepository.pollDeviceAuthOnce(deviceCode)
        }
        if (!currentCoroutineContext().isActive) return
        when (outcome) {
            is DeviceAuthOutcome.Approved -> {
                _uiState.update {
                    it.copy(
                        rommDevicePairing = false,
                        rommDeviceUserCode = null,
                        rommDeviceVerificationUrl = null
                    )
                }
                onAuthSuccess()
            }
            DeviceAuthOutcome.Denied -> failDevicePairing(
                FirstRunError(textRes = R.string.firstrun_device_pairing_error_denied)
            )
            DeviceAuthOutcome.Expired -> failDevicePairing(
                FirstRunError(textRes = R.string.firstrun_device_pairing_error_expired)
            )
            is DeviceAuthOutcome.AddedAccount -> failDevicePairing(
                FirstRunError(textRes = R.string.firstrun_device_pairing_error_unexpected)
            )
            is DeviceAuthOutcome.Failed -> failDevicePairing(
                FirstRunError(serverMessage = outcome.message)
            )
        }
    }

    private fun failDevicePairing(error: FirstRunError) {
        romMRepository.cancelDeviceAuth()
        _uiState.update {
            it.copy(
                rommUrlCommitted = false,
                rommDevicePairing = false,
                rommDeviceUserCode = null,
                rommDeviceVerificationUrl = null,
                rommBrowserMissing = false,
                isConnecting = false,
                connectionError = error,
                focusedIndex = 0
            )
        }
    }

    private suspend fun connectWithPairingCode(url: String, rawCode: String) {
        val code = rawCode.replace("-", "").replace(" ", "")
        if (code.length != 8) {
            _uiState.update {
                it.copy(
                    isConnecting = false,
                    connectionError = FirstRunError(
                        textRes = R.string.firstrun_pairing_code_error_incomplete
                    )
                )
            }
            return
        }

        when (val result = romMRepository.exchangePairingCode(url, code)) {
            is RomMResult.Success -> onAuthSuccess()
            is RomMResult.Error -> {
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        connectionError = FirstRunError(serverMessage = result.message),
                        rommPairingCode = ""
                    )
                }
            }
        }
    }

    private suspend fun onAuthSuccess() {
        when (val summary = romMRepository.getLibrarySummary()) {
            is RomMResult.Success -> {
                val (platformCount, gameCount) = summary.data
                gamepadInputHandler.blockInputFor(300)
                _uiState.update { state ->
                    state.copy(
                        isConnecting = false,
                        currentStep = FirstRunStep.ROMM_SUCCESS,
                        focusedIndex = 0,
                        rommGameCount = gameCount,
                        rommPlatformCount = platformCount
                    )
                }
            }
            is RomMResult.Error -> {
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        connectionError = FirstRunError(
                            textRes = R.string.firstrun_romm_success_error_library_fetch,
                            arg = summary.message
                        )
                    )
                }
            }
        }
    }

    fun openFolderPicker() {
        _uiState.update { it.copy(launchFolderPicker = true) }
    }

    fun clearFolderPickerFlag() {
        _uiState.update { it.copy(launchFolderPicker = false) }
    }

    fun setStoragePath(path: String) {
        _uiState.update {
            it.copy(
                romStoragePath = path,
                folderSelected = true
            )
        }
    }

    fun proceedFromRommSuccess() {
        viewModelScope.launch { setupStateRepository.confirmLibrarySource() }
        nextStep()
    }

    fun proceedFromRomPath() {
        val state = _uiState.value
        val path = state.romStoragePath
        if (!state.folderSelected || path == null) return
        viewModelScope.launch { setupStateRepository.confirmRomFolder(path) }
        nextStep()
    }

    fun openImageCachePicker() {
        _uiState.update { it.copy(launchImageCachePicker = true) }
    }

    fun clearImageCachePickerFlag() {
        _uiState.update { it.copy(launchImageCachePicker = false) }
    }

    fun setImageCachePath(path: String) {
        _uiState.update {
            it.copy(
                imageCachePath = path,
                imageCacheFolderSelected = true
            )
        }
    }

    fun skipImageCachePath() {
        _uiState.update { it.copy(imageCacheFolderSelected = false, imageCachePath = null) }
        viewModelScope.launch { preferencesRepository.setImageCachePath(null) }
        nextStep()
    }

    fun proceedFromImageCache() {
        val path = _uiState.value.imageCachePath?.takeIf { _uiState.value.imageCacheFolderSelected }
        viewModelScope.launch { preferencesRepository.setImageCachePath(path) }
        nextStep()
    }

    fun selectSaveSync(enabled: Boolean) {
        _uiState.update {
            it.copy(
                saveSyncEnabled = enabled,
                focusedIndex = if (enabled) SAVE_SYNC_ON_INDEX else SAVE_SYNC_OFF_INDEX
            )
        }
    }

    fun proceedFromSaveSync() {
        val enabled = _uiState.value.saveSyncEnabled
        viewModelScope.launch {
            if (setupStateRepository.answerSaveSync(enabled)) nextStep()
        }
    }

    fun refreshAllPermissions() {
        setupStateRepository.refresh()
        _uiState.update { it.copy(hasOverlayPermission = Settings.canDrawOverlays(application)) }
    }

    fun proceedFromPermissions() {
        if (!_uiState.value.requiredPermissionsGranted) {
            hapticManager.vibrate(HapticPattern.BOUNDARY_HIT)
            return
        }
        nextStep()
    }

    fun completeSetup(onDone: () -> Unit = {}) {
        onDone()
    }

    fun handleConfirm(
        onRequestStorage: () -> Unit,
        onRequestNotifications: () -> Unit,
        onRequestOverlay: () -> Unit,
        onRequestUsageStats: () -> Unit,
        onChooseFolder: () -> Unit,
        onChooseImageCacheFolder: () -> Unit,
        onOpenVerificationUrl: () -> Unit,
        onChooseCertificate: () -> Unit
    ) {
        val state = _uiState.value
        when (state.currentStep) {
            FirstRunStep.WELCOME -> nextStep()
            FirstRunStep.ROMM_LOGIN -> {
                when {
                    state.rommDevicePairing -> when (state.focusedIndex) {
                        0 -> onOpenVerificationUrl()
                        1 -> useManualPairingCode()
                        2 -> cancelDevicePairing()
                    }
                    !state.rommUrlCommitted -> when (state.focusedIndex) {
                        0 -> setRommFocusField(0)
                        1 -> if (!state.isConnecting && state.rommUrl.isNotBlank()) commitUrl()
                        2 -> previousStep()
                        3 -> onChooseCertificate()
                    }
                    else -> when (state.focusedIndex) {
                        0 -> setRommFocusField(2)
                        1 -> if (!state.isConnecting && canConnect(state)) connectToRomm()
                        2 -> editUrl()
                        3 -> if (state.rommSupportsDeviceAuth) useDevicePairing()
                    }
                }
            }
            FirstRunStep.ROMM_SUCCESS -> proceedFromRommSuccess()
            FirstRunStep.PERMISSIONS -> {
                when (state.focusedIndex) {
                    0 -> if (!state.hasStoragePermission) onRequestStorage()
                    1 -> if (!state.hasNotificationPermission) onRequestNotifications()
                    2 -> if (!state.hasOverlayPermission) onRequestOverlay()
                    3 -> if (!state.hasUsageStatsPermission && state.usageStatsGrantable) onRequestUsageStats()
                    4 -> proceedFromPermissions()
                }
            }
            FirstRunStep.SAVE_SYNC -> when (state.focusedIndex) {
                SAVE_SYNC_ON_INDEX -> selectSaveSync(true)
                SAVE_SYNC_OFF_INDEX -> selectSaveSync(false)
                SAVE_SYNC_NEXT_INDEX -> proceedFromSaveSync()
            }
            FirstRunStep.ROM_PATH -> {
                if (state.folderSelected) {
                    if (state.focusedIndex == 0) proceedFromRomPath()
                    else onChooseFolder()
                } else {
                    onChooseFolder()
                }
            }
            FirstRunStep.IMAGE_CACHE -> {
                if (state.imageCacheFolderSelected) {
                    if (state.focusedIndex == 0) proceedFromImageCache()
                    else onChooseImageCacheFolder()
                } else {
                    if (state.focusedIndex == 0) onChooseImageCacheFolder()
                    else skipImageCachePath()
                }
            }
            FirstRunStep.PLATFORM_SELECT -> when {
                state.platformSortMenuOpen -> {
                    val mode = PlatformFilterLogic.SortMode.entries[state.platformSortMenuIndex]
                    _uiState.update { it.copy(platformSortMenuOpen = false) }
                    setPlatformFilterSortMode(mode)
                }
                state.platformSearchActive -> _uiState.update { it.copy(platformSearchActive = false) }
                state.platformHeaderFocused -> when (state.platformHeaderIndex) {
                    PLATFORM_HEADER_SEARCH -> _uiState.update { it.copy(platformSearchActive = true) }
                    PLATFORM_HEADER_SORT -> _uiState.update {
                        it.copy(
                            platformSortMenuOpen = true,
                            platformSortMenuIndex = PlatformFilterLogic.SortMode.entries
                                .indexOf(it.platformFilterSortMode).coerceAtLeast(0)
                        )
                    }
                    else -> cyclePlatformFilterMode()
                }
                state.focusedIndex >= state.platforms.size -> {
                    if (state.platformButtonFocus == 0) toggleAllPlatforms()
                    else proceedFromPlatformSelect()
                }
                else -> {
                    val platform = state.platforms.getOrNull(state.focusedIndex)
                    if (platform != null) togglePlatform(platform.id)
                }
            }
            FirstRunStep.CORE_PROMPT -> {
                if (state.focusedIndex == 0) nextStep()
                else skipCorePrompt()
            }
            FirstRunStep.CORE_DOWNLOAD -> {
                if (state.focusedIndex == 0 && state.coreDownloadComplete) nextStep()
                else if (state.focusedIndex == 1) skipCoreDownloads()
            }
            FirstRunStep.COMPLETE -> {}
        }
    }

    private fun canConnect(state: FirstRunUiState): Boolean {
        if (state.rommAuthMethod == RomMAuthMethod.DEVICE) return state.rommUrl.isNotBlank()
        val normalized = state.rommPairingCode.replace("-", "").replace(" ", "")
        return state.rommUrl.isNotBlank() && normalized.length == 8
    }

    fun proceedFromPlatformSelect() {
        nextStep()
    }

    fun createInputHandler(
        onComplete: () -> Unit,
        onRequestStorage: () -> Unit,
        onRequestNotifications: () -> Unit,
        onRequestOverlay: () -> Unit,
        onRequestUsageStats: () -> Unit,
        onChooseFolder: () -> Unit,
        onChooseImageCacheFolder: () -> Unit,
        onOpenVerificationUrl: () -> Unit,
        onChooseCertificate: () -> Unit
    ) = FirstRunInputHandler(
        this,
        onComplete,
        onRequestStorage,
        onRequestNotifications,
        onRequestOverlay,
        onRequestUsageStats,
        onChooseFolder,
        onChooseImageCacheFolder,
        onOpenVerificationUrl,
        onChooseCertificate
    )
}
