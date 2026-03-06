package com.nendo.argosy.ui.screens.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.unit.dp
import com.nendo.argosy.data.social.SocialUser
import com.nendo.argosy.ui.components.SwitchPreference

private const val ITEM_ACCOUNT_CARD = 0
private const val ITEM_PRIVACY_HEADER = 1
private const val ITEM_ONLINE_STATUS = 2
private const val ITEM_NOW_PLAYING = 3
private const val ITEM_NOTIFICATIONS_HEADER = 4
private const val ITEM_FRIEND_ONLINE = 5
private const val ITEM_FRIEND_PLAYING = 6
private const val ITEM_SUPPRESS_IN_GAME = 7
private const val ITEM_COUNT = 8

fun profileFocusToItemIndex(focusIndex: Int): Int = when (focusIndex) {
    0 -> ITEM_ONLINE_STATUS
    1 -> ITEM_NOW_PLAYING
    2 -> ITEM_FRIEND_ONLINE
    3 -> ITEM_FRIEND_PLAYING
    4 -> ITEM_SUPPRESS_IN_GAME
    else -> ITEM_ONLINE_STATUS
}

@Composable
fun ProfileTabContent(
    user: SocialUser?,
    focusIndex: Int,
    listState: LazyListState,
    onlineStatus: Boolean,
    showNowPlaying: Boolean,
    notifyFriendOnline: Boolean,
    notifyFriendPlaying: Boolean,
    suppressInGame: Boolean,
    onToggleOnlineStatus: (Boolean) -> Unit,
    onToggleShowNowPlaying: (Boolean) -> Unit,
    onToggleNotifyFriendOnline: (Boolean) -> Unit,
    onToggleNotifyFriendPlaying: (Boolean) -> Unit,
    onToggleSuppressInGame: (Boolean) -> Unit
) {
    if (user == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Not connected",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
    ) {
        item { AccountInfoCard(user = user) }

        item { SectionHeader("PRIVACY") }

        item {
            SwitchPreference(
                title = "Online Status",
                subtitle = "Show when you are online",
                isEnabled = onlineStatus,
                isFocused = focusIndex == 0,
                onToggle = { onToggleOnlineStatus(!onlineStatus) }
            )
        }

        item {
            SwitchPreference(
                title = "Show Now Playing",
                subtitle = "Share what you are currently playing",
                isEnabled = showNowPlaying,
                isFocused = focusIndex == 1,
                onToggle = { onToggleShowNowPlaying(!showNowPlaying) }
            )
        }

        item { SectionHeader("NOTIFICATIONS") }

        item {
            SwitchPreference(
                title = "Friend Online",
                subtitle = "Notify when a friend comes online",
                isEnabled = notifyFriendOnline,
                isFocused = focusIndex == 2,
                onToggle = { onToggleNotifyFriendOnline(!notifyFriendOnline) }
            )
        }

        item {
            SwitchPreference(
                title = "Friend Playing",
                subtitle = "Notify when a friend starts a game",
                isEnabled = notifyFriendPlaying,
                isFocused = focusIndex == 3,
                onToggle = { onToggleNotifyFriendPlaying(!notifyFriendPlaying) }
            )
        }

        item {
            SwitchPreference(
                title = "Mute While Playing",
                subtitle = if (suppressInGame) "Friend notifications hidden during gameplay"
                    else "Friend notifications always shown",
                isEnabled = suppressInGame,
                isFocused = focusIndex == 4,
                onToggle = { onToggleSuppressInGame(!suppressInGame) }
            )
        }
    }
}

@Composable
private fun AccountInfoCard(user: SocialUser) {
    val avatarColor = try {
        Color(android.graphics.Color.parseColor(user.avatarColor))
    } catch (e: Exception) {
        Color(0xFF6366F1)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(avatarColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.displayName.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = user.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "@${user.username}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
    )
}
