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
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sync
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
    onSkip: (() -> Unit)? = null
) {
    val isVisible = syncProgress != null &&
        syncProgress != SyncProgress.Idle &&
        syncProgress != SyncProgress.Skipped

    val isBlocked = syncProgress is SyncProgress.BlockedReason
    val isActiveSync = syncProgress != null && syncProgress !is SyncProgress.Error && !isBlocked

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
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(overlayColor),
            contentAlignment = Alignment.Center
        ) {
            if (isBlocked) {
                BlockedSyncContent(
                    syncProgress = syncProgress as SyncProgress.BlockedReason,
                    gameTitle = gameTitle,
                    onGrantPermission = onGrantPermission,
                    onDisableSync = onDisableSync,
                    onSkip = onSkip
                )
            } else {
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
    onSkip: (() -> Unit)?
) {
    val isPermissionIssue = syncProgress is SyncProgress.BlockedReason.PermissionRequired
    val isAccessDenied = syncProgress is SyncProgress.BlockedReason.AccessDenied

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
            modifier = Modifier.size(56.dp)
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

            if (onDisableSync != null) {
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
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
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
                        .size(64.dp)
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
