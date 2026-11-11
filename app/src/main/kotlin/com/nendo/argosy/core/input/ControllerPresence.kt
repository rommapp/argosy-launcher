package com.nendo.argosy.core.input

import android.os.Build
import android.view.InputDevice
import kotlin.math.roundToInt

private const val PERCENT = 100f

/**
 * A connected pad as the last snapshot saw it. [batteryPercent] is null for a pad that reports no
 * battery.
 */
data class ConnectedPad(
    val controllerId: String,
    val name: String,
    val builtIn: Boolean,
    val batteryPercent: Int? = null
)

enum class ControllerChange { CONNECTED, DISCONNECTED }

data class ControllerEvent(val change: ControllerChange, val controller: ConnectedPad)

/**
 * Turns successive snapshots of the connected pads into connect and disconnect events. The first
 * snapshot is the baseline and produces none. A removed pad is reported with the name and flags it
 * had in the last snapshot that still held it, since the system forgets the device on removal.
 */
class ControllerPresence {
    private var known: Map<String, ConnectedPad>? = null

    fun update(snapshot: List<ConnectedPad>): List<ControllerEvent> {
        val current = snapshot.associateBy { it.controllerId }
        val previous = known
        known = current
        if (previous == null) return emptyList()
        val removed = previous.values
            .filter { it.controllerId !in current }
            .map { ControllerEvent(ControllerChange.DISCONNECTED, it) }
        val added = current.values
            .filter { it.controllerId !in previous }
            .map { ControllerEvent(ControllerChange.CONNECTED, it) }
        return removed + added
    }
}

fun InputDevice.toConnectedPad(): ConnectedPad = ConnectedPad(
    controllerId = controllerIdOf(this),
    name = name.orEmpty(),
    builtIn = ControllerDetector.isBuiltInPad(this),
    batteryPercent = controllerBatteryPercent(this)
)

/**
 * The pad's own battery level, or null where the system reports none for it or predates pad
 * battery reporting.
 */
fun controllerBatteryPercent(device: InputDevice): Int? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val battery = device.batteryState
    if (!battery.isPresent) return null
    val capacity = battery.capacity
    if (capacity.isNaN() || capacity < 0f) return null
    return (capacity * PERCENT).roundToInt().coerceIn(0, PERCENT.toInt())
}
