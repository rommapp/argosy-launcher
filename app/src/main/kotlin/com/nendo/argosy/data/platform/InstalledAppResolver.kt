package com.nendo.argosy.data.platform

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import com.nendo.argosy.data.emulator.EmulatorRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class InstalledApp(
    val packageName: String,
    val displayName: String,
    val icon: Drawable?
)

/**
 * Lists launchable user-installed apps suitable for ad-hoc emulator binding. Excludes system apps
 * (emulators are never system-installed), Argosy itself, and packages already in [EmulatorRegistry]
 * since those go through the standard emulator picker.
 */
@Singleton
class InstalledAppResolver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun getLaunchableUserApps(): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        val selfPackage = context.packageName

        return resolveInfos.asSequence()
            .filter { (it.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .filter { it.activityInfo.applicationInfo.packageName != selfPackage }
            .filter { !EmulatorRegistry.isKnownPackage(it.activityInfo.applicationInfo.packageName) }
            .distinctBy { it.activityInfo.applicationInfo.packageName }
            .map { info ->
                InstalledApp(
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
