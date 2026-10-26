package com.nendo.argosy.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sync
import androidx.compose.ui.focus.focusProperties
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.SnapshotConflictChoice
import com.nendo.argosy.ui.common.labelRes
import com.nendo.argosy.ui.common.subtitleRes
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.domain.model.SyncProgress
import com.nendo.argosy.domain.model.SyncState
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.common.detailMessage
import com.nendo.argosy.ui.common.statusMessage
import com.nendo.argosy.ui.components.launch.LaunchQuestionCard
import com.nendo.argosy.ui.components.launch.LaunchQuestionChoice
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalLauncherTheme

@Composable
fun SyncOverlay(
    syncProgress: SyncProgress?,
    modifier: Modifier = Modifier,
    gameTitle: String? = null,
    onGrantPermission: (() -> Unit)? = null,
    onDisableSync: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onSkip: (() -> Unit)? = null
) {
    val isVisible = syncProgress != null &&
        syncProgress != SyncProgress.Idle &&
        syncProgress != SyncProgress.Skipped

    val isBlocked = syncProgress is SyncProgress.BlockedReason
    val isActiveSync = syncProgress != null && syncProgress !is SyncProgress.Error && !isBlocked

    val spinTransition = if (isActiveSync) {
        rememberInfiniteTransition(label = "sync_rotation")
    } else null
    val displayRotation by spinTransition?.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    ) ?: remember { mutableStateOf(0f) }

    val channelName = syncProgress?.displayChannelName
    val rawStatusMessage = syncProgress?.statusMessage() ?: ""

    var debouncedStatusMessage by remember { mutableStateOf("") }

    LaunchedEffect(rawStatusMessage) {
        if (debouncedStatusMessage.isEmpty() && rawStatusMessage.isNotEmpty()) {
            debouncedStatusMessage = rawStatusMessage
        } else if (rawStatusMessage != debouncedStatusMessage) {
            delay(150)
            debouncedStatusMessage = rawStatusMessage
        }
    }

    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.55f)

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300)),
        modifier = modifier
            .fillMaxSize()
            .focusProperties { canFocus = false }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusProperties { canFocus = false }
                .background(overlayColor),
            contentAlignment = Alignment.Center
        ) {
            when {
                isBlocked -> {
                    BlockedSyncContent(
                        syncProgress = syncProgress as SyncProgress.BlockedReason,
                        gameTitle = gameTitle,
                        onGrantPermission = onGrantPermission,
                        onDisableSync = onDisableSync,
                        onOpenSettings = onOpenSettings,
                        onSkip = onSkip
                    )
                }
                else -> {
                    ActiveSyncContent(
                        channelName = channelName,
                        statusMessage = debouncedStatusMessage,
                        gameTitle = gameTitle,
                        rotation = displayRotation
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveSyncContent(
    channelName: String?,
    statusMessage: String,
    gameTitle: String?,
    rotation: Float
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Sync,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(56.dp)
                .rotate(rotation)
        )

        Spacer(modifier = Modifier.height(Dimens.spacingLg))

        Text(
            text = buildAnnotatedString {
                append(stringResource(R.string.ui_sync_overlay_slot_prefix))
                withStyle(SpanStyle(color = LocalLauncherTheme.current.semanticColors.info)) {
                    append(channelName ?: stringResource(R.string.ui_sync_overlay_slot_default))
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(Dimens.spacingMd))

        AnimatedContent(
            targetState = statusMessage,
            transitionSpec = {
                slideInVertically { -it / 2 } + fadeIn(tween(200)) togetherWith
                    slideOutVertically { it / 2 } + fadeOut(tween(150)) using
                    SizeTransform(clip = true)
            },
            label = "syncStatus"
        ) { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (gameTitle != null) {
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            Text(
                text = gameTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BlockedSyncContent(
    syncProgress: SyncProgress.BlockedReason,
    gameTitle: String?,
    onGrantPermission: (() -> Unit)?,
    onDisableSync: (() -> Unit)?,
    onOpenSettings: (() -> Unit)?,
    onSkip: (() -> Unit)?
) {
    val isPermissionIssue = syncProgress is SyncProgress.BlockedReason.PermissionRequired
    val isAccessDenied = syncProgress is SyncProgress.BlockedReason.AccessDenied

    val showGrantPermission = isPermissionIssue && onGrantPermission != null
    val showOpenSettings = isAccessDenied && onOpenSettings != null
    val showDisableSync = !showOpenSettings && onDisableSync != null

    var nextIndex = 0
    val grantIndex = if (showGrantPermission) nextIndex++ else -1
    val settingsIndex = if (showOpenSettings) nextIndex++ else -1
    val disableIndex = if (showDisableSync) nextIndex++ else -1
    val skipIndex = if (onSkip != null) nextIndex else -1

    val actions = buildList {
        if (showGrantPermission && onGrantPermission != null) add(onGrantPermission)
        if (showOpenSettings && onOpenSettings != null) add(onOpenSettings)
        if (showDisableSync && onDisableSync != null) add(onDisableSync)
        if (onSkip != null) add(onSkip)
    }

    var focusedIndex by remember { mutableIntStateOf(0) }
    val currentActions by rememberUpdatedState(actions)
    val currentOnSkip by rememberUpdatedState(onSkip)

    val inputHandler = remember {
        object : InputHandler {
            override fun onUp(): InputResult {
                focusedIndex = (focusedIndex - 1).coerceAtLeast(0)
                return InputResult.HANDLED
            }

            override fun onDown(): InputResult {
                focusedIndex = (focusedIndex + 1).coerceAtMost(currentActions.lastIndex.coerceAtLeast(0))
                return InputResult.HANDLED
            }

            override fun onConfirm(): InputResult {
                currentActions.getOrNull(focusedIndex)?.invoke()
                return InputResult.HANDLED
            }

            override fun onBack(): InputResult {
                val skip = currentOnSkip ?: return InputResult.HANDLED
                skip()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }

            override fun onLeft(): InputResult = InputResult.HANDLED
            override fun onRight(): InputResult = InputResult.HANDLED
            override fun onMenu(): InputResult = InputResult.HANDLED
            override fun onSecondaryAction(): InputResult = InputResult.HANDLED
            override fun onContextMenu(): InputResult = InputResult.HANDLED
            override fun onPrevSection(): InputResult = InputResult.HANDLED
            override fun onNextSection(): InputResult = InputResult.HANDLED
            override fun onPrevTrigger(): InputResult = InputResult.HANDLED
            override fun onNextTrigger(): InputResult = InputResult.HANDLED
            override fun onSelect(): InputResult = InputResult.HANDLED
            override fun onLeftStickClick(): InputResult = InputResult.HANDLED
            override fun onRightStickClick(): InputResult = InputResult.HANDLED
            override fun onLongConfirm(): InputResult = InputResult.HANDLED
        }
    }

    ModalInputEffect(active = true, handler = inputHandler)

    val icon = when {
        isPermissionIssue -> Icons.Default.Lock
        else -> Icons.Default.Block
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(horizontal = Dimens.spacingXl)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(Dimens.iconXl + Dimens.spacingSm)
        )

        Spacer(modifier = Modifier.height(Dimens.spacingLg))

        Text(
            text = syncProgress.statusMessage(),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        syncProgress.detailMessage()?.let { detail ->
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        if (gameTitle != null) {
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            Text(
                text = gameTitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(Dimens.spacingLg))

        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            if (showGrantPermission && onGrantPermission != null) {
                ActionButton(
                    label = stringResource(R.string.ui_sync_overlay_blocked_grant),
                    onClick = onGrantPermission,
                    focused = focusedIndex == grantIndex,
                    primary = true,
                    modifier = Modifier.weight(1f)
                )
            }

            if (showOpenSettings && onOpenSettings != null) {
                ActionButton(
                    label = stringResource(R.string.ui_sync_overlay_blocked_configure_path),
                    onClick = onOpenSettings,
                    focused = focusedIndex == settingsIndex,
                    primary = true,
                    modifier = Modifier.weight(1f)
                )
            } else if (showDisableSync && onDisableSync != null) {
                ActionButton(
                    label = stringResource(R.string.ui_sync_overlay_blocked_disable),
                    onClick = onDisableSync,
                    focused = focusedIndex == disableIndex,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (onSkip != null) {
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            ActionButton(
                label = stringResource(R.string.ui_sync_overlay_blocked_skip),
                onClick = onSkip,
                focused = focusedIndex == skipIndex
            )
        }
    }
}

@Composable
internal fun HardcoreConflictContent(
    gameName: String,
    focusIndex: Int,
    onKeepHardcore: (() -> Unit)?,
    onDowngradeToCasual: (() -> Unit)?,
    onKeepLocal: (() -> Unit)?,
    onCancelLaunch: () -> Unit
) {
    val keepLabel = stringResource(R.string.ui_sync_overlay_hardcore_keep)
    val keepDescription = stringResource(R.string.ui_sync_overlay_hardcore_keep_subtitle)
    val downgradeLabel = stringResource(R.string.ui_sync_overlay_hardcore_downgrade)
    val downgradeDescription = stringResource(R.string.ui_sync_overlay_hardcore_downgrade_subtitle)
    val skipLabel = stringResource(R.string.ui_sync_overlay_hardcore_skip)
    val skipDescription = stringResource(R.string.ui_sync_overlay_hardcore_skip_subtitle)
    LaunchQuestionCard(
        title = stringResource(R.string.ui_sync_overlay_hardcore_title),
        message = stringResource(R.string.ui_sync_overlay_hardcore_message, gameName),
        note = stringResource(R.string.ui_sync_overlay_hardcore_note),
        choices = buildList<LaunchQuestionChoice> {
            if (onKeepHardcore != null) add(LaunchQuestionChoice(keepLabel, keepDescription, onKeepHardcore))
            if (onDowngradeToCasual != null) {
                add(LaunchQuestionChoice(downgradeLabel, downgradeDescription, onDowngradeToCasual))
            }
            if (onKeepLocal != null) add(LaunchQuestionChoice(skipLabel, skipDescription, onKeepLocal))
        },
        focusIndex = focusIndex,
        onCancel = onCancelLaunch
    )
}

@Composable
internal fun LocalModifiedContent(
    gameTitle: String,
    focusIndex: Int,
    onKeepLocal: (() -> Unit)?,
    onRestoreSelected: (() -> Unit)?,
    onCancelLaunch: () -> Unit
) {
    val applyLabel = stringResource(R.string.ui_sync_overlay_local_modified_apply)
    val applyDescription = stringResource(R.string.ui_sync_overlay_local_modified_apply_subtitle)
    val restoreLabel = stringResource(R.string.ui_sync_overlay_local_modified_restore)
    val restoreDescription = stringResource(R.string.ui_sync_overlay_local_modified_restore_subtitle)
    LaunchQuestionCard(
        title = stringResource(R.string.ui_sync_overlay_local_modified_title),
        message = stringResource(R.string.ui_sync_overlay_local_modified_message, gameTitle),
        note = stringResource(R.string.ui_sync_overlay_local_modified_question),
        choices = buildList<LaunchQuestionChoice> {
            if (onKeepLocal != null) add(LaunchQuestionChoice(applyLabel, applyDescription, onKeepLocal))
            if (onRestoreSelected != null) {
                add(LaunchQuestionChoice(restoreLabel, restoreDescription, onRestoreSelected))
            }
        },
        focusIndex = focusIndex,
        onCancel = onCancelLaunch
    )
}

@Composable
internal fun SnapshotConflictContent(
    gameTitle: String,
    focusIndex: Int,
    onChoice: (SnapshotConflictChoice) -> Unit,
    onCancelLaunch: () -> Unit
) {
    LaunchQuestionCard(
        title = stringResource(R.string.ui_sync_overlay_snapshot_conflict_title),
        message = stringResource(R.string.ui_sync_overlay_snapshot_conflict_message, gameTitle),
        choices = SnapshotConflictChoice.entries.map { choice ->
            LaunchQuestionChoice(
                label = stringResource(choice.labelRes),
                description = stringResource(choice.subtitleRes),
                onClick = { onChoice(choice) }
            )
        },
        focusIndex = focusIndex,
        onCancel = onCancelLaunch
    )
}

@Composable
internal fun RestoreFailedContent(
    gameTitle: String,
    focusIndex: Int,
    onRestoreSynced: () -> Unit,
    onLaunchWithoutSync: () -> Unit,
    onCancelLaunch: () -> Unit
) {
    LaunchQuestionCard(
        title = stringResource(R.string.ui_sync_overlay_restore_failed_title),
        message = stringResource(R.string.ui_sync_overlay_restore_failed_message, gameTitle),
        choices = listOf(
            LaunchQuestionChoice(
                label = stringResource(R.string.ui_sync_overlay_local_modified_restore),
                description = stringResource(R.string.ui_sync_overlay_local_modified_restore_subtitle),
                onClick = onRestoreSynced
            ),
            LaunchQuestionChoice(
                label = stringResource(R.string.ui_sync_overlay_restore_failed_launch),
                description = stringResource(R.string.ui_sync_overlay_restore_failed_launch_subtitle),
                onClick = onLaunchWithoutSync
            )
        ),
        focusIndex = focusIndex,
        onCancel = onCancelLaunch
    )
}
