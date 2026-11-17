package com.nendo.argosy.ui.quickmenu.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.quickmenu.PLAY_STRIP_MIN_WEEKS
import com.nendo.argosy.ui.quickmenu.QuickMenuOrb
import com.nendo.argosy.ui.quickmenu.QuickMenuPreview
import com.nendo.argosy.ui.quickmenu.SaveStatusUi
import com.nendo.argosy.ui.quickmenu.SaveSyncState
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.util.formatPlayTime
import com.nendo.argosy.util.formatRelativeTime
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val QuickMenuTokens = ComponentDefaults.QuickMenu
private val RandomTokens = ComponentDefaults.QuickMenuRandom

/**
 * The focused game of a quick menu section: the presentation screen draws it [large], and the
 * single-screen sheet draws it beside its list. [onPrimary] is the section's A action.
 * [contentTopInset] keeps the details and cover below chrome drawn over the pane's top edge.
 */
@Composable
fun QuickMenuPreviewPane(
    preview: QuickMenuPreview,
    large: Boolean,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    showPrimaryButton: Boolean = false,
    contentTopInset: Dp = 0.dp
) {
    AnimatedContent(
        targetState = preview,
        contentKey = { it.section to it.game.id },
        transitionSpec = {
            fadeIn(animationSpec = tween(Motion.durationContent)) togetherWith
                fadeOut(animationSpec = tween(Motion.durationContent))
        },
        modifier = modifier.clip(RoundedCornerShape(if (large) Dimens.radiusSm else Dimens.radiusPanel)),
        label = "quickMenuPreview"
    ) { shown ->
        val screenshot = shown.extras?.screenshotPath?.takeIf { shown.section == QuickMenuOrb.RECENT }
        Box(modifier = Modifier.fillMaxSize()) {
            QuickMenuBackdrop(
                path = shown.game.backdropPath,
                isScreenshot = shown.game.backdropIsScreenshotOrArt,
                modifier = Modifier.fillMaxSize()
            )
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = contentTopInset)
                    .padding(if (large) Dimens.spacingXl else Dimens.spacingLg)
            ) {
                val sideWidth = maxWidth * QuickMenuTokens.previewCoverWidthRatio
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PreviewDetails(
                        preview = shown,
                        large = large,
                        onPrimary = onPrimary,
                        showPrimaryButton = showPrimaryButton,
                        modifier = Modifier.weight(1f)
                    )
                    if (screenshot != null) {
                        AsyncImage(
                            model = rememberFileImageModel(screenshot),
                            contentDescription = shown.game.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(sideWidth * 2)
                                .aspectRatio(SCREENSHOT_ASPECT)
                                .clip(RoundedCornerShape(Dimens.radiusLg))
                                .clickableNoFocus(onClick = onPrimary)
                        )
                    } else {
                        QuickMenuCover(
                            path = shown.game.coverPath,
                            title = shown.game.title,
                            modifier = Modifier
                                .width(sideWidth)
                                .aspectRatio(RandomTokens.coverAspectRatio)
                                .clickableNoFocus(onClick = onPrimary)
                        )
                    }
                }
            }
        }
    }
}

