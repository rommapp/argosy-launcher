package com.nendo.argosy.ui.notification

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

private val PERSISTENT_BAR_HEIGHT = 72.dp

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
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp)
                .padding(bottom = if (persistent != null) PERSISTENT_BAR_HEIGHT + 24.dp else 16.dp)
        ) {
            current?.let { notification ->
                NotificationBar(notification = notification)
            }
        }

        AnimatedVisibility(
            visible = persistent != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 16.dp)
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

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.container)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (notification.imagePath != null) {
            AsyncImage(
                model = notification.imagePath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            Icon(
                imageVector = notificationIcon(notification.type),
                contentDescription = null,
                tint = colors.content,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = notification.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = colors.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            notification.subtitle?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.content.copy(alpha = 0.8f),
                    maxLines = 1,
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
    val containerColor = Color(0xFF2A2A2A)
    val contentColor = Color.White.copy(alpha = 0.9f)
    val accentColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = accentColor,
                strokeWidth = 2.5.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notification.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                notification.subtitle?.let { subtitle ->
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            notification.progress?.let { progress ->
                Text(
                    text = progress.displayText,
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor
                )
            }
        }

        notification.progress?.let { progress ->
            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = accentColor,
                trackColor = contentColor.copy(alpha = 0.15f)
            )
        }
    }
}

private data class NotificationColors(
    val container: Color,
    val content: Color
)

@Composable
private fun notificationColors(type: NotificationType): NotificationColors {
    return when (type) {
        NotificationType.SUCCESS -> NotificationColors(
            container = Color(0xFF1B5E20),
            content = Color.White
        )
        NotificationType.INFO -> NotificationColors(
            container = Color(0xFF1565C0),
            content = Color.White
        )
        NotificationType.WARNING -> NotificationColors(
            container = Color(0xFFF57F17),
            content = Color.Black
        )
        NotificationType.ERROR -> NotificationColors(
            container = Color(0xFFB71C1C),
            content = Color.White
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
