package com.nendo.argosy.hardware

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemHotkeyDetectorTest {

    private val select = KeyEvent.KEYCODE_BUTTON_SELECT
    private val start = KeyEvent.KEYCODE_BUTTON_START
    private val b = KeyEvent.KEYCODE_BUTTON_B
    private val a = KeyEvent.KEYCODE_BUTTON_A
    private val guide = KeyEvent.KEYCODE_BUTTON_MODE

    private var now = 0L
    private val pending = mutableListOf<Pair<Long, Runnable>>()
    private val performed = mutableListOf<SystemHotkeyAction>()
    private var config = SystemHotkeyConfig.resolve(
        enabled = true,
        customHome = null,
        customBack = null,
        swapAB = false,
        swapStartSelect = false
    )
    private var otherAppInFront = true
    private var quickPanelAvailable = false
    private var quickPanelOpens = 0

    private val detector = SystemHotkeyDetector(
        config = { config },
        foreignAppInFront = { otherAppInFront },
        scheduleHold = { task, delayMs -> pending += (now + delayMs) to task },
        cancelHold = { task -> pending.removeAll { it.second === task } },
        perform = { performed += it },
        quickPanelAvailable = { quickPanelAvailable },
        openQuickPanel = { quickPanelOpens++ }
    )

    private fun advanceTo(time: Long) {
        while (true) {
            val due = pending.filter { it.first <= time }.minByOrNull { it.first } ?: break
            pending.remove(due)
            now = due.first
            due.second.run()
        }
        now = time
    }

    private fun down(keyCode: Int, at: Long): Boolean {
        advanceTo(at)
        return detector.onKeyDown(keyCode, isRepeat = false)
    }

    private fun up(keyCode: Int, at: Long): Boolean {
        advanceTo(at)
        return detector.onKeyUp(keyCode)
    }

    @Test
    fun `select and start held for two seconds go home and swallow both releases`() {
        assertFalse(down(select, at = 0))
        assertFalse(down(start, at = 50))
        advanceTo(2050)

        assertEquals(listOf(SystemHotkeyAction.HOME), performed)
        assertTrue(detector.onKeyDown(select, isRepeat = true))
        assertTrue(up(start, at = 2100))
        assertTrue(up(select, at = 2150))
    }

    @Test
    fun `select and start released at one point nine seconds do nothing and pass through`() {
        assertFalse(down(select, at = 0))
        assertFalse(down(start, at = 0))
        assertFalse(up(start, at = 1900))
        assertFalse(up(select, at = 1950))
        advanceTo(5000)

        assertTrue(performed.isEmpty())
    }

    @Test
    fun `holding select and pressing b goes back and swallows only the b press`() {
        assertFalse(down(select, at = 0))
        assertTrue(down(b, at = 100))
        assertEquals(listOf(SystemHotkeyAction.BACK), performed)

        assertTrue(detector.onKeyDown(b, isRepeat = true))
        assertTrue(up(b, at = 200))
        assertFalse(up(select, at = 300))
    }

    @Test
    fun `with the quick panel available select and b go back on release`() {
        quickPanelAvailable = true
        assertFalse(down(select, at = 0))
        assertTrue(down(b, at = 100))
        assertTrue(performed.isEmpty())

        assertTrue(up(b, at = 900))
        assertEquals(listOf(SystemHotkeyAction.BACK), performed)
        assertFalse(up(select, at = 1000))
        advanceTo(5000)
        assertEquals(0, quickPanelOpens)
    }

    @Test
    fun `with the quick panel available select and b held three seconds open it instead of going back`() {
        quickPanelAvailable = true
        assertFalse(down(select, at = 0))
        assertTrue(down(b, at = 100))
        advanceTo(3100)

        assertEquals(1, quickPanelOpens)
        assertTrue(up(b, at = 3200))
        assertFalse(up(select, at = 3300))
        assertTrue(performed.isEmpty())
    }

    @Test
    fun `releasing select first still goes back once, just short of the hold`() {
        quickPanelAvailable = true
        assertFalse(down(select, at = 0))
        assertTrue(down(b, at = 100))
        assertFalse(up(select, at = 3099))
        assertTrue(up(b, at = 3150))
        advanceTo(6000)

        assertEquals(listOf(SystemHotkeyAction.BACK), performed)
        assertEquals(0, quickPanelOpens)
    }

    @Test
    fun `b pressed before select does not go back`() {
        assertFalse(down(b, at = 0))
        assertFalse(down(select, at = 100))
        assertFalse(up(select, at = 200))
        assertFalse(up(b, at = 300))

        assertTrue(performed.isEmpty())
    }

    @Test
    fun `a lone select reaches the app down and up`() {
        assertFalse(down(select, at = 0))
        assertFalse(detector.onKeyDown(select, isRepeat = true))
        assertFalse(up(select, at = 3000))

        assertTrue(performed.isEmpty())
    }

    @Test
    fun `the back trigger follows the a and b swap`() {
        config = SystemHotkeyConfig.resolve(
            enabled = true,
            customHome = null,
            customBack = null,
            swapAB = true,
            swapStartSelect = false
        )

        assertFalse(down(select, at = 0))
        assertFalse(down(b, at = 50))
        assertTrue(down(a, at = 100))
        assertEquals(listOf(SystemHotkeyAction.BACK), performed)
    }

    @Test
    fun `a guide tap goes home and is consumed`() {
        assertTrue(down(guide, at = 0))
        assertTrue(up(guide, at = 200))
        advanceTo(3000)

        assertEquals(listOf(SystemHotkeyAction.HOME), performed)
    }

    @Test
    fun `a guide held for one second goes back and its release does nothing more`() {
        assertTrue(down(guide, at = 0))
        advanceTo(1000)
        assertTrue(detector.onKeyDown(guide, isRepeat = true))
        assertTrue(up(guide, at = 1500))

        assertEquals(listOf(SystemHotkeyAction.BACK), performed)
    }

    @Test
    fun `with the setting off every event passes and nothing fires`() {
        config = config.copy(enabled = false)

        assertFalse(down(guide, at = 0))
        assertFalse(up(guide, at = 100))
        assertFalse(down(select, at = 200))
        assertFalse(down(b, at = 300))
        assertFalse(down(start, at = 400))
        advanceTo(3000)
        assertFalse(up(b, at = 3000))
        assertFalse(up(start, at = 3000))
        assertFalse(up(select, at = 3000))

        assertTrue(performed.isEmpty())
    }

    @Test
    fun `with argosy in front every event passes and nothing fires`() {
        otherAppInFront = false

        assertFalse(down(guide, at = 0))
        assertFalse(up(guide, at = 100))
        assertFalse(down(select, at = 200))
        assertFalse(down(start, at = 300))
        advanceTo(3000)
        assertFalse(down(b, at = 3000))
        assertFalse(up(b, at = 3100))
        assertFalse(up(start, at = 3100))
        assertFalse(up(select, at = 3100))

        assertTrue(performed.isEmpty())
    }

    @Test
    fun `the front app is read once per chord`() {
        assertFalse(down(select, at = 0))
        otherAppInFront = false
        assertTrue(down(b, at = 100))

        assertEquals(listOf(SystemHotkeyAction.BACK), performed)
    }

    @Test
    fun `a custom home combo replaces select and start`() {
        config = SystemHotkeyConfig.resolve(
            enabled = true,
            customHome = listOf(KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1),
            customBack = null,
            swapAB = false,
            swapStartSelect = false
        )

        assertFalse(down(select, at = 0))
        assertFalse(down(start, at = 0))
        advanceTo(2500)
        assertTrue(performed.isEmpty())
        up(start, at = 2500)
        up(select, at = 2500)

        assertFalse(down(KeyEvent.KEYCODE_BUTTON_L1, at = 3000))
        assertFalse(down(KeyEvent.KEYCODE_BUTTON_R1, at = 3000))
        advanceTo(5000)
        assertEquals(listOf(SystemHotkeyAction.HOME), performed)
    }

    @Test
    fun `an untracked key passes without touching combo state`() {
        assertFalse(down(KeyEvent.KEYCODE_BUTTON_X, at = 0))
        assertFalse(up(KeyEvent.KEYCODE_BUTTON_X, at = 100))

        assertTrue(performed.isEmpty())
    }
}
