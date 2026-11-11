package com.nendo.argosy.ui.components.connections

import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkWifi1Bar
import androidx.compose.material.icons.filled.NetworkWifi2Bar
import androidx.compose.material.icons.filled.NetworkWifi3Bar
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.nendo.argosy.R
import com.nendo.argosy.hardware.BluetoothDeviceKind
import com.nendo.argosy.hardware.WifiBand
import com.nendo.argosy.ui.components.GamepadHoldTracker
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.QuickDetailRow
import com.nendo.argosy.ui.components.QuickNoticeRow
import com.nendo.argosy.ui.components.QuickRowFrame
import com.nendo.argosy.ui.components.QuickSectionHeader
import com.nendo.argosy.ui.components.QuickToggleRow
import com.nendo.argosy.ui.components.QuickValueText
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.primitives.SegmentedControl
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.LocalMotionTier
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.generated.MotionTokens

/**
 * The Connections page of the quick panel. Device and network actions show on the focused row
 * only; [forgetHold] fills the Forget button while the context button stays held.
 */
@Composable
fun QuickSettingsConnectionsPage(
    state: QuickConnectionsState,
    forgetHold: GamepadHoldTracker,
    onRowClick: (QuickConnectionsRow) -> Unit,
    onShowList: (ConnectionsList) -> Unit,
    onFocusRow: (QuickConnectionsRow) -> Unit,
    onForget: (QuickConnectionsRow) -> Unit
) {
    val rows = remember(state) { quickConnectionsRows(state) }
    val focusedKey = remember(state) { quickConnectionsFocusedRow(state)?.key }
    val listState = rememberLazyListState()
    val reduced = reducedMotion()
    val seenArrivals = remember { HashSet<String>() }
    val arrivalKeys = rows.filter { it.isArrival() }.map { it.key }
    val arrivals = remember(arrivalKeys) { newArrivals(arrivalKeys, seenArrivals) }
    val actions = remember(forgetHold, onRowClick, onShowList, onFocusRow, onForget) {
        RowActions(forgetHold, onRowClick, onShowList, onFocusRow, onForget)
    }

    val currentRows by rememberUpdatedState(rows)
    LaunchedEffect(focusedKey) {
        val listIndex = currentRows.indexOfFirst { it.key == focusedKey }
        if (listIndex >= 0) listState.animateScrollToItemCentered(listIndex)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        items(rows, key = { it.key }) { row ->
            val arrivalOrder = arrivals.indexOf(row.key)
            val fadeMs = if (reduced) MotionTokens.Tween.fastMs else MotionTokens.Tween.contentMs
            val entrance = if (arrivalOrder >= 0 && !reduced) Modifier.arrivalEntrance(arrivalOrder) else Modifier
            ConnectionsRow(
                row = row,
                state = state,
                isFocused = row.key == focusedKey,
                actions = actions,
                modifier = Modifier
                    .animateItem(
                        fadeInSpec = null,
                        placementSpec = if (reduced) null else tween(MotionTokens.Tween.pageMs, easing = Motion.argosyEase),
                        fadeOutSpec = tween(fadeMs)
                    )
                    .then(entrance)
                    .successTint(active = row.key == state.justAddedKey && row.isKept())
            )
        }
    }
}

private class RowActions(
    val forgetHold: GamepadHoldTracker,
    val onRowClick: (QuickConnectionsRow) -> Unit,
    val onShowList: (ConnectionsList) -> Unit,
    val onFocusRow: (QuickConnectionsRow) -> Unit,
    val onForget: (QuickConnectionsRow) -> Unit
)

internal fun QuickConnectionsRow.isArrival(): Boolean =
    this is QuickConnectionsRow.Nearby || this is QuickConnectionsRow.NearbyController

internal fun QuickConnectionsRow.isKept(): Boolean =
    this is QuickConnectionsRow.Paired || this is QuickConnectionsRow.Saved || this is QuickConnectionsRow.ConnectedNetwork

internal fun newArrivals(present: List<String>, seen: MutableSet<String>): List<String> {
    seen.retainAll(present.toSet())
    val fresh = present.filter { it !in seen }
    seen.addAll(fresh)
    return fresh
}

