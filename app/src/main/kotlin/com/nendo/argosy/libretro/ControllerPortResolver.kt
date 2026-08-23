package com.nendo.argosy.libretro

import android.view.InputDevice
import com.nendo.argosy.data.local.entity.ControllerOrderEntity
import com.swordfish.libretrodroid.PortResolver

class ControllerPortResolver : PortResolver {
    private var controllerOrder: Map<String, Int> = emptyMap()
    private var autoOrder: Map<String, Int> = emptyMap()

    fun setControllerOrder(orders: List<ControllerOrderEntity>) {
        controllerOrder = orders.associate { it.controllerId to it.port }
    }

    /**
     * Seats the pads that are connected with no assignment of their own, so a session started with
     * only external controllers still has a player one. Android hands out controller numbers per
     * device rather than per session, and leaves 0 on pads it cannot number, so the numbers alone
     * can seat the only connected pad as player two. Any stored assignment outranks this.
     */
    fun setAutoDetectedOrder(devices: List<InputDevice>) {
        val taken = controllerOrder.values.toMutableSet()
        autoOrder = devices
            .sortedWith(compareBy({ it.controllerNumber <= 0 }, { it.controllerNumber }, { it.id }))
            .map { getControllerId(it) }
            .filterNot { it in controllerOrder }
            .associateWith {
                var port = 0
                while (port in taken) port++
                taken.add(port)
                port
            }
    }

    fun clearControllerOrder() {
        controllerOrder = emptyMap()
        autoOrder = emptyMap()
    }

    override fun getPort(device: InputDevice): Int {
        val controllerId = getControllerId(device)
        return controllerOrder[controllerId] ?: getDefaultPort(device)
    }

    fun getPort(controllerId: String, fallbackControllerNumber: Int): Int {
        return controllerOrder[controllerId]
            ?: autoOrder[controllerId]
            ?: (fallbackControllerNumber - 1).coerceAtLeast(0)
    }

    fun hasCustomOrder(): Boolean = controllerOrder.isNotEmpty()

    private fun getDefaultPort(device: InputDevice): Int {
        return autoOrder[getControllerId(device)] ?: (device.controllerNumber - 1).coerceAtLeast(0)
    }

    private fun getControllerId(device: InputDevice): String {
        return "${device.vendorId}:${device.productId}:${device.descriptor}"
    }

    companion object {
        fun getControllerId(device: InputDevice): String {
            return "${device.vendorId}:${device.productId}:${device.descriptor}"
        }
    }
}
