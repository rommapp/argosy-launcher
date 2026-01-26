package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FeaturedPlayList
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.DrawerItem
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.DrawerState
import com.nendo.argosy.ui.navigation.Screen

@Composable
fun MainDrawer(
    items: List<DrawerItem>,
    currentRoute: String?,
    focusedIndex: Int,
    drawerState: DrawerState,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = Dimens.spacingLg)
        ) {
            DrawerStatusBar()
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = Dimens.spacingLg, vertical = Dimens.radiusLg),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                items.forEachIndexed { index, item ->
                    if (index == items.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }

                    val badge = if (item.route == Screen.Downloads.route && drawerState.downloadCount > 0) {
                        drawerState.downloadCount
                    } else null

                    DrawerMenuItem(
                        item = item,
                        icon = getIconForRoute(item.route),
                        isFocused = index == focusedIndex,
                        isSelected = currentRoute == item.route,
                        badge = badge,
                        onClick = { onNavigate(item.route) }
                    )
                }
            }

            RomMStatusFooter(isConnected = drawerState.rommConnected)
        }
    }
}

@Composable
private fun DrawerStatusBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SystemStatusBar(
            contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun RomMStatusFooter(isConnected: Boolean) {
    val mutedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Icon(
            imageVector = if (isConnected) Icons.Default.Cloud else Icons.Default.CloudOff,
            contentDescription = null,
            tint = mutedColor,
            modifier = Modifier.size(Dimens.iconSm)
        )
        Text(
            text = if (isConnected) "Connected" else "Offline",
            style = MaterialTheme.typography.labelSmall,
            color = mutedColor
        )
    }
}

@Composable
private fun DrawerMenuItem(
    item: DrawerItem,
    icon: ImageVector,
    isFocused: Boolean,
    isSelected: Boolean,
    badge: Int? = null,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isFocused -> MaterialTheme.colorScheme.primaryContainer
        isSelected -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else -> Color.Transparent
    }

    val contentColor = when {
        isFocused -> MaterialTheme.colorScheme.primary
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    val indicatorWidth = if (isFocused) Dimens.spacingXs else 0.dp

    val shape = RoundedCornerShape(topEnd = Dimens.radiusMd, bottomEnd = Dimens.radiusMd)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = Dimens.spacingMd)
            .clip(shape)
            .background(backgroundColor)
            .clickableNoFocus(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .width(indicatorWidth)
                .height(Dimens.spacingXxl)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(if (isFocused) (Dimens.spacingLg - Dimens.spacingXs) else Dimens.spacingLg))
        Icon(
            imageVector = icon,
            contentDescription = item.label,
            tint = contentColor
        )
        Spacer(modifier = Modifier.width(Dimens.spacingMd))
        Text(
            text = item.label,
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
            modifier = Modifier.weight(1f)
        )
        if (badge != null) {
            Box(
                modifier = Modifier
                    .size(Dimens.iconMd)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badge.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(modifier = Modifier.width(Dimens.spacingSm))
        }
    }
}

private fun getIconForRoute(route: String): ImageVector = when (route) {
    Screen.Home.route -> Icons.Filled.FeaturedPlayList
    Screen.Library.route -> Icons.Default.VideoLibrary
    Screen.Downloads.route -> Icons.Default.Download
    Screen.Apps.route -> Icons.Default.Apps
    Screen.Settings.route -> Icons.Default.Settings
    else -> Icons.Default.Apps
}