@Composable
private fun ConnectionsRow(
    row: QuickConnectionsRow,
    state: QuickConnectionsState,
    isFocused: Boolean,
    actions: RowActions,
    modifier: Modifier
) {
    Column(modifier = modifier) {
        when (row) {
            QuickConnectionsRow.ListTabs -> QuickRowFrame(isFocused = isFocused, onClick = null) {
                SegmentedControl(
                    options = listOf(
                        stringResource(R.string.ui_quick_connections_tab_wifi),
                        stringResource(R.string.ui_quick_connections_tab_bluetooth)
                    ),
                    selectedIndex = ConnectionsList.entries.indexOf(state.list),
                    onSelect = { actions.onShowList(ConnectionsList.entries[it]) },
                    focused = isFocused,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            QuickConnectionsRow.WifiToggle -> QuickToggleRow(
                icon = wifiToggleIcon(state),
                label = stringResource(R.string.ui_quick_settings_wifi),
                checked = state.wifiEnabled,
                isFocused = isFocused,
                onFocus = {},
                onToggle = { actions.onRowClick(row) },
                description = wifiDescription(state)
            )
            QuickConnectionsRow.BluetoothToggle -> QuickToggleRow(
                icon = if (state.bluetoothEnabled) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                label = stringResource(R.string.ui_quick_settings_bluetooth),
                checked = state.bluetoothEnabled,
                isFocused = isFocused,
                onFocus = {},
                onToggle = { actions.onRowClick(row) },
                description = bluetoothDescription(state)
            )
            QuickConnectionsRow.WifiOff -> ConnectionsEmptyState(
                icon = Icons.Default.WifiOff,
                titleRes = R.string.ui_quick_connections_wifi_off,
                hintRes = R.string.ui_quick_connections_wifi_off_hint
            )
            QuickConnectionsRow.BluetoothOff -> ConnectionsEmptyState(
                icon = Icons.Default.BluetoothDisabled,
                titleRes = R.string.ui_quick_connections_bluetooth_off,
                hintRes = R.string.ui_quick_connections_bluetooth_off_hint
            )
            QuickConnectionsRow.WifiPermission -> PermissionNotice(
                permission = state.wifiPermission,
                askRes = R.string.ui_quick_connections_wifi_permission,
                allowRes = R.string.ui_quick_connections_wifi_permission_allow,
                deniedRes = R.string.ui_quick_connections_wifi_permission_denied,
                settingsRes = R.string.ui_quick_connections_wifi_permission_settings,
                isFocused = isFocused,
                onAction = { actions.onRowClick(row) }
            )
            QuickConnectionsRow.BluetoothPermission -> PermissionNotice(
                permission = state.bluetoothPermission,
                askRes = R.string.ui_quick_connections_bluetooth_permission,
                allowRes = R.string.ui_quick_connections_bluetooth_permission_allow,
                deniedRes = R.string.ui_quick_connections_bluetooth_permission_denied,
                settingsRes = R.string.ui_quick_connections_bluetooth_permission_settings,
                isFocused = isFocused,
                onAction = { actions.onRowClick(row) }
            )
            QuickConnectionsRow.SavedHeader -> GroupHeader(R.string.ui_quick_connections_saved)
            QuickConnectionsRow.NearbyHeader -> GroupHeader(R.string.ui_quick_connections_nearby)
            QuickConnectionsRow.PairedHeader -> GroupHeader(R.string.ui_quick_connections_paired)
            QuickConnectionsRow.FoundHeader -> GroupHeader(R.string.ui_quick_connections_nearby_controllers)
            QuickConnectionsRow.NearbyPlaceholder -> PlaceholderText(R.string.ui_quick_connections_no_networks)
            QuickConnectionsRow.PairedPlaceholder -> PlaceholderText(R.string.ui_quick_connections_no_paired)
            QuickConnectionsRow.ForgetDeviceHint -> PlaceholderText(R.string.ui_quick_connections_forget_device_hint)
            QuickConnectionsRow.FoundPlaceholder -> PlaceholderText(
                if (state.discovering) R.string.ui_quick_connections_controllers_searching
                else R.string.ui_quick_connections_no_controllers
            )
            is QuickConnectionsRow.ConnectedNetwork -> QuickDetailRow(
                icon = signalIcon(row.network.signalLevel),
                label = row.network.ssid.orEmpty(),
                isFocused = isFocused,
                onClick = { actions.onRowClick(row) },
                valueText = stringResource(R.string.ui_quick_connections_saved_connected),
                labelWraps = true
            )
            is QuickConnectionsRow.Saved -> SavedRow(row, state, isFocused, actions)
            QuickConnectionsRow.ScanNetworks -> QuickDetailRow(
                icon = if (state.scanning) Icons.Default.Wifi else Icons.Default.Refresh,
                label = stringResource(
                    if (state.scanning) R.string.ui_quick_connections_scan_running
                    else R.string.ui_quick_connections_scan_again
                ),
                isFocused = isFocused,
                onClick = { actions.onRowClick(row) },
                leading = { icon -> SearchPulseIcon(active = state.scanning) { icon() } }
            )
            is QuickConnectionsRow.Nearby -> NearbyRow(row, state, isFocused, actions)
            is QuickConnectionsRow.Paired -> PairedRow(row, state, isFocused, actions)
            QuickConnectionsRow.PairController -> QuickDetailRow(
                icon = if (state.discovering) Icons.Default.Bluetooth else Icons.Default.Add,
                label = stringResource(
                    if (state.discovering) R.string.ui_quick_connections_searching_controllers
                    else R.string.ui_quick_connections_pair_controller
                ),
                isFocused = isFocused,
                onClick = { actions.onRowClick(row) },
                labelWraps = true,
                leading = { icon ->
                    if (state.discovering) {
                        SearchPulseIcon(active = true) { icon() }
                    } else {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = LocalArgosyTheme.current.focusAccent,
                            modifier = Modifier.size(Dimens.iconMd)
                        )
                    }
                }
            ) {
                FocusedAction(
                    row = row,
                    focused = isFocused,
                    action = focusedRowActionOf(state, row),
                    actions = actions
                ) {}
            }
            is QuickConnectionsRow.NearbyController -> FoundControllerRow(row, state, isFocused, actions)
        }
    }
}

private sealed interface FocusedRowAction {
    data class Press(@StringRes val labelRes: Int, val outside: Boolean = false) : FocusedRowAction
    data class Forget(@StringRes val labelRes: Int) : FocusedRowAction
}

private fun focusedRowActionOf(state: QuickConnectionsState, row: QuickConnectionsRow): FocusedRowAction? =
    when (quickConnectionsRowAction(state, row)) {
        null -> null
        ConnectionsRowAction.JOIN -> FocusedRowAction.Press(R.string.ui_quick_connections_nearby_join)
        ConnectionsRowAction.JOIN_OUTSIDE ->
            FocusedRowAction.Press(R.string.ui_quick_connections_nearby_join_outside, outside = true)
        ConnectionsRowAction.RETRY_JOIN -> FocusedRowAction.Press(R.string.ui_quick_connections_retry_join)
        ConnectionsRowAction.PAIR -> FocusedRowAction.Press(R.string.ui_quick_connections_found_pair)
        ConnectionsRowAction.RETRY_PAIR -> FocusedRowAction.Press(R.string.ui_quick_connections_retry_pair)
        ConnectionsRowAction.STOP_SEARCH -> FocusedRowAction.Press(R.string.ui_quick_connections_pair_stop)
        ConnectionsRowAction.FORGET_NETWORK -> FocusedRowAction.Forget(R.string.ui_quick_connections_forget)
        ConnectionsRowAction.FORGET_DEVICE -> FocusedRowAction.Forget(R.string.ui_quick_connections_forget_device)
    }

@Composable
private fun SavedRow(row: QuickConnectionsRow.Saved, state: QuickConnectionsState, isFocused: Boolean, actions: RowActions) {
    val restValue = when {
        row.connected -> stringResource(R.string.ui_quick_connections_saved_connected)
        row.inRange == false -> stringResource(R.string.ui_quick_connections_saved_out_of_range)
        else -> null
    }
    QuickDetailRow(
        icon = if (row.connected) signalIcon(state.current?.signalLevel ?: 0) else Icons.Default.Wifi,
        label = row.network.ssid,
        isFocused = isFocused,
        onClick = { actions.onRowClick(row) },
        labelWraps = true
    ) {
        FocusedAction(
            row = row,
            focused = isFocused,
            action = focusedRowActionOf(state, row),
            actions = actions
        ) {
            restValue?.let { QuickValueText(it, isFocused) }
        }
    }
}

@Composable
private fun PairedRow(row: QuickConnectionsRow.Paired, state: QuickConnectionsState, isFocused: Boolean, actions: RowActions) {
    val restValue = pairedValue(row.device)
    QuickDetailRow(
        icon = row.device.kind.icon,
        label = row.device.name,
        isFocused = isFocused,
        onClick = { actions.onRowClick(row) },
        labelWraps = true
    ) {
        FocusedAction(
            row = row,
            focused = isFocused,
            action = focusedRowActionOf(state, row),
            actions = actions
        ) {
            QuickValueText(restValue, isFocused)
        }
    }
}

@Composable
private fun NearbyRow(row: QuickConnectionsRow.Nearby, state: QuickConnectionsState, isFocused: Boolean, actions: RowActions) {
    val ssid = row.network.ssid
    val joining = state.joiningSsid == ssid
    val failed = state.failedSsid == ssid
    val landed = state.justAddedKey == row.key
    QuickDetailRow(
        icon = signalIcon(row.network.signalLevel),
        label = ssid,
        isFocused = isFocused,
        onClick = { actions.onRowClick(row) },
        labelWraps = true,
        leading = { icon -> ArrivalIcon(working = joining, landed = landed, icon = icon) }
    ) {
        FocusedAction(row = row, focused = isFocused, action = focusedRowActionOf(state, row), actions = actions) {
            when {
                joining -> QuickValueText(stringResource(R.string.ui_quick_connections_row_joining), isFocused)
                failed -> QuickValueText(stringResource(R.string.ui_quick_connections_row_failed), isFocused)
                row.network.secured -> Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = stringResource(R.string.ui_quick_connections_secured),
                    tint = LocalArgosyTheme.current.textDim,
                    modifier = Modifier.size(Dimens.iconSm)
                )
            }
        }
    }
}

