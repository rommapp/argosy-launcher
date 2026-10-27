package com.nendo.argosy.ui.components.launch

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.LaunchPhase
import com.nendo.argosy.domain.model.LaunchProgress
import com.nendo.argosy.domain.model.LaunchStep
import com.nendo.argosy.domain.model.failure
import com.nendo.argosy.domain.model.fraction
import com.nendo.argosy.domain.model.phase
import com.nendo.argosy.domain.model.sizeBytes
import com.nendo.argosy.ui.common.labelRes
import com.nendo.argosy.ui.common.waitLabelRes
import kotlin.math.ceil
import kotlin.math.roundToInt

enum class CalmLaunchBar { NONE, INDETERMINATE, DETERMINATE }

/**
 * Whether the presentation display draws this step. Questions stay on the surface taking input;
 * only the recap card mirrors, and every other step leaves the game showcase up.
 */
val LaunchStep.mirrorsToPresentation: Boolean
    get() = this is LaunchStep.Recap

fun LaunchStep.showsCancelHint(appPromptShowing: Boolean): Boolean =
    !appPromptShowing && this !is LaunchStep.Recap && this != LaunchStep.Launching

data class CalmLaunchUi(
    @StringRes val line: Int?,
    val lineIsFailure: Boolean,
    val bar: CalmLaunchBar,
    val fraction: Float?,
    val sizeBytes: Long?,
    val percent: Int?,
    val etaSeconds: Int?,
    val cancellable: Boolean,
    val revealAtMs: Long?,
    val reflowMs: Int,
    val statusFadeMs: Int,
    val statusFadeDelayMs: Int,
    val sweepMs: Int
)

/**
 * Maps a launch's progress at [nowMs] to what the calm column shows. A step stays quiet for
 * its first patience window; past it, the phase's waiting line and a bar appear. A failure the
 * launch survives shows at once and wins the line, without hiding a later step's bar.
 */
object LaunchOverlayState {
    private const val ETA_MIN_FRACTION = 0.05f
    private const val ETA_MIN_ELAPSED_MS = 1000L
    private const val MS_PER_SECOND = 1000.0

    fun from(
        progress: LaunchProgress,
        nowMs: Long,
        reducedMotion: Boolean,
        patienceMs: Int,
        reflowMs: Int,
        statusFadeMs: Int,
        statusFadeDelayMs: Int,
        sweepMs: Int
    ): CalmLaunchUi {
        val step = progress.step
        val phase = step.phase
        val canWait = phase != LaunchPhase.LAUNCHING && phase != LaunchPhase.QUESTION
        val elapsed = nowMs - progress.stepStartedAtMs
        val waiting = canWait && elapsed >= patienceMs
        val fraction = step.fraction
        val sizeBytes = step.sizeBytes
        val bar = when {
            !waiting -> CalmLaunchBar.NONE
            fraction != null -> CalmLaunchBar.DETERMINATE
            else -> CalmLaunchBar.INDETERMINATE
        }
        val failure = progress.failure ?: step.failure
        val shownFraction = fraction?.coerceIn(0f, 1f)?.takeIf { bar == CalmLaunchBar.DETERMINATE }
        return CalmLaunchUi(
            line = failure?.labelRes ?: waitLine(phase, sizeBytes).takeIf { waiting },
            lineIsFailure = failure != null,
            bar = bar,
            fraction = fraction,
            sizeBytes = sizeBytes,
            percent = shownFraction?.let { (it * 100).roundToInt() },
            etaSeconds = shownFraction?.let { etaSeconds(it, elapsed) },
            cancellable = phase != LaunchPhase.LAUNCHING,
            revealAtMs = (progress.stepStartedAtMs + patienceMs).takeIf { canWait },
            reflowMs = if (reducedMotion) 0 else reflowMs,
            statusFadeMs = if (reducedMotion) 0 else statusFadeMs,
            statusFadeDelayMs = if (reducedMotion) 0 else statusFadeDelayMs,
            sweepMs = if (reducedMotion) 0 else sweepMs
        )
    }

    @StringRes
    private fun waitLine(phase: LaunchPhase, sizeBytes: Long?): Int? =
        if (phase == LaunchPhase.CORE && sizeBytes != null) {
            R.string.ui_launch_overlay_wait_core_sized
        } else {
            phase.waitLabelRes
        }

    private fun etaSeconds(fraction: Float, elapsedMs: Long): Int? {
        if (fraction < ETA_MIN_FRACTION || fraction >= 1f || elapsedMs < ETA_MIN_ELAPSED_MS) return null
        val remainingMs = elapsedMs * (1.0 - fraction) / fraction
        return ceil(remainingMs / MS_PER_SECOND).toInt().takeIf { it > 0 }
    }
}
