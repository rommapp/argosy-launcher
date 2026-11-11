package com.nendo.argosy.libretro

import android.view.InputDevice
import com.nendo.argosy.core.input.ControllerDetector
import com.nendo.argosy.core.input.controllerIdOf
import com.nendo.argosy.core.input.isPhysicalGamepad
import com.nendo.argosy.data.local.entity.ControllerOrderEntity
import com.swordfish.libretrodroid.PortResolver
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Seats each pad on its session seat, its settings-assigned port, or the port its first input
 * claims, in that order. A built-in pad yields player one to an unseated external pad and takes
 * it back when it is the only pad and the saved player one is absent. Session seats never change
 * settings assignments.
 */
class ControllerPortResolver : PortResolver {
    private var controllerOrder: Map<String, Int> = emptyMap()
    private val claimedPorts = mutableMapOf<String, Int>()
    private val seatTakers = mutableMapOf<Int, String>()
    private var sessionSeats: Map<String, Int> = emptyMap()
    private var launchPadId: String? = null
    private var docked = false
    private val portClaimedListeners = CopyOnWriteArrayList<(controllerId: String, port: Int) -> Unit>()

    /**
     * Runs [listener] on the input path each time a pad takes a seat, with the seat it holds. A
     * listener must return at once and hand any further work off.
     */
    fun addPortClaimedListener(listener: (controllerId: String, port: Int) -> Unit) {
        portClaimedListeners += listener
    }

    fun removePortClaimedListener(listener: (controllerId: String, port: Int) -> Unit) {
        portClaimedListeners -= listener
    }

    fun setControllerOrder(orders: List<ControllerOrderEntity>) {
        controllerOrder = orders.associate { it.controllerId to it.port }
        claimedPorts.keys.removeAll(controllerOrder.keys)
        reseatLaunchPad()
    }

    fun clearControllerOrder() {
        controllerOrder = emptyMap()
        claimedPorts.clear()
        seatTakers.clear()
        reseatLaunchPad()
    }

    /**
     * Names the pad that launched this session and whether an external display was docked when it
     * started. Read once at session start.
     */
    fun setLaunchPad(controllerId: String?, isDocked: Boolean) {
        launchPadId = controllerId
        docked = isDocked
        reseatLaunchPad()
    }

    /**
     * Drops the claims of disconnected pads, and of built-in pads once no external pad remains.
     * Settings assignments are kept. Returns the seats the disconnected pads held before the
     * release, by controller id.
     */
    fun releaseDisconnected(connectedControllerIds: Set<String>): Map<String, Int> {
        val released = (sessionSeats.keys + claimedPorts.keys)
            .filter { it !in connectedControllerIds }
            .mapNotNull { id -> seatOf(id)?.let { id to it } }
            .toMap()
        claimedPorts.keys.retainAll(connectedControllerIds)
        seatTakers.values.retainAll(connectedControllerIds)
        val pads = connectedGamepads()
        if (pads.none { !ControllerDetector.isBuiltInPad(it) }) {
            pads.filter { ControllerDetector.isBuiltInPad(it) }
                .forEach { claimedPorts.remove(controllerIdOf(it)) }
        }
        reseatLaunchPad()
        return released
    }

    /**
     * Seats this pad on [port] for the rest of the session, ahead of a settings assignment that
     * names another pad for it. Settings assignments themselves are kept.
     */
    fun takeSeat(controllerId: String, port: Int) {
        claimedPorts.entries.removeAll { it.value == port && it.key != controllerId }
        seatTakers.entries.removeAll { it.value == controllerId }
        claimedPorts[controllerId] = port
        seatTakers[port] = controllerId
        reseatLaunchPad()
        notifyClaimed(controllerId, seatOf(controllerId) ?: port)
    }

