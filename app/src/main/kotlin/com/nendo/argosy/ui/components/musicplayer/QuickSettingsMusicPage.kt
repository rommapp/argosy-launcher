package com.nendo.argosy.ui.components.musicplayer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.MusicSelection
import com.nendo.argosy.ui.common.backgroundBlurDp
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.components.QuickRowFrame
import com.nendo.argosy.ui.components.QuickSwitchedSliderRow
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.components.quickPercentLabel
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.PanelSectionHeader
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.theme.reducedMotion
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.ui.util.verticalEdgeFade
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import java.util.Locale

private const val LOAD_MORE_LOOKAHEAD = 4
private const val PLAYER_MAX_HEIGHT_FRACTION = 2f / 3f

private val TransportFocus = FocusIndicators(fill = true)
private val PlayFocus = FocusIndicators(ring = true)

@Composable
fun QuickSettingsMusicPage(viewModel: MusicPlayerViewModel, onOpenRommSignIn: (() -> Unit)?) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        while (isActive) {
            viewModel.samplePosition()
            delay(ComponentDefaults.MusicPlayer.positionPollMs.toLong())
        }
    }

    val browse = state.browse
    if (browse == null) {
        MusicPlayerMain(state = state, viewModel = viewModel)
    } else {
        MusicBrowseList(
            browse = browse,
            activeSourceId = state.playback.sourceId,
            viewModel = viewModel,
            onOpenRommSignIn = onOpenRommSignIn
        )
    }
}

@Composable
private fun MusicPlayerMain(state: MusicPlayerUiState, viewModel: MusicPlayerViewModel) {
    val launcherLabel = stringResource(R.string.ui_quick_settings_music_source_launcher)
    val openSource: (MusicBrowseKind) -> Unit = { kind ->
        viewModel.focusSource(kind)
        viewModel.openBrowse(kind)
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val playerMaxHeight = maxHeight * PLAYER_MAX_HEIGHT_FRACTION
        Column(modifier = Modifier.fillMaxSize()) {
            if (state.hasQueue) {
                val coverPath = state.playback.coverPath.takeIf { state.playback.overrideTitle == null }
                val expanded = state.showsFullPlayer && coverPath != null
                Box(modifier = Modifier.heightIn(max = playerMaxHeight)) {
                    if (expanded) CoverBloom(coverPath = coverPath, modifier = Modifier.matchParentSize())
                    Column {
                        AnimatedVisibility(
                            visible = expanded,
                            enter = expandVertically(tween(Motion.durationContent)) +
                                fadeIn(tween(Motion.durationContent)),
                            exit = shrinkVertically(tween(Motion.durationContent)) +
                                fadeOut(tween(Motion.durationContent)),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            ExpandedCover(
                                coverPath = coverPath,
                                modifier = Modifier.padding(
                                    start = Dimens.spacingLg,
                                    end = Dimens.spacingLg,
                                    top = Dimens.spacingSm
                                )
                            )
                        }
                        NowPlayingHeader(state = state, launcherLabel = launcherLabel, expanded = expanded)
                        TransportRow(
                            state = state,
                            onButton = { button ->
                                viewModel.focusTransport(button)
                                viewModel.activateTransport(button)
                            }
                        )
                    }
                }
                QuickSwitchedSliderRow(
                    icon = Icons.Default.MusicNote,
                    label = stringResource(R.string.ui_quick_settings_music_volume),
                    on = state.launcherEnabled,
                    fraction = state.volumeFraction,
                    valueText = quickPercentLabel(state.volumeFraction),
                    isFocused = state.focusedRow == MusicPlayerRow.VOLUME,
                    onFocus = viewModel::focusVolume,
                    onToggle = viewModel::setMusicEnabled,
                    onFractionChange = viewModel::setVolumeFraction
                )
                SourceButtons(state = state, onSelect = openSource)
            } else {
                EmptyNowPlaying()
                SourceTiles(state = state, onSelect = openSource)
            }
            TrackList(
                state = state,
                onPlay = { position -> viewModel.playTrack(position) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CoverBloom(coverPath: String?, modifier: Modifier = Modifier) {
    val model = rememberFileImageModel(coverPath) ?: return
    val theme = LocalArgosyTheme.current
    val bloomAlpha = if (theme.isDark) {
        ComponentDefaults.MusicPlayer.artBloomAlphaDark
    } else {
        ComponentDefaults.MusicPlayer.artBloomAlphaLight
    }
    Box(modifier = modifier) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = bloomAlpha,
            modifier = Modifier
                .fillMaxSize()
                .blur(ComponentDefaults.Background.blur.backgroundBlurDp)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, theme.surfaceBase)))
        )
    }
}

