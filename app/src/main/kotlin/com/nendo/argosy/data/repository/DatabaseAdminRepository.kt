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

enum class HardResetBlocker {
    ACTIVE_SESSION,
    PENDING_UPLOADS,
    ACTIVE_DOWNLOADS,
    EMULATOR_DOWNLOAD,
    STEAM_DOWNLOAD
}

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
        imageCacheManager.validateAndCleanCache()
        attributionRepository.markDirty(StorageCategory.IMAGE_CACHE)
    }

    /** Deletes extracted ROM working copies; returns false when blocked by an active game session. */
    suspend fun clearRomExtractionCache(): Boolean = withContext(Dispatchers.IO) {
        if (sessionStateStore.hasActiveSession()) return@withContext false
        deleteQuietly(AppPaths.romCacheDir(context.filesDir))
        attributionRepository.markDirty(StorageCategory.ROM_EXTRACTION)
        true
    }

    /** Wipes RomM-sourced library content AND its downloaded files so a server switch starts clean. */
    suspend fun purgeRomMLibrary() = withContext(Dispatchers.IO) {
        val sources = listOf(GameSource.ROMM_REMOTE, GameSource.ROMM_SYNCED)
        deleteDownloadedFiles(sources)
        purgeDatabase(sources, includeLocalCollections = false, clearImages = true)
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
        if (sessionStateStore.hasActiveSession()) return HardResetBlocker.ACTIVE_SESSION
        if (database.saveCacheDao().countNeedingRemoteSync() > 0) return HardResetBlocker.PENDING_UPLOADS
        val downloadState = downloadManager.get().state.value
        if (downloadState.activeDownloads.isNotEmpty() || downloadState.queue.isNotEmpty()) {
            return HardResetBlocker.ACTIVE_DOWNLOADS
        }
        if (emulatorDownloadManager.get().hasActiveDownload()) return HardResetBlocker.EMULATOR_DOWNLOAD
        if (steamContentManager.get().hasBlockingDownloadState()) return HardResetBlocker.STEAM_DOWNLOAD
        return null
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

    private suspend fun deleteCacheDirs(sources: List<GameSource>) {
        if (sources.containsAll(GameSource.entries)) {
            deleteQuietly(AppPaths.saveCacheDir(context.filesDir))
            deleteQuietly(AppPaths.stateCacheDir(context.filesDir))
            deleteQuietly(AppPaths.romCacheDir(context.filesDir))
        } else {
            for (source in sources) {
                for (game in database.gameDao().getBySource(source)) {
                    deleteQuietly(File(AppPaths.saveCacheDir(context.filesDir), game.id.toString()))
                    deleteQuietly(File(AppPaths.stateCacheDir(context.filesDir), "${game.platformSlug}/${game.id}"))
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
