package com.nendo.argosy.hardware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundAppTrackerTest {

    private val tracker = ForegroundAppTracker(
        ownPackage = ARGOSY,
        inputSurfaces = setOf(ARGOSY_ACTIVITY),
        imePackage = { IME }
    )

    @Test
    fun `a window from another app marks it as in front`() {
        tracker.onWindowStateChanged(EMULATOR, "org.emu.MainActivity")

        assertTrue(tracker.otherAppInFront)
        assertEquals(EMULATOR, tracker.foregroundPackage)
    }

    @Test
    fun `an Argosy input surface clears it`() {
        tracker.onWindowStateChanged(EMULATOR, "org.emu.MainActivity")
        tracker.onWindowStateChanged(ARGOSY, ARGOSY_ACTIVITY)

        assertFalse(tracker.otherAppInFront)
    }

    @Test
    fun `a System UI window over Argosy changes nothing`() {
        tracker.onWindowStateChanged(ARGOSY, ARGOSY_ACTIVITY)
        tracker.onWindowStateChanged(ForegroundAppTracker.SYSTEM_UI_PACKAGE, "android.widget.FrameLayout")

        assertFalse(tracker.otherAppInFront)
        assertEquals(null, tracker.foregroundPackage)
    }

    @Test
    fun `the keyboard over Argosy changes nothing`() {
        tracker.onWindowStateChanged(ARGOSY, ARGOSY_ACTIVITY)
        tracker.onWindowStateChanged(IME, "android.inputmethodservice.SoftInputWindow")

        assertFalse(tracker.otherAppInFront)
    }

    @Test
    fun `a System UI window over an emulator keeps the emulator in front`() {
        tracker.onWindowStateChanged(EMULATOR, "org.emu.MainActivity")
        tracker.onWindowStateChanged(ForegroundAppTracker.SYSTEM_UI_PACKAGE, "android.widget.FrameLayout")

        assertTrue(tracker.otherAppInFront)
        assertEquals(EMULATOR, tracker.foregroundPackage)
    }

    private companion object {
        const val ARGOSY = "com.nendo.argosy"
        const val ARGOSY_ACTIVITY = "com.nendo.argosy.ArgosyActivity"
        const val EMULATOR = "org.emu"
        const val IME = "com.google.android.inputmethod.latin"
    }
}