private const val SCREENSHOT_ASPECT = 4f / 3f

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreviewDetails(
    preview: QuickMenuPreview,
    large: Boolean,
    onPrimary: () -> Unit,
    showPrimaryButton: Boolean,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val game = preview.game
    val titleStyle: TextStyle = if (large) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall
    val metaLine = listOfNotNull(game.year?.toString(), game.genre).joinToString(" | ")
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
        Text(
            text = eyebrowText(preview),
            style = MaterialTheme.typography.labelMedium,
            color = theme.textDim,
            maxLines = 1
        )
        Text(
            text = game.title,
            style = titleStyle,
            color = theme.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            QuickMenuChip(
                text = game.platformName ?: stringResource(R.string.ui_quick_menu_row_platform_unknown),
                tone = ChipTone.ACCENT
            )
            when {
                preview.isRunning -> QuickMenuChip(stringResource(R.string.ui_quick_menu_recent_running_chip), ChipTone.RUNNING)
                game.isDownloaded -> QuickMenuChip(stringResource(R.string.ui_quick_menu_preview_installed), ChipTone.SUCCESS)
                else -> QuickMenuChip(stringResource(R.string.ui_quick_menu_preview_not_downloaded))
            }
            if (preview.isRemoved) {
                QuickMenuChip(
                    text = stringResource(
                        if (preview.section == QuickMenuOrb.FAVORITES) R.string.ui_quick_menu_favorites_removed
                        else R.string.ui_quick_menu_top_new_dismissed
                    )
                )
            }
        }
        val rating = game.rating
        if (metaLine.isNotEmpty() || rating != null) {
            Text(
                text = listOfNotNull(
                    metaLine.takeIf { it.isNotEmpty() },
                    rating?.let { stringResource(R.string.ui_quick_menu_preview_rating, it.toInt()) }
                ).joinToString(" | "),
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        SectionBody(preview = preview, large = large)
        if (showPrimaryButton) {
            ActionButton(
                label = stringResource(
                    if (preview.isRunning) R.string.ui_quick_menu_recent_action_resume
                    else R.string.ui_quick_menu_recent_action_play
                ),
                onClick = onPrimary,
                primary = true
            )
        }
    }
}

@Composable
private fun eyebrowText(preview: QuickMenuPreview): String = when (preview.section) {
    QuickMenuOrb.RANDOM -> stringResource(R.string.ui_quick_menu_random_eyebrow)
    QuickMenuOrb.MOST_PLAYED -> preview.rank?.let { stringResource(R.string.ui_quick_menu_most_played_eyebrow, it) }
        ?: stringResource(R.string.ui_quick_menu_orb_most_played)
    QuickMenuOrb.TOP_UNPLAYED -> stringResource(R.string.ui_quick_menu_top_new_eyebrow)
    QuickMenuOrb.RECENT -> stringResource(R.string.ui_quick_menu_recent_eyebrow)
    QuickMenuOrb.FAVORITES -> stringResource(R.string.ui_quick_menu_orb_favorites)
    QuickMenuOrb.SEARCH -> stringResource(R.string.ui_quick_menu_orb_search)
}

@Composable
private fun SectionBody(preview: QuickMenuPreview, large: Boolean) {
    val theme = LocalArgosyTheme.current
    val context = LocalContext.current
    val game = preview.game
    when (preview.section) {
        QuickMenuOrb.RANDOM, QuickMenuOrb.TOP_UNPLAYED -> {
            game.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.textPrimary,
                    maxLines = if (large) RandomTokens.descriptionMaxLines else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (preview.section == QuickMenuOrb.TOP_UNPLAYED) {
                QuickMenuChip(text = topNewReasonText(preview.reason), tone = ChipTone.ACCENT)
            }
        }
        QuickMenuOrb.MOST_PLAYED -> {
            val stats = preview.extras?.stats
            PlayStats(
                totalMinutes = stats?.totalMinutes ?: game.playTimeMinutes.toLong(),
                sessionCount = stats?.sessionCount,
                lastPlayed = stats?.lastPlayed ?: game.lastPlayed,
                totalLabel = stringResource(R.string.ui_quick_menu_preview_total),
                lastPlayedLabel = stringResource(R.string.ui_quick_menu_preview_last_played)
            )
            if (stats != null && stats.playedWeeks >= PLAY_STRIP_MIN_WEEKS) {
                PlayStrip(
                    weeks = stats.weeklyMinutes,
                    busiestWeek = stats.busiestWeek,
                    modifier = Modifier.fillMaxWidth()
                )
                val busiestStart = stats.busiestWeekStart
                val busiestIndex = stats.busiestWeek
                if (busiestStart != null && busiestIndex != null) {
                    Text(
                        text = stringResource(
                            R.string.ui_quick_menu_preview_busiest_week,
                            busiestStart.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                            formatPlayTime(context, stats.weeklyMinutes[busiestIndex].toInt())
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.textDim
                    )
                }
            }
        }
        QuickMenuOrb.RECENT -> {
            game.lastPlayed?.let { lastPlayed ->
                Text(
                    text = stringResource(R.string.ui_quick_menu_recent_last_played, formatRelativeTime(context, lastPlayed)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.textDim
                )
            }
            preview.extras?.saveStatus?.let { SaveStatusLine(it) }
        }
        QuickMenuOrb.FAVORITES, QuickMenuOrb.SEARCH -> PlayStats(
            totalMinutes = game.playTimeMinutes.toLong(),
            sessionCount = null,
            lastPlayed = game.lastPlayed,
            totalLabel = stringResource(R.string.ui_quick_menu_favorites_played),
            lastPlayedLabel = stringResource(R.string.ui_quick_menu_favorites_last_played)
        )
    }
}

@Composable
private fun PlayStats(
    totalMinutes: Long,
    sessionCount: Int?,
    lastPlayed: Instant?,
    totalLabel: String,
    lastPlayedLabel: String
) {
    val context = LocalContext.current
    val total = totalMinutes.takeIf { it > 0 }
    if (total == null && lastPlayed == null) {
        QuickMenuChip(text = stringResource(R.string.ui_quick_menu_preview_never_played))
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXl)) {
        total?.let { StatBlock(value = formatPlayTime(context, it.toInt()), label = totalLabel) }
        sessionCount?.takeIf { it > 0 }?.let {
            StatBlock(value = it.toString(), label = stringResource(R.string.ui_quick_menu_preview_sessions))
        }
        lastPlayed?.let { StatBlock(value = formatRelativeTime(context, it), label = lastPlayedLabel) }
    }
}

@Composable
private fun SaveStatusLine(status: SaveStatusUi) {
    val context = LocalContext.current
    val semantic = LocalLauncherTheme.current.semanticColors
    val theme = LocalArgosyTheme.current
    val savedAt = formatRelativeTime(context, status.savedAt)
    val (textRes, color) = when (status.state) {
        SaveSyncState.SYNCED -> R.string.ui_quick_menu_recent_saved_synced to semantic.success
        SaveSyncState.PENDING -> R.string.ui_quick_menu_recent_saved_pending to semantic.warning
        SaveSyncState.LOCAL_ONLY -> R.string.ui_quick_menu_recent_saved_local to theme.textDim
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.spacingSm)
                .background(color, CircleShape)
        )
        Text(
            text = stringResource(textRes, savedAt),
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textPrimary
        )
    }
}