@Composable
private fun EmptyNowPlaying() {
    val theme = LocalArgosyTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingLg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = theme.textMute,
            modifier = Modifier.size(Dimens.iconXl)
        )
        Text(
            text = stringResource(R.string.ui_quick_settings_music_nothing_playing),
            style = MaterialTheme.typography.titleMedium,
            color = theme.textDim
        )
    }
}

private data class MusicSource(
    val kind: MusicBrowseKind,
    val icon: ImageVector,
    val labelRes: Int
)

private val MusicSources = listOf(
    MusicSource(
        MusicBrowseKind.PLAYLISTS,
        Icons.AutoMirrored.Filled.QueueMusic,
        R.string.ui_quick_settings_music_button_playlists
    ),
    MusicSource(MusicBrowseKind.SOUNDTRACKS, Icons.Default.Album, R.string.ui_quick_settings_music_button_soundtracks)
)

private fun MusicPlayerUiState.isSourceFocused(kind: MusicBrowseKind): Boolean =
    focusedRow == MusicPlayerRow.SOURCES && sourceButton == kind

@Composable
private fun SourceButtons(state: MusicPlayerUiState, onSelect: (MusicBrowseKind) -> Unit) {
    val theme = LocalArgosyTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        MusicSources.forEach { source ->
            val focused = state.isSourceFocused(source.kind)
            val tint = if (focused) theme.focusAccent else theme.textPrimary
            ActionButton(
                onClick = { onSelect(source.kind) },
                focused = focused,
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = source.icon, contentDescription = null, tint = tint, modifier = Modifier.size(Dimens.iconSm))
                Spacer(modifier = Modifier.width(Dimens.spacingXs))
                Text(
                    text = stringResource(source.labelRes),
                    style = MaterialTheme.typography.titleSmall,
                    color = tint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = theme.textDim,
                    modifier = Modifier.size(Dimens.iconSm)
                )
            }
        }
    }
}

