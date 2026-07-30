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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nendo.argosy.data.netplay.NetplayPreflightResult
import com.nendo.argosy.data.social.Friend
import com.nendo.argosy.data.social.NetplaySession
import com.nendo.argosy.data.social.PresenceStatus
import com.nendo.argosy.ui.components.friends.SocialAvatar
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.util.clickableNoFocus

sealed class FriendNetplayJoinState {
    data object Loading : FriendNetplayJoinState()
    data class Joinable(val filePath: String) : FriendNetplayJoinState()
    data object RomNotFound : FriendNetplayJoinState()
    data object RomVersionMismatch : FriendNetplayJoinState()
    data object CoreVersionMismatch : FriendNetplayJoinState()
    data object CoreNotSupported : FriendNetplayJoinState()
    data object SessionBusy : FriendNetplayJoinState()
}

fun NetplayPreflightResult.toFriendJoinState(): FriendNetplayJoinState = when (this) {
    is NetplayPreflightResult.Joinable -> FriendNetplayJoinState.Joinable(localFilePath)
    NetplayPreflightResult.RomNotFound -> FriendNetplayJoinState.RomNotFound
    NetplayPreflightResult.RomVersionMismatch -> FriendNetplayJoinState.RomVersionMismatch
    NetplayPreflightResult.CoreVersionMismatch -> FriendNetplayJoinState.CoreVersionMismatch
    NetplayPreflightResult.CoreNotSupported -> FriendNetplayJoinState.CoreNotSupported
}

/**
 * Maps a combined friends-tab focus index (received, then sent, then accepted)
 * to the LazyColumn item index, accounting for the section header rows.
 */
fun friendsFocusToItemIndex(
    focusIndex: Int,
    receivedCount: Int,
    sentCount: Int,
    acceptedCount: Int
): Int {
    var item = 0
    var remaining = focusIndex
    if (receivedCount > 0) {
        item += 1
        if (remaining < receivedCount) return item + remaining
        item += receivedCount
        remaining -= receivedCount
    }
    if (sentCount > 0) {
        item += 1
        if (remaining < sentCount) return item + remaining
        item += sentCount
        remaining -= sentCount
    }
    val hasRequests = receivedCount > 0 || sentCount > 0
    if (acceptedCount > 0 && hasRequests) {
        item += 1
    }
    return item + remaining
}

@Composable
fun FriendsTabContent(
    friends: List<Friend>,
    receivedRequests: List<Friend>,
    sentRequests: List<Friend>,
    focusedIndex: Int,
    listState: LazyListState,
    onViewProfile: (String) -> Unit,
    onToggleFavorite: (String) -> Unit = {},
    onAcceptRequest: (String) -> Unit = {},
    onDeclineRequest: (String) -> Unit = {},
    onCancelRequest: (String) -> Unit = {},
    netplayPreflight: (suspend (NetplaySession) -> NetplayPreflightResult)? = null,
    onJoinNetplaySession: ((Friend, NetplaySession) -> Unit)? = null,
    onJoinableChanged: ((String, Boolean) -> Unit)? = null
) {
    if (friends.isEmpty() && receivedRequests.isEmpty() && sentRequests.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "No Friends Yet",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(Dimens.spacingSm))
                Text(
                    text = "Add friends using your friend code in Settings",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
        return
    }

    val hasRequests = receivedRequests.isNotEmpty() || sentRequests.isNotEmpty()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        contentPadding = PaddingValues(horizontal = Dimens.spacingLg, vertical = Dimens.spacingMd)
    ) {
        if (receivedRequests.isNotEmpty()) {
            item(key = "friends_hdr_incoming") { RequestsSectionHeader(text = "Incoming Requests") }
            itemsIndexed(receivedRequests, key = { _, friend -> friend.id }) { index, friend ->
                RequestCard(
                    friend = friend,
                    isFocused = index == focusedIndex,
                    incoming = true,
                    onAccept = { onAcceptRequest(friend.id) },
                    onDecline = { onDeclineRequest(friend.id) },
                    onCancel = {}
                )
            }
        }

        if (sentRequests.isNotEmpty()) {
            val base = receivedRequests.size
            item(key = "friends_hdr_sent") { RequestsSectionHeader(text = "Sent Requests") }
            itemsIndexed(sentRequests, key = { _, friend -> friend.id }) { index, friend ->
                RequestCard(
                    friend = friend,
                    isFocused = base + index == focusedIndex,
                    incoming = false,
                    onAccept = {},
                    onDecline = {},
                    onCancel = { onCancelRequest(friend.id) }
                )
            }
        }

        if (friends.isNotEmpty()) {
            val base = receivedRequests.size + sentRequests.size
            if (hasRequests) {
                item(key = "friends_hdr_accepted") { RequestsSectionHeader(text = "Friends") }
            }
            itemsIndexed(friends, key = { _, friend -> friend.id }) { index, friend ->
                FriendCard(
                    friend = friend,
                    isFocused = base + index == focusedIndex,
                    onClick = { onViewProfile(friend.id) },
                    onToggleFavorite = { onToggleFavorite(friend.id) },
                    netplayPreflight = netplayPreflight,
                    onJoinNetplaySession = onJoinNetplaySession,
                    onJoinableChanged = onJoinableChanged
                )
            }
        }
    }
}

@Composable
private fun RequestsSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = Dimens.spacingXs)
    )
}

