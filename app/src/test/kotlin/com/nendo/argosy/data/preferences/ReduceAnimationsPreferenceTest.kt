package com.nendo.argosy.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.nendo.argosy.ui.theme.toThemeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReduceAnimationsPreferenceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private suspend fun TestScope.repository(): DisplayPreferencesRepository {
        val file = tempFolder.newFile("settings.preferences_pb")
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val store = PreferenceDataStoreFactory.create(scope = scope) { file }.apply {
            edit { it[booleanPreferencesKey("dual_screen_enabled")] = false }
        }
        return DisplayPreferencesRepository(store)
    }

    @Test
    fun `an empty store reads reduce animations as off`() = runTest {
        assertFalse(repository().preferences.first().reduceAnimations)
    }

    @Test
    fun `the setter round-trips through the reduce_animations key`() = runTest {
        val repo = repository()

        repo.setReduceAnimations(true)
        assertTrue(repo.preferences.first().reduceAnimations)

        repo.setReduceAnimations(false)
        assertFalse(repo.preferences.first().reduceAnimations)
    }

    @Test
    fun `the aggregate default matches the display default`() {
        assertEquals(DisplayPreferences().reduceAnimations, UserPreferences().reduceAnimations)
    }

    @Test
    fun `toThemeState carries the toggle in both directions`() {
        assertTrue(UserPreferences(reduceAnimations = true).toThemeState().reduceAnimations)
        assertFalse(UserPreferences(reduceAnimations = false).toThemeState().reduceAnimations)
    }
}