@Composable
private fun FoundControllerRow(
    row: QuickConnectionsRow.NearbyController,
    state: QuickConnectionsState,
    isFocused: Boolean,
    actions: RowActions
) {
    val address = row.device.address
    val pairing = state.pairingAddress == address
    val failed = state.pairFailedAddress == address
    val landed = state.justAddedKey == row.key
    QuickDetailRow(
        icon = Icons.Default.SportsEsports,
        label = row.device.name,
        isFocused = isFocused,
        onClick = { actions.onRowClick(row) },
        labelWraps = true,
        leading = { icon -> ArrivalIcon(working = pairing, landed = landed, icon = icon) }
    ) {
        FocusedAction(row = row, focused = isFocused, action = focusedRowActionOf(state, row), actions = actions) {
            when {
                pairing -> QuickValueText(stringResource(R.string.ui_quick_connections_row_pairing), isFocused)
                failed -> QuickValueText(stringResource(R.string.ui_quick_connections_row_pair_failed), isFocused)
            }
        }
    }
}

@Composable
internal fun ArrivalIcon(working: Boolean, landed: Boolean, icon: @Composable () -> Unit) {
    Crossfade(
        targetState = landed,
        animationSpec = if (reducedMotion()) snap() else tween(Motion.durationMicro, easing = Motion.argosyEase),
        label = "connections-landed"
    ) { showCheck ->
        if (showCheck) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = LocalLauncherTheme.current.semanticColors.success,
                modifier = Modifier.size(Dimens.iconMd)
            )
        } else {
            ProgressRingIcon(active = working) { icon() }
        }
    }
}

