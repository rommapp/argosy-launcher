package com.nendo.argosy.ui.components.connections

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.preferences.MenuWrapMode
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.stepOption
import com.nendo.argosy.ui.screens.settings.sections.input.toggleLeftRight

class QuickConnectionsInputHandler(
    private val controller: QuickConnectionsController,
    private val wrapMode: () -> MenuWrapMode
) : InputHandler {

    private fun focusedRow(): QuickConnectionsRow? = quickConnectionsFocusedRow(controller.state.value)

    override fun onUp(): InputResult = controller.moveFocus(-1, wrapMode())

    override fun onDown(): InputResult = controller.moveFocus(1, wrapMode())

    override fun onLeft(): InputResult = horizontal(-1)

    override fun onRight(): InputResult = horizontal(1)

    private fun horizontal(delta: Int): InputResult {
        val state = controller.state.value
        return when (focusedRow()) {
            QuickConnectionsRow.WifiToggle -> toggleLeftRight(delta, state.wifiEnabled, controller::setWifiEnabled)
            QuickConnectionsRow.BluetoothToggle ->
                toggleLeftRight(delta, state.bluetoothEnabled, controller::setBluetoothEnabled)
            QuickConnectionsRow.ListTabs ->
                stepOption(ConnectionsList.entries, state.list, delta, controller::showList)
            else -> InputResult.UNHANDLED
        }
    }

    override fun onConfirm(): InputResult {
        val row = focusedRow() ?: return InputResult.UNHANDLED
        return activate(row)
    }

    override fun onContextMenu(): InputResult = when (val row = focusedRow()) {
        is QuickConnectionsRow.Saved -> {
            controller.forget(row.network)
            InputResult.HANDLED
        }
        is QuickConnectionsRow.Paired -> if (controller.state.value.canForgetDevices) {
            controller.forgetDevice(row.device)
            InputResult.HANDLED
        } else {
            InputResult.UNHANDLED
        }
        else -> InputResult.UNHANDLED
    }

    fun tapRow(row: QuickConnectionsRow) {
        if (!focusOn(row)) return
        activate(row)
    }

    fun tapList(list: ConnectionsList) {
        focusOn(QuickConnectionsRow.ListTabs)
        controller.showList(list)
    }

    fun tapForget(row: QuickConnectionsRow.Saved) {
        if (!focusOn(row)) return
        controller.forget(row.network)
    }

    fun tapForgetDevice(row: QuickConnectionsRow.Paired) {
        if (!focusOn(row)) return
        controller.forgetDevice(row.device)
    }

    private fun focusOn(row: QuickConnectionsRow): Boolean {
        val index = quickConnectionsFocusableRows(controller.state.value).indexOf(row)
        if (index < 0) return false
        controller.setFocus(index)
        return true
    }

    private fun activate(row: QuickConnectionsRow): InputResult {
        val state = controller.state.value
        return when (row) {
            QuickConnectionsRow.WifiToggle -> {
                val enabled = !state.wifiEnabled
                controller.setWifiEnabled(enabled)
                InputResult.toggled(enabled)
            }
            QuickConnectionsRow.BluetoothToggle -> {
                val enabled = !state.bluetoothEnabled
                controller.setBluetoothEnabled(enabled)
                InputResult.toggled(enabled)
            }
            is QuickConnectionsRow.Nearby -> {
                controller.selectNearby(row.network)
                if (row.network.security.needsPassphrase) InputResult.handled(SoundType.OPEN_MODAL) else InputResult.HANDLED
            }
            QuickConnectionsRow.BluetoothPermission -> {
                controller.requestBluetoothPermission()
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
}
