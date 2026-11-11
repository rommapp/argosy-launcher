package com.nendo.argosy.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryThresholdsTest {

    private fun discharging(level: Int) = BatterySample(level, charging = false)

    private fun charging(level: Int) = BatterySample(level, charging = true)

    private fun eventsPerReading(vararg samples: BatterySample): List<List<BatteryEvent>> {
        var step: BatteryStep? = null
        return samples.map { sample ->
            advanceBattery(step, sample).also { step = it }.events
        }
    }

    private fun fired(vararg samples: BatterySample): List<BatteryEvent> = eventsPerReading(*samples).flatten()

    @Test
    fun `discharging one percent at a time fires each threshold once at its level`() {
        val levels = (20 downTo 0).toList()
        val events = eventsPerReading(*levels.map { discharging(it) }.toTypedArray())
        val byLevel = levels.zip(events).filter { it.second.isNotEmpty() }.toMap()
        assertEquals(
            mapOf(
                15 to listOf(BatteryEvent.LOW_15),
                5 to listOf(BatteryEvent.CRITICAL_5),
                3 to listOf(BatteryEvent.CRITICAL_3),
                1 to listOf(BatteryEvent.CRITICAL_1)
            ),
            byLevel
        )
    }

    @Test
    fun `jitter around the low threshold fires it once`() {
        assertEquals(
            listOf(BatteryEvent.LOW_15),
            fired(discharging(16), discharging(15), discharging(16), discharging(15), discharging(14), discharging(15))
        )
    }

    @Test
    fun `jitter around the critical threshold fires it once`() {
        assertEquals(
            listOf(BatteryEvent.CRITICAL_5),
            fired(discharging(6), discharging(5), discharging(6), discharging(5), discharging(4), discharging(5))
        )
    }

    @Test
    fun `a level that climbs back above a passed threshold without charging does not rearm it`() {
        assertEquals(
            listOf(BatteryEvent.LOW_15),
            fired(discharging(16), discharging(15), discharging(20), discharging(16), discharging(15))
        )
    }

    @Test
    fun `plugging in mid-sequence cancels the rest until the next crossing`() {
        val events = eventsPerReading(
            discharging(6),
            discharging(5),
            charging(5),
            discharging(5),
            discharging(4),
            discharging(3)
        )
        assertEquals(
            listOf(
                emptyList<BatteryEvent>(),
                listOf(BatteryEvent.CRITICAL_5),
                listOf(BatteryEvent.PLUGGED),
                listOf(BatteryEvent.UNPLUGGED),
                emptyList<BatteryEvent>(),
                listOf(BatteryEvent.CRITICAL_3)
            ),
            events
        )
    }

    @Test
    fun `a new discharge above a threshold fires it again`() {
        assertEquals(
            listOf(
                BatteryEvent.LOW_15,
                BatteryEvent.PLUGGED,
                BatteryEvent.UNPLUGGED,
                BatteryEvent.LOW_15
            ),
            fired(discharging(16), discharging(15), charging(15), charging(20), discharging(20), discharging(15))
        )
    }

    @Test
    fun `starting below a threshold counts it as passed and fires the next one down`() {
        assertEquals(
            listOf(BatteryEvent.CRITICAL_5),
            fired(discharging(10), discharging(9), discharging(5), discharging(10), discharging(5))
        )
        assertEquals(listOf(BatteryEvent.CRITICAL_3), fired(discharging(4), discharging(3)))
    }

    @Test
    fun `starting exactly on a threshold does not fire it`() {
        assertEquals(emptyList<BatteryEvent>(), fired(discharging(15), discharging(14)))
    }

    @Test
    fun `charging while low never fires a threshold even as the level drops`() {
        assertEquals(
            emptyList<BatteryEvent>(),
            fired(charging(16), charging(15), charging(5), charging(3), charging(1), charging(0))
        )
    }

    @Test
    fun `unplugging below a threshold does not fire it`() {
        assertEquals(
            listOf(BatteryEvent.UNPLUGGED),
            fired(charging(10), discharging(10), discharging(9))
        )
    }

    @Test
    fun `the first reading never reports a plug change`() {
        assertEquals(emptyList<BatteryEvent>(), fired(charging(50)))
        assertEquals(emptyList<BatteryEvent>(), fired(discharging(50)))
    }

    @Test
    fun `plug and unplug fire only on a change`() {
        assertEquals(
            listOf(BatteryEvent.PLUGGED, BatteryEvent.UNPLUGGED),
            fired(discharging(50), charging(50), charging(51), discharging(51), discharging(50))
        )
    }

    @Test
    fun `a jump past several thresholds fires each one in order`() {
        assertEquals(
            listOf(
                emptyList<BatteryEvent>(),
                listOf(BatteryEvent.LOW_15, BatteryEvent.CRITICAL_5, BatteryEvent.CRITICAL_3)
            ),
            eventsPerReading(discharging(16), discharging(2))
        )
    }

    @Test
    fun `only the five three and one percent events are critical`() {
        assertEquals(
            setOf(BatteryEvent.CRITICAL_5, BatteryEvent.CRITICAL_3, BatteryEvent.CRITICAL_1),
            BatteryEvent.entries.filter { it.isCritical }.toSet()
        )
    }

    @Test
    fun `the low threshold is fifteen percent and critical is five`() {
        assertEquals(15, BatteryThresholds.LOW_PERCENT)
        assertEquals(5, BatteryThresholds.CRITICAL_PERCENT)
    }
}
