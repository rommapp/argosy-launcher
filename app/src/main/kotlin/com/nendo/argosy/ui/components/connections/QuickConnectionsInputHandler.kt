package com.nendo.argosy.ui.components.connections

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.preferences.MenuWrapMode
import com.nendo.argosy.ui.input.InputDispatcher
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.stepOption
import com.nendo.argosy.ui.screens.settings.sections.input.toggleLeftRight

/**
 * Gamepad and touch routing for a Connections list. Left and right switch tabs on the tabs row and
 * the triggers switch them from any row. The context button held on a forgettable row feeds the
 * controller's forget hold through the key repeats.
 */
class QuickConnectionsInputHandler(
    private val controller: QuickConnectionsController,
    private val wrapMode: () -> MenuWrapMode
) : InputHandler {

    private fun focusedRow(): QuickConnectionsRow? = quickConnectionsFocusedRow(controller.state.value)

    override fun onUp(): InputResult = controller.moveFocus(-1, wrapMode())

    override fun onDown(): InputResult = controller.moveFocus(1, wrapMode())

    override fun onLeft(): InputResult = horizontal(-1)

    override fun onRight(): InputResult = horizontal(1)

    override fun onPrevTrigger(): InputResult = stepList(-1)

    override fun onNextTrigger(): InputResult = stepList(1)

    private fun stepList(delta: Int): InputResult =
        stepOption(ConnectionsList.entries, controller.state.value.list, delta, controller::showList)

    private fun horizontal(delta: Int): InputResult {
        val state = controller.state.value
        return when (focusedRow()) {
            QuickConnectionsRow.ListTabs -> stepList(delta)
            QuickConnectionsRow.WifiToggle -> if (state.canToggleWifi) {
                toggleLeftRight(delta, state.wifiEnabled, controller::setWifiEnabled)
            } else {
                InputResult.handled(SoundType.SILENT)
            }
            QuickConnectionsRow.BluetoothToggle -> if (state.canSwitchBluetoothDirectly(on = delta > 0)) {
                toggleLeftRight(delta, state.bluetoothEnabled, controller::setBluetoothEnabled)
            } else {
                InputResult.handled(SoundType.SILENT)
            }
            else -> InputResult.UNHANDLED
        }
    }

    override fun onConfirm(): InputResult {
        val row = focusedRow() ?: return InputResult.UNHANDLED
        return activate(row)
    }

    override fun onContextMenu(): InputResult {
        val row = focusedRow() ?: return InputResult.UNHANDLED
        if (!canForget(row)) return InputResult.UNHANDLED
        val isRepeat = InputDispatcher.currentIsRepeat
        controller.holdForget(isRepeat = isRepeat)
        return if (isRepeat) InputResult.handled(SoundType.SILENT) else InputResult.HANDLED
    }

    override fun onBack(): InputResult =
        if (controller.releaseForget()) InputResult.HANDLED else InputResult.UNHANDLED

    fun tapRow(row: QuickConnectionsRow) {
        if (!focusOn(row)) return
        activate(row)
    }

    fun tapList(list: ConnectionsList) {
        focusOn(QuickConnectionsRow.ListTabs)
        controller.showList(list)
    }

    fun forget(row: QuickConnectionsRow) {
        if (!canForget(row) || !focusOn(row)) return
        when (row) {
            is QuickConnectionsRow.Saved -> controller.forget(row.network)
            is QuickConnectionsRow.Paired -> controller.forgetDevice(row.device)
            else -> Unit
        }
    }

    fun focusRow(row: QuickConnectionsRow) {
        focusOn(row)
    }

    private fun canForget(row: QuickConnectionsRow): Boolean =
        quickConnectionsCanForget(controller.state.value, row)

    private fun focusOn(row: QuickConnectionsRow): Boolean {
        val state = controller.state.value
        val index = quickConnectionsFocusableRows(state).indexOfFirst { it.key == row.key }
        if (index < 0) return false
        if (index != quickConnectionsFocusIndex(state)) controller.setFocus(index)
        return true
    }

    private fun activate(row: QuickConnectionsRow): InputResult {
        val state = controller.state.value
        return when (row) {
            QuickConnectionsRow.WifiToggle -> activateWifiToggle(state)
            QuickConnectionsRow.BluetoothToggle -> activateBluetoothToggle(state)
            is QuickConnectionsRow.Nearby -> if (state.canJoinWifi) {
                controller.selectNearby(row.network)
                if (row.network.security.needsPassphrase) InputResult.handled(SoundType.OPEN_MODAL) else InputResult.HANDLED
            } else {
                controller.openWifiPanel()
                InputResult.HANDLED
            }
            QuickConnectionsRow.ScanNetworks -> {
                controller.scanNetworks()
                InputResult.HANDLED
            }
            QuickConnectionsRow.WifiPermission -> {
                controller.requestPermission(ConnectionsList.WIFI)
                InputResult.HANDLED
            }
            QuickConnectionsRow.BluetoothPermission -> {
                controller.requestPermission(ConnectionsList.BLUETOOTH)
                InputResult.HANDLED
            }
            QuickConnectionsRow.PairController -> {
                controller.toggleSearch()
                InputResult.HANDLED
            }
            is QuickConnectionsRow.NearbyController -> {
                controller.pair(row.device)
                InputResult.HANDLED
            }
            else -> InputResult.handled(SoundType.SILENT)
        }
    }

    private fun activateWifiToggle(state: QuickConnectionsState): InputResult {
        if (!state.canToggleWifi) {
            controller.openWifiPanel()
            return InputResult.HANDLED
        }
        val enabled = !state.wifiEnabled
        controller.setWifiEnabled(enabled)
        return InputResult.toggled(enabled)
    }

    private fun activateBluetoothToggle(state: QuickConnectionsState): InputResult {
        val enabled = !state.bluetoothEnabled
        if (state.canSwitchBluetoothDirectly(on = enabled)) {
            controller.setBluetoothEnabled(enabled)
            return InputResult.toggled(enabled)
        }
        if (enabled) controller.requestBluetoothOn() else controller.openBluetoothSettings()
        return InputResult.HANDLED
    }
}
