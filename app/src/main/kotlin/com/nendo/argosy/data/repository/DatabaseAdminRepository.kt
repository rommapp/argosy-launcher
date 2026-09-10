package com.nendo.argosy.data.repository

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.download.MediaDownloadManager
import com.nendo.argosy.data.emulator.EmulatorDownloadManager
import com.nendo.argosy.data.local.ALauncherDatabase
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.SessionStateStore
import com.nendo.argosy.data.social.SocialRepository
import com.nendo.argosy.data.steam.SteamContentManager
import com.nendo.argosy.data.storage.StorageAttributionRepository
import com.nendo.argosy.data.storage.StorageCategory
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.util.AppPaths
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DatabaseAdminRepository"

/**
 * Why a hard reset refused to run.
 *
 * [PendingUploads] names the accounts still holding unsent saves rather than reporting a bare
 * count, because on a shared device the person asking for the reset may not be the one whose
 * saves would be destroyed by it.
 */
sealed interface HardResetBlocker {
    data object ActiveSession : HardResetBlocker
    data class PendingUploads(val accounts: List<PendingUploadAccount>) : HardResetBlocker
    data object ActiveDownloads : HardResetBlocker
    data object EmulatorDownload : HardResetBlocker
    data object SteamDownload : HardResetBlocker
    data object MediaDownload : HardResetBlocker
}

data class PendingUploadAccount(
    val ownerUserId: Long?,
    val username: String?,
    val pendingCount: Int
)

/**
 * What a hard reset would do to game files on disk: rows whose file Argosy downloaded are
 * deleted, rows whose file was found on disk are kept. Byte counts walk directories so a
 * folder-based game is sized as a whole.
 */
data class HardResetPreview(
    val deleteCount: Int = 0,
    val deleteBytes: Long = 0L,
    val keepCount: Int = 0,
    val keepBytes: Long = 0L
)

data class FileDeletionSummary(
    val deleted: Int,
    val kept: Int,
    val failed: Int,
    val recordFile: File?
)

private data class ResetTarget(
    val game: GameEntity,
    val path: String,
    val deletable: Boolean
)

