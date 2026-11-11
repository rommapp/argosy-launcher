package com.nendo.argosy.data.setup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.nendo.argosy.data.preferences.AppPreferencesRepository
import com.nendo.argosy.data.preferences.StoragePreferencesRepository
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LegacySetupMigrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val legacyFlag = booleanPreferencesKey("first_run_complete")

    private class Stores(val dataStore: DataStore<Preferences>) {
        val app = AppPreferencesRepository(dataStore)
        val sync = SyncPreferencesRepository(dataStore)
        val storage = StoragePreferencesRepository(dataStore)
        val migration = LegacySetupMigration(app, sync, storage)
    }

    private fun TestScope.stores(): Stores {
        val file = tempFolder.newFile("settings.preferences_pb")
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        return Stores(PreferenceDataStoreFactory.create(scope = scope) { file })
    }

    private suspend fun Stores.seedFinishedInstall(flag: Boolean) {
        sync.setRomMCredentials("https://romm.example.com/", "token", "player", 7)
        storage.setRomStoragePath("/storage/emulated/0/ROMs")
        dataStore.edit { it[legacyFlag] = flag }
    }

    @Test
    fun `an install that finished the old wizard has its values confirmed`() = runTest {
        val stores = stores()
        stores.seedFinishedInstall(flag = true)

        stores.migration.run()

        val sync = stores.sync.preferences.first()
        assertTrue(sync.librarySourceConfirmed)
        assertTrue(sync.saveSyncAnswered)
        assertTrue(stores.storage.preferences.first().romStoragePathConfirmed)
        assertFalse(stores.app.isLegacyFirstRunComplete())
    }

    @Test
    fun `an install set up before the flag existed has its values confirmed`() = runTest {
        val stores = stores()
        stores.seedFinishedInstall(flag = false)

        stores.migration.run()

        val sync = stores.sync.preferences.first()
        assertTrue(sync.librarySourceConfirmed)
        assertTrue(sync.saveSyncAnswered)
        assertTrue(stores.storage.preferences.first().romStoragePathConfirmed)
    }

    @Test
    fun `an install killed after sign-in and before a rom folder confirms nothing`() = runTest {
        val stores = stores()
        stores.sync.setRomMCredentials("https://romm.example.com/", "token", "player", 7)

        stores.migration.run()

        val sync = stores.sync.preferences.first()
        assertFalse(sync.librarySourceConfirmed)
        assertFalse(sync.saveSyncAnswered)
        assertFalse(stores.storage.preferences.first().romStoragePathConfirmed)
    }

    @Test
    fun `a fresh install migrates once and later setup values are left to setup`() = runTest {
        val stores = stores()
        stores.migration.run()
        stores.seedFinishedInstall(flag = false)

        stores.migration.run()

        val sync = stores.sync.preferences.first()
        assertFalse(sync.librarySourceConfirmed)
        assertFalse(sync.saveSyncAnswered)
        assertFalse(stores.storage.preferences.first().romStoragePathConfirmed)
    }

    @Test
    fun `values missing at migration time are not confirmed`() = runTest {
        val stores = stores()
        stores.dataStore.edit { it[legacyFlag] = true }

        stores.migration.run()

        val sync = stores.sync.preferences.first()
        assertFalse(sync.librarySourceConfirmed)
        assertTrue(sync.saveSyncAnswered)
        assertFalse(stores.storage.preferences.first().romStoragePathConfirmed)
    }

    @Test
    fun `running twice changes nothing after the first run`() = runTest {
        val stores = stores()
        stores.seedFinishedInstall(flag = true)
        stores.migration.run()
        stores.storage.setRomStoragePath("/storage/emulated/0/Other")
        stores.dataStore.edit { it.remove(booleanPreferencesKey("rom_storage_path_confirmed")) }

        stores.migration.run()

        assertFalse(stores.storage.preferences.first().romStoragePathConfirmed)
        assertTrue(stores.sync.preferences.first().librarySourceConfirmed)
    }

    @Test
    fun `answering save sync stores the answer and its marker together`() = runTest {
        val stores = stores()

        stores.sync.answerSaveSync(false)

        val sync = stores.sync.preferences.first()
        assertFalse(sync.saveSyncEnabled)
        assertTrue(sync.saveSyncAnswered)
    }

    @Test
    fun `another screen changing save sync does not count as an answer`() = runTest {
        val stores = stores()

        stores.sync.setSaveSyncEnabled(true)

        assertFalse(stores.sync.preferences.first().saveSyncAnswered)
    }

    @Test
    fun `only a confirmed rom folder carries the marker`() = runTest {
        val stores = stores()

        stores.storage.setRomStoragePath("/storage/emulated/0/ROMs")
        assertFalse(stores.storage.preferences.first().romStoragePathConfirmed)

        stores.storage.confirmRomStoragePath("/storage/emulated/0/ROMs")
        val storage = stores.storage.preferences.first()
        assertTrue(storage.romStoragePathConfirmed)
        assertTrue(storage.romStoragePath == "/storage/emulated/0/ROMs")
    }
}
