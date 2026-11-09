package com.nendo.argosy.ui.components

import com.nendo.argosy.data.preferences.BuiltinEmulatorSettings
import com.nendo.argosy.data.preferences.MenuWrapMode
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.social.SocialConnectionState
import com.nendo.argosy.data.social.SocialRepository
import com.nendo.argosy.hardware.BrightnessController
import com.nendo.argosy.hardware.DevicePerformance
import com.nendo.argosy.hardware.DevicePerformanceResolver
import com.nendo.argosy.hardware.DisplayBrightness
import com.nendo.argosy.hardware.DisplayVolume
import com.nendo.argosy.hardware.PerformanceMode
import com.nendo.argosy.hardware.PerformanceModeStore
import com.nendo.argosy.hardware.VolumeController
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuickSettingsRootAccessTest {

    private val lockedRoot = QuickSettingsState(
        deviceSettingsSupported = true,
        deviceSettingsEnabled = false,
        deviceAccessNeedsRoot = true,
        performanceModes = listOf(PerformanceMode.BALANCED, PerformanceMode.GAMING)
    )

    @Test
    fun `a root-gated device shows the root notice in place of the permission notice`() {
        val items = quickSettingsVisibleItems(QuickSettingsPage.PERFORMANCE, lockedRoot)

        assertTrue(QuickSettingsItem.RootAccess in items)
        assertFalse(QuickSettingsItem.DeviceAccess in items)
        assertEquals(QuickSettingsItem.RootAccess, quickSettingsFocusableItems(QuickSettingsPage.PERFORMANCE, lockedRoot).first())
    }

    @Test
    fun `a permission-gated device keeps the permission notice`() {
        val items = quickSettingsVisibleItems(QuickSettingsPage.PERFORMANCE, lockedRoot.copy(deviceAccessNeedsRoot = false))

        assertTrue(QuickSettingsItem.DeviceAccess in items)
        assertFalse(QuickSettingsItem.RootAccess in items)
    }

    @Test
    fun `once root is granted neither notice shows`() {
        val items = quickSettingsVisibleItems(QuickSettingsPage.PERFORMANCE, lockedRoot.copy(deviceSettingsEnabled = true))

        assertFalse(QuickSettingsItem.RootAccess in items)
        assertFalse(QuickSettingsItem.DeviceAccess in items)
    }

    @Test
    fun `confirm on the root notice asks the store for access`() = runTest {
        val backend = mockk<DevicePerformance>(relaxed = true) {
            every { needsRootGrant } returns true
            every { canWrite } returns false
            every { modes } returns listOf(PerformanceMode.BALANCED, PerformanceMode.GAMING)
        }
        val store = mockk<PerformanceModeStore>(relaxed = true)
        val controller = QuickSettingsController(
            preferencesRepository = mockk<UserPreferencesRepository>(relaxed = true) {
                every { userPreferences } returns flowOf(UserPreferences())
                every { getBuiltinEmulatorSettings() } returns flowOf(BuiltinEmulatorSettings())
            },
            brightnessController = mockk<BrightnessController>(relaxed = true) {
                every { getBrightness() } returns DisplayBrightness(primary = 0.5f, secondary = null)
            },
            volumeController = mockk<VolumeController>(relaxed = true) {
                every { getVolume() } returns DisplayVolume(primary = 0.5f, secondary = null)
            },
            performanceResolver = mockk<DevicePerformanceResolver> { every { resolve() } returns backend },
            performanceStore = store,
            refreshController = mockk(relaxed = true),
            hardwareStatsReader = mockk(relaxed = true),
            socialRepository = mockk<SocialRepository>(relaxed = true) {
                every { connectionState } returns MutableStateFlow(SocialConnectionState.Disconnected)
            },
            hapticManager = mockk(relaxed = true),
            soundManager = mockk(relaxed = true),
            quickFriends = mockk(relaxed = true),
            wrapMode = { MenuWrapMode.HARD_STOP },
            scope = backgroundScope,
            deviceDispatcher = StandardTestDispatcher(testScheduler)
        )
        backgroundScope.launch { controller.state.collect {} }

        controller.setOpen(true)
        runCurrent()
        controller.selectPage(QuickSettingsPage.PERFORMANCE)
        controller.focusItem(QuickSettingsItem.RootAccess)
        controller.createInputHandler(onDismiss = {}, onOpenDeviceAccess = null).onConfirm()
        runCurrent()

        coVerify(exactly = 1) { store.requestAccess(backend) }
    }
}
