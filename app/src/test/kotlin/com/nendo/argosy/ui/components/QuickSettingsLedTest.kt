package com.nendo.argosy.ui.components

import com.nendo.argosy.core.input.ControllerProfile
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.preferences.ActiveControllerProfile
import com.nendo.argosy.data.preferences.ActiveControllerProfiles
import com.nendo.argosy.data.preferences.BuiltinEmulatorSettings
import com.nendo.argosy.data.preferences.MenuWrapMode
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.social.SocialConnectionState
import com.nendo.argosy.data.social.SocialRepository
import com.nendo.argosy.hardware.LEDController
import com.nendo.argosy.ui.components.activity.QuickActivityController
import com.nendo.argosy.ui.components.activity.QuickActivityState
import com.nendo.argosy.ui.components.power.QuickPowerController
import com.nendo.argosy.ui.components.power.QuickPowerState
import com.nendo.argosy.ui.input.InputResult
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuickSettingsLedTest {

    private fun preferences(enabled: Boolean, brightness: Int) = mockk<UserPreferencesRepository>(relaxed = true) {
        every { userPreferences } returns flowOf(
            UserPreferences(ambientLedEnabled = enabled, ambientLedBrightness = brightness)
        )
        every { getBuiltinEmulatorSettings() } returns flowOf(BuiltinEmulatorSettings())
    }

    private fun TestScope.controller(repository: UserPreferencesRepository, ledAvailable: Boolean = true) =
        QuickSettingsController(
            preferencesRepository = repository,
            activeControllerProfiles = mockk<ActiveControllerProfiles>(relaxed = true) {
                every { active } returns flowOf(
                    ActiveControllerProfile(controllerId = null, profile = ControllerProfile(), saved = false)
                )
            },
            brightnessController = mockk(relaxed = true),
            volumeController = mockk(relaxed = true),
            performanceResolver = mockk(relaxed = true),
            performanceStore = mockk(relaxed = true),
            refreshController = mockk(relaxed = true),
            hardwareStatsReader = mockk(relaxed = true),
            ledController = mockk<LEDController>(relaxed = true) { every { isAvailable } returns ledAvailable },
            socialRepository = mockk<SocialRepository>(relaxed = true) {
                every { connectionState } returns MutableStateFlow(SocialConnectionState.Disconnected)
            },
            hapticManager = mockk(relaxed = true),
            soundManager = mockk(relaxed = true),
            quickFriends = mockk(relaxed = true),
            power = mockk<QuickPowerController>(relaxed = true) {
                every { state } returns MutableStateFlow(QuickPowerState())
            },
            activity = mockk<QuickActivityController>(relaxed = true) {
                every { state } returns MutableStateFlow(QuickActivityState())
            },
            wrapMode = { MenuWrapMode.HARD_STOP },
            scope = backgroundScope,
            deviceDispatcher = StandardTestDispatcher(testScheduler)
        )

    private fun TestScope.started(controller: QuickSettingsController): QuickSettingsController {
        backgroundScope.launch { controller.state.collect {} }
        runCurrent()
        return controller
    }

    private fun focusedLed(controller: QuickSettingsController) = controller.run {
        focusItem(QuickSettingsItem.Led)
        createInputHandler(onDismiss = {}, onOpenDeviceAccess = null, onOpenRoute = null)
    }

    @Test
    fun `the LED row shows only on a device with LEDs`() {
        val quick = QuickSettingsPage.QUICK

        assertTrue(QuickSettingsItem.Led in quickSettingsVisibleItems(quick, QuickSettingsState(ledAvailable = true)))
        assertFalse(QuickSettingsItem.Led in quickSettingsVisibleItems(quick, QuickSettingsState(ledAvailable = false)))
    }

    @Test
    fun `the row reads the LED switch and brightness the LED settings store`() = runTest {
        val state = started(controller(preferences(enabled = true, brightness = 40))).state.value

        assertTrue(state.ledAvailable)
        assertTrue(state.ledEnabled)
        assertEquals(0.4f, state.ledBrightness, 0.0001f)
    }

    @Test
    fun `A toggles the LEDs and the d-pad steps brightness along the LED scale`() = runTest {
        val repository = preferences(enabled = true, brightness = 40)
        val input = focusedLed(started(controller(repository)))

        input.onRight()
        input.onConfirm()
        runCurrent()

        coVerify { repository.setAmbientLedBrightness(45) }
        coVerify { repository.setAmbientLedEnabled(false) }
    }

    @Test
    fun `below 10 percent the d-pad moves one percent at a time`() = runTest {
        val repository = preferences(enabled = true, brightness = 5)
        val input = focusedLed(started(controller(repository)))

        input.onLeft()
        runCurrent()

        coVerify { repository.setAmbientLedBrightness(4) }
    }

    @Test
    fun `brightness does not move while the LEDs are off`() = runTest {
        val repository = preferences(enabled = false, brightness = 40)
        val input = focusedLed(started(controller(repository)))

        assertEquals(InputResult.handled(SoundType.BOUNDARY), input.onRight())
        runCurrent()

        coVerify(exactly = 0) { repository.setAmbientLedBrightness(any()) }
    }
}