@Composable
private fun RequestCard(
    friend: Friend,
    isFocused: Boolean,
    incoming: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onCancel: () -> Unit
) {
    val shape = RoundedCornerShape(Dimens.radiusLg)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .argosyFocusIndicators(
                focused = isFocused,
                indicators = FocusIndicators.Ring,
                shape = shape
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.spacingMd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
        ) {
            SocialAvatar(
                displayName = friend.displayName,
                avatarColor = friend.avatarColor,
                size = Dimens.avatarLg,
                avatarPngBase64 = friend.quayPassAvatar
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
            ) {
                Text(
                    text = friend.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "@${friend.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (incoming) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
                    RequestActionButton(label = "Accept", primary = true, onClick = onAccept)
                    RequestActionButton(label = "Decline", primary = false, onClick = onDecline)
                }
            } else {
                RequestActionButton(label = "Cancel", primary = false, onClick = onCancel)
            }
        }
    }
}

@Composable
private fun RequestActionButton(
    label: String,
    primary: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.radiusPanel))
            .background(
                if (primary) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface
            )
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (primary) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun FriendCard(
    friend: Friend,
    isFocused: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    netplayPreflight: (suspend (NetplaySession) -> NetplayPreflightResult)? = null,
    onJoinNetplaySession: ((Friend, NetplaySession) -> Unit)? = null,
    onJoinableChanged: ((String, Boolean) -> Unit)? = null
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
            Box(contentAlignment = Alignment.BottomEnd) {
                SocialAvatar(
                    displayName = friend.displayName,
                    avatarColor = friend.avatarColor,
                    size = Dimens.avatarLg,
                    avatarPngBase64 = friend.quayPassAvatar
                )

                friend.presence?.let { presence ->
                    if (presence != PresenceStatus.OFFLINE) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(presenceColor(presence))
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = friend.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (friend.presence == PresenceStatus.IN_GAME && friend.currentGame != null) {
                            if (friend.currentGame.netplaySession != null) {
                                "Hosting ${friend.currentGame.title}"
                            } else {
                                "Playing ${friend.currentGame.title}"
                            }
                        } else {
                            presenceLabel(friend.presence)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (friend.presence == PresenceStatus.OFFLINE || friend.presence == null) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        } else {
                            presenceColor(friend.presence)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    friend.deviceName?.let { device ->
                        Text(
                            text = device,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val netplaySession = friend.currentGame?.netplaySession
                    if (netplaySession != null) {
                        NetplayFriendBadge()
                    }
                }

                val netplaySession = friend.currentGame?.netplaySession
                if (netplaySession != null && netplayPreflight != null) {
                    FriendNetplayJoinRow(
                        friend = friend,
                        netplaySession = netplaySession,
                        preflight = netplayPreflight,
                        onJoin = { onJoinNetplaySession?.invoke(friend, netplaySession) },
                        onJoinableChanged = onJoinableChanged
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clickableNoFocus(onClick = onToggleFavorite),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (friend.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (friend.isFavorite) "Remove favorite" else "Add favorite",
                    modifier = Modifier.size(20.dp),
                    tint = if (friend.isFavorite) Color(0xFFFBBF24) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }
    }
}

@Composable
private fun NetplayFriendBadge() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF6366F1).copy(alpha = 0.2f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.SportsEsports,
            contentDescription = "Netplay session",
            tint = Color(0xFF6366F1),
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = "Netplay",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF6366F1),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun FriendNetplayJoinRow(
    friend: Friend,
    netplaySession: NetplaySession,
    preflight: suspend (NetplaySession) -> NetplayPreflightResult,
    onJoin: () -> Unit,
    onJoinableChanged: ((String, Boolean) -> Unit)? = null
) {
    var joinState by remember(netplaySession.sessionId, netplaySession.joinable) {
        mutableStateOf<FriendNetplayJoinState>(
            if (!netplaySession.joinable) FriendNetplayJoinState.SessionBusy
            else FriendNetplayJoinState.Loading
        )
    }

    LaunchedEffect(netplaySession.sessionId, netplaySession.joinable) {
        if (netplaySession.joinable) {
            joinState = FriendNetplayJoinState.Loading
            val result = try {
                preflight(netplaySession)
            } catch (e: Exception) {
                NetplayPreflightResult.RomNotFound
            }
            joinState = result.toFriendJoinState()
        } else {
            joinState = FriendNetplayJoinState.SessionBusy
        }
        onJoinableChanged?.invoke(friend.id, joinState is FriendNetplayJoinState.Joinable)
    }

    DisposableEffect(friend.id) {
        onDispose {
            onJoinableChanged?.invoke(friend.id, false)
        }
    }

    Spacer(modifier = Modifier.height(4.dp))
    when (val state = joinState) {
        FriendNetplayJoinState.Loading -> {
            Text(
                text = "Checking...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        is FriendNetplayJoinState.Joinable -> {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickableNoFocus(onClick = onJoin)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Join",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        FriendNetplayJoinState.RomNotFound -> DisabledLabel("You don't have this ROM")
        FriendNetplayJoinState.RomVersionMismatch -> DisabledLabel("Different ROM version")
        FriendNetplayJoinState.CoreVersionMismatch -> DisabledLabel("Update Argosy to match")
        FriendNetplayJoinState.CoreNotSupported -> DisabledLabel("Core unsupported")
        FriendNetplayJoinState.SessionBusy -> DisabledLabel("Session busy")
    }
}

@Composable
private fun DisabledLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun presenceColor(status: PresenceStatus?): Color = when (status) {
    PresenceStatus.ONLINE -> Color(0xFF22C55E)
    PresenceStatus.AWAY -> Color(0xFFFBBF24)
    PresenceStatus.IN_GAME -> Color(0xFF6366F1)
    PresenceStatus.OFFLINE, null -> Color(0xFF6B7280)
}

private fun presenceLabel(status: PresenceStatus?): String = when (status) {
    PresenceStatus.ONLINE -> "Online"
    PresenceStatus.AWAY -> "Away"
    PresenceStatus.IN_GAME -> "In Game"
    PresenceStatus.OFFLINE, null -> "Offline"
}
