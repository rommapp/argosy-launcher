package com.nendo.argosy.ui.components.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.data.model.PlayDay
import com.nendo.argosy.domain.model.MonthlyDigest
import com.nendo.argosy.domain.model.RecapAwardKind
import com.nendo.argosy.ui.common.ChartPalette
import com.nendo.argosy.ui.common.labelRes
import com.nendo.argosy.ui.common.tierColor
import com.nendo.argosy.ui.components.GameTitle
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.playtime.PlayShareBand
import com.nendo.argosy.ui.components.playtime.ShareEntry
import com.nendo.argosy.ui.components.playtime.SummaryFact
import com.nendo.argosy.ui.components.playtime.buildShareSegments
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.primitives.GlassPanel
import com.nendo.argosy.ui.primitives.RuledSectionHeader
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.util.formatMonthDay
import com.nendo.argosy.util.formatMonthName
import com.nendo.argosy.util.formatMonthYear
import java.time.YearMonth
import java.time.ZoneId

private const val TOP_GAMES = 3
private const val SHORT_TOP_GAMES = 2

@Composable
fun MonthlyRecapCard(
    state: MonthlyRecapCardState,
    cursor: RecapCursor,
    onClose: () -> Unit,
    onCurrent: (Int) -> Unit
) {
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        val short = RecapLayout.isShort(maxHeight.value)
        RecapCardFrame(
            maxWidth = ComponentDefaults.UnlockRecap.monthlyCardMaxWidthDp.dp,
            cursor = cursor,
            header = { MonthHeading(state.month) },
            actions = { DoneButton(onClick = onClose) }
        ) {
            when (state) {
                is MonthlyRecapCardState.Loading ->
                    StatusLine(stringResource(R.string.ui_monthly_recap_loading, formatMonthName(state.month)))
                is MonthlyRecapCardState.Empty ->
                    StatusLine(stringResource(R.string.ui_monthly_recap_empty, formatMonthName(state.month)))
                is MonthlyRecapCardState.Failed ->
                    StatusLine(stringResource(R.string.ui_monthly_recap_failed, formatMonthName(state.month)))
                is MonthlyRecapCardState.Ready -> DigestContent(state.digest, cursor, short, onCurrent)
            }
        }
    }
}

/**
 * The card the surface not holding [MonthlyRecapCard] shows: the month, its play time and session
 * facts, and the close action.
 */
@Composable
fun MonthlyRecapCompactCard(state: MonthlyRecapCardState, onClose: () -> Unit) {
    GlassPanel(
        modifier = Modifier
            .padding(Dimens.spacingLg)
            .widthIn(max = ComponentDefaults.UnlockRecap.cardMaxWidthDp.dp)
            .fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(Dimens.spacingLg)) {
            MonthHeading(state.month)
            val digest = (state as? MonthlyRecapCardState.Ready)?.digest
            if (digest != null && digest.sessionCount > 0) {
                Spacer(modifier = Modifier.height(Dimens.spacingMd))
                HeroTime(digest)
                Spacer(modifier = Modifier.height(Dimens.spacingMd))
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg)) {
                    SessionsFact(digest)
                    DaysFact(digest)
                }
            }
            Spacer(modifier = Modifier.height(Dimens.spacingLg))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                DoneButton(onClick = onClose)
            }
        }
    }
}

@Composable
private fun MonthHeading(month: YearMonth) {
    val theme = LocalArgosyTheme.current
    Text(
        text = stringResource(R.string.ui_monthly_recap_eyebrow),
        style = MaterialTheme.typography.labelMedium,
        color = theme.textDim
    )
    Spacer(modifier = Modifier.height(Dimens.spacingXs))
    Text(
        text = formatMonthYear(month),
        style = MaterialTheme.typography.headlineSmall,
        color = theme.textPrimary
    )
}

@Composable
private fun DoneButton(onClick: () -> Unit) {
    ActionButton(
        label = stringResource(R.string.ui_monthly_recap_done),
        onClick = onClick,
        primary = true,
        glyph = InputButton.A
    )
}

@Composable
private fun StatusLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = LocalArgosyTheme.current.textDim,
        modifier = Modifier.padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingLg)
    )
}

