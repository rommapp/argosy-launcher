package com.nendo.argosy.ui.components.connections

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkWifi1Bar
import androidx.compose.material.icons.filled.NetworkWifi2Bar
import androidx.compose.material.icons.filled.NetworkWifi3Bar
import androidx.compose.material.icons.filled.SignalWifi0Bar
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.nendo.argosy.R
import com.nendo.argosy.hardware.BluetoothDeviceKind
import com.nendo.argosy.hardware.WifiBand
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.QuickDetailRow
import com.nendo.argosy.ui.components.QuickNoticeRow
import com.nendo.argosy.ui.components.QuickRowFrame
import com.nendo.argosy.ui.primitives.SegmentedControl
import com.nendo.argosy.ui.components.QuickSectionHeader
import com.nendo.argosy.ui.components.QuickStatusRow
import com.nendo.argosy.ui.components.QuickToggleRow
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

@Composable
fun QuickSettingsConnectionsPage(
    state: QuickConnectionsState,
    onRowClick: (QuickConnectionsRow) -> Unit,
    onShowList: (ConnectionsList) -> Unit,
    onForget: (QuickConnectionsRow.Saved) -> Unit,
    onForgetDevice: (QuickConnectionsRow.Paired) -> Unit
) {
    if (!state.switchable) {
        LimitedConnections(state)
        return
    }
    val rows = remember(state) { quickConnectionsRows(state) }
    val focusedKey = remember(state) { quickConnectionsFocusedRow(state)?.key }
    val listState = rememberLazyListState()

    LaunchedEffect(focusedKey, rows.size) {
        val listIndex = rows.indexOfFirst { it.key == focusedKey }
        if (listIndex >= 0) listState.animateScrollToItemCentered(listIndex)
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(rows, key = { it.key }) { row ->
            ConnectionsRow(
                row = row,
                state = state,
                isFocused = row.key == focusedKey,
                onClick = { onRowClick(row) },
                onShowList = onShowList,
                onForget = onForget,
                onForgetDevice = onForgetDevice
            )
        }
    }
}

@Composable
fun QuickConnectionsEffects(controller: QuickConnectionsController, pageVisible: Boolean) {
    LaunchedEffect(controller, pageVisible) {
        if (pageVisible) controller.onPageShown() else controller.onPageHidden()
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        controller.onBluetoothPermissionResult()
    }
    LaunchedEffect(controller, permissionLauncher) {
        controller.bluetoothPermissionRequests.collect { permissions ->
            if (permissions.isEmpty()) {
                controller.onBluetoothPermissionResult()
            } else {
                permissionLauncher.launch(permissions.toTypedArray())
            }
        }
    }
}

