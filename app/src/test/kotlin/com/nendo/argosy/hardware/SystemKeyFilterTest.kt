package com.nendo.argosy.hardware

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemKeyFilterTest {

    private var backFiltering = true
    private var backHoldScheduled: Runnable? = null
    private var backTaps = 0
    private var backHolds = 0
    private val hotkeyHolds = mutableListOf<Runnable>()
    private val performed = mutableListOf<SystemHotkeyAction>()

    private val filter = SystemKeyFilter(
        backGate = BackHoldGate(
            filtering = { backFiltering },
            scheduleHold = { backHoldScheduled = it },
            cancelHold = { if (backHoldScheduled === it) backHoldScheduled = null },
            onTap = { backTaps++ },
            onHold = { backHolds++ }
        ),
        hotkeys = SystemHotkeyDetector(
            config = {
                SystemHotkeyConfig.resolve(
                    enabled = true,
                    customHome = null,
                    customBack = null,
                    swapAB = false,
                    swapStartSelect = false
                )
            },
            foreignAppInFront = { true },
            scheduleHold = { task, _ -> hotkeyHolds += task },
            cancelHold = { task -> hotkeyHolds.remove(task) },
            perform = { performed += it }
        )
    )

    private fun down(keyCode: Int, repeatCount: Int = 0) =
        filter.onKey(keyCode, KeyEvent.ACTION_DOWN, repeatCount)

    private fun up(keyCode: Int) = filter.onKey(keyCode, KeyEvent.ACTION_UP, 0)

    @Test
    fun `a back tap still replays through the back gate`() {
        assertTrue(down(KeyEvent.KEYCODE_BACK))
        assertTrue(up(KeyEvent.KEYCODE_BACK))

        assertEquals(1, backTaps)
        assertEquals(0, backHolds)
        assertTrue(performed.isEmpty())
    }

    @Test
    fun `a held back still opens the panel and sends no tap`() {
        down(KeyEvent.KEYCODE_BACK)
        backHoldScheduled?.run()
        assertTrue(down(KeyEvent.KEYCODE_BACK, repeatCount = 1))
        assertTrue(up(KeyEvent.KEYCODE_BACK))

        assertEquals(1, backHolds)
        assertEquals(0, backTaps)
    }

    @Test
    fun `back outside a filtered session passes even with hotkeys on`() {
        backFiltering = false

        assertFalse(down(KeyEvent.KEYCODE_BACK))
        assertFalse(up(KeyEvent.KEYCODE_BACK))
        assertEquals(0, backTaps + backHolds)
        assertTrue(performed.isEmpty())
    }

    @Test
    fun `controller buttons reach the hotkey detector and leave the back gate alone`() {
        assertFalse(down(KeyEvent.KEYCODE_BUTTON_SELECT))
        assertTrue(down(KeyEvent.KEYCODE_BUTTON_B))

        assertEquals(listOf(SystemHotkeyAction.BACK), performed)
        assertEquals(0, backTaps + backHolds)
        assertEquals(null, backHoldScheduled)
    }

    @Test
    fun `other actions pass untouched`() {
        assertFalse(filter.onKey(KeyEvent.KEYCODE_BUTTON_MODE, KeyEvent.ACTION_MULTIPLE, 0))
        assertTrue(performed.isEmpty())
    }
}
