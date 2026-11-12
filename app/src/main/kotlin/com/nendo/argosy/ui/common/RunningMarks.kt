package com.nendo.argosy.ui.common

import android.animation.ValueAnimator
import android.content.res.Resources
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.nendo.argosy.R
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.background.RunningGamesSnapshot
import com.nendo.argosy.data.streaming.StreamSessionManager
import com.nendo.argosy.libretro.slot.GameSlots
import com.nendo.argosy.ui.theme.LocalMotionTier
import com.nendo.argosy.ui.theme.MotionTier
import com.nendo.argosy.ui.theme.generated.MotionTokens
import kotlin.math.PI
import kotlin.math.cos

/**
 * A game left running. [order] is its position in the Resume list; [slotIndex] is null for a game
 * an external emulator holds and 0 for the built-in game in Argosy's own task. [snapshotPath] is
 * its last frame, null when none could be read.
 */
data class RunningGameUi(
    val gameId: Long,
    val variantFileId: Long?,
    val taskId: Int,
    val slotIndex: Int?,
    val displayId: Int,
    val emulatorPackage: String,
    val playedThisRunMs: Long,
    val isClosing: Boolean,
    val order: Int,
    val snapshotPath: String? = null,
    val snapshotTakenAtMillis: Long = 0L
) {
    val isBuiltIn: Boolean get() = emulatorPackage == EmulatorRegistry.BUILTIN_PACKAGE
}

data class RunningStripUnit(val weight: Int, val isClosing: Boolean)

/**
 * Every running game by game id, plus what the status strip draws: one unit per loaded game slot
 * in slot order, one per game outside the pool, and the free pool units.
 */
data class RunningGamesUi(
    val games: List<RunningGameUi> = emptyList(),
    val appCount: Int = 0,
    val slotUnits: List<RunningStripUnit> = emptyList(),
    val outsidePoolUnits: List<RunningStripUnit> = emptyList(),
    val freeUnits: Int = GameSlots.CAPACITY_UNITS
) {
    private val byGame: Map<Long, RunningGameUi> = games.associateBy { it.gameId }

    val isAnyRunning: Boolean get() = games.isNotEmpty()

    fun markFor(gameId: Long): RunningGameUi? = byGame[gameId]

    fun isRunning(gameId: Long): Boolean = gameId in byGame
}

fun RunningGamesSnapshot.toRunningGamesUi(): RunningGamesUi {
    val games = apps.mapIndexedNotNull { index, app ->
        if (app.packageName == StreamSessionManager.STREAM_PACKAGE) return@mapIndexedNotNull null
        val game = app.game ?: return@mapIndexedNotNull null
        RunningGameUi(
            gameId = game.gameId,
            variantFileId = game.variantFileId,
            taskId = app.taskId,
            slotIndex = app.slotIndex,
            displayId = app.displayId,
            emulatorPackage = app.packageName,
            playedThisRunMs = playedThisRunMs[app.taskId] ?: 0L,
            isClosing = app.taskId in closingTaskIds,
            order = index,
            snapshotPath = snapshots[app.taskId]?.path,
            snapshotTakenAtMillis = snapshots[app.taskId]?.takenAtMillis ?: 0L
        )
    }.distinctBy { it.gameId }
    val loaded = apps.filter { (it.slotIndex ?: 0) > 0 }.sortedBy { it.slotIndex }
    val usedUnits = loaded.sumOf { GameSlots.weightOf(it.platformSlug) }
    return RunningGamesUi(
        games = games,
        appCount = apps.size,
        slotUnits = loaded.map { RunningStripUnit(GameSlots.weightOf(it.platformSlug), it.taskId in closingTaskIds) },
        outsidePoolUnits = apps
            .filter { it.game != null && (it.slotIndex ?: 0) == 0 && it.packageName != StreamSessionManager.STREAM_PACKAGE }
            .map { RunningStripUnit(1, it.taskId in closingTaskIds) },
        freeUnits = (GameSlots.CAPACITY_UNITS - usedUnits).coerceAtLeast(0)
    )
}

data class CloseRunningPrompt(val taskId: Int, val title: String)

val LocalRunningGames = compositionLocalOf { RunningGamesUi() }

/**
 * Opens the Resume screen, for a surface that offers it outside the drawer.
 */
val LocalOpenRunningGames = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * The one breathing clock every running line reads in its draw pass: 0 at the trough, 1 at the
 * peak. It holds at the midpoint whenever nothing runs or motion is reduced.
 */
val LocalRunningBreath = staticCompositionLocalOf<State<Float>> { mutableFloatStateOf(RUNNING_BREATH_MID) }

const val RUNNING_BREATH_MID = 0.5f

private val SineInOut = Easing { fraction -> ((1.0 - cos(PI * fraction)) / 2.0).toFloat() }

@Composable
fun rememberRunningBreath(active: Boolean): State<Float> {
    val reduced = LocalMotionTier.current == MotionTier.Reduced || !ValueAnimator.areAnimatorsEnabled()
    if (!active || reduced) return remember { mutableFloatStateOf(RUNNING_BREATH_MID) }
    val transition = rememberInfiniteTransition(label = "runningBreath")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = MotionTokens.Tween.breathMs / 2, easing = SineInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "runningBreathLevel"
    )
}

/**
 * How long a running game has been played this run, in the wording every running line uses.
 */
fun runDurationText(resources: Resources, playedMs: Long): String {
    val totalMinutes = playedMs / MILLIS_PER_MINUTE
    val hours = totalMinutes / MINUTES_PER_HOUR
    val minutes = totalMinutes % MINUTES_PER_HOUR
    return if (hours > 0) {
        resources.getString(R.string.ui_running_duration_hours_minutes, hours, minutes)
    } else {
        resources.getString(R.string.ui_running_duration_minutes, minutes)
    }
}

private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
