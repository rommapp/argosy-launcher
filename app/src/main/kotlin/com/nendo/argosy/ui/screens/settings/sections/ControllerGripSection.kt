package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
    sectionTitle = { null }
)

internal fun controllerGripMaxFocusIndex(display: DisplayState): Int =
    controllerGripLayout.maxFocusIndex(display)

internal fun controllerGripItemAtFocusIndex(index: Int, display: DisplayState): ControllerGripItem? =
    controllerGripLayout.itemAtFocusIndex(index, display)

internal fun controllerGripSections(display: DisplayState) =
    controllerGripLayout.buildSections(display)

internal fun controllerGripFocusIndexOf(item: ControllerGripItem, display: DisplayState): Int =
    controllerGripLayout.focusIndexOf(item, display)

internal fun gripAutoControllerSubtitle(controllers: List<GripAutoController>): String =
    when (controllers.size) {
        0 -> "No controllers chosen"
        1 -> controllers.first().name
        else -> "${controllers.size} controllers"
    }

@Composable
fun ControllerGripSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val display = uiState.display

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
                title = "Mode",
                value = display.gripReserveMode.displayName,
                subtitle = when (display.gripReserveMode) {
                    GripReserveMode.OFF -> "Never shift the UI up"
                    GripReserveMode.ON -> "Always shift the UI up in portrait"
                    GripReserveMode.AUTO -> "Automatically shift the UI up when a chosen controller is connected"
                },
                isFocused = isFocused(item),
                onClick = { viewModel.cycleGripReserveMode(1) },
                onPrev = { viewModel.cycleGripReserveMode(-1) },
                options = GripReserveMode.entries.map { it.displayName },
                onSelect = { index -> viewModel.setGripReserveMode(GripReserveMode.entries[index]) },
                pickerRequestToken = pickerToken(item)
            )

            ControllerGripItem.Controllers -> NavigationPreference(
                icon = Icons.Outlined.Gamepad,
                title = "Controllers",
                subtitle = gripAutoControllerSubtitle(display.gripAutoControllers.controllers),
                isFocused = isFocused(item),
                onClick = { viewModel.showGripControllerModal() }
            )

            ControllerGripItem.ReservedHeight -> SliderPreference(
                title = "Reserved Height",
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
