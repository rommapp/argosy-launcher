package com.nendo.argosy.ui.components.friends

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import com.nendo.argosy.R
import com.nendo.argosy.data.social.FriendActivity
import com.nendo.argosy.ui.theme.Dimens

internal const val FRIENDS_MAX_STACKED_AVATARS = 3
internal const val FRIENDS_AVATAR_OVERLAP_SHARE = 0.35f
internal const val FRIENDS_LINE_MAX_LINES = 2

/**
 * Stacked avatars and one line naming the friends tied to a game. Friends playing now lead; a game
 * only played recently says so instead. Draws nothing for an empty list.
 */
@Composable
fun FriendsActivityBadge(
    friends: List<FriendActivity>,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    if (friends.isEmpty()) return
    val playing = friends.filter { it.playingNow }
    val shown = playing.ifEmpty { friends }
    val avatarSize = Dimens.iconMd
    val overlap = avatarSize * FRIENDS_AVATAR_OVERLAP_SHARE
    val stacked = shown.take(FRIENDS_MAX_STACKED_AVATARS)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Box(modifier = Modifier.width(avatarSize + (avatarSize - overlap) * (stacked.size - 1))) {
            stacked.forEachIndexed { index, friend ->
                SocialAvatar(
                    displayName = friend.displayName,
                    avatarColor = friend.avatarColor,
                    avatarPngBase64 = friend.quayPassAvatar,
                    userId = friend.friendId,
                    size = avatarSize,
                    modifier = Modifier
                        .offset(x = (avatarSize - overlap) * index)
                        .zIndex((stacked.size - index).toFloat())
                )
            }
        }
        Text(
            text = friendsActivityLine(shown, playingNow = playing.isNotEmpty()),
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            maxLines = FRIENDS_LINE_MAX_LINES,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun friendsActivityLine(friends: List<FriendActivity>, playingNow: Boolean): String {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(friends, playingNow, context, configuration) {
        friendsActivityLine(context.resources, friends, playingNow)
    }
}

internal fun friendsActivityLine(resources: Resources, friends: List<FriendActivity>, playingNow: Boolean): String {
    val lead = friends.first().displayName
    val others = friends.size - 1
    return when {
        playingNow && others == 0 -> resources.getString(R.string.social_friends_activity_playing_one, lead)
        playingNow -> resources.getQuantityString(R.plurals.social_friends_activity_playing_more, others, lead, others)
        others == 0 -> resources.getString(R.string.social_friends_activity_recent_one, lead)
        else -> resources.getQuantityString(R.plurals.social_friends_activity_recent_more, others, lead, others)
    }
}
