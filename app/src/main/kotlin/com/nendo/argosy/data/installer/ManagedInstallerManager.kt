package com.nendo.argosy.data.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import com.nendo.argosy.data.emulator.ApkAssetMatcher
import com.nendo.argosy.data.emulator.ApkMatchResult
import com.nendo.argosy.data.local.entity.ManagedInstallerEntity
import com.nendo.argosy.data.remote.github.GitHubAsset
import com.nendo.argosy.data.remote.github.GitHubReleaseClient
import com.nendo.argosy.data.remote.github.ReleaseLookup
import com.nendo.argosy.data.update.AppInstaller
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.SafeCoroutineScope
import com.nendo.argosy.util.apkArchivePackageName
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.nendo.argosy.util.FileNames
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ManagedInstaller"
private const val BUFFER_SIZE = 64 * 1024
private const val CACHE_DIR_NAME = "managed_installer_apks"

sealed class InstallerJobState {
    data object Idle : InstallerJobState()
    data object Checking : InstallerJobState()
    data class Downloading(val progress: Float) : InstallerJobState()
    data object WaitingForInstall : InstallerJobState()
    data class NeedsVariantChoice(val assets: List<GitHubAsset>) : InstallerJobState()
    data class Failed(val reason: InstallerFailure) : InstallerJobState()
}

enum class InstallerFailure {
    NO_RELEASE,
    NO_APK,
    DOWNLOAD,
    RATE_LIMITED,
    NOT_FOUND,
    WRONG_PACKAGE
}

data class InstallerJob(val installerId: Long, val state: InstallerJobState)

