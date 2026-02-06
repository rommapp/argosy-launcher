package com.nendo.argosy.domain.usecase.sync

import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.SyncResult
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.NotificationType
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
        val allPlatforms = if (platform != null) {
            val relatedSlugs = PlatformDefinitions.getSlugsForCanonical(platform.slug)
            platformDao.getAllBySlugs(relatedSlugs)
        } else {
            listOf(platform).filterNotNull()
        }

        val platformIds = allPlatforms.map { it.id }
        val canonicalSlug = platform?.slug?.let { PlatformDefinitions.getCanonicalSlug(it) }
        Logger.info(TAG, "invoke: syncing ${platformIds.size} platform(s) for canonical slug '$canonicalSlug': $platformIds")

        notificationManager.showPersistent(
            title = "Syncing $platformName",
            subtitle = "Fetching games...",
            key = NOTIFICATION_KEY
        )

        return try {
            withContext(NonCancellable) {
                var totalAdded = 0
                var totalUpdated = 0
                var totalDeleted = 0
                val allErrors = mutableListOf<String>()

                for (id in platformIds) {
                    val result = romMRepository.syncPlatform(id)

                    if (result.errors.singleOrNull() == "Sync already in progress") {
                        notificationManager.dismissByKey(NOTIFICATION_KEY)
                        return@withContext SyncPlatformResult.Error("Sync already in progress")
                    }

                    totalAdded += result.gamesAdded
                    totalUpdated += result.gamesUpdated
                    totalDeleted += result.gamesDeleted
                    allErrors.addAll(result.errors)
                }

                val subtitle = buildString {
                    append("$totalAdded added, $totalUpdated updated")
                    if (totalDeleted > 0) {
                        append(", $totalDeleted removed")
                    }
                }

                if (allErrors.isEmpty()) {
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
                        subtitle = allErrors.firstOrNull() ?: "Unknown error",
                        type = NotificationType.ERROR
                    )
                }

                SyncPlatformResult.Success(SyncResult(
                    platformsSynced = platformIds.size,
                    gamesAdded = totalAdded,
                    gamesUpdated = totalUpdated,
                    gamesDeleted = totalDeleted,
                    errors = allErrors
                ))
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
