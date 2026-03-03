package com.nendo.argosy.data.social

import android.util.Log
import com.nendo.argosy.data.emulator.ActiveSession
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PresenceManager @Inject constructor(
    private val socialRepository: SocialRepository,
    private val playSessionTracker: PlaySessionTracker,
    private val preferencesRepository: UserPreferencesRepository,
    private val gameDao: GameDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var lastSentStatus: PresenceStatus? = null
    private var lastSentGameId: Int? = null

    init {
        observePresenceChanges()
    }

    private fun observePresenceChanges() {
        scope.launch {
            combine(
                playSessionTracker.activeSession,
                preferencesRepository.userPreferences.map { prefs ->
                    Triple(prefs.socialOnlineStatusEnabled, prefs.socialShowNowPlaying, prefs.isSocialLinked)
                }.distinctUntilChanged(),
                socialRepository.connectionState
            ) { playSession, prefsTriple, connectionState ->
                PresenceContext(
                    playSession = playSession,
                    onlineStatusEnabled = prefsTriple.first,
                    showNowPlaying = prefsTriple.second,
                    isSocialLinked = prefsTriple.third,
                    isConnected = connectionState is SocialConnectionState.Connected
                )
            }
            .distinctUntilChanged()
            .collect { context ->
                updatePresence(context)
            }
        }
    }

    private suspend fun updatePresence(context: PresenceContext) {
        if (!context.isSocialLinked || !context.isConnected) {
            Log.d(TAG, "Skipping presence update - not linked or not connected")
            return
        }

        val presenceInfo = calculatePresence(context)

        if (presenceInfo.status != lastSentStatus || presenceInfo.gameIgdbId != lastSentGameId) {
            Log.d(TAG, "Sending presence update: status=${presenceInfo.status}, gameIgdbId=${presenceInfo.gameIgdbId}, gameTitle=${presenceInfo.gameTitle}")
            socialRepository.sendPresence(presenceInfo.status, presenceInfo.gameIgdbId, presenceInfo.gameTitle)
            lastSentStatus = presenceInfo.status
            lastSentGameId = presenceInfo.gameIgdbId
        }
    }

    private data class PresenceInfo(val status: PresenceStatus, val gameIgdbId: Int?, val gameTitle: String?)

    private suspend fun calculatePresence(context: PresenceContext): PresenceInfo {
        if (!context.onlineStatusEnabled) {
            return PresenceInfo(PresenceStatus.OFFLINE, null, null)
        }

        val playSession = context.playSession
        if (playSession != null) {
            return if (context.showNowPlaying) {
                val gameInfo = getGameInfo(playSession.gameId)
                PresenceInfo(PresenceStatus.IN_GAME, gameInfo?.first, gameInfo?.second)
            } else {
                PresenceInfo(PresenceStatus.ONLINE, null, null)
            }
        }

        return PresenceInfo(PresenceStatus.ONLINE, null, null)
    }

    private suspend fun getGameInfo(gameId: Long): Pair<Int?, String?>? {
        val game = gameDao.getById(gameId) ?: return null
        return game.igdbId?.toInt() to game.title
    }

    private data class PresenceContext(
        val playSession: ActiveSession?,
        val onlineStatusEnabled: Boolean,
        val showNowPlaying: Boolean,
        val isSocialLinked: Boolean,
        val isConnected: Boolean
    )

    companion object {
        private const val TAG = "PresenceManager"
    }
}
