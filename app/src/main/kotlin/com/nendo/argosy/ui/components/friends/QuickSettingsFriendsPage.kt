package com.nendo.argosy.ui.components.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.R
import com.nendo.argosy.data.social.Friend
import com.nendo.argosy.data.social.PresenceStatus
import com.nendo.argosy.data.social.SocialUser
import com.nendo.argosy.ui.components.QuickRowFrame
import com.nendo.argosy.ui.components.QuickSectionHeader
import com.nendo.argosy.ui.components.QuickToggleRow
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.quaypass.QuayPassIcons
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ColorTokens
import com.nendo.argosy.ui.theme.generated.ComponentDefaults

@Composable
fun QuickSettingsFriendsPage(
    state: QuickFriendsState,
    showQuayPass: Boolean,
    quayPassEnabled: Boolean,
    onRowClick: (Int) -> Unit,
    onRowLongClick: (Int) -> Unit,
    onActionClick: (Int, QuickFriendsAction) -> Unit
) {
    val rows = remember(showQuayPass, state) { quickFriendsRows(showQuayPass, state) }
    val listState = rememberLazyListState()

    LaunchedEffect(state.focusIndex, rows.size) {
        if (state.focusIndex in rows.indices) listState.animateScrollToItemCentered(state.focusIndex)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(rows.size, key = { rows[it].key }) { index ->
                val isFocused = index == state.focusIndex
                when (val row = rows[index]) {
                    QuickFriendsRow.Profile -> state.localUser?.let { user ->
                        ProfileBlock(
                            user = user,
                            online = state.socialConnected && state.appearOnline,
                            isFocused = isFocused,
                            onClick = { onRowClick(index) },
                            onLongClick = { onRowLongClick(index) }
                        )
                    }
                    QuickFriendsRow.QuayPass -> QuickToggleRow(
                        icon = if (quayPassEnabled) QuayPassIcons.On else QuayPassIcons.Off,
                        label = stringResource(R.string.ui_quick_settings_quaypass),
                        checked = quayPassEnabled,
                        isFocused = isFocused,
                        onFocus = {},
                        onToggle = { onRowClick(index) }
                    )
                    QuickFriendsRow.AppearOnline -> QuickToggleRow(
                        icon = if (state.appearOnline) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        label = stringResource(R.string.ui_quick_friends_appear_online),
                        checked = state.appearOnline,
                        isFocused = isFocused,
                        onFocus = {},
                        onToggle = { onRowClick(index) }
                    )
                    QuickFriendsRow.Actions -> ActionsRow(
                        focusedAction = state.action.takeIf { isFocused },
                        onAction = { action -> onActionClick(index, action) }
                    )
                    is QuickFriendsRow.Entry -> Column {
                        FriendSectionLabel(
                            position = row.position,
                            onlineCount = state.onlineCount,
                            offlineCount = state.friends.size - state.onlineCount
                        )
                        FriendRow(
                            friend = row.friend,
                            isFocused = isFocused,
                            onClick = { onRowClick(index) },
                            onLongClick = { onRowLongClick(index) }
                        )
                    }
                }
            }
            if (state.socialConnected && state.friends.isEmpty()) {
                item(key = "friendsEmpty") {
                    Text(
                        text = stringResource(R.string.ui_drawer_friends_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalArgosyTheme.current.textDim,
                        modifier = Modifier.padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingMd)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileBlock(
    user: SocialUser,
    online: Boolean,
    isFocused: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    QuickRowFrame(isFocused = isFocused, onClick = onClick, twoLine = true, onLongClick = onLongClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SocialAvatar(
                displayName = user.displayName,
                avatarColor = user.avatarColor,
                size = Dimens.avatarMd,
                showOnlineDot = online,
                avatarUrl = user.avatarUrl,
                userId = user.id
            )
            Spacer(modifier = Modifier.width(Dimens.spacingMd))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = theme.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(
                        if (online) R.string.ui_quick_friends_status_online else R.string.ui_quick_friends_status_offline
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (online) presenceOnlineColor() else theme.textDim
                )
            }
        }
    }
}

@Composable
private fun ActionsRow(
    focusedAction: QuickFriendsAction?,
    onAction: (QuickFriendsAction) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        ActionChoice(
            icon = Icons.Default.QrCode,
            label = stringResource(R.string.ui_quick_friends_my_code),
            focused = focusedAction == QuickFriendsAction.MY_CODE,
            onClick = { onAction(QuickFriendsAction.MY_CODE) },
            modifier = Modifier.weight(1f)
        )
        ActionChoice(
            icon = Icons.Default.PersonAdd,
            label = stringResource(R.string.ui_quick_friends_add_friend),
            focused = focusedAction == QuickFriendsAction.ADD_FRIEND,
            onClick = { onAction(QuickFriendsAction.ADD_FRIEND) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ActionChoice(
    icon: ImageVector,
    label: String,
    focused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val tint = if (focused) theme.focusAccent else theme.textPrimary
    ActionButton(onClick = onClick, modifier = modifier, focused = focused) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(Dimens.iconSm))
        Spacer(modifier = Modifier.width(Dimens.spacingXs))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FriendSectionLabel(position: Int, onlineCount: Int, offlineCount: Int) {
    when {
        position == 0 && onlineCount > 0 -> QuickSectionHeader(
            title = stringResource(R.string.ui_drawer_friends_online, onlineCount)
        )
        position == onlineCount && offlineCount > 0 -> QuickSectionHeader(
            title = stringResource(R.string.ui_drawer_friends_offline, offlineCount)
        )
    }
}

@Composable
private fun FriendRow(
    friend: Friend,
    isFocused: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val online = friend.isOnlineNow
    val presenceColor = if (friend.currentGame?.shownExternalNetplay != null) {
        presenceNetplayColor()
    } else {
        presenceOnlineColor()
    }
    QuickRowFrame(isFocused = isFocused, onClick = onClick, onLongClick = onLongClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SocialAvatar(
                displayName = friend.displayName,
                avatarColor = friend.avatarColor,
                size = Dimens.avatarXs,
                showOnlineDot = online,
                avatarPngBase64 = friend.quayPassAvatar,
                modifier = Modifier.alpha(if (online) 1f else ComponentDefaults.QuickPanel.offlineAvatarAlpha)
            )
            Spacer(modifier = Modifier.width(Dimens.spacingSm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = friend.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (online) theme.textPrimary else theme.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (friend.presence == PresenceStatus.IN_GAME) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = null,
                            modifier = Modifier.size(Dimens.iconXs),
                            tint = presenceColor
                        )
                        Text(
                            text = friendActivityLabel(friend),
                            style = MaterialTheme.typography.labelSmall,
                            color = presenceColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (friend.isFavorite) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = stringResource(R.string.ui_drawer_friend_favorite),
                    modifier = Modifier.size(Dimens.iconXs),
                    tint = ColorTokens.Domain.favoriteStar
                )
            }
        }
    }
}

@Composable
private fun friendActivityLabel(friend: Friend): String {
    val game = friend.currentGame ?: return stringResource(R.string.ui_drawer_friend_in_game)
    return when {
        game.netplaySession != null -> stringResource(R.string.ui_drawer_friend_hosting, game.title)
        game.shownExternalNetplay != null -> stringResource(R.string.ui_drawer_friend_playing_online, game.title)
        else -> stringResource(R.string.ui_drawer_friend_playing, game.title)
    }
}
