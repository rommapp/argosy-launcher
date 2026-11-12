package com.nendo.argosy.data.update

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nendo.argosy.data.remote.github.UpdateState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val appUpdateManager: AppUpdateManager
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "UpdateCheckWorker"
        private const val WORK_NAME = "update_check"
        internal const val INTERVAL_HOURS = 6L
        internal const val FLEX_HOURS = 1L
        internal val SCHEDULE_POLICY = ExistingPeriodicWorkPolicy.UPDATE

        internal fun buildRequest(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                INTERVAL_HOURS, TimeUnit.HOURS,
                FLEX_HOURS, TimeUnit.HOURS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                SCHEDULE_POLICY,
                buildRequest()
            )
            Log.d(TAG, "Update check scheduled every $INTERVAL_HOURS hours on any network")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Running periodic update check")
        return try {
            when (val state = appUpdateManager.check()) {
                null -> Log.d(TAG, "Update checks are off in this debug build")
                is UpdateState.UpdateAvailable -> Log.d(TAG, "Update available: ${state.release.tagName}")
                is UpdateState.UpToDate -> Log.d(TAG, "App is up to date")
                is UpdateState.Error -> Log.d(TAG, "Update check failed: ${state.message}")
                else -> Unit
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed", e)
            Result.success()
        }
    }
}