@Composable
private fun SourceTiles(state: MusicPlayerUiState, onSelect: (MusicBrowseKind) -> Unit) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        MusicSources.forEach { source ->
            val focused = state.isSourceFocused(source.kind)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(theme.surfaceRaised)
                    .argosyFocusIndicators(
                        focused = focused,
                        indicators = FocusIndicators(fill = true, ring = true),
                        shape = shape,
                        ringThickness = Dimens.borderThin
                    )
                    .clickableNoFocus(onClick = { onSelect(source.kind) })
                    .padding(Dimens.spacingMd),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                Icon(
                    imageVector = source.icon,
                    contentDescription = null,
                    tint = if (focused) theme.focusAccent else theme.textDim,
                    modifier = Modifier.size(Dimens.iconLg)
                )
                Text(
                    text = stringResource(source.labelRes),
                    style = MaterialTheme.typography.titleSmall,
                    color = theme.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TrackList(
    state: MusicPlayerUiState,
    onPlay: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tracks = state.playback.tracks
    val listState = rememberLazyListState()
    val tracksFocused = state.focusedRow == MusicPlayerRow.TRACKS
    val anchor = if (tracksFocused) state.trackFocus else state.playback.index
    val reduced = reducedMotion()
    LaunchedEffect(anchor, tracks.size) {
        snapshotFlow { listState.layoutInfo.viewportSize.height }
            .collectLatest { if (anchor in tracks.indices) listState.animateScrollToItemCentered(anchor, reduced) }
    }
    if (tracks.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth()) {
            if (state.hasQueue) BrowseMessage(stringResource(R.string.ui_quick_settings_music_queue_empty))
        }
        return
    }
    val showGame = remember(tracks) { tracks.mapTo(HashSet()) { it.gameTitle }.size > 1 }
    Column(modifier = modifier.fillMaxWidth()) {
        PanelSectionHeader(
            title = stringResource(
                R.string.ui_quick_settings_music_up_next,
                stringResource(
                    R.string.ui_quick_settings_music_track_position,
                    state.playback.index + 1,
                    state.playback.count
                )
            ),
            modifier = Modifier.padding(
                start = Dimens.spacingMd,
                end = Dimens.spacingMd,
                top = Dimens.spacingMd,
                bottom = Dimens.spacingXs
            )
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalEdgeFade(listState, fadeHeight = Dimens.spacingLg)
        ) {
            itemsIndexed(tracks, key = { position, track -> "$position:${track.id}" }) { position, track ->
                TrackRow(
                    number = position + 1,
                    title = track.title,
                    gameTitle = track.gameTitle.takeIf { showGame },
                    isCurrent = position == state.playback.index,
                    isFocused = tracksFocused && position == state.trackFocus,
                    onClick = { onPlay(position) }
                )
            }
        }
    }
}

@Composable
private fun TrackRow(
    number: Int,
    title: String,
    gameTitle: String?,
    isCurrent: Boolean,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    QuickRowFrame(isFocused = isFocused, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(Dimens.iconLg), contentAlignment = Alignment.Center) {
                if (isCurrent) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = stringResource(R.string.ui_quick_settings_music_now_playing),
                        tint = theme.focusAccent,
                        modifier = Modifier.size(Dimens.iconSm)
                    )
                } else {
                    Text(
                        text = number.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textDim,
                        maxLines = 1
                    )
                }
            }
            Spacer(modifier = Modifier.width(Dimens.spacingSm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isCurrent) theme.focusAccent else theme.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (gameTitle != null) {
                    Text(
                        text = gameTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun NowPlayingHeader(state: MusicPlayerUiState, launcherLabel: String, expanded: Boolean) {
    val playback = state.playback
    val overrideTitle = playback.overrideTitle
    val title = overrideTitle ?: playback.trackTitle
        ?: stringResource(R.string.ui_quick_settings_music_nothing_playing)
    val gameTitle = if (overrideTitle == null) playback.gameTitle else null
    val sourceLabel = if (playback.sourceId == MusicSelection.LAUNCHER_ID) {
        launcherLabel
    } else {
        playback.sourceLabel.orEmpty().takeUnless { it == gameTitle }.orEmpty()
    }
    val coverPath = if (overrideTitle == null) playback.coverPath else null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm)
    ) {
        AnimatedContent(
            targetState = expanded,
            transitionSpec = {
                fadeIn(tween(Motion.durationContent)) togetherWith
                    fadeOut(tween(Motion.durationContent)) using
                    SizeTransform(clip = false) { _, _ -> tween(Motion.durationContent) }
            },
            label = "nowPlayingHeader"
        ) { isExpanded ->
            if (isExpanded) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    NowPlayingText(
                        title = title,
                        gameTitle = gameTitle,
                        sourceLabel = sourceLabel,
                        alignment = Alignment.CenterHorizontally
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MusicCover(coverPath = coverPath, size = ComponentDefaults.MusicPlayer.coverSizeDp.dp)
                    Spacer(modifier = Modifier.width(Dimens.spacingMd))
                    NowPlayingText(
                        title = title,
                        gameTitle = gameTitle,
                        sourceLabel = sourceLabel,
                        alignment = Alignment.Start,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(Dimens.spacingSm))
        PlaybackProgress(positionMs = state.positionMs, durationMs = state.durationMs)
    }
}

@Composable
private fun ExpandedCover(coverPath: String?, modifier: Modifier = Modifier) {
    val model = rememberFileImageModel(coverPath) ?: return
    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = ComponentDefaults.MusicPlayer.coverExpandedMaxHeightDp.dp)
            .clip(RoundedCornerShape(Dimens.radiusMd))
    )
}

@Composable
private fun NowPlayingText(
    title: String,
    gameTitle: String?,
    sourceLabel: String,
    alignment: Alignment.Horizontal,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    Column(modifier = modifier, horizontalAlignment = alignment) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = theme.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (gameTitle != null) {
            Text(
                text = gameTitle,
                style = MaterialTheme.typography.bodySmall,
                color = theme.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (sourceLabel.isNotEmpty()) {
            Text(
                text = sourceLabel,
                style = MaterialTheme.typography.labelSmall,
                color = theme.focusAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PlaybackProgress(positionMs: Long, durationMs: Long) {
    val theme = LocalArgosyTheme.current
    val fraction = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shape = RoundedCornerShape(Dimens.radiusSm)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Text(
            text = formatPlaybackTime(positionMs),
            style = MaterialTheme.typography.labelSmall,
            color = theme.textDim
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(ComponentDefaults.MusicPlayer.progressHeightDp.dp)
                .clip(shape)
                .background(theme.surfaceElevated)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(theme.focusAccent)
            )
        }
        Text(
            text = if (durationMs > 0L) formatPlaybackTime(durationMs) else formatPlaybackTime(0L),
            style = MaterialTheme.typography.labelSmall,
            color = theme.textDim
        )
    }
}

@Composable
private fun TransportRow(
    state: MusicPlayerUiState,
    onButton: (MusicTransportButton) -> Unit
) {
    val rowFocused = state.focusedRow == MusicPlayerRow.TRANSPORT
    fun focused(button: MusicTransportButton) = rowFocused && state.transportButton == button
    val buttonSize = ComponentDefaults.MusicPlayer.transportButtonDp.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TransportButton(
            icon = Icons.Default.SkipPrevious,
            description = stringResource(R.string.ui_quick_settings_music_previous),
            size = buttonSize,
            isFocused = focused(MusicTransportButton.PREVIOUS),
            onClick = { onButton(MusicTransportButton.PREVIOUS) }
        )
        PlayPauseButton(
            isPlaying = state.isAudible,
            isFocused = focused(MusicTransportButton.PLAY_PAUSE),
            onClick = { onButton(MusicTransportButton.PLAY_PAUSE) }
        )
        TransportButton(
            icon = Icons.Default.SkipNext,
            description = stringResource(R.string.ui_quick_settings_music_next),
            size = buttonSize,
            isFocused = focused(MusicTransportButton.NEXT),
            onClick = { onButton(MusicTransportButton.NEXT) }
        )
        TransportButton(
            icon = Icons.Default.Shuffle,
            description = stringResource(R.string.ui_quick_settings_music_shuffle),
            size = buttonSize,
            isFocused = focused(MusicTransportButton.SHUFFLE),
            showDot = state.playback.shuffle,
            onClick = { onButton(MusicTransportButton.SHUFFLE) }
        )
        TransportButton(
            icon = Icons.Default.Bedtime,
            description = stringResource(R.string.ui_quick_settings_music_play_in_background),
            size = buttonSize,
            isFocused = focused(MusicTransportButton.BACKGROUND),
            showDot = state.playInBackground,
            onClick = { onButton(MusicTransportButton.BACKGROUND) }
        )
    }
}

@Composable
private fun PlayPauseButton(isPlaying: Boolean, isFocused: Boolean, onClick: () -> Unit) {
    val theme = LocalArgosyTheme.current
    Box(
        modifier = Modifier
            .size(ComponentDefaults.MusicPlayer.playButtonDp.dp)
            .argosyFocusIndicators(
                focused = isFocused,
                indicators = PlayFocus,
                shape = CircleShape,
                ringThickness = Dimens.borderThin
            )
            .clip(CircleShape)
            .background(theme.focusAccent)
            .clickableNoFocus(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = stringResource(
                if (isPlaying) R.string.ui_quick_settings_music_pause else R.string.ui_quick_settings_music_play
            ),
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(Dimens.iconMd)
        )
    }
}

@Composable
private fun TransportButton(
    icon: ImageVector,
    description: String,
    size: Dp,
    isFocused: Boolean,
    onClick: () -> Unit,
    showDot: Boolean = false
) {
    val theme = LocalArgosyTheme.current
    Box(
        modifier = Modifier
            .size(size)
            .argosyFocusIndicators(focused = isFocused, indicators = TransportFocus, shape = CircleShape)
            .clip(CircleShape)
            .clickableNoFocus(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = when {
                isFocused || showDot -> theme.focusAccent
                else -> theme.textPrimary
            },
            modifier = Modifier.size(Dimens.iconMd)
        )
        if (showDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Dimens.spacingXs)
                    .size(Dimens.spacingXs)
                    .clip(CircleShape)
                    .background(theme.focusAccent)
            )
        }
    }
}

@Composable
private fun MusicCover(coverPath: String?, size: Dp) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusMd)
    val model = rememberFileImageModel(coverPath)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(theme.surfaceRaised),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = theme.textDim,
            modifier = Modifier.size(Dimens.iconMd)
        )
        if (model != null) {
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
private fun MusicBrowseList(
    browse: MusicBrowseUi,
    activeSourceId: String,
    viewModel: MusicPlayerViewModel,
    onOpenRommSignIn: (() -> Unit)?
) {
    val theme = LocalArgosyTheme.current
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(browse.showKeyboard) {
        if (browse.showKeyboard) {
            focusRequester.requestFocus()
        } else {
            keyboardController?.hide()
        }
    }

    val reduced = reducedMotion()
    LaunchedEffect(browse.focusIndex, browse.status) {
        if (browse.focusIndex >= 0 && browse.status == MusicBrowseStatus.READY) {
            listState.animateScrollToItemCentered(browse.focusIndex, reduced)
        }
    }

    LaunchedEffect(listState, browse.kind) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisible ->
                val rowCount = viewModel.uiState.value.browse?.rows?.size ?: 0
                if (lastVisible != null && rowCount > 0 && lastVisible >= rowCount - LOAD_MORE_LOOKAHEAD) {
                    viewModel.onListEndApproached()
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BrowseHeader(kind = browse.kind, onBack = { viewModel.closeBrowse() })
        MusicSearchField(
            query = browse.query,
            placeholder = stringResource(
                when (browse.kind) {
                    MusicBrowseKind.PLAYLISTS -> R.string.ui_quick_settings_music_search_playlists
                    MusicBrowseKind.SOUNDTRACKS -> R.string.ui_quick_settings_music_search_soundtracks
                }
            ),
            isFocused = browse.isSearchFocused,
            focusRequester = focusRequester,
            onQueryChange = { viewModel.updateQuery(it) },
            onTap = { viewModel.focusSearch() }
        )
        browse.notice?.let { notice ->
            val isSignIn = notice == MusicBrowseNotice.SIGN_IN_FOR_PLAYLISTS
            val signInAction = onOpenRommSignIn.takeIf { isSignIn }
            Text(
                text = stringResource(noticeRes(browse.kind, notice)),
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    signInAction != null -> theme.focusAccent
                    isSignIn -> theme.textMute
                    else -> theme.textDim
                },
                modifier = Modifier
                    .then(if (signInAction != null) Modifier.clickableNoFocus(onClick = signInAction) else Modifier)
                    .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingXs)
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            when {
                browse.status == MusicBrowseStatus.LOADING -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(Dimens.iconLg))
                }
                browse.status == MusicBrowseStatus.FAILED -> Column(
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.spacingSm)
                ) {
                    BrowseMessage(stringResource(R.string.ui_quick_settings_music_load_failed))
                    RetryRow(
                        isFocused = browse.focusIndex == 0,
                        onClick = {
                            viewModel.setBrowseFocus(0)
                            viewModel.confirmBrowse(0)
                        }
                    )
                }
                browse.rows.isEmpty() -> BrowseMessage(
                    stringResource(
                        when (browse.kind) {
                            MusicBrowseKind.PLAYLISTS -> R.string.ui_quick_settings_music_empty_playlists
                            MusicBrowseKind.SOUNDTRACKS -> R.string.ui_quick_settings_music_empty_soundtracks
                        }
                    )
                )
                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(browse.rows, key = { _, row -> row.key }) { index, row ->
                        BrowseRow(
                            row = row,
                            isFocused = browse.focusIndex == index,
                            isActive = row.key == activeSourceId,
                            isPending = row.key == browse.pendingSelectionId,
                            onClick = {
                                viewModel.setBrowseFocus(index)
                                viewModel.confirmBrowse(index)
                            }
                        )
                    }
                    if (browse.isLoadingMore) {
                        item(key = "loading_more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Dimens.spacingSm),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(Dimens.iconMd))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RetryRow(isFocused: Boolean, onClick: () -> Unit) {
    val theme = LocalArgosyTheme.current
    QuickRowFrame(isFocused = isFocused, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = if (isFocused) theme.focusAccent else theme.textDim,
                modifier = Modifier.size(Dimens.iconMd)
            )
            Spacer(modifier = Modifier.width(Dimens.spacingSm))
            Text(
                text = stringResource(R.string.ui_quick_settings_music_retry),
                style = MaterialTheme.typography.bodyLarge,
                color = theme.textPrimary
            )
        }
    }
}