@Composable
private fun FocusedAction(
    row: QuickConnectionsRow,
    focused: Boolean,
    action: FocusedRowAction?,
    actions: RowActions,
    rest: @Composable () -> Unit
) {
    FocusedActionSlot(
        shown = focused && action != null,
        animationSpec = Motion.tierFocusSpring(LocalMotionTier.current),
        rest = rest
    ) {
        when (action) {
            null -> Unit
            is FocusedRowAction.Forget -> ForgetHoldButton(
                label = stringResource(action.labelRes),
                tracker = actions.forgetHold.takeIf { focused },
                onHoldStart = { actions.onFocusRow(row) },
                onConfirmed = { actions.onForget(row) }
            )
            is FocusedRowAction.Press -> if (action.outside) {
                OutsideLaunchButton(label = stringResource(action.labelRes), onClick = { actions.onRowClick(row) })
            } else {
                ActionButton(
                    label = stringResource(action.labelRes),
                    onClick = { actions.onRowClick(row) },
                    focused = true,
                    glyph = InputButton.A
                )
            }
        }
    }
}

@Composable
private fun GroupHeader(@StringRes titleRes: Int) {
    Column {
        Spacer(modifier = Modifier.height(Dimens.spacingSm))
        QuickSectionHeader(title = stringResource(titleRes))
    }
}

