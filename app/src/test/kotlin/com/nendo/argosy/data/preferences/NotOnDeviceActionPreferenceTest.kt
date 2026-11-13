package com.nendo.argosy.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NotOnDeviceActionPreferenceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val key = stringPreferencesKey("romm_not_on_device_action")

    @Test
    fun `an unset choice reads as download`() = runTest {
        val file = tempFolder.newFile("settings.preferences_pb")
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(StandardTestDispatcher(testScheduler))) { file }
        val repo = SyncPreferencesRepository(store)

        assertEquals(NotOnDeviceAction.DOWNLOAD, repo.preferences.first().notOnDeviceAction)
    }

    @Test
    fun `each choice round-trips through its stored token`() = runTest {
        val file = tempFolder.newFile("settings.preferences_pb")
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(StandardTestDispatcher(testScheduler))) { file }
        val repo = SyncPreferencesRepository(store)

        NotOnDeviceAction.entries.forEach { action ->
            repo.setNotOnDeviceAction(action)

            assertEquals(action, repo.preferences.first().notOnDeviceAction)
            assertEquals(action.token, store.data.first()[key])
        }
    }

    @Test
    fun `stored tokens stay literal and unknown tokens fall back to download`() {
        assertEquals("download", NotOnDeviceAction.DOWNLOAD.token)
        assertEquals("stream", NotOnDeviceAction.STREAM.token)
        assertEquals(NotOnDeviceAction.DOWNLOAD, NotOnDeviceAction.fromToken("STREAM"))
        assertEquals(NotOnDeviceAction.DOWNLOAD, NotOnDeviceAction.fromToken(null))
    }

    @Test
    fun `the choice is kept per account`() {
        assertTrue(AccountScopedPreferenceKeys.isPerAccount(key))
    }
}
