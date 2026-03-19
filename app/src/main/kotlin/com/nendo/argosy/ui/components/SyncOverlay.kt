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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.nendo.argosy.domain.model.SyncProgress
import com.nendo.argosy.domain.model.SyncState
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
    val rawStatusMessage = syncProgress?.statusMessage ?: ""

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
                        gameTitle = gameTitle ?: "Game",
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
                append("Channel: ")
                withStyle(SpanStyle(color = LocalLauncherTheme.current.semanticColors.info)) {
                    append(channelName ?: "Latest")
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
            text = syncProgress.statusMessage,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        syncProgress.detailMessage?.let { detail ->
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
            if (isPermissionIssue && onGrantPermission != null) {
                Button(
                    onClick = onGrantPermission,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Grant Permission")
                }
            }

            if (isSavePathNotFound && onOpenSettings != null) {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Configure Save Path")
                }
            } else if (onDisableSync != null && !isSavePathNotFound) {
                OutlinedButton(
                    onClick = onDisableSync,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Disable Sync")
                }
            }
        }

        if (onSkip != null) {
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            TextButton(onClick = onSkip) {
                Text("Skip for Now")
            }
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
            text = "Hardcore Save Conflict",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(Dimens.spacingSm))

        Text(
            text = "The server version of \"$gameName\" no longer meets the requirements for hardcore mode.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Dimens.spacingSm))

        Text(
            text = "This can happen if the save was modified outside of Argosy.",
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
                    label = "Keep Hardcore",
                    subtitle = "Upload local save to server",
                    isFocused = focusIndex == 0,
                    onClick = onKeepHardcore
                )
            }

            if (onDowngradeToCasual != null) {
                ConflictOption(
                    label = "Downgrade to Casual",
                    subtitle = "Use server save, lose hardcore status",
                    isFocused = focusIndex == 1,
                    onClick = onDowngradeToCasual
                )
            }

            if (onKeepLocal != null) {
                ConflictOption(
                    label = "Skip Sync",
                    subtitle = "Launch without syncing",
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
            text = "Local Save Modified",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(Dimens.spacingSm))

        Text(
            text = "Your local save for \"$gameTitle\" has changes that haven't been backed up.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Dimens.spacingSm))

        Text(
            text = "Would you like to keep your local progress or restore the previously selected save?",
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
                    label = "Apply Local",
                    subtitle = "Set local save as the latest version",
                    isFocused = focusIndex == 0,
                    onClick = onKeepLocal
                )
            }

            if (onRestoreSelected != null) {
                ConflictOption(
                    label = "Use Synced Save",
                    subtitle = "Overwrite local with last synced version (backup created)",
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
            text = "Save Conflict",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(Dimens.spacingSm))

        Text(
            text = "\"$gameTitle\" -- ${channelName ?: "Default Save"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Dimens.spacingSm))

        Text(
            text = "The server has a newer save. Overwriting will replace it with your local save.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Dimens.spacingMd))

        val localLabel = formatTimestamp(localTimestamp)
        val serverLabel = formatTimestamp(serverTimestamp)
        val deviceSuffix = if (serverDeviceName != null) " ($serverDeviceName)" else ""

        Row(
            modifier = Modifier.fillMaxWidth(0.85f),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TimestampLabel(
                icon = Icons.Default.PhoneAndroid,
                label = "Local",
                timestamp = localLabel,
                isNewer = localIsNewer
            )
            TimestampLabel(
                icon = Icons.Default.Cloud,
                label = "Server$deviceSuffix",
                timestamp = serverLabel,
                isNewer = !localIsNewer
            )
        }

        Spacer(modifier = Modifier.height(Dimens.spacingLg))

        Column(modifier = Modifier.fillMaxWidth(0.85f)) {
            if (onSkipSync != null) {
                ConflictOption(
                    label = "Skip Sync",
                    subtitle = "Keep your local save, do not upload",
                    isFocused = focusIndex == 0,
                    onClick = onSkipSync
                )
            }
            if (onOverwrite != null) {
                ConflictOption(
                    label = "Overwrite Server",
                    subtitle = "Replace the server save with your local version",
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

private fun formatTimestamp(instant: java.time.Instant): String {
    val now = java.time.Instant.now()
    val duration = java.time.Duration.between(instant, now)
    return when {
        duration.isNegative -> "in the future"
        duration.toMinutes() < 1 -> "just now"
        duration.toHours() < 1 -> "${duration.toMinutes()}m ago"
        duration.toDays() < 1 -> "${duration.toHours()}h ago"
        duration.toDays() < 30 -> "${duration.toDays()}d ago"
        else -> "${duration.toDays() / 30}mo ago"
    }
}

@Composable
private fun ConflictOption(
    label: String,
    subtitle: String,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isFocused) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    val contentColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val subtitleColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
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
        else -> "Syncing..."
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
