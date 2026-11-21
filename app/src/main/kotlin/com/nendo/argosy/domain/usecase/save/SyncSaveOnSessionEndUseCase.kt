package com.nendo.argosy.domain.usecase.save

import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.ActiveSaveRepository
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.SaveSyncResult
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject

class SyncSaveOnSessionEndUseCase @Inject constructor(
    private val saveSyncRepository: SaveSyncRepository,
    private val gameDao: GameDao,
    private val activeSaveRepository: ActiveSaveRepository,
    private val emulatorResolver: EmulatorResolver,
    private val preferencesRepository: UserPreferencesRepository,
    private val romMRepository: RomMRepository,
    private val fileAccessLayer: FileAccessLayer,
    private val saveCacheManager: SaveCacheManager,
) {
    companion object {
        private const val TAG = "SyncSaveOnSessionEnd"
    }

    sealed class Result {
        data class Uploaded(
            val rommSaveId: Long? = null,
            val serverTimestamp: Instant? = null
        ) : Result()
        data object Queued : Result()
        data object NoChange : Result()
        data class Conflict(
            val gameId: Long,
            val emulatorId: String,
            val channelName: String?,
            val upload: SaveSyncResult.Conflict
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

        val emulatorId = resolveSessionEmulatorId(game, emulatorPackage) ?: return false

        return SavePathRegistry.canSyncWithSettings(emulatorId, prefs.saveSyncEnabled)
    }

    private suspend fun resolveSessionEmulatorId(game: GameEntity, emulatorPackage: String): String? =
        emulatorResolver.resolveSessionEmulator(game.id, game.platformId, game.platformSlug, emulatorPackage)
            ?.emulatorId

    suspend operator fun invoke(
        gameId: Long,
        emulatorPackage: String,
        sessionStartTime: Long = 0L,
        coreName: String? = null,
        isHardcore: Boolean = false,
        channelName: String? = null
    ): Result {
        Logger.debug(TAG, "[SaveSync] SESSION gameId=$gameId | Session end sync starting | emulatorPackage=$emulatorPackage, core=$coreName, sessionStart=$sessionStartTime, hardcore=$isHardcore")

        val prefs = preferencesRepository.userPreferences.first()
        if (!prefs.saveSyncEnabled) {
            Logger.info(TAG, "[SaveSync] SESSION gameId=$gameId | Result=NOT_CONFIGURED | Save sync disabled in preferences")
            return Result.NotConfigured
        }

        val game = gameDao.getById(gameId) ?: run {
            Logger.warn(TAG, "[SaveSync] SESSION gameId=$gameId | Result=ERROR | Game not found")
            return Result.Error("Game not found")
        }
        if (game.rommId == null) {
            Logger.info(TAG, "[SaveSync] SESSION gameId=$gameId | Result=NOT_CONFIGURED | No rommId, skipping sync | game=${game.title}")
            return Result.NotConfigured
        }

        val emulatorId = resolveSessionEmulatorId(game, emulatorPackage)
        if (emulatorId == null) {
            Logger.warn(TAG, "[SaveSync] SESSION gameId=$gameId | Result=NOT_CONFIGURED | Cannot resolve emulator | launchPackage=$emulatorPackage")
            return Result.NotConfigured
        }
        Logger.debug(TAG, "[SaveSync] SESSION gameId=$gameId | Resolved emulator | emulatorId=$emulatorId, launchPackage=$emulatorPackage")

        val saveIdForLookup = game.saveId ?: game.titleId
        var savePath = saveSyncRepository.discoverSavePath(
            emulatorId = emulatorId,
            gameTitle = game.title,
            platformSlug = game.platformSlug,
            romPath = game.localPath,
            cachedSaveId = saveIdForLookup,
            coreName = coreName,
            emulatorPackage = emulatorPackage,
            gameId = gameId
        )
        Logger.debug(TAG, "[SaveSync] SESSION gameId=$gameId | Initial path discovery | savePath=$savePath, cachedSaveId=$saveIdForLookup")

        if (savePath == null) {
            Logger.info(TAG, "[SaveSync] SESSION gameId=$gameId | Result=NO_SAVE_FOUND | No save path discovered")
            return Result.NoSaveFound
        }

        if (!fileAccessLayer.exists(savePath)) {
            Logger.info(TAG, "[SaveSync] SESSION gameId=$gameId | Result=NO_SAVE_FOUND | Path exists but file missing | path=$savePath")
            return Result.NoSaveFound
        }

        val isDirectory = fileAccessLayer.isDirectory(savePath)
        val members = if (isDirectory) fileAccessLayer.walk(savePath).filter { it.isFile }.toList() else emptyList()
        val saveSize = if (isDirectory) members.sumOf { it.size } else fileAccessLayer.length(savePath)
        val localModified = Instant.ofEpochMilli(
            if (isDirectory) members.maxOfOrNull { it.lastModified } ?: 0L else fileAccessLayer.lastModified(savePath)
        )
        val activeChannel = channelName ?: activeSaveRepository.getActiveChannel(gameId)
            ?: if (isHardcore) null else com.nendo.argosy.data.repository.SaveSyncApiClient.AUTOSAVE_SLOT_NAME
        Logger.debug(TAG, "[SaveSync] SESSION gameId=$gameId | Save ready for upload | path=$savePath, size=${saveSize}bytes, modified=$localModified, channel=$activeChannel")

        if (saveCacheManager.isUnchangedServerPlacement(gameId, emulatorId, activeChannel, savePath)) {
            Logger.info(TAG, "[SaveSync] SESSION gameId=$gameId | Result=NO_CHANGE | Save is unchanged since it last matched the server")
            return Result.NoChange
        }

        saveSyncRepository.createOrUpdateSyncEntity(
            gameId = gameId,
            rommId = game.rommId,
            emulatorId = emulatorId,
            localPath = savePath,
            localUpdatedAt = localModified,
            channelName = activeChannel
        )


        if (!romMRepository.isConnected()) {
            Logger.debug(TAG, "[SaveSync] SESSION gameId=$gameId | RomM not connected, attempting reconnect...")
            romMRepository.checkConnection()
        }
        if (!romMRepository.isConnected()) {
            saveSyncRepository.queueUpload(gameId, emulatorId, savePath, activeChannel)
            Logger.info(TAG, "[SaveSync] SESSION gameId=$gameId | Result=QUEUED | Offline at session end, queued for retry on reconnect | path=$savePath")
            return Result.Queued
        }

        return when (val syncResult = saveSyncRepository.uploadSave(gameId, emulatorId, activeChannel, isHardcore = isHardcore)) {
            is SaveSyncResult.Success -> if (syncResult.noOp) {
                Logger.info(TAG, "[SaveSync] SESSION gameId=$gameId | Result=NO_CHANGE | Save already in sync")
                Result.NoChange
            } else {
                Logger.info(TAG, "[SaveSync] SESSION gameId=$gameId | Result=UPLOADED | Save synced successfully | rommSaveId=${syncResult.rommSaveId}")
                Result.Uploaded(
                    rommSaveId = syncResult.rommSaveId,
                    serverTimestamp = syncResult.serverTimestamp
                )
            }
            is SaveSyncResult.Conflict -> {
                Logger.info(TAG, "[SaveSync] SESSION gameId=$gameId | Result=CONFLICT | local=${syncResult.localTimestamp}, server=${syncResult.serverTimestamp}")
                Result.Conflict(
                    gameId = syncResult.gameId,
                    emulatorId = emulatorId,
                    channelName = activeChannel,
                    upload = syncResult
                )
            }
            is SaveSyncResult.Error -> {
                Logger.warn(TAG, "[SaveSync] SESSION gameId=$gameId | Result=QUEUED | Upload failed, queued for retry | error=${syncResult.message}")
                saveSyncRepository.queueUpload(gameId, emulatorId, savePath, activeChannel)
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
            is SaveSyncResult.NeedsHardcoreResolution -> {
                Logger.warn(TAG, "[SaveSync] SESSION gameId=$gameId | Result=NEEDS_HARDCORE_RESOLUTION | Unexpected during upload")
                Result.Error("Hardcore save requires resolution")
            }
        }
    }
}
