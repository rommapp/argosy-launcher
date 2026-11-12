package com.nendo.argosy.ui.screens.settings.delegates

import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.ui.screens.settings.SyncSettingsState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncSettingsDelegateSaveSyncTest {

    private val stored = MutableStateFlow(UserPreferences(saveSyncEnabled = true))
    private val repository: UserPreferencesRepository = mockk(relaxed = true)

    init {
        every { repository.preferences } returns stored
    }

    private fun delegate(): SyncSettingsDelegate = SyncSettingsDelegate(
        application = mockk(relaxed = true),
        preferencesRepository = repository,
        saveSyncRepository = mockk(relaxed = true),
        saveCacheRepository = mockk(relaxed = true),
        stateCacheManager = mockk(relaxed = true),
        syncCoordinator = mockk(relaxed = true),
        platformRepository = mockk(relaxed = true),
        rommRepository = mockk(relaxed = true),
        imageCacheManager = mockk(relaxed = true),
        notificationManager = mockk(relaxed = true),
        attributionRepository = mockk(relaxed = true),
        retroAchievementsRepository = mockk(relaxed = true),
        saveSyncGate = mockk(relaxed = true)
    )

    @Test
    fun `the toggle follows a stored change made outside settings`() = runTest {
        val delegate = delegate()
        delegate.observeSaveSync(this)
        runCurrent()
        assertTrue(delegate.state.value.saveSyncEnabled)

        stored.update { it.copy(saveSyncEnabled = false) }
        runCurrent()

        assertFalse(delegate.state.value.saveSyncEnabled)
        coroutineContext.cancelChildren()
    }

    @Test
    fun `a section reload keeps the stored value`() = runTest {
        val delegate = delegate()
        delegate.observeSaveSync(this)
        runCurrent()

        delegate.updateState(SyncSettingsState())

        assertTrue(delegate.state.value.saveSyncEnabled)
        coroutineContext.cancelChildren()
    }
}
