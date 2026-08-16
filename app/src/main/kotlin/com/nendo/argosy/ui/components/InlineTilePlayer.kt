package com.nendo.argosy.ui.components

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.view.TextureView
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ColorTokens
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import android.media.AudioAttributes as FocusAudioAttributes

private const val VOLUME_MUTED = 0f
private const val VOLUME_ENGAGED = 1f
private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L
private const val SEEK_STEP_MS = 10_000L

/**
 * A grid tile that plays a local video file in place: muted and looping while it is only a preview,
 * audible and captioned with a transport readout once the caller says the tile is engaged.
 *
 * Draws into a `TextureView`, not a `SurfaceView`: a punched-through surface ignores the grid's
 * focus-scale `graphicsLayer` and would paint in the wrong place.
 *
 * The player is released, never paused, on every exit path. Hardware decoders are finite and shared
 * with the emulator and the fullscreen player.
 *
 * @param filePath an already resolved local path; nothing here opens a network stream.
 * @param isPlaying the caller's decision to spend a decoder on this tile.
 * @param isEngaged sound on, audio focus taken, transport readout visible.
 * @param isPaused holds the file where it is; a preview that is not engaged is never paused.
 * @param seekTicks a running count of seek presses, signed by direction. Each new tick moves the
 *   file by one step; the caller never has to know where it had reached.
 * @param startPositionMs where to open the file, for a tile returning to something it was already
 *   part way through.
 * @param onPositionChanged reports where the file reached, as it plays and once more as it closes.
 * @param onTakeAudio raised when this tile starts sounding, for the caller to hush its own audio.
 * @param onReleaseAudio raised when it stops.
 */
