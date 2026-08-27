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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.nendo.argosy.R
import com.nendo.argosy.data.social.SocialNotification
import com.nendo.argosy.data.social.SocialUser
import com.nendo.argosy.ui.components.friends.SocialAvatar
import com.nendo.argosy.ui.theme.Dimens
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
                    text = stringResource(R.string.social_notifications_empty_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.social_notifications_empty_body),
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
                    text = formatRelativeTime(LocalContext.current, notification.updatedAt),
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
                text = stringResource(R.string.social_notifications_unknown_avatar),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Box(modifier = modifier.height(Dimens.avatarMd)) {
        displayed.forEachIndexed { index, user ->
            val avatarSize = if (displayed.size == 1) Dimens.avatarMd else Dimens.avatarXs
            Box(
                modifier = Modifier
                    .offset(x = (index * 14).dp)
                    .zIndex((displayed.size - index).toFloat())
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(Dimens.borderThin),
                contentAlignment = Alignment.Center
            ) {
                SocialAvatar(
                    displayName = user.displayName,
                    avatarColor = user.avatarColor,
                    size = avatarSize
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
                    text = stringResource(R.string.social_notifications_avatar_overflow, overflowCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun formatNotificationText(notification: SocialNotification): String {
    val actors = notification.resolvedActors
    val actorName = actors.firstOrNull()?.displayName ?: stringResource(R.string.social_notifications_default_actor)
    val eventType = notification.eventType ?: stringResource(R.string.social_notifications_event_type_fallback)

    return when (notification.type) {
        "comment" -> {
            when (actors.size) {
                0 -> stringResource(R.string.social_notifications_comment_zero, eventType)
                1 -> stringResource(R.string.social_notifications_comment_one, actorName, eventType)
                2 -> stringResource(R.string.social_notifications_comment_two, actorName, actors[1].displayName, eventType)
                else -> stringResource(
                    R.string.social_notifications_comment_many,
                    actorName,
                    actors[1].displayName,
                    actors.size - 2,
                    eventType
                )
            }
        }
        "like_milestone" -> {
            val likeCount = (notification.metadata?.get("like_count") as? Number)?.toInt()
            val preview = notification.eventPreview ?: stringResource(R.string.social_notifications_like_milestone_default_preview)
            if (likeCount != null) {
                stringResource(R.string.social_notifications_like_milestone_with_count, preview, likeCount)
            } else {
                stringResource(R.string.social_notifications_like_milestone_generic, preview)
            }
        }
        "friend_request" -> stringResource(R.string.social_notifications_friend_request, actorName)
        "friend_accepted" -> stringResource(R.string.social_notifications_friend_accepted, actorName)
        "friend_added" -> stringResource(R.string.social_notifications_friend_added, actorName)
        else -> stringResource(R.string.social_notifications_generic_fallback)
    }
}

private const val MAX_VISIBLE_AVATARS = 4
