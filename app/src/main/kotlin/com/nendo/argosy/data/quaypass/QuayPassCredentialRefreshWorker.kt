package com.nendo.argosy.data.quaypass

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
class QuayPassCredentialRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val credentialManager: QuayPassCredentialManager,
    private val accountSwitchMarkerStore: com.nendo.argosy.data.preferences.AccountSwitchMarkerStore
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (accountSwitchMarkerStore.isSwitching()) {
            Logger.info(TAG, "Account switch in progress, deferring QuayPass credential refresh")
            return Result.retry()
        }
        return try {
            credentialManager.refreshIfNeeded()
            Result.success()
        } catch (t: Throwable) {
            Logger.error(TAG, "QuayPass credential refresh failed; retry", t)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "QuayPassRefreshWorker"
        private const val WORK_NAME = "quaypass_credential_refresh"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<QuayPassCredentialRefreshWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Logger.info(TAG, "Scheduled QuayPass credential refresh worker (daily)")
        }
    }
}
