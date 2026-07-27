package com.nendo.argosy.data.platform

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class LaunchableApp(
    val packageName: String,
    val displayName: String,
    val icon: Drawable?
)

@Singleton
class InstalledAppResolver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Every launchable package except Argosy itself. Known emulators and preinstalled
     * system apps stay in: this backs the manual picker, which is the only way to bind
     * an emulator to a platform Argosy does not recognise.
     */
    fun getLaunchableApps(): List<LaunchableApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        val selfPackage = context.packageName

        return resolveInfos.asSequence()
            .filter { it.activityInfo.applicationInfo.packageName != selfPackage }
            .distinctBy { it.activityInfo.applicationInfo.packageName }
            .map { info ->
                LaunchableApp(
                    packageName = info.activityInfo.applicationInfo.packageName,
                    displayName = info.loadLabel(pm).toString(),
                    icon = runCatching { info.loadIcon(pm) }.getOrNull()
                )
            }
            .sortedBy { it.displayName.lowercase() }
            .toList()
    }

    fun isAppInstalled(packageName: String): Boolean {
        return runCatching {
            context.packageManager.getApplicationInfo(packageName, 0)
            true
        }.getOrDefault(false)
    }
}
