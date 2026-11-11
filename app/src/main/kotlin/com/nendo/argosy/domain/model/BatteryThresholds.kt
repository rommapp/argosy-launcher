package com.nendo.argosy.domain.model

object BatteryThresholds {
    const val LOW_PERCENT = 15
    const val CRITICAL_PERCENT = 5
    const val CRITICAL_RESAVE_PERCENT = 3
    const val CRITICAL_LAST_RESAVE_PERCENT = 1
}

data class BatterySample(
    val level: Int,
    val charging: Boolean
)

/**
 * A battery change worth acting on. Threshold events carry the level they fire at; [PLUGGED] and
 * [UNPLUGGED] carry none.
 */
enum class BatteryEvent(val thresholdPercent: Int?) {
    LOW_15(BatteryThresholds.LOW_PERCENT),
    CRITICAL_5(BatteryThresholds.CRITICAL_PERCENT),
    CRITICAL_3(BatteryThresholds.CRITICAL_RESAVE_PERCENT),
    CRITICAL_1(BatteryThresholds.CRITICAL_LAST_RESAVE_PERCENT),
    PLUGGED(null),
    UNPLUGGED(null);

    val isCritical: Boolean get() = this == CRITICAL_5 || this == CRITICAL_3 || this == CRITICAL_1

    companion object {
        val THRESHOLDS: List<BatteryEvent> = listOf(LOW_15, CRITICAL_5, CRITICAL_3, CRITICAL_1)
    }
}

/**
 * One battery reading and what it set off. [passed] holds the thresholds this discharge has
 * already gone below; a passed threshold stays silent until the charger goes in again.
 */
data class BatteryStep(
    val sample: BatterySample,
    val passed: Set<BatteryEvent>,
    val events: List<BatteryEvent>
)

/**
 * A threshold fires when a discharge goes below it, once per discharge. A discharge that starts
 * below a threshold, at the first reading or at unplugging, counts that threshold as passed
 * without firing it. Charging clears every passed threshold.
 */
fun advanceBattery(previous: BatteryStep?, current: BatterySample): BatteryStep {
    if (current.charging) {
        val events = if (previous != null && !previous.sample.charging) listOf(BatteryEvent.PLUGGED) else emptyList()
        return BatteryStep(current, emptySet(), events)
    }
    val below = BatteryEvent.THRESHOLDS.filter { threshold -> current.level <= checkNotNull(threshold.thresholdPercent) }
    if (previous == null || previous.sample.charging) {
        val events = if (previous != null) listOf(BatteryEvent.UNPLUGGED) else emptyList()
        return BatteryStep(current, below.toSet(), events)
    }
    val crossed = below.filter { it !in previous.passed }
    return BatteryStep(current, previous.passed + crossed, crossed)
}
