package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.GripReserveMode
import com.nendo.argosy.domain.model.GripAutoController
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.NavigationPreference
import com.nendo.argosy.ui.components.SliderPreference
import com.nendo.argosy.ui.screens.settings.DisplayState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.GripControllerModal
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.GRIP_RESERVE_MAX_PERCENT
import com.nendo.argosy.ui.theme.GRIP_RESERVE_MIN_PERCENT

internal const val GRIP_RESERVE_PERCENT_STEP = 2

internal sealed class ControllerGripItem(
    val key: String,
    val visibleWhen: (DisplayState) -> Boolean = { true }
) {
    data object Mode : ControllerGripItem("gripMode")
    data object Controllers : ControllerGripItem(
        "gripControllers",
        visibleWhen = { it.gripReserveMode == GripReserveMode.AUTO }
    )
    data object ReservedHeight : ControllerGripItem(
        "gripReservePercent",
        visibleWhen = { it.gripReserveMode != GripReserveMode.OFF }
    )

    companion object {
        val ALL: List<ControllerGripItem>
            get() = listOf(Mode, Controllers, ReservedHeight)
    }
}

private val controllerGripLayout = SettingsLayout<ControllerGripItem, DisplayState>(
    allItems = ControllerGripItem.ALL,
    isFocusable = { true },
    visibleWhen = { item, display -> item.visibleWhen(display) },
    sectionOf = { "grip" },
    sectionTitleRes = { null }
)

internal fun controllerGripMaxFocusIndex(display: DisplayState): Int =
    controllerGripLayout.maxFocusIndex(display)

internal fun controllerGripItemAtFocusIndex(index: Int, display: DisplayState): ControllerGripItem? =
    controllerGripLayout.itemAtFocusIndex(index, display)

internal fun controllerGripSections(display: DisplayState) =
    controllerGripLayout.buildSections(display)

internal fun controllerGripFocusIndexOf(item: ControllerGripItem, display: DisplayState): Int =
    controllerGripLayout.focusIndexOf(item, display)

@Composable
internal fun gripAutoControllerSubtitle(controllers: List<GripAutoController>): String =
    when (controllers.size) {
        0 -> stringResource(R.string.settings_grip_controllers_subtitle_empty)
        1 -> controllers.first().name
        else -> pluralStringResource(
            R.plurals.settings_grip_controllers_subtitle_count,
            controllers.size,
            controllers.size
        )
    }

private fun gripModeLabelRes(mode: GripReserveMode): Int = when (mode) {
    GripReserveMode.OFF -> R.string.settings_grip_mode_off
    GripReserveMode.ON -> R.string.settings_grip_mode_on
    GripReserveMode.AUTO -> R.string.settings_grip_mode_auto
}

@Composable
fun ControllerGripSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val display = uiState.display
    val context = LocalContext.current

    val visibleItems = remember(display) { controllerGripLayout.visibleItems(display) }
    val sections = remember(display) { controllerGripLayout.buildSections(display) }

    fun isFocused(item: ControllerGripItem): Boolean =
        uiState.focusedIndex == controllerGripLayout.focusIndexOf(item, display)

    fun pickerToken(item: ControllerGripItem): Int =
        if (uiState.enumPickerKey == item.key) uiState.enumPickerToken else 0

    SectionPaneLayout(
        items = visibleItems,
        sections = sections,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { controllerGripLayout.focusToListIndex(it, display) },
        itemKey = { it.key },
        isNavItem = { false },
        isHeader = { false },
        onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) { item ->
        when (item) {
            ControllerGripItem.Mode -> CyclePreference(
                title = stringResource(R.string.settings_grip_mode_title),
                value = stringResource(gripModeLabelRes(display.gripReserveMode)),
                subtitle = when (display.gripReserveMode) {
                    GripReserveMode.OFF -> stringResource(R.string.settings_grip_mode_subtitle_off)
                    GripReserveMode.ON -> stringResource(R.string.settings_grip_mode_subtitle_on)
                    GripReserveMode.AUTO -> stringResource(R.string.settings_grip_mode_subtitle_auto)
                },
                isFocused = isFocused(item),
                onClick = { viewModel.cycleGripReserveMode(1) },
                onPrev = { viewModel.cycleGripReserveMode(-1) },
                options = remember(context) {
                    GripReserveMode.entries.map { context.getString(gripModeLabelRes(it)) }
                },
                onSelect = { index -> viewModel.setGripReserveMode(GripReserveMode.entries[index]) },
                pickerRequestToken = pickerToken(item)
            )

            ControllerGripItem.Controllers -> NavigationPreference(
                icon = Icons.Outlined.Gamepad,
                title = stringResource(R.string.settings_grip_controllers_title),
                subtitle = gripAutoControllerSubtitle(display.gripAutoControllers.controllers),
                isFocused = isFocused(item),
                onClick = { viewModel.showGripControllerModal() }
            )

            ControllerGripItem.ReservedHeight -> SliderPreference(
                title = stringResource(R.string.settings_grip_reserved_height_title),
                value = display.gripReservePercent,
                minValue = GRIP_RESERVE_MIN_PERCENT,
                maxValue = GRIP_RESERVE_MAX_PERCENT,
                isFocused = isFocused(item),
                step = GRIP_RESERVE_PERCENT_STEP,
                suffix = "%",
                onAdjust = { viewModel.adjustGripReservePercent(it) }
            )
        }
    }

    if (display.showGripControllerModal) {
        GripControllerModal(
            controllers = display.gripAutoControllers.controllers,
            onAdd = { id, name -> viewModel.addGripAutoController(id, name) },
            onRemove = { id -> viewModel.removeGripAutoController(id) },
            onDismiss = { viewModel.hideGripControllerModal() }
        )
    }
}
