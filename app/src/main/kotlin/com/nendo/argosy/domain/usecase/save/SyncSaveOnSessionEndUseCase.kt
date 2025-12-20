package com.nendo.argosy.domain.usecase.save

import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.emulator.TitleIdDetector
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.SaveSyncResult
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.Instant
import javax.inject.Inject

class SyncSaveOnSessionEndUseCase @Inject constructor(
    private val saveSyncRepository: SaveSyncRepository,
    private val gameDao: GameDao,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val emulatorDetector: EmulatorDetector,
    private val preferencesRepository: UserPreferencesRepository,
    private val romMRepository: RomMRepository,
    private val titleIdDetector: TitleIdDetector
) {
    companion object {
        private const val TAG = "SyncSaveOnSessionEnd"
    }

    sealed class Result {
        data object Uploaded : Result()
        data object Queued : Result()
        data class Conflict(
            val gameId: Long,
            val localTimestamp: Instant,
            val serverTimestamp: Instant
        ) : Result()
        data object NoSaveFound : Result()
        data object NotConfigured : Result()
        data class Error(val message: String) : Result()
    }

    suspend fun canSync(gameId: Long, emulatorPackage: String): Boolean {
        val prefs = preferencesRepository.userPreferences.first()
        if (!prefs.saveSyncEnabled) return false
        if (!romMRepository.isConnected()) return false

        val game = gameDao.getById(gameId) ?: return false
        if (game.rommId == null) return false

        val emulatorConfig = emulatorConfigDao.getByGameId(gameId)
            ?: emulatorConfigDao.getDefaultForPlatform(game.platformId)

        val emulatorId = resolveEmulatorId(emulatorConfig?.packageName, emulatorPackage)
            ?: return false

        return SavePathRegistry.canSyncWithSettings(
            emulatorId,
            prefs.saveSyncEnabled,
            prefs.experimentalFolderSaveSync
        )
    }

    suspend operator fun invoke(
        gameId: Long,
        emulatorPackage: String,
        sessionStartTime: Long = 0L,
        coreName: String? = null
    ): Result {
        Logger.debug(TAG, "Session end sync starting for gameId=$gameId, emulator=$emulatorPackage, core=$coreName")

        val prefs = preferencesRepository.userPreferences.first()
        if (!prefs.saveSyncEnabled) {
            Logger.debug(TAG, "Save sync disabled in preferences")
            return Result.NotConfigured
        }
        if (!romMRepository.isConnected()) {
            Logger.debug(TAG, "RomM not connected, attempting reconnect...")
            romMRepository.checkConnection()
        }
        if (!romMRepository.isConnected()) {
            Logger.debug(TAG, "RomM still not connected after retry")
            return Result.NotConfigured
        }

        val game = gameDao.getById(gameId) ?: return Result.Error("Game not found")
        if (game.rommId == null) {
            Logger.debug(TAG, "Game ${game.title} has no rommId, skipping sync")
            return Result.NotConfigured
        }

        val emulatorConfig = emulatorConfigDao.getByGameId(gameId)
            ?: emulatorConfigDao.getDefaultForPlatform(game.platformId)

        val emulatorId = resolveEmulatorId(emulatorConfig?.packageName, emulatorPackage)
        if (emulatorId == null) {
            Logger.debug(TAG, "Cannot resolve emulator for package: $emulatorPackage")
            return Result.NotConfigured
        }
        Logger.debug(TAG, "Resolved emulator: $emulatorId for game ${game.title}")

        var titleId = game.titleId
        var savePath = saveSyncRepository.discoverSavePath(
            emulatorId, game.title, game.platformSlug, game.localPath, titleId, coreName
        )

        if (savePath == null && titleId == null && sessionStartTime > 0) {
            Logger.debug(TAG, "No single-file save found, checking for folder-based save detection...")
            val detected = titleIdDetector.detectRecentTitleId(
                emulatorId, game.platformSlug, sessionStartTime
            )
            if (detected == null) {
                Logger.debug(TAG, "Folder-based save detection not applicable for emulator: $emulatorId")
            }
            if (detected != null) {
                val existingGame = gameDao.getByTitleIdAndPlatform(detected.titleId, game.platformId)
                if (existingGame == null || existingGame.id == gameId) {
                    Logger.debug(TAG, "Detected and caching titleId: ${detected.titleId}")
                    gameDao.updateTitleId(gameId, detected.titleId)
                    titleId = detected.titleId
                    savePath = detected.savePath
                } else {
                    Logger.debug(TAG, "TitleId ${detected.titleId} already assigned to game ${existingGame.id}, skipping")
                }
            }
        }

        if (savePath == null) {
            Logger.info(TAG, "Session end: No save found for ${game.title}")
            return Result.NoSaveFound
        }
        Logger.debug(TAG, "Found save path: $savePath")

        val saveFile = File(savePath)
        if (!saveFile.exists()) {
            Logger.info(TAG, "Session end: Save file does not exist for ${game.title}")
            return Result.NoSaveFound
        }

        val localModified = Instant.ofEpochMilli(saveFile.lastModified())
        val activeChannel = game.activeSaveChannel

        saveSyncRepository.createOrUpdateSyncEntity(
            gameId = gameId,
            rommId = game.rommId,
            emulatorId = emulatorId,
            localPath = savePath,
            localUpdatedAt = localModified,
            channelName = activeChannel
        )

        Logger.debug(TAG, "Uploading save for ${game.title} (channel: ${activeChannel ?: "default"})")

        return when (val syncResult = saveSyncRepository.uploadSave(gameId, emulatorId, activeChannel)) {
            is SaveSyncResult.Success -> {
                Logger.info(TAG, "Session end: Save synced for ${game.title}")
                Result.Uploaded
            }
            is SaveSyncResult.Conflict -> {
                Logger.info(TAG, "Session end: Conflict for ${game.title} (local=${syncResult.localTimestamp}, server=${syncResult.serverTimestamp})")
                Result.Conflict(
                    syncResult.gameId,
                    syncResult.localTimestamp,
                    syncResult.serverTimestamp
                )
            }
            is SaveSyncResult.Error -> {
                Logger.warn(TAG, "Session end: Upload failed for ${game.title}, queued for retry: ${syncResult.message}")
                saveSyncRepository.queueUpload(gameId, emulatorId, savePath)
                Result.Queued
            }
            is SaveSyncResult.NoSaveFound -> {
                Logger.info(TAG, "Session end: No save found for ${game.title}")
                Result.NoSaveFound
            }
            is SaveSyncResult.NotConfigured -> {
                Logger.info(TAG, "Session end: Sync not configured for ${game.title}")
                Result.NotConfigured
            }
        }
    }

    private fun resolveEmulatorId(configPackage: String?, launchPackage: String): String? {
        val packageToResolve = configPackage ?: launchPackage

        EmulatorRegistry.getByPackage(packageToResolve)?.let { return it.id }

        val family = EmulatorRegistry.findFamilyForPackage(packageToResolve)
        if (family != null) {
            return family.baseId
        }

        return emulatorDetector.getByPackage(packageToResolve)?.id
    }
}
