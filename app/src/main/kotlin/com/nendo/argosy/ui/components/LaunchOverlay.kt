package com.nendo.argosy.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nendo.argosy.R
import com.nendo.argosy.data.emulator.LaunchProgressTracker
import com.nendo.argosy.data.launcher.SteamLaunchers
import com.nendo.argosy.domain.model.LaunchProgress
import com.nendo.argosy.domain.model.LaunchPromptOption
import com.nendo.argosy.domain.model.LaunchStep
import com.nendo.argosy.domain.model.RunningGameQuestion
import com.nendo.argosy.domain.model.SnapshotConflictChoice
import com.nendo.argosy.domain.model.SyncProgress
import com.nendo.argosy.ui.components.launch.CalmLaunchContent
import com.nendo.argosy.ui.components.launch.LaunchHostSignal
import com.nendo.argosy.ui.components.launch.LaunchQuestionCard
import com.nendo.argosy.ui.components.launch.LaunchQuestionChoice
import com.nendo.argosy.ui.components.launch.LaunchVeil
import com.nendo.argosy.ui.components.launch.darkensForLaunch
import com.nendo.argosy.ui.components.launch.mirrorsToPresentation
import com.nendo.argosy.ui.components.launch.opensOnSurface
import com.nendo.argosy.ui.components.launch.veilsDisplay
import com.nendo.argosy.ui.components.recap.LaunchRecapCard
import com.nendo.argosy.ui.components.recap.LaunchRecapCompactCard
import com.nendo.argosy.ui.components.recap.LaunchRecapCursor
import com.nendo.argosy.ui.components.recap.RecapCursor
import com.nendo.argosy.ui.components.recap.RecapSurface
import com.nendo.argosy.ui.components.recap.rememberRecapFullCardSurface
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.LocalMotionTier
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.MotionTier
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.clickableNoFocus
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LaunchProgressEntryPoint {
    fun launchProgressTracker(): LaunchProgressTracker
    fun launchRecapCursor(): LaunchRecapCursor
}

private fun launchProgressEntryPoint(context: Context): LaunchProgressEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, LaunchProgressEntryPoint::class.java)

/**
 * The launch in progress over the surface that starts launches. It holds the input stack while a
 * launch runs. A launch whose game window has been requested ends once that surface stops, once
 * the overlay leaves composition (the in-game dashboard takes the surface), and when the overlay
 * or its surface comes back after such a stop. A recreation of the surface is none of these.
 */
@Composable
fun LaunchOverlay(
    modifier: Modifier = Modifier,
    viewModel: LaunchOverlayViewModel = hiltViewModel()
) {
    val progress by viewModel.progress.collectAsState()
    val promptFocus by viewModel.promptFocus.collectAsState()
    val recapCursor by viewModel.recapCursorState.collectAsState()
    val hasPresentationSurface by viewModel.hasPresentationSurface.collectAsState()
    val gameDisplayId by viewModel.gameDisplayId.collectAsState()
    val context = LocalContext.current
    val surfaceDisplayId = ContextCompat.getDisplayOrDefault(context).displayId
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel, context) {
        val activity = context.findActivity()
        fun report(signal: LaunchHostSignal) =
            viewModel.hostSignal(signal, activity?.isChangingConfigurations == true)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> report(LaunchHostSignal.STARTED)
                Lifecycle.Event.ON_STOP -> report(LaunchHostSignal.STOPPED)
                else -> Unit
            }
        }
        report(LaunchHostSignal.COMPOSED)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            report(LaunchHostSignal.LEFT_COMPOSITION)
        }
    }
    ModalInputEffect(active = progress != null, handler = viewModel.inputHandler)
    progress?.step?.let { step ->
        val cancelHint = stringResource(R.string.ui_launch_overlay_hint_cancel)
        val showsCancelHint = step !is LaunchStep.Recap && step != LaunchStep.Launching
        FooterHints(
            hints = if (showsCancelHint) listOf(InputButton.B to cancelHint) else emptyList(),
            reservesSpace = false
        )
    }

    LaunchOverlayContent(
        progress = progress,
        promptFocus = promptFocus,
        recapCursor = recapCursor,
        surface = RecapSurface.CONTROL,
        gameOpensHere = opensOnSurface(hasPresentationSurface, gameDisplayId, surfaceDisplayId),
        onAnswer = viewModel::answer,
        onCancel = { viewModel.cancel() },
        onRecapCurrent = viewModel::recapCurrent,
        modifier = modifier
    )
}