@Composable
private fun PermissionNotice(
    permission: RadioPermission,
    @StringRes askRes: Int,
    @StringRes allowRes: Int,
    @StringRes deniedRes: Int,
    @StringRes settingsRes: Int,
    isFocused: Boolean,
    onAction: () -> Unit
) {
    val denied = permission == RadioPermission.DENIED
    QuickNoticeRow(
        message = stringResource(if (denied) deniedRes else askRes),
        actionLabel = stringResource(if (denied) settingsRes else allowRes),
        isFocused = isFocused,
        onAction = onAction
    )
}

@Composable
private fun ConnectionsEmptyState(icon: ImageVector, @StringRes titleRes: Int, @StringRes hintRes: Int) {
    val theme = LocalArgosyTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingXxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = theme.textMute,
            modifier = Modifier
                .size(Dimens.iconXl)
                .padding(bottom = Dimens.spacingSm)
        )
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            color = theme.textPrimary,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(hintRes),
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textDim,
            textAlign = TextAlign.Center
        )
    }
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
private fun wifiDescription(state: QuickConnectionsState): String? = when (val status = wifiStatusOf(state)) {
    null -> null
    WifiStatus.Standard -> stringResource(R.string.ui_quick_connections_wifi_toggle_standard)
    WifiStatus.NotConnected -> stringResource(R.string.ui_quick_settings_wifi_not_connected)
    WifiStatus.Connected -> stringResource(R.string.ui_quick_settings_wifi_connected)
    is WifiStatus.Network -> status.band?.let { band ->
        stringResource(R.string.ui_quick_connections_wifi_status_network, status.ssid, stringResource(band.labelRes))
    } ?: status.ssid
}

@Composable
private fun bluetoothDescription(state: QuickConnectionsState): String? = when (val status = bluetoothStatusOf(state)) {
    null -> null
    BluetoothStatus.Standard -> stringResource(R.string.ui_quick_connections_bluetooth_toggle_standard)
    BluetoothStatus.On -> stringResource(R.string.ui_quick_settings_radio_on)
    BluetoothStatus.NoneConnected -> stringResource(R.string.ui_quick_connections_bluetooth_status_none)
    is BluetoothStatus.OneConnected -> stringResource(R.string.ui_quick_connections_bluetooth_status_one, status.name)
    is BluetoothStatus.ManyConnected ->
        pluralStringResource(R.plurals.ui_quick_connections_bluetooth_status_many, status.count, status.count)
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

internal fun wifiToggleIcon(state: QuickConnectionsState): ImageVector {
    val current = state.current
    return when {
        !state.wifiEnabled -> Icons.Default.WifiOff
        current != null -> signalIcon(current.signalLevel)
        else -> Icons.Default.Wifi
    }
}

internal fun signalIcon(level: Int): ImageVector = when (level) {
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

internal val BluetoothDeviceKind.icon: ImageVector
    get() = when (this) {
        BluetoothDeviceKind.CONTROLLER -> Icons.Default.SportsEsports
        BluetoothDeviceKind.AUDIO -> Icons.Default.Headphones
        BluetoothDeviceKind.OTHER -> Icons.Default.Bluetooth
    }
