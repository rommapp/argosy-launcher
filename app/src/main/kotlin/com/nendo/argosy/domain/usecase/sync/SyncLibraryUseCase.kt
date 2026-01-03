package com.nendo.argosy.domain.usecase.sync

import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.remote.romm.SyncResult
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.screens.common.LibrarySyncBus
import com.nendo.argosy.util.Logger
import com.nendo.argosy.ui.notification.NotificationProgress
import com.nendo.argosy.ui.notification.NotificationType
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "SyncLibraryUseCase"
private const val NOTIFICATION_KEY = "romm-sync"

sealed class SyncLibraryResult {
    data class Success(val result: SyncResult) : SyncLibraryResult()
    data class Error(val message: String) : SyncLibraryResult()
}

class SyncLibraryUseCase @Inject constructor(
    private val romMRepository: RomMRepository,
    private val notificationManager: NotificationManager,
    private val librarySyncBus: LibrarySyncBus
) {
    suspend operator fun invoke(
        initializeFirst: Boolean = false,
        onProgress: ((current: Int, total: Int, platform: String) -> Unit)? = null
    ): SyncLibraryResult {
        Logger.info(TAG, "invoke: starting, initializeFirst=$initializeFirst")

        if (initializeFirst) {
            romMRepository.initialize()
        }

        if (!romMRepository.isConnected()) {
            Logger.info(TAG, "invoke: not connected")
            return SyncLibraryResult.Error("RomM not connected")
        }

        Logger.info(TAG, "invoke: fetching summary")
        return when (val summary = romMRepository.getLibrarySummary()) {
            is RomMResult.Error -> {
                Logger.error(TAG, "invoke: summary error: ${summary.message}")
                SyncLibraryResult.Error(summary.message)
            }
            is RomMResult.Success -> {
                val (platformCount, _) = summary.data
                Logger.info(TAG, "invoke: got $platformCount platforms, showing persistent")

                notificationManager.showPersistent(
                    title = "Syncing Library",
                    subtitle = "Starting...",
                    key = NOTIFICATION_KEY,
                    progress = NotificationProgress(0, platformCount)
                )

                try {
                    withContext(NonCancellable) {
                        Logger.info(TAG, "invoke: calling syncLibrary")
                        val result = romMRepository.syncLibrary { current, total, platform ->
                            Logger.info(TAG, "invoke: progress $current/$total - $platform")
                            notificationManager.updatePersistent(
                                key = NOTIFICATION_KEY,
                                subtitle = platform,
                                progress = NotificationProgress(current, total)
                            )
                            onProgress?.invoke(current, total, platform)
                        }

                        Logger.info(TAG, "invoke: syncLibrary returned - added=${result.gamesAdded}, updated=${result.gamesUpdated}, deleted=${result.gamesDeleted}, errors=${result.errors}")

                        if (result.errors.singleOrNull() == "Sync already in progress") {
                            Logger.info(TAG, "invoke: sync already in progress, returning silently")
                            notificationManager.dismissByKey(NOTIFICATION_KEY)
                            return@withContext SyncLibraryResult.Error("Sync already in progress")
                        }

                        Logger.info(TAG, "invoke: syncing favorites")
                        romMRepository.syncFavorites()

                        val subtitle = buildString {
                            append("${result.gamesAdded} added, ${result.gamesUpdated} updated")
                            if (result.gamesDeleted > 0) {
                                append(", ${result.gamesDeleted} removed")
                            }
                        }

                        if (result.errors.isEmpty()) {
                            Logger.info(TAG, "invoke: completing with success")
                            notificationManager.completePersistent(
                                key = NOTIFICATION_KEY,
                                title = "Sync complete",
                                subtitle = subtitle,
                                type = NotificationType.SUCCESS
                            )
                        } else {
                            Logger.info(TAG, "invoke: completing with errors")
                            notificationManager.completePersistent(
                                key = NOTIFICATION_KEY,
                                title = "Sync completed with errors",
                                subtitle = "${result.errors.size} platform(s) failed",
                                type = NotificationType.ERROR
                            )
                        }

                        librarySyncBus.emitSyncCompleted()
                        SyncLibraryResult.Success(result)
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
                    SyncLibraryResult.Error(e.message ?: "Sync failed")
                }
            }
        }
    }
}
