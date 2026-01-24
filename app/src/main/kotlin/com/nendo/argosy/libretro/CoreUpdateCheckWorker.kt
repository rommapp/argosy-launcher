package com.nendo.argosy.libretro

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nendo.argosy.data.local.dao.CoreVersionDao
import com.nendo.argosy.data.local.entity.CoreVersionEntity
import com.nendo.argosy.util.Logger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.concurrent.TimeUnit

private const val TAG = "CoreUpdateCheck"
private const val BUILDBOT_BASE = "https://buildbot.libretro.com/nightly/android/latest/arm64-v8a"

@HiltWorker
class CoreUpdateCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val coreVersionDao: CoreVersionDao,
    private val coreManager: LibretroCoreManager
) : CoroutineWorker(context, params) {

    companion object {
        private const val WORK_NAME = "core_update_check"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .build()

            val request = PeriodicWorkRequestBuilder<CoreUpdateCheckWorker>(
                1, TimeUnit.DAYS,
                2, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )

            Logger.info(TAG, "Scheduled periodic core update check | interval=1d | network=unmetered")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Logger.info(TAG, "Starting core update check")

        try {
            val downloadedCores = coreManager.getDownloadedCores()
            if (downloadedCores.isEmpty()) {
                Logger.info(TAG, "No downloaded cores to check")
                return@withContext Result.success()
            }

            Logger.info(TAG, "Checking ${downloadedCores.size} downloaded cores")
            var updatesFound = 0

            for (coreInfo in downloadedCores) {
                val latestVersion = fetchLatestVersion(coreInfo.fileName)
                if (latestVersion == null) {
                    Logger.warn(TAG, "Failed to check version for ${coreInfo.displayName}")
                    continue
                }

                val existing = coreVersionDao.getByCoreId(coreInfo.coreId)
                val updateAvailable = existing?.installedVersion != null &&
                    existing.installedVersion != latestVersion

                if (updateAvailable) {
                    updatesFound++
                    Logger.info(TAG, "Update available for ${coreInfo.displayName}: ${existing?.installedVersion} -> $latestVersion")
                }

                coreVersionDao.updateVersionCheck(
                    coreId = coreInfo.coreId,
                    latestVersion = latestVersion,
                    checkedAt = Instant.now(),
                    updateAvailable = updateAvailable
                )
            }

            Logger.info(TAG, "Core update check complete | checked=${downloadedCores.size} | updates=$updatesFound")
            Result.success()
        } catch (e: Exception) {
            Logger.error(TAG, "Core update check failed", e)
            Result.retry()
        }
    }

    private fun fetchLatestVersion(fileName: String): String? {
        return try {
            val url = URL("$BUILDBOT_BASE/$fileName.zip")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            try {
                connection.connect()
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    connection.getHeaderField("Last-Modified")
                        ?: connection.contentLengthLong.toString()
                } else {
                    null
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Logger.warn(TAG, "Failed to fetch version for $fileName: ${e.message}")
            null
        }
    }
}
