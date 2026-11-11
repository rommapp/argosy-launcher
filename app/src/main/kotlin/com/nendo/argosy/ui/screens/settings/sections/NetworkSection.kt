package com.nendo.argosy.ui.screens.settings.sections

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.hardware.WifiBand
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.GamepadHoldTracker
import com.nendo.argosy.ui.components.ListSection
import com.nendo.argosy.ui.components.PreferenceTrailingButton
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.components.connections.ArrivalIcon
import com.nendo.argosy.ui.components.connections.BluetoothStatus
import com.nendo.argosy.ui.components.connections.ConnectionsList
import com.nendo.argosy.ui.components.connections.ConnectionsRowAction
import com.nendo.argosy.ui.components.connections.FocusedActionSlot
import com.nendo.argosy.ui.components.connections.ForgetHoldButton
import com.nendo.argosy.ui.components.connections.QuickConnectionsEffects
import com.nendo.argosy.ui.components.connections.QuickConnectionsInputHandler
import com.nendo.argosy.ui.components.connections.QuickConnectionsModals
import com.nendo.argosy.ui.components.connections.QuickConnectionsRow
import com.nendo.argosy.ui.components.connections.QuickConnectionsState
import com.nendo.argosy.ui.components.connections.RadioPermission
import com.nendo.argosy.ui.components.connections.SearchPulseIcon
import com.nendo.argosy.ui.components.connections.WifiStatus
import com.nendo.argosy.ui.components.connections.arrivalEntrance
import com.nendo.argosy.ui.components.connections.bluetoothStatusOf
import com.nendo.argosy.ui.components.connections.icon
import com.nendo.argosy.ui.components.connections.isArrival
import com.nendo.argosy.ui.components.connections.isGroupHeader
import com.nendo.argosy.ui.components.connections.isKept
import com.nendo.argosy.ui.components.connections.newArrivals
import com.nendo.argosy.ui.components.connections.quickConnectionsFocusIndex
import com.nendo.argosy.ui.components.connections.quickConnectionsRowAction
import com.nendo.argosy.ui.components.connections.quickConnectionsRows
import com.nendo.argosy.ui.components.connections.reducedMotion
import com.nendo.argosy.ui.components.connections.signalIcon
import com.nendo.argosy.ui.components.connections.successTint
import com.nendo.argosy.ui.components.connections.wifiStatusOf
import com.nendo.argosy.ui.components.connections.wifiToggleIcon
import com.nendo.argosy.ui.components.preferenceModifier
import com.nendo.argosy.ui.components.preferenceSecondaryColor
import com.nendo.argosy.ui.primitives.SegmentedControl
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.LocalSettingsTouchFocus
import com.nendo.argosy.ui.screens.settings.components.SectionHeader
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.util.formatRelativeTimeVerbose
import java.time.Instant

@StringRes
internal fun networkGroupTitleRes(row: QuickConnectionsRow, list: ConnectionsList): Int? = when (row) {
    QuickConnectionsRow.ListTabs -> when (list) {
        ConnectionsList.WIFI -> R.string.settings_network_group_wifi
        ConnectionsList.BLUETOOTH -> R.string.settings_network_group_bluetooth
    }
    QuickConnectionsRow.SavedHeader -> R.string.settings_network_group_saved
    QuickConnectionsRow.NearbyHeader -> R.string.settings_network_group_nearby
    QuickConnectionsRow.PairedHeader -> R.string.settings_network_group_paired
    QuickConnectionsRow.FoundHeader -> R.string.settings_network_group_found
    else -> null
}

internal fun networkSettingsSections(
    rows: List<QuickConnectionsRow>,
    nameOf: (QuickConnectionsRow) -> String? = { null }
): List<ListSection> {
    val focusable = rows.filter { it.focusable }
    val starts = rows.indices.filter { it == 0 || rows[it].isGroupHeader() }
    return starts.mapIndexedNotNull { order, start ->
        val end = (starts.getOrNull(order + 1) ?: rows.size) - 1
        val members = rows.subList(start, end + 1).filter { it.focusable }
        if (members.isEmpty()) return@mapIndexedNotNull null
        ListSection(
            name = nameOf(rows[start]),
            listStartIndex = start,
            listEndIndex = end,
            focusStartIndex = focusable.indexOf(members.first()),
            focusEndIndex = focusable.indexOf(members.last())
        )
    }
}