@Composable
private fun DigestContent(digest: MonthlyDigest, cursor: RecapCursor, short: Boolean, onCurrent: (Int) -> Unit) {
    if (digest.sessionCount > 0) {
        Spacer(modifier = Modifier.height(Dimens.spacingLg))
        TopBlock(digest, short)
    }
    if (digest.topGames.isNotEmpty()) {
        SectionHeading(stringResource(R.string.ui_monthly_recap_top_games_heading))
        TopGamesBand(digest, short)
    }
    digest.music?.let { music ->
        SectionHeading(stringResource(R.string.music_stats_recap_heading), playTime(music.listenedMs))
        RecapMusicSection(music, short)
    }
    if (digest.unlocks.isNotEmpty() || digest.awards.isNotEmpty()) {
        AchievementsSection(digest, cursor, short, onCurrent)
    }
}

@Composable
private fun SectionHeading(title: String, trailing: String? = null) {
    Spacer(modifier = Modifier.height(Dimens.spacingLg))
    RuledSectionHeader(
        title = title,
        modifier = Modifier.padding(horizontal = Dimens.spacingLg),
        trailing = {
            if (trailing != null) {
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.titleSmall,
                    color = LocalArgosyTheme.current.textDim,
                    maxLines = 1
                )
            }
        }
    )
    Spacer(modifier = Modifier.height(Dimens.spacingSm))
}

@Composable
private fun TopBlock(digest: MonthlyDigest, short: Boolean) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingLg)
    ) {
        if (maxWidth >= ComponentDefaults.UnlockRecap.monthlyStackWidthDp.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg)) {
                Column(modifier = Modifier.weight(1f)) { HeroColumn(digest, short) }
                MonthCalendar(digest)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingLg)) {
                HeroColumn(digest, short)
                MonthCalendar(digest)
            }
        }
    }
}