    fun playerOneControllerId(): String? =
        sessionSeats.entries.firstOrNull { it.value == 0 }?.key
            ?: seatTakers[0]
            ?: controllerOrder.entries.firstOrNull { it.value == 0 }?.key
            ?: claimedPorts.entries.firstOrNull { it.value == 0 }?.key

    override fun getPort(device: InputDevice): Int {
        if (device.id == android.view.KeyCharacterMap.VIRTUAL_KEYBOARD) return 0
        val controllerId = controllerIdOf(device)
        if (sessionSeats.isNotEmpty() && !ControllerDetector.isBuiltInPad(device)) reseatLaunchPad()
        seatOf(controllerId)?.let { return it }
        claimPort(controllerId, lowestPort = if (yieldsPlayerOne(device)) 1 else 0)
        return seatOf(controllerId) ?: 0
    }

    /**
     * The port this pad holds right now. Never seats the pad, so it never takes player one.
     */
    fun peekPort(device: InputDevice): Int? = seatOf(controllerIdOf(device))

    fun getPort(controllerId: String, fallbackControllerNumber: Int): Int {
        return seatOf(controllerId) ?: (fallbackControllerNumber - 1).coerceAtLeast(0)
    }

    fun hasCustomOrder(): Boolean = controllerOrder.isNotEmpty()

    /**
     * The seat this pad holds right now, by controller id. A settings assignment whose seat another
     * pad has taken for the session does not count.
     */
    fun seatOf(controllerId: String): Int? =
        sessionSeats[controllerId]
            ?: controllerOrder[controllerId]?.takeIf { port -> seatTakers[port].let { it == null || it == controllerId } }
            ?: claimedPorts[controllerId]

    private fun claimPort(controllerId: String, lowestPort: Int) {
        val taken = controllerOrder.values.toSet() + claimedPorts.values.toSet() + sessionSeats.values.toSet()
        var port = lowestPort
        while (port in taken) port++
        claimedPorts[controllerId] = port
        reseatLaunchPad()
        notifyClaimed(controllerId, seatOf(controllerId) ?: port)
    }

    private fun notifyClaimed(controllerId: String, port: Int) {
        portClaimedListeners.forEach { it(controllerId, port) }
    }

    private fun reseatLaunchPad() {
        val pads = connectedGamepads()
        val loneBuiltIn = pads.singleOrNull { ControllerDetector.isBuiltInPad(it) }
            ?.takeIf { pads.none { pad -> !ControllerDetector.isBuiltInPad(pad) } }
        val playerOneHeldByAbsentPad = controllerOrder.entries.firstOrNull { it.value == 0 }?.key
            ?.let { seated -> pads.none { controllerIdOf(it) == seated } } == true
        if (loneBuiltIn != null && playerOneHeldByAbsentPad) {
            sessionSeats = mapOf(controllerIdOf(loneBuiltIn) to 0)
            return
        }
        if (!docked || launchPadId == null) {
            sessionSeats = emptyMap()
            return
        }
        val connected = connectedGamepads().map { pad ->
            SeatedPad(controllerIdOf(pad), ControllerDetector.isBuiltInPad(pad))
        }
        sessionSeats = ControllerSeating.launchPadSeats(
            docked = docked,
            launchPadId = launchPadId,
            connected = connected,
            seats = claimedPorts + controllerOrder
        )
    }

    private fun yieldsPlayerOne(device: InputDevice): Boolean {
        if (!device.isPhysicalGamepad() || !ControllerDetector.isBuiltInPad(device)) return false
        return connectedGamepads().any { pad ->
            !ControllerDetector.isBuiltInPad(pad) && isUnseated(controllerIdOf(pad))
        }
    }

    private fun isUnseated(controllerId: String): Boolean = seatOf(controllerId) == null

    private fun connectedGamepads(): List<InputDevice> =
        InputDevice.getDeviceIds().toList()
            .mapNotNull { InputDevice.getDevice(it) }
            .filter { it.isPhysicalGamepad() }
}
