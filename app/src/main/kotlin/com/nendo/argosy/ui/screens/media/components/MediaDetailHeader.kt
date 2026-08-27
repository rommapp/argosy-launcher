package com.nendo.argosy.ui.screens.media.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.ui.screens.media.MediaItemUi
import com.nendo.argosy.ui.screens.media.episodeLabel
import com.nendo.argosy.ui.screens.media.runtimeLabel
import com.nendo.argosy.ui.theme.AspectRatioClass
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.generated.ComponentDefaults

/**
 * The detail header in its full form. It collapses into [MediaStickyCollapsedHeader] on scroll, so
 * the title stays on screen once the poster has left it.
 */
@Composable
fun MediaExpandedHeader(item: MediaItemUi, modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    val isWide = LocalUiScale.current.aspectRatioClass.let {
        it == AspectRatioClass.WIDE || it == AspectRatioClass.ULTRA_WIDE
    }
    Row(
        modifier = modifier.fillMaxWidth().padding(Dimens.spacingLg),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg)
    ) {
        Box(
            modifier = Modifier
                .width(Dimens.mediaPosterWidth)
                .height(Dimens.mediaPosterHeight)
                .clip(RoundedCornerShape(Dimens.radiusMd))
                .background(theme.surfaceRaised)
        ) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        val context = LocalContext.current
        val seriesName = item.seriesName?.takeIf { item.episodeLabel(context) != null }
        val overview = item.overview
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
            if (seriesName != null) {
                Text(
                    text = seriesName,
                    style = MaterialTheme.typography.labelLarge,
                    color = theme.focusAccent
                )
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.headlineSmall,
                color = theme.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            MediaMetadataChips(item = item)
            if (overview != null) {
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.textDim,
                    maxLines = if (isWide) OVERVIEW_LINES_WIDE else OVERVIEW_LINES_COMPACT,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun MediaStickyCollapsedHeader(
    item: MediaItemUi,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    AnimatedVisibility(
        visible = isVisible,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.headerHeight)
                .background(
                    theme.surfaceRaised.copy(alpha = ComponentDefaults.MediaBackdrop.surfaceAlpha)
                )
                .padding(horizontal = Dimens.spacingLg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
        ) {
            Box(
                modifier = Modifier
                    .size(Dimens.avatarLg)
                    .clip(RoundedCornerShape(Dimens.radiusSm))
                    .background(theme.surfaceElevated)
            ) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * The facts about a title, then what kind of title it is.
 *
 * Two runs rather than one: a year, a runtime and a certificate answer different questions than a
 * genre list does, and a title carrying five genres swamps them when everything shares a line. The
 * certificate is boxed because it is an authority's mark rather than a description, and the score
 * is starred so a bare number is not left to explain itself.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaMetadataChips(item: MediaItemUi, modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    val context = LocalContext.current
    val seasonCount = item.childCount?.takeIf { item.isSeries && it > 0 }
    val seasonChip = seasonCount?.let {
        pluralStringResource(R.plurals.media_detail_chip_season_count, it, it)
    }
    val facts = buildList {
        item.year?.let { add(it.toString()) }
        item.runtimeLabel(context)?.let { add(it) }
        seasonChip?.let { add(it) }
    }
    val genres = item.genres?.takeIf { it.isNotBlank() }
    if (facts.isEmpty() && genres == null && item.officialRating == null &&
        item.communityRating == null
    ) {
        return
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            facts.forEachIndexed { index, fact ->
                if (index > 0) MetadataSeparator()
                Text(
                    text = fact,
                    style = MaterialTheme.typography.labelMedium,
                    color = theme.textDim,
                    maxLines = 1
                )
            }
            item.officialRating?.let { rating ->
                Text(
                    text = rating,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textDim,
                    maxLines = 1,
                    modifier = Modifier
                        .border(
                            width = Dimens.borderThin,
                            color = theme.hairlineLow,
                            shape = RoundedCornerShape(Dimens.radiusSm)
                        )
                        .padding(horizontal = Dimens.spacingXs)
                )
            }
            item.communityRating?.let { rating ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = theme.focusAccent,
                        modifier = Modifier.size(Dimens.iconXs)
                    )
                    Text(
                        text = "%.1f".format(rating),
                        style = MaterialTheme.typography.labelMedium,
                        color = theme.textDim,
                        maxLines = 1
                    )
                }
            }
        }

        genres?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = theme.textMute,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MetadataSeparator() {
    Text(
        text = "·",
        style = MaterialTheme.typography.labelMedium,
        color = LocalArgosyTheme.current.textMute
    )
}

private const val OVERVIEW_LINES_WIDE = 5
private const val OVERVIEW_LINES_COMPACT = 3
