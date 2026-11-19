package com.nendo.argosy.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.ui.coil.AppIconData
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.reducedMotion
import com.nendo.argosy.ui.util.clickableNoFocus

private val APP_DRAWER_MAX_HEIGHT =
    com.nendo.argosy.ui.theme.generated.DimensionTokens.Layout.slotPickerListMaxHeight.dp

data class AppLaunchTarget(val displayId: Int, val number: Int)

enum class AppContextMenuItem {
    APP_INFO,
    TOGGLE_HOME,
    TOGGLE_SECONDARY_HOME,
    TOGGLE_VISIBILITY,
    REORDER,
    UNINSTALL
}

/**
 * A row in the app menu: one of the fixed actions, or one screen the app can be opened on, which
 * there is one of per attached display.
 */
sealed interface AppMenuRow {
    data class Action(val item: AppContextMenuItem) : AppMenuRow
    data class OpenOnScreen(val displayId: Int, val number: Int, val screenKey: String) : AppMenuRow
}

@Composable
fun AppLaunchMenuRow(number: Int, isFocused: Boolean, onClick: () -> Unit) {
    val accent = LocalArgosyTheme.current.focusAccent
    val background = if (isFocused) accent.copy(alpha = 0.15f) else Color.Transparent
    val content = if (isFocused) {
        lerp(accent, Color.White, 0.45f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickableNoFocus(onClick = onClick)
            .background(background)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.radiusLg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Tv,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(Dimens.iconMd)
        )
        Spacer(modifier = Modifier.width(Dimens.radiusLg))
        Text(
            text = stringResource(R.string.library_apps_menu_open_on_screen, number),
            style = MaterialTheme.typography.bodyLarge,
            color = content
        )
    }
}

@Composable
fun AppMenuItemRow(
    row: AppMenuRow,
    isFocused: Boolean,
    isAppHidden: Boolean = false,
    isOnHome: Boolean = false,
    isOnSecondaryHome: Boolean = false,
    @StringRes reorderLabel: Int = R.string.dock_menu_reorder,
    onClick: () -> Unit = {}
) {
    if (row is AppMenuRow.OpenOnScreen) {
        AppLaunchMenuRow(number = row.number, isFocused = isFocused, onClick = onClick)
        return
    }
    val item = (row as AppMenuRow.Action).item
    val (icon, label) = when (item) {
        AppContextMenuItem.APP_INFO ->
            Icons.Default.Info to stringResource(R.string.library_apps_menu_app_info)
        AppContextMenuItem.TOGGLE_HOME -> if (isOnHome) {
            Icons.Default.Home to stringResource(R.string.library_apps_menu_remove_from_home)
        } else {
            Icons.Outlined.Home to stringResource(R.string.library_apps_menu_add_to_home)
        }
        AppContextMenuItem.TOGGLE_SECONDARY_HOME -> if (isOnSecondaryHome) {
            Icons.Default.PushPin to stringResource(R.string.library_apps_menu_unpin_from_app_bar)
        } else {
            Icons.Outlined.PushPin to stringResource(R.string.library_apps_menu_pin_to_app_bar)
        }
        AppContextMenuItem.TOGGLE_VISIBILITY -> if (isAppHidden) {
            Icons.Default.Visibility to stringResource(R.string.library_apps_menu_show)
        } else {
            Icons.Default.VisibilityOff to stringResource(R.string.library_apps_menu_hide)
        }
        AppContextMenuItem.REORDER ->
            Icons.Default.SwapVert to stringResource(reorderLabel)
        AppContextMenuItem.UNINSTALL ->
            Icons.Default.Delete to stringResource(R.string.library_apps_menu_uninstall)
    }

    val theme = LocalArgosyTheme.current
    val isDangerous = item == AppContextMenuItem.UNINSTALL
    val backgroundColor = when {
        isFocused && isDangerous -> theme.destructive.copy(alpha = 0.15f)
        isFocused -> theme.focusAccent.copy(alpha = 0.15f)
        else -> Color.Transparent
    }
    val contentColor = when {
        isFocused && isDangerous -> lerp(theme.destructive, Color.White, 0.45f)
        isFocused -> lerp(theme.focusAccent, Color.White, 0.45f)
        isDangerous -> theme.destructive
        else -> MaterialTheme.colorScheme.onSurface
    }
    val iconColor = when {
        isFocused && isDangerous -> lerp(theme.destructive, Color.White, 0.45f)
        isFocused -> lerp(theme.focusAccent, Color.White, 0.45f)
        isDangerous -> theme.destructive
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickableNoFocus(onClick = onClick)
            .background(backgroundColor)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.radiusLg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(Dimens.iconMd)
        )
        Spacer(modifier = Modifier.width(Dimens.radiusLg))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor
        )
    }
}

@Composable
fun AppLaunchMenu(
    appLabel: String,
    rows: List<AppMenuRow>,
    focusIndex: Int,
    isAppHidden: Boolean = false,
    isOnSecondaryHome: Boolean = false,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    CenteredModal(title = appLabel, onDismiss = onDismiss) {
        rows.forEachIndexed { index, row ->
            AppMenuItemRow(
                row = row,
                isFocused = index == focusIndex,
                isAppHidden = isAppHidden,
                isOnSecondaryHome = isOnSecondaryHome,
                onClick = { onSelect(index) }
            )
        }
    }
}

data class AppDrawerEntry(val packageName: String, val label: String)

@Composable
fun CompanionAppDrawer(
    apps: List<AppDrawerEntry>,
    onLaunch: (String) -> Unit,
    onLongPress: ((String) -> Unit)? = null,
    focusIndex: Int = -1,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    val reduced = reducedMotion()
    LaunchedEffect(focusIndex) {
        if (focusIndex in apps.indices) listState.animateScrollToItemCentered(focusIndex, reduced)
    }
    CenteredModal(title = stringResource(R.string.dual_companion_app_drawer_title), onDismiss = onDismiss) {
        LazyColumn(
            state = listState,
            modifier = Modifier.heightIn(max = APP_DRAWER_MAX_HEIGHT)
        ) {
            items(apps.size, key = { apps[it].packageName }) { index ->
                val entry = apps[index]
                AppDrawerRow(
                    entry = entry,
                    isFocused = index == focusIndex,
                    onClick = { onLaunch(entry.packageName) },
                    onLongPress = onLongPress?.let { press -> { press(entry.packageName) } }
                )
            }
        }
    }
}

@Composable
private fun AppDrawerRow(
    entry: AppDrawerEntry,
    isFocused: Boolean,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)?
) {
    val accent = LocalArgosyTheme.current.focusAccent
    val background = if (isFocused) accent.copy(alpha = 0.15f) else Color.Transparent
    val content = if (isFocused) {
        lerp(accent, Color.White, 0.45f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .let { base ->
                if (onLongPress == null) base.clickableNoFocus(onClick = onClick)
                else base.clickableNoFocus(onClick = onClick, onLongClick = onLongPress)
            }
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = AppIconData(entry.packageName),
            contentDescription = null,
            modifier = Modifier
                .size(Dimens.iconLg)
                .clip(RoundedCornerShape(Dimens.radiusMd))
        )
        Spacer(modifier = Modifier.width(Dimens.radiusLg))
        Text(
            text = entry.label,
            style = MaterialTheme.typography.bodyLarge,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