@Composable
private fun HeroColumn(digest: MonthlyDigest, short: Boolean) {
    val theme = LocalArgosyTheme.current
    val topGame = digest.topGames.firstOrNull()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
    ) {
        if (!short && topGame?.coverPath != null) {
            AsyncImage(
                model = topGame.coverPath,
                contentDescription = topGame.gameTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(
                        ComponentDefaults.UnlockRecap.favoriteCoverWidthDp.dp,
                        ComponentDefaults.UnlockRecap.favoriteCoverHeightDp.dp
                    )
                    .clip(RoundedCornerShape(Dimens.radiusSm))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
        Column {
            HeroTime(digest)
            if (!short && topGame != null) {
                Spacer(modifier = Modifier.height(Dimens.spacingXs))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
                ) {
                    Text(
                        text = stringResource(R.string.ui_monthly_recap_most_played),
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.textDim,
                        maxLines = 1
                    )
                    GameTitle(
                        title = topGame.gameTitle,
                        titleStyle = MaterialTheme.typography.bodyMedium,
                        titleColor = theme.textPrimary,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(Dimens.spacingMd))
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg)) {
        SessionsFact(digest)
        DaysFact(digest)
        SummaryFact(
            label = stringResource(R.string.ui_monthly_recap_stat_longest),
            value = playTime(digest.longestSessionMs),
            detail = null,
            labelStyle = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun HeroTime(digest: MonthlyDigest) {
    val theme = LocalArgosyTheme.current
    Text(
        text = playTime(digest.totalActiveMs),
        style = MaterialTheme.typography.headlineMedium,
        color = theme.textPrimary,
        maxLines = 1
    )
    Text(
        text = stringResource(R.string.ui_monthly_recap_hero_caption, formatMonthName(digest.month)),
        style = MaterialTheme.typography.bodyMedium,
        color = theme.textDim,
        maxLines = 1
    )
}

@Composable
private fun SessionsFact(digest: MonthlyDigest) {
    SummaryFact(
        label = stringResource(R.string.ui_monthly_recap_stat_sessions),
        value = digest.sessionCount.toString(),
        detail = null,
        labelStyle = MaterialTheme.typography.labelLarge
    )
}

@Composable
private fun DaysFact(digest: MonthlyDigest) {
    SummaryFact(
        label = stringResource(R.string.ui_monthly_recap_stat_days),
        value = stringResource(R.string.ui_monthly_recap_days_value, digest.daysPlayed, digest.days.size),
        detail = null,
        labelStyle = MaterialTheme.typography.labelLarge
    )
}

@Composable
private fun MonthCalendar(digest: MonthlyDigest) {
    val busiest = digest.busiestDayIndex
    val gap = ComponentDefaults.UnlockRecap.calendarGapDp.toFloat()
    BoxWithConstraints {
        val cell = MonthGridLayout.cellDp(
            availableDp = maxWidth.value,
            gapDp = gap,
            minDp = ComponentDefaults.UnlockRecap.calendarCellMinDp.toFloat(),
            maxDp = ComponentDefaults.UnlockRecap.calendarCellMaxDp.toFloat()
        )
        Column(modifier = Modifier.width(MonthGridLayout.widthDp(cell, gap).dp)) {
            MonthPlayGrid(
                month = digest.month,
                days = digest.days,
                busiestIndex = busiest,
                cellSize = cell.dp
            )
            busiest?.let { BusiestDayCaption(digest.days[it]) }
        }
    }
}

@Composable
private fun BusiestDayCaption(day: PlayDay) {
    Spacer(modifier = Modifier.height(Dimens.spacingXs))
    Text(
        text = stringResource(
            R.string.ui_monthly_recap_busiest_day,
            formatMonthDay(day.date.atStartOfDay(ZoneId.systemDefault()).toInstant()),
            playTime(day.activeMs)
        ),
        style = MaterialTheme.typography.bodySmall,
        color = LocalArgosyTheme.current.textDim,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun TopGamesBand(digest: MonthlyDigest, short: Boolean) {
    val theme = LocalArgosyTheme.current
    val context = LocalContext.current
    val newBadge = stringResource(R.string.ui_monthly_recap_top_games_new)
    val othersLabel = stringResource(R.string.ui_monthly_recap_top_games_others)
    val segments = remember(digest.topGames, short, theme.isDark, newBadge, othersLabel, context) {
        buildShareSegments(
            entries = digest.topGames.map { game ->
                ShareEntry(
                    label = game.gameTitle,
                    activeMs = game.activeMs,
                    badge = newBadge.takeIf { game.isNewFavorite }
                )
            },
            limit = if (short) SHORT_TOP_GAMES else TOP_GAMES,
            series = ChartPalette.series(theme.isDark),
            othersLabel = othersLabel,
            othersColor = theme.textMute,
            valueLabel = { ms -> RecapPlayTime.of(context, ms) },
            shareLabel = { percent -> context.getString(R.string.ui_monthly_recap_share_percent, percent) }
        )
    }
    PlayShareBand(
        title = null,
        segments = segments,
        modifier = Modifier.padding(horizontal = Dimens.spacingLg)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AchievementsSection(
    digest: MonthlyDigest,
    cursor: RecapCursor,
    short: Boolean,
    onCurrent: (Int) -> Unit
) {
    val masteries = digest.awards.count { it.kind == RecapAwardKind.MASTERED }
    val unlocked = pluralStringResource(R.plurals.ui_monthly_recap_unlocked_count, digest.unlocks.size, digest.unlocks.size)
    val trailing = if (masteries > 0) {
        stringResource(
            R.string.ui_monthly_recap_achievements_trailing,
            unlocked,
            pluralStringResource(R.plurals.ui_monthly_recap_mastery_count, masteries, masteries)
        )
    } else {
        unlocked
    }
    SectionHeading(stringResource(R.string.ui_monthly_recap_achievements_heading), trailing)
    val spotlight = RecapSpotlight.of(digest.unlocks, cursor.currentIndex)
    if (spotlight != null) {
        UnlockRevealRow(
            unlocks = digest.unlocks,
            currentIndex = spotlight.position - 1,
            scrubs = cursor.scrubs,
            onCurrent = onCurrent,
            short = short,
            revealOnVisible = true
        )
        Spacer(modifier = Modifier.height(Dimens.spacingSm))
        UnlockDetailPanel(spotlight, short, showGame = true)
    }
    if (digest.awards.isNotEmpty()) {
        Spacer(modifier = Modifier.height(Dimens.spacingMd))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spacingLg)
        ) {
            digest.awards.forEach { award ->
                RecapChip(
                    text = stringResource(
                        R.string.ui_monthly_recap_award_line,
                        stringResource(award.kind.labelRes),
                        award.gameTitle
                    ),
                    color = award.kind.tierColor
                )
            }
        }
    }
}

@Composable
private fun playTime(ms: Long): String = RecapPlayTime.of(LocalContext.current, ms)
