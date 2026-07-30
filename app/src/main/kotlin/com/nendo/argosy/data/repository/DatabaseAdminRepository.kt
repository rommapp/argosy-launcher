package com.nendo.argosy.data.repository

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.emulator.EmulatorDownloadManager
import com.nendo.argosy.data.local.ALauncherDatabase
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
}

data class PendingUploadAccount(
    val ownerUserId: Long?,
    val username: String?,
    val pendingCount: Int
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
    private val socialRepository: Lazy<SocialRepository>,
    private val soundFeedbackManager: Lazy<SoundFeedbackManager>
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
     * Deletes all downloaded game files, the full library database, and every cache while
     * keeping settings and logins. All-or-nothing: returns the first blocker without
     * deleting anything, or null after a completed reset.
     */
    suspend fun hardReset(): HardResetBlocker? = withContext(Dispatchers.IO) {
        checkHardResetBlockers()?.let { return@withContext it }

        deleteDownloadedFiles(GameSource.entries)
        purgeDatabase(GameSource.entries, includeLocalCollections = true, clearImages = true)
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
            database.bgmPlaylistDao().clearDanglingGameFileIds()
        }

        if (clearImages) {
            imageCacheManager.clearCache()
            attributionRepository.markDirty(StorageCategory.IMAGE_CACHE)
        }
    }

    suspend fun deleteDownloadedFiles(sources: List<GameSource>) = withContext(Dispatchers.IO) {
        for (game in database.gameDao().getDownloadedBySources(sources)) {
            val path = game.localPath ?: continue
            try {
                val file = File(path)
                if (file.exists()) {
                    if (file.isDirectory) file.deleteRecursively() else file.delete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "deleteDownloadedFiles: failed to delete $path: ${e.message}")
            }
        }
        deleteCacheDirs(sources)
        attributionRepository.markDirty(StorageCategory.GAMES)
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
