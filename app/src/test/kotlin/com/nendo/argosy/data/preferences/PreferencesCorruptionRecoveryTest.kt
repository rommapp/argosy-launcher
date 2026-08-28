package com.nendo.argosy.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A truncated preferences file must not be fatal.
 *
 * Cutting power mid-write leaves the file unparseable, and DataStore's default is to rethrow on
 * every later read. Preferences are read during startup, so that reads as an app that will not
 * open until its data is wiped. These pin that the handler recovers instead.
 */
class PreferencesCorruptionRecoveryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val key = booleanPreferencesKey("a_setting")

    @Test
    fun `a truncated file reads as empty instead of throwing`() = runTest {
        val file = tempFolder.newFile("settings.preferences_pb")
        file.writeBytes(byteArrayOf(0x08, 0x7F, 0x00, 0x11, 0x22))

        val store = PreferenceDataStoreFactory.create(
            corruptionHandler = preferencesCorruptionHandler(),
            scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        ) { file }

        assertNull(store.data.first()[key])
    }

    @Test
    fun `the store still works after recovering`() = runTest {
        val file = tempFolder.newFile("settings.preferences_pb")
        file.writeBytes(ByteArray(64) { 0xFF.toByte() })

        val store = PreferenceDataStoreFactory.create(
            corruptionHandler = preferencesCorruptionHandler(),
            scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        ) { file }

        store.edit { it[key] = true }

        assertEquals(true, store.data.first()[key])
    }

    @Test
    fun `an empty file is recovered too`() = runTest {
        val file = tempFolder.newFile("settings.preferences_pb")
        assertTrue(file.length() == 0L)

        val store = PreferenceDataStoreFactory.create(
            corruptionHandler = preferencesCorruptionHandler(),
            scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        ) { file }

        assertNull(store.data.first()[key])
    }
}
