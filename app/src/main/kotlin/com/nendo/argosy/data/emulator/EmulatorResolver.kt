package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmulatorResolver @Inject constructor(
    private val emulatorDetector: EmulatorDetector,
    private val emulatorConfigDao: EmulatorConfigDao
) {
    fun resolveEmulatorId(packageName: String): String? {
        EmulatorRegistry.getByPackage(packageName)?.let { return it.id }
        EmulatorRegistry.findFamilyForPackage(packageName)?.let { return it.baseId }
        return emulatorDetector.getByPackage(packageName)?.id
    }

    suspend fun getEmulatorPackageForGame(gameId: Long, platformId: String): String? {
        val config = emulatorConfigDao.getByGameId(gameId)
            ?: emulatorConfigDao.getDefaultForPlatform(platformId)
        if (config?.packageName != null) return config.packageName
        if (emulatorDetector.installedEmulators.value.isEmpty()) {
            emulatorDetector.detectEmulators()
        }
        return emulatorDetector.getPreferredEmulator(platformId)?.def?.packageName
    }

    suspend fun getEmulatorIdForGame(gameId: Long, platformId: String): String? {
        return getEmulatorPackageForGame(gameId, platformId)?.let { resolveEmulatorId(it) }
    }
}
