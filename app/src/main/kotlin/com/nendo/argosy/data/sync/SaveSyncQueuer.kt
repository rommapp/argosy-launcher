package com.nendo.argosy.data.sync

import android.content.Context
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.data.preferences.PersistedSession
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

interface SaveSyncQueuer {
    suspend fun ensureQueuedForActiveSession(session: PersistedSession): Boolean
}

@Singleton
class SaveSyncQueuerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val emulatorResolver: EmulatorResolver,
    private val preferencesRepository: UserPreferencesRepository,
    private val saveSyncRepository: SaveSyncRepository,
    private val pendingSyncQueueDao: PendingSyncQueueDao,
) : SaveSyncQueuer {

    override suspend fun ensureQueuedForActiveSession(session: PersistedSession): Boolean {
        val gameId = session.gameId
        val prefs = preferencesRepository.userPreferences.first()
        if (!prefs.saveSyncEnabled) return false

        val game = gameDao.getById(gameId) ?: return false
        if (game.rommId == null) return false

        val emulatorConfig = emulatorConfigDao.getByGameId(gameId)
            ?: emulatorConfigDao.getDefaultForPlatform(game.platformId)
        val packageToResolve = emulatorConfig?.packageName ?: session.emulatorPackage
        val emulatorId = emulatorResolver.resolveEmulatorId(packageToResolve) ?: return false
        if (!SavePathRegistry.canSyncWithSettings(emulatorId, prefs.saveSyncEnabled)) return false

        val alreadyQueued = pendingSyncQueueDao.getByGameId(gameId)
            .any { it.syncType == SyncType.SAVE_FILE }
        if (alreadyQueued) {
            Logger.debug(TAG, "[SaveSync] QUEUER gameId=$gameId | already queued, skipping")
            return true
        }

        val savePath = saveSyncRepository.discoverSavePath(
            emulatorId = emulatorId,
            gameTitle = game.title,
            platformSlug = game.platformSlug,
            romPath = game.localPath,
            cachedSaveId = game.saveId ?: game.titleId,
            coreName = session.coreName,
            emulatorPackage = session.emulatorPackage,
            gameId = gameId,
        ) ?: run {
            Logger.debug(TAG, "[SaveSync] QUEUER gameId=$gameId | no save path discovered, deferring queue")
            return false
        }

        saveSyncRepository.queueUpload(gameId, emulatorId, savePath)
        SaveSyncWorker.runNow(context)
        Logger.info(TAG, "[SaveSync] QUEUER gameId=$gameId | queued for upload + worker kicked | path=$savePath")
        return true
    }

    companion object {
        private const val TAG = "SaveSyncQueuer"
    }
}
