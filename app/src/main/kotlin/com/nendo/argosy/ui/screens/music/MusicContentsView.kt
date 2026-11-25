package com.nendo.argosy.ui.screens.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.MusicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.MusicArt
import com.nendo.argosy.domain.model.MusicColumn
import com.nendo.argosy.domain.model.MusicTrack
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.components.withNavBarClearance
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.theme.reducedMotion
import com.nendo.argosy.ui.util.clickableNoFocus

@Composable
fun MusicContentsView(
    contents: MusicContentsUiState,
    playingTrackId: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSelectAlbum: (Int) -> Unit,
    onPlayTrack: (Int) -> Unit,
    onTrackOptions: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val backdrop = if (contents.isFlat) {
        contents.column.heroArt ?: contents.column.sleeves.firstOrNull()
    } else {
        contents.focusedAlbum?.art
    }
    Box(modifier = modifier.fillMaxSize()) {
        backdrop?.let { BlurredHero(it) }
        Column(Modifier.fillMaxSize()) {
            ContentsHeader(column = contents.column, onBack = onBack)
            when {
                contents.isLoading -> MusicLoadingState()
                contents.loadFailed -> MusicMessageState(
                    icon = Icons.Outlined.CloudOff,
                    title = stringResource(R.string.music_library_contents_error),
                    message = null,
                    actionLabel = stringResource(R.string.music_library_error_retry),
                    onAction = onRetry
                )
                else -> Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = Dimens.spacingLg),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXl)
                ) {
                    val coverWeight = ComponentDefaults.MusicColumns.contentsCoverWeightRatio
                    CoverPane(
                        contents = contents,
                        onSelectAlbum = onSelectAlbum,
                        modifier = Modifier
                            .weight(coverWeight)
                            .fillMaxHeight()
                    )
                    Column(
                        Modifier
                            .weight(1f - coverWeight)
                            .fillMaxHeight()
                    ) {
                        ContentsHeading(contents)
                        TrackList(
                            contents = contents,
                            playingTrackId = playingTrackId,
                            onPlayTrack = onPlayTrack,
                            onTrackOptions = onTrackOptions
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentsHeader(column: MusicColumn, onBack: () -> Unit) {
    val theme = LocalArgosyTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(ComponentDefaults.FrostedSurface.minimumTouchTargetDp.dp)
                .clickableNoFocus(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.music_library_contents_back_desc),
                tint = theme.textDim,
                modifier = Modifier.size(Dimens.iconMd)
            )
        }
        Text(
            text = musicColumnTitle(column).ifEmpty { stringResource(column.kind.kickerRes) },
            style = MaterialTheme.typography.titleMedium,
            color = theme.textDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = Dimens.spacingSm)
        )
    }
}

@Composable
private fun CoverPane(
    contents: MusicContentsUiState,
    onSelectAlbum: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val albums = contents.albums
    val index = contents.albumIndex
    val sleeves: List<MusicArt>
    val titles: List<String>
    if (contents.isFlat) {
        sleeves = contents.column.sleeves
        titles = contents.column.sleeveTitles
    } else {
        val stack = albums.drop(index).take(STACK_DEPTH)
        sleeves = stack.mapNotNull { it.art }
        titles = stack.filter { it.art != null }.map { it.title }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (sleeves.isNotEmpty()) {
                MusicSleeveStack(
                    sleeves = sleeves,
                    titles = titles,
                    hiddenCount = 0,
                    sizeRatio = ComponentDefaults.MusicColumns.contentsSleeveRatio,
                    centerBias = ComponentDefaults.MusicColumns.contentsSleeveBiasRatio
                )
            }
        }
        if (!contents.isFlat && albums.size > 1) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
                modifier = Modifier.padding(bottom = withNavBarClearance(Dimens.footerHeight + Dimens.spacingMd))
            ) {
                AlbumStepArrow(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    description = stringResource(R.string.music_library_contents_previous_album),
                    onClick = { onSelectAlbum((index - 1).mod(albums.size)) }
                )
                Text(
                    text = stringResource(R.string.music_library_contents_position, index + 1, albums.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = theme.textDim
                )
                AlbumStepArrow(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    description = stringResource(R.string.music_library_contents_next_album),
                    onClick = { onSelectAlbum((index + 1).mod(albums.size)) }
                )
            }
        }
    }
}

