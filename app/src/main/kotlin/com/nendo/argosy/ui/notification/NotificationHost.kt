package com.nendo.argosy.ui.notification

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import kotlinx.coroutines.delay

private val PERSISTENT_BAR_HEIGHT = 52.dp
private val FOOTER_CLEARANCE = 56.dp

@Composable
fun NotificationHost(
    manager: NotificationManager,
    modifier: Modifier = Modifier
) {
    val notifications by manager.notifications.collectAsState()
    val persistent by manager.persistentNotification.collectAsState()
    val current = notifications.firstOrNull()

    LaunchedEffect(current?.id) {
        current?.let { notification ->
            delay(notification.duration.ms)
            manager.dismiss(notification.id)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = current != null,
            enter = slideInHorizontally(initialOffsetX = { it }) +
                    fadeIn(animationSpec = tween(200)) +
                    scaleIn(
                        initialScale = 0.92f,
                        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f)
                    ),
            exit = slideOutHorizontally(targetOffsetX = { it / 3 }) +
                   fadeOut(animationSpec = tween(150)) +
                   scaleOut(targetScale = 0.95f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp)
                .padding(bottom = if (persistent != null) PERSISTENT_BAR_HEIGHT + FOOTER_CLEARANCE + 8.dp else FOOTER_CLEARANCE)
        ) {
            current?.let { notification ->
                NotificationBar(notification = notification)
            }
        }

        AnimatedVisibility(
            visible = persistent != null,
            enter = slideInHorizontally(initialOffsetX = { it }) +
                    fadeIn(animationSpec = tween(200)),
            exit = slideOutHorizontally(targetOffsetX = { it / 3 }) +
                   fadeOut(animationSpec = tween(150)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = FOOTER_CLEARANCE)
        ) {
            persistent?.let { notification ->
                PersistentNotificationBar(notification = notification)
            }
        }
    }
}

@Composable
private fun NotificationBar(
    notification: Notification,
    modifier: Modifier = Modifier
) {
    val colors = notificationColors(notification.type)
    val baseColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
    val backgroundColor = colors.tint.copy(alpha = 0.15f).compositeOver(baseColor)
    val textColor = MaterialTheme.colorScheme.onSurface
    val isError = notification.type == NotificationType.ERROR

    Row(
        modifier = modifier
            .widthIn(max = if (isError) 360.dp else 280.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (notification.imagePath != null) {
            AsyncImage(
                model = notification.imagePath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
        } else {
            Icon(
                imageVector = notificationIcon(notification.type),
                contentDescription = null,
                tint = colors.icon,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            val isError = notification.type == NotificationType.ERROR
            Text(
                text = notification.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = textColor,
                maxLines = if (isError) 2 else 1,
                overflow = TextOverflow.Ellipsis
            )
            notification.subtitle?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.7f),
                    maxLines = if (isError) 3 else 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PersistentNotificationBar(
    notification: Notification,
    modifier: Modifier = Modifier
) {
    val baseColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
    val accentColor = MaterialTheme.colorScheme.secondary
    val backgroundColor = accentColor.copy(alpha = 0.10f).compositeOver(baseColor)
    val textColor = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier
            .widthIn(max = 280.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = accentColor,
                strokeWidth = 2.dp
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                notification.subtitle?.let { subtitle ->
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            notification.progress?.let { progress ->
                Text(
                    text = progress.displayText,
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor
                )
            }
        }

        notification.progress?.let { progress ->
            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = accentColor,
                trackColor = textColor.copy(alpha = 0.12f)
            )
        }
    }
}

private data class NotificationColors(
    val icon: Color,
    val tint: Color
)

@Composable
private fun notificationColors(type: NotificationType): NotificationColors {
    val semantic = LocalLauncherTheme.current.semanticColors
    return when (type) {
        NotificationType.SUCCESS -> NotificationColors(
            icon = semantic.success,
            tint = semantic.success
        )
        NotificationType.INFO -> NotificationColors(
            icon = MaterialTheme.colorScheme.primary,
            tint = MaterialTheme.colorScheme.primary
        )
        NotificationType.WARNING -> NotificationColors(
            icon = semantic.warning,
            tint = semantic.warning
        )
        NotificationType.ERROR -> NotificationColors(
            icon = MaterialTheme.colorScheme.error,
            tint = MaterialTheme.colorScheme.error
        )
    }
}

private fun notificationIcon(type: NotificationType): ImageVector {
    return when (type) {
        NotificationType.SUCCESS -> Icons.Default.Check
        NotificationType.INFO -> Icons.Default.Info
        NotificationType.WARNING -> Icons.Default.Warning
        NotificationType.ERROR -> Icons.Default.Close
    }
}