internal fun networkSettingsSections(state: QuickConnectionsState): List<ListSection> =
    networkSettingsSections(quickConnectionsRows(state))

private fun groupName(context: Context, row: QuickConnectionsRow, list: ConnectionsList): String? =
    networkGroupTitleRes(row, list)?.let(context::getString)

/**
 * One radio at a time behind the Wi-Fi and Bluetooth tabs, with the open tab's groups on the
 * section rail. Forget, Join, Pair and Stop belong to the focused row and fade in with its focus
 * fill.
 */
@Composable
fun NetworkSection(viewModel: SettingsViewModel) {
    val controller = viewModel.networkConnections
    val input = viewModel.networkConnectionsInput
    val state by controller.state.collectAsState()
    QuickConnectionsEffects(controller = controller, pageVisible = true)

    val context = LocalContext.current
    val rows = remember(state) { quickConnectionsRows(state) }
    val focusable = remember(rows) { rows.filter { it.focusable } }
    val focusIndex = remember(state) { quickConnectionsFocusIndex(state) }
    val focusedKey = focusable.getOrNull(focusIndex)?.key
    val list = state.list
    val sections = remember(rows, list, context) {
        networkSettingsSections(rows) { groupName(context, it, list) }
    }
    val reduced = reducedMotion()
    val seenArrivals = remember { HashSet<String>() }
    val arrivalKeys = remember(rows) { rows.filter { it.isArrival() }.map { it.key } }
    val arrivals = remember(arrivalKeys) { newArrivals(arrivalKeys, seenArrivals) }

    val touch = LocalSettingsTouchFocus.current
    val rowTouch = remember(touch, focusable, input) {
        touch.copy(onRowTap = { index -> focusable.getOrNull(index)?.let(input::focusRow) })
    }

    CompositionLocalProvider(LocalSettingsTouchFocus provides rowTouch) {
        SectionPaneLayout(
            items = rows,
            sections = sections,
            focusedIndex = focusIndex,
            focusToListIndex = { index -> focusable.getOrNull(index)?.let(rows::indexOf) ?: index },
            itemKey = { it.key },
            isNavItem = { false },
            isHeader = { it.isGroupHeader() },
            onSectionTap = { section -> focusable.getOrNull(section.focusStartIndex)?.let(input::focusRow) },
            modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) { row ->
            val arrivalOrder = arrivals.indexOf(row.key)
            val entrance = if (arrivalOrder >= 0 && !reduced) Modifier.arrivalEntrance(arrivalOrder) else Modifier
            Box(
                modifier = entrance.successTint(active = row.key == state.justAddedKey && row.isKept())
            ) {
                NetworkRow(
                    row = row,
                    state = state,
                    isFocused = row.key == focusedKey,
                    input = input,
                    forgetHold = controller.forgetHold
                )
            }
        }
    }

    QuickConnectionsModals(state = state, controller = controller)
}

