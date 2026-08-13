package com.nendo.argosy.ui.screens.player

import com.nendo.argosy.data.remote.jellyfin.JellyfinApiClient
import com.nendo.argosy.data.remote.jellyfin.JellyfinPlaybackProgressInfo
import com.nendo.argosy.data.remote.jellyfin.JellyfinPlaybackStartInfo
import com.nendo.argosy.data.remote.jellyfin.JellyfinPlaybackStopInfo
import com.nendo.argosy.data.remote.jellyfin.PROGRESS_EVENT_PAUSE
import com.nendo.argosy.data.remote.jellyfin.PROGRESS_EVENT_TIME_UPDATE
import com.nendo.argosy.data.remote.jellyfin.PROGRESS_EVENT_UNPAUSE
import com.nendo.argosy.data.remote.jellyfin.PLAY_METHOD_TRANSCODE
import com.nendo.argosy.data.remote.jellyfin.TICKS_PER_MILLISECOND
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "PlayerSessionReporter"
private const val PROGRESS_INTERVAL_MS = 10_000L

/**
 * Tells the server what this client is doing with the stream it opened.
 *
 * The three reports are not telemetry. The server allocates an encoder against the play session on
 * start and only frees it when a stop arrives, so a playback that ends without one leaves an ffmpeg
 * process alive until the server's own timeout - the single most common way a client makes a
 * Jellyfin install unusable for everyone else on it. [stop] is therefore idempotent and is called
 * from every exit the player has, including the ones that are not a user decision.
 *
 * It runs on a scope of its own rather than the caller's. A stop issued while the screen is being
 * torn down has to outlive the thing that issued it, and a scope that dies with the caller cancels
 * the one request that must not be missed.
 */
class PlayerSessionReporter @Inject constructor(
    private val apiClient: JellyfinApiClient
) {

    private data class OpenSession(
        val itemId: String,
        val mediaSourceId: String,
        val playSessionId: String?,
        val playMethod: String,
        val audioStreamIndex: Int?,
        val subtitleStreamIndex: Int?
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var session: OpenSession? = null
    private var progressJob: Job? = null
    private var lastPositionMs: Long = 0
    private var lastPaused: Boolean = false

    @Suppress("LongParameterList")
    fun start(
        itemId: String,
        mediaSourceId: String,
        playSessionId: String?,
        playMethod: String,
        positionMs: Long,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?
    ) {
        stop(lastPositionMs)
        val open = OpenSession(
            itemId = itemId,
            mediaSourceId = mediaSourceId,
            playSessionId = playSessionId,
            playMethod = playMethod,
            audioStreamIndex = audioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex
        )
        session = open
        lastPositionMs = positionMs
        lastPaused = false

        scope.launch {
            apiClient.reportPlaybackStart(
                JellyfinPlaybackStartInfo(
                    itemId = open.itemId,
                    mediaSourceId = open.mediaSourceId,
                    playSessionId = open.playSessionId,
                    positionTicks = positionMs * TICKS_PER_MILLISECOND,
                    playMethod = open.playMethod,
                    audioStreamIndex = open.audioStreamIndex,
                    subtitleStreamIndex = open.subtitleStreamIndex
                )
            )
        }
        startProgressLoop()
        Logger.info(TAG, "session opened for ${open.itemId}")
    }

    fun setPosition(positionMs: Long, isPaused: Boolean) {
        lastPositionMs = positionMs
        lastPaused = isPaused
    }

    /**
     * Reports a transport change the moment it happens rather than waiting for the next tick, so a
     * server deciding whether an encoder is still needed is not working from a ten-second-old
     * picture of a paused client.
     */
    fun reportTransport(positionMs: Long, isPaused: Boolean) {
        setPosition(positionMs, isPaused)
        val open = session ?: return
        val event = if (isPaused) PROGRESS_EVENT_PAUSE else PROGRESS_EVENT_UNPAUSE
        scope.launch { sendProgress(open, positionMs, isPaused, event) }
    }

    fun stop(positionMs: Long) {
        val open = session ?: return
        session = null
        progressJob?.cancel()
        progressJob = null
        val finalPosition = positionMs.coerceAtLeast(0)
        scope.launch {
            apiClient.reportPlaybackStopped(
                JellyfinPlaybackStopInfo(
                    itemId = open.itemId,
                    mediaSourceId = open.mediaSourceId,
                    playSessionId = open.playSessionId,
                    positionTicks = finalPosition * TICKS_PER_MILLISECOND
                )
            )
            killEncoder(open)
        }
        Logger.info(TAG, "session closed for ${open.itemId} at ${finalPosition}ms")
    }

    /**
     * The last thing the player can do on the way out. The scope is deliberately left running: the
     * stop request it just launched is the one report that must not be cancelled, and an idle scope
     * with no work in it goes away with this object.
     */
    fun release() {
        stop(lastPositionMs)
    }

    /**
     * The encoder does not always go away on a stop report alone - a session the server has already
     * lost track of keeps its process. This is the explicit kill, and it only applies where there is
     * something to kill.
     */
    private suspend fun killEncoder(open: OpenSession) {
        if (open.playMethod != PLAY_METHOD_TRANSCODE) return
        val playSessionId = open.playSessionId ?: return
        apiClient.stopActiveEncoding(playSessionId)
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                delay(PROGRESS_INTERVAL_MS)
                val open = session ?: break
                sendProgress(open, lastPositionMs, lastPaused, PROGRESS_EVENT_TIME_UPDATE)
            }
        }
    }

    private suspend fun sendProgress(
        open: OpenSession,
        positionMs: Long,
        isPaused: Boolean,
        event: String
    ) {
        apiClient.reportPlaybackProgress(
            JellyfinPlaybackProgressInfo(
                itemId = open.itemId,
                mediaSourceId = open.mediaSourceId,
                playSessionId = open.playSessionId,
                positionTicks = positionMs.coerceAtLeast(0) * TICKS_PER_MILLISECOND,
                isPaused = isPaused,
                playMethod = open.playMethod,
                audioStreamIndex = open.audioStreamIndex,
                subtitleStreamIndex = open.subtitleStreamIndex,
                eventName = event
            )
        )
    }
}
