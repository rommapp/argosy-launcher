package com.nendo.argosy.ui.startup

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationDuration
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.LibraryPointerRepair
import com.nendo.argosy.domain.usecase.libretro.LibretroMigrationUseCase
import com.nendo.argosy.domain.usecase.libretro.MigrationResult
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val WEEKLY_INTEGRITY_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
private const val TAG = "StartupMaintenance"

/**
 * The startup maintenance every launcher surface waits on before its first home render. One pass
 * runs per process: the first [awaitPass] caller starts it and every other surface joins the same
 * pass. [status] names the step in progress for the splash. Integrity, server-removed pruning,
 * collections and the library pointer repair run behind the home once the pass ends.
 */
@Singleton
class StartupMaintenanceCoordinator @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val gameRepository: GameRepository,
    private val romMRepository: RomMRepository,
    private val libretroMigrationUseCase: LibretroMigrationUseCase,
    private val notificationManager: NotificationManager,
    private val libraryPointerRepair: LibraryPointerRepair
) {
    private class Step(
        val key: String,
        @StringRes val label: Int,
        val isDue: suspend () -> Boolean = { true },
        val run: suspend () -> Unit
    )

    private val scope = SafeCoroutineScope(Dispatchers.IO, "StartupMaintenance")

    private val _status = MutableStateFlow<Int?>(null)
    val status: StateFlow<Int?> = _status.asStateFlow()

    private val pass: Deferred<Unit> = scope.async(start = CoroutineStart.LAZY) {
        blockingSteps().forEach { runStep(it) }
        _status.value = null
        scope.launch {
            backgroundSteps().forEach { runStep(it, showStatus = false) }
            libraryPointerRepair.start()
        }
    }

    suspend fun awaitPass() {
        pass.await()
    }

    private suspend fun runStep(step: Step, showStatus: Boolean = true) {
        try {
            if (!step.isDue()) return
            if (showStatus) _status.value = step.label
            step.run()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.error(TAG, "Startup step ${step.key} failed, continuing", e)
        }
    }

    private fun blockingSteps(): List<Step> = listOf(
        Step(
            key = "emulators",
            label = R.string.ui_startup_status_checking_emulators
        ) {
            libretroMigrationUseCase.pinExistingPs2Emulator()
            libretroMigrationUseCase.pinExistingBuiltinPriorityEmulators()
            runBuiltinEmulatorMigration()
            libretroMigrationUseCase.cleanupRemovedCores()
        }
    )

    private fun backgroundSteps(): List<Step> = listOf(
        Step(
            key = "integrity",
            label = R.string.ui_startup_status_scanning_roms,
            isDue = { isWeeklyIntegrityCheckDue() }
        ) {
            val validated = gameRepository.validateLocalFiles()
            val discovered = gameRepository.discoverLocalFiles()
            if (validated != null && discovered != null) {
                preferencesRepository.setLastIntegrityCheckTime(System.currentTimeMillis())
            }
        },
        Step(
            key = "server_removed",
            label = R.string.ui_startup_status_scanning_roms,
            isDue = { preferencesRepository.userPreferences.first().syncFilters.deleteOrphans }
        ) {
            romMRepository.pruneServerRemovedGames()
        },
        Step(
            key = "collections",
            label = R.string.ui_startup_status_syncing_collections,
            isDue = { romMRepository.isConnected() }
        ) {
            romMRepository.syncCollections()
        }
    )

    private suspend fun isWeeklyIntegrityCheckDue(): Boolean {
        val prefs = preferencesRepository.userPreferences.first()
        if (!prefs.weeklyIntegrityCheckEnabled) return false
        val lastCheck = prefs.lastIntegrityCheckTime ?: return true
        return System.currentTimeMillis() - lastCheck >= WEEKLY_INTEGRITY_INTERVAL_MS
    }

    private suspend fun runBuiltinEmulatorMigration() {
        val result = libretroMigrationUseCase.runMigrationIfNeeded()
        if (result !is MigrationResult.Success || result.coresDownloaded.isEmpty()) return
        notificationManager.show(
            title = NotificationText.Res(R.string.ui_builtin_cores_ready_title),
            subtitle = NotificationText.Plural(
                R.plurals.ui_builtin_cores_ready_subtitle,
                result.coresDownloaded.size,
                listOf(result.coresDownloaded.size)
            ),
            type = NotificationType.INFO,
            duration = NotificationDuration.MEDIUM
        )
    }
}
