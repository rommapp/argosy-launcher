package com.nendo.argosy.ui.dualscreen

import android.content.Context
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.data.preferences.DisplayPreferences
import com.nendo.argosy.data.preferences.DisplayPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMVideoSource
import com.nendo.argosy.ui.common.LocalRunningGames
import com.nendo.argosy.ui.components.GameVideoPlayer
import com.nendo.argosy.ui.components.gameVideoClip
import com.nendo.argosy.ui.theme.reducedMotion
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ShowcaseVideoEntryPoint {
    fun displayPreferencesRepository(): DisplayPreferencesRepository
    fun romMVideoSource(): RomMVideoSource
}

private fun showcaseVideoEntryPoint(context: Context): ShowcaseVideoEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, ShowcaseVideoEntryPoint::class.java)

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
    val homeVideoGameId by (
        DualScreenManagerHolder.instance?.homeVideoGameId
            ?: remember { MutableStateFlow<Long?>(null) }
        ).collectAsState()
    val allowed = prefs.videoWallpaperEnabled && !reducedMotion() && !running && homeVideoGameId != gameId &&
        lifecycle.isAtLeast(Lifecycle.State.STARTED)
    LaunchedEffect(allowed) { if (!allowed) reportPlaying(false) }
    if (!allowed) return

    val source = entryPoint.romMVideoSource()
    val clip = remember(detail.videoPath, detail.youtubeVideoId) {
        gameVideoClip(source.urlFor(detail.videoPath), detail.youtubeVideoId)
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
    GameVideoPlayer(
        clip = clip,
        muted = prefs.videoWallpaperMuted,
        onReady = { ready = true },
        onError = { ready = false },
        modifier = modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }
    )
}

private const val MILLIS_PER_SECOND = 1_000L
