package com.nendo.argosy.domain.usecase.save

import com.nendo.argosy.data.emulator.EmulatorResolver
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
    private val emulatorResolver: EmulatorResolver,
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

        val packageToResolve = emulatorConfig?.packageName ?: emulatorPackage
        val emulatorId = emulatorResolver.resolveEmulatorId(packageToResolve) ?: return false

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
        Logger.debug(TAG, "[SaveSync] SESSION gameId=$gameId | Session end sync starting | emulatorPackage=$emulatorPackage, core=$coreName, sessionStart=$sessionStartTime")

        val prefs = preferencesRepository.userPreferences.first()
        if (!prefs.saveSyncEnabled) {
            Logger.debug(TAG, "[SaveSync] SESSION gameId=$gameId | Save sync disabled in preferences")
            return Result.NotConfigured
        }
        if (!romMRepository.isConnected()) {
            Logger.debug(TAG, "[SaveSync] SESSION gameId=$gameId | RomM not connected, attempting reconnect...")
            romMRepository.checkConnection()
        }
        if (!romMRepository.isConnected()) {
            Logger.debug(TAG, "[SaveSync] SESSION gameId=$gameId | RomM still not connected after retry")
            return Result.NotConfigured
        }

        val game = gameDao.getById(gameId) ?: return Result.Error("Game not found")
        if (game.rommId == null) {
            Logger.debug(TAG, "[SaveSync] SESSION gameId=$gameId | No rommId, skipping sync | game=${game.title}")
            return Result.NotConfigured
        }

        val emulatorConfig = emulatorConfigDao.getByGameId(gameId)
            ?: emulatorConfigDao.getDefaultForPlatform(game.platformId)

        val pkgToResolve = emulatorConfig?.packageName ?: emulatorPackage
        val emulatorId = emulatorResolver.resolveEmulatorId(pkgToResolve)
        if (emulatorId == null) {
            Logger.debug(TAG, "[SaveSync] SESSION gameId=$gameId | Cannot resolve emulator | configPackage=${emulatorConfig?.packageName}, launchPackage=$emulatorPackage")
            return Result.NotConfigured
        }
        Logger.debug(TAG, "[SaveSync] SESSION gameId=$gameId | Resolved emulator | emulatorId=$emulatorId, configPackage=${emulatorConfig?.packageName}, launchPackage=$emulatorPackage")

        var titleId = game.titleId
        var savePath = saveSyncRepository.discoverSavePath(
            emulatorId = emulatorId,
            gameTitle = game.title,
            platformSlug = game.platformSlug,
            romPath = game.localPath,
            cachedTitleId = titleId,
            coreName = coreName,
            emulatorPackage = emulatorPackage,
            gameId = gameId
        )
        Logger.debug(TAG, "[SaveSync] SESSION gameId=$gameId | Initial path discovery | savePath=$savePath, cachedTitleId=$titleId")

        if (savePath == null && titleId == null && sessionStartTime > 0) {
            Logger.debug(TAG, "[SaveSync] SESSION gameId=$gameId | No save found, attempting folder-based detection")
            val detected = titleIdDetector.detectRecentTitleId(
                emulatorId, game.platformSlug, sessionStartTime, emulatorPackage
            )
            if (detected == null) {
                Logger.debug(TAG, "[SaveSync] SESSION gameId=$gameId | No folder-based save detected")
            } else {
                val existingGame = gameDao.getByTitleIdAndPlatform(detected.titleId, game.platformId)
                if (existingGame == null || existingGame.id == gameId) {
                    Logger.debug(TAG, "[SaveSync] SESSION gameId=$gameId | Detected titleId | titleId=${detected.titleId}, savePath=${detected.savePath}")
                    gameDao.updateTitleId(gameId, detected.titleId)
                    titleId = detected.titleId
                    savePath = detected.savePath
                } else {
                    Logger.debug(TAG, "[SaveSync] SESSION gameId=$gameId | TitleId already assigned to another game | titleId=${detected.titleId}, otherGameId=${existingGame.id}")
                }
            }
        }

        if (savePath == null) {
            Logger.info(TAG, "[SaveSync] SESSION gameId=$gameId | Result=NO_SAVE_FOUND | No save path discovered")
            return Result.NoSaveFound
        }

        val saveFile = File(savePath)
        if (!saveFile.exists()) {
            Logger.info(TAG, "[SaveSync] SESSION gameId=$gameId | Result=NO_SAVE_FOUND | Path exists but file missing | path=$savePath")
            return Result.NoSaveFound
        }

        val saveSize = if (saveFile.isDirectory) saveFile.walkTopDown().filter { it.isFile }.sumOf { it.length() } else saveFile.length()
        val localModified = Instant.ofEpochMilli(saveFile.lastModified())
        val activeChannel = game.activeSaveChannel
        Logger.debug(TAG, "[SaveSync] SESSION gameId=$gameId | Save ready for upload | path=$savePath, size=${saveSize}bytes, modified=$localModified, channel=$activeChannel")

        saveSyncRepository.createOrUpdateSyncEntity(
            gameId = gameId,
            rommId = game.rommId,
            emulatorId = emulatorId,
            localPath = savePath,
            localUpdatedAt = localModified,
            channelName = activeChannel
        )

        return when (val syncResult = saveSyncRepository.uploadSave(gameId, emulatorId, activeChannel)) {
            is SaveSyncResult.Success -> {
                Logger.info(TAG, "[SaveSync] SESSION gameId=$gameId | Result=UPLOADED | Save synced successfully")
                Result.Uploaded
            }
            is SaveSyncResult.Conflict -> {
                Logger.info(TAG, "[SaveSync] SESSION gameId=$gameId | Result=CONFLICT | local=${syncResult.localTimestamp}, server=${syncResult.serverTimestamp}")
                Result.Conflict(
                    syncResult.gameId,
                    syncResult.localTimestamp,
                    syncResult.serverTimestamp
                )
            }
            is SaveSyncResult.Error -> {
                Logger.warn(TAG, "[SaveSync] SESSION gameId=$gameId | Result=QUEUED | Upload failed, queued for retry | error=${syncResult.message}")
                saveSyncRepository.queueUpload(gameId, emulatorId, savePath)
                Result.Queued
            }
            is SaveSyncResult.NoSaveFound -> {
                Logger.info(TAG, "[SaveSync] SESSION gameId=$gameId | Result=NO_SAVE_FOUND | Repository returned no save")
                Result.NoSaveFound
            }
            is SaveSyncResult.NotConfigured -> {
                Logger.info(TAG, "[SaveSync] SESSION gameId=$gameId | Result=NOT_CONFIGURED | Sync not configured")
                Result.NotConfigured
            }
        }
    }
}
