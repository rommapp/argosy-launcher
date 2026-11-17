package com.nendo.argosy.ui.screens.settings.sections

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.GripReserveMode
import com.nendo.argosy.domain.model.GripAutoController
import com.nendo.argosy.ui.components.NavigationPreference
import com.nendo.argosy.ui.components.SegmentedPreference
import com.nendo.argosy.ui.components.SliderPreference
import com.nendo.argosy.ui.screens.settings.DisplayState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.GripControllerModal
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.screens.settings.search.SearchableSetting
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.GRIP_RESERVE_MAX_PERCENT
import com.nendo.argosy.ui.theme.GRIP_RESERVE_MIN_PERCENT

internal const val GRIP_RESERVE_PERCENT_STEP = 2

internal sealed class ControllerGripItem(
    val key: String,
    val visibleWhen: (DisplayState) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = this is SearchableSetting

    sealed class Row(
        key: String,
        @StringRes override val labelRes: Int,
        visibleWhen: (DisplayState) -> Boolean = { true }
    ) : ControllerGripItem(key, visibleWhen), SearchableSetting

    data object Mode : Row("gripMode", R.string.settings_grip_mode_title)
    data object Controllers : Row(
        "gripControllers",
        R.string.settings_grip_controllers_title,
        visibleWhen = { it.gripReserveMode == GripReserveMode.AUTO }
    )
    data object ReservedHeight : Row(
        "gripReservePercent",
        R.string.settings_grip_reserved_height_title,
        visibleWhen = { it.gripReserveMode != GripReserveMode.OFF }
    )

    companion object {
        val ALL: List<ControllerGripItem>
            get() = listOf(Mode, Controllers, ReservedHeight)
    }
}

internal val controllerGripLayout = SettingsLayout<ControllerGripItem, DisplayState>(
    allItems = ControllerGripItem.ALL,
    isFocusable = { it.isFocusable },
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

    val visibleItems = remember(display) { controllerGripLayout.visibleItems(display) }
    val sections = remember(display) { controllerGripLayout.buildSections(display) }

    fun isFocused(item: ControllerGripItem): Boolean =
        uiState.focusedIndex == controllerGripLayout.focusIndexOf(item, display)

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
            is ControllerGripItem.Mode -> SegmentedPreference(
                title = stringResource(item.labelRes),
                subtitle = when (display.gripReserveMode) {
                    GripReserveMode.OFF -> stringResource(R.string.settings_grip_mode_subtitle_off)
                    GripReserveMode.ON -> stringResource(R.string.settings_grip_mode_subtitle_on)
                    GripReserveMode.AUTO -> stringResource(R.string.settings_grip_mode_subtitle_auto)
                },
                options = GripReserveMode.entries.map { stringResource(gripModeLabelRes(it)) },
                selectedIndex = GripReserveMode.entries.indexOf(display.gripReserveMode),
                isFocused = isFocused(item),
                onSelect = { index -> viewModel.setGripReserveMode(GripReserveMode.entries[index]) }
            )

            is ControllerGripItem.Controllers -> NavigationPreference(
                icon = Icons.Outlined.Gamepad,
                title = stringResource(item.labelRes),
                subtitle = gripAutoControllerSubtitle(display.gripAutoControllers.controllers),
                isFocused = isFocused(item),
                onClick = { viewModel.showGripControllerModal() }
            )

            is ControllerGripItem.ReservedHeight -> SliderPreference(
                title = stringResource(item.labelRes),
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
