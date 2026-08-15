package com.nendo.argosy.ui.screens.media.modals

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.primitives.ModalScaffold
import com.nendo.argosy.ui.screens.media.MediaDownloadOption
import com.nendo.argosy.ui.screens.media.MediaDownloadPrompt
import com.nendo.argosy.ui.screens.media.MediaDownloadScope
import com.nendo.argosy.ui.screens.media.MediaDownloadStep
import com.nendo.argosy.ui.screens.media.MediaEpisodePickerRow
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * The download choice: what to fetch for a series, then at what quality.
 *
 * It renders whatever options the prompt carries and reports moves back out; the focused index lives
 * in the view model like every other selection in the app, so the same prompt drives touch and
 * gamepad from one source.
 */
@Composable
fun MediaDownloadModalHost(
    prompt: MediaDownloadPrompt?,
    onMove: (Int) -> Unit,
    onFocus: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onCollapseSeason: (Boolean) -> Unit = {},
    onCommitSelection: () -> Unit = {}
) {
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnConfirm by rememberUpdatedState(onConfirm)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnCollapse by rememberUpdatedState(onCollapseSeason)
    val currentOnCommit by rememberUpdatedState(onCommitSelection)

    val inputHandler = remember {
        object : InputHandler {
            override fun onUp(): InputResult {
                currentOnMove(-1)
                return InputResult.HANDLED
            }

            override fun onDown(): InputResult {
                currentOnMove(1)
                return InputResult.HANDLED
            }

            override fun onConfirm(): InputResult {
                currentOnConfirm()
                return InputResult.HANDLED
            }

            override fun onBack(): InputResult {
                currentOnDismiss()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }

            override fun onLeft(): InputResult {
                currentOnCollapse(false)
                return InputResult.HANDLED
            }

            override fun onRight(): InputResult {
                currentOnCollapse(true)
                return InputResult.HANDLED
            }

            override fun onMenu(): InputResult = InputResult.HANDLED

            override fun onSecondaryAction(): InputResult = InputResult.HANDLED

            override fun onContextMenu(): InputResult {
                currentOnCommit()
                return InputResult.HANDLED
            }
            override fun onPrevSection(): InputResult = InputResult.HANDLED
            override fun onNextSection(): InputResult = InputResult.HANDLED
            override fun onPrevTrigger(): InputResult = InputResult.HANDLED
            override fun onNextTrigger(): InputResult = InputResult.HANDLED
            override fun onSelect(): InputResult = InputResult.HANDLED
            override fun onLeftStickClick(): InputResult = InputResult.HANDLED
            override fun onRightStickClick(): InputResult = InputResult.HANDLED
            override fun onLongConfirm(): InputResult = InputResult.HANDLED
        }
    }

    ModalInputEffect(active = prompt != null, handler = inputHandler)

    val content = prompt ?: return
    val theme = LocalArgosyTheme.current
    val warningColor = LocalLauncherTheme.current.semanticColors.warning

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    ModalScaffold(visible = true, onDismiss = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier
                .heightIn(max = screenHeight - (Dimens.footerHeight + Dimens.spacingLg) * 2)
                .padding(Dimens.spacingLg)
        ) {
            Text(
                text = content.title,
                style = MaterialTheme.typography.titleMedium,
                color = theme.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            content.subtitle?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = theme.textMute)
            }
            content.note?.let {
                Spacer(Modifier.height(Dimens.spacingSm))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                ) {
                    Icon(
                        imageVector = Icons.Default.Subtitles,
                        contentDescription = null,
                        tint = theme.textDim,
                        modifier = Modifier.size(Dimens.iconXs)
                    )
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textDim,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            content.warning?.let {
                Spacer(Modifier.height(Dimens.spacingSm))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = warningColor,
                        modifier = Modifier.size(Dimens.iconXs)
                    )
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = warningColor
                    )
                }
            }
            Spacer(Modifier.height(Dimens.spacingLg))
            val listState = rememberLazyListState()
            FocusedScroll(
                listState = listState,
                focusedIndex = content.focusedIndex.coerceAtMost(
                    (content.episodeRowCount - 1).coerceAtLeast(0)
                )
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                if (content.step == MediaDownloadStep.EPISODES) {
                    val rows = content.episodes.visibleRows
                    itemsIndexed(
                        items = rows,
                        key = { _, row -> row.itemId ?: "season-${row.seasonKey}" }
                    ) { index, row ->
                        MediaEpisodePickerRowView(
                            row = row,
                            isSelected = row.itemId in content.episodes.selected,
                            isCollapsed = row.seasonKey in content.episodes.collapsed,
                            focused = index == content.focusedIndex,
                            onClick = {
                                onFocus(index)
                                onConfirm()
                            }
                        )
                    }
                } else {
                    itemsIndexed(
                        items = content.options,
                        key = { _, option ->
                            option.quality?.name ?: option.scope?.name ?: option.label
                        }
                    ) { index, option ->
                        MediaDownloadOptionRow(
                            option = option,
                            step = content.step,
                            focused = index == content.focusedIndex,
                            onClick = {
                                onFocus(index)
                                onConfirm()
                            }
                        )
                    }
                }
            }
            if (content.step == MediaDownloadStep.EPISODES) {
                Spacer(Modifier.height(Dimens.spacingSm))
                Text(
                    text = selectionSummary(content.episodes.selected.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textDim
                )
                Spacer(Modifier.height(Dimens.spacingSm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                ) {
                    MediaEpisodeActionButton(
                        label = "Cancel",
                        isPrimary = false,
                        isFocused = content.isEpisodeCancelFocused,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    MediaEpisodeActionButton(
                        label = "Download",
                        isPrimary = content.episodes.selected.isNotEmpty(),
                        isFocused = content.isEpisodeDownloadFocused,
                        onClick = { if (content.episodes.selected.isNotEmpty()) onCommitSelection() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * The chooser's two actions, as surfaces a finger can press.
 */
@Composable
private fun MediaEpisodeActionButton(
    label: String,
    isPrimary: Boolean,
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    val background by animateColorAsState(
        targetValue = when {
            isFocused -> theme.focusAccent
            isPrimary -> theme.focusAccent.copy(alpha = 0.35f).compositeOver(theme.surfaceElevated)
            else -> theme.surfaceElevated
        },
        animationSpec = Motion.focusColorSpec,
        label = "media-episode-action-bg"
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(background)
            .clickableNoFocus(onClick = onClick)
            .padding(vertical = Dimens.spacingSm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (isFocused || isPrimary) theme.textPrimary else theme.textDim
        )
    }
}

private fun selectionSummary(count: Int): String = when (count) {
    0 -> "Nothing chosen yet"
    1 -> "1 episode chosen"
    else -> "$count episodes chosen"
}

/**
 * A season to fold, or an episode to tick. A season carries no tick of its own: pressing it takes
 * the whole season on or off, which is a different act from choosing one episode and reads better
 * as a heading than as a third checkbox state.
 */
@Composable
private fun MediaEpisodePickerRowView(
    row: MediaEpisodePickerRow,
    isSelected: Boolean,
    isCollapsed: Boolean,
    focused: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    val background by animateColorAsState(
        targetValue = if (focused) {
            theme.focusAccent.copy(alpha = 0.2f).compositeOver(theme.surfaceElevated)
        } else if (row.isHeader) {
            theme.surfaceRaised
        } else {
            theme.surfaceElevated
        },
        animationSpec = Motion.focusColorSpec,
        label = "media-episode-row-bg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Icon(
            imageVector = when {
                row.isHeader && isCollapsed -> Icons.Default.ChevronRight
                row.isHeader -> Icons.Default.ExpandMore
                row.isDownloaded -> Icons.Default.DownloadDone
                isSelected -> Icons.Default.CheckBox
                else -> Icons.Default.CheckBoxOutlineBlank
            },
            contentDescription = null,
            tint = if (row.isDownloaded) theme.focusAccent else theme.textDim,
            modifier = Modifier.size(Dimens.iconSm)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.label,
                style = if (row.isHeader) {
                    MaterialTheme.typography.labelLarge
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = if (row.isDownloaded) theme.textMute else theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            row.supporting?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textMute,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun MediaDownloadOptionRow(
    option: MediaDownloadOption,
    step: MediaDownloadStep,
    focused: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    val background by animateColorAsState(
        targetValue = if (focused) {
            theme.focusAccent.copy(alpha = 0.2f).compositeOver(theme.surfaceElevated)
        } else {
            theme.surfaceElevated
        },
        animationSpec = Motion.focusColorSpec,
        label = "media-download-option-bg"
    )
    val labelColor by animateColorAsState(
        targetValue = when {
            !option.enabled -> theme.textMute
            focused -> lerp(theme.focusAccent, Color.White, 0.45f)
            else -> theme.textPrimary
        },
        animationSpec = Motion.focusColorSpec,
        label = "media-download-option-label"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.menuRowHeightLg)
            .clip(shape)
            .background(background)
            .border(
                width = Dimens.borderThin,
                color = if (focused) theme.focusAccent else theme.hairlineLow,
                shape = shape
            )
            .clickableNoFocus(enabled = option.enabled, onClick = onClick)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
    ) {
        val icon = when {
            option.scope == MediaDownloadScope.REMOVE -> Icons.Default.Delete
            step == MediaDownloadStep.QUALITY -> Icons.Default.HighQuality
            else -> Icons.Default.Download
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = labelColor,
            modifier = Modifier.size(Dimens.iconSm)
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = option.label,
                style = MaterialTheme.typography.titleSmall,
                color = labelColor,
                maxLines = 1
            )
            option.supporting?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
