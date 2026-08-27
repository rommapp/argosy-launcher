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
import com.nendo.argosy.ui.util.clickableNoFocus
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
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import com.nendo.argosy.R
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
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.util.formatRelativeTimeShort

@Composable
fun SyncOverlay(
    syncProgress: SyncProgress?,
    modifier: Modifier = Modifier,
    gameTitle: String? = null,
    onGrantPermission: (() -> Unit)? = null,
    onDisableSync: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
    onKeepHardcore: (() -> Unit)? = null,
    onDowngradeToCasual: (() -> Unit)? = null,
    onKeepLocal: (() -> Unit)? = null,
    onKeepLocalModified: (() -> Unit)? = null,
    onRestoreSelected: (() -> Unit)? = null,
    hardcoreConflictFocusIndex: Int = 0,
    localModifiedFocusIndex: Int = 0
) {
    val isVisible = syncProgress != null &&
        syncProgress != SyncProgress.Idle &&
        syncProgress != SyncProgress.Skipped

    val isBlocked = syncProgress is SyncProgress.BlockedReason
    val isHardcoreConflict = syncProgress is SyncProgress.HardcoreConflict
    val isLocalModified = syncProgress is SyncProgress.LocalModified
    val isPostSessionConflict = syncProgress is SyncProgress.PostSessionConflict
    val isActiveSync = syncProgress != null && syncProgress !is SyncProgress.Error && !isBlocked && !isHardcoreConflict && !isLocalModified && !isPostSessionConflict

    val infiniteTransition = rememberInfiniteTransition(label = "sync_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val displayRotation = if (isActiveSync) rotation else 0f

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
                isHardcoreConflict && syncProgress is SyncProgress.HardcoreConflict -> {
                    HardcoreConflictContent(
                        gameName = syncProgress.gameName,
                        focusIndex = hardcoreConflictFocusIndex,
                        onKeepHardcore = onKeepHardcore,
                        onDowngradeToCasual = onDowngradeToCasual,
                        onKeepLocal = onKeepLocal
                    )
                }
                isLocalModified && syncProgress is SyncProgress.LocalModified -> {
                    LocalModifiedContent(
                        gameTitle = gameTitle
                            ?: stringResource(R.string.ui_sync_overlay_unknown_game),
                        focusIndex = localModifiedFocusIndex,
                        onKeepLocal = onKeepLocalModified,
                        onRestoreSelected = onRestoreSelected
                    )
                }
                isPostSessionConflict && syncProgress is SyncProgress.PostSessionConflict -> {
                    PostSessionConflictContent(
                        gameTitle = syncProgress.gameTitle,
                        channelName = syncProgress.channelName,
                        localTimestamp = syncProgress.localTimestamp,
                        serverTimestamp = syncProgress.serverTimestamp,
                        serverDeviceName = syncProgress.serverDeviceName,
                        focusIndex = localModifiedFocusIndex,
                        onSkipSync = syncProgress.onSkipSync,
                        onOverwrite = syncProgress.onOverwrite
                    )
                }
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
    val isSavePathNotFound = syncProgress is SyncProgress.BlockedReason.SavePathNotFound

    val showGrantPermission = isPermissionIssue && onGrantPermission != null
    val showOpenSettings = (isSavePathNotFound || (isAccessDenied && onOpenSettings != null)) && onOpenSettings != null
    val showDisableSync = !showOpenSettings && onDisableSync != null && !isSavePathNotFound

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
        isAccessDenied -> Icons.Default.Block
        else -> Icons.Default.FolderOff
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
private fun HardcoreConflictContent(
    gameName: String,
    focusIndex: Int,
    onKeepHardcore: (() -> Unit)?,
    onDowngradeToCasual: (() -> Unit)?,
    onKeepLocal: (() -> Unit)?
) {
    val warningColor = Color(0xFFFF9800)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(horizontal = Dimens.spacingXl)
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = warningColor,
            modifier = Modifier.size(Dimens.iconXl + Dimens.spacingSm)
        )

        Spacer(modifier = Modifier.height(Dimens.spacingLg))

        Text(
            text = stringResource(R.string.ui_sync_overlay_hardcore_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(Dimens.spacingSm))

        Text(
            text = stringResource(R.string.ui_sync_overlay_hardcore_message, gameName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Dimens.spacingSm))

        Text(
            text = stringResource(R.string.ui_sync_overlay_hardcore_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Dimens.spacingLg))

        Column(
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            if (onKeepHardcore != null) {
                ConflictOption(
                    label = stringResource(R.string.ui_sync_overlay_hardcore_keep),
                    subtitle = stringResource(R.string.ui_sync_overlay_hardcore_keep_subtitle),
                    isFocused = focusIndex == 0,
                    onClick = onKeepHardcore
                )
            }

            if (onDowngradeToCasual != null) {
                ConflictOption(
                    label = stringResource(R.string.ui_sync_overlay_hardcore_downgrade),
                    subtitle = stringResource(
                        R.string.ui_sync_overlay_hardcore_downgrade_subtitle
                    ),
                    isFocused = focusIndex == 1,
                    onClick = onDowngradeToCasual
                )
            }

            if (onKeepLocal != null) {
                ConflictOption(
                    label = stringResource(R.string.ui_sync_overlay_hardcore_skip),
                    subtitle = stringResource(R.string.ui_sync_overlay_hardcore_skip_subtitle),
                    isFocused = focusIndex == 2,
                    onClick = onKeepLocal
                )
            }
        }
    }
}

@Composable
private fun LocalModifiedContent(
    gameTitle: String,
    focusIndex: Int,
    onKeepLocal: (() -> Unit)?,
    onRestoreSelected: (() -> Unit)?
) {
    val warningColor = Color(0xFFFF9800)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(horizontal = Dimens.spacingXl)
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = warningColor,
            modifier = Modifier.size(Dimens.iconXl + Dimens.spacingSm)
        )

        Spacer(modifier = Modifier.height(Dimens.spacingLg))

        Text(
            text = stringResource(R.string.ui_sync_overlay_local_modified_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(Dimens.spacingSm))

        Text(
            text = stringResource(R.string.ui_sync_overlay_local_modified_message, gameTitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Dimens.spacingSm))

        Text(
            text = stringResource(R.string.ui_sync_overlay_local_modified_question),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Dimens.spacingLg))

        Column(
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            if (onKeepLocal != null) {
                ConflictOption(
                    label = stringResource(R.string.ui_sync_overlay_local_modified_apply),
                    subtitle = stringResource(
                        R.string.ui_sync_overlay_local_modified_apply_subtitle
                    ),
                    isFocused = focusIndex == 0,
                    onClick = onKeepLocal
                )
            }

            if (onRestoreSelected != null) {
                ConflictOption(
                    label = stringResource(R.string.ui_sync_overlay_local_modified_restore),
                    subtitle = stringResource(
                        R.string.ui_sync_overlay_local_modified_restore_subtitle
                    ),
                    isFocused = focusIndex == 1,
                    onClick = onRestoreSelected
                )
            }
        }
    }
}