@Composable
private fun AlbumStepArrow(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(ComponentDefaults.FrostedSurface.minimumTouchTargetDp.dp)
            .clickableNoFocus(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = LocalArgosyTheme.current.textDim,
            modifier = Modifier.size(Dimens.iconMd)
        )
    }
}

@Composable
private fun ContentsHeading(contents: MusicContentsUiState) {
    val theme = LocalArgosyTheme.current
    val separator = stringResource(R.string.music_library_separator)
    val album = contents.focusedAlbum
    val title = album?.title ?: musicColumnTitle(contents.column)
    val trackCount = contents.tracks.size
    val durationSec = contents.tracks.sumOf { it.durationSec ?: 0 }
    val facts = buildList {
        album?.artist?.let(::add)
        album?.year?.let { add(it.toString()) }
        if (trackCount > 0) {
            add(pluralStringResource(R.plurals.music_library_count_tracks, trackCount, trackCount))
        }
        if (durationSec > 0) add(formatMusicDuration(durationSec))
    }.joinToString(separator)
    Column(Modifier.padding(end = Dimens.spacingLg, top = Dimens.spacingSm, bottom = Dimens.spacingMd)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = theme.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (facts.isNotEmpty()) {
            Text(
                text = facts,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Dimens.spacingXs)
            )
        }
    }
}

private const val STACK_DEPTH = 3

@Composable
private fun TrackList(
    contents: MusicContentsUiState,
    playingTrackId: String?,
    onPlayTrack: (Int) -> Unit,
    onTrackOptions: (Int) -> Unit
) {
    when {
        contents.tracksLoading -> MusicLoadingState()
        contents.tracks.isEmpty() -> MusicMessageState(
            icon = Icons.Outlined.MusicOff,
            title = stringResource(R.string.music_library_contents_empty),
            message = null
        )
        else -> {
            val listState = rememberLazyListState()
            val reduced = reducedMotion()
            LaunchedEffect(contents.trackIndex, contents.tracks) {
                listState.animateScrollToItemCentered(contents.trackIndex, reduced)
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    end = Dimens.spacingLg,
                    top = Dimens.spacingSm,
                    bottom = withNavBarClearance(Dimens.footerHeight + Dimens.spacingLg)
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(contents.tracks, key = { _, track -> track.key }) { index, track ->
                    TrackRow(
                        track = track,
                        number = track.trackNumber ?: (index + 1),
                        showArtist = contents.isFlat || track.artist != contents.focusedAlbum?.artist,
                        isFocused = index == contents.trackIndex,
                        isPlaying = track.key == playingTrackId,
                        onClick = { onPlayTrack(index) },
                        onLongClick = { onTrackOptions(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: MusicTrack,
    number: Int,
    showArtist: Boolean,
    isFocused: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.menuRowHeight)
            .argosyFocusIndicators(
                focused = isFocused,
                indicators = FocusIndicators.ListRow,
                tint = theme.textPrimary,
                shape = shape
            )
            .clickableNoFocus(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val numberWidth = ComponentDefaults.MusicColumns.trackNumberWidthDp.dp * LocalUiScale.current.scale
        if (isPlaying) {
            Icon(
                imageVector = Icons.Filled.GraphicEq,
                contentDescription = stringResource(R.string.music_library_badge_playing),
                tint = theme.textPrimary,
                modifier = Modifier
                    .width(numberWidth)
                    .size(Dimens.iconSm)
            )
        } else {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = theme.textMute,
                modifier = Modifier.width(numberWidth)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isPlaying) FontWeight.SemiBold else null,
                color = theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val artist = track.artist
            if (showArtist && artist != null) {
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        track.durationSec?.let { seconds ->
            Text(
                text = formatMusicDuration(seconds),
                style = MaterialTheme.typography.labelMedium,
                color = theme.textDim,
                modifier = Modifier.padding(start = Dimens.spacingSm)
            )
        }
    }
}
