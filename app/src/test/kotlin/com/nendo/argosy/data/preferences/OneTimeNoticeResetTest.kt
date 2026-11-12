package com.nendo.argosy.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.nendo.argosy.data.model.AppUpdateRelease
import com.nendo.argosy.data.update.UpdateStateStore
import com.nendo.argosy.domain.model.ArgosyPermission
import com.nendo.argosy.domain.permissions.PermissionRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.YearMonth

class OneTimeNoticeResetTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class Stores(dataStore: DataStore<Preferences>) {
        val app = AppPreferencesRepository(dataStore)
        val sync = SyncPreferencesRepository(dataStore)
    }

    private fun TestScope.stores(): Stores {
        val file = tempFolder.newFile("settings.preferences_pb")
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        return Stores(PreferenceDataStoreFactory.create(scope = scope) { file })
    }

    private suspend fun Stores.seedShownNotices() {
        sync.setRomMCredentials("https://romm.example.com/", "token", "player", 7)
        assertTrue(sync.claimRaSignInNotice())
        sync.markSaveMigrationNoticeShown(41L)
        app.setPermissionRecord(
            PermissionRecord(
                acknowledged = setOf(ArgosyPermission.ALL_FILES_ACCESS, ArgosyPermission.NOTIFICATIONS),
                noticed = setOf(ArgosyPermission.NOTIFICATIONS)
            )
        )
        app.setLastMonthlyRecapShown(YearMonth.of(2026, 10))
        app.finishSetupFlow()
    }

    @Test
    fun `the ra sign-in notice can be claimed again after the reset`() = runTest {
        val stores = stores()
        stores.seedShownNotices()
        assertFalse(stores.sync.claimRaSignInNotice())

        stores.sync.resetOneTimeNotices()

        assertTrue(stores.sync.claimRaSignInNotice())
    }

    @Test
    fun `save migration notices show again after the reset`() = runTest {
        val stores = stores()
        stores.seedShownNotices()

        stores.sync.resetOneTimeNotices()

        assertFalse(stores.sync.isSaveMigrationNoticeShown(41L))
    }

    @Test
    fun `permission revocations and the monthly recap marker are forgotten`() = runTest {
        val stores = stores()
        stores.seedShownNotices()

        stores.app.resetOneTimeNotices()

        assertTrue(stores.app.permissionRecord().noticed.isEmpty())
        assertNull(stores.app.preferences.first().lastMonthlyRecapShown)
    }

    @Test
    fun `the reset keeps accounts, acknowledged permissions and setup progress`() = runTest {
        val stores = stores()
        stores.seedShownNotices()

        stores.sync.resetOneTimeNotices()
        stores.app.resetOneTimeNotices()

        val sync = stores.sync.preferences.first()
        assertFalse(sync.rommBaseUrl.isNullOrBlank())
        assertEquals("token", sync.rommToken)
        assertEquals(
            setOf(ArgosyPermission.ALL_FILES_ACCESS, ArgosyPermission.NOTIFICATIONS),
            stores.app.permissionRecord().acknowledged
        )
        assertTrue(stores.app.preferences.first().setupFlowFinished)
    }

    @Test
    fun `clearing the skipped update tag keeps the stored check result`() {
        val release = AppUpdateRelease(
            tag = "v3.1.0",
            name = "Argosy 3.1.0",
            notes = null,
            assetName = "argosy-v3.1.0-arm64.apk",
            assetUrl = "https://example/argosy-v3.1.0-arm64.apk",
            assetSize = 42L,
            assetDigest = "sha256:00",
            abi = "arm64"
        )
        val file = File(tempFolder.root, "update_state.json")
        UpdateStateStore(file).update { it.copy(latest = release, skippedTag = "v3.1.0") }

        UpdateStateStore(file).update { it.withoutSkippedTag() }

        val reloaded = UpdateStateStore(file).load()
        assertNull(reloaded.skippedTag)
        assertEquals(release, reloaded.latest)
    }
}
