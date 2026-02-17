package com.nendo.argosy.ui.screens.gamedetail.delegates

import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.local.dao.EmulatorSaveConfigDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.ui.common.savechannel.SaveChannelDelegate
import com.nendo.argosy.ui.common.savechannel.SaveTab
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.showError
import com.nendo.argosy.ui.screens.gamedetail.components.SaveStatusEvent
import com.nendo.argosy.ui.screens.gamedetail.components.SaveStatusInfo
import com.nendo.argosy.ui.screens.gamedetail.components.SaveSyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaveManagementDelegate @Inject constructor(
    private val gameRepository: GameRepository,
    private val saveSyncDao: SaveSyncDao,
    private val emulatorSaveConfigDao: EmulatorSaveConfigDao,
    private val emulatorResolver: EmulatorResolver,
    private val saveCacheManager: SaveCacheManager,
    private val notificationManager: NotificationManager,
    val saveChannelDelegate: SaveChannelDelegate
) {

    suspend fun loadSaveStatusInfo(
        gameId: Long,
        emulatorId: String,
        activeChannel: String?,
        activeSaveTimestamp: Long?
    ): SaveStatusInfo? {
        val syncEntity = if (activeChannel != null) {
            saveSyncDao.getByGameEmulatorAndChannel(gameId, emulatorId, activeChannel)
        } else {
            saveSyncDao.getByGameAndEmulator(gameId, emulatorId)
        }

        val cacheTimestamp = if (activeChannel != null) {
            saveCacheManager.getMostRecentInChannel(gameId, activeChannel)?.cachedAt
        } else {
            saveCacheManager.getMostRecentSave(gameId)?.cachedAt
        }

        val effectiveTimestamp = activeSaveTimestamp
            ?: cacheTimestamp?.toEpochMilli()

        if (activeSaveTimestamp == null && effectiveTimestamp != null) {
            gameRepository.updateActiveSaveTimestamp(gameId, effectiveTimestamp)
        }

        val lastSyncTime = syncEntity?.lastSyncedAt
            ?: syncEntity?.localUpdatedAt
            ?: syncEntity?.serverUpdatedAt
            ?: cacheTimestamp

        return if (syncEntity != null) {
            SaveStatusInfo(
                status = when (syncEntity.syncStatus) {
                    SaveSyncEntity.STATUS_SYNCED -> SaveSyncStatus.SYNCED
                    SaveSyncEntity.STATUS_LOCAL_NEWER -> SaveSyncStatus.LOCAL_NEWER
                    SaveSyncEntity.STATUS_SERVER_NEWER -> SaveSyncStatus.LOCAL_NEWER
                    SaveSyncEntity.STATUS_PENDING_UPLOAD -> SaveSyncStatus.PENDING_UPLOAD
                    SaveSyncEntity.STATUS_CONFLICT -> SaveSyncStatus.LOCAL_NEWER
                    else -> SaveSyncStatus.NO_SAVE
                },
                channelName = activeChannel,
                activeSaveTimestamp = effectiveTimestamp,
                lastSyncTime = lastSyncTime
            )
        } else {
            SaveStatusInfo(
                status = if (cacheTimestamp != null) SaveSyncStatus.LOCAL_ONLY else SaveSyncStatus.NO_SAVE,
                channelName = activeChannel,
                activeSaveTimestamp = effectiveTimestamp,
                lastSyncTime = lastSyncTime
            )
        }
    }

    fun showSaveCacheDialog(
        scope: CoroutineScope,
        gameId: Long,
        activeChannel: String?,
        onEmulatorNotFound: () -> Unit
    ) {
        scope.launch {
            val game = gameRepository.getById(gameId) ?: return@launch
            val emulatorId = emulatorResolver.getEmulatorIdForGame(gameId, game.platformId, game.platformSlug)
            if (emulatorId == null) {
                onEmulatorNotFound()
                return@launch
            }
            val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(gameId, game.platformId, game.platformSlug)
            val savePath = computeEffectiveSavePath(emulatorId, game.platformSlug)
            saveChannelDelegate.show(
                scope = scope,
                gameId = gameId,
                activeChannel = activeChannel,
                savePath = savePath,
                emulatorId = emulatorId,
                emulatorPackage = emulatorPackage
            )
        }
    }

    private suspend fun computeEffectiveSavePath(emulatorId: String, platformSlug: String): String? {
        val userConfig = emulatorSaveConfigDao.getByEmulator(emulatorId)
        if (userConfig?.isUserOverride == true) {
            return userConfig.savePathPattern
        }
        val config = SavePathRegistry.getConfig(emulatorId) ?: return null
        val paths = SavePathRegistry.resolvePath(config, platformSlug)
        return paths.firstOrNull()
    }

    fun confirmSaveCacheSelection(
        scope: CoroutineScope,
        gameId: Long,
        platformId: Long,
        platformSlug: String,
        onSaveStatusChanged: (SaveStatusEvent) -> Unit
    ) {
        scope.launch {
            val emulatorId = emulatorResolver.getEmulatorIdForGame(gameId, platformId, platformSlug)
            if (emulatorId == null) {
                notificationManager.showError("Cannot determine emulator")
                return@launch
            }
            saveChannelDelegate.confirmSelection(
                scope = scope,
                emulatorId = emulatorId,
                onSaveStatusChanged = onSaveStatusChanged,
                onRestored = { }
            )
        }
    }

    fun restoreSave(
        scope: CoroutineScope,
        gameId: Long,
        platformId: Long,
        platformSlug: String,
        syncToServer: Boolean,
        onSaveStatusChanged: (SaveStatusEvent) -> Unit
    ) {
        scope.launch {
            val emulatorId = emulatorResolver.getEmulatorIdForGame(gameId, platformId, platformSlug)
            if (emulatorId == null) {
                notificationManager.showError("Cannot determine emulator for save restore")
                return@launch
            }

            saveChannelDelegate.restoreSave(
                scope = scope,
                emulatorId = emulatorId,
                syncToServer = syncToServer,
                onSaveStatusChanged = onSaveStatusChanged
            )
        }
    }

    fun confirmMigrateChannel(
        scope: CoroutineScope,
        gameId: Long,
        platformId: Long,
        platformSlug: String,
        onSaveStatusChanged: (SaveStatusEvent) -> Unit
    ) {
        scope.launch {
            val emulatorId = emulatorResolver.getEmulatorIdForGame(
                gameId, platformId, platformSlug
            ) ?: "unknown"
            saveChannelDelegate.confirmMigrateChannel(
                scope = scope,
                emulatorId = emulatorId,
                onSaveStatusChanged = onSaveStatusChanged,
                onRestored = { }
            )
        }
    }
}
