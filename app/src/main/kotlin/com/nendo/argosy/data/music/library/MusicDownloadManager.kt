package com.nendo.argosy.data.music.library

import android.content.Context
import android.util.Log
import com.nendo.argosy.data.local.ALauncherDatabase
import com.nendo.argosy.data.local.dao.MusicDownloadDao
import com.nendo.argosy.data.local.entity.MusicDownloadEntity
import com.nendo.argosy.data.remote.romm.RomMMusicLibraryService
import com.nendo.argosy.data.remote.ssl.ClientCertStore
import com.nendo.argosy.data.remote.ssl.UserCertStore
import com.nendo.argosy.data.remote.ssl.UserCertTrustManager.withUserCertTrust
import com.nendo.argosy.domain.model.MusicArt
import com.nendo.argosy.domain.model.MusicTrack
import com.nendo.argosy.domain.model.MusicTrackSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MusicDownloads"
private const val MAX_CONCURRENT_DOWNLOADS = 2
private const val CONNECT_TIMEOUT_SECONDS = 15L
private const val READ_TIMEOUT_SECONDS = 60L
private const val DOWNLOAD_DIR = "music"
private const val TRACK_DIR = "tracks"
private const val ART_DIR = "art"
private const val PART_SUFFIX = ".part"

/**
 * Saves Music tracks and their covers to app-specific storage, two at a time. Each download
 * writes a `.part` file and renames it on completion; [revision] bumps on every row change.
 */
