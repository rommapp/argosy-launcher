package com.nendo.argosy.core.input

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.InputEvent
import com.nendo.argosy.hardware.pad.MutedPadDevices
import com.nendo.argosy.util.SafeCoroutineScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val CONTROLLER_EVENT_BUFFER = 16
private const val BATTERY_POLL_MS = 60_000L

fun controllerIdOf(device: InputDevice): String =
    "${device.vendorId}:${device.productId}:${device.descriptor}"

@Singleton
class ConnectedControllerTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
    private val scope = SafeCoroutineScope(Dispatchers.IO, "ConnectedControllerTracker")
    private val activeState = ActiveControllerState()
    private val deviceGeneration = MutableStateFlow(0)
    private val presence = ControllerPresence()

    private val _controllers = MutableStateFlow(readControllers().also { synchronized(presence) { presence.update(it) } })

    /**
     * Every physical pad connected now, with its name and battery level. Battery levels are read
     * again at each device change and once a minute while a non-built-in pad is connected.
     */
    val controllers: StateFlow<List<ConnectedPad>> = _controllers.asStateFlow()

    private val _connectedControllerIds = MutableStateFlow(idsOf(_controllers.value))
    val connectedControllerIds: StateFlow<Set<String>> = _connectedControllerIds.asStateFlow()

    private val _controllerEvents = MutableSharedFlow<ControllerEvent>(
        extraBufferCapacity = CONTROLLER_EVENT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * Pads connecting and disconnecting after this process first read its pads. Pads present at
     * that first read never appear here as connecting.
     */
    val controllerEvents: SharedFlow<ControllerEvent> = _controllerEvents.asSharedFlow()

    val activeController: StateFlow<ActiveController?> = activeState.active

    /**
     * Layout of [activeController], or of the first connected pad until one has sent input.
     * Re-detected whenever the active pad changes or any input device is added, changed or removed.
     */
    val activeLayout: StateFlow<DetectedLayout?> =
        combine(activeController, deviceGeneration) { active, _ -> detectActive(active).layout }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, detectActive(null).layout)

    private val removedDeviceIds: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    private val listener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = refresh()
        override fun onInputDeviceChanged(deviceId: Int) = refresh()
        override fun onInputDeviceRemoved(deviceId: Int) {
            removedDeviceIds.add(deviceId)
            refresh()
        }
    }

    init {
        inputManager.registerInputDeviceListener(listener, null)
        scope.launch { pollBattery() }
        scope.launch { MutedPadDevices.devices.drop(1).collect { refresh() } }
    }

    /**
     * Whether the input device [deviceId] has been removed since this process started. Android
     * never reuses a device id within a boot.
     */
    fun wasRemoved(deviceId: Int): Boolean = deviceId in removedDeviceIds

    fun noteInput(event: InputEvent) = activeState.noteInput(event)

    /**
     * Freezes [activeController] against input while a mapping capture waits for its press.
     */
    fun holdActiveController(held: Boolean) = activeState.setHeld(held)

    fun activeDevice(): InputDevice? = deviceOf(activeController.value)

    fun detectActive(): DetectionResult = detectActive(activeController.value)

    fun refresh() {
        val snapshot = readControllers()
        val events = synchronized(presence) { presence.update(snapshot) }
        _controllers.value = snapshot
        _connectedControllerIds.value = idsOf(snapshot)
        events.forEach { _controllerEvents.tryEmit(it) }
        activeState.onPadsChanged(connectedPads().map { it.toActiveController() })
        deviceGeneration.value++
    }

    private suspend fun pollBattery() {
        while (true) {
            delay(BATTERY_POLL_MS)
            if (_controllers.value.any { !it.builtIn }) refreshBattery()
        }
    }

    private fun refreshBattery() {
        val levels = readControllers().associate { it.controllerId to it.batteryPercent }
        _controllers.update { current ->
            current.map { pad -> pad.copy(batteryPercent = levels[pad.controllerId] ?: pad.batteryPercent) }
        }
    }

    private fun deviceOf(active: ActiveController?): InputDevice? =
        active?.let { InputDevice.getDevice(it.deviceId) }?.takeIf { it.actsAsGamepad() }

    private fun detectActive(active: ActiveController?): DetectionResult =
        ControllerDetector.detectFor(deviceOf(active))

    private fun connectedPads(): List<InputDevice> =
        InputDevice.getDeviceIds()
            .toList()
            .mapNotNull { InputDevice.getDevice(it) }
            .filter { it.actsAsGamepad() }

    private fun readControllers(): List<ConnectedPad> =
        InputDevice.getDeviceIds()
            .toList()
            .mapNotNull { InputDevice.getDevice(it) }
            .filter { it.isPhysicalGamepad() }
            .map { it.toConnectedPad() }
            .distinctBy { it.controllerId }

    private fun idsOf(controllers: List<ConnectedPad>): Set<String> =
        controllers.mapTo(mutableSetOf()) { it.controllerId }
}
