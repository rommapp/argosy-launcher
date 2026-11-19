package com.nendo.argosy.data.setup

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.nendo.argosy.data.preferences.AppPreferencesRepository
import com.nendo.argosy.data.preferences.DisplayPreferencesRepository
import com.nendo.argosy.domain.model.HomeLayoutKind
import com.nendo.argosy.domain.model.HomeLayoutSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NewInstallHomeLayoutSeedTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class Stores(val app: AppPreferencesRepository, val display: DisplayPreferencesRepository) {
        val seed = NewInstallHomeLayoutSeed(app, display)
    }

    private suspend fun TestScope.stores(): Stores {
        val file = tempFolder.newFile("settings.preferences_pb")
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }.apply {
            edit { it[booleanPreferencesKey("dual_screen_enabled")] = false }
        }
        return Stores(AppPreferencesRepository(dataStore), DisplayPreferencesRepository(dataStore))
    }

    private suspend fun Stores.selectedLayout(): HomeLayoutKind =
        display.preferences.first().homeLayout.selected

    @Test
    fun `a fresh install starts on columns`() = runTest {
        val stores = stores()

        stores.seed.run()

        assertEquals(HomeLayoutKind.COLUMNS, stores.selectedLayout())
    }

    @Test
    fun `an install that finished setup keeps its absent layout`() = runTest {
        val stores = stores()
        stores.app.finishSetupFlow()

        stores.seed.run()

        assertEquals(HomeLayoutKind.CAROUSEL, stores.selectedLayout())
    }

    @Test
    fun `a stored layout is not overwritten`() = runTest {
        val stores = stores()
        stores.display.setHomeLayout(HomeLayoutSettings(selected = HomeLayoutKind.AUTO_GRID))

        stores.seed.run()

        assertEquals(HomeLayoutKind.AUTO_GRID, stores.selectedLayout())
    }

    @Test
    fun `a stored carousel is not overwritten`() = runTest {
        val stores = stores()
        stores.display.setHomeLayout(HomeLayoutSettings())

        stores.seed.run()

        assertEquals(HomeLayoutKind.CAROUSEL, stores.selectedLayout())
    }
}
