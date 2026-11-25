package com.nendo.argosy.ui.screens.musicstats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.MusicArt
import com.nendo.argosy.domain.model.MusicMonthTotals
import com.nendo.argosy.domain.music.MusicStatsCalendar
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.primitives.EnumValueControl
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.screens.music.rememberMusicArtModel
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.theme.reducedMotion
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.util.formatMonthYear

@Composable
fun MusicStatsMonthHeader(
    yearMonth: Int,
    totals: MusicMonthTotals?,
    isFocused: Boolean,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val separator = stringResource(R.string.music_library_separator)
    Column(modifier = modifier.padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm)) {
        EnumValueControl(
            value = formatMonthYear(MusicStatsCalendar.toYearMonth(yearMonth)),
            focused = isFocused,
            onPrev = { onStep(-1) },
            onNext = { onStep(1) },
            onOpen = {},
            valueColor = theme.textPrimary
        )
        if (totals != null && totals.playCount + totals.trackCount > 0) {
            Text(
                text = musicStatsListened(totals.listenedMs),
                style = MaterialTheme.typography.headlineMedium,
                color = theme.textPrimary,
                maxLines = 1,
                modifier = Modifier.padding(top = Dimens.spacingSm)
            )
            Text(
                text = listOf(
                    pluralStringResource(R.plurals.music_stats_count_plays, totals.playCount, totals.playCount),
                    pluralStringResource(R.plurals.music_stats_count_tracks, totals.trackCount, totals.trackCount),
                    pluralStringResource(R.plurals.music_stats_count_days, totals.activeDays, totals.activeDays)
                ).joinToString(separator),
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun MusicStatsShelfRow(
    shelf: MusicStatsShelf,
    yearMonth: Int,
    isFocused: Boolean,
    focusedItem: Int,
    onTap: (Int) -> Unit,
    onLongPress: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(vertical = Dimens.spacingSm)) {
        ShelfHeading(shelf, yearMonth, isFocused)
        Row(verticalAlignment = Alignment.Top) {
            shelf.coverPath?.let { GameCover(it) }
            val listState = rememberLazyListState()
            val reduced = reducedMotion()
            LaunchedEffect(isFocused, focusedItem) {
                if (isFocused) listState.animateScrollToItemCentered(focusedItem, reduced)
            }
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = Dimens.spacingLg),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
            ) {
                when (shelf.kind) {
                    MusicStatsShelfKind.TRACKS -> itemsIndexed(shelf.tracks, key = { _, entry -> entry.stat.groupKey }) { index, track ->
                        CoverTile(
                            art = track.art,
                            title = track.stat.title,
                            subtitle = track.stat.artistCredit ?: track.stat.trackGameTitle,
                            size = ComponentDefaults.MusicStats.trackTileDp.dp,
                            isFocused = isFocused && index == focusedItem,
                            onTap = { onTap(index) },
                            onLongPress = { onLongPress(index) }
                        )
                    }
                    MusicStatsShelfKind.ALBUMS -> itemsIndexed(shelf.albums, key = { _, entry -> entry.stat.albumKey }) { index, album ->
                        CoverTile(
                            art = album.album?.art,
                            title = album.album?.title ?: album.stat.title ?: album.stat.trackGameTitle.orEmpty(),
                            subtitle = album.album?.artist ?: album.stat.artistCredit,
                            size = ComponentDefaults.MusicStats.albumTileDp.dp,
                            isFocused = isFocused && index == focusedItem,
                            onTap = { onTap(index) },
                            onLongPress = { onLongPress(index) }
                        )
                    }
                    MusicStatsShelfKind.ARTISTS -> itemsIndexed(shelf.artists, key = { _, entry -> entry.artistKey }) { index, artist ->
                        ArtistTile(
                            name = artist.name,
                            listenedMs = artist.listenedMs,
                            isFocused = isFocused && index == focusedItem,
                            onTap = { onTap(index) },
                            onLongPress = { onLongPress(index) }
                        )
                    }
                    MusicStatsShelfKind.MONTH -> Unit
                }
            }
        }
    }
}

