package com.nendo.argosy.domain.usecase.save

import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.SaveSyncResult
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
    private val romMRepository: RomMRepository
) {
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

    suspend operator fun invoke(gameId: Long, emulatorPackage: String): Result {
        val prefs = preferencesRepository.userPreferences.first()
        if (!prefs.saveSyncEnabled) return Result.NotConfigured
        if (!romMRepository.isConnected()) return Result.NotConfigured

        val game = gameDao.getById(gameId) ?: return Result.Error("Game not found")
        if (game.rommId == null) return Result.NotConfigured

        val emulatorConfig = emulatorConfigDao.getByGameId(gameId)
            ?: emulatorConfigDao.getDefaultForPlatform(game.platformId)

        val emulatorId = resolveEmulatorId(emulatorConfig?.packageName, emulatorPackage)
            ?: return Result.NotConfigured

        val savePath = saveSyncRepository.discoverSavePath(emulatorId, game.title, game.platformId, game.localPath)
            ?: return Result.NoSaveFound

        val saveFile = File(savePath)
        if (!saveFile.exists()) return Result.NoSaveFound

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

        return when (val syncResult = saveSyncRepository.uploadSave(gameId, emulatorId, activeChannel)) {
            is SaveSyncResult.Success -> Result.Uploaded
            is SaveSyncResult.Conflict -> Result.Conflict(
                syncResult.gameId,
                syncResult.localTimestamp,
                syncResult.serverTimestamp
            )
            is SaveSyncResult.Error -> {
                saveSyncRepository.queueUpload(gameId, emulatorId, savePath)
                Result.Queued
            }
            is SaveSyncResult.NoSaveFound -> Result.NoSaveFound
            is SaveSyncResult.NotConfigured -> Result.NotConfigured
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