@OptIn(UnstableApi::class)
@Composable
fun InlineTilePlayer(
    filePath: String,
    isPlaying: Boolean,
    isEngaged: Boolean,
    modifier: Modifier = Modifier,
    isPaused: Boolean = false,
    seekTicks: Int = 0,
    startPositionMs: Long = 0L,
    onPositionChanged: (Long) -> Unit = {},
    onTakeAudio: () -> Unit = {},
    onReleaseAudio: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isStarted by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    var isFileReadable by remember(filePath) { mutableStateOf<Boolean?>(null) }
    var hasFailed by remember(filePath) { mutableStateOf(false) }
    var hasFirstFrame by remember(filePath) { mutableStateOf(false) }
    var isAdvancing by remember(filePath) { mutableStateOf(false) }
    var videoAspect by remember(filePath) { mutableFloatStateOf(0f) }
    var positionMs by remember(filePath) { mutableLongStateOf(0L) }
    var durationMs by remember(filePath) { mutableLongStateOf(0L) }
    var hasAudioFocus by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var lastSeekTicks by remember(filePath, isEngaged) { mutableIntStateOf(seekTicks) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> isStarted = true
                Lifecycle.Event.ON_STOP -> isStarted = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(filePath) {
        isFileReadable = withContext(Dispatchers.IO) { isReadableVideoFile(filePath) }
    }

    val shouldPlay = isPlaying && isStarted && isFileReadable == true && !hasFailed

    DisposableEffect(filePath, shouldPlay) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                hasFailed = true
            }

            override fun onRenderedFirstFrame() {
                hasFirstFrame = true
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isAdvancing = playing
            }

            override fun onVideoSizeChanged(size: VideoSize) {
                videoAspect = aspectOf(size)
            }
        }
        val created = if (shouldPlay) {
            createTilePlayer(context, filePath, listener, startPositionMs)
        } else {
            null
        }
        if (shouldPlay && created == null) hasFailed = true
        player = created
        onDispose {
            player = null
            hasFirstFrame = false
            isAdvancing = false
            created?.let { onPositionChanged(it.currentPosition.coerceAtLeast(0L)) }
            created?.removeListener(listener)
            created?.release()
        }
    }

    LaunchedEffect(player, isPaused) {
        player?.playWhenReady = !isPaused
    }

    LaunchedEffect(player, seekTicks) {
        val active = player ?: return@LaunchedEffect
        val previous = lastSeekTicks
        lastSeekTicks = seekTicks
        val steps = seekTicks - previous
        if (steps == 0) return@LaunchedEffect
        val limit = active.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
        val target = (active.currentPosition + steps * SEEK_STEP_MS).coerceIn(0L, limit)
        active.seekTo(target)
        positionMs = target
        onPositionChanged(target)
    }

    DisposableEffect(isEngaged, player) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val request = if (isEngaged && player != null) tileFocusRequest { granted -> hasAudioFocus = granted } else null
        if (request != null && audioManager != null) {
            hasAudioFocus =
                audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        if (request != null) onTakeAudio()
        onDispose {
            if (request != null && audioManager != null) {
                audioManager.abandonAudioFocusRequest(request)
            }
            if (request != null) onReleaseAudio()
            hasAudioFocus = false
        }
    }

    LaunchedEffect(player, isEngaged, hasAudioFocus) {
        player?.volume = if (isEngaged && hasAudioFocus) VOLUME_ENGAGED else VOLUME_MUTED
    }

    LaunchedEffect(player, isEngaged) {
        val active = player
        if (active == null || !isEngaged) return@LaunchedEffect
        while (isActive) {
            positionMs = active.currentPosition.coerceAtLeast(0L)
            durationMs = active.duration.takeIf { it > 0L } ?: 0L
            onPositionChanged(positionMs)
            delay(ComponentDefaults.InlineTilePlayer.positionPollMs.toLong())
        }
    }

    val active = player
    Box(modifier = modifier) {
        if (active != null) {
            AndroidView(
                factory = { viewContext ->
                    val texture = TextureView(viewContext)
                    AspectRatioFrameLayout(viewContext).apply {
                        setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
                        addView(
                            texture,
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                    }
                },
                update = { frame ->
                    if (videoAspect > 0f) frame.setAspectRatio(videoAspect)
                    val texture = frame.getChildAt(0) as? TextureView
                    if (texture != null && frame.tag !== active) {
                        active.setVideoTextureView(texture)
                        frame.tag = active
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (active == null || !hasFirstFrame) {
            TilePlaceholder(
                icon = if (isFileReadable == false || hasFailed) Icons.Outlined.VideocamOff else Icons.Outlined.Movie,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isEngaged && active != null && hasFirstFrame) {
            TileTransport(
                isAdvancing = isAdvancing,
                positionMs = positionMs,
                durationMs = durationMs,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun TilePlaceholder(icon: ImageVector, modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    Box(
        modifier = modifier.background(theme.surfaceRaised),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = theme.textPrimary.copy(alpha = ComponentDefaults.InlineTilePlayer.posterIconAlpha),
            modifier = Modifier.size(Dimens.iconLg)
        )
    }
}

@Composable
private fun TileTransport(
    isAdvancing: Boolean,
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    val onScrim = ColorTokens.Scheme.Dark.onSurface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = ComponentDefaults.InlineTilePlayer.overlayScrimAlpha))
            .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        Icon(
            imageVector = if (isAdvancing) Icons.Filled.PlayArrow else Icons.Filled.Pause,
            contentDescription = null,
            tint = onScrim,
            modifier = Modifier.size(Dimens.iconSm)
        )
        Text(
            text = transportReadout(positionMs, durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = onScrim
        )
    }
}

/**
 * Muted, looping and deliberately not holding audio focus of its own: focus is taken only while the
 * tile is engaged, so a wall of previews never fights the launcher's music for the output. The
 * attribute intent otherwise matches the fullscreen player, so a preview and the real thing route
 * through the same stream.
 */
@OptIn(UnstableApi::class)
private fun createTilePlayer(
    context: Context,
    filePath: String,
    listener: Player.Listener,
    startPositionMs: Long
): ExoPlayer? = runCatching {
    ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            false
        )
        repeatMode = Player.REPEAT_MODE_ONE
        volume = VOLUME_MUTED
        addListener(listener)
        setMediaItem(
            MediaItem.fromUri(Uri.fromFile(File(filePath))),
            startPositionMs.coerceAtLeast(0L)
        )
        prepare()
        playWhenReady = true
    }
}.getOrNull()

private fun tileFocusRequest(onFocusChanged: (Boolean) -> Unit): AudioFocusRequest =
    AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(
            FocusAudioAttributes.Builder()
                .setUsage(FocusAudioAttributes.USAGE_MEDIA)
                .setContentType(FocusAudioAttributes.CONTENT_TYPE_MOVIE)
                .build()
        )
        .setOnAudioFocusChangeListener { change ->
            onFocusChanged(change == AudioManager.AUDIOFOCUS_GAIN)
        }
        .build()

private fun isReadableVideoFile(filePath: String): Boolean = runCatching {
    val file = File(filePath)
    file.isFile && file.canRead() && file.length() > 0L
}.getOrDefault(false)

private fun aspectOf(size: VideoSize): Float =
    if (size.height == 0 || size.width == 0) 0f
    else size.width * size.pixelWidthHeightRatio / size.height

private fun transportReadout(positionMs: Long, durationMs: Long): String =
    if (durationMs > 0L) "${clockOf(positionMs)} / ${clockOf(durationMs)}" else clockOf(positionMs)

private fun clockOf(millis: Long): String {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / (SECONDS_PER_MINUTE * MINUTES_PER_HOUR)
    val minutes = (totalSeconds / SECONDS_PER_MINUTE) % MINUTES_PER_HOUR
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
