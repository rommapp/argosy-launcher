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
import com.nendo.argosy.data.local.dao.MediaItemDao
import com.nendo.argosy.data.local.entity.MediaItemType
import com.nendo.argosy.data.media.ActiveMediaPlayback
import com.nendo.argosy.data.media.MediaPlaybackTracker
import com.nendo.argosy.data.preferences.JellyfinPreferencesRepository
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PresenceManager @Inject constructor(
    private val application: Application,
    private val socialRepository: SocialRepository,
    private val playSessionTracker: PlaySessionTracker,
    private val mediaPlaybackTracker: MediaPlaybackTracker,
    private val preferencesRepository: UserPreferencesRepository,
    private val jellyfinPreferencesRepository: JellyfinPreferencesRepository,
    private val gameDao: GameDao,
    private val mediaItemDao: MediaItemDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var lastSentStatus: PresenceStatus? = null
    private var lastSentGameId: Int? = null
    private var lastSentTitle: String? = null
    private var lastSentMedia: MediaPresence? = null
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
            val flags = combine(
                preferencesRepository.userPreferences,
                jellyfinPreferencesRepository.preferences
            ) { prefs, jellyfinPrefs ->
                PresenceFlags(
                    onlineStatusEnabled = prefs.socialOnlineStatusEnabled,
                    showNowPlaying = prefs.socialShowNowPlaying,
                    shareMediaPresence = jellyfinPrefs.shareMediaPresence,
                    isSocialLinked = prefs.isSocialLinked,
                    socialUserId = prefs.socialUserId,
                    mediaOwnerId = jellyfinPrefs.userId
                )
            }.distinctUntilChanged()

            combine(
                playSessionTracker.activeSession,
                mediaPlaybackTracker.activePlayback,
                flags,
                socialRepository.serviceConnectionState,
                _screenOn
            ) { playSession, mediaPlayback, presenceFlags, serviceState, screenOn ->
                PresenceContext(
                    playSession = playSession,
                    mediaPlayback = mediaPlayback,
                    onlineStatusEnabled = presenceFlags.onlineStatusEnabled,
                    showNowPlaying = presenceFlags.showNowPlaying,
                    shareMediaPresence = presenceFlags.shareMediaPresence,
                    isSocialLinked = presenceFlags.isSocialLinked,
                    socialUserId = presenceFlags.socialUserId,
                    mediaOwnerId = presenceFlags.mediaOwnerId,
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
        Log.d(TAG, "updatePresence: linked=${context.isSocialLinked} connected=${context.isConnected} screenOn=${context.isScreenOn} session=${context.playSession?.gameId} media=${context.mediaPlayback?.itemId} onlineEnabled=${context.onlineStatusEnabled} nowPlaying=${context.showNowPlaying} mediaPresence=${context.shareMediaPresence}")
        if (!context.isSocialLinked) return

        if (!context.isConnected) {
            lastSentStatus = null
            lastSentGameId = null
            lastSentTitle = null
            lastSentMedia = null
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
            presenceInfo.gameIgdbId != lastSentGameId ||
            presenceInfo.gameTitle != lastSentTitle ||
            presenceInfo.media != lastSentMedia
        ) {
            Log.d(TAG, "Sending presence: ${presenceInfo.status}, title=${presenceInfo.gameTitle}, igdbId=${presenceInfo.gameIgdbId}, media=${presenceInfo.media}")
            val sent = socialRepository.sendPresence(
                presenceInfo.status,
                presenceInfo.gameIgdbId,
                presenceInfo.gameTitle,
                presenceInfo.media
            )
            if (sent) {
                lastSentStatus = presenceInfo.status
                lastSentGameId = presenceInfo.gameIgdbId
                lastSentTitle = presenceInfo.gameTitle
                lastSentMedia = presenceInfo.media
                lastSentSocialUserId = context.socialUserId
            } else {
                Log.w(TAG, "Presence send failed, will retry on next state change")
                lastSentStatus = null
                lastSentGameId = null
                lastSentTitle = null
                lastSentMedia = null
                lastSentSocialUserId = null
            }
        } else {
            Log.d(TAG, "Presence unchanged, skipping: ${presenceInfo.status}, title=${presenceInfo.gameTitle}")
        }
    }

    private data class PresenceInfo(
        val status: PresenceStatus,
        val gameIgdbId: Int?,
        val gameTitle: String?,
        val media: MediaPresence? = null
    )

    /**
     * A game always outranks media. A play session that exists, or begins while a video is open,
     * makes the status IN_GAME and it stays there until the session ends, whatever the player is
     * doing behind it. Media only reaches the wire once no play session is active.
     *
     * A media item stays WATCHING across a pause: the item is still open, and flipping presence on
     * every transport press would put a wire message behind the pause button.
     */
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

        val mediaPlayback = context.mediaPlayback
        if (mediaPlayback != null && context.showNowPlaying && context.shareMediaPresence) {
            val media = resolveMedia(context.mediaOwnerId, mediaPlayback)
            return PresenceInfo(PresenceStatus.WATCHING, null, media.title, media)
        }

        return PresenceInfo(PresenceStatus.ONLINE, null, null)
    }

    /**
     * Describes the title a cover can be found for, which is not always the one being played: an
     * episode's own artwork is a still frame, so a watching presence names the series instead.
     *
     * Every unresolved step still answers rather than giving up -- a library that has not synced
     * since the ids were added, an episode whose series row never arrived, a title no metadata
     * agent matched. What survives is the title, which is what a search needs, and that is the same
     * shape the receiver has to handle anyway.
     */
    private suspend fun resolveMedia(owner: String?, playback: ActiveMediaPlayback): MediaPresence {
        val unresolved = MediaPresence(null, playback.title, null, null, null, null)
        val ownerId = owner ?: return unresolved
        val played = mediaItemDao.getByItemId(ownerId, playback.itemId) ?: return unresolved

        val coverBearing = when (MediaItemType.fromWire(played.itemType)) {
            MediaItemType.EPISODE, MediaItemType.SEASON ->
                played.seriesId?.let { mediaItemDao.getByItemId(ownerId, it) }
                    ?: return MediaPresence(
                        kind = MediaPresenceKind.SERIES,
                        title = played.seriesName ?: playback.title,
                        year = null,
                        tmdbId = null,
                        imdbId = null,
                        tvdbId = null
                    )
            else -> played
        }

        return MediaPresence(
            kind = when (MediaItemType.fromWire(coverBearing.itemType)) {
                MediaItemType.MOVIE -> MediaPresenceKind.MOVIE
                MediaItemType.SERIES -> MediaPresenceKind.SERIES
                else -> null
            },
            title = coverBearing.name,
            year = coverBearing.productionYear,
            tmdbId = coverBearing.tmdbId,
            imdbId = coverBearing.imdbId,
            tvdbId = coverBearing.tvdbId
        )
    }

    private suspend fun getGameInfo(gameId: Long): Pair<Int?, String?>? {
        val game = gameDao.getById(gameId) ?: return null
        return game.igdbId?.toInt() to game.title
    }

    private data class PresenceFlags(
        val onlineStatusEnabled: Boolean,
        val showNowPlaying: Boolean,
        val shareMediaPresence: Boolean,
        val isSocialLinked: Boolean,
        val socialUserId: String?,
        val mediaOwnerId: String?
    )

    /**
     * [socialUserId] is part of the state, not decoration. Watching only the linked boolean
     * makes an A-to-B swap invisible -- it never changes -- and the send dedupe then suppresses
     * the first post-swap presence because the status and game are the same as the old account's.
     */
    private data class PresenceContext(
        val playSession: ActiveSession?,
        val mediaPlayback: ActiveMediaPlayback?,
        val onlineStatusEnabled: Boolean,
        val showNowPlaying: Boolean,
        val shareMediaPresence: Boolean,
        val isSocialLinked: Boolean,
        val socialUserId: String?,
        val mediaOwnerId: String?,
        val isConnected: Boolean,
        val isScreenOn: Boolean
    )

    companion object {
        private const val TAG = "PresenceManager"
        private const val RECONNECT_COOLDOWN_MS = 5_000L
    }
}
