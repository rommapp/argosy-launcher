package com.nendo.argosy.ui.screens.common

import com.nendo.argosy.data.emulator.PlaySessionTracker
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionEndCoordinatorTest {
    private val playSessionTracker: PlaySessionTracker = mockk(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun coordinator() = SessionEndCoordinator(
        application = mockk(relaxed = true),
        playSessionTracker = playSessionTracker,
        emulatorResolver = mockk(relaxed = true),
        preferencesRepository = mockk(relaxed = true),
        gameRepository = mockk(relaxed = true),
        savePathValidator = mockk(relaxed = true),
        gameLauncher = mockk(relaxed = true)
    )

    @Test
    fun `a coordinator with no session end reports none running and no overlay`() {
        val coordinator = coordinator()

        assertFalse(coordinator.sessionEndRunning.value)
        assertNull(coordinator.syncOverlayState.value)
    }
}
