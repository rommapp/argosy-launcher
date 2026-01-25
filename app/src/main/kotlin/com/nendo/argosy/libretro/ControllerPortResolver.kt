package com.nendo.argosy.libretro

import android.view.InputDevice
import com.nendo.argosy.data.local.entity.ControllerOrderEntity
import com.swordfish.libretrodroid.PortResolver

class ControllerPortResolver : PortResolver {
    private var controllerOrder: Map<String, Int> = emptyMap()

    fun setControllerOrder(orders: List<ControllerOrderEntity>) {
        controllerOrder = orders.associate { it.controllerId to it.port }
    }

    fun clearControllerOrder() {
        controllerOrder = emptyMap()
    }

    override fun getPort(device: InputDevice): Int {
        val controllerId = getControllerId(device)
        return controllerOrder[controllerId] ?: getDefaultPort(device)
    }

    fun getPort(controllerId: String, fallbackControllerNumber: Int): Int {
        return controllerOrder[controllerId] ?: (fallbackControllerNumber - 1).coerceAtLeast(0)
    }

    fun hasCustomOrder(): Boolean = controllerOrder.isNotEmpty()

    private fun getDefaultPort(device: InputDevice): Int {
        return (device.controllerNumber - 1).coerceAtLeast(0)
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
