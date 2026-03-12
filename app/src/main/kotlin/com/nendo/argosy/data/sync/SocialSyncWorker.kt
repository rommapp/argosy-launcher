package com.nendo.argosy.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nendo.argosy.util.Logger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class SocialSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val socialSyncCoordinator: SocialSyncCoordinator
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SocialSyncWorker"
        private const val WORK_NAME = "social_sync_periodic"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SocialSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )

            Logger.info(TAG, "Scheduled periodic social sync worker (every 6h)")
        }
    }

    override suspend fun doWork(): Result {
        Logger.info(TAG, "Starting social sync")

        return try {
            val result = socialSyncCoordinator.processQueue()
            when (result) {
                is SocialSyncCoordinator.ProcessResult.NotConnected -> {
                    Logger.info(TAG, "Not connected, will retry")
                    Result.retry()
                }
                is SocialSyncCoordinator.ProcessResult.Completed -> {
                    Logger.info(TAG, "Completed: processed=${result.processed}, failed=${result.failed}")
                    Result.success()
                }
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Social sync failed, will retry", e)
            Result.retry()
        }
    }
}