@Composable
fun LaunchOverlayMirror(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val entryPoint = remember { launchProgressEntryPoint(context) }
    val tracker = remember(entryPoint) { entryPoint.launchProgressTracker() }
    val cursorStore = remember(entryPoint) { entryPoint.launchRecapCursor() }
    val progress by tracker.progress.collectAsState()
    val recapCursor by cursorStore.state.collectAsState()
    LaunchOverlayContent(
        progress = progress?.takeIf { it.step.mirrorsToPresentation },
        promptFocus = NO_PROMPT_FOCUS,
        recapCursor = recapCursor,
        surface = RecapSurface.PRESENTATION,
        gameOpensHere = false,
        onAnswer = tracker::answer,
        onCancel = { tracker.cancel() },
        onRecapCurrent = { index ->
            val count = (tracker.progress.value?.step as? LaunchStep.Recap)?.recap?.unlocks?.size
            if (count != null) cursorStore.centered(index, count)
        },
        modifier = modifier
    )
}

/**
 * The black veil a presentation surface on [displayId] draws while the game opens on it, lifting
 * once the launch leaves LAUNCHING.
 */
@Composable
fun LaunchVeilMirror(
    gameDisplayId: Int?,
    displayId: Int?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tracker = remember { launchProgressEntryPoint(context).launchProgressTracker() }
    val progress by tracker.progress.collectAsState()
    LaunchVeil(
        veiled = veilsDisplay(progress, gameDisplayId, displayId),
        modifier = modifier,
        veiledSinceMs = progress?.stepStartedAtMs
    )
}

/**
 * The blur the screen behind the launch overlay takes while a recap shows, for the surface's
 * content to apply alongside its other modal blurs.
 */
@Composable
fun launchRecapBackdropBlur(): Dp {
    val context = LocalContext.current
    val tracker = remember { launchProgressEntryPoint(context).launchProgressTracker() }
    val progress by tracker.progress.collectAsState()
    val blur by animateDpAsState(
        targetValue = if (progress?.step is LaunchStep.Recap) Motion.blurRadiusDrawer else 0.dp,
        animationSpec = tween(launchOverlayFadeMs()),
        label = "launchRecapBlur"
    )
    return blur
}

/**
 * The launch overlay's fade length, zero under reduced motion.
 */
@Composable
fun launchOverlayFadeMs(): Int =
    if (LocalMotionTier.current == MotionTier.Reduced) 0 else ComponentDefaults.LaunchOverlay.fadeMs

