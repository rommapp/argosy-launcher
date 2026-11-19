package com.nendo.argosy.ui.components.launch

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.BoxArtShape
import com.nendo.argosy.domain.model.LaunchProgress
import com.nendo.argosy.domain.model.phase
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.common.rememberResolvedCoverPath
import com.nendo.argosy.ui.components.LaunchCancelButton
import com.nendo.argosy.ui.primitives.ArgosyIndeterminateProgressBar
import com.nendo.argosy.ui.primitives.ArgosyProgressBar
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.reducedMotion
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.util.formatBytes
import kotlinx.coroutines.delay

private val Defaults = ComponentDefaults.LaunchOverlay

/**
 * The quiet launch column: cover art, title and Cancel at equal spacing. The status block fades
 * in once [LaunchOverlayState] says the step has outlasted its patience or a failure needs
 * showing. Cancel's slot is always held, empty once the launch cannot be cancelled.
 */
@Composable
fun CalmLaunchContent(
    progress: LaunchProgress,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reduced = reducedMotion()
    var nowMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val ui = LaunchOverlayState.from(
        progress = progress,
        nowMs = nowMs,
        reducedMotion = reduced,
        patienceMs = Defaults.patienceMs,
        reflowMs = Defaults.reflowMs,
        statusFadeMs = Defaults.statusFadeMs,
        statusFadeDelayMs = Defaults.statusFadeDelayMs,
        sweepMs = ComponentDefaults.ProgressBar.sweepMs
    )
    LaunchedEffect(progress.stepStartedAtMs, progress.step.phase) {
        nowMs = SystemClock.elapsedRealtime()
        val revealAtMs = ui.revealAtMs ?: return@LaunchedEffect
        val waitMs = revealAtMs - SystemClock.elapsedRealtime()
        if (waitMs > 0) delay(waitMs)
        nowMs = SystemClock.elapsedRealtime()
    }
    val ticking = ui.bar == CalmLaunchBar.DETERMINATE
    LaunchedEffect(ticking) {
        while (ticking) {
            delay(Defaults.etaTickMs.toLong())
            nowMs = SystemClock.elapsedRealtime()
        }
    }

    val coverPath = progress.gameId?.let { rememberResolvedCoverPath(it, null) }
    val cover = rememberFileImageModel(coverPath)
    val hasArt = cover != null
    val shownCover = rememberLastShown(cover, hasArt)
    var artBitmap by remember(progress.gameId) { mutableStateOf<Bitmap?>(null) }
    val artColor = rememberArtGlowColor(artBitmap)
    val title = progress.gameTitle
    val hasLead = hasArt || title != null
    val gap = Dimens.spacingLg
    val enter = reflowEnter(ui)
    val exit = reflowExit(ui)
    val cancelAlpha by animateFloatAsState(
        targetValue = if (ui.cancellable) 1f else 0f,
        animationSpec = tween(durationMillis = ui.statusFadeMs, easing = Motion.argosyEase),
        label = "launchCancelFade"
    )

    BoxWithConstraints(modifier = modifier) {
        val artHeight = min(Defaults.artHeightDp.dp, maxHeight * Defaults.artHeightRatio)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = Dimens.spacingXl)
        ) {
            AnimatedVisibility(visible = hasArt, enter = enter, exit = exit) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model = shownCover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        onSuccess = { state ->
                            artBitmap = (state.result.drawable as? BitmapDrawable)?.bitmap
                        },
                        modifier = Modifier
                            .height(artHeight)
                            .aspectRatio(BoxArtShape.TALL.aspectRatio, matchHeightConstraintsFirst = true)
                            .launchBreathGlow(artColor, progress.gameId)
                            .clip(RoundedCornerShape(Dimens.radiusLg))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    if (title != null) Spacer(Modifier.height(gap))
                }
            }
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = Defaults.titleMaxLines,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val statusShown = ui.line != null || ui.bar != CalmLaunchBar.NONE
            val status = rememberLastShown(ui, statusShown)
            AnimatedVisibility(visible = statusShown, enter = enter, exit = exit) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (hasLead) Spacer(Modifier.height(gap))
                    CalmLaunchStatus(status, enter, exit)
                    if (!hasLead) Spacer(Modifier.height(gap))
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(cancelAlpha)
            ) {
                if (hasLead) Spacer(Modifier.height(gap))
                LaunchCancelButton(onClick = { if (ui.cancellable) onCancel() })
            }
        }
    }
}

@Composable
private fun CalmLaunchStatus(
    status: CalmLaunchUi,
    enter: EnterTransition,
    exit: ExitTransition
) {
    val lineGap = Dimens.spacingSm
    val barWidth = Defaults.progressWidthDp.dp
    AnimatedVisibility(visible = status.bar != CalmLaunchBar.NONE, enter = enter, exit = exit) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (status.bar == CalmLaunchBar.DETERMINATE) {
                ArgosyProgressBar(progress = status.fraction, modifier = Modifier.width(barWidth))
            } else {
                ArgosyIndeterminateProgressBar(modifier = Modifier.width(barWidth), sweepMs = status.sweepMs)
            }
            if (status.line != null || status.percent != null) Spacer(Modifier.height(lineGap))
        }
    }
    status.line?.let { line ->
        val size = status.sizeBytes
        Text(
            text = if (line == R.string.ui_launch_overlay_wait_core_sized && size != null) {
                stringResource(line, formatBytes(size))
            } else {
                stringResource(line)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (status.lineIsFailure) {
                LocalLauncherTheme.current.semanticColors.warning
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center
        )
    }
    val percent = rememberLastShown(status.percent, status.percent != null)
    val eta = status.etaSeconds
    AnimatedVisibility(visible = status.percent != null, enter = enter, exit = exit) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(lineGap))
            Text(
                text = when {
                    percent == null -> ""
                    eta != null -> pluralStringResource(R.plurals.ui_launch_overlay_percent_eta, eta, percent, eta)
                    else -> stringResource(R.string.ui_launch_overlay_percent, percent)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun <T> rememberLastShown(value: T, shown: Boolean): T {
    var last by remember { mutableStateOf(value) }
    SideEffect { if (shown) last = value }
    return if (shown) value else last
}

private fun reflowEnter(ui: CalmLaunchUi): EnterTransition =
    expandVertically(
        animationSpec = tween(durationMillis = ui.reflowMs, easing = Motion.argosyEase),
        expandFrom = Alignment.Top
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = ui.statusFadeMs,
            delayMillis = ui.statusFadeDelayMs,
            easing = Motion.argosyEase
        )
    )

private fun reflowExit(ui: CalmLaunchUi): ExitTransition =
    fadeOut(
        animationSpec = tween(durationMillis = ui.statusFadeMs, easing = Motion.argosyEase)
    ) + shrinkVertically(
        animationSpec = tween(
            durationMillis = ui.reflowMs,
            delayMillis = (ui.statusFadeDelayMs + ui.statusFadeMs - ui.reflowMs).coerceAtLeast(0),
            easing = Motion.argosyEase
        ),
        shrinkTowards = Alignment.Top
    )
