package com.nendo.argosy.core.notification

import com.nendo.argosy.core.input.ConnectedPad
import org.junit.Assert.assertEquals
import org.junit.Test

class ControllerNoticesTest {

    private val medium = NotificationDuration.MEDIUM.ms

    private fun joined(id: String, seat: Int) = PadNotice(id, "Pad $id", PadNoticeKind.JOINED, seat)
    private fun left(id: String) = PadNotice(id, "Pad $id", PadNoticeKind.DISCONNECTED)

    @Test
    fun `a notice within the window joins the open card`() {
        val burst = ControllerBurst()
        burst.add(joined("a", 1), medium, now = 0)

        val card = burst.add(joined("b", 2), medium, now = 1_999)

        assertEquals(listOf("a", "b"), card.pads.map { it.controllerId })
    }

    @Test
    fun `a notice exactly at the window edge still joins`() {
        val burst = ControllerBurst()
        burst.add(joined("a", 1), medium, now = 0)

        assertEquals(2, burst.add(joined("b", 2), medium, now = 2_000).pads.size)
    }

    @Test
    fun `a notice past the window opens a new card`() {
        val burst = ControllerBurst()
        burst.add(joined("a", 1), medium, now = 0)

        val card = burst.add(joined("b", 2), medium, now = 2_001)

        assertEquals(listOf("b"), card.pads.map { it.controllerId })
        assertEquals(medium, card.durationMs)
    }

    @Test
    fun `each notice restarts the time but never past the cap`() {
        val burst = ControllerBurst()
        assertEquals(medium, burst.add(joined("a", 1), medium, now = 0).durationMs)
        assertEquals(medium, burst.add(joined("b", 2), medium, now = 1_500).durationMs)
        assertEquals(NotificationDuration.LONG.ms - 3_000, burst.add(joined("c", 3), medium, now = 3_000).durationMs)
        assertEquals(NotificationDuration.LONG.ms - 4_500, burst.add(joined("d", 4), medium, now = 4_500).durationMs)
    }

    @Test
    fun `a notice once the cap has run out opens a new card`() {
        val burst = ControllerBurst()
        burst.add(joined("a", 1), medium, now = 0)
        burst.add(joined("b", 2), medium, now = 2_000)
        burst.add(joined("c", 3), medium, now = 4_000)

        val card = burst.add(joined("d", 4), medium, now = 6_000)

        assertEquals(listOf("d"), card.pads.map { it.controllerId })
        assertEquals(medium, card.durationMs)
    }

    @Test
    fun `a disconnect never folds into a connect card`() {
        val burst = ControllerBurst()
        burst.add(joined("a", 1), medium, now = 0)

        val card = burst.add(left("b"), medium, now = 500)

        assertEquals(listOf("b"), card.pads.map { it.controllerId })
    }

    @Test
    fun `a pad's later notice replaces its earlier one in the card`() {
        val burst = ControllerBurst()
        burst.add(PadNotice("a", "Pad a", PadNoticeKind.WAITING), medium, now = 0)
        burst.add(joined("b", 2), medium, now = 100)

        val card = burst.add(joined("a", 1), medium, now = 200)

        assertEquals(listOf(PadNoticeKind.JOINED, PadNoticeKind.JOINED), card.pads.map { it.kind })
        assertEquals(listOf("a", "b"), card.pads.map { it.controllerId })
    }

    @Test
    fun `the tier is the longest duration that fits the time left`() {
        assertEquals(NotificationDuration.MEDIUM, ControllerBurstCard(emptyList(), 4_500).tier)
        assertEquals(NotificationDuration.SHORT, ControllerBurstCard(emptyList(), 3_000).tier)
        assertEquals(NotificationDuration.SHORT, ControllerBurstCard(emptyList(), 500).tier)
    }

    @Test
    fun `a low pad is picked once and a recovered then low pad is not picked again`() {
        val watch = PadBatteryWatch()
        val low = ConnectedPad("a", "Pad a", builtIn = false, batteryPercent = 15)

        assertEquals(listOf(low), watch.newlyLow(listOf(low)))
        assertEquals(emptyList<ConnectedPad>(), watch.newlyLow(listOf(low.copy(batteryPercent = 10))))
        assertEquals(emptyList<ConnectedPad>(), watch.newlyLow(listOf(low.copy(batteryPercent = 80))))
        assertEquals(emptyList<ConnectedPad>(), watch.newlyLow(listOf(low.copy(batteryPercent = 5))))
    }

    @Test
    fun `pads above the threshold, with no battery, or built in are never picked`() {
        val watch = PadBatteryWatch()
        val pads = listOf(
            ConnectedPad("high", "High", builtIn = false, batteryPercent = 16),
            ConnectedPad("none", "None", builtIn = false, batteryPercent = null),
            ConnectedPad("grip", "Grip", builtIn = true, batteryPercent = 3)
        )

        assertEquals(emptyList<ConnectedPad>(), watch.newlyLow(pads))
    }

    @Test
    fun `each low pad is picked on its own`() {
        val watch = PadBatteryWatch()
        val first = ConnectedPad("a", "Pad a", builtIn = false, batteryPercent = 12)
        val second = ConnectedPad("b", "Pad b", builtIn = false, batteryPercent = 40)
        watch.newlyLow(listOf(first, second))

        val later = watch.newlyLow(listOf(first, second.copy(batteryPercent = 14)))

        assertEquals(listOf("b"), later.map { it.controllerId })
    }
}