@Composable
private fun BrowseHeader(kind: MusicBrowseKind, onBack: () -> Unit) {
    val theme = LocalArgosyTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .clickableNoFocus(onClick = onBack)
                .padding(Dimens.spacingSm),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.ui_quick_settings_music_back),
                tint = theme.textPrimary,
                modifier = Modifier.size(Dimens.iconMd)
            )
        }
        Spacer(modifier = Modifier.width(Dimens.spacingSm))
        Text(
            text = stringResource(
                when (kind) {
                    MusicBrowseKind.PLAYLISTS -> R.string.ui_quick_settings_music_playlists
                    MusicBrowseKind.SOUNDTRACKS -> R.string.ui_quick_settings_music_soundtracks
                }
            ),
            style = MaterialTheme.typography.titleSmall,
            color = theme.textPrimary
        )
    }
}

@Composable
private fun MusicSearchField(
    query: String,
    placeholder: String,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onTap: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs)
            .clip(shape)
            .background(theme.surfaceRaised)
            .argosyFocusIndicators(focused = isFocused, indicators = FocusIndicators.ListRow, shape = shape)
            .clickableNoFocus(onClick = onTap)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = if (isFocused) theme.focusAccent else theme.textDim,
            modifier = Modifier.size(Dimens.iconSm)
        )
        Spacer(modifier = Modifier.width(Dimens.spacingSm))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.textDim
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = TextStyle(
                    color = theme.textPrimary,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize
                ),
                cursorBrush = SolidColor(theme.focusAccent),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        }
    }
}

