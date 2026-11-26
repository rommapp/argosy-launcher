package com.nendo.argosy.ui.screens.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.MusicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.MusicColumn
import com.nendo.argosy.domain.model.MusicColumnKind
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
    onPlayColumn: () -> Unit,
    onMoveTrack: (Int) -> Unit,
    onEndMove: () -> Unit,
    modifier: Modifier = Modifier,
    split: Boolean = false
) {
    val backdrop = if (contents.isFlat) {
        contents.column.heroArt ?: contents.column.sleeves.firstOrNull()
    } else {
        contents.focusedAlbum?.art
    }
    Box(modifier = modifier.fillMaxSize()) {
        if (!split) backdrop?.let { BlurredHero(it) }
        Column(Modifier.fillMaxSize()) {
            ContentsHeader(
                column = contents.column,
                showsPlayColumn = contents.canPlayColumn,
                onBack = onBack,
                onPlayColumn = onPlayColumn,
                albumStepper = if (split && contents.steppable) {
                    { AlbumStepper(contents = contents, onSelectAlbum = onSelectAlbum) }
                } else {
                    null
                }
            )
            when {
                contents.isLoading -> MusicLoadingState()
                contents.loadFailed -> MusicMessageState(
                    icon = Icons.Outlined.CloudOff,
                    title = stringResource(R.string.music_library_contents_error),
                    message = null,
                    actionLabel = stringResource(R.string.music_library_error_retry),
                    onAction = onRetry
                )
                split -> SplitContentsList(
                    contents = contents,
                    playingTrackId = playingTrackId,
                    onPlayTrack = onPlayTrack,
                    onTrackOptions = onTrackOptions,
                    onMoveTrack = onMoveTrack,
                    onEndMove = onEndMove
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
                            onTrackOptions = onTrackOptions,
                            onMoveTrack = onMoveTrack,
                            onEndMove = onEndMove
                        )
                    }
                }
            }
        }
    }
}

private val MusicContentsUiState.steppable: Boolean get() = !isFlat && albums.size > 1

@Composable
private fun SplitContentsList(
    contents: MusicContentsUiState,
    playingTrackId: String?,
    onPlayTrack: (Int) -> Unit,
    onTrackOptions: (Int) -> Unit,
    onMoveTrack: (Int) -> Unit,
    onEndMove: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth / maxHeight >= ComponentDefaults.HomeColumns.wideAspectMin
        val widthFraction = if (wide) ComponentDefaults.MusicColumns.contentsReadingWidthRatio else 1f
        Column(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(widthFraction)
                .padding(start = Dimens.spacingLg)
        ) {
            ContentsHeading(contents)
            TrackList(
                contents = contents,
                playingTrackId = playingTrackId,
                onPlayTrack = onPlayTrack,
                onTrackOptions = onTrackOptions,
                onMoveTrack = onMoveTrack,
                onEndMove = onEndMove,
                bottomClearance = Dimens.spacingLg
            )
        }
    }
}

@Composable
private fun AlbumStepper(
    contents: MusicContentsUiState,
    onSelectAlbum: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val albums = contents.albums
    val index = contents.albumIndex
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
        modifier = modifier
    ) {
        AlbumStepArrow(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            description = stringResource(R.string.music_library_contents_previous_album),
            onClick = { onSelectAlbum((index - 1).mod(albums.size)) }
        )
        Text(
            text = stringResource(R.string.music_library_contents_position, index + 1, albums.size),
            style = MaterialTheme.typography.labelLarge,
            color = LocalArgosyTheme.current.textDim
        )
        AlbumStepArrow(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            description = stringResource(R.string.music_library_contents_next_album),
            onClick = { onSelectAlbum((index + 1).mod(albums.size)) }
        )
    }
}

@Composable
private fun ContentsHeader(
    column: MusicColumn,
    showsPlayColumn: Boolean,
    onBack: () -> Unit,
    onPlayColumn: () -> Unit,
    albumStepper: (@Composable () -> Unit)? = null
) {
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
            modifier = Modifier
                .weight(1f)
                .padding(start = Dimens.spacingSm)
        )
        albumStepper?.let { stepper ->
            Box(modifier = Modifier.padding(end = Dimens.spacingMd)) { stepper() }
        }
        if (showsPlayColumn) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
                modifier = Modifier
                    .heightIn(min = ComponentDefaults.FrostedSurface.minimumTouchTargetDp.dp)
                    .clip(RoundedCornerShape(Dimens.radiusPill))
                    .background(theme.surfaceRaised)
                    .clickableNoFocus(onClick = onPlayColumn)
                    .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs)
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = theme.textDim,
                    modifier = Modifier.size(Dimens.iconSm)
                )
                Text(
                    text = stringResource(R.string.music_library_contents_play_all),
                    style = MaterialTheme.typography.labelLarge,
                    color = theme.textPrimary
                )
            }
        }
    }
}

