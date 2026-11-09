package com.nendo.argosy.ui.components

import com.nendo.argosy.data.preferences.BuiltinEmulatorSettings
import com.nendo.argosy.data.preferences.MenuWrapMode
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.social.SocialConnectionState
import com.nendo.argosy.data.social.SocialRepository
import com.nendo.argosy.hardware.BrightnessController
import com.nendo.argosy.hardware.DisplayBrightness
import com.nendo.argosy.hardware.DisplayVolume
import com.nendo.argosy.hardware.VolumeController
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuickSettingsDeviceDispatcherTest {

    private val brightnessController = mockk<BrightnessController>(relaxed = true) {
        every { getBrightness() } returns DisplayBrightness(primary = DEVICE_BRIGHTNESS, secondary = null)
    }
    private val volumeController = mockk<VolumeController>(relaxed = true) {
        every { getVolume() } returns DisplayVolume(primary = DEVICE_VOLUME, secondary = null)
    }
    private val preferencesRepository = mockk<UserPreferencesRepository>(relaxed = true) {
        every { userPreferences } returns flowOf(UserPreferences())
        every { getBuiltinEmulatorSettings() } returns flowOf(BuiltinEmulatorSettings())
    }
    private val socialRepository = mockk<SocialRepository>(relaxed = true) {
        every { connectionState } returns MutableStateFlow(SocialConnectionState.Disconnected)
    }

    private fun TestScope.controller(deviceDispatcher: TestDispatcher) = QuickSettingsController(
        preferencesRepository = preferencesRepository,
        brightnessController = brightnessController,
        volumeController = volumeController,
        performanceResolver = mockk(relaxed = true),
        performanceStore = mockk(relaxed = true),
        refreshController = mockk(relaxed = true),
        hardwareStatsReader = mockk(relaxed = true),
        socialRepository = socialRepository,
        hapticManager = mockk(relaxed = true),
        soundManager = mockk(relaxed = true),
        quickFriends = mockk(relaxed = true),
        wrapMode = { MenuWrapMode.HARD_STOP },
        scope = backgroundScope,
        deviceDispatcher = deviceDispatcher
    )

    @Test
    fun `construction leaves the display levels unread until the device dispatcher runs`() = runTest {
        val deviceDispatcher = StandardTestDispatcher(testScheduler)
        val controller = controller(deviceDispatcher)
        backgroundScope.launch { controller.state.collect {} }

        verify(exactly = 0) { brightnessController.getBrightness() }
        verify(exactly = 0) { volumeController.getVolume() }

        runCurrent()

        val state = controller.state.value
        assertEquals(DEVICE_BRIGHTNESS, state.screenBrightness, 0f)
        assertEquals(DEVICE_VOLUME, state.systemVolume, 0f)
    }

    private companion object {
        const val DEVICE_BRIGHTNESS = 0.8f
        const val DEVICE_VOLUME = 0.3f
    }
}
