package com.nendo.argosy.ui.screens.gamedetail.components

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.data.remote.romm.RomMVideoSource
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.screens.gamedetail.ScreenshotPair
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ScreenshotVideoEntryPoint {
    fun romMVideoSource(): RomMVideoSource
}

private fun screenshotVideoSource(context: Context): RomMVideoSource =
    EntryPointAccessors.fromApplication(context.applicationContext, ScreenshotVideoEntryPoint::class.java)
        .romMVideoSource()

@Composable
fun ScreenshotViewerOverlay(
    screenshots: List<ScreenshotPair>,
    currentIndex: Int,
    onNavigate: (delta: Int) -> Unit,
    onDismiss: () -> Unit,
    onSetBackground: () -> Unit = {},
    videoPaused: Boolean = false,
    onToggleVideo: () -> Unit = {}
) {
    if (screenshots.isEmpty()) return

    val screenshot = screenshots[currentIndex.coerceIn(0, screenshots.size - 1)]
    var cacheLoadFailed by remember(currentIndex) { mutableStateOf(false) }
    val useRemote = cacheLoadFailed || screenshot.cachedPath == null
    val mediaModifier = Modifier
        .fillMaxSize()
        .padding(bottom = Dimens.settingsItemMinHeight * 2)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
    ) {
        when {
            screenshot.isVideo -> ViewerVideo(
                resourcePath = screenshot.remoteUrl,
                paused = videoPaused,
                modifier = mediaModifier
            )
            useRemote -> AsyncImage(
                model = screenshot.remoteUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = mediaModifier
            )
            else -> AsyncImage(
                model = java.io.File(screenshot.cachedPath!!),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = mediaModifier,
                onError = { cacheLoadFailed = true }
            )
        }

        Row(modifier = Modifier.fillMaxSize().padding(bottom = Dimens.settingsItemMinHeight)) {
            Box(
                modifier = Modifier
                    .weight(0.25f)
                    .fillMaxHeight()
                    .clickableNoFocus { onNavigate(-1) }
            )
            Box(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight()
                    .clickableNoFocus(onClick = if (screenshot.isVideo) onToggleVideo else onDismiss)
            )
            Box(
                modifier = Modifier
                    .weight(0.25f)
                    .fillMaxHeight()
                    .clickableNoFocus { onNavigate(1) }
            )
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Dimens.settingsItemMinHeight)
                .height(Dimens.settingsItemMinHeight)
        ) {
            Text(
                text = stringResource(
                    R.string.gamedetail_screenshot_viewer_position,
                    currentIndex + 1,
                    screenshots.size
                ),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
        }

        val navigateHint = stringResource(R.string.gamedetail_screenshot_viewer_footer_navigate)
        val closeHint = stringResource(R.string.gamedetail_screenshot_viewer_footer_close)
        val backgroundHint = stringResource(R.string.gamedetail_screenshot_viewer_footer_set_background)
        val playbackHint = stringResource(
            if (videoPaused) R.string.gamedetail_screenshot_viewer_footer_play
            else R.string.gamedetail_screenshot_viewer_footer_pause
        )
        FooterHints(
            hints = buildList {
                add(InputButton.DPAD_HORIZONTAL to navigateHint)
                if (screenshot.isVideo) add(InputButton.A to playbackHint)
                add(InputButton.B to closeHint)
                if (!screenshot.isVideo) add(InputButton.X to backgroundHint)
            },
            onHintClick = { button ->
                when (button) {
                    InputButton.A -> if (screenshot.isVideo) onToggleVideo()
                    InputButton.B -> onDismiss()
                    InputButton.X -> if (!screenshot.isVideo) onSetBackground()
                    else -> {}
                }
            }
        )
    }
}

@Composable
internal fun VideoEntryPlaceholder(
    iconSize: Dp,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs, Alignment.CenterVertically)
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
        Text(
            text = stringResource(R.string.gamedetail_section_screenshots_video_label),
            style = MaterialTheme.typography.labelMedium,
            color = tint
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ViewerVideo(
    resourcePath: String,
    paused: Boolean,
    modifier: Modifier
) {
    val context = LocalContext.current
    val source = remember(context) { screenshotVideoSource(context) }
    val url = remember(resourcePath) { source.urlFor(resourcePath) }
    var ready by remember(url) { mutableStateOf(false) }
    var failed by remember(url) { mutableStateOf(false) }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (url != null && !failed) {
            val player = remember(url) {
                source.createPlayer(context).apply {
                    repeatMode = Player.REPEAT_MODE_ONE
                    setMediaItem(MediaItem.fromUri(url))
                    prepare()
                }
            }
            val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
            val playing = !paused && lifecycle.isAtLeast(Lifecycle.State.STARTED)
            LaunchedEffect(player, playing) { player.playWhenReady = playing }
            DisposableEffect(player) {
                val listener = object : Player.Listener {
                    override fun onRenderedFirstFrame() {
                        ready = true
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        failed = true
                    }
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
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        this.player = player
                    }
                },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize()
            )
        }
        if (!ready || failed || paused) {
            VideoEntryPlaceholder(iconSize = Dimens.iconXl, tint = Color.White)
        }
    }
}