@Composable
private fun BrowseRow(
    row: MusicBrowseRowUi,
    isFocused: Boolean,
    isActive: Boolean,
    isPending: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val title = row.title ?: stringResource(R.string.ui_quick_settings_music_source_launcher)
    val detail = row.ownerName?.let { stringResource(R.string.ui_quick_settings_music_playlist_owner, it) }
        ?: row.platformName
    val count = row.trackCount?.let {
        pluralStringResource(R.plurals.ui_quick_settings_music_track_count, it, it)
    }

    QuickRowFrame(isFocused = isFocused, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (row.isLauncher) {
                Box(
                    modifier = Modifier.size(ComponentDefaults.MusicPlayer.rowCoverDp.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = theme.textDim,
                        modifier = Modifier.size(Dimens.iconMd)
                    )
                }
            } else {
                MusicCover(coverPath = row.coverPath, size = ComponentDefaults.MusicPlayer.rowCoverDp.dp)
            }
            Spacer(modifier = Modifier.width(Dimens.spacingMd))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = theme.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
                    if (detail != null) {
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.textDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    if (count != null) {
                        Text(
                            text = count,
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.textDim,
                            maxLines = 1
                        )
                    }
                }
            }
            when {
                isPending -> CircularProgressIndicator(modifier = Modifier.size(Dimens.iconSm))
                isActive -> Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = stringResource(R.string.ui_quick_settings_music_now_playing),
                    tint = theme.focusAccent,
                    modifier = Modifier.size(Dimens.iconSm)
                )
            }
        }
    }
}

@Composable
private fun BrowseMessage(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = LocalArgosyTheme.current.textDim,
        modifier = Modifier.padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingMd)
    )
}

private fun noticeRes(kind: MusicBrowseKind, notice: MusicBrowseNotice): Int = when (notice) {
    MusicBrowseNotice.OFFLINE -> when (kind) {
        MusicBrowseKind.PLAYLISTS -> R.string.ui_quick_settings_music_notice_playlists_offline
        MusicBrowseKind.SOUNDTRACKS -> R.string.ui_quick_settings_music_notice_soundtracks_offline
    }
    MusicBrowseNotice.LOCAL_ONLY -> R.string.ui_quick_settings_music_notice_local_only
    MusicBrowseNotice.SERVER_FAILED -> R.string.ui_quick_settings_music_notice_server_failed
    MusicBrowseNotice.NO_PLAYABLE_TRACKS -> R.string.ui_quick_settings_music_notice_no_tracks
    MusicBrowseNotice.SIGN_IN_FOR_PLAYLISTS -> R.string.ui_quick_settings_music_notice_sign_in_playlists
}

private fun formatPlaybackTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60L, totalSeconds % 60L)
}
