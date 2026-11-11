package com.nendo.argosy.data.platform

import android.content.Context
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where a platform's roms are written and looked for. Every answer is null or empty while no game
 * folder is set; nothing falls back to app storage.
 */
@Singleton
class PlatformFolderResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val platformDao: PlatformDao,
    private val gameDao: GameDao,
    private val preferencesRepository: UserPreferencesRepository
) {
    /**
     * App-private downloads folder. Read-only lookups search it; nothing writes to it.
     */
    val legacyAppDownloadsDir: File by lazy {
        File(context.getExternalFilesDir(null), "downloads")
    }

    suspend fun storageBase(): File? =
        preferencesRepository.userPreferences.first().romStoragePath
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it) }

    suspend fun downloadDirForPlatform(platformId: Long, fallbackSlug: String): File? {
        val base = storageBase() ?: return null
        return platformDownloadDir(platformDao.getById(platformId), fallbackSlug, base)
    }

    suspend fun downloadDirForGame(gameId: Long, fallbackSlug: String): File? {
        val platformId = gameDao.getById(gameId)?.platformId
            ?: return storageBase()?.let { platformDownloadDir(null, fallbackSlug, it) }
        return downloadDirForPlatform(platformId, fallbackSlug)
    }

    suspend fun romRootsForPlatform(platformId: Long, fallbackSlug: String): List<File> {
        val platform = platformDao.getById(platformId)
        val base = storageBase()
            ?: return platform?.customRomPath?.let { listOf(File(it)) }.orEmpty()
        if (platform == null) return listOf(File(base, fallbackSlug))
        return platformRomRoots(platform, base, platformDao.getAllPlatforms())
    }
}
