package com.nendo.argosy.data.music.stats

import android.os.SystemClock
import com.nendo.argosy.data.emulator.ActiveSession
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.MusicGameContextDao
import com.nendo.argosy.data.local.dao.MusicListenDao
import com.nendo.argosy.data.local.entity.MusicGameContextEntity
import com.nendo.argosy.data.local.entity.MusicGameContextTagEntity
import com.nendo.argosy.data.local.entity.MusicListenArtistEntity
import com.nendo.argosy.data.local.entity.MusicListenEntity
import com.nendo.argosy.data.music.identity.MusicAccountReader
import com.nendo.argosy.data.music.navidrome.NavidromeScrobbler
import com.nendo.argosy.domain.model.MusicQueueTrack
import com.nendo.argosy.domain.model.MusicSourceKind
import com.nendo.argosy.domain.model.MusicTrackIdentity
import com.nendo.argosy.domain.music.CreditSplitter
import com.nendo.argosy.domain.music.RecordingKey
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MusicListenRecorder"
private const val FLUSH_INTERVAL_MS = 30_000L
private const val NO_CONTEXT = -1L

/**
 * Writes the listen log from the player's events. Calls are cheap and safe on the main thread;
 * all database work runs in order on a background queue. A listen row is created at the first
 * audible moment of a track that carries an identity, updated on every pause, end and 30 s while
 * audible, and attached to the running game session's context when one is active while heard.
 */
