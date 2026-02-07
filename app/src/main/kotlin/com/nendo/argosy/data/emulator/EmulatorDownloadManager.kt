package com.nendo.argosy.data.emulator

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.nendo.argosy.data.update.AppInstaller
import com.nendo.argosy.ui.screens.settings.EmulatorDownloadState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "EmulatorDownloadMgr"
private const val BUFFER_SIZE = 64 * 1024

data class EmulatorDownloadProgress(
    val emulatorId: String,
    val state: EmulatorDownloadState,
    val variant: String? = null
)

@Singleton
class EmulatorDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appInstaller: AppInstaller,
    private val emulatorUpdateManager: EmulatorUpdateManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _downloadProgress = MutableStateFlow<EmulatorDownloadProgress?>(null)
    val downloadProgress: StateFlow<EmulatorDownloadProgress?> = _downloadProgress.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private var pendingInstall: PendingEmulatorInstall? = null
    private var isReceiverRegistered = false

    private val packageAddedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_PACKAGE_ADDED ||
                intent.action == Intent.ACTION_PACKAGE_REPLACED) {
                val packageName = intent.data?.schemeSpecificPart ?: return
                Log.d(TAG, "Package installed/updated: $packageName")
                handlePackageInstalled(packageName)
            }
        }
    }

    fun downloadAndInstall(
        emulatorId: String,
        downloadUrl: String,
        assetName: String,
        variant: String?
    ) {
        if (_downloadProgress.value?.state is EmulatorDownloadState.Downloading) {
            Log.w(TAG, "Download already in progress")
            return
        }

        scope.launch {
            try {
                _downloadProgress.value = EmulatorDownloadProgress(
                    emulatorId = emulatorId,
                    state = EmulatorDownloadState.Downloading(0f),
                    variant = variant
                )

                val apkFile = downloadApk(emulatorId, downloadUrl, assetName)

                if (apkFile != null && apkFile.exists()) {
                    pendingInstall = PendingEmulatorInstall(
                        emulatorId = emulatorId,
                        apkPath = apkFile.absolutePath,
                        variant = variant
                    )

                    _downloadProgress.value = EmulatorDownloadProgress(
                        emulatorId = emulatorId,
                        state = EmulatorDownloadState.WaitingForInstall,
                        variant = variant
                    )

                    registerPackageReceiver()

                    withContext(Dispatchers.Main) {
                        appInstaller.installApk(context, apkFile)
                    }
                } else {
                    _downloadProgress.value = EmulatorDownloadProgress(
                        emulatorId = emulatorId,
                        state = EmulatorDownloadState.Failed("Download failed"),
                        variant = variant
                    )
                    scheduleErrorDismissal()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download failed for $emulatorId", e)
                _downloadProgress.value = EmulatorDownloadProgress(
                    emulatorId = emulatorId,
                    state = EmulatorDownloadState.Failed(e.message ?: "Download failed"),
                    variant = variant
                )
                scheduleErrorDismissal()
            }
        }
    }

    private suspend fun downloadApk(
        emulatorId: String,
        downloadUrl: String,
        assetName: String
    ): File? = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "emulator_apks")
        cacheDir.mkdirs()
        val apkFile = File(cacheDir, assetName)

        try {
            val request = Request.Builder()
                .url(downloadUrl)
                .header("Accept", "application/octet-stream")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "Download failed: ${response.code}")
                return@withContext null
            }

            val body = response.body ?: return@withContext null
            val contentLength = body.contentLength()
            var bytesRead = 0L

            FileOutputStream(apkFile).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var read: Int

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesRead += read

                        if (contentLength > 0) {
                            val progress = bytesRead.toFloat() / contentLength
                            _downloadProgress.value = EmulatorDownloadProgress(
                                emulatorId = emulatorId,
                                state = EmulatorDownloadState.Downloading(progress),
                                variant = pendingInstall?.variant
                            )
                        }
                    }
                }
            }

            Log.d(TAG, "Downloaded $bytesRead bytes to ${apkFile.absolutePath}")
            apkFile
        } catch (e: Exception) {
            Log.e(TAG, "Download exception", e)
            apkFile.delete()
            null
        }
    }

    private fun handlePackageInstalled(packageName: String) {
        val pending = pendingInstall ?: return

        val emulatorDef = EmulatorRegistry.getById(pending.emulatorId)
        if (emulatorDef?.packageName != packageName &&
            emulatorDef?.packagePatterns?.none { packageName.matches(Regex(it)) } != false) {
            return
        }

        scope.launch {
            try {
                val installedVersion = getInstalledVersion(packageName)
                if (installedVersion != null) {
                    emulatorUpdateManager.markAsInstalled(
                        emulatorId = pending.emulatorId,
                        version = installedVersion,
                        variant = pending.variant
                    )
                }

                _downloadProgress.value = EmulatorDownloadProgress(
                    emulatorId = pending.emulatorId,
                    state = EmulatorDownloadState.Installed,
                    variant = pending.variant
                )

                val apkFile = File(pending.apkPath)
                if (apkFile.exists()) {
                    apkFile.delete()
                }

                pendingInstall = null

                kotlinx.coroutines.delay(1500)
                _downloadProgress.value = null

                if (pendingInstall == null) {
                    unregisterPackageReceiver()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling install completion", e)
            }
        }
    }

    private fun getInstalledVersion(packageName: String): String? {
        return try {
            val info = context.packageManager.getPackageInfo(packageName, 0)
            info.versionName
        } catch (e: Exception) {
            null
        }
    }

    private fun scheduleErrorDismissal() {
        scope.launch {
            kotlinx.coroutines.delay(3000)
            if (_downloadProgress.value?.state is EmulatorDownloadState.Failed) {
                _downloadProgress.value = null
            }
        }
    }

    private fun registerPackageReceiver() {
        if (isReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(packageAddedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(packageAddedReceiver, filter)
        }
        isReceiverRegistered = true
    }

    private fun unregisterPackageReceiver() {
        if (!isReceiverRegistered) return
        try {
            context.unregisterReceiver(packageAddedReceiver)
            isReceiverRegistered = false
        } catch (_: Exception) {}
    }

    fun cancelDownload() {
        _downloadProgress.value = null
        pendingInstall = null
    }

    fun canInstallPackages(): Boolean = appInstaller.canInstallPackages(context)

    fun openInstallPermissionSettings() = appInstaller.openInstallPermissionSettings(context)
}

private data class PendingEmulatorInstall(
    val emulatorId: String,
    val apkPath: String,
    val variant: String?
)
