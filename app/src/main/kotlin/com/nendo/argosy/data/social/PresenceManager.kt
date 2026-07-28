package com.nendo.argosy.data.social

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.nendo.argosy.data.emulator.ActiveSession
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PresenceManager @Inject constructor(
    private val application: Application,
    private val socialRepository: SocialRepository,
    private val playSessionTracker: PlaySessionTracker,
    private val preferencesRepository: UserPreferencesRepository,
    private val gameDao: GameDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var lastSentStatus: PresenceStatus? = null
    private var lastSentGameId: Int? = null
    private var lastSentSocialUserId: String? = null
    private var lastReconnectAttempt = 0L

    private val _screenOn = MutableStateFlow(true)

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d(TAG, "Screen off")
                    _screenOn.value = false
                }
                Intent.ACTION_SCREEN_ON -> {
                    Log.d(TAG, "Screen on")
                    _screenOn.value = true
                    socialRepository.reconnectIfNeeded()
                }
            }
        }
    }

    init {
        registerScreenReceiver()
        registerProcessLifecycle()
        observePresenceChanges()
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        application.registerReceiver(screenReceiver, filter)
    }

    private fun registerProcessLifecycle() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                Log.d(TAG, "App foregrounded, triggering reconnect check")
                socialRepository.reconnectIfNeeded()
            }
        })
    }

    private fun observePresenceChanges() {
        scope.launch {
            combine(
                playSessionTracker.activeSession,
                preferencesRepository.userPreferences.map { prefs ->
                    PresenceFlags(
                        onlineStatusEnabled = prefs.socialOnlineStatusEnabled,
                        showNowPlaying = prefs.socialShowNowPlaying,
                        isSocialLinked = prefs.isSocialLinked,
                        socialUserId = prefs.socialUserId
                    )
                }.distinctUntilChanged(),
                socialRepository.serviceConnectionState,
                _screenOn
            ) { playSession, flags, serviceState, screenOn ->
                PresenceContext(
                    playSession = playSession,
                    onlineStatusEnabled = flags.onlineStatusEnabled,
                    showNowPlaying = flags.showNowPlaying,
                    isSocialLinked = flags.isSocialLinked,
                    socialUserId = flags.socialUserId,
                    isConnected = serviceState is ArgosSocialService.ConnectionState.Connected,
                    isScreenOn = screenOn
                )
            }
            .collect { context ->
                updatePresence(context)
            }
        }
    }

    private suspend fun updatePresence(context: PresenceContext) {
        Log.d(TAG, "updatePresence: linked=${context.isSocialLinked} connected=${context.isConnected} screenOn=${context.isScreenOn} session=${context.playSession?.gameId} onlineEnabled=${context.onlineStatusEnabled} nowPlaying=${context.showNowPlaying}")
        if (!context.isSocialLinked) return

        if (!context.isConnected) {
            lastSentStatus = null
            lastSentGameId = null
            lastSentSocialUserId = null
            if (context.isScreenOn && context.onlineStatusEnabled) {
                val now = System.currentTimeMillis()
                if (now - lastReconnectAttempt >= RECONNECT_COOLDOWN_MS) {
                    lastReconnectAttempt = now
                    Log.d(TAG, "Connection down but presence needed, triggering reconnect")
                    socialRepository.reconnectIfNeeded()
                }
            }
            return
        }

        val presenceInfo = calculatePresence(context)

        val identityChanged = context.socialUserId != lastSentSocialUserId
        if (identityChanged ||
            presenceInfo.status != lastSentStatus ||
            presenceInfo.gameIgdbId != lastSentGameId
        ) {
            Log.d(TAG, "Sending presence: ${presenceInfo.status}, game=${presenceInfo.gameTitle}, igdbId=${presenceInfo.gameIgdbId}")
            val sent = socialRepository.sendPresence(presenceInfo.status, presenceInfo.gameIgdbId, presenceInfo.gameTitle)
            if (sent) {
                lastSentStatus = presenceInfo.status
                lastSentGameId = presenceInfo.gameIgdbId
                lastSentSocialUserId = context.socialUserId
            } else {
                Log.w(TAG, "Presence send failed, will retry on next state change")
                lastSentStatus = null
                lastSentGameId = null
                lastSentSocialUserId = null
            }
        } else {
            Log.d(TAG, "Presence unchanged, skipping: ${presenceInfo.status}, game=${presenceInfo.gameTitle}")
        }
    }

    private data class PresenceInfo(val status: PresenceStatus, val gameIgdbId: Int?, val gameTitle: String?)

    private suspend fun calculatePresence(context: PresenceContext): PresenceInfo {
        if (!context.onlineStatusEnabled || !context.isScreenOn) {
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

    private data class PresenceFlags(
        val onlineStatusEnabled: Boolean,
        val showNowPlaying: Boolean,
        val isSocialLinked: Boolean,
        val socialUserId: String?
    )

    /**
     * [socialUserId] is part of the state, not decoration. Watching only the linked boolean
     * makes an A-to-B swap invisible -- it never changes -- and the send dedupe then suppresses
     * the first post-swap presence because the status and game are the same as the old account's.
     */
    private data class PresenceContext(
        val playSession: ActiveSession?,
        val onlineStatusEnabled: Boolean,
        val showNowPlaying: Boolean,
        val isSocialLinked: Boolean,
        val socialUserId: String?,
        val isConnected: Boolean,
        val isScreenOn: Boolean
    )

    companion object {
        private const val TAG = "PresenceManager"
        private const val RECONNECT_COOLDOWN_MS = 5_000L
    }
}
