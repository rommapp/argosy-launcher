package com.nendo.argosy.data.setup

import com.nendo.argosy.domain.model.CoreDownloadProgress
import com.nendo.argosy.libretro.LibretroCoreManager
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The built-in emulator downloads setup hands off. They run in the background, one at a time,
 * and outlive the setup screen.
 */
@Singleton
class SetupCoreDownloads @Inject constructor(
    private val coreManager: LibretroCoreManager
) {
    private val scope = SafeCoroutineScope(Dispatchers.IO, TAG)
    private var job: Job? = null

    private val _progress = MutableStateFlow(CoreDownloadProgress())
    val progress: StateFlow<CoreDownloadProgress> = _progress.asStateFlow()

    /**
     * The built-in emulators the platforms in [platformSlugs] need and this device lacks. Reads
     * the installed cores: call it off the main thread.
     */
    fun missingCoreIds(platformSlugs: Set<String>): List<String> =
        coreManager.getMissingCoresForPlatforms(platformSlugs).map { it.coreId }

    fun start(coreIds: List<String>) {
        if (coreIds.isEmpty() || job?.isActive == true) return
        _progress.value = CoreDownloadProgress(total = coreIds.size)
        job = scope.launch {
            coreIds.forEach { coreId ->
                val result = coreManager.downloadCoreById(coreId)
                result.exceptionOrNull()?.let { Logger.warn(TAG, "Built-in emulator $coreId did not download", it) }
                _progress.update {
                    if (result.isSuccess) it.copy(completed = it.completed + 1) else it.copy(failed = it.failed + 1)
                }
            }
        }
    }

    private companion object {
        const val TAG = "SetupCoreDownloads"
    }
}
