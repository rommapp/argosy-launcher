package com.nendo.argosy.ui.screens.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.QrCodeWithOverlay
import com.nendo.argosy.ui.components.QrScannerWithPermission
import com.nendo.argosy.ui.components.preferenceContentColor
import com.nendo.argosy.ui.components.preferenceModifier
import com.nendo.argosy.ui.components.preferenceSecondaryColor
import com.nendo.argosy.ui.screens.settings.ROMM_AUTH_METHOD_PICKER_KEY
import com.nendo.argosy.ui.screens.settings.RomMAddressRole
import com.nendo.argosy.ui.screens.settings.RomMAddressRow
import com.nendo.argosy.ui.screens.settings.RomMAddressVerification
import com.nendo.argosy.ui.screens.settings.RomMAuthMethod
import com.nendo.argosy.ui.screens.settings.ServerState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

/**
 * Focus index of every row the config form can show, derived once from [ServerState] so the
 * form, the confirm router and the left/right handler never disagree. A null index or an
 * empty list is a row the current form does not contain. Signed out, the form is the pairing
 * field followed by the pairing rows; signed in, the address cards and the Add card take the
 * field's place as consecutive indices ahead of the same pairing rows.
 */
internal data class RomMConfigIndices(
    val firstFieldIndex: Int?,
    val addressIndices: List<Int>,
    val addAddressIndex: Int?,
    val authMethodIndex: Int,
    val pairingCodeIndex: Int?,
    val connectIndex: Int,
    val scanIndex: Int?,
    val certificateIndex: Int,
    val cancelIndex: Int
) {
    val maxIndex: Int get() = cancelIndex

    fun addressRowAt(focusIndex: Int): Int? = addressIndices.indexOf(focusIndex).takeIf { it >= 0 }
}

internal fun rommConfigIndices(server: ServerState): RomMConfigIndices {
    val editing = server.rommEditingAddresses
    val pairingCode = server.rommAuthMethod == RomMAuthMethod.PAIRING_CODE
    var next = 0
    val firstField = if (editing) null else next++
    val addresses = if (editing) List(server.rommAddressRows.size) { next++ } else emptyList()
    val add = if (editing && server.rommAddressRows.size < 2) next++ else null
    val authMethod = next++
    val code = if (pairingCode) next++ else null
    val connect = next++
    val scan = if (pairingCode && server.rommHasCamera) next++ else null
    val certificate = next++
    val cancel = next
    return RomMConfigIndices(
        firstFieldIndex = firstField,
        addressIndices = addresses,
        addAddressIndex = add,
        authMethodIndex = authMethod,
        pairingCodeIndex = code,
        connectIndex = connect,
        scanIndex = scan,
        certificateIndex = certificate,
        cancelIndex = cancel
    )
}

internal fun rommConfigMaxIndex(server: ServerState): Int =
    if (server.rommDevicePairing) 0 else rommConfigIndices(server).maxIndex

@Composable
private fun authMethodLabel(method: RomMAuthMethod): String = when (method) {
    RomMAuthMethod.DEVICE -> stringResource(R.string.settings_romm_config_auth_method_device)
    RomMAuthMethod.PAIRING_CODE -> stringResource(R.string.settings_romm_config_auth_method_pairing_code)
}

private fun cycleAuthMethod(current: RomMAuthMethod, direction: Int): RomMAuthMethod {
    val methods = RomMAuthMethod.entries
    return methods[(methods.indexOf(current) + direction).mod(methods.size)]
}

