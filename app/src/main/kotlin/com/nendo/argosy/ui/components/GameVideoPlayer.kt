package com.nendo.argosy.ui.components

import android.content.Context
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.nendo.argosy.R
import com.nendo.argosy.data.remote.romm.RomMVideoSource
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

sealed interface GameVideoClip {
    val key: String

    data class Server(val url: String) : GameVideoClip {
        override val key: String get() = url
    }

    data class YouTube(val videoId: String) : GameVideoClip {
        override val key: String get() = videoId
    }
}

/**
 * The clip to play for a game, preferring the server clip at [serverUrl] over the YouTube trailer.
 */
fun gameVideoClip(serverUrl: String?, youtubeVideoId: String?): GameVideoClip? =
    serverUrl?.let { GameVideoClip.Server(it) } ?: youtubeVideoId?.let { GameVideoClip.YouTube(it) }

@EntryPoint
@InstallIn(SingletonComponent::class)
interface GameVideoEntryPoint {
    fun romMVideoSource(): RomMVideoSource
}

private fun gameVideoEntryPoint(context: Context): GameVideoEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, GameVideoEntryPoint::class.java)

/**
 * Plays [clip] on a loop, filling [modifier]'s bounds. [onReady] fires once playback is visible.
 */
@Composable
fun GameVideoPlayer(
    clip: GameVideoClip,
    muted: Boolean,
    onReady: () -> Unit,
    onError: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (clip) {
        is GameVideoClip.Server -> ServerVideo(
            url = clip.url,
            muted = muted,
            onReady = onReady,
            onError = onError,
            modifier = modifier
        )
        is GameVideoClip.YouTube -> YouTubeVideoPlayer(
            videoId = clip.videoId,
            muted = muted,
            onReady = onReady,
            onError = onError,
            modifier = modifier
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ServerVideo(
    url: String,
    muted: Boolean,
    onReady: () -> Unit,
    onError: () -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val currentOnReady by rememberUpdatedState(onReady)
    val currentOnError by rememberUpdatedState(onError)
    val player = remember(url) {
        gameVideoEntryPoint(context).romMVideoSource().createPlayer(context).apply {
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }
    LaunchedEffect(player, muted) { player.volume = if (muted) 0f else 1f }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() = currentOnReady()
            override fun onPlayerError(error: PlaybackException) = currentOnError()
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    AndroidView(
        factory = { viewContext ->
            (
                LayoutInflater.from(viewContext)
                    .inflate(R.layout.game_video_player_view, FrameLayout(viewContext), false) as PlayerView
                ).apply { this.player = player }
        },
        update = { it.player = player },
        modifier = modifier.fillMaxSize()
    )
}
