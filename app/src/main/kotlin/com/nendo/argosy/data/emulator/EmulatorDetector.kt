package com.nendo.argosy.data.emulator

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.util.Log
import com.nendo.argosy.data.platform.PlatformDefinitions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "EmulatorDetector"

data class InstalledEmulator(
    val def: EmulatorDef,
    val versionName: String?,
    val versionCode: Long
)

@Singleton
class EmulatorDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _installedEmulators = MutableStateFlow<List<InstalledEmulator>>(emptyList())
    val installedEmulators: StateFlow<List<InstalledEmulator>> = _installedEmulators.asStateFlow()

    private val packageManager: PackageManager = context.packageManager

    suspend fun detectEmulators(): List<InstalledEmulator> = withContext(Dispatchers.IO) {
        val installed = mutableListOf<InstalledEmulator>()
        val detectedPackages = mutableSetOf<String>()

        for (emulatorDef in EmulatorRegistry.getAll()) {
            try {
                val packageInfo = packageManager.getPackageInfo(emulatorDef.packageName, 0)
                installed.add(createInstalledEmulator(emulatorDef, packageInfo))
                detectedPackages.add(emulatorDef.packageName)
            } catch (_: PackageManager.NameNotFoundException) {
            }
        }

        detectEmulatorFamilies(installed, detectedPackages)

        _installedEmulators.value = installed
        installed
    }

    private fun detectEmulatorFamilies(
        installed: MutableList<InstalledEmulator>,
        detectedPackages: MutableSet<String>
    ) {
        @Suppress("DEPRECATION")
        val allPackages = try {
            packageManager.getInstalledPackages(0)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get installed packages", e)
            return
        }

        for (packageInfo in allPackages) {
            val packageName = packageInfo.packageName
            if (packageName in detectedPackages) continue

            val family = EmulatorRegistry.findFamilyForPackage(packageName)
            if (family != null) {
                val def = EmulatorRegistry.createDefFromFamily(family, packageName)
                installed.add(createInstalledEmulator(def, packageInfo))
                detectedPackages.add(packageName)
            }
        }
    }

    private fun createInstalledEmulator(def: EmulatorDef, packageInfo: PackageInfo): InstalledEmulator {
        val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        return InstalledEmulator(
            def = def,
            versionName = packageInfo.versionName,
            versionCode = versionCode
        )
    }

    fun isInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun getInstalledForPlatform(platformId: String): List<InstalledEmulator> {
        val canonical = PlatformDefinitions.getCanonicalId(platformId)
        return _installedEmulators.value.filter { canonical in it.def.supportedPlatforms }
    }

    fun getPreferredEmulator(platformId: String): InstalledEmulator? {
        val canonical = PlatformDefinitions.getCanonicalId(platformId)
        val installed = getInstalledForPlatform(canonical)
        if (installed.isEmpty()) return null

        val recommended = EmulatorRegistry.getRecommendedEmulators()[canonical]
        if (recommended != null) {
            for (emulatorId in recommended) {
                val match = installed.find { it.def.id == emulatorId }
                if (match != null) return match
            }
        }

        val dominated = setOf("retroarch", "retroarch_64", "lemuroid")
        val standalone = installed.filterNot { it.def.id in dominated }
        return standalone.firstOrNull() ?: installed.first()
    }

    fun hasAnyEmulator(platformId: String): Boolean {
        val canonical = PlatformDefinitions.getCanonicalId(platformId)
        return _installedEmulators.value.any { canonical in it.def.supportedPlatforms }
    }

    fun getByPackage(packageName: String): EmulatorDef? {
        EmulatorRegistry.getByPackage(packageName)?.let { return it }

        _installedEmulators.value
            .find { it.def.packageName == packageName }
            ?.let { return it.def }

        return null
    }
}
