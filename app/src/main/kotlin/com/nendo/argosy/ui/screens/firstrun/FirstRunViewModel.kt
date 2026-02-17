package com.nendo.argosy.ui.screens.firstrun

import android.os.Build
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.app.Application
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.libretro.LibretroCoreManager
import com.nendo.argosy.libretro.LibretroCoreRegistry
import com.nendo.argosy.util.PermissionHelper
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
    ROM_PATH,
    IMAGE_CACHE,
    SAVE_SYNC,
    USAGE_STATS,
    PLATFORM_SELECT,
    CORE_PROMPT,
    CORE_DOWNLOAD,
    COMPLETE
}

data class CoreDownloadState(
    val coreId: String,
    val displayName: String,
    val platforms: Set<String>,
    val status: CoreDownloadStatus = CoreDownloadStatus.PENDING
)

enum class CoreDownloadStatus {
    PENDING, DOWNLOADING, COMPLETE, FAILED
}

data class FirstRunUiState(
    val currentStep: FirstRunStep = FirstRunStep.WELCOME,
    val focusedIndex: Int = 0,
    val rommUrl: String = "",
    val rommUsername: String = "",
    val rommPassword: String = "",
    val isConnecting: Boolean = false,
    val connectionError: String? = null,
    val rommGameCount: Int = 0,
    val rommPlatformCount: Int = 0,
    val romStoragePath: String? = null,
    val folderSelected: Boolean = false,
    val launchFolderPicker: Boolean = false,
    val imageCachePath: String? = null,
    val imageCacheFolderSelected: Boolean = false,
    val launchImageCachePicker: Boolean = false,
    val saveSyncEnabled: Boolean = false,
    val hasStoragePermission: Boolean = false,
    val hasUsageStatsPermission: Boolean = false,
    val rommFocusField: Int? = null,
    val platforms: List<PlatformEntity> = emptyList(),
    val platformButtonFocus: Int = 1,
    val missingCoreCount: Int = 0,
    val coreDownloads: List<CoreDownloadState> = emptyList(),
    val coreDownloadComplete: Boolean = false
)

