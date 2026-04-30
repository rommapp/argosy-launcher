package com.nendo.argosy.ui.screens.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.nendo.argosy.data.social.SocialNotification
import com.nendo.argosy.data.social.SocialUser
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.util.formatRelativeTime

@Composable
fun NotificationsTabContent(
    notifications: List<SocialNotification>,
    focusedIndex: Int,
    listState: LazyListState,
    onNotificationTap: (SocialNotification) -> Unit
) {
    if (notifications.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "No Notifications",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Activity from your posts and friends will appear here",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
    ) {
        itemsIndexed(notifications, key = { _, notif -> notif.id }) { index, notification ->
            NotificationCard(
                notification = notification,
                isFocused = index == focusedIndex,
                onClick = { onNotificationTap(notification) }
            )
        }
    }
}

@Composable
private fun NotificationCard(
    notification: SocialNotification,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val borderModifier = if (isFocused) {
        Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape)
    } else Modifier

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(borderModifier)
            .clickableNoFocus(onClick = onClick),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (notification.isUnread) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActorAvatarStack(
                actors = notification.resolvedActors,
                modifier = Modifier.width(48.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = formatNotificationText(notification),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (notification.isUnread) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatRelativeTime(notification.updatedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }

            if (notification.isUnread) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
private fun ActorAvatarStack(
    actors: List<SocialUser>,
    modifier: Modifier = Modifier
) {
    val displayed = actors.take(MAX_VISIBLE_AVATARS)
    val overflowCount = actors.size - MAX_VISIBLE_AVATARS

    if (displayed.isEmpty()) {
        Box(
            modifier = modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "?",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Box(modifier = modifier.height(40.dp)) {
        displayed.forEachIndexed { index, user ->
            val avatarSize = if (displayed.size == 1) 40.dp else 32.dp
            Box(
                modifier = Modifier
                    .offset(x = (index * 14).dp)
                    .zIndex((displayed.size - index).toFloat())
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(1.dp)
                    .clip(CircleShape)
                    .background(parseColorSafe(user.avatarColor)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.displayName.take(1).uppercase(),
                    style = if (displayed.size == 1) {
                        MaterialTheme.typography.titleSmall
                    } else {
                        MaterialTheme.typography.labelSmall
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (overflowCount > 0) {
            Box(
                modifier = Modifier
                    .offset(x = (displayed.size * 14).dp)
                    .zIndex(0f)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(1.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+$overflowCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun formatNotificationText(notification: SocialNotification): String {
    val actors = notification.resolvedActors
    val actorName = actors.firstOrNull()?.displayName ?: "Someone"
    val eventType = notification.eventType ?: "post"

    return when (notification.type) {
        "comment" -> {
            when (actors.size) {
                0 -> "Someone commented on your $eventType"
                1 -> "$actorName commented on your $eventType"
                2 -> "$actorName and ${actors[1].displayName} commented on your $eventType"
                else -> "$actorName, ${actors[1].displayName}, and ${actors.size - 2} others commented on your $eventType"
            }
        }
        "like_milestone" -> {
            val likeCount = (notification.metadata?.get("like_count") as? Number)?.toInt()
            val preview = notification.eventPreview ?: "Your post"
            if (likeCount != null) {
                "$preview reached $likeCount likes"
            } else {
                "$preview reached a like milestone"
            }
        }
        "friend_request" -> "$actorName sent you a friend request"
        "friend_accepted" -> "$actorName accepted your friend request"
        "friend_added" -> "$actorName added you as a friend"
        else -> "New notification"
    }
}

private fun parseColorSafe(hexColor: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hexColor))
    } catch (e: Exception) {
        Color(0xFF6366F1)
    }
}

private const val MAX_VISIBLE_AVATARS = 4
