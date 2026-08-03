package com.nendo.argosy.data.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean,
    val declaresGameCategory: Boolean = false
)

/**
 * Whether the package itself claims to be a game.
 *
 * `category` is what the Play Store listing sets and is the reliable signal on anything
 * installed from a store; the deprecated flag is the only thing older sideloaded builds set, so
 * both are consulted. A package that declares neither is not evidence of anything either way -
 * plenty of games set no category at all.
 */
private fun ApplicationInfo.declaresGameCategory(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && category == ApplicationInfo.CATEGORY_GAME) {
        return true
    }
    @Suppress("DEPRECATION")
    return (flags and ApplicationInfo.FLAG_IS_GAME) != 0
}

@Singleton
class AppsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val packageManager: PackageManager = context.packageManager

    val packageChanges: Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(Unit)
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }

        context.registerReceiver(receiver, filter)

        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }

    suspend fun getInstalledApps(includeSystemApps: Boolean = false): List<InstalledApp> = withContext(Dispatchers.IO) {
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val activities = packageManager.queryIntentActivities(launcherIntent, 0)

        activities
            .asSequence()
            .map { resolveInfo ->
                val appInfo = resolveInfo.activityInfo.applicationInfo
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                InstalledApp(
                    packageName = appInfo.packageName,
                    label = resolveInfo.loadLabel(packageManager).toString(),
                    isSystemApp = isSystem,
                    declaresGameCategory = appInfo.declaresGameCategory()
                )
            }
            .distinctBy { it.packageName }
            .filter { includeSystemApps || !it.isSystemApp }
            .filterNot { isArgosy(it.packageName) }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun getLaunchIntent(packageName: String): Intent? {
        return packageManager.getLaunchIntentForPackage(packageName)
    }

    /**
     * Every Argosy on the device, not just this one. A second build installed alongside - a debug
     * flavour next to a release - is a launcher too, and starting it hands it the display this one
     * is running on, which ends the session that opened the drawer.
     */
    private fun isArgosy(packageName: String): Boolean =
        packageName == context.packageName ||
            packageName == ARGOSY_BASE_PACKAGE ||
            packageName.startsWith("$ARGOSY_BASE_PACKAGE.")
}

private const val ARGOSY_BASE_PACKAGE = "com.nendo.argosy"
