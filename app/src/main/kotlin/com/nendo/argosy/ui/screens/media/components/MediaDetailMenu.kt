package com.nendo.argosy.ui.screens.media.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Recommend
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.screens.media.MediaDetailRow
import com.nendo.argosy.ui.screens.media.MediaDetailSection
import com.nendo.argosy.ui.screens.media.MediaDetailUiState
import com.nendo.argosy.ui.screens.media.formatPosition
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * The detail screen's permanent left rail, presented the way game detail presents its own: Play as a
 * filled primary button at the top, the rest of what acts on this title beneath it, then a divider
 * under Options and the title's own sections below it.
 *
 * The rail keeps a dim marker on its selected row while focus is out in the region that row names,
 * so leaving it and coming back lands where it was left rather than at the top.
 */
@Composable
fun MediaDetailMenu(
    uiState: MediaDetailUiState,
    isCompact: Boolean,
    onRow: (Int) -> Unit,
    onPlayLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val isSectionFocused = uiState.section == MediaDetailSection.MENU

    LaunchedEffect(uiState.rowIndex, uiState.rows.size) {
        if (uiState.rowIndex !in uiState.rows.indices) return@LaunchedEffect
        val layoutInfo = listState.layoutInfo
        val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == uiState.rowIndex }
        val visibleEnd = layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding
        val fullyVisible = itemInfo != null &&
            itemInfo.offset >= layoutInfo.viewportStartOffset &&
            itemInfo.offset + itemInfo.size <= visibleEnd
        if (!fullyVisible) listState.animateScrollToItem(uiState.rowIndex)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxHeight(),
        contentPadding = PaddingValues(bottom = Dimens.footerHeight + Dimens.spacingXl),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
        horizontalAlignment = if (isCompact) Alignment.CenterHorizontally else Alignment.Start
    ) {
        itemsIndexed(uiState.rows, key = { _, row -> row.name }) { index, row ->
            val focused = isSectionFocused && index == uiState.rowIndex
            val selected = index == uiState.rowIndex

            Column(
                horizontalAlignment = if (isCompact) Alignment.CenterHorizontally else Alignment.Start
            ) {
                when (row) {
                    MediaDetailRow.PLAY -> MediaPlayMenuItem(
                        label = playLabel(uiState),
                        focused = focused,
                        isCompact = isCompact,
                        onClick = { onRow(index) },
                        onLongClick = onPlayLongPress
                    )

                    else -> MediaMenuRow(
                        label = labelFor(row, uiState),
                        icon = iconFor(row, uiState),
                        focused = focused,
                        selected = selected,
                        isCompact = isCompact,
                        onClick = { onRow(index) }
                    )
                }

                if (row == MediaDetailRow.OPTIONS && index < uiState.rows.lastIndex) {
                    Spacer(Modifier.height(Dimens.spacingXs))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = Dimens.borderThin
                    )
                    Spacer(Modifier.height(Dimens.spacingXs))
                }
            }
        }
    }
}

@Composable
private fun MediaPlayMenuItem(
    label: String,
    focused: Boolean,
    isCompact: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusMd)
    val container = if (focused) theme.focusAccent else theme.focusAccent.copy(alpha = 0.5f)
    val content = if (focused) Color.White else Color.White.copy(alpha = 0.7f)

    Row(
        modifier = (if (isCompact) Modifier else Modifier.fillMaxWidth())
            .heightIn(min = Dimens.buttonHeight)
            .clip(shape)
            .background(container)
            .clickableNoFocus(onClick = onClick, onLongClick = onLongClick)
            .padding(
                horizontal = if (isCompact) Dimens.spacingSm else Dimens.buttonPaddingH,
                vertical = Dimens.buttonPaddingV
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = label,
            tint = content,
            modifier = Modifier.size(if (isCompact) Dimens.iconMd else Dimens.iconSm)
        )
        if (!isCompact) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MediaMenuRow(
    label: String,
    icon: ImageVector,
    focused: Boolean,
    selected: Boolean,
    isCompact: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val contentColor = when {
        focused -> lerp(theme.focusAccent, Color.White, 0.45f)
        selected -> theme.textPrimary
        else -> theme.textDim
    }

    Box(
        modifier = Modifier
            .then(if (isCompact) Modifier else Modifier.fillMaxWidth())
            .heightIn(min = Dimens.menuRowHeight)
            .argosyFocusIndicators(
                focused = focused,
                indicators = FocusIndicators.NavRow,
                selected = selected
            )
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingSm),
        contentAlignment = if (isCompact) Alignment.Center else Alignment.CenterStart
    ) {
        if (isCompact) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(Dimens.iconSm)
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(Dimens.iconSm)
                )
            }
        }
    }
}

private fun playLabel(uiState: MediaDetailUiState): String {
    val target = uiState.playTarget
    return when {
        target == null -> "Play"
        target.hasResumePosition -> "Resume ${formatPosition(target.resumeTicks)}"
        target.episodeLabel != null -> "Play ${target.episodeLabel}"
        else -> "Play"
    }
}

private fun labelFor(row: MediaDetailRow, uiState: MediaDetailUiState): String = when (row) {
    MediaDetailRow.PLAY -> playLabel(uiState)
    MediaDetailRow.DOWNLOAD -> uiState.downloadSummary.label
    MediaDetailRow.FAVORITE -> if (uiState.item?.isFavorite == true) "Favorited" else "Favorite"
    MediaDetailRow.WATCHED -> if (uiState.item?.played == true) "Watched" else "Mark Watched"
    MediaDetailRow.OPTIONS -> "Options"
    MediaDetailRow.SEASONS -> "Seasons"
    MediaDetailRow.EPISODES -> "Episodes"
    MediaDetailRow.CAST -> "Cast"
    MediaDetailRow.SIMILAR -> "More Like This"
}

private fun iconFor(row: MediaDetailRow, uiState: MediaDetailUiState): ImageVector = when (row) {
    MediaDetailRow.PLAY -> Icons.Default.PlayArrow
    MediaDetailRow.DOWNLOAD ->
        if (uiState.downloadSummary.isComplete) Icons.Default.DownloadDone else Icons.Default.Download
    MediaDetailRow.FAVORITE ->
        if (uiState.item?.isFavorite == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder
    MediaDetailRow.WATCHED ->
        if (uiState.item?.played == true) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked
    MediaDetailRow.OPTIONS -> Icons.Default.Tune
    MediaDetailRow.SEASONS -> Icons.Default.Layers
    MediaDetailRow.EPISODES -> Icons.AutoMirrored.Filled.List
    MediaDetailRow.CAST -> Icons.Default.Groups
    MediaDetailRow.SIMILAR -> Icons.Default.Recommend
}
