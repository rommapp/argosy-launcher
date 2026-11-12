package com.nendo.argosy.data.setup

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.nendo.argosy.data.model.SetupAccess
import com.nendo.argosy.data.permissions.PermissionRegistry
import com.nendo.argosy.data.permissions.SaveSyncGate
import com.nendo.argosy.data.preferences.AppPreferences
import com.nendo.argosy.data.preferences.AppPreferencesRepository
import com.nendo.argosy.data.preferences.StoragePreferences
import com.nendo.argosy.data.preferences.StoragePreferencesRepository
import com.nendo.argosy.data.preferences.SyncPreferences
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.domain.model.ArgosyPermission
import com.nendo.argosy.domain.model.PermissionAvailability
import com.nendo.argosy.domain.model.SetupFacts
import com.nendo.argosy.domain.model.SetupState
import com.nendo.argosy.domain.setup.SetupRouter
import com.nendo.argosy.util.DisplayAffinityHelper
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.SafeCoroutineScope
import com.nendo.argosy.util.SecondaryHomeComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single source of setup progress. [state] is derived from stored confirmations and live
 * access each time either changes; nothing records "setup finished".
 */
@Singleton
class SetupStateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncPreferences: SyncPreferencesRepository,
    private val storagePreferences: StoragePreferencesRepository,
    private val appPreferences: AppPreferencesRepository,
    private val legacyMigration: LegacySetupMigration,
    private val permissionRegistry: PermissionRegistry,
    private val saveSyncGate: SaveSyncGate,
    private val displayAffinityHelper: DisplayAffinityHelper
) {
    private val scope = SafeCoroutineScope(Dispatchers.IO, TAG)

    private val migration = scope.async(start = CoroutineStart.LAZY) { migrateLegacySetup() }

    private val _access = MutableStateFlow<SetupAccess?>(null)

    private val foregroundObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            refresh()
        }
    }

    val facts: Flow<SetupFacts> = combine(
        syncPreferences.preferences,
        storagePreferences.preferences,
        appPreferences.preferences,
        _access.filterNotNull()
    ) { sync, storage, app, access -> factsOf(sync, storage, app, access) }
        .onStart {
            migration.await()
            if (_access.value == null) _access.compareAndSet(null, readAccess())
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    val state: Flow<SetupState> = facts.map { SetupRouter.derive(it) }.distinctUntilChanged()

    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(foregroundObserver)
    }

    fun refresh() {
        scope.launch { _access.value = readAccess() }
    }

    suspend fun confirmLibrarySource() = syncPreferences.confirmRommLibrary()

    suspend fun confirmRomFolder(path: String) = storagePreferences.confirmRomStoragePath(path)

    /**
     * Records the save sync answer. An answer of on goes through [SaveSyncGate] first and is not
     * recorded, returning false, while the person leaves a sync-required permission missing.
     */
    suspend fun answerSaveSync(enabled: Boolean): Boolean {
        if (enabled && !saveSyncGate.clearToEnable()) return false
        syncPreferences.answerSaveSync(enabled)
        return true
    }

    private suspend fun migrateLegacySetup() {
        try {
            legacyMigration.run()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn(TAG, "legacy setup migration failed", e)
        }
    }

    private fun readAccess(): SetupAccess {
        val usage = permissionRegistry.availabilityOf(ArgosyPermission.USAGE_ACCESS)
        return SetupAccess(
            allFiles = permissionRegistry.availabilityOf(ArgosyPermission.ALL_FILES_ACCESS) ==
                PermissionAvailability.GRANTED,
            usageAccess = usage == PermissionAvailability.GRANTED,
            usageAccessGrantable = usage != PermissionAvailability.UNAVAILABLE,
            notifications = permissionRegistry.availabilityOf(ArgosyPermission.NOTIFICATIONS) ==
                PermissionAvailability.GRANTED,
            dualScreenDevice = displayAffinityHelper.hasPhysicalSecondaryDisplay,
            defaultHome = SecondaryHomeComponent.isDefaultHome(context)
        )
    }

    private companion object {
        const val TAG = "SetupState"

        fun factsOf(
            sync: SyncPreferences,
            storage: StoragePreferences,
            app: AppPreferences,
            access: SetupAccess
        ) = SetupFacts(
            librarySignedIn = !sync.rommBaseUrl.isNullOrBlank() && !sync.rommToken.isNullOrBlank(),
            librarySourceConfirmed = sync.librarySourceConfirmed,
            allFilesAccess = access.allFiles,
            usageAccess = access.usageAccess,
            usageAccessGrantable = access.usageAccessGrantable,
            notifications = access.notifications,
            romFolderPresent = !storage.romStoragePath.isNullOrBlank(),
            romFolderConfirmed = storage.romStoragePathConfirmed,
            saveSyncAnswered = sync.saveSyncAnswered,
            dualScreenDevice = access.dualScreenDevice,
            defaultHome = access.defaultHome,
            defaultHomeDeclined = app.defaultHomeDeclined
        )
    }
}