@Singleton
class DatabaseAdminRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: ALauncherDatabase,
    private val imageCacheManager: ImageCacheManager,
    private val attributionRepository: StorageAttributionRepository,
    private val downloadManager: Lazy<DownloadManager>,
    private val emulatorDownloadManager: Lazy<EmulatorDownloadManager>,
    private val steamContentManager: Lazy<SteamContentManager>,
    private val mediaDownloadManager: Lazy<MediaDownloadManager>,
    private val socialRepository: Lazy<SocialRepository>,
    private val soundFeedbackManager: Lazy<SoundFeedbackManager>,
    private val hardResetRecorder: HardResetRecorder
) {
    private val sessionStateStore by lazy { SessionStateStore(context) }

    /** Deletes all cached image files and reconciles DB paths; safe to re-download from the server. */
    suspend fun clearImageCache() = withContext(Dispatchers.IO) {
        imageCacheManager.clearCache()
        imageCacheManager.validateAndCleanCache(force = true)
        attributionRepository.markDirty(StorageCategory.IMAGE_CACHE)
    }

    /** Deletes extracted ROM working copies; returns false when blocked by an active game session. */
    suspend fun clearRomExtractionCache(): Boolean = withContext(Dispatchers.IO) {
        if (sessionStateStore.hasActiveSession()) return@withContext false
        deleteQuietly(AppPaths.romCacheDir(context.filesDir))
        attributionRepository.markDirty(StorageCategory.ROM_EXTRACTION)
        true
    }

    /** Resets the entire library database and per-game caches; downloaded ROM files stay on disk. */
    suspend fun purgeAllLibrary() = withContext(Dispatchers.IO) {
        deleteCacheDirs(GameSource.entries)
        purgeDatabase(GameSource.entries, includeLocalCollections = true, clearImages = true)
    }

    /**
     * Deletes the game files Argosy downloaded, the full library database, and every cache while
     * keeping settings and logins. Files Argosy only found on disk stay where they are; their
     * rows go with the rest of the database. All-or-nothing: returns the first blocker without
     * deleting anything, or null after a completed reset.
     */
    suspend fun hardReset(): HardResetBlocker? = withContext(Dispatchers.IO) {
        checkHardResetBlockers()?.let { return@withContext it }

        val summary = deleteDownloadedFiles(GameSource.entries)
        Log.i(
            TAG,
            "hardReset: deleted ${summary.deleted}, kept ${summary.kept}, failed ${summary.failed}" +
                (summary.recordFile?.let { ", record ${it.absolutePath}" } ?: "")
        )
        purgeDatabase(GameSource.entries, includeLocalCollections = true, clearImages = true)
        downloadManager.get().cleanAbandonedStaging()
        soundFeedbackManager.get().clearSfxCache()
        if (!emulatorDownloadManager.get().clearApkCache()) {
            Log.w(TAG, "hardReset: emulator APK cache skipped, download became active mid-reset")
        }
        if (!steamContentManager.get().clearDownloadData()) {
            Log.w(TAG, "hardReset: steam download data skipped, download became active mid-reset")
        }
        socialRepository.get().clearPresenceCovers()
        attributionRepository.refresh(force = true, deep = true)
        null
    }

    private suspend fun checkHardResetBlockers(): HardResetBlocker? {
        if (sessionStateStore.hasActiveSession()) return HardResetBlocker.ActiveSession
        pendingUploadsByAccount()?.let { return it }
        val downloadState = downloadManager.get().state.value
        if (downloadState.activeDownloads.isNotEmpty() || downloadState.queue.isNotEmpty()) {
            return HardResetBlocker.ActiveDownloads
        }
        if (emulatorDownloadManager.get().hasActiveDownload()) return HardResetBlocker.EmulatorDownload
        if (steamContentManager.get().hasBlockingDownloadState()) return HardResetBlocker.SteamDownload
        if (mediaDownloadManager.get().hasBlockingDownloadState()) return HardResetBlocker.MediaDownload
        return null
    }

    /**
     * Pending save uploads grouped by the account that owns them, or null when nothing is pending.
     */
    suspend fun pendingUploadsByAccount(): HardResetBlocker.PendingUploads? {
        val tallies = database.saveCacheDao().countNeedingRemoteSyncByOwner()
            .filter { it.pendingCount > 0 }
        if (tallies.isEmpty()) return null
        val namesByUserId = database.rommAccountDao().getAll().associate { it.rommUserId to it.username }
        return HardResetBlocker.PendingUploads(
            tallies.map {
                PendingUploadAccount(
                    ownerUserId = it.ownerUserId,
                    username = it.ownerUserId?.let { id -> namesByUserId[id] },
                    pendingCount = it.pendingCount
                )
            }
        )
    }

    suspend fun purgeDatabase(
        sources: List<GameSource>,
        includeLocalCollections: Boolean,
        clearImages: Boolean
    ) = withContext(Dispatchers.IO) {
        val sourceNames = sources.map { it.name }

        database.withTransaction {
            database.saveSyncDao().deleteByGameSources(sourceNames)
            database.saveCacheDao().deleteByGameSources(sourceNames)
            database.stateCacheDao().deleteByGameSources(sourceNames)
            database.stateTombstoneDao().deleteByGameSources(sourceNames)
            database.pendingConflictDao().deleteByGameSources(sourceNames)
            database.pendingSyncQueueDao().deleteByGameSources(sourceNames)
            database.playSessionDao().deleteByGameSources(sourceNames)
            database.downloadQueueDao().deleteByGameSources(sourceNames)
            if (includeLocalCollections) {
                database.collectionDao().deleteAllCollections()
            } else {
                database.collectionDao().deleteRomMSynced()
            }
            database.gameDao().deleteBySources(sources)
            database.platformDao().deleteEmptyPlatforms()
            database.pinnedCollectionDao().deleteOrphaned()
            database.homeTileDao().deleteTilesForMissingGames()
            database.bgmPlaylistDao().clearDanglingGameFileIds()
        }

        if (clearImages) {
            imageCacheManager.clearCache()
            attributionRepository.markDirty(StorageCategory.IMAGE_CACHE)
        }
    }

    suspend fun previewHardReset(): HardResetPreview = withContext(Dispatchers.IO) {
        val targets = resetTargets(GameSource.entries)
        val (deletable, kept) = targets.partition { it.deletable }
        HardResetPreview(
            deleteCount = deletable.size,
            deleteBytes = deletable.sumOf { sizeOf(it.path) },
            keepCount = kept.size,
            keepBytes = kept.sumOf { sizeOf(it.path) }
        )
    }

    /**
     * Deletes the files of every row whose origin says Argosy downloaded it and leaves the rest
     * on disk. The record is written before the first delete so a crash mid-pass still leaves a
     * list of what was about to go.
     */
    suspend fun deleteDownloadedFiles(sources: List<GameSource>): FileDeletionSummary =
        withContext(Dispatchers.IO) {
            val targets = resetTargets(sources)
            val session = hardResetRecorder.begin(targets.map { it.toRecordEntry() })
            var deleted = 0
            var kept = 0
            var failed = 0
            for (target in targets) {
                if (!target.deletable) {
                    session.recordKept(target.game.id)
                    kept++
                    continue
                }
                try {
                    val file = File(target.path)
                    if (!file.exists()) {
                        session.recordMissing(target.game.id)
                        deleted++
                        continue
                    }
                    val removed = if (file.isDirectory) file.deleteRecursively() else file.delete()
                    if (removed) {
                        session.recordDeleted(target.game.id)
                        deleted++
                    } else {
                        session.recordFailed(target.game.id, null)
                        failed++
                        Log.e(TAG, "deleteDownloadedFiles: could not delete ${target.path}")
                    }
                } catch (e: Exception) {
                    session.recordFailed(target.game.id, e.message)
                    failed++
                    Log.e(TAG, "deleteDownloadedFiles: failed to delete ${target.path}: ${e.message}")
                }
            }
            session.complete()
            Log.i(TAG, "deleteDownloadedFiles: deleted $deleted, kept $kept, failed $failed")
            deleteCacheDirs(sources)
            attributionRepository.markDirty(StorageCategory.GAMES)
            FileDeletionSummary(deleted = deleted, kept = kept, failed = failed, recordFile = session.file)
        }

    private suspend fun resetTargets(sources: List<GameSource>): List<ResetTarget> =
        database.gameDao().getDownloadedBySources(sources).mapNotNull { game ->
            val path = game.localPath ?: return@mapNotNull null
            ResetTarget(game = game, path = path, deletable = game.fileOrigin.deletedOnReset)
        }

    private fun ResetTarget.toRecordEntry() = HardResetRecordEntry(
        gameId = game.id,
        title = game.title,
        platformSlug = game.platformSlug,
        path = path,
        origin = game.fileOrigin.name,
        action = if (deletable) HardResetRecorder.ACTION_DELETE else HardResetRecorder.ACTION_KEEP,
        result = null
    )

    private fun sizeOf(path: String): Long {
        val file = File(path)
        return when {
            !file.exists() -> 0L
            file.isDirectory -> file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            else -> file.length()
        }
    }

    /**
     * The cache root plus one per account. Entries written before the cache was partitioned sit
     * directly under the root, so a purge that only walked the account directories would leave
     * them behind.
     */
    private fun accountCacheRoots(root: File): List<File> =
        listOf(root) + (root.listFiles { f -> f.isDirectory && AppPaths.isOwnerCacheDir(f.name) }
            ?.toList() ?: emptyList())

    private suspend fun deleteCacheDirs(sources: List<GameSource>) {
        if (sources.containsAll(GameSource.entries)) {
            deleteQuietly(AppPaths.saveCacheDir(context.filesDir))
            deleteQuietly(AppPaths.stateCacheDir(context.filesDir))
            deleteQuietly(AppPaths.romCacheDir(context.filesDir))
        } else {
            val saveCacheRoots = accountCacheRoots(AppPaths.saveCacheDir(context.filesDir))
            val stateCacheRoots = accountCacheRoots(AppPaths.stateCacheDir(context.filesDir))
            for (source in sources) {
                for (game in database.gameDao().getBySource(source)) {
                    saveCacheRoots.forEach { deleteQuietly(File(it, game.id.toString())) }
                    stateCacheRoots.forEach {
                        deleteQuietly(File(it, "${game.platformSlug}/${game.id}"))
                    }
                    deleteQuietly(File(AppPaths.romCacheDir(context.filesDir), "${game.platformSlug}/${game.id}"))
                }
            }
        }
        attributionRepository.markDirty(StorageCategory.SAVE_STATE_CACHE)
        attributionRepository.markDirty(StorageCategory.ROM_EXTRACTION)
    }

    private fun deleteQuietly(dir: File) {
        try {
            if (dir.exists()) dir.deleteRecursively()
        } catch (e: Exception) {
            Log.e(TAG, "deleteQuietly: failed to delete ${dir.absolutePath}: ${e.message}")
        }
    }
}
