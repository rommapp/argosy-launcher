package com.nendo.argosy.data.steam

import android.content.Context
import com.nendo.argosy.data.launcher.SteamLaunchers
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.ui.components.SteamDownloadLocationPrompt
import com.nendo.argosy.ui.components.SteamMarkOption
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SteamDownloadPromptController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao,
    private val steamContentManager: SteamContentManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _prompt = MutableStateFlow<SteamDownloadLocationPrompt?>(null)
    val prompt: StateFlow<SteamDownloadLocationPrompt?> = _prompt.asStateFlow()

    private val _markOptions = MutableStateFlow<List<SteamMarkOption>>(emptyList())
    val markOptions: StateFlow<List<SteamMarkOption>> = _markOptions.asStateFlow()

    private val _focusIndex = MutableStateFlow(0)
    val focusIndex: StateFlow<Int> = _focusIndex.asStateFlow()

    fun requestSteamDownload(gameId: Long) {
        scope.launch {
            val game = gameDao.getById(gameId) ?: return@launch
            if (game.steamAppId == null) return@launch
            _markOptions.value = SteamLaunchers.getMarkOptions(context).map {
                SteamMarkOption(it.launcherPackage, it.displayName)
            }
            _focusIndex.value = 0
            _prompt.value = SteamDownloadLocationPrompt(
                gameId = gameId,
                title = game.title,
                coverPath = game.coverPath
            )
        }
    }

    private val maxFocusIndex: Int
        get() = _markOptions.value.size  // 0 = Download via Argosy, 1..N = mark options

    fun moveFocus(delta: Int) {
        _focusIndex.value = (_focusIndex.value + delta).coerceIn(0, maxFocusIndex)
    }

    fun setFocus(index: Int) {
        _focusIndex.value = index.coerceIn(0, maxFocusIndex)
    }

    fun confirmFocused() {
        val focus = _focusIndex.value
        if (focus == 0) {
            confirmDownloadToSd()
        } else {
            val option = _markOptions.value.getOrNull(focus - 1) ?: return
            confirmMarkInstalled(option.launcherPackage)
        }
    }

    fun confirmDownloadToSd() {
        val p = _prompt.value ?: return
        scope.launch {
            val game = gameDao.getById(p.gameId) ?: return@launch
            val steamAppId = game.steamAppId ?: return@launch
            if (game.isExternallyManaged) {
                gameDao.setSteamLauncher(p.gameId, null)
            }
            steamContentManager.queueDownloadOptimistic(steamAppId, game.title, game.coverPath)
            clearPrompt()
        }
    }

    fun confirmMarkInstalled(launcherPackage: String) {
        val p = _prompt.value ?: return
        scope.launch {
            gameDao.setSteamLauncher(p.gameId, launcherPackage)
            clearPrompt()
        }
    }

    fun dismiss() {
        clearPrompt()
    }

    /** Clears the managing-launcher link for a game. Used by "Unlink from <launcher>". */
    fun unlinkLauncher(gameId: Long) {
        scope.launch { gameDao.setSteamLauncher(gameId, null) }
    }

    private fun clearPrompt() {
        _prompt.value = null
        _markOptions.value = emptyList()
        _focusIndex.value = 0
    }
}
