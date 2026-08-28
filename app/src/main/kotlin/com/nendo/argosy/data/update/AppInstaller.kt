package com.nendo.argosy.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppInstaller @Inject constructor() {

    fun canInstallPackages(context: Context): Boolean {
        return context.packageManager.canRequestPackageInstalls()
    }

    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun installApk(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        resolveSystemInstaller(context, intent)?.let { intent.setPackage(it) }

        context.startActivity(intent)
    }

    /**
     * The package installer this intent is addressed to, or null when none can be identified.
     *
     * Left implicit, the intent is offered to every app that registers the package-archive type,
     * along with the read grant on the update, so a sideloaded handler can put itself in front of
     * the update flow. Preferring a system handler keeps that from being a choice the user has to
     * make correctly. Null falls back to the implicit intent rather than blocking the update,
     * because a device with no system handler for this type still has to be able to install.
     */
    private fun resolveSystemInstaller(context: Context, intent: Intent): String? =
        context.packageManager.queryIntentActivities(intent, 0)
            .firstOrNull { it.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 }
            ?.activityInfo
            ?.packageName

    fun getApkCacheFile(context: Context, version: String): File {
        return File(context.cacheDir, "argosy-$version.apk")
    }

    fun clearCachedApks(context: Context) {
        context.cacheDir.listFiles()?.filter { it.name.startsWith("argosy-") && it.name.endsWith(".apk") }?.forEach { it.delete() }
    }

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
