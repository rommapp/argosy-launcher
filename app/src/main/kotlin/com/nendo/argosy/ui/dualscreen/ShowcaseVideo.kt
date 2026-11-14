package com.nendo.argosy.ui.dualscreen

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.nendo.argosy.data.preferences.DisplayPreferences
import com.nendo.argosy.data.preferences.DisplayPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMVideoSource
import com.nendo.argosy.ui.common.LocalRunningGames
import com.nendo.argosy.ui.components.YouTubeVideoPlayer
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ShowcaseVideoEntryPoint {
    fun displayPreferencesRepository(): DisplayPreferencesRepository
    fun romMVideoSource(): RomMVideoSource
}

private fun showcaseVideoEntryPoint(context: Context): ShowcaseVideoEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, ShowcaseVideoEntryPoint::class.java)

private sealed interface ShowcaseVideoClip {
    data class Server(val url: String) : ShowcaseVideoClip
    data class YouTube(val videoId: String) : ShowcaseVideoClip
}

@Composable
internal fun ShowcaseVideo(
    detail: CompanionDetail,
    fadeMs: Int,
    onPlayingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val reportPlaying by rememberUpdatedState(onPlayingChange)
    DisposableEffect(detail.gameId) { onDispose { reportPlaying(false) } }
    val gameId = detail.gameId ?: return
    val context = LocalContext.current
    val entryPoint = remember(context) { showcaseVideoEntryPoint(context) }
    val prefs by entryPoint.displayPreferencesRepository().preferences
        .collectAsState(initial = DisplayPreferences())
    val running = LocalRunningGames.current.isRunning(gameId)
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val allowed = prefs.videoWallpaperEnabled && !running && lifecycle.isAtLeast(Lifecycle.State.STARTED)
    LaunchedEffect(allowed) { if (!allowed) reportPlaying(false) }
    if (!allowed) return

    val source = entryPoint.romMVideoSource()
    val clip = remember(detail.videoPath, detail.youtubeVideoId) {
        source.urlFor(detail.videoPath)?.let { ShowcaseVideoClip.Server(it) }
            ?: detail.youtubeVideoId?.let { ShowcaseVideoClip.YouTube(it) }
    } ?: return

    var due by remember(gameId, clip) { mutableStateOf(false) }
    LaunchedEffect(gameId, clip, prefs.videoWallpaperDelaySeconds) {
        delay(prefs.videoWallpaperDelaySeconds * MILLIS_PER_SECOND)
        due = true
    }
    if (!due) return

    var ready by remember(gameId, clip) { mutableStateOf(false) }
    LaunchedEffect(ready) { reportPlaying(ready) }
    val alpha by animateFloatAsState(if (ready) 1f else 0f, tween(fadeMs), label = "showcaseVideo")
    val faded = modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }
    when (clip) {
        is ShowcaseVideoClip.Server -> ServerVideo(
            url = clip.url,
            source = source,
            muted = prefs.videoWallpaperMuted,
            onReady = { ready = true },
            modifier = faded
        )
        is ShowcaseVideoClip.YouTube -> YouTubeVideoPlayer(
            videoId = clip.videoId,
            muted = prefs.videoWallpaperMuted,
            onReady = { ready = true },
            onError = { ready = false },
            modifier = faded
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ServerVideo(
    url: String,
    source: RomMVideoSource,
    muted: Boolean,
    onReady: () -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val player = remember(url) {
        source.createPlayer(context).apply {
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }
    LaunchedEffect(player, muted) { player.volume = if (muted) 0f else 1f }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() = onReady()
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                this.player = player
            }
        },
        update = { it.player = player },
        modifier = modifier
    )
}

private const val MILLIS_PER_SECOND = 1_000L
