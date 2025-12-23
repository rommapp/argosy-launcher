package com.nendo.argosy.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.download.DownloadCompletionEvent
import com.nendo.argosy.data.download.DownloadManager
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.model.GameSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ApkInstallManager"
private const val ANDROID_PLATFORM_ID = "android"

data class PendingApkInstall(
    val gameId: Long,
    val apkPath: String,
    val expectedPackageName: String?
)

sealed class ApkInstallState {
    data object Idle : ApkInstallState()
    data class WaitingForInstall(val gameId: Long, val apkPath: String) : ApkInstallState()
    data class InstallComplete(val gameId: Long, val packageName: String) : ApkInstallState()
    data class InstallFailed(val gameId: Long, val reason: String) : ApkInstallState()
}

@Singleton
class ApkInstallManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager,
    private val gameDao: GameDao,
    private val appInstaller: AppInstaller,
    private val imageCacheManager: ImageCacheManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow<ApkInstallState>(ApkInstallState.Idle)
    val state: StateFlow<ApkInstallState> = _state.asStateFlow()

    private val pendingInstalls = mutableMapOf<String, PendingApkInstall>()

    private val packageAddedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_PACKAGE_ADDED) {
                val packageName = intent.data?.schemeSpecificPart ?: return
                Log.d(TAG, "Package installed: $packageName")
                handlePackageInstalled(packageName)
            }
        }
    }

    private var isReceiverRegistered = false

    init {
        observeDownloadCompletions()
    }

    private fun observeDownloadCompletions() {
        scope.launch {
            downloadManager.completionEvents.collect { event ->
                handleDownloadCompletion(event)
            }
        }
    }

    private suspend fun handleDownloadCompletion(event: DownloadCompletionEvent) {
        val game = gameDao.getById(event.gameId) ?: return

        if (game.platformId != ANDROID_PLATFORM_ID) return
        if (!event.localPath.endsWith(".apk", ignoreCase = true)) return

        Log.d(TAG, "APK download complete: ${event.localPath}")

        val apkFile = File(event.localPath)
        if (!apkFile.exists()) {
            Log.e(TAG, "APK file not found: ${event.localPath}")
            return
        }

        val expectedPackageName = getApkPackageName(apkFile)
        if (expectedPackageName == null) {
            Log.e(TAG, "Could not read package name from APK")
            return
        }

        pendingInstalls[expectedPackageName] = PendingApkInstall(
            gameId = event.gameId,
            apkPath = event.localPath,
            expectedPackageName = expectedPackageName
        )

        _state.value = ApkInstallState.WaitingForInstall(event.gameId, event.localPath)

        registerPackageReceiver()
        appInstaller.installApk(context, apkFile)
    }

    private fun getApkPackageName(apkFile: File): String? {
        return try {
            val packageInfo: PackageInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageArchiveInfo(
                    apkFile.absolutePath,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            }
            packageInfo?.packageName
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get package name from APK: ${e.message}")
            null
        }
    }

    private fun handlePackageInstalled(packageName: String) {
        val pending = pendingInstalls.remove(packageName)
        if (pending == null) {
            Log.d(TAG, "Package $packageName not in pending installs")
            return
        }

        scope.launch {
            try {
                gameDao.getById(pending.gameId)?.let { game ->
                    val updatedGame = game.copy(
                        packageName = packageName,
                        source = GameSource.ANDROID_APP,
                        localPath = null
                    )
                    gameDao.update(updatedGame)

                    imageCacheManager.queueAppIconCache(pending.gameId, packageName)

                    Log.d(TAG, "Game ${game.title} updated with package $packageName")
                }

                val apkFile = File(pending.apkPath)
                if (apkFile.exists()) {
                    apkFile.delete()
                    Log.d(TAG, "Deleted APK file: ${pending.apkPath}")
                }

                _state.value = ApkInstallState.InstallComplete(pending.gameId, packageName)

                if (pendingInstalls.isEmpty()) {
                    unregisterPackageReceiver()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update game after install: ${e.message}")
                _state.value = ApkInstallState.InstallFailed(pending.gameId, e.message ?: "Unknown error")
            }
        }
    }

    private fun registerPackageReceiver() {
        if (isReceiverRegistered) return
        val filter = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply {
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(packageAddedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(packageAddedReceiver, filter)
        }
        isReceiverRegistered = true
        Log.d(TAG, "Package receiver registered")
    }

    private fun unregisterPackageReceiver() {
        if (!isReceiverRegistered) return
        try {
            context.unregisterReceiver(packageAddedReceiver)
            isReceiverRegistered = false
            Log.d(TAG, "Package receiver unregistered")
        } catch (_: Exception) {
        }
    }

    fun canInstallPackages(): Boolean {
        return appInstaller.canInstallPackages(context)
    }

    fun openInstallPermissionSettings() {
        appInstaller.openInstallPermissionSettings(context)
    }

    fun resetState() {
        _state.value = ApkInstallState.Idle
    }

    suspend fun installApkForGame(gameId: Long): Boolean {
        val game = gameDao.getById(gameId) ?: return false
        val localPath = game.localPath ?: return false

        if (!localPath.endsWith(".apk", ignoreCase = true)) return false

        val apkFile = File(localPath)
        if (!apkFile.exists()) return false

        val expectedPackageName = getApkPackageName(apkFile) ?: return false

        pendingInstalls[expectedPackageName] = PendingApkInstall(
            gameId = gameId,
            apkPath = localPath,
            expectedPackageName = expectedPackageName
        )

        _state.value = ApkInstallState.WaitingForInstall(gameId, localPath)

        registerPackageReceiver()
        appInstaller.installApk(context, apkFile)
        return true
    }
}
