package com.nendo.argosy.data.emulator

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

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

        for (emulatorDef in EmulatorRegistry.getAll()) {
            try {
                val packageInfo = packageManager.getPackageInfo(emulatorDef.packageName, 0)
                val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }

                installed.add(
                    InstalledEmulator(
                        def = emulatorDef,
                        versionName = packageInfo.versionName,
                        versionCode = versionCode
                    )
                )
            } catch (e: PackageManager.NameNotFoundException) {
                // Emulator not installed
            }
        }

        _installedEmulators.value = installed
        installed
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
        return _installedEmulators.value.filter { platformId in it.def.supportedPlatforms }
    }

    fun getPreferredEmulator(platformId: String): InstalledEmulator? {
        val installed = getInstalledForPlatform(platformId)
        if (installed.isEmpty()) return null

        val recommended = EmulatorRegistry.getRecommendedEmulators()[platformId]
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
        return _installedEmulators.value.any { platformId in it.def.supportedPlatforms }
    }
}
