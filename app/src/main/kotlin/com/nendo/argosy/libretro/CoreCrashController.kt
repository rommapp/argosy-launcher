package com.nendo.argosy.libretro

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.CoreVersionHistoryEntity
import com.nendo.argosy.domain.usecase.game.ConfigureEmulatorUseCase
import com.nendo.argosy.ui.components.CoreCrashOption
import com.nendo.argosy.ui.components.CoreCrashPrompt
import com.nendo.argosy.ui.components.CoreDownloadProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoreCrashController @Inject constructor(
    private val crashDetector: CoreCrashDetector,
    private val coreManager: LibretroCoreManager,
    private val gameDao: GameDao,
    private val configureEmulator: ConfigureEmulatorUseCase
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _prompt = MutableStateFlow<CoreCrashPrompt?>(null)
    val prompt: StateFlow<CoreCrashPrompt?> = _prompt.asStateFlow()

    private val _focusIndex = MutableStateFlow(0)
    val focusIndex: StateFlow<Int> = _focusIndex.asStateFlow()

    private val _downloading = MutableStateFlow<CoreDownloadProgress?>(null)
    val downloading: StateFlow<CoreDownloadProgress?> = _downloading.asStateFlow()

    private var history: List<CoreVersionHistoryEntity> = emptyList()

    suspend fun runBootDetection() {
        val crash = crashDetector.detect() ?: return
        val game = gameDao.getById(crash.gameId) ?: return
        val displayName = LibretroCoreRegistry.getCoreById(crash.coreId)?.displayName ?: crash.coreId
        history = coreManager.getCoreHistory(crash.coreId)

        val options = buildList {
            history.forEach { entry ->
                add(CoreCrashOption.Revert(entry.id, "Revert to ${relative(entry.archivedAt)}"))
            }
            LibretroCoreRegistry.getCoresForPlatform(game.platformSlug)
                .filter { it.coreId != crash.coreId }
                .forEach { alt ->
                    add(CoreCrashOption.SwitchCore(alt.coreId, "Switch ${game.platformSlug.uppercase()} to ${alt.displayName}"))
                }
            add(CoreCrashOption.Redownload())
            add(CoreCrashOption.Keep())
        }

        _focusIndex.value = 0
        _prompt.value = CoreCrashPrompt(
            coreId = crash.coreId,
            displayName = displayName,
            platformId = game.platformId,
            platformSlug = game.platformSlug,
            options = options
        )
    }

    fun moveFocus(delta: Int) {
        if (_downloading.value != null) return
        val max = ((_prompt.value?.options?.size ?: 1) - 1).coerceAtLeast(0)
        _focusIndex.value = (_focusIndex.value + delta).coerceIn(0, max)
    }

    fun setFocus(index: Int) {
        if (_downloading.value != null) return
        val max = ((_prompt.value?.options?.size ?: 1) - 1).coerceAtLeast(0)
        _focusIndex.value = index.coerceIn(0, max)
    }

    fun confirmFocused() {
        if (_downloading.value != null) {
            if (_downloading.value?.done == true) dismiss()
            return
        }
        val current = _prompt.value ?: return
        val option = current.options.getOrNull(_focusIndex.value) ?: return
        scope.launch {
            when (option) {
                is CoreCrashOption.Revert -> {
                    history.find { it.id == option.historyId }?.let {
                        coreManager.rollbackToHistory(current.coreId, it)
                    }
                    clear()
                }
                is CoreCrashOption.SwitchCore -> {
                    configureEmulator.setCoreForPlatform(current.platformId, option.targetCoreId)
                    coreManager.blockInstalledVersion(current.coreId)
                    clear()
                }
                is CoreCrashOption.Redownload -> redownload(current)
                is CoreCrashOption.Keep -> clear()
            }
        }
    }

    private suspend fun redownload(current: CoreCrashPrompt) {
        val info = LibretroCoreRegistry.getCoreById(current.coreId) ?: run { clear(); return }
        _downloading.value = CoreDownloadProgress("Downloading ${current.displayName}...", 0f)
        val result = coreManager.downloadCore(info) { fraction ->
            _downloading.value = _downloading.value?.copy(fraction = fraction)
        }
        _downloading.value = CoreDownloadProgress(
            label = if (result.isSuccess) "Downloaded ${current.displayName}" else "Download failed",
            fraction = 1f,
            done = true,
            failed = result.isFailure
        )
    }

    fun dismiss() = clear()

    private fun clear() {
        _prompt.value = null
        _focusIndex.value = 0
        _downloading.value = null
        history = emptyList()
    }

    private fun relative(instant: Instant): String {
        val elapsed = Duration.between(instant, Instant.now())
        return when {
            elapsed.toDays() >= 1 -> "${elapsed.toDays()}d ago"
            elapsed.toHours() >= 1 -> "${elapsed.toHours()}h ago"
            else -> "recently"
        }
    }
}
