package com.nendo.argosy.ui.components

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import kotlinx.coroutines.delay
import java.io.File

private const val FADE_STEPS = 12

/**
 * Plays a curated page's own music, carrying the player across page turns so a change of page is a
 * fade rather than a cut. A page with no track of its own fades what was playing out and lets the
 * player go.
 *
 * The launcher's own music is faded out by the caller; this plays in its place rather than over it.
 */
@OptIn(UnstableApi::class)
@Composable
fun PageThemePlayer(filePath: String?) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    val fadeMs = ComponentDefaults.InlineTilePlayer.audioFadeMs.toLong()

    LaunchedEffect(filePath) {
        val existing = player
        val readable = filePath?.takeIf { it.isNotBlank() && File(it).canRead() }

        if (readable == null) {
            if (existing != null) {
                existing.fadeTo(0f, fadeMs)
                existing.release()
                player = null
            }
            return@LaunchedEffect
        }

        if (existing == null) {
            val created = createThemePlayer(context, readable) ?: return@LaunchedEffect
            player = created
            created.fadeTo(1f, fadeMs)
            return@LaunchedEffect
        }

        existing.fadeTo(0f, fadeMs)
        existing.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(readable))))
        existing.prepare()
        existing.playWhenReady = true
        existing.fadeTo(1f, fadeMs)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> player?.playWhenReady = true
                Lifecycle.Event.ON_STOP -> player?.playWhenReady = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player?.release()
            player = null
        }
    }
}

/**
 * Ramps the output rather than switching it, so a page turn is heard as a transition. Stepped by
 * hand because the player owns its own volume and no animation runs against it.
 */
private suspend fun ExoPlayer.fadeTo(target: Float, durationMs: Long) {
    val start = volume
    if (start == target || durationMs <= 0L) {
        volume = target
        return
    }
    val stepDelay = durationMs / FADE_STEPS
    repeat(FADE_STEPS) { step ->
        volume = start + (target - start) * ((step + 1).toFloat() / FADE_STEPS)
        delay(stepDelay)
    }
    volume = target
}

@OptIn(UnstableApi::class)
private fun createThemePlayer(context: Context, filePath: String): ExoPlayer? = runCatching {
    ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true
        )
        repeatMode = Player.REPEAT_MODE_ONE
        volume = 0f
        setMediaItem(MediaItem.fromUri(Uri.fromFile(File(filePath))))
        prepare()
        playWhenReady = true
    }
}.getOrNull()