private const val NO_PROMPT_FOCUS = -1

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun LaunchOverlayContent(
    progress: LaunchProgress?,
    promptFocus: Int,
    recapCursor: RecapCursor,
    surface: RecapSurface,
    gameOpensHere: Boolean,
    onAnswer: (LaunchPromptOption) -> Unit,
    onCancel: () -> Unit,
    onRecapCurrent: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var lastShown by remember { mutableStateOf<LaunchProgress?>(null) }
    SideEffect { progress?.let { lastShown = it } }

    val scrimAlpha = if (LocalLauncherTheme.current.isDarkTheme) {
        ComponentDefaults.LaunchOverlay.scrimAlphaDark
    } else {
        ComponentDefaults.LaunchOverlay.scrimAlphaLight
    }
    val scrimColor = MaterialTheme.colorScheme.background.copy(alpha = scrimAlpha)
    val fadeMs = launchOverlayFadeMs()

    AnimatedVisibility(
        visible = progress != null,
        enter = fadeIn(tween(fadeMs)),
        exit = fadeOut(tween(fadeMs)),
        modifier = modifier.fillMaxSize().focusProperties { canFocus = false }
    ) {
        val shown = progress ?: lastShown ?: return@AnimatedVisibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrimColor)
                .clickableNoFocus { },
            contentAlignment = Alignment.Center
        ) {
            when (val step = shown.step) {
                is LaunchStep.Prompt -> PromptContent(
                    gameTitle = shown.gameTitle,
                    prompt = step,
                    focusIndex = promptFocus,
                    onAnswer = onAnswer,
                    onCancel = onCancel
                )
                is LaunchStep.ChooseLauncher -> LauncherChoiceContent(
                    gameTitle = shown.gameTitle ?: stringResource(R.string.ui_sync_overlay_unknown_game),
                    options = step.options,
                    focusIndex = promptFocus,
                    onAnswer = onAnswer,
                    onCancel = onCancel
                )
                is LaunchStep.RunningGame -> RunningGameContent(
                    gameTitle = shown.gameTitle ?: stringResource(R.string.ui_sync_overlay_unknown_game),
                    question = step,
                    focusIndex = promptFocus,
                    onAnswer = onAnswer,
                    onCancel = onCancel
                )
                is LaunchStep.Recap -> if (rememberRecapFullCardSurface() == surface) {
                    LaunchRecapCard(
                        recap = step.recap,
                        cursor = recapCursor,
                        onStart = { onAnswer(LaunchPromptOption.START_GAME) },
                        onCancel = onCancel,
                        onCurrent = onRecapCurrent
                    )
                } else {
                    LaunchRecapCompactCard(
                        recap = step.recap,
                        onStart = { onAnswer(LaunchPromptOption.START_GAME) },
                        onCancel = onCancel
                    )
                }
                else -> CalmLaunchContent(
                    progress = shown,
                    onCancel = onCancel
                )
            }
            LaunchVeil(
                veiled = darkensForLaunch(shown, gameOpensHere),
                veiledSinceMs = shown.stepStartedAtMs
            )
        }
    }
}

@Composable
internal fun LaunchCancelButton(onClick: () -> Unit) {
    ActionButton(
        label = stringResource(R.string.ui_launch_overlay_cancel),
        onClick = onClick,
        glyph = InputButton.B
    )
}

@Composable
private fun PromptContent(
    gameTitle: String?,
    prompt: LaunchStep.Prompt,
    focusIndex: Int,
    onAnswer: (LaunchPromptOption) -> Unit,
    onCancel: () -> Unit
) {
    when (val conflict = prompt.conflict) {
        is SyncProgress.HardcoreConflict -> HardcoreConflictContent(
            gameName = conflict.gameName,
            focusIndex = focusIndex,
            onKeepHardcore = { onAnswer(LaunchPromptOption.KEEP_HARDCORE) },
            onDowngradeToCasual = { onAnswer(LaunchPromptOption.DOWNGRADE_TO_CASUAL) },
            onKeepLocal = { onAnswer(LaunchPromptOption.SKIP_HARDCORE_SAVE) },
            onCancelLaunch = onCancel
        )
        is SyncProgress.LocalModified -> if (conflict.snapshotConflict) {
            SnapshotConflictContent(
                gameTitle = gameTitle ?: stringResource(R.string.ui_sync_overlay_unknown_game),
                focusIndex = focusIndex,
                onChoice = { choice ->
                    onAnswer(
                        when (choice) {
                            SnapshotConflictChoice.MINE -> LaunchPromptOption.KEEP_MINE
                            SnapshotConflictChoice.THEIRS -> LaunchPromptOption.TAKE_THEIRS
                            SnapshotConflictChoice.BRANCH -> LaunchPromptOption.BRANCH_LOCAL
                            SnapshotConflictChoice.REVERT -> LaunchPromptOption.REVERT_LOCAL
                        }
                    )
                },
                onCancelLaunch = onCancel
            )
        } else if (conflict.restoreFailed) {
            RestoreFailedContent(
                gameTitle = gameTitle ?: stringResource(R.string.ui_sync_overlay_unknown_game),
                focusIndex = focusIndex,
                onRestoreSynced = { onAnswer(LaunchPromptOption.RESTORE_SERVER) },
                onLaunchWithoutSync = { onAnswer(LaunchPromptOption.LAUNCH_WITHOUT_SYNC) },
                onCancelLaunch = onCancel
            )
        } else LocalModifiedContent(
            gameTitle = gameTitle ?: stringResource(R.string.ui_sync_overlay_unknown_game),
            focusIndex = focusIndex,
            onKeepLocal = { onAnswer(LaunchPromptOption.APPLY_LOCAL) },
            onRestoreSelected = { onAnswer(LaunchPromptOption.RESTORE_SERVER) },
            onCancelLaunch = onCancel
        )
        else -> Unit
    }
}