@Singleton
class MusicDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: ALauncherDatabase,
    private val sources: MusicSourceRegistry,
    private val scopeReader: MusicDownloadScopeReader,
    private val userCertStore: UserCertStore,
    private val clientCertStore: ClientCertStore
) {
    private data class DownloadSlot(val serverKey: String, val trackKey: String) {
        val id: String get() = "$serverKey|$trackKey"
    }

    private val dao: MusicDownloadDao get() = database.musicDownloadDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val permits = Semaphore(MAX_CONCURRENT_DOWNLOADS)
    private val lock = Mutex()
    private val jobs = mutableMapOf<DownloadSlot, Job>()

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .withUserCertTrust(userCertStore, clientCertStore)
            .build()
    }

    init {
        scope.launch { resumeInterrupted() }
    }

    suspend fun enqueue(tracks: List<MusicTrack>) = withContext(Dispatchers.IO) {
        val downloadScope = scopeReader.current()
        lock.withLock {
            tracks.distinctBy { it.key }.groupBy { it.source }.forEach { (kind, group) ->
                val serverKey = downloadScope.servers.keyFor(kind)
                if (serverKey == null) {
                    Log.w(TAG, "Skipped ${group.size} ${kind.token} tracks: no server signed in")
                    return@forEach
                }
                val existing = dao.getByKeys(listOf(serverKey), group.map { it.key }).associateBy { it.trackKey }
                group.filter { track ->
                    existing[track.key].let { it == null || it.status == MusicDownloadStatus.FAILED.name }
                }.forEach { track ->
                    dao.upsert(track.toQueuedRow(serverKey, downloadScope.ownerKey))
                    start(track, DownloadSlot(serverKey, track.key))
                }
            }
        }
        bump()
    }

    suspend fun remove(tracks: List<MusicTrack>) = withContext(Dispatchers.IO) {
        val servers = scopeReader.current().servers
        lock.withLock {
            tracks.forEach { track ->
                val serverKey = servers.keyFor(track.source) ?: return@forEach
                val slot = DownloadSlot(serverKey, track.key)
                jobs.remove(slot)?.cancel()
                val row = dao.get(serverKey, track.key) ?: return@forEach
                row.filePath?.let { File(it).delete() }
                File(trackDir(), partName(slot)).delete()
                dao.delete(serverKey, track.key)
                row.artLocalPath
                    ?.takeIf { isOwnedArt(it) && dao.countWithArt(it) == 0 }
                    ?.let { File(it).delete() }
            }
        }
        bump()
    }

    private fun start(track: MusicTrack, slot: DownloadSlot) {
        jobs[slot]?.cancel()
        jobs[slot] = scope.launch {
            permits.withPermit { run(track, slot) }
            lock.withLock { if (jobs[slot] === coroutineContext[Job]) jobs.remove(slot) }
        }
    }

    private suspend fun run(track: MusicTrack, slot: DownloadSlot) {
        val part = File(trackDir(), partName(slot))
        var produced: File? = null
        try {
            val row = dao.get(slot.serverKey, slot.trackKey) ?: return
            if (!scopeReader.servers.first().matches(row, track.key)) return
            dao.updateStatus(slot.serverKey, slot.trackKey, MusicDownloadStatus.DOWNLOADING.name)
            bump()
            val stream = sources.of(track.source).streamSource(track)
                ?: throw IOException("No stream for track")
            val (file, size) = fetchTrack(slot, stream, part)
            produced = file
            currentCoroutineContext().ensureActive()
            val artPath = saveArt(track.art, stream)
            dao.markDone(
                serverKey = slot.serverKey,
                trackKey = slot.trackKey,
                status = MusicDownloadStatus.DONE.name,
                filePath = file.absolutePath,
                sizeBytes = size,
                artLocalPath = artPath,
                downloadedAt = Instant.now()
            )
        } catch (e: CancellationException) {
            part.delete()
            produced?.delete()
            throw e
        } catch (e: Exception) {
            part.delete()
            produced?.delete()
            Log.w(TAG, "Download failed for ${track.source.token} track: ${e.javaClass.simpleName}")
            if (dao.get(slot.serverKey, slot.trackKey) != null) {
                dao.updateStatus(slot.serverKey, slot.trackKey, MusicDownloadStatus.FAILED.name)
            }
        }
        bump()
    }

    private fun fetchTrack(
        slot: DownloadSlot,
        stream: MusicTrackSource.Remote,
        part: File
    ): Pair<File, Long> {
        part.parentFile?.mkdirs()
        val request = Request.Builder().url(stream.url).apply {
            stream.headers.forEach { (name, value) -> header(name, value) }
        }.build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty body")
            val extension = extensionFor(body.contentType()?.let { "${it.type}/${it.subtype}" })
            body.byteStream().use { input -> part.outputStream().use { input.copyTo(it) } }
            val target = File(trackDir(), "${digest(slot.id)}.$extension")
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) throw IOException("Rename failed")
            return target to target.length()
        }
    }

    private fun saveArt(art: MusicArt?, stream: MusicTrackSource.Remote): String? = when (art) {
        null -> null
        is MusicArt.Local -> art.path.takeIf { File(it).isFile }
        is MusicArt.Remote -> runCatching { fetchArt(art, stream) }.getOrNull()
    }

    private fun fetchArt(art: MusicArt.Remote, stream: MusicTrackSource.Remote): String? {
        val target = File(artDir(), digest(art.cacheKey))
        if (target.isFile && target.length() > 0) return target.absolutePath
        target.parentFile?.mkdirs()
        val headers = if (RomMMusicLibraryService.sameOrigin(art.url, stream.url)) stream.headers else emptyMap()
        val request = Request.Builder().url(art.url).apply {
            headers.forEach { (name, value) -> header(name, value) }
        }.build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            val part = File(artDir(), "${target.name}$PART_SUFFIX")
            body.byteStream().use { input -> part.outputStream().use { input.copyTo(it) } }
            if (!part.renameTo(target)) {
                part.delete()
                return null
            }
            return target.absolutePath
        }
    }

    private suspend fun resumeInterrupted() {
        val pending = dao.getByStatuses(
            scopeReader.current().servers.keys,
            listOf(MusicDownloadStatus.QUEUED.name, MusicDownloadStatus.DOWNLOADING.name)
        )
        if (pending.isEmpty()) return
        lock.withLock {
            pending.forEach { row ->
                MusicDownloadAlbums.trackFrom(row)?.let { start(it, DownloadSlot(row.serverKey, row.trackKey)) }
            }
        }
        bump()
    }

    private fun MusicTrack.toQueuedRow(serverKey: String, ownerKey: Long) = MusicDownloadEntity(
        trackKey = key,
        serverKey = serverKey,
        ownerKey = ownerKey,
        source = source.token,
        trackId = id,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        trackNumber = trackNumber,
        durationSec = durationSec,
        year = year,
        genre = genre,
        artCacheKey = (art as? MusicArt.Remote)?.cacheKey,
        artLocalPath = (art as? MusicArt.Local)?.path,
        filePath = null,
        status = MusicDownloadStatus.QUEUED.name,
        sizeBytes = null,
        downloadedAt = null
    )

    private fun bump() = _revision.update { it + 1 }

    private fun rootDir(): File = File(context.getExternalFilesDir(null) ?: context.filesDir, DOWNLOAD_DIR)

    private fun trackDir(): File = File(rootDir(), TRACK_DIR)

    private fun artDir(): File = File(rootDir(), ART_DIR)

    private fun isOwnedArt(path: String): Boolean = File(path).parentFile?.absolutePath == artDir().absolutePath

    private fun partName(slot: DownloadSlot): String = "${digest(slot.id)}$PART_SUFFIX"

    private fun digest(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun extensionFor(contentType: String?): String = when (contentType?.lowercase()) {
        "audio/mpeg", "audio/mp3" -> "mp3"
        "audio/flac", "audio/x-flac" -> "flac"
        "audio/ogg", "application/ogg" -> "ogg"
        "audio/opus" -> "opus"
        "audio/mp4", "audio/m4a", "audio/x-m4a", "audio/aac" -> "m4a"
        "audio/wav", "audio/x-wav", "audio/wave" -> "wav"
        else -> "audio"
    }
}