@Composable
private fun NetworkRow(
    row: QuickConnectionsRow,
    state: QuickConnectionsState,
    isFocused: Boolean,
    input: QuickConnectionsInputHandler,
    forgetHold: GamepadHoldTracker
) {
    val onClick: () -> Unit = { input.tapRow(row) }
    val action = quickConnectionsRowAction(state, row)
    val actionSlot: (@Composable () -> Unit)? = if (action == null) {
        null
    } else {
        { RowActionSlot(row, action, isFocused, input, forgetHold) {} }
    }
    when (row) {
        QuickConnectionsRow.ListTabs -> NetworkTabs(list = state.list, isFocused = isFocused, input = input)
        QuickConnectionsRow.SavedHeader, QuickConnectionsRow.NearbyHeader,
        QuickConnectionsRow.PairedHeader, QuickConnectionsRow.FoundHeader -> GroupTitle(row, state.list)
        QuickConnectionsRow.WifiToggle -> SwitchPreference(
            title = stringResource(R.string.settings_network_wifi_title),
            isEnabled = state.wifiEnabled,
            isFocused = isFocused,
            onToggle = { onClick() },
            icon = wifiToggleIcon(state),
            subtitle = wifiSubtitle(state)
        )
        QuickConnectionsRow.BluetoothToggle -> SwitchPreference(
            title = stringResource(R.string.settings_network_bluetooth_title),
            isEnabled = state.bluetoothEnabled,
            isFocused = isFocused,
            onToggle = { onClick() },
            icon = if (state.bluetoothEnabled) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
            subtitle = bluetoothSubtitle(state)
        )
        QuickConnectionsRow.WifiOff -> NetworkNote(R.string.settings_network_wifi_off_hint)
        QuickConnectionsRow.BluetoothOff -> NetworkNote(R.string.settings_network_bluetooth_off_hint)
        QuickConnectionsRow.WifiPermission -> PermissionRow(
            denied = state.wifiPermission == RadioPermission.DENIED,
            askRes = R.string.settings_network_wifi_permission,
            allowRes = R.string.settings_network_wifi_permission_allow,
            deniedRes = R.string.settings_network_wifi_permission_denied,
            settingsRes = R.string.settings_network_wifi_permission_settings,
            isFocused = isFocused,
            onClick = onClick
        )
        QuickConnectionsRow.BluetoothPermission -> PermissionRow(
            denied = state.bluetoothPermission == RadioPermission.DENIED,
            askRes = R.string.settings_network_bluetooth_permission,
            allowRes = R.string.settings_network_bluetooth_permission_allow,
            deniedRes = R.string.settings_network_bluetooth_permission_denied,
            settingsRes = R.string.settings_network_bluetooth_permission_settings,
            isFocused = isFocused,
            onClick = onClick
        )
        is QuickConnectionsRow.ConnectedNetwork -> ActionPreference(
            title = row.network.ssid.orEmpty(),
            subtitle = stringResource(R.string.settings_network_saved_connected),
            isFocused = isFocused,
            icon = signalIcon(row.network.signalLevel),
            onClick = onClick
        )
        is QuickConnectionsRow.Saved -> ActionPreference(
            title = row.network.ssid,
            subtitle = when {
                row.connected -> stringResource(R.string.settings_network_saved_connected)
                row.inRange == false -> stringResource(R.string.settings_network_saved_out_of_range)
                else -> null
            },
            isFocused = isFocused,
            icon = if (row.connected) signalIcon(state.current?.signalLevel ?: 0) else Icons.Default.Wifi,
            trailingContent = actionSlot,
            onClick = onClick
        )
        QuickConnectionsRow.ScanNetworks -> ActionPreference(
            title = stringResource(
                if (state.scanning) R.string.settings_network_scan_running else R.string.settings_network_scan_again
            ),
            subtitle = state.lastScanAt?.let { at ->
                stringResource(
                    R.string.settings_network_scan_updated,
                    formatRelativeTimeVerbose(LocalContext.current, Instant.ofEpochMilli(at))
                )
            },
            isFocused = isFocused,
            icon = if (state.scanning) Icons.Default.Wifi else Icons.Default.Refresh,
            leading = { icon -> SearchPulseIcon(active = state.scanning) { icon() } },
            onClick = onClick
        )
        QuickConnectionsRow.NearbyPlaceholder -> NetworkNote(R.string.settings_network_no_networks)
        is QuickConnectionsRow.Nearby -> {
            val ssid = row.network.ssid
            val joining = state.joiningSsid == ssid
            ActionPreference(
                title = ssid,
                subtitle = stringResource(
                    when {
                        joining -> R.string.settings_network_nearby_joining
                        state.failedSsid == ssid -> R.string.settings_network_nearby_failed
                        row.network.secured -> R.string.settings_network_nearby_secured
                        else -> R.string.settings_network_nearby_open
                    }
                ),
                isFocused = isFocused,
                icon = signalIcon(row.network.signalLevel),
                leading = { icon -> ArrivalIcon(working = joining, landed = state.justAddedKey == row.key, icon = icon) },
                trailingContent = actionSlot,
                onClick = onClick
            )
        }
        QuickConnectionsRow.PairedPlaceholder -> NetworkNote(R.string.settings_network_no_paired)
        is QuickConnectionsRow.Paired -> {
            val battery = row.device.batteryPercent.takeIf { row.device.connected }
                ?.let { stringResource(R.string.settings_network_battery, it) }
            val pairedSlot: (@Composable () -> Unit)? = if (action == null) {
                null
            } else {
                {
                    RowActionSlot(row, action, isFocused, input, forgetHold) {
                        battery?.let { BatteryText(it, isFocused) }
                    }
                }
            }
            ActionPreference(
                title = row.device.name,
                subtitle = stringResource(
                    if (row.device.connected) R.string.settings_network_device_connected
                    else R.string.settings_network_device_not_connected
                ),
                isFocused = isFocused,
                icon = row.device.kind.icon,
                trailingText = battery?.takeIf { action == null },
                trailingContent = pairedSlot,
                onClick = onClick
            )
        }
        QuickConnectionsRow.PairController -> ActionPreference(
            title = stringResource(
                if (state.discovering) R.string.settings_network_pair_searching
                else R.string.settings_network_pair_controller
            ),
            subtitle = null,
            isFocused = isFocused,
            icon = if (state.discovering) Icons.Default.Bluetooth else Icons.Default.Add,
            iconTint = LocalArgosyTheme.current.focusAccent.takeUnless { state.discovering },
            leading = { icon -> SearchPulseIcon(active = state.discovering) { icon() } },
            trailingContent = actionSlot,
            onClick = onClick
        )
        QuickConnectionsRow.ForgetDeviceHint -> NetworkNote(R.string.settings_network_forget_device_hint)
        QuickConnectionsRow.FoundPlaceholder -> NetworkNote(
            if (state.discovering) R.string.settings_network_pair_searching_hint
            else R.string.settings_network_no_controllers
        )
        is QuickConnectionsRow.NearbyController -> {
            val address = row.device.address
            val pairing = state.pairingAddress == address
            ActionPreference(
                title = row.device.name,
                subtitle = when {
                    pairing -> stringResource(R.string.settings_network_found_pairing)
                    state.pairFailedAddress == address -> stringResource(R.string.settings_network_found_pair_failed)
                    else -> null
                },
                isFocused = isFocused,
                icon = Icons.Default.SportsEsports,
                leading = { icon -> ArrivalIcon(working = pairing, landed = state.justAddedKey == row.key, icon = icon) },
                trailingContent = actionSlot,
                onClick = onClick
            )
        }
    }
}

