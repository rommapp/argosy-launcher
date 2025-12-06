package com.nendo.argosy.data.update

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nendo.argosy.BuildConfig
import com.nendo.argosy.data.remote.github.UpdateRepository
import com.nendo.argosy.data.remote.github.UpdateState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val updateRepository: UpdateRepository
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "UpdateCheckWorker"
        private const val WORK_NAME = "update_check"

        fun schedule(context: Context) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Skipping update check scheduling for debug build")
                return
            }

            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                12, TimeUnit.HOURS,
                1, TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )

            Log.d(TAG, "Update check scheduled every 12 hours")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Skipping update check for debug build")
            return Result.success()
        }

        Log.d(TAG, "Running periodic update check")

        return try {
            when (val state = updateRepository.checkForUpdates()) {
                is UpdateState.UpdateAvailable -> {
                    Log.d(TAG, "Update available: ${state.release.tagName}")
                }
                is UpdateState.UpToDate -> {
                    Log.d(TAG, "App is up to date")
                }
                is UpdateState.Error -> {
                    Log.d(TAG, "Update check failed: ${state.message}")
                }
                else -> {}
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed", e)
            Result.success()
        }
    }
}
