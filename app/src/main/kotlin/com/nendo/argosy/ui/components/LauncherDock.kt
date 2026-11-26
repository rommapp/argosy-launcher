package com.nendo.argosy.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.generated.ComponentDefaults

/**
 * The app dock as every surface draws it, with reordering, the screen focus picker and the role
 * swap wired to the dual-screen manager. [onHeightChanged] reports the bar's height without its
 * surrounding padding.
 */
@Composable
fun LauncherDock(
    apps: List<String>,
    reorder: DockReorderSession?,
    onAppClick: (String) -> Unit,
    onAppHold: (String) -> Unit,
    onOpenDrawer: () -> Unit,
    focusDisplays: List<DisplayFocusTarget>,
    focusPickerOpen: Boolean,
    focusPickerIndex: Int,
    canSwapRoles: Boolean,
    toolsOpen: Boolean,
    toolIndex: Int,
    onToolsToggle: () -> Unit,
    onToolFocus: (Int) -> Unit,
    onToolsDismiss: () -> Unit,
    topPadding: Dp,
    modifier: Modifier = Modifier,
    focusedIndex: Int = APP_BAR_NOTHING_FOCUSED,
    onKeyboardToggle: (() -> Unit)? = null,
    controllerInputEnabled: Boolean = true,
    maximumWidth: Dp = Dimens.breadcrumbMaxWidth,
    onHeightChanged: (Dp) -> Unit = {}
) {
    val manager = DualScreenManagerHolder.instance
    val density = LocalDensity.current
    CompanionAppBar(
        apps = apps,
        onAppClick = onAppClick,
        reorder = reorder,
        onReorderStart = { manager?.beginDockReorder(it, false) },
        onReorderTarget = { token, target -> manager?.targetDockReorder(token, target) },
        onReorderSave = { manager?.saveDockReorder(it) },
        onReorderCancel = { manager?.cancelDockReorder(it) },
        onAppHold = onAppHold,
        focusedIndex = focusedIndex,
        onOpenDrawer = onOpenDrawer,
        onKeyboardToggle = onKeyboardToggle,
        focusDisplays = focusDisplays,
        focusPickerOpen = focusPickerOpen,
        focusPickerIndex = focusPickerIndex,
        onFocusPickerToggle = manager?.let { dsm ->
            { if (focusPickerOpen) dsm.closeFocusPicker() else dsm.openFocusPicker() }
        },
        onFocusPickerMove = { manager?.moveFocusPicker(it) },
        onFocusDisplay = { displayId ->
            manager?.focusDisplay(displayId)
            manager?.closeFocusPicker()
        },
        onSwapRoles = manager?.takeIf { canSwapRoles }?.let { dsm -> { dsm.swapRoles() } },
        toolsOpen = toolsOpen,
        toolIndex = toolIndex,
        onToolsToggle = onToolsToggle,
        onToolFocus = onToolFocus,
        onToolsDismiss = onToolsDismiss,
        controllerInputEnabled = controllerInputEnabled,
        maximumWidth = maximumWidth,
        modifier = modifier
            .padding(
                start = Dimens.spacingMd,
                top = topPadding,
                end = Dimens.spacingMd,
                bottom = ComponentDefaults.Carousel.headerEdgePaddingDp.dp
            )
            .onSizeChanged { onHeightChanged(with(density) { it.height.toDp() }) }
    )
}
