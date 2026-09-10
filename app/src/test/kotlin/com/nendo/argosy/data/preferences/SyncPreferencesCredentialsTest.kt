package com.nendo.argosy.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The device id identifies this device to one RomM instance. An instance answers on more than
 * one address, so moving between them must keep the id; only a different user drops it.
 */
class SyncPreferencesCredentialsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun repository(scope: CoroutineScope): SyncPreferencesRepository {
        val file = tempFolder.newFile("settings.preferences_pb")
        return SyncPreferencesRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
    }

    @Test
    fun `changing the address in use keeps the device id`() = runTest {
        val repo = repository(CoroutineScope(StandardTestDispatcher(testScheduler)))
        repo.setRomMCredentials("https://romm.example.com/", "token", "player", 7)
        repo.setRommDeviceId("device-1", "2.15.1")

        repo.setRomMCredentials("http://192.168.1.10:8080/", "token")

        val prefs = repo.preferences.first()
        assertEquals("http://192.168.1.10:8080/", prefs.rommBaseUrl)
        assertEquals("device-1", prefs.rommDeviceId)
        assertEquals("2.15.1", prefs.rommDeviceClientVersion)
        assertEquals(7L, prefs.rommUserId)
    }

    @Test
    fun `a different user drops the device id`() = runTest {
        val repo = repository(CoroutineScope(StandardTestDispatcher(testScheduler)))
        repo.setRomMCredentials("https://romm.example.com/", "token", "player", 7)
        repo.setRommDeviceId("device-1", "2.15.1")

        repo.setRomMCredentials("https://romm.example.com/", "other-token", "other", 8)

        val prefs = repo.preferences.first()
        assertNull(prefs.rommDeviceId)
        assertNull(prefs.rommDeviceClientVersion)
        assertEquals(8L, prefs.rommUserId)
    }

    @Test
    fun `the same user on a new address keeps the device id`() = runTest {
        val repo = repository(CoroutineScope(StandardTestDispatcher(testScheduler)))
        repo.setRomMCredentials("https://romm.example.com/", "token", "player", 7)
        repo.setRommDeviceId("device-1", "2.15.1")

        repo.setRomMCredentials("http://192.168.1.10:8080/", "token", "player", 7)

        assertEquals("device-1", repo.preferences.first().rommDeviceId)
    }
}
