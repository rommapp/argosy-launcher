package com.nendo.argosy.data.preferences

import androidx.datastore.core.createSingleProcessCoordinator
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * A truncated preferences file must not be fatal.
 *
 * Cutting power mid-write leaves the file unparseable, and DataStore's default is to rethrow on
 * every later read. Preferences are read during startup, so that reads as an app that will not
 * open until its data is wiped. These pin that the store Argosy builds recovers instead.
 */
class PreferencesCorruptionRecoveryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val key = booleanPreferencesKey("a_setting")

    private fun TestScope.storeOver(file: File) = multiProcessPreferencesStore(
        file = file,
        scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
        coordinator = { createSingleProcessCoordinator(it.absolutePath) }
    )

    @Test
    fun `a truncated file reads as empty instead of throwing`() = runTest {
        val file = tempFolder.newFile("settings.preferences_pb")
        file.writeBytes(byteArrayOf(0x08, 0x7F, 0x00, 0x11, 0x22))

        assertNull(storeOver(file).data.first()[key])
    }

    @Test
    fun `the store still works after recovering`() = runTest {
        val file = tempFolder.newFile("settings.preferences_pb")
        file.writeBytes(ByteArray(64) { 0xFF.toByte() })

        val store = storeOver(file)
        store.edit { it[key] = true }

        assertEquals(true, store.data.first()[key])
    }

    @Test
    fun `an empty file is recovered too`() = runTest {
        val file = tempFolder.newFile("settings.preferences_pb")
        assertTrue(file.length() == 0L)

        assertNull(storeOver(file).data.first()[key])
    }
}
