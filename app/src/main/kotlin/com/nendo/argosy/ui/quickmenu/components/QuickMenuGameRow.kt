package com.nendo.argosy.ui.quickmenu.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.parseGameTitle
import com.nendo.argosy.ui.primitives.ArgosyProgressBar
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.quickmenu.QuickMenuGameUi
import com.nendo.argosy.ui.screens.home.GameDownloadIndicator
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * One game in a quick menu list: a 52 dp row with a 3:4 thumbnail and two lines. [fraction] draws
 * the hours bar on line two; [trailingText] sits at the end of the title line.
 */
@Composable
fun QuickMenuGameRow(
    game: QuickMenuGameUi,
    isFocused: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    downloadIndicator: GameDownloadIndicator = GameDownloadIndicator.NONE,
    rank: Int? = null,
    meta: String? = null,
    fraction: Float? = null,
    trailingText: String? = null,
    runningChip: String? = null
) {
    val theme = LocalArgosyTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.menuRowHeightLg)
            .argosyFocusIndicators(
                focused = isFocused,
                indicators = FocusIndicators(fill = true, ring = true),
                shape = RoundedCornerShape(Dimens.radiusControl)
            )
            .clickableNoFocus(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        if (rank != null) {
            Text(
                text = stringResource(R.string.ui_quick_menu_most_played_rank_short, rank),
                style = MaterialTheme.typography.labelLarge,
                color = if (isFocused) theme.focusAccent else theme.textDim,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.width(Dimens.iconMd)
            )
        }
        QuickMenuCover(
            path = game.coverPath,
            title = game.title,
            showTitle = false,
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(ComponentDefaults.QuickMenuRandom.coverAspectRatio, matchHeightConstraintsFirst = true)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs, Alignment.CenterVertically)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                val dim = theme.textDim
                val title = remember(game.title, dim) { seriesStyledTitle(game.title, dim) }
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isFocused) theme.focusAccent else theme.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (trailingText != null) {
                    Text(
                        text = trailingText,
                        style = MaterialTheme.typography.labelLarge,
                        color = theme.textPrimary,
                        textAlign = TextAlign.End,
                        maxLines = 1
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickMenuChip(
                    text = game.platformName ?: stringResource(R.string.ui_quick_menu_row_platform_unknown)
                )
                if (fraction != null) {
                    ArgosyProgressBar(progress = fraction, modifier = Modifier.weight(1f))
                } else if (meta != null) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (runningChip != null) {
            QuickMenuChip(text = runningChip, tone = ChipTone.RUNNING)
        }
        NotDownloadedMark(game = game, indicator = downloadIndicator)
    }
}

private fun seriesStyledTitle(title: String, seriesColor: Color): AnnotatedString {
    val parsed = parseGameTitle(title)
    val entryStart = if (parsed.seriesName == null) 0 else title.lastIndexOf(parsed.gameName).coerceAtLeast(0)
    return buildAnnotatedString {
        append(title)
        if (entryStart > 0) {
            addStyle(SpanStyle(color = seriesColor, fontWeight = FontWeight.Normal), 0, entryStart)
            addStyle(SpanStyle(fontWeight = FontWeight.Medium), entryStart, title.length)
        }
    }
}