@Composable
private fun LimitedConnections(state: QuickConnectionsState) {
    Column(modifier = Modifier.fillMaxSize()) {
        WifiStatusRow(state)
        BluetoothStatusRow(state)
        if (state.loaded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(Dimens.spacingLg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.ui_quick_connections_unsupported),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalArgosyTheme.current.textDim,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ConnectionsRow(
    row: QuickConnectionsRow,
    state: QuickConnectionsState,
    isFocused: Boolean,
    onClick: () -> Unit,
    onShowList: (ConnectionsList) -> Unit,
    onForget: (QuickConnectionsRow.Saved) -> Unit,
    onForgetDevice: (QuickConnectionsRow.Paired) -> Unit
) {
    when (row) {
        QuickConnectionsRow.ListTabs -> QuickRowFrame(isFocused = isFocused, onClick = null) {
            SegmentedControl(
                options = listOf(
                    stringResource(R.string.ui_quick_connections_tab_wifi),
                    stringResource(R.string.ui_quick_connections_tab_bluetooth)
                ),
                selectedIndex = ConnectionsList.entries.indexOf(state.list),
                onSelect = { onShowList(ConnectionsList.entries[it]) },
                focused = isFocused,
                modifier = Modifier.fillMaxWidth()
            )
        }
        QuickConnectionsRow.WifiOffPlaceholder -> PlaceholderText(R.string.ui_quick_connections_wifi_off)
        QuickConnectionsRow.BluetoothOffPlaceholder -> PlaceholderText(R.string.ui_quick_connections_bluetooth_off)
        QuickConnectionsRow.WifiStatus -> WifiStatusRow(state)
        QuickConnectionsRow.BluetoothStatus -> BluetoothStatusRow(state)
        QuickConnectionsRow.WifiToggle -> QuickToggleRow(
            icon = if (state.wifiEnabled) Icons.Default.Wifi else Icons.Default.WifiOff,
            label = stringResource(R.string.ui_quick_settings_wifi),
            checked = state.wifiEnabled,
            isFocused = isFocused,
            onFocus = {},
            onToggle = { onClick() },
            description = stringResource(wifiStatusRes(state)).takeIf { state.wifiEnabled }
        )
        is QuickConnectionsRow.CurrentNetwork -> QuickDetailRow(
            icon = signalIcon(row.network.signalLevel),
            label = row.network.ssid,
            isFocused = false,
            onClick = null,
            valueText = row.network.band?.let { stringResource(it.labelRes) }
        )
        QuickConnectionsRow.NearbyHeader ->
            QuickSectionHeader(title = stringResource(R.string.ui_quick_connections_nearby))
        QuickConnectionsRow.NearbyPlaceholder -> PlaceholderText(
            if (state.scanning) R.string.ui_quick_connections_scanning else R.string.ui_quick_connections_no_networks
        )
        is QuickConnectionsRow.Nearby -> QuickDetailRow(
            icon = signalIcon(row.network.signalLevel),
            label = row.network.ssid,
            isFocused = isFocused,
            onClick = onClick,
            valueText = when (row.network.ssid) {
                state.joiningSsid -> stringResource(R.string.ui_quick_connections_row_joining)
                state.failedSsid -> stringResource(R.string.ui_quick_connections_row_failed)
                else -> null
            }
        ) {
            if (row.network.secured) {
                Spacer(modifier = Modifier.width(Dimens.spacingSm))
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = stringResource(R.string.ui_quick_connections_secured),
                    tint = LocalArgosyTheme.current.textDim,
                    modifier = Modifier.size(Dimens.iconSm)
                )
            }
        }
        QuickConnectionsRow.SavedHeader ->
            QuickSectionHeader(title = stringResource(R.string.ui_quick_connections_saved))
        is QuickConnectionsRow.Saved -> QuickDetailRow(
            icon = Icons.Default.Wifi,
            label = row.network.ssid,
            isFocused = isFocused,
            onClick = onClick
        ) {
            Spacer(modifier = Modifier.width(Dimens.spacingSm))
            ActionButton(
                label = stringResource(R.string.ui_quick_connections_forget),
                onClick = { onForget(row) },
                focused = isFocused,
                glyph = InputButton.X
            )
        }
        QuickConnectionsRow.BluetoothToggle -> QuickToggleRow(
            icon = if (state.bluetoothEnabled) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
            label = stringResource(R.string.ui_quick_settings_bluetooth),
            checked = state.bluetoothEnabled,
            isFocused = isFocused,
            onFocus = {},
            onToggle = { onClick() }
        )
        QuickConnectionsRow.BluetoothPermission -> QuickNoticeRow(
            message = stringResource(R.string.ui_quick_connections_bluetooth_permission),
            actionLabel = stringResource(R.string.ui_quick_connections_bluetooth_permission_allow),
            isFocused = isFocused,
            onAction = onClick
        )
        QuickConnectionsRow.PairedHeader ->
            QuickSectionHeader(title = stringResource(R.string.ui_quick_connections_paired))
        QuickConnectionsRow.PairedPlaceholder -> PlaceholderText(R.string.ui_quick_connections_no_paired)
        is QuickConnectionsRow.Paired -> QuickDetailRow(
            icon = row.device.kind.icon,
            label = row.device.name,
            isFocused = isFocused,
            onClick = onClick,
            valueText = pairedValue(row.device)
        ) {
            if (state.canForgetDevices) {
                Spacer(modifier = Modifier.width(Dimens.spacingSm))
                ActionButton(
                    label = stringResource(R.string.ui_quick_connections_forget_device),
                    onClick = { onForgetDevice(row) },
                    focused = isFocused,
                    glyph = InputButton.X
                )
            }
        }
        QuickConnectionsRow.PairController -> QuickDetailRow(
            icon = Icons.Default.BluetoothSearching,
            label = stringResource(R.string.ui_quick_connections_pair_controller),
            isFocused = isFocused,
            onClick = onClick,
            valueText = stringResource(R.string.ui_quick_connections_pair_stop).takeIf { state.discovering }
        )
        QuickConnectionsRow.NearbyControllersHeader ->
            QuickSectionHeader(title = stringResource(R.string.ui_quick_connections_nearby_controllers))
        QuickConnectionsRow.NearbyControllersPlaceholder -> PlaceholderText(
            if (state.discovering) R.string.ui_quick_connections_controllers_searching
            else R.string.ui_quick_connections_no_controllers
        )
        is QuickConnectionsRow.NearbyController -> QuickDetailRow(
            icon = Icons.Default.SportsEsports,
            label = row.device.name,
            isFocused = isFocused,
            onClick = onClick,
            valueText = when (row.device.address) {
                state.pairingAddress -> stringResource(R.string.ui_quick_connections_row_pairing)
                state.pairFailedAddress -> stringResource(R.string.ui_quick_connections_row_pair_failed)
                else -> null
            }
        )
    }
}

@Composable
private fun WifiStatusRow(state: QuickConnectionsState) {
    QuickStatusRow(
        icon = if (state.wifiEnabled) Icons.Default.Wifi else Icons.Default.WifiOff,
        label = stringResource(R.string.ui_quick_settings_wifi),
        valueText = stringResource(wifiStatusRes(state))
    )
}

@Composable
private fun BluetoothStatusRow(state: QuickConnectionsState) {
    QuickStatusRow(
        icon = if (state.bluetoothEnabled) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
        label = stringResource(R.string.ui_quick_settings_bluetooth),
        valueText = stringResource(
            if (state.bluetoothEnabled) R.string.ui_quick_settings_radio_on else R.string.ui_quick_settings_radio_off
        )
    )
}

@Composable
private fun PlaceholderText(@StringRes textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodyMedium,
        color = LocalArgosyTheme.current.textDim,
        modifier = Modifier.padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
    )
}

@Composable
private fun pairedValue(device: BluetoothPairedUi): String {
    val battery = device.batteryPercent
    return when {
        device.connected && battery != null -> stringResource(R.string.ui_quick_connections_battery, battery)
        device.connected -> stringResource(R.string.ui_quick_connections_device_connected)
        else -> stringResource(R.string.ui_quick_connections_device_not_connected)
    }
}

@StringRes
private fun wifiStatusRes(state: QuickConnectionsState): Int = when {
    !state.wifiEnabled -> R.string.ui_quick_settings_radio_off
    state.wifiConnected -> R.string.ui_quick_settings_wifi_connected
    else -> R.string.ui_quick_settings_wifi_not_connected
}

private fun signalIcon(level: Int): ImageVector = when (level) {
    0 -> Icons.Default.SignalWifi0Bar
    1 -> Icons.Default.NetworkWifi1Bar
    2 -> Icons.Default.NetworkWifi2Bar
    3 -> Icons.Default.NetworkWifi3Bar
    else -> Icons.Default.SignalWifi4Bar
}

@get:StringRes
private val WifiBand.labelRes: Int
    get() = when (this) {
        WifiBand.GHZ_2_4 -> R.string.ui_quick_connections_band_2_4
        WifiBand.GHZ_5 -> R.string.ui_quick_connections_band_5
        WifiBand.GHZ_6 -> R.string.ui_quick_connections_band_6
    }

private val BluetoothDeviceKind.icon: ImageVector
    get() = when (this) {
        BluetoothDeviceKind.CONTROLLER -> Icons.Default.SportsEsports
        BluetoothDeviceKind.AUDIO -> Icons.Default.Headphones
        BluetoothDeviceKind.OTHER -> Icons.Default.Bluetooth
    }
