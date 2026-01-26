package com.nendo.argosy.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nendo.argosy.data.repository.RetroAchievementsRepository
import com.nendo.argosy.util.Logger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class AchievementSubmissionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val raRepository: RetroAchievementsRepository
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "AchievementSubmissionWorker"
        private const val WORK_NAME = "ra_achievement_submission"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<AchievementSubmissionWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )

            Logger.info(TAG, "Scheduled achievement submission worker")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        if (!raRepository.isLoggedIn()) {
            Logger.info(TAG, "Not logged in to RA, skipping submission")
            return Result.success()
        }

        val pendingCount = raRepository.getPendingCount()
        if (pendingCount == 0) {
            Logger.debug(TAG, "No pending achievements to submit")
            return Result.success()
        }

        Logger.info(TAG, "Submitting $pendingCount pending achievements")

        return try {
            val submitted = raRepository.submitPendingAchievements()
            Logger.info(TAG, "Submitted $submitted achievements")
            Result.success()
        } catch (e: Exception) {
            Logger.error(TAG, "Achievement submission failed, will retry", e)
            Result.retry()
        }
    }
}
