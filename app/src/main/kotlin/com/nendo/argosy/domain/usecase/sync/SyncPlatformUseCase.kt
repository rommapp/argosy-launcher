package com.nendo.argosy.domain.usecase.sync

import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.SyncResult
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "SyncPlatformUseCase"
private const val NOTIFICATION_KEY = "romm-platform-sync"

sealed class SyncPlatformResult {
    data class Success(val result: SyncResult) : SyncPlatformResult()
    data class Error(val reason: SyncPlatformFailureReason) : SyncPlatformResult()

    /**
     * A sync was already running, so this request did nothing. Distinct from Error because
     * callers back off quietly rather than telling the user something failed.
     */
    data object AlreadyInProgress : SyncPlatformResult()
}

/**
 * Why a platform sync could not run or did not finish. [Unexpected] keeps the exception text
 * as-is because it is not this app's sentence to translate.
 */
sealed class SyncPlatformFailureReason {
    data object NotConnected : SyncPlatformFailureReason()
    data object PlatformNotFound : SyncPlatformFailureReason()
    data class Unexpected(val message: String?) : SyncPlatformFailureReason()
}

class SyncPlatformUseCase @Inject constructor(
    private val romMRepository: RomMRepository,
    private val platformDao: PlatformDao,
    private val notificationManager: NotificationManager,
    private val copy: SyncNotificationCopy
) {
    suspend operator fun invoke(platformId: Long, platformName: String): SyncPlatformResult {
        Logger.info(TAG, "invoke: starting sync for platform $platformId ($platformName)")

        if (!romMRepository.isConnected()) {
            Logger.info(TAG, "invoke: not connected")
            return SyncPlatformResult.Error(SyncPlatformFailureReason.NotConnected)
        }

        val platform = platformDao.getById(platformId)
        if (platform == null) {
            Logger.info(TAG, "invoke: platform $platformId not found")
            return SyncPlatformResult.Error(SyncPlatformFailureReason.PlatformNotFound)
        }
        Logger.info(TAG, "invoke: syncing platform ${platform.id} (slug='${platform.slug}')")

        notificationManager.showPersistent(
            title = copy.platformProgressTitle(platformName),
            subtitle = copy.platformProgressFetching(),
            key = NOTIFICATION_KEY,
            platformSlug = platform.slug
        )

        return try {
            withContext(NonCancellable) {
                val result = romMRepository.syncPlatform(platformId)

                if (result.alreadyInProgress) {
                    notificationManager.dismissByKey(NOTIFICATION_KEY)
                    return@withContext SyncPlatformResult.AlreadyInProgress
                }

                if (result.errors.isEmpty()) {
                    notificationManager.completePersistent(
                        key = NOTIFICATION_KEY,
                        title = copy.platformCompleteTitle(platformName),
                        subtitle = copy.platformCompleteCounts(
                            result.gamesAdded,
                            result.gamesUpdated,
                            result.gamesDeleted
                        ),
                        type = NotificationType.SUCCESS,
                        platformSlug = platform.slug
                    )
                } else {
                    notificationManager.completePersistent(
                        key = NOTIFICATION_KEY,
                        title = copy.platformCompletedWithErrorsTitle(),
                        subtitle = copy.platformErrorDetail(result.errors.firstOrNull()),
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
                    title = copy.platformFailedTitle(),
                    subtitle = e.message?.let { NotificationText.Raw(it) },
                    type = NotificationType.ERROR
                )
            }
            SyncPlatformResult.Error(SyncPlatformFailureReason.Unexpected(e.message))
        }
    }
}
