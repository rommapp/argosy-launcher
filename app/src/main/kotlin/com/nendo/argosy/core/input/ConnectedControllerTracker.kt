package com.nendo.argosy.core.input

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

fun controllerIdOf(device: InputDevice): String =
    "${device.vendorId}:${device.productId}:${device.descriptor}"

private fun InputDevice.isGamepad(): Boolean =
    (sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) ||
        (sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)

@Singleton
class ConnectedControllerTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager

    private val _connectedControllerIds = MutableStateFlow(readConnectedControllerIds())
    val connectedControllerIds: StateFlow<Set<String>> = _connectedControllerIds.asStateFlow()

    private val listener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = refresh()
        override fun onInputDeviceChanged(deviceId: Int) = refresh()
        override fun onInputDeviceRemoved(deviceId: Int) = refresh()
    }

    init {
        inputManager.registerInputDeviceListener(listener, null)
    }

    private fun refresh() {
        _connectedControllerIds.value = readConnectedControllerIds()
    }

    private fun readConnectedControllerIds(): Set<String> =
        InputDevice.getDeviceIds()
            .toList()
            .mapNotNull { InputDevice.getDevice(it) }
            .filter { it.isGamepad() }
            .mapTo(mutableSetOf()) { controllerIdOf(it) }
}
