package com.nendo.argosy.libretro

import android.view.InputDevice
import com.nendo.argosy.core.input.ControllerDetector
import com.nendo.argosy.core.input.isPhysicalGamepad
import com.nendo.argosy.data.local.entity.ControllerOrderEntity
import com.swordfish.libretrodroid.PortResolver

/**
 * Seats pads on their settings-assigned port, otherwise on their first input. A built-in pad
 * leaves player one open while an external pad is connected and unseated, and gives its seat
 * back once no external pad remains.
 */
class ControllerPortResolver : PortResolver {
    private var controllerOrder: Map<String, Int> = emptyMap()
    private val claimedPorts = mutableMapOf<String, Int>()

    var onPortClaimed: ((controllerId: String, port: Int) -> Unit)? = null

    fun setControllerOrder(orders: List<ControllerOrderEntity>) {
        controllerOrder = orders.associate { it.controllerId to it.port }
        claimedPorts.keys.removeAll(controllerOrder.keys)
    }

    fun clearControllerOrder() {
        controllerOrder = emptyMap()
        claimedPorts.clear()
    }

    /**
     * Drops the claims of disconnected pads, and of built-in pads once no external pad remains.
     * Settings assignments are kept.
     */
    fun releaseDisconnected(connectedControllerIds: Set<String>) {
        claimedPorts.keys.retainAll(connectedControllerIds)
        val pads = connectedGamepads()
        if (pads.any { !ControllerDetector.isBuiltInPad(it) }) return
        pads.filter { ControllerDetector.isBuiltInPad(it) }
            .forEach { claimedPorts.remove(getControllerId(it)) }
    }

    fun claimedPortFor0(): String? = claimedPorts.entries.firstOrNull { it.value == 0 }?.key

    override fun getPort(device: InputDevice): Int {
        if (device.id == android.view.KeyCharacterMap.VIRTUAL_KEYBOARD) return 0
        val controllerId = getControllerId(device)
        controllerOrder[controllerId]?.let { return it }
        claimedPorts[controllerId]?.let { return it }
        return claimPort(controllerId, lowestPort = if (yieldsPlayerOne(device)) 1 else 0)
    }

    /**
     * The port this pad holds right now, without seating it. For describing pads rather than
     * routing their input: asking is not playing, and it must not take player one.
     */
    fun peekPort(device: InputDevice): Int? {
        val controllerId = getControllerId(device)
        return controllerOrder[controllerId] ?: claimedPorts[controllerId]
    }

    fun getPort(controllerId: String, fallbackControllerNumber: Int): Int {
        return controllerOrder[controllerId]
            ?: claimedPorts[controllerId]
            ?: (fallbackControllerNumber - 1).coerceAtLeast(0)
    }

    fun hasCustomOrder(): Boolean = controllerOrder.isNotEmpty()

    private fun claimPort(controllerId: String, lowestPort: Int): Int {
        val taken = controllerOrder.values.toSet() + claimedPorts.values.toSet()
        var port = lowestPort
        while (port in taken) port++
        claimedPorts[controllerId] = port
        onPortClaimed?.invoke(controllerId, port)
        return port
    }

    private fun yieldsPlayerOne(device: InputDevice): Boolean {
        if (!device.isPhysicalGamepad() || !ControllerDetector.isBuiltInPad(device)) return false
        return connectedGamepads().any { pad ->
            !ControllerDetector.isBuiltInPad(pad) && isUnseated(getControllerId(pad))
        }
    }

    private fun isUnseated(controllerId: String): Boolean =
        controllerId !in controllerOrder && controllerId !in claimedPorts

    private fun connectedGamepads(): List<InputDevice> =
        InputDevice.getDeviceIds().toList()
            .mapNotNull { InputDevice.getDevice(it) }
            .filter { it.isPhysicalGamepad() }

    private fun getControllerId(device: InputDevice): String {
        return "${device.vendorId}:${device.productId}:${device.descriptor}"
    }

    companion object {
        fun getControllerId(device: InputDevice): String {
            return "${device.vendorId}:${device.productId}:${device.descriptor}"
        }
    }
}
