package com.nendo.argosy.data.music.analysis

import android.content.Context
import android.util.Log
import com.nendo.argosy.domain.model.MusicQueueTrack
import com.nendo.argosy.domain.model.MusicTrackSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Each music track's [TrackAnalysis], kept as a sidecar file in the cache, which Android never
 * backs up and may clear. A sidecar missing, unreadable or written for a different version of
 * the source is built again by decoding the track.
 */
@Singleton
class TrackAnalysisStore internal constructor(
    dirOf: () -> File,
    private val decoder: TrackPcmDecoder,
    private val scope: CoroutineScope
) {
    @Inject constructor(@ApplicationContext context: Context, decoder: TrackDecoder) : this(
        { File(context.cacheDir, DIR) },
        decoder,
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    )

    private val dir by lazy(dirOf)
    private val lock = Any()
    private val entries = HashMap<String, Entry>()
    private var prefetchIds: Set<String> = emptySet()
    private var prefetchWorker: Job? = null
    private val demandRunning = MutableStateFlow(0)

    /**
     * The analysis of [track]: the cached sidecar, or progressive snapshots during a decode and
     * then the finished analysis. Null while nothing is decoded yet and for an undecodable track.
     * Requests for one track id share a single decode.
     */
    fun analysis(track: MusicQueueTrack): StateFlow<TrackAnalysis?> {
        val entry = synchronized(lock) {
            val entry = entryFor(track)
            entry.demanded = true
            start(entry)
            entry
        }
        return entry.state.asStateFlow()
    }

    /**
     * Analyses [tracks] in the background, one at a time and only while no requested track is
     * decoding. Replaces the previous prefetch set; decodes of tracks it drops stop unless collected.
     */
    fun prefetch(tracks: List<MusicQueueTrack>) {
        synchronized(lock) {
            prefetchIds = tracks.mapTo(HashSet()) { it.id }
            entries.values.toList().forEach { entry ->
                val awaitingCollector = entry.demanded && !entry.subscribed && !entry.done
                if (!isWanted(entry) && !awaitingCollector) drop(entry)
            }
            prefetchWorker?.cancel()
            prefetchWorker = scope.launch { runPrefetch(tracks) }
        }
    }

    private suspend fun runPrefetch(tracks: List<MusicQueueTrack>) {
        for (track in tracks) {
            demandRunning.first { it == 0 }
            val job = synchronized(lock) {
                if (track.id in prefetchIds) start(entryFor(track)) else null
            } ?: continue
            job.join()
        }
    }

    private fun entryFor(track: MusicQueueTrack): Entry {
        entries[track.id]?.let { return it }
        val entry = Entry(track)
        entries[track.id] = entry
        entry.watcher = scope.launch {
            entry.state.subscriptionCount.collect { count -> onSubscribers(entry, count) }
        }
        return entry
    }

    private fun onSubscribers(entry: Entry, count: Int) {
        synchronized(lock) {
            if (count > 0) {
                entry.subscribed = true
            } else if (entry.subscribed && !isWanted(entry)) {
                drop(entry)
            }
        }
    }

    private fun start(entry: Entry): Job {
        entry.job?.let { return it }
        val counted = entry.demanded
        if (counted) demandRunning.update { it + 1 }
        val job = scope.launch { load(entry) }
        entry.job = job
        job.invokeOnCompletion {
            if (counted) demandRunning.update { it - 1 }
            synchronized(lock) {
                entry.done = true
                if (!isWanted(entry)) drop(entry)
            }
        }
        return job
    }

    private fun isWanted(entry: Entry): Boolean =
        entry.track.id in prefetchIds || entry.state.subscriptionCount.value > 0

    private fun drop(entry: Entry) {
        if (entries[entry.track.id] !== entry) return
        entries.remove(entry.track.id)
        entry.job?.cancel()
        entry.watcher?.cancel()
    }

    private suspend fun load(entry: Entry) {
        val track = entry.track
        val sidecar = File(dir, "${sha1(track.id)}.$EXTENSION")
        val key = sourceKey(track.source)
        TrackAnalysisSidecar.read(sidecar, key)?.let { cached ->
            sidecar.setLastModified(System.currentTimeMillis())
            entry.state.value = cached
            return
        }
        val built = decode(track, entry.state)
        if (built != null && TrackAnalysisSidecar.write(built, key, sidecar)) evict()
        entry.state.value = built
    }

    private suspend fun decode(track: MusicQueueTrack, state: MutableStateFlow<TrackAnalysis?>): TrackAnalysis? {
        var growing: GrowingTrackAnalysis? = null
        var lastEmitNs = 0L
        val sink = object : TrackPcmSink {
            override fun onFormat(sampleRate: Int, channels: Int): Boolean {
                growing = GrowingTrackAnalysis(sampleRate, channels)
                return true
            }

            override fun onFrames(samples: FloatArray, frameCount: Int) {
                val target = growing ?: return
                target.add(samples, frameCount)
                val now = System.nanoTime()
                if (lastEmitNs == 0L || now - lastEmitNs >= EMIT_INTERVAL_NS) {
                    lastEmitNs = now
                    state.value = target.snapshot()
                }
            }
        }
        val decoded = try {
            decoder.decode(track.source, sink)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Track analysis decode failed: ${e.javaClass.simpleName}")
            false
        }
        return if (decoded) growing?.snapshot()?.takeIf { it.frames > 0L } else null
    }

    private fun evict() {
        val files = dir.listFiles() ?: return
        val now = System.currentTimeMillis()
        files.filter { it.name.endsWith(TrackAnalysisSidecar.PARTIAL_SUFFIX) && now - it.lastModified() > STALE_PARTIAL_MS }
            .forEach { it.delete() }
        val sidecars = files.filter { it.name.endsWith(".$EXTENSION") }.sortedBy { it.lastModified() }
        var total = sidecars.sumOf { it.length() }
        for (file in sidecars) {
            if (total <= MAX_CACHE_BYTES) break
            total -= file.length()
            file.delete()
        }
    }

    private fun sourceKey(source: MusicTrackSource): Long = when (source) {
        is MusicTrackSource.Local -> File(source.path).let { file ->
            if (file.isFile) file.length() * SOURCE_KEY_PRIME + file.lastModified() else 0L
        }
        is MusicTrackSource.Remote -> 0L
    }

    private fun sha1(value: String): String =
        MessageDigest.getInstance(SHA_1).digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { HEX_FORMAT.format(it) }

    private class Entry(val track: MusicQueueTrack) {
        val state = MutableStateFlow<TrackAnalysis?>(null)
        var job: Job? = null
        var watcher: Job? = null
        var demanded = false
        var subscribed = false
        var done = false
    }

    private companion object {
        const val TAG = "TrackAnalysisStore"
        const val DIR = "music-analysis"
        const val EXTENSION = "tan"
        const val SHA_1 = "SHA-1"
        const val HEX_FORMAT = "%02x"
        const val EMIT_INTERVAL_NS = 250_000_000L
        const val STALE_PARTIAL_MS = 60_000L
        const val MAX_CACHE_BYTES = 200L * 1024L * 1024L
        const val SOURCE_KEY_PRIME = 31L
    }
}