@Singleton
class MusicListenRecorder @Inject constructor(
    private val listenDao: MusicListenDao,
    private val contextDao: MusicGameContextDao,
    private val gameDao: GameDao,
    private val accountReader: MusicAccountReader,
    private val scrobbler: NavidromeScrobbler,
    private val sessionTracker: dagger.Lazy<PlaySessionTracker>
) {
    private sealed interface Event {
        val wallMs: Long
        val clockMs: Long
    }

    private class Started(
        val track: MusicQueueTrack,
        val selectionId: String?,
        val startPositionMs: Long,
        override val wallMs: Long,
        override val clockMs: Long
    ) : Event

    private class Audible(
        val audible: Boolean,
        val positionMs: Long?,
        val durationMs: Long?,
        override val wallMs: Long,
        override val clockMs: Long
    ) : Event

    private class Ended(
        val reason: MusicListenEndReason,
        val positionMs: Long?,
        val durationMs: Long?,
        override val wallMs: Long,
        override val clockMs: Long
    ) : Event

    private class Tick(override val wallMs: Long, override val clockMs: Long) : Event

    private class OpenListen(
        val identity: MusicTrackIdentity,
        val trackGameTitle: String?,
        val selectionId: String?,
        val startPositionMs: Long,
        var durationMs: Long
    ) {
        var rowId: Long? = null
        var ownerKey: Long = 0
        var listenedMs: Long = 0
        var audibleSince: Long? = null
        var anchorPositionMs: Long = startPositionMs
        var anchorListenedMs: Long = 0
        var contextId: Long? = null
        var counted: Boolean = false

        val positionMs: Long get() = anchorPositionMs + (listenedMs - anchorListenedMs)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val events = Channel<Event>(Channel.UNLIMITED)
    private val contextIds = HashMap<String, Long>()
    private var current: OpenListen? = null
    private var audible = false
    private var ticker: Job? = null

    init {
        scope.launch {
            for (event in events) {
                try {
                    handle(event)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.warn(TAG, "Listen event failed", e)
                }
            }
        }
    }

    fun onTrackStarted(track: MusicQueueTrack, selectionId: String?, startPositionMs: Long = 0L) {
        send { wall, clock -> Started(track, selectionId, startPositionMs.coerceAtLeast(0L), wall, clock) }
    }

    fun onAudibleChanged(audible: Boolean, positionMs: Long?, durationMs: Long?) {
        send { wall, clock -> Audible(audible, positionMs, durationMs, wall, clock) }
    }

    fun onTrackEnded(reason: MusicListenEndReason, positionMs: Long?, durationMs: Long?) {
        send { wall, clock -> Ended(reason, positionMs, durationMs, wall, clock) }
    }

    private inline fun send(build: (wallMs: Long, clockMs: Long) -> Event) {
        events.trySend(build(System.currentTimeMillis(), SystemClock.elapsedRealtime()))
    }

    private suspend fun handle(event: Event) {
        when (event) {
            is Started -> {
                current?.let { finish(it, MusicListenEndReason.STOPPED, null, null, event) }
                current = event.track.identity?.let { identity ->
                    OpenListen(
                        identity = identity,
                        trackGameTitle = event.track.gameTitle,
                        selectionId = event.selectionId,
                        startPositionMs = event.startPositionMs,
                        durationMs = (identity.durationSec ?: event.track.durationSec ?: 0) * 1000L
                    )
                }
                current?.takeIf { audible }?.let { resume(it, event) }
            }
            is Audible -> {
                val listen = current
                if (event.audible == audible) {
                    listen?.let {
                        accumulate(it, event.clockMs)
                        it.observe(event.positionMs, event.durationMs)
                    }
                    return
                }
                audible = event.audible
                setTicker(event.audible)
                if (listen == null) return
                if (event.audible) resume(listen, event) else pause(listen, event)
            }
            is Ended -> current?.let { finish(it, event.reason, event.positionMs, event.durationMs, event) }
            is Tick -> current?.takeIf { it.audibleSince != null }?.let { listen ->
                accumulate(listen, event.clockMs)
                attachContext(listen)
                persist(listen, event.wallMs, null)
            }
        }
    }

    private suspend fun resume(listen: OpenListen, event: Event) {
        if (event is Audible) listen.observe(event.positionMs, event.durationMs)
        if (listen.rowId == null) open(listen, event.wallMs)
        listen.audibleSince = event.clockMs
        attachContext(listen)
        persist(listen, event.wallMs, null)
    }

    private suspend fun pause(listen: OpenListen, event: Audible) {
        accumulate(listen, event.clockMs)
        listen.observe(event.positionMs, event.durationMs)
        listen.audibleSince = null
        persist(listen, event.wallMs, null)
    }

    private suspend fun finish(
        listen: OpenListen,
        reason: MusicListenEndReason,
        positionMs: Long?,
        durationMs: Long?,
        event: Event
    ) {
        current = null
        accumulate(listen, event.clockMs)
        listen.observe(positionMs, durationMs)
        listen.audibleSince = null
        persist(listen, event.wallMs, reason)
    }

    private fun OpenListen.observe(positionMs: Long?, durationMs: Long?) {
        positionMs?.takeIf { it >= 0 }?.let {
            anchorPositionMs = it
            anchorListenedMs = listenedMs
        }
        durationMs?.takeIf { it > this.durationMs }?.let { this.durationMs = it }
    }

    private fun accumulate(listen: OpenListen, clockMs: Long) {
        val since = listen.audibleSince ?: return
        listen.listenedMs += (clockMs - since).coerceAtLeast(0L)
        listen.audibleSince = clockMs
    }

    private suspend fun open(listen: OpenListen, wallMs: Long) {
        val identity = listen.identity
        val ownerKey = accountReader.current().ownerKey
        listen.ownerKey = ownerKey
        val credits = CreditSplitter.split(identity.artistCredit)
        val row = MusicListenEntity(
            ownerKey = ownerKey,
            startedAt = wallMs,
            endedAt = wallMs,
            localDay = MusicListenRules.localDay(wallMs, ZoneId.systemDefault()),
            listenedMs = 0,
            endPositionMs = listen.startPositionMs,
            durationMs = listen.durationMs,
            counted = false,
            endReason = null,
            source = identity.source,
            sourceAccountKey = identity.sourceAccountKey,
            sourceTrackId = identity.sourceTrackId,
            trackKey = identity.trackKey,
            recordingKey = RecordingKey.of(identity),
            albumKey = identity.albumKey,
            selectionId = listen.selectionId,
            romId = identity.romId,
            romFileId = identity.romFileId,
            isrc = identity.isrc,
            title = identity.title,
            artistCredit = identity.artistCredit,
            albumTitle = identity.albumTitle,
            trackGameTitle = listen.trackGameTitle,
            contextId = null,
            scrobbleState = MusicListenEntity.SCROBBLE_NONE,
            scrobbledAt = null
        )
        val artists = credits.mapIndexed { index, name ->
            MusicListenArtistEntity(
                listenId = 0,
                position = index,
                ownerKey = ownerKey,
                artistKey = CreditSplitter.artistKey(name),
                name = name
            )
        }
        listen.rowId = listenDao.insertWithArtists(row, artists)
        if (identity.source == MusicSourceKind.NAVIDROME.token) scrobbler.nowPlaying(identity.sourceTrackId)
    }

    private suspend fun persist(listen: OpenListen, wallMs: Long, reason: MusicListenEndReason?) {
        val id = listen.rowId ?: return
        val becameCounted = !listen.counted && MusicListenRules.isCounted(listen.listenedMs, listen.durationMs)
        if (becameCounted) listen.counted = true
        val position = listen.positionMs
        listenDao.updateProgress(
            ownerKey = listen.ownerKey,
            id = id,
            endedAt = wallMs,
            listenedMs = listen.listenedMs,
            endPositionMs = if (listen.durationMs > 0) position.coerceAtMost(listen.durationMs) else position,
            durationMs = listen.durationMs,
            counted = listen.counted,
            endReason = reason?.token,
            contextId = listen.contextId
        )
        if (becameCounted && listen.identity.source == MusicSourceKind.NAVIDROME.token) {
            scrobbler.submitCounted(listen.ownerKey, id)
        }
    }

    private suspend fun attachContext(listen: OpenListen) {
        if (listen.contextId != null || listen.rowId == null || listen.audibleSince == null) return
        val session = sessionTracker.get().activeSession.value ?: return
        listen.contextId = contextFor(listen.ownerKey, session)
    }

    private suspend fun contextFor(ownerKey: Long, session: ActiveSession): Long? {
        val startMs = session.startTime.toEpochMilli()
        val cacheKey = "$ownerKey:${session.gameId}:$startMs"
        contextIds[cacheKey]?.let { return it.takeIf { id -> id != NO_CONTEXT } }
        val game = gameDao.getById(session.gameId)
        if (game == null) {
            contextIds[cacheKey] = NO_CONTEXT
            return null
        }
        val accounts = accountReader.current()
        val context = MusicGameContextEntity(
            ownerKey = ownerKey,
            gameId = game.id,
            sessionStartTime = startMs,
            gameKey = MusicGameContexts.gameKey(game, accounts.rommServerKey),
            rommId = game.rommId,
            igdbId = game.igdbId,
            platformSlug = game.platformSlug,
            gameTitle = game.title,
            emulatorPackage = session.emulatorPackage,
            isBuiltIn = session.emulatorPackage == EmulatorRegistry.BUILTIN_PACKAGE
        )
        val tags = MusicGameContexts.tags(game).map {
            MusicGameContextTagEntity(contextId = 0, kind = it.kind.token, tagKey = it.tagKey, ownerKey = ownerKey, label = it.label)
        }
        val id = contextDao.snapshot(context, tags).takeIf { it > 0 }
        contextIds[cacheKey] = id ?: NO_CONTEXT
        return id
    }

    private fun setTicker(running: Boolean) {
        ticker?.cancel()
        ticker = if (!running) {
            null
        } else {
            scope.launch {
                while (isActive) {
                    delay(FLUSH_INTERVAL_MS)
                    send { wall, clock -> Tick(wall, clock) }
                }
            }
        }
    }
}