@Composable
private fun NetworkTabs(list: ConnectionsList, isFocused: Boolean, input: QuickConnectionsInputHandler) {
    Box(modifier = preferenceModifier(isFocused)) {
        SegmentedControl(
            options = listOf(
                stringResource(R.string.settings_network_tab_wifi),
                stringResource(R.string.settings_network_tab_bluetooth)
            ),
            selectedIndex = ConnectionsList.entries.indexOf(list),
            onSelect = { input.tapList(ConnectionsList.entries[it]) },
            focused = isFocused,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun RowActionSlot(
    row: QuickConnectionsRow,
    action: ConnectionsRowAction,
    isFocused: Boolean,
    input: QuickConnectionsInputHandler,
    forgetHold: GamepadHoldTracker,
    rest: @Composable () -> Unit
) {
    FocusedActionSlot(
        shown = isFocused,
        animationSpec = Motion.focusSpring,
        rest = rest
    ) {
        val forgetRes = forgetLabelOf(action)
        val pressRes = pressLabelOf(action)
        when {
            forgetRes != null -> ForgetHoldButton(
                label = stringResource(forgetRes),
                tracker = forgetHold.takeIf { isFocused },
                onHoldStart = { input.focusRow(row) },
                onConfirmed = { input.forget(row) }
            )
            pressRes != null -> PreferenceTrailingButton(
                label = stringResource(pressRes),
                isFocused = true,
                onClick = { input.tapRow(row) }
            )
        }
    }
}

@Composable
private fun BatteryText(text: String, isFocused: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = preferenceSecondaryColor(isFocused)
    )
}

@StringRes
private fun pressLabelOf(action: ConnectionsRowAction): Int? = when (action) {
    ConnectionsRowAction.JOIN -> R.string.settings_network_nearby_join
    ConnectionsRowAction.JOIN_OUTSIDE -> R.string.settings_network_nearby_join_outside
    ConnectionsRowAction.RETRY_JOIN -> R.string.settings_network_nearby_retry
    ConnectionsRowAction.PAIR -> R.string.settings_network_found_pair
    ConnectionsRowAction.RETRY_PAIR -> R.string.settings_network_found_retry
    ConnectionsRowAction.STOP_SEARCH -> R.string.settings_network_pair_stop
    ConnectionsRowAction.FORGET_NETWORK, ConnectionsRowAction.FORGET_DEVICE -> null
}

@StringRes
private fun forgetLabelOf(action: ConnectionsRowAction): Int? = when (action) {
    ConnectionsRowAction.FORGET_NETWORK -> R.string.settings_network_forget
    ConnectionsRowAction.FORGET_DEVICE -> R.string.settings_network_forget_device
    else -> null
}

@Composable
private fun GroupTitle(row: QuickConnectionsRow, list: ConnectionsList) {
    val titleRes = networkGroupTitleRes(row, list) ?: return
    Column {
        Spacer(modifier = Modifier.height(Dimens.spacingSm))
        SectionHeader(stringResource(titleRes))
    }
}

@Composable
private fun PermissionRow(
    denied: Boolean,
    @StringRes askRes: Int,
    @StringRes allowRes: Int,
    @StringRes deniedRes: Int,
    @StringRes settingsRes: Int,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    ActionPreference(
        title = stringResource(if (denied) settingsRes else allowRes),
        subtitle = stringResource(if (denied) deniedRes else askRes),
        isFocused = isFocused,
        icon = Icons.Default.Security,
        onClick = onClick
    )
}

@Composable
private fun NetworkNote(@StringRes textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
    )
}

@Composable
private fun wifiSubtitle(state: QuickConnectionsState): String? = when (val status = wifiStatusOf(state)) {
    null -> null
    WifiStatus.Standard -> stringResource(R.string.settings_network_wifi_standard)
    WifiStatus.NotConnected -> stringResource(R.string.settings_network_wifi_not_connected)
    WifiStatus.Connected -> stringResource(R.string.settings_network_wifi_connected)
    is WifiStatus.Network -> status.band?.let { band ->
        stringResource(R.string.settings_network_wifi_status, status.ssid, stringResource(band.settingsLabelRes))
    } ?: status.ssid
}

@Composable
private fun bluetoothSubtitle(state: QuickConnectionsState): String? = when (val status = bluetoothStatusOf(state)) {
    null -> null
    BluetoothStatus.Standard -> stringResource(R.string.settings_network_bluetooth_standard)
    BluetoothStatus.On -> stringResource(R.string.settings_network_bluetooth_on)
    BluetoothStatus.NoneConnected -> stringResource(R.string.settings_network_bluetooth_status_none)
    is BluetoothStatus.OneConnected -> stringResource(R.string.settings_network_bluetooth_status_one, status.name)
    is BluetoothStatus.ManyConnected ->
        pluralStringResource(R.plurals.settings_network_bluetooth_status_many, status.count, status.count)
}

@get:StringRes
private val WifiBand.settingsLabelRes: Int
    get() = when (this) {
        WifiBand.GHZ_2_4 -> R.string.settings_network_band_2_4
        WifiBand.GHZ_5 -> R.string.settings_network_band_5
        WifiBand.GHZ_6 -> R.string.settings_network_band_6
    }
