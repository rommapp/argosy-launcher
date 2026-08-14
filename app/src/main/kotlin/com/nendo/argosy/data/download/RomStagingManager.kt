package com.nendo.argosy.data.download

import android.content.Context
import android.os.StatFs
import com.nendo.argosy.util.AppPaths
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.RandomAccessFile
import java.util.Properties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RomStagingManager"
private const val MANIFEST_NAME = "staging.manifest"
private const val ARCHIVE_SUBDIR = "archive"
private const val OUTPUT_SUBDIR = "out"
private const val PARTIAL_SUFFIX = ".partial"
private const val COPY_BUFFER_SIZE = 256 * 1024
private const val PROGRESS_REPORT_INTERVAL_BYTES = 4L * 1024 * 1024

enum class StagingPhase { DOWNLOADING, EXTRACTING, MOVING }

data class StagingManifest(
    val downloadId: Long,
    val gameId: Long,
    val gameTitle: String,
    val fileName: String,
    val destinationDir: String,
    val phase: StagingPhase,
    val launchRelPath: String? = null
)

class StagingArea(val root: File, val manifest: StagingManifest) {
    val archiveDir: File get() = File(root, ARCHIVE_SUBDIR)
    val outputDir: File get() = File(root, OUTPUT_SUBDIR)
    val destinationDir: File get() = File(manifest.destinationDir)
}

/**
 * App-private workspace for one rom download: the archive lands here, unpacks here, and only the
 * finished tree is carried across to the configured rom folder.
 *
 * Everything the recovery path needs is written beside the files as a manifest rather than held in
 * the queue row, so a staging directory left behind by a process death describes itself: which
 * download owns it, where its output belongs, and how far it got. A directory whose owner is no
 * longer queued is abandoned by definition and can be swept.
 *
 * One download stages at a time. A second archive would double the internal footprint and put two
 * unpack jobs on the same flash at once, which is the contention this whole path exists to avoid.
 */
