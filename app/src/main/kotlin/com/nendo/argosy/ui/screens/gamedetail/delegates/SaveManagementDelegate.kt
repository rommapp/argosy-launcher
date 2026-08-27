package com.nendo.argosy.ui.screens.gamedetail.delegates

import android.content.Context
import com.nendo.argosy.R
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.local.dao.EmulatorSaveConfigDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.repository.ForceSyncResult
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.ui.common.savechannel.SaveChannelDelegate
import com.nendo.argosy.ui.common.savechannel.SaveTab
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.core.notification.showSuccess
import com.nendo.argosy.ui.screens.gamedetail.components.SaveStatusEvent
import com.nendo.argosy.ui.screens.gamedetail.components.SaveStatusInfo
import com.nendo.argosy.ui.screens.gamedetail.components.SaveSyncStatus
import com.nendo.argosy.ui.screens.gamedetail.components.mapSaveSyncStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

class SaveManagementDelegate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameRepository: GameRepository,
    private val activeSaveRepository: com.nendo.argosy.data.repository.ActiveSaveRepository,
    private val saveSyncDao: SaveSyncDao,
    private val emulatorSaveConfigDao: EmulatorSaveConfigDao,
    private val emulatorResolver: EmulatorResolver,
    private val saveCacheManager: SaveCacheManager,
    private val saveSyncRepository: SaveSyncRepository,
    private val syncPreferencesRepository: com.nendo.argosy.data.preferences.SyncPreferencesRepository,
    private val getUnifiedSavesUseCase: com.nendo.argosy.domain.usecase.save.GetUnifiedSavesUseCase,
    private val notificationManager: NotificationManager,
    private val retroArchPathResolver: com.nendo.argosy.data.emulator.RetroArchPathResolver,
    private val coreVersionExtractor: com.nendo.argosy.data.emulator.CoreVersionExtractor,
    val saveChannelDelegate: SaveChannelDelegate
) {

    suspend fun loadSaveStatusInfo(
        gameId: Long,
        emulatorId: String,
        activeChannel: String?,
        activeSaveTimestamp: Long?,
        includeServer: Boolean
    ): SaveStatusInfo? {
        val ownerUserId = syncPreferencesRepository.getRommUserId()
        val namedChannel = com.nendo.argosy.data.repository.SaveSyncApiClient
            .namedChannelOrNull(activeChannel)

        val syncEntity = if (namedChannel != null) {
            saveSyncDao.getByGameEmulatorAndChannel(gameId, emulatorId, namedChannel, ownerUserId)
        } else {
            saveSyncDao.getByGameAndEmulator(gameId, emulatorId, ownerUserId)
        }

        val cacheTimestamp = if (namedChannel != null) {
            saveCacheManager.getMostRecentInChannel(gameId, namedChannel)?.cachedAt
        } else {
            saveCacheManager.getMostRecentSave(gameId)?.cachedAt
        }

        // Unified view so a server-only cloud save (no local cache, no sync row -- the common
        // freshly-synced case) is not misreported as NO_SAVE.
        // Resolve against the coordinates passed in, not the game row's -- a save event can carry a
        // channel the row has not been updated to yet, and mixing the two reports one channel's
        // timestamp under another channel's status.
        val serverTimestamp = if (cacheTimestamp == null && syncEntity == null) {
            getUnifiedSavesUseCase.resolveActive(
                gameId = gameId,
                activeChannel = activeChannel,
                activeSaveTimestamp = activeSaveTimestamp,
                includeServer = includeServer
            )?.timestamp
        } else {
            null
        }

        val effectiveTimestamp = activeSaveTimestamp
            ?: cacheTimestamp?.toEpochMilli()
            ?: serverTimestamp?.toEpochMilli()

        if (activeSaveTimestamp == null && cacheTimestamp != null) {
            activeSaveRepository.activateTimestamp(gameId, cacheTimestamp.toEpochMilli())
        }

        val lastSyncTime = syncEntity?.lastSyncedAt
            ?: syncEntity?.localUpdatedAt
            ?: syncEntity?.serverUpdatedAt
            ?: cacheTimestamp
            ?: serverTimestamp

        return if (syncEntity != null) {
            SaveStatusInfo(
                status = mapSaveSyncStatus(syncEntity.syncStatus),
                channelName = activeChannel,
                activeSaveTimestamp = effectiveTimestamp,
                lastSyncTime = lastSyncTime
            )
        } else {
            SaveStatusInfo(
                status = when {
                    cacheTimestamp != null -> SaveSyncStatus.LOCAL_ONLY
                    serverTimestamp != null -> SaveSyncStatus.SYNCED
                    else -> SaveSyncStatus.NO_SAVE
                },
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
            val coreName = saveSyncRepository.resolveCoreForGame(gameId)
            val savePath = computeEffectiveSavePath(emulatorId, game.platformSlug, emulatorPackage, coreName)
            val stateCoreId = coreVersionExtractor.getCoreIdForEmulator(emulatorId, game.platformSlug)
            saveChannelDelegate.show(
                scope = scope,
                gameId = gameId,
                activeChannel = activeChannel,
                savePath = savePath,
                emulatorId = emulatorId,
                emulatorPackage = emulatorPackage,
                currentCoreId = stateCoreId,
                currentCoreVersion = stateCoreVersion(stateCoreId, emulatorId, emulatorPackage)
            )
        }
    }

    /**
     * States identify their core through [CoreVersionExtractor], not through the save-side
     * `resolveCoreForGame`. The two disagree - notably the built-in emulator, which the save
     * resolver reports as its libretro core and this one reports as `builtin` - and the states
     * cache was written by this one, so restoring and validating against anything else compares a
     * row to a value that never wrote it.
     */
    private fun stateCoreVersion(
        coreId: String?,
        emulatorId: String,
        emulatorPackage: String?
    ): String? =
        if (coreId != null && emulatorId.startsWith("retroarch")) {
            coreVersionExtractor.getRetroArchCoreVersion(coreId, emulatorPackage)
        } else {
            null
        }

    private suspend fun computeEffectiveSavePath(
        emulatorId: String,
        platformSlug: String,
        emulatorPackage: String?,
        coreName: String? = null
    ): String? {
        if (com.nendo.argosy.data.emulator.RetroArchPathResolver.isRetroArch(emulatorId)) {
            val resolvedCore = coreName ?: SavePathRegistry.getRetroArchCore(platformSlug)
            val req = com.nendo.argosy.data.emulator.RetroArchPathResolver.Request(
                emulatorId = emulatorId,
                coreName = resolvedCore,
                romPath = null,
            )
            return when (val display = retroArchPathResolver.displaySavePath(req)) {
                is com.nendo.argosy.data.emulator.RetroArchPathResolver.DisplayPath.ContentDirectory ->
                    context.getString(R.string.gamedetail_per_game_save_path_content_dir)
                is com.nendo.argosy.data.emulator.RetroArchPathResolver.DisplayPath.Resolved -> display.path
                com.nendo.argosy.data.emulator.RetroArchPathResolver.DisplayPath.Unknown -> null
            }
        }
        val userConfig = emulatorSaveConfigDao.getByEmulator(emulatorId)
        if (userConfig?.isUserOverride == true) {
            return userConfig.savePathPattern
        }
        val config = SavePathRegistry.getConfig(emulatorId) ?: return null
        return SavePathRegistry.resolvePathWithPackage(config, emulatorPackage).firstOrNull()
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
                notificationManager.showError(
                    NotificationText.Res(R.string.gamedetail_notice_no_emulator_for_selection)
                )
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
                notificationManager.showError(
                    NotificationText.Res(R.string.gamedetail_notice_no_emulator_for_restore)
                )
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

    fun syncCurrentChannel(
        scope: CoroutineScope,
        gameId: Long,
        platformId: Long,
        platformSlug: String,
        channelName: String?,
        onLoadingChange: (Boolean) -> Unit,
        onSyncStatusChanged: (SaveStatusEvent) -> Unit
    ) {
        scope.launch {
            onLoadingChange(true)
            try {
                val emulatorId = emulatorResolver.getEmulatorIdForGame(gameId, platformId, platformSlug)
                if (emulatorId == null) {
                    notificationManager.showError(
                        NotificationText.Res(R.string.gamedetail_notice_no_emulator_for_sync)
                    )
                    return@launch
                }
                when (val result = saveSyncRepository.forceSyncChannel(gameId, emulatorId, channelName)) {
                    ForceSyncResult.AlreadyInSync -> notificationManager.showSuccess(
                        NotificationText.Res(R.string.gamedetail_notice_saves_up_to_date)
                    )
                    is ForceSyncResult.Uploaded -> {
                        notificationManager.showSuccess(
                            NotificationText.Res(R.string.gamedetail_notice_save_uploaded)
                        )
                        onSyncStatusChanged(SaveStatusEvent(channelName = channelName, timestamp = null))
                    }
                    is ForceSyncResult.Downloaded -> {
                        notificationManager.showSuccess(
                            NotificationText.Res(R.string.gamedetail_notice_save_downloaded)
                        )
                        onSyncStatusChanged(SaveStatusEvent(channelName = channelName, timestamp = null))
                    }
                    ForceSyncResult.SkippedByUser -> notificationManager.showSuccess(
                        NotificationText.Res(R.string.gamedetail_notice_sync_skipped)
                    )
                    is ForceSyncResult.Error -> notificationManager.showError(NotificationText.Raw(result.message))
                }
            } finally {
                onLoadingChange(false)
            }
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