@Composable
private fun PostSessionConflictContent(
    gameTitle: String,
    channelName: String?,
    localTimestamp: java.time.Instant,
    serverTimestamp: java.time.Instant,
    serverDeviceName: String?,
    focusIndex: Int,
    onSkipSync: (() -> Unit)?,
    onOverwrite: (() -> Unit)?
) {
    val warningColor = Color(0xFFFF9800)
    val localIsNewer = localTimestamp.isAfter(serverTimestamp)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(horizontal = Dimens.spacingXl)
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = warningColor,
            modifier = Modifier.size(Dimens.iconXl + Dimens.spacingSm)
        )

        Spacer(modifier = Modifier.height(Dimens.spacingLg))

        Text(
            text = stringResource(R.string.ui_sync_overlay_post_session_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(Dimens.spacingSm))

        Text(
            text = stringResource(
                R.string.ui_sync_overlay_post_session_subject,
                gameTitle,
                channelName ?: stringResource(R.string.ui_sync_overlay_post_session_default_slot)
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Dimens.spacingSm))

        Text(
            text = stringResource(R.string.ui_sync_overlay_post_session_message),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Dimens.spacingMd))

        val context = LocalContext.current
        val localLabel = formatRelativeTimeShort(context, localTimestamp)
        val serverLabel = formatRelativeTimeShort(context, serverTimestamp)
        val serverLabelText = if (serverDeviceName != null) {
            stringResource(R.string.ui_sync_overlay_post_session_server_named, serverDeviceName)
        } else {
            stringResource(R.string.ui_sync_overlay_post_session_server)
        }

        Row(
            modifier = Modifier.fillMaxWidth(0.85f),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TimestampLabel(
                icon = Icons.Default.PhoneAndroid,
                label = stringResource(R.string.ui_sync_overlay_post_session_local),
                timestamp = localLabel,
                isNewer = localIsNewer
            )
            TimestampLabel(
                icon = Icons.Default.Cloud,
                label = serverLabelText,
                timestamp = serverLabel,
                isNewer = !localIsNewer
            )
        }

        Spacer(modifier = Modifier.height(Dimens.spacingLg))

        Column(modifier = Modifier.fillMaxWidth(0.85f)) {
            if (onSkipSync != null) {
                ConflictOption(
                    label = stringResource(R.string.ui_sync_overlay_post_session_skip),
                    subtitle = stringResource(
                        R.string.ui_sync_overlay_post_session_skip_subtitle
                    ),
                    isFocused = focusIndex == 0,
                    onClick = onSkipSync
                )
            }
            if (onOverwrite != null) {
                ConflictOption(
                    label = stringResource(R.string.ui_sync_overlay_post_session_overwrite),
                    subtitle = stringResource(
                        R.string.ui_sync_overlay_post_session_overwrite_subtitle
                    ),
                    isFocused = focusIndex == 1,
                    onClick = onOverwrite
                )
            }
        }
    }
}