@Singleton
class RomStagingManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val lock = Any()
    private var holderId: Long? = null

    val stagingRoot: File get() = AppPaths.romStagingRoot(context.filesDir)

    fun claim(downloadId: Long): Boolean = synchronized(lock) {
        val current = holderId
        if (current != null && current != downloadId) return false
        holderId = downloadId
        true
    }

    fun release(downloadId: Long) = synchronized(lock) {
        if (holderId == downloadId) holderId = null
    }

    fun internalAvailableBytes(): Long? = availableBytes(context.filesDir)

    /**
     * Free bytes on the volume holding [dir], or null when no ancestor of it can be stat'ed.
     *
     * A path Argosy cannot stat is a path whose free space is unknown, never a full one - rom
     * folders inside another app's `Android/data` raise on the directory itself and answer on a
     * parent, so the walk is what keeps the check from throwing on the devices it matters for.
     */
    fun availableBytes(dir: File): Long? {
        var probe: File? = dir
        while (probe != null) {
            val candidate: File = probe
            val result = runCatching { StatFs(candidate.absolutePath).availableBytes }.getOrNull()
            if (result != null) return result
            probe = candidate.parentFile
        }
        return null
    }

    fun open(manifest: StagingManifest): StagingArea {
        val root = AppPaths.romStagingDir(context.filesDir, manifest.downloadId)
        val area = StagingArea(root, manifest)
        area.archiveDir.mkdirs()
        area.outputDir.mkdirs()
        writeManifest(area)
        return area
    }

    fun advance(area: StagingArea, phase: StagingPhase, launchRelPath: String? = null): StagingArea {
        val next = StagingArea(
            area.root,
            area.manifest.copy(phase = phase, launchRelPath = launchRelPath ?: area.manifest.launchRelPath)
        )
        writeManifest(next)
        return next
    }

    /**
     * Drops a staging area and the half-written destination files it had already started.
     *
     * Only the `.partial` names this area's own output would produce are removed, so a discard can
     * never reach a finished rom or another download's work in the same folder.
     */
    fun discard(area: StagingArea) {
        removeOwnPartials(area)
        area.root.deleteRecursively()
        release(area.manifest.downloadId)
    }

    fun list(): List<StagingArea> {
        val roots = stagingRoot.listFiles()?.filter { it.isDirectory } ?: return emptyList()
        return roots.mapNotNull { root -> readManifest(root)?.let { StagingArea(root, it) } }
    }

    /**
     * Removes every staging directory whose download is no longer queued, plus any directory with
     * no readable manifest at all. Returns the bytes reclaimed.
     */
    fun cleanAbandoned(liveDownloadIds: Set<Long>): Long {
        val roots = stagingRoot.listFiles()?.filter { it.isDirectory } ?: return 0L
        var freed = 0L
        for (root in roots) {
            val manifest = readManifest(root)
            if (manifest != null && manifest.downloadId in liveDownloadIds) continue
            val bytes = directoryBytes(root)
            manifest?.let { removeOwnPartials(StagingArea(root, it)) }
            if (root.deleteRecursively()) {
                freed += bytes
                Logger.info(TAG, "Cleared abandoned staging | dir=${root.name} bytes=$bytes")
            } else {
                Logger.warn(TAG, "Could not clear abandoned staging | dir=${root.name}")
            }
        }
        return freed
    }

    fun stagedBytes(): Long = directoryBytes(stagingRoot)

    fun outputBytes(area: StagingArea): Long = directoryBytes(area.outputDir)

    /**
     * Carries the finished tree from [StagingArea.outputDir] into [StagingArea.destinationDir].
     *
     * Every file is written to a sibling `.partial` first and nothing is promoted until the last
     * byte of the last file has landed, so an interrupted transfer leaves a destination that is
     * either entirely the previous content or entirely the new content, and never a folder of
     * half-written roms that the library would read as an installed game. A `.partial` shorter
     * than its source is resumed from its own length, which is what lets a transfer that died
     * mid-copy continue without downloading or unpacking anything again.
     */
    suspend fun deploy(
        area: StagingArea,
        onProgress: (copiedBytes: Long, totalBytes: Long) -> Unit
    ): Boolean {
        val sources = area.outputDir.walkTopDown().filter { it.isFile }.toList()
        if (sources.isEmpty()) {
            Logger.warn(TAG, "Deploy skipped | game=${area.manifest.gameTitle} staging output empty")
            return false
        }
        val destination = area.destinationDir
        if (!destination.isDirectory && !destination.mkdirs()) {
            Logger.warn(TAG, "Deploy failed | cannot create ${destination.absolutePath}")
            return false
        }

        val totalBytes = sources.sumOf { it.length() }
        val prefixLength = area.outputDir.absolutePath.length + 1
        val partials = mutableListOf<Pair<File, File>>()
        var copied = 0L
        var lastReported = 0L

        for (source in sources) {
            val relative = source.absolutePath.substring(prefixLength)
            val target = File(destination, relative)
            val partial = File(target.parentFile, target.name + PARTIAL_SUFFIX)
            target.parentFile?.mkdirs()
            val fileBytes = resumeCopy(source, partial) { bytesInFile ->
                val done = copied + bytesInFile
                if (done - lastReported >= PROGRESS_REPORT_INTERVAL_BYTES) {
                    lastReported = done
                    onProgress(done, totalBytes)
                }
            }
            if (fileBytes == null) return false
            copied += fileBytes
            partials += partial to target
        }

        for ((partial, target) in partials) {
            if (!promote(partial, target)) {
                Logger.warn(TAG, "Deploy failed | could not promote ${target.name}")
                return false
            }
        }
        onProgress(totalBytes, totalBytes)
        return true
    }

    private suspend fun resumeCopy(source: File, partial: File, onBytes: (Long) -> Unit): Long? {
        val expected = source.length()
        val existing = if (partial.isFile) partial.length() else 0L
        if (existing == expected && expected > 0L) {
            onBytes(expected)
            return expected
        }
        val startAt = if (existing in 1 until expected) existing else 0L
        return runCatching {
            RandomAccessFile(partial, "rw").use { output ->
                output.setLength(startAt)
                output.seek(startAt)
                RandomAccessFile(source, "r").use { input ->
                    input.seek(startAt)
                    val buffer = ByteArray(COPY_BUFFER_SIZE)
                    var written = startAt
                    var read = input.read(buffer)
                    while (read > 0) {
                        currentCoroutineContext().ensureActive()
                        output.write(buffer, 0, read)
                        written += read
                        onBytes(written)
                        read = input.read(buffer)
                    }
                    output.fd.sync()
                    written
                }
            }
        }.onFailure {
            if (it is CancellationException) throw it
            Logger.warn(TAG, "Copy failed | file=${source.name}", it)
        }.getOrNull()
    }

    private fun promote(partial: File, target: File): Boolean {
        if (target.isDirectory) target.deleteRecursively()
        if (partial.renameTo(target)) return true
        return runCatching {
            partial.copyTo(target, overwrite = true)
            partial.delete()
            true
        }.getOrDefault(false)
    }

    private fun removeOwnPartials(area: StagingArea) {
        val outputDir = area.outputDir
        if (!outputDir.isDirectory) return
        val destination = area.destinationDir
        if (!destination.isDirectory) return
        val prefixLength = outputDir.absolutePath.length + 1
        outputDir.walkTopDown().filter { it.isFile }.forEach { source ->
            val relative = source.absolutePath.substring(prefixLength)
            File(destination, relative + PARTIAL_SUFFIX).takeIf { it.isFile }?.delete()
        }
    }

    private fun directoryBytes(dir: File): Long {
        if (!dir.isDirectory) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun writeManifest(area: StagingArea) {
        val properties = Properties().apply {
            setProperty("downloadId", area.manifest.downloadId.toString())
            setProperty("gameId", area.manifest.gameId.toString())
            setProperty("gameTitle", area.manifest.gameTitle)
            setProperty("fileName", area.manifest.fileName)
            setProperty("destinationDir", area.manifest.destinationDir)
            setProperty("phase", area.manifest.phase.name)
            area.manifest.launchRelPath?.let { setProperty("launchRelPath", it) }
        }
        runCatching {
            area.root.mkdirs()
            File(area.root, MANIFEST_NAME).outputStream().use { properties.store(it, null) }
        }.onFailure {
            Logger.warn(TAG, "Could not write staging manifest | dir=${area.root.name}", it)
        }
    }

    private fun readManifest(root: File): StagingManifest? {
        val file = File(root, MANIFEST_NAME)
        if (!file.isFile) return null
        return runCatching {
            val properties = Properties().apply { file.inputStream().use { load(it) } }
            StagingManifest(
                downloadId = properties.getProperty("downloadId").toLong(),
                gameId = properties.getProperty("gameId").toLong(),
                gameTitle = properties.getProperty("gameTitle").orEmpty(),
                fileName = properties.getProperty("fileName").orEmpty(),
                destinationDir = properties.getProperty("destinationDir") ?: return null,
                phase = StagingPhase.valueOf(properties.getProperty("phase")),
                launchRelPath = properties.getProperty("launchRelPath")
            )
        }.getOrNull()
    }
}
