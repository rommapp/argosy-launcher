package com.nendo.argosy.data.cheats

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.util.Logger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay

@HiltWorker
class CheatsSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val gameDao: GameDao,
    private val cheatsRepository: CheatsRepository
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "CheatsSyncWorker"
        private const val WORK_NAME = "cheats_sync"
        private const val BATCH_SIZE = 50
        private const val DELAY_BETWEEN_REQUESTS_MS = 1000L

        fun schedule(context: Context) {
            if (!cheatsConfigured()) {
                Logger.debug(TAG, "CheatsDB not configured, skipping worker schedule")
                return
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<CheatsSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )

            Logger.info(TAG, "Scheduled cheats sync worker")
        }

        private fun cheatsConfigured(): Boolean {
            return com.nendo.argosy.BuildConfig.CHEATSDB_API_SECRET.isNotBlank()
        }
    }

    override suspend fun doWork(): Result {
        if (!cheatsRepository.isConfigured()) {
            Logger.info(TAG, "CheatsDB not configured, skipping sync")
            return Result.success()
        }

        Logger.info(TAG, "Starting background cheats sync")

        return try {
            var totalSynced = 0
            var hasMore = true

            while (hasMore) {
                val games = gameDao.getGamesWithoutCheats(BATCH_SIZE)

                if (games.isEmpty()) {
                    hasMore = false
                    continue
                }

                for (game in games) {
                    try {
                        cheatsRepository.syncCheatsForGame(game)
                        totalSynced++
                        delay(DELAY_BETWEEN_REQUESTS_MS)
                    } catch (e: Exception) {
                        Logger.error(TAG, "Failed to sync cheats for game ${game.id}: ${e.message}")
                    }
                }

                if (games.size < BATCH_SIZE) {
                    hasMore = false
                }
            }

            Logger.info(TAG, "Cheats sync complete, synced $totalSynced games")
            Result.success()
        } catch (e: Exception) {
            Logger.error(TAG, "Cheats sync failed", e)
            Result.retry()
        }
    }
}
