package com.nendo.argosy.ui.screens.settings.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.R
import com.nendo.argosy.data.model.DisplaySnapshot
import com.nendo.argosy.ui.common.labelRes
import com.nendo.argosy.ui.common.liveRoleLabelRes
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.preferenceContentColor
import com.nendo.argosy.ui.components.preferenceModifier
import com.nendo.argosy.ui.components.preferenceSecondaryColor
import com.nendo.argosy.ui.dualscreen.PresentOnCompanion
import com.nendo.argosy.ui.dualscreen.PresentationSlot
import com.nendo.argosy.ui.dualscreen.SlotOwner
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.primitives.ArgosyConfirmModalHost
import com.nendo.argosy.ui.primitives.ModalScaffold
import com.nendo.argosy.ui.screens.settings.delegates.AboutToolsDelegate
import com.nendo.argosy.ui.screens.settings.delegates.ControllerTestPad
import com.nendo.argosy.ui.screens.settings.delegates.DisplayInfoState
import com.nendo.argosy.ui.screens.setup.components.ButtonCheckKeyCapture
import com.nendo.argosy.ui.screens.setup.components.ButtonCheckLastPress
import com.nendo.argosy.ui.screens.setup.components.ControllerDiagram
import com.nendo.argosy.ui.screens.setup.components.SetupCompanionBody
import com.nendo.argosy.ui.screens.setup.components.SetupCompanionState
import com.nendo.argosy.ui.screens.setup.components.currentGlyphSwaps
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

private const val CONTROLLER_TEST_SLOT_OWNER = "settings.controllerTest"

/**
 * The modals the About debug tools open: the controller test, display info and the one-time
 * notice reset confirm.
 */
@Composable
fun AboutToolsOverlays(tools: AboutToolsDelegate) {
    val state by tools.state.collectAsState()
    ControllerTestOverlay(open = state.controllerTestOpen, pad = state.controllerTestPad, tools = tools)
    DisplayInfoModal(info = state.displayInfo, tools = tools)
    ArgosyConfirmModalHost(
        visible = state.resetConfirmOpen,
        title = stringResource(R.string.about_tools_reset_confirm_title),
        message = stringResource(R.string.about_tools_reset_confirm_message),
        confirmLabel = stringResource(R.string.about_tools_reset_confirm_action),
        onConfirm = tools::confirmResetNotices,
        onDismiss = tools::cancelResetNotices
    )
}