@Composable
private fun ShelfHeading(shelf: MusicStatsShelf, yearMonth: Int, isFocused: Boolean) {
    val theme = LocalArgosyTheme.current
    val kicker = (shelf.title as? MusicStatsShelfTitle.Pairing)?.kind?.kickerRes
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Dimens.spacingLg, end = Dimens.spacingLg, bottom = Dimens.spacingXs)
    ) {
        Column(Modifier.weight(1f)) {
            if (kicker != null) {
                Text(
                    text = stringResource(kicker),
                    style = MaterialTheme.typography.labelMedium,
                    color = theme.textMute,
                    maxLines = 1
                )
            }
            Text(
                text = musicStatsShelfTitle(shelf.title, yearMonth),
                style = MaterialTheme.typography.titleMedium,
                color = if (isFocused) theme.textPrimary else theme.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (shelf.listenedMs > 0) {
            Text(
                text = musicStatsListened(shelf.listenedMs),
                style = MaterialTheme.typography.labelLarge,
                color = theme.textDim,
                maxLines = 1,
                modifier = Modifier.padding(start = Dimens.spacingSm)
            )
        }
    }
}

@Composable
private fun GameCover(coverPath: String) {
    val scale = LocalUiScale.current.scale
    AsyncImage(
        model = coverPath,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .padding(start = Dimens.spacingLg)
            .size(
                ComponentDefaults.MusicStats.gameCoverWidthDp.dp * scale,
                ComponentDefaults.MusicStats.gameCoverHeightDp.dp * scale
            )
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(LocalArgosyTheme.current.surfaceRaised)
    )
}

@Composable
private fun CoverTile(
    art: MusicArt?,
    title: String,
    subtitle: String?,
    size: Dp,
    isFocused: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val width = size * LocalUiScale.current.scale
    val shape = RoundedCornerShape(Dimens.radiusSm)
    Column(
        modifier = Modifier
            .width(width)
            .clickableNoFocus(onClick = onTap, onLongClick = onLongPress)
    ) {
        TileArt(
            art = art,
            fallback = Icons.Filled.MusicNote,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .argosyFocusIndicators(focused = isFocused, indicators = FocusIndicators.Ring, shape = shape)
                .clip(shape)
        )
        TileText(title = title, subtitle = subtitle, isFocused = isFocused)
    }
}

@Composable
private fun ArtistTile(
    name: String,
    listenedMs: Long,
    isFocused: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        modifier = Modifier
            .width(ComponentDefaults.MusicStats.artistTileWidthDp.dp * LocalUiScale.current.scale)
            .clip(shape)
            .background(theme.surfaceRaised)
            .argosyFocusIndicators(focused = isFocused, indicators = FocusIndicators.ListRow, tint = theme.textPrimary, shape = shape)
            .clickableNoFocus(onClick = onTap, onLongClick = onLongPress)
            .padding(Dimens.spacingSm)
    ) {
        Icon(
            imageVector = Icons.Filled.Person,
            contentDescription = null,
            tint = theme.textMute,
            modifier = Modifier.size(Dimens.iconMd)
        )
        TileText(title = name, subtitle = musicStatsListened(listenedMs), isFocused = isFocused)
    }
}

@Composable
private fun TileArt(art: MusicArt?, fallback: ImageVector, modifier: Modifier) {
    val theme = LocalArgosyTheme.current
    val model = rememberMusicArtModel(art)
    Box(modifier = modifier.background(theme.surfaceRaised), contentAlignment = Alignment.Center) {
        if (model == null) {
            Icon(
                imageVector = fallback,
                contentDescription = null,
                tint = theme.textMute,
                modifier = Modifier.size(Dimens.iconLg)
            )
        } else {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun TileText(title: String, subtitle: String?, isFocused: Boolean) {
    val theme = LocalArgosyTheme.current
    Column(modifier = Modifier.padding(top = Dimens.spacingXs)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isFocused) theme.textPrimary else theme.textDim,
            maxLines = ComponentDefaults.MusicStats.tileTitleMaxLines,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = theme.textMute,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
