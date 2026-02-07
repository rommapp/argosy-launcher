package com.nendo.argosy.data.emulator

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.nendo.argosy.data.local.dao.EmulatorUpdateDao
import com.nendo.argosy.data.local.entity.EmulatorUpdateEntity
import com.nendo.argosy.data.remote.github.EmulatorUpdateCheckResult
import com.nendo.argosy.data.remote.github.EmulatorUpdateRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

sealed class UpdateCheckState {
    data object Idle : UpdateCheckState()
    data object Checking : UpdateCheckState()
    data class Completed(val updatesFound: Int) : UpdateCheckState()
    data class Error(val message: String) : UpdateCheckState()
}

@Singleton
class EmulatorUpdateManager @Inject constructor(
    private val emulatorUpdateRepository: EmulatorUpdateRepository,
    private val emulatorDetector: EmulatorDetector,
    private val emulatorUpdateDao: EmulatorUpdateDao,
    private val dataStore: DataStore<Preferences>,
    @ApplicationContext private val context: Context
) {
    private val packageManager: PackageManager = context.packageManager
    companion object {
        private const val TAG = "EmulatorUpdateManager"
        private val CHECK_INTERVAL = Duration.ofHours(24)
        private val LAST_CHECK_KEY = longPreferencesKey("emulator_update_last_check")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _checkState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val checkState: StateFlow<UpdateCheckState> = _checkState.asStateFlow()

    val updateCount: Flow<Int> = emulatorUpdateDao.observeUpdateCount()

    val availableUpdates: Flow<List<EmulatorUpdateEntity>> =
        emulatorUpdateDao.observeAvailableUpdates()

    val platformUpdateCounts: Flow<Map<String, Int>> = availableUpdates.map { updates ->
        val platformCounts = mutableMapOf<String, Int>()
        for (update in updates) {
            val emulatorDef = EmulatorRegistry.getById(update.emulatorId) ?: continue
            for (platformSlug in emulatorDef.supportedPlatforms) {
                platformCounts[platformSlug] = (platformCounts[platformSlug] ?: 0) + 1
            }
        }
        platformCounts
    }

    fun checkIfNeeded() {
        scope.launch {
            val lastCheck = getLastCheckTime()
            val now = Instant.now()

            if (lastCheck == null || Duration.between(lastCheck, now) > CHECK_INTERVAL) {
                Log.d(TAG, "Check needed: lastCheck=$lastCheck, now=$now")
                checkForUpdates()
            } else {
                Log.d(TAG, "Check not needed: last check was ${Duration.between(lastCheck, now).toHours()} hours ago")
            }
        }
    }

    fun forceCheck() {
        Log.d(TAG, "Force check triggered")
        scope.launch {
            checkForUpdates(ignoreCache = true)
        }
    }

    suspend fun checkForUpdates(ignoreCache: Boolean = false): UpdateCheckState = withContext(Dispatchers.IO) {
        if (_checkState.value is UpdateCheckState.Checking) {
            return@withContext _checkState.value
        }

        _checkState.value = UpdateCheckState.Checking
        Log.d(TAG, "Starting emulator update check (ignoreCache=$ignoreCache)")

        try {
            val installedEmulators = emulatorDetector.installedEmulators.first()
            val updateCheckable = EmulatorRegistry.getUpdateCheckable()

            Log.d(TAG, "Installed emulators: ${installedEmulators.map { it.def.id }}")
            Log.d(TAG, "Update checkable: ${updateCheckable.map { it.id }}")

            val emulatorsToCheck = updateCheckable.filter { def ->
                installedEmulators.any { it.def.packageName == def.packageName }
            }

            Log.d(TAG, "Checking ${emulatorsToCheck.size} emulators for updates: ${emulatorsToCheck.map { it.id }}")

            var updatesFound = 0

            for (emulatorDef in emulatorsToCheck) {
                val installed = installedEmulators.find { it.def.packageName == emulatorDef.packageName }
                val installedVersion = installed?.let { getInstalledVersion(it.def.packageName) }

                val cached = emulatorUpdateDao.getByEmulatorId(emulatorDef.id)
                val storedVariant = cached?.installedVariant

                if (storedVariant != null && installed != null) {
                    val currentVariant = ApkAssetMatcher.extractVariantFromAssetName(installed.def.packageName)
                    if (currentVariant != null && currentVariant != storedVariant) {
                        Log.d(TAG, "Variant desync detected for ${emulatorDef.id}: stored=$storedVariant, current=$currentVariant")
                        emulatorUpdateDao.clearInstalledVariant(emulatorDef.id)
                    }
                }

                val result = emulatorUpdateRepository.checkForUpdate(
                    emulator = emulatorDef,
                    installedVersion = installedVersion,
                    storedVariant = cached?.installedVariant
                )

                if (result is EmulatorUpdateCheckResult.UpdateAvailable) {
                    updatesFound++
                }
            }

            if (!ignoreCache) {
                setLastCheckTime(Instant.now())
            }

            val state = UpdateCheckState.Completed(updatesFound)
            _checkState.value = state
            Log.d(TAG, "Update check complete: $updatesFound updates found")
            state
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed", e)
            val state = UpdateCheckState.Error(e.message ?: "Unknown error")
            _checkState.value = state
            state
        }
    }

    suspend fun getUpdateForEmulator(emulatorId: String): EmulatorUpdateEntity? {
        return emulatorUpdateDao.getByEmulatorId(emulatorId)
    }

    suspend fun getUpdatesForPlatform(platformSlug: String): List<EmulatorUpdateEntity> {
        val platformEmulators = EmulatorRegistry.getForPlatform(platformSlug)
        val updates = emulatorUpdateDao.getAvailableUpdates()
        return updates.filter { update ->
            platformEmulators.any { it.id == update.emulatorId }
        }
    }

    suspend fun hasUpdateForPlatform(platformSlug: String): Boolean {
        return getUpdatesForPlatform(platformSlug).isNotEmpty()
    }

    suspend fun markAsInstalled(emulatorId: String, version: String, variant: String?) {
        emulatorUpdateRepository.markAsInstalled(emulatorId, version, variant)
    }

    fun resetCheckState() {
        _checkState.value = UpdateCheckState.Idle
    }

    private fun getInstalledVersion(packageName: String): String? {
        return try {
            val info = packageManager.getPackageInfo(packageName, 0)
            info.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    private suspend fun getLastCheckTime(): Instant? {
        return dataStore.data.map { prefs ->
            prefs[LAST_CHECK_KEY]?.let { Instant.ofEpochMilli(it) }
        }.first()
    }

    private suspend fun setLastCheckTime(time: Instant) {
        dataStore.edit { prefs ->
            prefs[LAST_CHECK_KEY] = time.toEpochMilli()
        }
    }
}