@Composable
private fun CoverPane(
    contents: MusicContentsUiState,
    onSelectAlbum: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val albums = contents.albums
    val index = contents.albumIndex
    val column = contents.column
    val isFlat = contents.isFlat
    val (sleeves, titles) = remember(isFlat, column, albums, index) {
        if (isFlat) {
            column.sleeves to column.sleeveTitles
        } else {
            val stack = albums.drop(index).take(STACK_DEPTH)
            stack.mapNotNull { it.art } to stack.filter { it.art != null }.map { it.title }
        }
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
        if (contents.steppable) {
            AlbumStepper(
                contents = contents,
                onSelectAlbum = onSelectAlbum,
                modifier = Modifier.padding(bottom = withNavBarClearance(Dimens.footerHeight + Dimens.spacingMd))
            )
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
    onTrackOptions: (Int) -> Unit,
    onMoveTrack: (Int) -> Unit,
    onEndMove: () -> Unit,
    bottomClearance: Dp = Dimens.footerHeight + Dimens.spacingLg
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
                    bottom = withNavBarClearance(bottomClearance)
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
                modifier = Modifier.fillMaxSize()
            ) {
                val repeatsAllowed = contents.column.kind == MusicColumnKind.PLAYLIST
                itemsIndexed(
                    contents.tracks,
                    key = { index, track -> if (repeatsAllowed) "$index:${track.key}" else track.key }
                ) { index, track ->
                    val focused = index == contents.trackIndex
                    val lifted = focused && contents.isMoving
                    TrackRow(
                        track = track,
                        number = track.trackNumber ?: (index + 1),
                        showArtist = contents.isFlat || track.artist != contents.focusedAlbum?.artist,
                        isFocused = focused,
                        isLifted = lifted,
                        isPlaying = track.key == playingTrackId,
                        canMoveUp = index > 0,
                        canMoveDown = index < contents.tracks.lastIndex,
                        onClick = {
                            when {
                                lifted -> onEndMove()
                                contents.isMoving -> Unit
                                else -> onPlayTrack(index)
                            }
                        },
                        onLongClick = { if (!contents.isMoving) onTrackOptions(index) },
                        onMoveUp = { onMoveTrack(-1) },
                        onMoveDown = { onMoveTrack(1) }
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
    isLifted: Boolean,
    isPlaying: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.menuRowHeight)
            .argosyFocusIndicators(
                focused = isFocused,
                indicators = if (isLifted) LiftedTrackIndicators else FocusIndicators.ListRow,
                tint = if (isLifted) theme.focusAccent else theme.textPrimary,
                shape = shape
            )
            .argosyFocusIndicators(
                focused = false,
                selected = isPlaying,
                indicators = PlayingTrackStripe,
                stripeColor = theme.focusAccent,
                stripeWidth = Dimens.borderMedium
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
                tint = theme.focusAccent,
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
        if (isLifted) {
            MoveArrow(
                icon = Icons.Filled.KeyboardArrowUp,
                description = stringResource(R.string.music_library_contents_move_up),
                enabled = canMoveUp,
                onClick = onMoveUp
            )
            MoveArrow(
                icon = Icons.Filled.KeyboardArrowDown,
                description = stringResource(R.string.music_library_contents_move_down),
                enabled = canMoveDown,
                onClick = onMoveDown
            )
        }
    }
}

private val LiftedTrackIndicators = FocusIndicators(fill = true, ring = true)

private val PlayingTrackStripe = FocusIndicators(stripe = true)

@Composable
private fun MoveArrow(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    val theme = LocalArgosyTheme.current
    Box(
        modifier = Modifier
            .size(ComponentDefaults.FrostedSurface.minimumTouchTargetDp.dp)
            .clickableNoFocus(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) theme.textPrimary else theme.textMute,
            modifier = Modifier.size(Dimens.iconMd)
        )
    }
}