@Composable
private fun ControllerTestOverlay(open: Boolean, pad: ControllerTestPad?, tools: AboutToolsDelegate) {
    val theme = LocalArgosyTheme.current
    val check by tools.buttonCheck.state.collectAsState()
    ModalInputEffect(active = open, handler = tools.controllerTestInput, onInputLost = tools::closeControllerTest)
    if (open) {
        ButtonCheckKeyCapture(state = check, controller = tools.buttonCheck, onPadDevice = tools::onPadInput)
        val swaps = currentGlyphSwaps()
        PresentOnCompanion(
            owner = SlotOwner(CONTROLLER_TEST_SLOT_OWNER),
            slot = PresentationSlot.Setup(
                SetupCompanionState(
                    title = R.string.about_tools_controller_companion_title,
                    body = SetupCompanionBody.Controller(
                        reading = check.reading,
                        swaps = swaps,
                        swapped = check.swappedButtons,
                        showsLastPress = true,
                        testing = check.testing
                    )
                )
            )
        )
    }
    ModalScaffold(visible = open, onDismiss = tools::closeControllerTest, maxWidth = Dimens.modalWidthXl) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Dimens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
        ) {
            Text(
                text = stringResource(R.string.about_tools_controller_heading),
                style = MaterialTheme.typography.titleLarge,
                color = theme.textPrimary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXl),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                ) {
                    ControllerDiagram(reading = check.reading, swapped = check.swappedButtons)
                    ButtonCheckLastPress(lastPress = check.reading.lastPress, testing = check.testing)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                ) {
                    PadDetails(pad)
                    Text(
                        text = stringResource(R.string.about_tools_controller_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.focusAccent
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        ActionButton(
                            label = stringResource(R.string.about_tools_controller_close),
                            onClick = tools::closeControllerTest
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PadDetails(pad: ControllerTestPad?) {
    val theme = LocalArgosyTheme.current
    if (pad == null) {
        Text(
            text = stringResource(R.string.about_tools_controller_waiting),
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textDim
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
        Text(
            text = pad.name.ifBlank { stringResource(R.string.about_tools_controller_unnamed) },
            style = MaterialTheme.typography.titleMedium,
            color = theme.textPrimary
        )
        DetailLine(stringResource(R.string.about_tools_controller_ids, hexId(pad.vendorId), hexId(pad.productId)))
        DetailLine(stringResource(R.string.about_tools_controller_id, pad.controllerId))
        DetailLine(
            if (pad.seat != null) {
                stringResource(R.string.about_tools_controller_seat, pad.seat + 1)
            } else {
                stringResource(R.string.about_tools_controller_no_seat)
            }
        )
    }
}

@Composable
private fun DetailLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = LocalArgosyTheme.current.textDim,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun DisplayInfoModal(info: DisplayInfoState?, tools: AboutToolsDelegate) {
    val theme = LocalArgosyTheme.current
    ModalInputEffect(active = info != null, handler = tools.displayInfoInput, onInputLost = tools::closeDisplayInfo)
    var lastShown by remember { mutableStateOf<DisplayInfoState?>(null) }
    SideEffect { info?.let { lastShown = it } }
    val listState = rememberLazyListState()
    ModalScaffold(visible = info != null, onDismiss = tools::closeDisplayInfo, maxWidth = Dimens.modalWidthLg) {
        val shown = info ?: lastShown ?: return@ModalScaffold
        FocusedScroll(listState = listState, focusedIndex = shown.focusIndex)
        Column(
            modifier = Modifier.fillMaxWidth().padding(Dimens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            Text(
                text = stringResource(R.string.about_tools_display_heading),
                style = MaterialTheme.typography.titleLarge,
                color = theme.textPrimary
            )
            when {
                shown.loading -> StatusLine(R.string.about_tools_display_loading)
                shown.displays.isEmpty() -> StatusLine(R.string.about_tools_display_empty)
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
                ) {
                    itemsIndexed(shown.displays, key = { _, display -> display.displayId }) { index, display ->
                        DisplayCard(
                            display = display,
                            isFocused = index == shown.focusIndex,
                            onClick = { tools.focusDisplay(index) }
                        )
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ActionButton(
                    label = stringResource(R.string.about_tools_display_close),
                    onClick = tools::closeDisplayInfo
                )
            }
        }
    }
}

@Composable
private fun StatusLine(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = LocalArgosyTheme.current.textDim
    )
}

@Composable
private fun DisplayCard(display: DisplaySnapshot, isFocused: Boolean, onClick: () -> Unit) {
    Column(
        modifier = preferenceModifier(isFocused, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        Text(
            text = stringResource(
                R.string.about_tools_display_entry_title,
                display.displayId,
                display.name.ifBlank { stringResource(display.kind.labelRes) }
            ),
            style = MaterialTheme.typography.titleMedium,
            color = preferenceContentColor(isFocused)
        )
        CardLine(
            text = display.physicalId?.let { stringResource(R.string.about_tools_display_physical, it) }
                ?: stringResource(R.string.about_tools_display_physical_none),
            isFocused = isFocused
        )
        CardLine(
            text = stringResource(
                R.string.about_tools_display_size,
                display.widthPx, display.heightPx, display.widthDp, display.heightDp
            ),
            isFocused = isFocused
        )
        CardLine(
            text = stringResource(
                R.string.about_tools_display_density,
                display.densityDpi,
                display.refreshRateHz
            ),
            isFocused = isFocused
        )
        CardLine(
            text = stringResource(
                R.string.about_tools_display_kind_role,
                stringResource(display.kind.labelRes),
                stringResource(display.role.liveRoleLabelRes)
            ),
            isFocused = isFocused
        )
    }
}

@Composable
private fun CardLine(text: String, isFocused: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = preferenceSecondaryColor(isFocused),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

private fun hexId(value: Int): String = String.format(java.util.Locale.ROOT, "0x%04X", value)
