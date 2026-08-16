package com.nendo.argosy.ui.components

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

/**
 * Plays a curated page's own music for as long as that page is the one on screen. Released rather
 * than paused when the page changes, so a wall of pages never holds a decoder each.
 *
 * The launcher's own music is faded out by the caller; this plays in its place rather than over it.
 */
@OptIn(UnstableApi::class)
@Composable
fun PageThemePlayer(filePath: String?) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(filePath, lifecycleOwner) {
        if (filePath.isNullOrBlank() || !File(filePath).canRead()) {
            return@DisposableEffect onDispose { }
        }
        val player = runCatching {
            ExoPlayer.Builder(context).build().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true
                )
                repeatMode = Player.REPEAT_MODE_ONE
                setMediaItem(MediaItem.fromUri(Uri.fromFile(File(filePath))))
                prepare()
                playWhenReady = true
            }
        }.getOrNull() ?: return@DisposableEffect onDispose { }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> player.playWhenReady = true
                Lifecycle.Event.ON_STOP -> player.playWhenReady = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }
}