@Composable
private fun RunningGameContent(
    gameTitle: String,
    question: LaunchStep.RunningGame,
    focusIndex: Int,
    onAnswer: (LaunchPromptOption) -> Unit,
    onCancel: () -> Unit
) {
    val runningTitle = question.runningGameTitle
    val (title, message) = when (question.kind) {
        RunningGameQuestion.NEWER_SAVE ->
            stringResource(R.string.ui_launch_overlay_running_newer_save_title) to
                stringResource(R.string.ui_launch_overlay_running_newer_save_message, gameTitle)
        RunningGameQuestion.EMULATOR_BUSY ->
            stringResource(R.string.ui_launch_overlay_running_busy_title, question.emulatorName) to
                if (runningTitle != null) {
                    stringResource(R.string.ui_launch_overlay_running_busy_message_game, question.emulatorName, runningTitle)
                } else {
                    stringResource(R.string.ui_launch_overlay_running_busy_message, question.emulatorName)
                }
    }
    LaunchQuestionCard(
        title = title,
        message = message,
        choices = question.options.mapNotNull { option ->
            val (label, description) = when (option) {
                LaunchPromptOption.LOAD_NEWER_SAVE ->
                    R.string.ui_launch_overlay_running_load_newer_label to R.string.ui_launch_overlay_running_load_newer_subtitle
                LaunchPromptOption.KEEP_PLAYING ->
                    R.string.ui_launch_overlay_running_keep_playing_label to R.string.ui_launch_overlay_running_keep_playing_subtitle
                LaunchPromptOption.CLOSE_AND_LAUNCH ->
                    R.string.ui_launch_overlay_running_close_label to R.string.ui_launch_overlay_running_close_subtitle
                else -> return@mapNotNull null
            }
            LaunchQuestionChoice(
                label = stringResource(label),
                description = stringResource(description),
                onClick = { onAnswer(option) }
            )
        },
        focusIndex = focusIndex,
        onCancel = onCancel
    )
}

@Composable
private fun LauncherChoiceContent(
    gameTitle: String,
    options: List<LaunchPromptOption>,
    focusIndex: Int,
    onAnswer: (LaunchPromptOption) -> Unit,
    onCancel: () -> Unit
) {
    LaunchQuestionCard(
        title = stringResource(R.string.ui_launch_overlay_launcher_choice_title),
        message = stringResource(R.string.ui_launch_overlay_launcher_choice_message, gameTitle),
        choices = options.map { option ->
            val launcherName = SteamLaunchers.forPromptOption(option)?.displayName.orEmpty()
            LaunchQuestionChoice(
                label = launcherName,
                description = stringResource(R.string.ui_launch_overlay_launcher_choice_option_subtitle, launcherName),
                onClick = { onAnswer(option) }
            )
        },
        focusIndex = focusIndex,
        onCancel = onCancel
    )
}
