package com.nendo.argosy.ui.components.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nendo.argosy.data.social.Friend
import com.nendo.argosy.data.social.PresenceStatus
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ColorTokens

private enum class FriendMenuAction(val label: String, val icon: ImageVector) {
    JOIN("Join", Icons.Default.SportsEsports),
    VIEW_PROFILE("View Profile", Icons.Default.Person),
    MY_CODE("My Friend Code", Icons.Default.QrCode),
    ADD_FRIEND("Add Friend", Icons.Default.PersonAdd)
}

@Composable
fun FriendOptionsModal(
    friend: Friend,
    onJoinSession: (Friend) -> Unit,
    onViewProfile: (Friend) -> Unit,
    onShowFriendCode: () -> Unit,
    onShowAddFriend: () -> Unit,
    onDismiss: () -> Unit
) {
    val inputDispatcher = LocalInputDispatcher.current
    val isJoinable = friend.currentGame?.netplaySession?.joinable == true
    val actions = remember(isJoinable) {
        buildList {
            if (isJoinable) add(FriendMenuAction.JOIN)
            add(FriendMenuAction.VIEW_PROFILE)
            add(FriendMenuAction.MY_CODE)
            add(FriendMenuAction.ADD_FRIEND)
        }
    }
    val focusIndex = remember { mutableIntStateOf(0) }
    val focusedIndex = focusIndex.intValue.coerceIn(0, actions.lastIndex)

    val activate: (FriendMenuAction) -> Unit = { action ->
        when (action) {
            FriendMenuAction.JOIN -> onJoinSession(friend)
            FriendMenuAction.VIEW_PROFILE -> onViewProfile(friend)
            FriendMenuAction.MY_CODE -> onShowFriendCode()
            FriendMenuAction.ADD_FRIEND -> onShowAddFriend()
        }
    }

    val inputHandler = remember(actions, friend, onJoinSession, onViewProfile, onShowFriendCode, onShowAddFriend, onDismiss) {
        object : InputHandler {
            override fun onUp(): InputResult {
                if (focusIndex.intValue > 0) {
                    focusIndex.intValue--
                    return InputResult.HANDLED
                }
                return InputResult.UNHANDLED
            }

            override fun onDown(): InputResult {
                if (focusIndex.intValue < actions.lastIndex) {
                    focusIndex.intValue++
                    return InputResult.HANDLED
                }
                return InputResult.UNHANDLED
            }

            override fun onConfirm(): InputResult {
                activate(actions[focusIndex.intValue.coerceIn(0, actions.lastIndex)])
                return InputResult.HANDLED
            }

            override fun onBack(): InputResult {
                onDismiss()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.pushModal(inputHandler)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            inputDispatcher.removeModal(inputHandler)
        }
    }

    Modal(
        title = friend.displayName,
        titleContent = { FriendMenuHeader(friend) },
        onDismiss = onDismiss,
        footerHints = listOf(
            InputButton.A to "Select",
            InputButton.B to "Close"
        ),
        content = {
            actions.forEachIndexed { index, action ->
                OptionItem(
                    label = action.label,
                    icon = action.icon,
                    isFocused = index == focusedIndex,
                    onClick = { activate(action) }
                )
            }
        }
    )
}

@Composable
private fun FriendMenuHeader(friend: Friend) {
    val isOnline = friend.presence == PresenceStatus.ONLINE || friend.presence == PresenceStatus.IN_GAME
    val isInGame = friend.presence == PresenceStatus.IN_GAME

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
    ) {
        SocialAvatar(
            displayName = friend.displayName,
            avatarColor = friend.avatarColor,
            size = Dimens.iconLg,
            showOnlineDot = isOnline
        )
        Column {
            Text(
                text = friend.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isInGame) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = null,
                        modifier = Modifier.size(Dimens.iconXs),
                        tint = ColorTokens.Domain.Presence.online
                    )
                    Text(
                        text = when {
                            friend.currentGame == null -> "In Game"
                            friend.currentGame.netplaySession != null -> "Hosting ${friend.currentGame.title}"
                            else -> "Playing ${friend.currentGame.title}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = ColorTokens.Domain.Presence.online,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Text(
                    text = when (friend.presence) {
                        PresenceStatus.ONLINE -> "Online"
                        PresenceStatus.AWAY -> "Away"
                        else -> "Offline"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalArgosyTheme.current.textMute
                )
            }
        }
    }
}