@Composable
fun RomMConfigForm(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    if (uiState.server.rommShowScanner) {
        Box(modifier = Modifier.fillMaxSize()) {
            QrScannerWithPermission(
                onResult = { result ->
                    viewModel.handleRommScanResult(result.origin, result.code)
                },
                onDismiss = { viewModel.dismissRommScanner() }
            )
        }
        return
    }

    if (uiState.server.rommDevicePairing) {
        DevicePairingScreen(uiState, viewModel)
        return
    }

    val server = uiState.server
    val indices = rommConfigIndices(server)
    val inputShape = RoundedCornerShape(Dimens.radiusMd)
    val keyboard = LocalSoftwareKeyboardController.current
    var wasUrlFocused by remember { mutableStateOf(false) }
    val firstFieldFocusRequester = remember { FocusRequester() }
    val pairingCodeFocusRequester = remember { FocusRequester() }

    val authMethod = server.rommAuthMethod
    val isDevice = authMethod == RomMAuthMethod.DEVICE
    val isPairingCode = authMethod == RomMAuthMethod.PAIRING_CODE

    LaunchedEffect(server.rommFocusField) {
        when (server.rommFocusField) {
            null -> return@LaunchedEffect
            indices.firstFieldIndex -> firstFieldFocusRequester.requestFocus()
            indices.pairingCodeIndex -> pairingCodeFocusRequester.requestFocus()
        }
        viewModel.clearRommFocusField()
    }

    Column(
        modifier = Modifier
            .padding(Dimens.spacingMd)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        if (server.rommEditingAddresses) {
            SectionHeader(stringResource(R.string.settings_romm_config_section_addresses))
            server.rommAddressRows.forEachIndexed { rowIndex, row ->
                AddressCard(
                    row = row,
                    isFocused = uiState.focusedIndex == indices.addressIndices[rowIndex],
                    onClick = { viewModel.openRommAddressMenu(rowIndex) }
                )
            }
            if (indices.addAddressIndex != null) {
                AddAddressCard(
                    isFocused = uiState.focusedIndex == indices.addAddressIndex,
                    onClick = { viewModel.addRommAddress() }
                )
            }
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            SectionHeader(stringResource(R.string.settings_romm_config_section_pairing))
        } else {
            OutlinedTextField(
                value = server.rommConfigUrl,
                onValueChange = { viewModel.setRommConfigUrl(it) },
                label = { Text(stringResource(R.string.settings_romm_config_server_url_label)) },
                placeholder = { Text(stringResource(R.string.settings_romm_config_server_url_placeholder)) },
                singleLine = true,
                shape = inputShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(
                    onGo = {
                        if (!server.rommConnecting && server.rommConfigUrl.isNotBlank()) {
                            keyboard?.hide()
                            viewModel.commitRommUrl()
                        }
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(firstFieldFocusRequester)
                    .onFocusChanged { fs ->
                        if (wasUrlFocused && !fs.isFocused && server.rommConfigUrl.isNotBlank()) {
                            viewModel.commitRommUrl()
                        }
                        wasUrlFocused = fs.isFocused
                    }
                    .then(fieldFocusModifier(uiState.focusedIndex == indices.firstFieldIndex, inputShape))
            )
        }

        CyclePreference(
            title = stringResource(R.string.settings_romm_config_auth_method_title),
            value = authMethodLabel(authMethod),
            isFocused = uiState.focusedIndex == indices.authMethodIndex,
            onClick = { viewModel.setRommAuthMethod(cycleAuthMethod(authMethod, 1)) },
            onPrev = { viewModel.setRommAuthMethod(cycleAuthMethod(authMethod, -1)) },
            options = RomMAuthMethod.entries.map { authMethodLabel(it) },
            onSelect = { viewModel.setRommAuthMethod(RomMAuthMethod.entries[it]) },
            pickerRequestToken = if (uiState.enumPickerKey == ROMM_AUTH_METHOD_PICKER_KEY) uiState.enumPickerToken else 0
        )

        when {
            isDevice -> Text(
                text = stringResource(R.string.settings_romm_config_device_instructions),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Dimens.spacingSm)
            )
            isPairingCode -> Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.settings_romm_config_pairing_code_instructions),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Dimens.spacingSm)
                )
                Spacer(modifier = Modifier.height(Dimens.spacingSm))
                PairingCodeInput(
                    code = server.rommConfigPairingCode,
                    onCodeChange = { viewModel.setRommConfigPairingCode(it) },
                    isFocused = uiState.focusedIndex == indices.pairingCodeIndex,
                    focusRequester = pairingCodeFocusRequester
                )
            }
        }

        if (server.rommConfigError != null) {
            Text(
                text = server.rommConfigError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = Dimens.spacingSm)
            )
        }

        Spacer(modifier = Modifier.height(Dimens.spacingSm))

        ActionPreference(
            title = when {
                server.rommConnecting && isDevice -> stringResource(R.string.settings_romm_config_connect_generating_title)
                server.rommConnecting -> stringResource(R.string.settings_romm_config_connect_connecting_title)
                isDevice -> stringResource(R.string.settings_romm_config_connect_pair_title)
                else -> stringResource(R.string.settings_romm_config_connect_title)
            },
            subtitle = if (isDevice) {
                stringResource(R.string.settings_romm_config_connect_device_subtitle)
            } else {
                stringResource(R.string.settings_romm_config_connect_subtitle)
            },
            isFocused = uiState.focusedIndex == indices.connectIndex,
            onClick = { viewModel.connectToRomm() }
        )

        if (indices.scanIndex != null) {
            ActionPreference(
                title = stringResource(R.string.settings_romm_config_scan_title),
                subtitle = stringResource(R.string.settings_romm_config_scan_subtitle),
                isFocused = uiState.focusedIndex == indices.scanIndex,
                onClick = { viewModel.showRommScanner() }
            )
        }

        ActionPreference(
            title = stringResource(R.string.settings_romm_config_certificate_title),
            subtitle = if (server.importedCertCount > 0) {
                pluralStringResource(
                    R.plurals.settings_romm_config_certificate_subtitle_count,
                    server.importedCertCount,
                    server.importedCertCount
                )
            } else {
                stringResource(R.string.settings_romm_config_certificate_subtitle)
            },
            isFocused = uiState.focusedIndex == indices.certificateIndex,
            onClick = { viewModel.requestCertificatePicker() }
        )

        ActionPreference(
            title = stringResource(R.string.settings_romm_config_cancel_title),
            subtitle = stringResource(R.string.settings_romm_config_cancel_subtitle),
            isFocused = uiState.focusedIndex == indices.cancelIndex,
            onClick = { viewModel.cancelRommConfig() }
        )
    }
}

