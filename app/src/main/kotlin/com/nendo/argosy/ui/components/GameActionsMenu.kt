package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.input.ModalPresenceEffect
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.util.clickableNoFocus

data class GameActionEntry(
    val icon: ImageVector? = null,
    val label: String,
    val isDangerous: Boolean = false,
    val onClick: () -> Unit
)

/**
 * The game actions menu a long press or Hold-A opens on a game. [focusIndex] runs through
 * [options] and then [dangerousOptions]. The caller routes gamepad input to it.
 */
@Composable
fun GameActionsMenu(
    options: List<GameActionEntry>,
    dangerousOptions: List<GameActionEntry>,
    focusIndex: Int,
    onDismiss: () -> Unit
) {
    ModalPresenceEffect()
    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)
    val listState = rememberLazyListState()
    val listIndex = if (focusIndex < options.size) focusIndex else focusIndex + 1
    FocusedScroll(listState = listState, focusedIndex = listIndex)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor)
            .clickableNoFocus(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(Dimens.radiusLg)
                )
                .clickableNoFocus(enabled = false) {}
                .padding(Dimens.spacingLg)
                .width(Dimens.modalWidth)
                .heightIn(max = maxHeight * 0.85f)
        ) {
            Text(
                text = stringResource(R.string.library_quickmenu_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(Dimens.spacingMd))

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                itemsIndexed(options, key = { index, _ -> "option:$index" }) { index, entry ->
                    GameActionItem(
                        icon = entry.icon,
                        label = entry.label,
                        isFocused = focusIndex == index,
                        onClick = entry.onClick
                    )
                }
                if (dangerousOptions.isNotEmpty()) {
                    item(key = "divider") {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = Dimens.spacingSm),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
                itemsIndexed(dangerousOptions, key = { index, _ -> "danger:$index" }) { index, entry ->
                    GameActionItem(
                        icon = entry.icon,
                        label = entry.label,
                        isFocused = focusIndex == options.size + index,
                        isDangerous = entry.isDangerous,
                        onClick = entry.onClick
                    )
                }
            }
        }
    }
}

@Composable
private fun GameActionItem(
    icon: ImageVector? = null,
    label: String,
    isFocused: Boolean = false,
    isDangerous: Boolean = false,
    onClick: () -> Unit
) {
    val contentColor = when {
        isDangerous && isFocused -> lerp(LocalArgosyTheme.current.destructive, Color.White, 0.45f)
        isDangerous -> LocalArgosyTheme.current.destructive
        isFocused -> lerp(LocalArgosyTheme.current.focusAccent, Color.White, 0.45f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    val backgroundColor = when {
        isDangerous && isFocused -> LocalArgosyTheme.current.destructive.copy(alpha = 0.15f)
        isFocused -> LocalArgosyTheme.current.focusAccent.copy(alpha = 0.15f)
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickableNoFocus(onClick = onClick)
            .background(backgroundColor, RoundedCornerShape(Dimens.radiusMd))
            .padding(horizontal = Dimens.radiusLg, vertical = Dimens.spacingSm + Dimens.borderMedium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.radiusLg)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(Dimens.iconSm)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            modifier = Modifier.weight(1f)
        )
    }
}
