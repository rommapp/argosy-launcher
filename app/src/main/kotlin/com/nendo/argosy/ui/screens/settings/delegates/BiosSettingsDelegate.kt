package com.nendo.argosy.ui.screens.settings.delegates

import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.GpuDriverManager
import com.nendo.argosy.data.local.dao.FirmwareDao
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.repository.BiosRepository
import com.nendo.argosy.ui.screens.settings.BiosFirmwareItem
import com.nendo.argosy.ui.screens.settings.BiosPlatformGroup
import com.nendo.argosy.ui.screens.settings.BiosState
import com.nendo.argosy.ui.screens.settings.DistributeResultItem
import com.nendo.argosy.ui.screens.settings.GpuDriverInfo
import com.nendo.argosy.ui.screens.settings.PlatformDistributeResult
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
import javax.inject.Inject

class BiosSettingsDelegate @Inject constructor(
    private val biosRepository: BiosRepository,
    private val firmwareDao: FirmwareDao,
    private val platformRepository: PlatformRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val gpuDriverManager: GpuDriverManager
) {
    private val _state = MutableStateFlow(BiosState())
    val state: StateFlow<BiosState> = _state.asStateFlow()

    private val _launchFolderPicker = MutableSharedFlow<Unit>()
    val launchFolderPicker: SharedFlow<Unit> = _launchFolderPicker.asSharedFlow()

    private val _launchGpuDriverFilePicker = MutableSharedFlow<Unit>()
    val launchGpuDriverFilePicker: SharedFlow<Unit> = _launchGpuDriverFilePicker.asSharedFlow()

    fun updateState(newState: BiosState) {
        _state.value = newState
    }

    fun init(scope: CoroutineScope) {
        scope.launch {
            loadBiosState()
        }
    }

    suspend fun loadBiosState() {
        val prefs = preferencesRepository.preferences.first()
        val allFirmware = firmwareDao.getAll()
        val platforms = platformRepository.getAllPlatforms()

        val platformGroups = allFirmware
            .groupBy { it.platformSlug }
            .mapNotNull { (slug, items) ->
                val platform = platforms.find { it.slug == slug }
                if (platform != null) {
                    val firmwareItems = items.map { entity ->
                        BiosFirmwareItem(
                            id = entity.id,
                            rommId = entity.rommId,
                            platformSlug = entity.platformSlug,
                            fileName = entity.fileName,
                            fileSizeBytes = entity.fileSizeBytes,
                            isDownloaded = entity.localPath != null,
                            localPath = entity.localPath
                        )
                    }
                    BiosPlatformGroup(
                        platformSlug = slug,
                        platformName = platform.name,
                        totalFiles = items.size,
                        downloadedFiles = items.count { it.localPath != null },
                        firmwareItems = firmwareItems
                    )
                } else null
            }
            .sortedBy { it.platformName }

        val totalFiles = allFirmware.size
        val downloadedFiles = allFirmware.count { it.localPath != null }

        _state.update {
            it.copy(
                platformGroups = platformGroups,
                totalFiles = totalFiles,
                downloadedFiles = downloadedFiles,
                customBiosPath = prefs.customBiosPath
            )
        }
    }

    fun togglePlatformExpanded(index: Int) {
        _state.update { state ->
            val newExpandedIndex = if (state.expandedPlatformIndex == index) -1 else index
            state.copy(expandedPlatformIndex = newExpandedIndex)
        }
    }

    fun downloadAllBios(scope: CoroutineScope) {
        scope.launch {
            _state.update { it.copy(isDownloading = true, downloadProgress = 0f) }

            val downloadedCount = biosRepository.downloadAllMissing { current, total, fileName ->
                _state.update {
                    it.copy(
                        downloadingFileName = fileName,
                        downloadProgress = if (total > 0) current.toFloat() / total else 0f
                    )
                }
            }

            _state.update {
                it.copy(
                    isDownloading = false,
                    downloadingFileName = null,
                    downloadProgress = 0f
                )
            }

            loadBiosState()
        }
    }

    fun downloadBiosForPlatform(platformSlug: String, scope: CoroutineScope) {
        scope.launch {
            _state.update { it.copy(isDownloading = true, downloadProgress = 0f) }

            val missing = firmwareDao.getMissingByPlatformSlug(platformSlug)
            var completed = 0

            for (firmware in missing) {
                _state.update {
                    it.copy(
                        downloadingFileName = firmware.fileName,
                        downloadProgress = if (missing.isNotEmpty()) completed.toFloat() / missing.size else 0f
                    )
                }

                biosRepository.downloadFirmware(firmware.rommId) { progress ->
                    _state.update {
                        val overallProgress = (completed + progress.progress) / missing.size
                        it.copy(downloadProgress = overallProgress)
                    }
                }
                completed++
            }

            _state.update {
                it.copy(
                    isDownloading = false,
                    downloadingFileName = null,
                    downloadProgress = 0f
                )
            }

            loadBiosState()
        }
    }

    fun downloadSingleBios(rommId: Long, scope: CoroutineScope) {
        scope.launch {
            val firmware = firmwareDao.getByRommId(rommId) ?: return@launch

            _state.update {
                it.copy(
                    isDownloading = true,
                    downloadingFileName = firmware.fileName,
                    downloadProgress = 0f
                )
            }

            biosRepository.downloadFirmware(rommId) { progress ->
                _state.update { it.copy(downloadProgress = progress.progress) }
            }

            _state.update {
                it.copy(
                    isDownloading = false,
                    downloadingFileName = null,
                    downloadProgress = 0f
                )
            }

            loadBiosState()
        }
    }

    fun distributeAllBios(scope: CoroutineScope) {
        scope.launch {
            _state.update { it.copy(isDistributing = true) }

            val detailedResults = biosRepository.distributeAllBiosToEmulatorsDetailed()
            val platforms = platformRepository.getAllPlatforms()

            val resultItems = detailedResults.map { result ->
                val emulatorDef = EmulatorRegistry.getById(result.emulatorId)
                val emulatorName = emulatorDef?.displayName ?: result.emulatorId

                val platformResults = result.platformResults.map { (slug, count) ->
                    val platform = platforms.find { it.slug == slug }
                    PlatformDistributeResult(
                        platformSlug = slug,
                        platformName = platform?.name ?: slug,
                        filesCopied = count
                    )
                }.sortedBy { it.platformName }

                DistributeResultItem(
                    emulatorId = result.emulatorId,
                    emulatorName = emulatorName,
                    platformResults = platformResults
                )
            }.sortedBy { it.emulatorName }

            val edenDistributed = resultItems.any { it.emulatorId == "eden" }
            val shouldPromptGpuDriver = edenDistributed &&
                gpuDriverManager.isAdrenoGpuSupported() &&
                !checkHasGpuDriverInstalled()

            _state.update {
                it.copy(
                    isDistributing = false,
                    distributeResults = resultItems,
                    showDistributeResultModal = !shouldPromptGpuDriver && resultItems.isNotEmpty(),
                    showGpuDriverPrompt = shouldPromptGpuDriver,
                    deviceGpuName = if (shouldPromptGpuDriver) gpuDriverManager.getDeviceGpu() else null,
                    gpuDriverPromptFocusIndex = 0
                )
            }

            if (shouldPromptGpuDriver) {
                fetchGpuDriverInfo(scope)
            }
        }
    }

    fun dismissDistributeResultModal() {
        _state.update {
            it.copy(
                showDistributeResultModal = false,
                distributeResults = emptyList()
            )
        }
    }

    fun openFolderPicker(scope: CoroutineScope) {
        scope.launch {
            _launchFolderPicker.emit(Unit)
        }
    }

    fun onBiosFolderSelected(path: String, scope: CoroutineScope) {
        scope.launch {
            _state.update { it.copy(customBiosPath = path, isBiosMigrating = true) }
            try {
                biosRepository.migrateToCustomPath(path)
            } finally {
                _state.update { it.copy(isBiosMigrating = false) }
                loadBiosState()
            }
        }
    }

    fun resetBiosToDefault(scope: CoroutineScope) {
        scope.launch {
            _state.update { it.copy(customBiosPath = null, biosPathActionIndex = 0, isBiosMigrating = true) }
            try {
                biosRepository.migrateToCustomPath(null)
            } finally {
                _state.update { it.copy(isBiosMigrating = false) }
                loadBiosState()
            }
        }
    }

    fun moveBiosPathActionFocus(delta: Int, hasCustomPath: Boolean): Boolean {
        val maxIndex = if (hasCustomPath) 1 else 0
        val current = _state.value.biosPathActionIndex
        val newIndex = current + delta

        return if (newIndex in 0..maxIndex) {
            _state.update { it.copy(biosPathActionIndex = newIndex) }
            true
        } else {
            false
        }
    }

    fun resetBiosPathActionFocus() {
        _state.update { it.copy(biosPathActionIndex = 0) }
    }

    fun moveActionFocus(delta: Int) {
        _state.update { state ->
            // Both buttons are shown when totalFiles > 0, allow navigation between them
            val maxIndex = if (state.totalFiles > 0) 1 else 0
            val newIndex = (state.actionIndex + delta).coerceIn(0, maxIndex)
            state.copy(actionIndex = newIndex)
        }
    }

    fun movePlatformSubFocus(delta: Int, hasDownloadButton: Boolean): Boolean {
        val maxIndex = if (hasDownloadButton) 1 else 0
        val current = _state.value.platformSubFocusIndex
        val newIndex = current + delta

        return if (newIndex in 0..maxIndex) {
            _state.update { it.copy(platformSubFocusIndex = newIndex) }
            true
        } else {
            false
        }
    }

    fun resetPlatformSubFocus() {
        _state.update { it.copy(platformSubFocusIndex = 0) }
    }

    private fun checkHasGpuDriverInstalled(): Boolean {
        val edenPackage = biosRepository.findInstalledEdenPackage() ?: return false
        return gpuDriverManager.hasInstalledDriver(edenPackage)
    }

    private fun fetchGpuDriverInfo(scope: CoroutineScope) {
        scope.launch {
            val driverInfo = gpuDriverManager.getLatestDriverInfo()
            if (driverInfo != null) {
                _state.update {
                    it.copy(
                        gpuDriverInfo = GpuDriverInfo(
                            name = driverInfo.name,
                            version = driverInfo.version
                        )
                    )
                }
            }
        }
    }

    fun installGpuDriver(scope: CoroutineScope) {
        scope.launch {
            val edenPackage = biosRepository.findInstalledEdenPackage() ?: return@launch
            val driverInfo = gpuDriverManager.getLatestDriverInfo() ?: return@launch

            _state.update {
                it.copy(
                    gpuDriverInfo = it.gpuDriverInfo?.copy(isInstalling = true)
                )
            }

            val success = gpuDriverManager.downloadAndInstallDriver(
                driverInfo = driverInfo,
                edenPackage = edenPackage,
                onProgress = { downloaded, total ->
                    val progress = if (total > 0) downloaded.toFloat() / total else 0f
                    _state.update {
                        it.copy(
                            gpuDriverInfo = it.gpuDriverInfo?.copy(installProgress = progress)
                        )
                    }
                }
            )

            _state.update {
                it.copy(
                    gpuDriverInfo = it.gpuDriverInfo?.copy(isInstalling = false),
                    showGpuDriverPrompt = false,
                    hasGpuDriverInstalled = success,
                    showDistributeResultModal = it.distributeResults.isNotEmpty()
                )
            }
        }
    }

    fun openGpuDriverFilePicker(scope: CoroutineScope) {
        scope.launch {
            _launchGpuDriverFilePicker.emit(Unit)
        }
    }

    fun installGpuDriverFromFile(filePath: String, scope: CoroutineScope) {
        scope.launch {
            val edenPackage = biosRepository.findInstalledEdenPackage() ?: return@launch

            _state.update {
                it.copy(
                    gpuDriverInfo = it.gpuDriverInfo?.copy(isInstalling = true)
                )
            }

            val success = gpuDriverManager.installDriverFromFile(filePath, edenPackage)

            _state.update {
                it.copy(
                    gpuDriverInfo = it.gpuDriverInfo?.copy(isInstalling = false),
                    showGpuDriverPrompt = false,
                    hasGpuDriverInstalled = success,
                    showDistributeResultModal = it.distributeResults.isNotEmpty()
                )
            }
        }
    }

    fun dismissGpuDriverPrompt() {
        _state.update {
            it.copy(
                showGpuDriverPrompt = false,
                gpuDriverInfo = null,
                showDistributeResultModal = it.distributeResults.isNotEmpty()
            )
        }
    }

    fun moveGpuDriverPromptFocus(delta: Int) {
        _state.update { state ->
            val newIndex = (state.gpuDriverPromptFocusIndex + delta).coerceIn(0, 2)
            state.copy(gpuDriverPromptFocusIndex = newIndex)
        }
    }
}