@HiltViewModel
class FirstRunViewModel @Inject constructor(
    private val application: Application,
    private val preferencesRepository: UserPreferencesRepository,
    private val romMRepository: RomMRepository,
    private val platformRepository: PlatformRepository,
    private val permissionHelper: PermissionHelper,
    private val coreManager: LibretroCoreManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(FirstRunUiState())
    val uiState: StateFlow<FirstRunUiState> = _uiState.asStateFlow()

    fun nextStep() {
        _uiState.update { state ->
            val nextStep = when (state.currentStep) {
                FirstRunStep.WELCOME -> FirstRunStep.ROMM_LOGIN
                FirstRunStep.ROMM_LOGIN -> FirstRunStep.ROMM_SUCCESS
                FirstRunStep.ROMM_SUCCESS -> FirstRunStep.ROM_PATH
                FirstRunStep.ROM_PATH -> FirstRunStep.IMAGE_CACHE
                FirstRunStep.IMAGE_CACHE -> FirstRunStep.SAVE_SYNC
                FirstRunStep.SAVE_SYNC -> FirstRunStep.USAGE_STATS
                FirstRunStep.USAGE_STATS -> {
                    if (state.rommPlatformCount > 10) FirstRunStep.PLATFORM_SELECT
                    else FirstRunStep.CORE_PROMPT
                }
                FirstRunStep.PLATFORM_SELECT -> FirstRunStep.CORE_PROMPT
                FirstRunStep.CORE_PROMPT -> FirstRunStep.CORE_DOWNLOAD
                FirstRunStep.CORE_DOWNLOAD -> FirstRunStep.COMPLETE
                FirstRunStep.COMPLETE -> FirstRunStep.COMPLETE
            }
            val initialFocus = if (nextStep == FirstRunStep.IMAGE_CACHE) 1 else 0
            state.copy(currentStep = nextStep, focusedIndex = initialFocus)
        }
        when (_uiState.value.currentStep) {
            FirstRunStep.PLATFORM_SELECT -> loadPlatformsForSelection()
            FirstRunStep.CORE_PROMPT -> checkMissingCores()
            FirstRunStep.CORE_DOWNLOAD -> prepareCoreDownloads()
            else -> {}
        }
    }

    fun previousStep() {
        _uiState.update { state ->
            val prevStep = when (state.currentStep) {
                FirstRunStep.WELCOME -> FirstRunStep.WELCOME
                FirstRunStep.ROMM_LOGIN -> FirstRunStep.WELCOME
                FirstRunStep.ROMM_SUCCESS -> FirstRunStep.ROMM_LOGIN
                FirstRunStep.ROM_PATH -> FirstRunStep.ROMM_SUCCESS
                FirstRunStep.IMAGE_CACHE -> FirstRunStep.ROM_PATH
                FirstRunStep.SAVE_SYNC -> FirstRunStep.IMAGE_CACHE
                FirstRunStep.USAGE_STATS -> FirstRunStep.SAVE_SYNC
                FirstRunStep.PLATFORM_SELECT -> FirstRunStep.USAGE_STATS
                FirstRunStep.CORE_PROMPT -> {
                    if (state.rommPlatformCount > 10) FirstRunStep.PLATFORM_SELECT
                    else FirstRunStep.USAGE_STATS
                }
                FirstRunStep.CORE_DOWNLOAD -> FirstRunStep.CORE_PROMPT
                FirstRunStep.COMPLETE -> FirstRunStep.CORE_DOWNLOAD
            }
            val initialFocus = if (prevStep == FirstRunStep.IMAGE_CACHE) 1 else 0
            state.copy(currentStep = prevStep, focusedIndex = initialFocus)
        }
    }

    private fun loadPlatformsForSelection() {
        viewModelScope.launch {
            when (val result = romMRepository.fetchAndStorePlatforms(defaultSyncEnabled = false)) {
                is RomMResult.Success -> {
                    _uiState.update { it.copy(platforms = result.data) }
                }
                is RomMResult.Error -> {
                    val platforms = platformRepository.observeAllPlatforms().first()
                    _uiState.update { it.copy(platforms = platforms) }
                }
            }
        }
    }

    private fun checkMissingCores() {
        val enabledPlatforms = _uiState.value.platforms
            .filter { it.syncEnabled }
            .map { it.slug }
            .toSet()

        val missingCores = coreManager.getMissingCoresForPlatforms(enabledPlatforms)
        _uiState.update { it.copy(missingCoreCount = missingCores.size) }
    }

    fun skipCorePrompt() {
        _uiState.update { state ->
            state.copy(currentStep = FirstRunStep.COMPLETE, focusedIndex = 0)
        }
    }

    private fun prepareCoreDownloads() {
        val enabledPlatforms = _uiState.value.platforms
            .filter { it.syncEnabled }
            .map { it.slug }
            .toSet()

        val missingCores = coreManager.getMissingCoresForPlatforms(enabledPlatforms)
        val coreDownloads = missingCores.map { core ->
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

                _uiState.update { state ->
                    val updatedCores = state.coreDownloads.map {
                        if (it.coreId == core.coreId) it.copy(status = newStatus)
                        else it
                    }
                    val allComplete = updatedCores.all { it.status == CoreDownloadStatus.COMPLETE || it.status == CoreDownloadStatus.FAILED }
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
                        if (it.coreId == coreId) it.copy(status = CoreDownloadStatus.DOWNLOADING)
                        else it
                    },
                    coreDownloadComplete = false
                )
            }

            val result = coreManager.downloadCoreById(coreId)
            val newStatus = if (result.isSuccess) CoreDownloadStatus.COMPLETE else CoreDownloadStatus.FAILED

            _uiState.update { state ->
                val updatedCores = state.coreDownloads.map {
                    if (it.coreId == coreId) it.copy(status = newStatus)
                    else it
                }
                val allComplete = updatedCores.all { it.status == CoreDownloadStatus.COMPLETE || it.status == CoreDownloadStatus.FAILED }
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
            val platform = _uiState.value.platforms.find { it.id == platformId } ?: return@launch
            platformRepository.updateSyncEnabled(platformId, !platform.syncEnabled)
            val updatedPlatforms = platformRepository.observeAllPlatforms().first()
            _uiState.update { it.copy(platforms = updatedPlatforms) }
        }
    }

    fun toggleAllPlatforms() {
        viewModelScope.launch {
            val platforms = _uiState.value.platforms
            val allEnabled = platforms.all { it.syncEnabled }
            val newState = !allEnabled
            platforms.forEach { platform ->
                platformRepository.updateSyncEnabled(platform.id, newState)
            }
            val updatedPlatforms = platformRepository.observeAllPlatforms().first()
            _uiState.update { it.copy(platforms = updatedPlatforms) }
        }
    }

    fun getMaxFocusIndex(): Int {
        val state = _uiState.value
        return when (state.currentStep) {
            FirstRunStep.WELCOME -> 0
            FirstRunStep.ROMM_LOGIN -> 4
            FirstRunStep.ROMM_SUCCESS -> 0
            FirstRunStep.ROM_PATH -> {
                when {
                    !state.hasStoragePermission -> 0
                    state.folderSelected -> 1
                    else -> 0
                }
            }
            FirstRunStep.IMAGE_CACHE -> 1
            FirstRunStep.SAVE_SYNC -> 1
            FirstRunStep.USAGE_STATS -> if (state.hasUsageStatsPermission) 0 else 1
            FirstRunStep.PLATFORM_SELECT -> state.platforms.size
            FirstRunStep.CORE_PROMPT -> 1
            FirstRunStep.CORE_DOWNLOAD -> 1
            FirstRunStep.COMPLETE -> 0
        }
    }

    fun moveFocus(delta: Int): Boolean {
        val state = _uiState.value
        val maxIndex = getMaxFocusIndex()
        val newIndex = (state.focusedIndex + delta).coerceIn(0, maxIndex)
        if (newIndex == state.focusedIndex) return false
        _uiState.update { it.copy(focusedIndex = newIndex) }
        return true
    }

    fun moveButtonFocus(delta: Int): Boolean {
        val state = _uiState.value
        if (state.currentStep != FirstRunStep.PLATFORM_SELECT) return false
        if (state.focusedIndex < state.platforms.size) return false
        val newIndex = (state.platformButtonFocus + delta).coerceIn(0, 1)
        if (newIndex == state.platformButtonFocus) return false
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

    fun setRommUsername(username: String) {
        _uiState.update { it.copy(rommUsername = username, connectionError = null) }
    }

    fun setRommPassword(password: String) {
        _uiState.update { it.copy(rommPassword = password, connectionError = null) }
    }

    fun connectToRomm() {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, connectionError = null) }

            val url = _uiState.value.rommUrl
            val username = _uiState.value.rommUsername
            val password = _uiState.value.rommPassword

            val connectResult = romMRepository.connect(url)
            if (connectResult is RomMResult.Error) {
                _uiState.update { state ->
                    state.copy(
                        isConnecting = false,
                        connectionError = "Could not connect to server: ${connectResult.message}"
                    )
                }
                return@launch
            }

            val workingUrl = (connectResult as RomMResult.Success).data
            _uiState.update { it.copy(rommUrl = workingUrl.trimEnd('/')) }

            val loginResult = romMRepository.login(username, password)
            when (loginResult) {
                is RomMResult.Success -> {
                    when (val summary = romMRepository.getLibrarySummary()) {
                        is RomMResult.Success -> {
                            val (platformCount, gameCount) = summary.data
                            _uiState.update { state ->
                                state.copy(
                                    isConnecting = false,
                                    currentStep = FirstRunStep.ROMM_SUCCESS,
                                    rommGameCount = gameCount,
                                    rommPlatformCount = platformCount
                                )
                            }
                        }
                        is RomMResult.Error -> {
                            _uiState.update { state ->
                                state.copy(
                                    isConnecting = false,
                                    connectionError = "Failed to fetch library: ${summary.message}"
                                )
                            }
                        }
                    }
                }
                is RomMResult.Error -> {
                    _uiState.update { state ->
                        state.copy(
                            isConnecting = false,
                            connectionError = "Login failed: ${loginResult.message}"
                        )
                    }
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

    fun proceedFromRomPath() {
        val state = _uiState.value
        if (state.hasStoragePermission && state.folderSelected) {
            nextStep()
        }
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
        nextStep()
    }

    fun proceedFromImageCache() {
        nextStep()
    }

    fun enableSaveSync() {
        _uiState.update { it.copy(saveSyncEnabled = true) }
        nextStep()
    }

    fun skipSaveSync() {
        _uiState.update { it.copy(saveSyncEnabled = false) }
        nextStep()
    }

    fun checkUsageStatsPermission() {
        val hasPermission = permissionHelper.hasUsageStatsPermission(application)
        _uiState.update { it.copy(hasUsageStatsPermission = hasPermission) }
    }

    fun proceedFromUsageStats() {
        nextStep()
    }

    fun skipUsageStats() {
        nextStep()
    }

    fun completeSetup() {
        val state = _uiState.value

        viewModelScope.launch {
            if (state.hasStoragePermission && state.folderSelected) {
                state.romStoragePath?.let { path ->
                    preferencesRepository.setRomStoragePath(path)
                }
                if (state.imageCacheFolderSelected) {
                    state.imageCachePath?.let { path ->
                        preferencesRepository.setImageCachePath(path)
                    }
                }
                preferencesRepository.setSaveSyncEnabled(state.saveSyncEnabled)
            }
            preferencesRepository.setFirstRunComplete()
        }
    }

    fun checkStoragePermission() {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
        _uiState.update { it.copy(hasStoragePermission = hasPermission) }
    }

    fun onStoragePermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasStoragePermission = granted) }
    }

    fun handleConfirm(
        onRequestPermission: () -> Unit,
        onChooseFolder: () -> Unit,
        onChooseImageCacheFolder: () -> Unit,
        onRequestUsageStats: () -> Unit
    ) {
        val state = _uiState.value
        when (state.currentStep) {
            FirstRunStep.WELCOME -> nextStep()
            FirstRunStep.ROMM_LOGIN -> {
                when (state.focusedIndex) {
                    in 0..2 -> setRommFocusField(state.focusedIndex)
                    3 -> if (!state.isConnecting && state.rommUrl.isNotBlank()) connectToRomm()
                    4 -> previousStep()
                }
            }
            FirstRunStep.ROMM_SUCCESS -> nextStep()
            FirstRunStep.ROM_PATH -> {
                if (!state.hasStoragePermission) {
                    onRequestPermission()
                } else if (state.folderSelected) {
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
            FirstRunStep.SAVE_SYNC -> {
                if (state.focusedIndex == 0) enableSaveSync() else skipSaveSync()
            }
            FirstRunStep.USAGE_STATS -> {
                if (state.focusedIndex == 0) {
                    if (!state.hasUsageStatsPermission) {
                        onRequestUsageStats()
                    } else {
                        proceedFromUsageStats()
                    }
                } else {
                    skipUsageStats()
                }
            }
            FirstRunStep.PLATFORM_SELECT -> {
                if (state.focusedIndex >= state.platforms.size) {
                    if (state.platformButtonFocus == 0) toggleAllPlatforms()
                    else proceedFromPlatformSelect()
                } else {
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

    fun proceedFromPlatformSelect() {
        nextStep()
    }

    fun createInputHandler(
        onComplete: () -> Unit,
        onRequestPermission: () -> Unit,
        onChooseFolder: () -> Unit,
        onChooseImageCacheFolder: () -> Unit,
        onRequestUsageStats: () -> Unit
    ) = FirstRunInputHandler(this, onComplete, onRequestPermission, onChooseFolder, onChooseImageCacheFolder, onRequestUsageStats)
}
