package com.nendo.argosy.util

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import com.nendo.argosy.data.preferences.EmulatorDisplayTarget
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MirroredDisplayLaunchTargetTest {

    private val phone = mockk<Display>(relaxed = true) {
        every { displayId } returns Display.DEFAULT_DISPLAY
        every { state } returns Display.STATE_ON
    }
    private val tv = mockk<Display>(relaxed = true) {
        every { displayId } returns TV_DISPLAY
        every { state } returns Display.STATE_ON
    }
    private val displayManager = mockk<DisplayManager>(relaxed = true) {
        every { displays } returns arrayOf(phone, tv)
    }
    private val context = mockk<Context>(relaxed = true) {
        every { getSystemService(Context.DISPLAY_SERVICE) } returns displayManager
    }
    private val screenCatalog = mockk<ScreenCatalog>(relaxed = true) {
        every { attachedScreens() } returns listOf(
            AttachedScreen("phone", Display.DEFAULT_DISPLAY, 1, 1080, 2340, true),
            AttachedScreen(TV_KEY, TV_DISPLAY, 2, 1920, 1080, false)
        )
    }
    private val helper = DisplayAffinityHelper(context, screenCatalog).apply {
        appTargetDisplayId = TV_DISPLAY
        roleDisplayIds = TV_DISPLAY to Display.DEFAULT_DISPLAY
    }

    @Before
    fun setUp() {
        mockkObject(ScreenCatalog.Companion)
        every { ScreenCatalog.isScreen(any()) } returns true
        mockkObject(DisplayAffinityHelper.Companion)
        every { DisplayAffinityHelper.dockedExternalDisplayId(context) } returns null
    }

    @After
    fun tearDown() {
        unmockkObject(ScreenCatalog.Companion)
        unmockkObject(DisplayAffinityHelper.Companion)
    }

    @Test
    fun `mirrored HDMI ignores saved display targets while dual screen mode is off`() {
        assertTrue(helper.hasPhysicalSecondaryDisplay)
        assertFalse(helper.hasSecondaryDisplay)
        assertNull(helper.dockedDisplayId)
        assertNull(helper.getRoleDisplayIds(rolesSwapped = false))
        assertNull(helper.appScreenDisplayId(rolesSwapped = false))
        assertNull(helper.getAppScreenLaunchOptions(rolesSwapped = false))
        assertNull(helper.appLaunchDisplayId(
            preferredScreenKey = TV_KEY,
            defaultTarget = EmulatorDisplayTarget.PRIMARY
        ))
        assertNull(helper.getAppLaunchOptions(
            preferredScreenKey = TV_KEY,
            defaultTarget = EmulatorDisplayTarget.PRIMARY
        ))
        EmulatorDisplayTarget.entries.forEach { target ->
            assertNull(target.name, helper.getDisplayTargetId(target, rolesSwapped = false))
        }
        assertEquals(Display.DEFAULT_DISPLAY, helper.getEmulatorDisplayId(rolesSwapped = false))
        assertNull(helper.getActivityOptions(forEmulator = true, overrideDisplayId = TV_DISPLAY))
        assertNull(helper.getActivityOptions(forEmulator = false, overrideDisplayId = TV_DISPLAY))
    }

    @Test
    fun `saved display targets work when dual screen mode is enabled`() {
        helper.dualScreenEnabled = true

        assertTrue(helper.hasSecondaryDisplay)
        assertEquals(TV_DISPLAY to Display.DEFAULT_DISPLAY, helper.getRoleDisplayIds(false))
        assertEquals(TV_DISPLAY, helper.appLaunchDisplayId(preferredScreenKey = TV_KEY))
        assertEquals(
            TV_DISPLAY,
            helper.getDisplayTargetId(EmulatorDisplayTarget.PRIMARY, rolesSwapped = false)
        )
        assertEquals(
            Display.DEFAULT_DISPLAY,
            helper.getDisplayTargetId(EmulatorDisplayTarget.PRESENTATION, rolesSwapped = false)
        )
    }

    private companion object {
        const val TV_DISPLAY = 2
        const val TV_KEY = "tv"
    }
}
