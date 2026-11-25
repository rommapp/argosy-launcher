package com.nendo.argosy.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DisplayPreferencesLedBrightnessTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private suspend fun TestScope.store(): DataStore<Preferences> {
        val file = tempFolder.newFile("settings.preferences_pb")
        return PreferenceDataStoreFactory.create(scope = CoroutineScope(StandardTestDispatcher(testScheduler))) { file }
            .apply { edit { it[booleanPreferencesKey("dual_screen_enabled")] = false } }
    }

    @Test
    fun `LED brightness defaults to full`() = runTest {
        assertEquals(100, DisplayPreferencesRepository(store()).preferences.first().ambientLedBrightness)
    }

    @Test
    fun `a one-percent brightness persists and stays within 0 to 100`() = runTest {
        val backing = store()
        DisplayPreferencesRepository(backing).setAmbientLedBrightness(1)
        assertEquals(1, DisplayPreferencesRepository(backing).preferences.first().ambientLedBrightness)

        DisplayPreferencesRepository(backing).setAmbientLedBrightness(140)
        assertEquals(100, DisplayPreferencesRepository(backing).preferences.first().ambientLedBrightness)
    }
}