@Composable
private fun addressStatus(row: RomMAddressRow): String = when {
    row.saving -> stringResource(R.string.settings_romm_config_address_status_checking)
    row.inUse -> stringResource(R.string.settings_romm_config_address_status_in_use)
    row.verification == RomMAddressVerification.VERIFIED ->
        stringResource(R.string.settings_romm_config_address_status_verified)
    row.verification == RomMAddressVerification.UNVERIFIED ->
        stringResource(R.string.settings_romm_config_address_status_unverified)
    else -> ""
}

@Composable
private fun AddressCard(row: RomMAddressRow, isFocused: Boolean, onClick: () -> Unit) {
    if (row.stored.isBlank()) {
        AddAddressCard(isFocused = isFocused, onClick = onClick)
        return
    }
    ActionPreference(
        icon = when (row.role) {
            RomMAddressRole.LOCAL -> Icons.Default.Home
            RomMAddressRole.REMOTE -> Icons.Default.Public
        },
        title = row.stored,
        subtitle = addressStatus(row),
        isFocused = isFocused,
        onClick = onClick
    )
}

@Composable
private fun AddAddressCard(isFocused: Boolean, onClick: () -> Unit) {
    val theme = LocalArgosyTheme.current
    val density = LocalDensity.current
    val dashColor = if (isFocused) Color.Transparent else theme.hairlineHigh
    val dash = with(density) { Dimens.spacingXs.toPx() }
    val stroke = with(density) { Dimens.borderThin.toPx() }
    val corner = with(density) { Dimens.radiusControl.toPx() }
    Row(
        modifier = Modifier
            .drawWithContent {
                drawContent()
                drawRoundRect(
                    color = dashColor,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(corner),
                    style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)))
                )
            }
            .then(preferenceModifier(isFocused, onClick = onClick)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else theme.textDim,
            modifier = Modifier.size(Dimens.iconMd)
        )
        Spacer(modifier = Modifier.width(Dimens.spacingMd))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_romm_config_add_address_title),
                style = MaterialTheme.typography.titleMedium,
                color = if (isFocused) preferenceContentColor(true) else theme.textDim
            )
            Text(
                text = stringResource(R.string.settings_romm_config_add_address_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = preferenceSecondaryColor(isFocused)
            )
        }
    }
}

@Composable
private fun fieldFocusModifier(focused: Boolean, shape: RoundedCornerShape): Modifier =
    if (focused) {
        Modifier.background(LocalArgosyTheme.current.focusAccent.copy(alpha = 0.15f), shape)
    } else {
        Modifier
    }

@Composable
private fun DevicePairingScreen(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val server = uiState.server
    Column(
        modifier = Modifier
            .padding(Dimens.spacingMd)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Text(
            text = stringResource(R.string.settings_romm_device_pairing_title),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(R.string.settings_romm_device_pairing_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        server.rommDeviceVerificationUrl?.let { url ->
            QrCodeWithOverlay(data = url)
        }

        server.rommDeviceUserCode?.let { code ->
            Text(
                text = code,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace)
            )
        }

        server.rommDeviceVerificationUrl?.let { url ->
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (server.rommConfigError != null) {
            Text(
                text = server.rommConfigError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(Dimens.spacingSm))

        ActionPreference(
            title = stringResource(R.string.settings_romm_device_pairing_cancel_title),
            subtitle = stringResource(R.string.settings_romm_device_pairing_cancel_subtitle),
            isFocused = true,
            onClick = { viewModel.cancelRommConfig() }
        )
    }
}
