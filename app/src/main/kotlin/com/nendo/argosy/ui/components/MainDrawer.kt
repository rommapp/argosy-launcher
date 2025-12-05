package com.nendo.argosy.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
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
import com.nendo.argosy.ui.navigation.Screen

@Composable
fun MainDrawer(
    items: List<DrawerItem>,
    currentRoute: String?,
    focusedIndex: Int,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(modifier = modifier) {
        Column(modifier = Modifier.padding(vertical = 24.dp)) {
            Text(
                text = "A-LAUNCHER",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            items.forEachIndexed { index, item ->
                if (index == items.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }

                DrawerMenuItem(
                    item = item,
                    icon = getIconForRoute(item.route),
                    isFocused = index == focusedIndex,
                    isSelected = currentRoute == item.route,
                    onClick = { onNavigate(item.route) }
                )
            }
        }
    }
}

@Composable
private fun DrawerMenuItem(
    item: DrawerItem,
    icon: ImageVector,
    isFocused: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val animSpec = spring<Color>(stiffness = 500f)
    val dpAnimSpec = spring<androidx.compose.ui.unit.Dp>(stiffness = 500f)

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isFocused -> MaterialTheme.colorScheme.primaryContainer
            isSelected -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else -> Color.Transparent
        },
        animationSpec = animSpec,
        label = "bg"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            isFocused -> MaterialTheme.colorScheme.primary
            isSelected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface
        },
        animationSpec = animSpec,
        label = "content"
    )

    val indicatorWidth by animateDpAsState(
        targetValue = if (isFocused) 4.dp else 0.dp,
        animationSpec = dpAnimSpec,
        label = "indicator"
    )

    val shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 16.dp)
            .clip(shape)
            .background(backgroundColor)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .width(indicatorWidth)
                .height(48.dp)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(if (isFocused) 20.dp else 24.dp))
        Icon(
            imageVector = icon,
            contentDescription = item.label,
            tint = contentColor
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.titleMedium,
            color = contentColor
        )
    }
}

private fun getIconForRoute(route: String): ImageVector = when (route) {
    Screen.Home.route -> Icons.Default.Home
    Screen.Library.route -> Icons.Default.VideoLibrary
    Screen.Downloads.route -> Icons.Default.Download
    Screen.Apps.route -> Icons.Default.Apps
    Screen.Settings.route -> Icons.Default.Settings
    else -> Icons.Default.Apps
}