@Singleton
class ManagedInstallerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ManagedInstallerRepository,
    private val releaseClient: GitHubReleaseClient,
    private val appInstaller: AppInstaller
) {
    private val scope = SafeCoroutineScope(Dispatchers.IO, "ManagedInstallerManager")

    private val _job = MutableStateFlow<InstallerJob?>(null)
    val job: StateFlow<InstallerJob?> = _job.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private var pending: PendingInstall? = null
    private var isReceiverRegistered = false

    private val packageAddedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val packageName = intent.data?.schemeSpecificPart ?: return
            val waiting = pending ?: return
            if (waiting.expectedPackage != null && waiting.expectedPackage != packageName) return
            scope.launch { completeInstall(waiting, packageName) }
        }
    }

    private suspend fun completeInstall(waiting: PendingInstall, packageName: String) {
        repository.recordInstall(
            id = waiting.installerId,
            tag = waiting.tag,
            packageName = packageName,
            assetVariant = waiting.variant
        )
        File(waiting.apkPath).delete()
        pending = null
        _job.value = null
        unregisterPackageReceiver()
    }

    fun isInstalled(packageName: String?): Boolean {
        if (packageName == null) return false
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun canInstallPackages(): Boolean = appInstaller.canInstallPackages(context)

    fun openInstallPermissionSettings() = appInstaller.openInstallPermissionSettings(context)

    fun openImeSettings() {
        context.startActivity(
            Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun showImePicker() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showInputMethodPicker()
    }

    fun isImeEnabled(packageName: String): Boolean {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            ?: return false
        return imm.enabledInputMethodList.any { it.packageName == packageName }
    }

    fun isImeActive(packageName: String): Boolean {
        val current = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        ) ?: return false
        return current.startsWith("$packageName/")
    }

    fun clearJob() {
        _job.value = null
    }

    fun reconcilePendingInstall() {
        val waiting = pending ?: return
        if (_job.value?.state !is InstallerJobState.WaitingForInstall) return
        val expected = waiting.expectedPackage
        val stampNow = expected?.let { packageStamp(it) }
        if (expected != null && stampNow != null && stampNow != waiting.stampBeforeInstall) {
            scope.launch { completeInstall(waiting, expected) }
            return
        }
        scope.launch {
            File(waiting.apkPath).delete()
            pending = null
            _job.value = null
            unregisterPackageReceiver()
        }
    }

    private fun packageStamp(packageName: String): PackageStamp? = try {
        val info = context.packageManager.getPackageInfo(packageName, 0)
        PackageStamp(
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            },
            lastUpdateTime = info.lastUpdateTime
        )
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    suspend fun refresh(rows: List<ManagedInstallerEntity>) {
        rows.forEach { row ->
            when (val lookup = latestRelease(row)) {
                is ReleaseLookup.Found -> repository.recordCheck(row.id, lookup.release.tagName)
                ReleaseLookup.NotFound -> repository.recordCheck(row.id, null)
                is ReleaseLookup.RateLimited -> return
                is ReleaseLookup.Failed -> Unit
            }
        }
    }

    private suspend fun latestRelease(row: ManagedInstallerEntity): ReleaseLookup =
        releaseClient.latestRelease(row.repoOwner, row.repoName, row.includePrereleases)

    fun install(row: ManagedInstallerEntity, chosenAsset: GitHubAsset? = null) {
        val current = _job.value
        if (current != null && !current.state.isTerminal()) {
            val resumingVariantChoice = chosenAsset != null &&
                current.installerId == row.id &&
                current.state is InstallerJobState.NeedsVariantChoice
            if (!resumingVariantChoice) return
        }
        _job.value = InstallerJob(row.id, InstallerJobState.Checking)

        scope.launch {
            val release = when (val lookup = latestRelease(row)) {
                is ReleaseLookup.Found -> lookup.release
                ReleaseLookup.NotFound -> return@launch fail(row.id, InstallerFailure.NOT_FOUND)
                is ReleaseLookup.RateLimited -> return@launch fail(row.id, InstallerFailure.RATE_LIMITED)
                is ReleaseLookup.Failed -> return@launch fail(row.id, InstallerFailure.NO_RELEASE)
            }

            val asset: GitHubAsset
            val variant: String?
            if (chosenAsset != null) {
                asset = chosenAsset
                variant = ApkAssetMatcher.extractVariantFromAssetName(chosenAsset.name)
                    ?: chosenAsset.name
            } else {
                val assets = SeededInstallers.installableAssets(row, release.assets)
                when (val match = ApkAssetMatcher.matchApk(assets, storedVariant = row.assetVariant)) {
                    is ApkMatchResult.SingleMatch -> {
                        asset = match.asset
                        variant = match.variant
                    }
                    is ApkMatchResult.MultipleMatches -> {
                        _job.value = InstallerJob(
                            row.id,
                            InstallerJobState.NeedsVariantChoice(match.assets)
                        )
                        return@launch
                    }
                    ApkMatchResult.NoMatch -> return@launch fail(row.id, InstallerFailure.NO_APK)
                }
            }

            val apkFile = download(row.id, asset)
                ?: return@launch fail(row.id, InstallerFailure.DOWNLOAD)

            val archivePackage = apkArchivePackageName(context, apkFile)
            if (archivePackage == null) {
                apkFile.delete()
                return@launch fail(row.id, InstallerFailure.DOWNLOAD)
            }
            if (row.packageName != null && row.packageName != archivePackage) {
                Logger.warn(TAG, "Installer ${row.id} served $archivePackage, expected ${row.packageName}")
                apkFile.delete()
                return@launch fail(row.id, InstallerFailure.WRONG_PACKAGE)
            }
            val expectedPackage = archivePackage
            pending = PendingInstall(
                installerId = row.id,
                apkPath = apkFile.absolutePath,
                tag = release.tagName,
                variant = variant,
                expectedPackage = expectedPackage,
                stampBeforeInstall = expectedPackage?.let { packageStamp(it) }
            )
            _job.value = InstallerJob(row.id, InstallerJobState.WaitingForInstall)
            registerPackageReceiver()
            withContext(Dispatchers.Main) { appInstaller.installApk(context, apkFile) }
        }
    }

    private fun fail(installerId: Long, reason: InstallerFailure) {
        Logger.warn(TAG, "Installer $installerId failed: $reason")
        _job.value = InstallerJob(installerId, InstallerJobState.Failed(reason))
    }

    private suspend fun download(installerId: Long, asset: GitHubAsset): File? =
        withContext(Dispatchers.IO) {
            val cacheDir = File(context.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
            val apkFile = File(cacheDir, FileNames.sanitize(asset.name))
            val partFile = File(cacheDir, apkFile.name + ".part")
            try {
                val request = Request.Builder()
                    .url(asset.downloadUrl)
                    .header("Accept", "application/octet-stream")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body ?: return@withContext null
                    val total = body.contentLength()
                    var read = 0L
                    FileOutputStream(partFile).use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var count: Int
                            while (input.read(buffer).also { count = it } != -1) {
                                output.write(buffer, 0, count)
                                read += count
                                if (total > 0) {
                                    _job.value = InstallerJob(
                                        installerId,
                                        InstallerJobState.Downloading(read.toFloat() / total)
                                    )
                                }
                            }
                        }
                    }
                    if (total > 0 && read != total) {
                        partFile.delete()
                        return@withContext null
                    }
                }
                apkFile.delete()
                if (!partFile.renameTo(apkFile)) {
                    partFile.delete()
                    return@withContext null
                }
                apkFile
            } catch (e: Exception) {
                Logger.error(TAG, "Download threw", e)
                partFile.delete()
                null
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
        } catch (_: Exception) {
        }
    }

    private data class PendingInstall(
        val installerId: Long,
        val apkPath: String,
        val tag: String,
        val variant: String?,
        val expectedPackage: String?,
        val stampBeforeInstall: PackageStamp?
    )

    private data class PackageStamp(val versionCode: Long, val lastUpdateTime: Long)
}

private fun InstallerJobState.isTerminal(): Boolean =
    this is InstallerJobState.Idle || this is InstallerJobState.Failed