@Composable
private fun TimestampLabel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    timestamp: String,
    isNewer: Boolean
) {
    val tint = if (isNewer) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(Dimens.iconMd))
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
        Text(timestamp, style = MaterialTheme.typography.bodySmall, color = tint)
    }
}

@Composable
private fun ConflictOption(
    label: String,
    subtitle: String,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val focusAccent = LocalArgosyTheme.current.focusAccent
    val backgroundColor = if (isFocused) {
        focusAccent.copy(alpha = 0.15f)
    } else {
        Color.Transparent
    }
    val contentColor = if (isFocused) {
        lerp(focusAccent, Color.White, 0.45f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val subtitleColor = if (isFocused) {
        lerp(focusAccent, Color.White, 0.45f).copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(backgroundColor)
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = subtitleColor
        )
    }
}

@Deprecated(
    "Use SyncOverlay with SyncProgress instead",
    ReplaceWith("SyncOverlay(syncProgress, modifier, gameTitle)")
)
@Composable
fun SyncOverlay(
    syncState: SyncState?,
    modifier: Modifier = Modifier,
    gameTitle: String? = null
) {
    val isVisible = syncState != null && syncState != SyncState.Idle
    val isActiveSync = syncState != null && syncState !is SyncState.Error

    val infiniteTransition = rememberInfiniteTransition(label = "sync_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val displayRotation = if (isActiveSync) rotation else 0f

    val message = when (syncState) {
        is SyncState.Error -> syncState.message
        else -> stringResource(R.string.ui_sync_overlay_legacy_status)
    }

    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)

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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(Dimens.iconXl + Dimens.spacingMd)
                        .rotate(displayRotation)
                )
                Spacer(modifier = Modifier.height(Dimens.spacingMd))
                Text(
                    text = message,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
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
    }
}
