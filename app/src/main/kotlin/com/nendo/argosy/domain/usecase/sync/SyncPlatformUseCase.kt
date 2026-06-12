package com.nendo.argosy.domain.usecase.sync

import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.SyncResult
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "SyncPlatformUseCase"
private const val NOTIFICATION_KEY = "romm-platform-sync"

sealed class SyncPlatformResult {
    data class Success(val result: SyncResult) : SyncPlatformResult()
    data class Error(val message: String) : SyncPlatformResult()
}

class SyncPlatformUseCase @Inject constructor(
    private val romMRepository: RomMRepository,
    private val platformDao: PlatformDao,
    private val notificationManager: NotificationManager
) {
    suspend operator fun invoke(platformId: Long, platformName: String): SyncPlatformResult {
        Logger.info(TAG, "invoke: starting sync for platform $platformId ($platformName)")

        if (!romMRepository.isConnected()) {
            Logger.info(TAG, "invoke: not connected")
            return SyncPlatformResult.Error("RomM not connected")
        }

        val platform = platformDao.getById(platformId)
        if (platform == null) {
            Logger.info(TAG, "invoke: platform $platformId not found")
            return SyncPlatformResult.Error("Platform not found")
        }
        Logger.info(TAG, "invoke: syncing platform ${platform.id} (slug='${platform.slug}')")

        notificationManager.showPersistent(
            title = "Syncing $platformName",
            subtitle = "Fetching games...",
            key = NOTIFICATION_KEY
        )

        return try {
            withContext(NonCancellable) {
                val result = romMRepository.syncPlatform(platformId)

                if (result.errors.singleOrNull() == "Sync already in progress") {
                    notificationManager.dismissByKey(NOTIFICATION_KEY)
                    return@withContext SyncPlatformResult.Error("Sync already in progress")
                }

                val subtitle = buildString {
                    append("${result.gamesAdded} added, ${result.gamesUpdated} updated")
                    if (result.gamesDeleted > 0) {
                        append(", ${result.gamesDeleted} removed")
                    }
                }

                if (result.errors.isEmpty()) {
                    notificationManager.completePersistent(
                        key = NOTIFICATION_KEY,
                        title = "$platformName synced",
                        subtitle = subtitle,
                        type = NotificationType.SUCCESS
                    )
                } else {
                    notificationManager.completePersistent(
                        key = NOTIFICATION_KEY,
                        title = "Sync completed with errors",
                        subtitle = result.errors.firstOrNull() ?: "Unknown error",
                        type = NotificationType.ERROR
                    )
                }

                SyncPlatformResult.Success(result)
            }
        } catch (e: Exception) {
            Logger.error(TAG, "invoke: exception", e)
            withContext(NonCancellable) {
                notificationManager.completePersistent(
                    key = NOTIFICATION_KEY,
                    title = "Sync failed",
                    subtitle = e.message,
                    type = NotificationType.ERROR
                )
            }
            SyncPlatformResult.Error(e.message ?: "Sync failed")
        }
    }
}
