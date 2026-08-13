package com.nendo.argosy.ui.screens.media.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import com.nendo.argosy.ui.screens.media.MediaDetailAction
import com.nendo.argosy.ui.screens.media.MediaDetailUiState
import com.nendo.argosy.ui.screens.media.formatPosition
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * The detail screen's primary actions. Long press on Play mirrors the gamepad's hold-confirm and
 * raises the resume choice, so touch reaches Start Over without a controller.
 */
@Composable
fun MediaActionRow(
    uiState: MediaDetailUiState,
    isSectionFocused: Boolean,
    onAction: (Int) -> Unit,
    onPlayLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        itemsIndexed(uiState.actions, key = { _, action -> action.name }) { index, action ->
            MediaActionButton(
                label = labelFor(action, uiState),
                icon = iconFor(action, uiState),
                focused = isSectionFocused && index == uiState.actionIndex,
                onClick = { onAction(index) },
                onLongClick = { if (action == MediaDetailAction.PLAY) onPlayLongPress() else onAction(index) }
            )
        }
    }
}

private fun labelFor(action: MediaDetailAction, uiState: MediaDetailUiState): String = when (action) {
    MediaDetailAction.PLAY -> {
        val target = uiState.playTarget
        when {
            target == null -> "Play"
            target.hasResumePosition -> "Resume ${formatPosition(target.resumeTicks)}"
            target.episodeLabel != null -> "Play ${target.episodeLabel}"
            else -> "Play"
        }
    }
    MediaDetailAction.DOWNLOAD -> uiState.downloadSummary.label
    MediaDetailAction.FAVORITE -> if (uiState.item?.isFavorite == true) "Favourited" else "Favourite"
    MediaDetailAction.WATCHED -> if (uiState.item?.played == true) "Watched" else "Mark Watched"
}

private fun iconFor(action: MediaDetailAction, uiState: MediaDetailUiState): ImageVector = when (action) {
    MediaDetailAction.PLAY -> Icons.Default.PlayArrow
    MediaDetailAction.DOWNLOAD ->
        if (uiState.downloadSummary.isComplete) Icons.Default.DownloadDone else Icons.Default.Download
    MediaDetailAction.FAVORITE ->
        if (uiState.item?.isFavorite == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder
    MediaDetailAction.WATCHED ->
        if (uiState.item?.played == true) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked
}

@Composable
private fun MediaActionButton(
    label: String,
    icon: ImageVector,
    focused: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    val background by animateColorAsState(
        targetValue = if (focused) {
            theme.focusAccent.copy(alpha = 0.2f).compositeOver(theme.surfaceElevated)
        } else {
            theme.surfaceElevated
        },
        label = "media-action-bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (focused) lerp(theme.focusAccent, Color.White, 0.45f) else theme.textPrimary,
        label = "media-action-label"
    )
    Row(
        modifier = Modifier
            .heightIn(min = Dimens.buttonHeight)
            .clip(shape)
            .background(background)
            .border(
                width = Dimens.borderThin,
                color = if (focused) theme.focusAccent else theme.hairlineLow,
                shape = shape
            )
            .clickableNoFocus(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = Dimens.buttonPaddingH, vertical = Dimens.buttonPaddingV),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(Dimens.iconSm)
        )
        Text(text = label, style = MaterialTheme.typography.titleSmall, color = contentColor, maxLines = 1)
    }
}
