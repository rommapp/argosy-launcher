package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FeaturedPlayList
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VideoLibrary
import com.nendo.argosy.ui.quaypass.QuayPassIcons
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.nendo.argosy.R
import com.nendo.argosy.data.social.Friend
import com.nendo.argosy.data.social.PresenceStatus
import com.nendo.argosy.data.social.SocialUser
import com.nendo.argosy.ui.ACCOUNTS_SECTION_NAME
import com.nendo.argosy.ui.DRAWER_ACCOUNT_ROW_INDEX
import com.nendo.argosy.ui.DRAWER_NAV_ITEM_OFFSET
import com.nendo.argosy.ui.DrawerItem
import com.nendo.argosy.ui.DrawerModal
import com.nendo.argosy.ui.DrawerState
import com.nendo.argosy.ui.DrawerTab
import com.nendo.argosy.ui.components.friends.AddFriendModal
import com.nendo.argosy.ui.components.friends.FriendCodeModal
import com.nendo.argosy.ui.components.friends.FriendOptionsModal
import com.nendo.argosy.ui.components.friends.FriendsOption
import com.nendo.argosy.ui.components.friends.FriendsOptionsModal
import com.nendo.argosy.ui.components.friends.SocialAvatar
import com.nendo.argosy.ui.primitives.InputGlyph
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.navigation.Screen

@Composable
fun MainDrawer(
    items: List<DrawerItem>,
    currentRoute: String?,
    drawerState: DrawerState,
    isOpen: Boolean,
    onNavigate: (String) -> Unit,
    onShowFriendCode: () -> Unit,
    onShowAddFriend: () -> Unit,
    onDismissModal: () -> Unit,
    onRegenerateFriendCode: () -> Unit,
    onAddFriendByCode: (String) -> Unit,
    onJoinFriendSession: (Friend) -> Unit,
    onSelectTab: (DrawerTab) -> Unit,
    onHintClick: ((InputButton) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = Dimens.spacingLg)
        ) {
            DrawerStatusBar(
                isRommConnected = drawerState.rommConnected,
                localUser = drawerState.localUser,
                localAvatarDoodle = drawerState.localAvatarDoodle,
                rommUsername = drawerState.rommUsername,
                isFocused = drawerState.currentTab == DrawerTab.NAVIGATION &&
                    drawerState.navFocusIndex == DRAWER_ACCOUNT_ROW_INDEX,
                onOpenAccounts = {
                    onNavigate(Screen.Settings.createRoute(section = ACCOUNTS_SECTION_NAME))
                }
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = Dimens.spacingLg, vertical = Dimens.radiusLg),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            if (drawerState.socialConnected) {
                TabHeader(currentTab = drawerState.currentTab, onSelectTab = onSelectTab)
                Spacer(modifier = Modifier.height(Dimens.spacingSm))
            }

            when (drawerState.currentTab) {
                DrawerTab.NAVIGATION -> {
                    NavigationContent(
                        items = items,
                        currentRoute = currentRoute,
                        focusedIndex = drawerState.navFocusIndex - DRAWER_NAV_ITEM_OFFSET,
                        downloadCount = drawerState.downloadCount,
                        saveSyncAttentionCount = drawerState.saveSyncAttentionCount,
                        isRommConnected = drawerState.rommConnected,
                        onNavigate = onNavigate,
                        modifier = Modifier.weight(1f)
                    )
                }
                DrawerTab.FRIENDS -> {
                    FriendsContent(
                        friends = drawerState.friends,
                        focusedIndex = drawerState.friendsFocusIndex,
                        onJoinSession = onJoinFriendSession,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (drawerState.currentTab == DrawerTab.FRIENDS) {
                FooterSpacer()
            }
        }
    }

    if (isOpen) {
        val focusedFriend = drawerState.friends.getOrNull(drawerState.friendsFocusIndex)
        val focusedIsJoinable = focusedFriend?.currentGame?.netplaySession?.joinable == true
        FooterHints(
            hints = if (drawerState.currentTab == DrawerTab.FRIENDS) {
                buildList {
                    add(InputButton.Y to "Favorite")
                    add(InputButton.X to "Options")
                    if (focusedIsJoinable) add(InputButton.A to "Join")
                }
            } else {
                emptyList()
            },
            onHintClick = onHintClick
        )
    }

    when (val modal = drawerState.modal) {
        is DrawerModal.FriendOptions -> {
            val friend = drawerState.friends.firstOrNull { it.id == modal.friendId }
            if (friend != null) {
                FriendOptionsModal(
                    friend = friend,
                    onJoinSession = {
                        onDismissModal()
                        onJoinFriendSession(it)
                    },
                    onViewProfile = {
                        onDismissModal()
                        onNavigate(Screen.UserProfile.createRoute(it.id))
                    },
                    onShowFriendCode = {
                        onDismissModal()
                        onShowFriendCode()
                    },
                    onShowAddFriend = {
                        onDismissModal()
                        onShowAddFriend()
                    },
                    onDismiss = onDismissModal
                )
            } else {
                LaunchedEffect(modal) { onDismissModal() }
            }
        }
        DrawerModal.FriendsOptions -> {
            FriendsOptionsModal(
                onSelectOption = { option ->
                    onDismissModal()
                    when (option) {
                        FriendsOption.ADD_FRIEND -> onShowAddFriend()
                        FriendsOption.SHOW_CODE -> onShowFriendCode()
                    }
                },
                onDismiss = onDismissModal
            )
        }
        DrawerModal.FriendCode -> {
            FriendCodeModal(
                code = drawerState.friendCode,
                url = drawerState.friendCodeUrl,
                onRegenerate = onRegenerateFriendCode,
                onDismiss = onDismissModal
            )
        }
        DrawerModal.AddFriend -> {
            AddFriendModal(
                onSubmit = { code ->
                    onAddFriendByCode(code)
                    onDismissModal()
                },
                onDismiss = onDismissModal
            )
        }
        DrawerModal.None -> {}
    }
}

@Composable
private fun TabHeader(currentTab: DrawerTab, onSelectTab: (DrawerTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingLg),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        InputGlyph(
            button = InputButton.LB,
            size = Dimens.iconSm,
            tint = LocalArgosyTheme.current.textMute
        )
        TabIndicator(
            label = "Nav",
            isSelected = currentTab == DrawerTab.NAVIGATION,
            onClick = { onSelectTab(DrawerTab.NAVIGATION) }
        )
        TabIndicator(
            label = "Friends",
            isSelected = currentTab == DrawerTab.FRIENDS,
            onClick = { onSelectTab(DrawerTab.FRIENDS) }
        )
        InputGlyph(
            button = InputButton.RB,
            size = Dimens.iconSm,
            tint = LocalArgosyTheme.current.textMute
        )
    }
}

@Composable
private fun TabIndicator(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickableNoFocus(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
        Spacer(modifier = Modifier.height(Dimens.spacingXs))
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(2.dp)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    RoundedCornerShape(1.dp)
                )
        )
    }
}

@Composable
private fun NavigationContent(
    items: List<DrawerItem>,
    currentRoute: String?,
    focusedIndex: Int,
    downloadCount: Int,
    saveSyncAttentionCount: Int,
    isRommConnected: Boolean,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(focusedIndex) {
        if (items.isNotEmpty() && focusedIndex in items.indices) {
            listState.animateScrollToItem(focusedIndex)
        }
    }

    Column(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(items, key = { _, item -> item.route }) { index, item ->
                if (index == items.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }

                val badge = when {
                    item.route == Screen.Downloads.route && downloadCount > 0 -> downloadCount
                    item.route == Screen.SaveSync.route && saveSyncAttentionCount > 0 -> saveSyncAttentionCount
                    else -> null
                }

                DrawerMenuItem(
                    item = item,
                    icon = getIconForRoute(item.route),
                    isFocused = index == focusedIndex,
                    isSelected = currentRoute == item.route,
                    badge = badge,
                    onClick = {
                        android.util.Log.d("MainDrawer", "Menu item clicked: ${item.route}")
                        onNavigate(item.route)
                    }
                )
            }
        }

        DrawerDeviceStatus(isRommConnected = isRommConnected)
    }
}

@Composable
private fun FriendsContent(
    friends: List<Friend>,
    focusedIndex: Int,
    onJoinSession: (Friend) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(focusedIndex) {
        if (friends.isNotEmpty() && focusedIndex in friends.indices) {
            val layoutInfo = listState.layoutInfo
            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
            val itemHeight = layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 60
            val centerOffset = (viewportHeight - itemHeight) / 2
            listState.animateScrollToItem(focusedIndex, -centerOffset)
        }
    }

    if (friends.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(Dimens.spacingLg)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.spacingXxl),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(Dimens.radiusLg))
                Text(
                    text = "No friends yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        val onlineFriends = friends.filter {
            it.presence == PresenceStatus.ONLINE ||
                it.presence == PresenceStatus.IN_GAME ||
                it.presence == PresenceStatus.WATCHING
        }
        val offlineFriends = friends.filter {
            it.presence == null || it.presence == PresenceStatus.OFFLINE || it.presence == PresenceStatus.AWAY
        }

        LazyColumn(
            state = listState,
            modifier = modifier,
            contentPadding = PaddingValues(vertical = Dimens.spacingSm)
        ) {
            if (onlineFriends.isNotEmpty()) {
                item {
                    SectionLabel("ONLINE (${onlineFriends.size})")
                }
                itemsIndexed(onlineFriends, key = { _, f -> f.id }) { index, friend ->
                    FriendItem(
                        friend = friend,
                        isFocused = index == focusedIndex,
                        onJoinSession = onJoinSession
                    )
                }
            }

            if (offlineFriends.isNotEmpty()) {
                item {
                    SectionLabel("OFFLINE (${offlineFriends.size})")
                }
                itemsIndexed(offlineFriends, key = { _, f -> f.id }) { index, friend ->
                    val globalIndex = onlineFriends.size + index
                    FriendItem(
                        friend = friend,
                        isFocused = globalIndex == focusedIndex,
                        onJoinSession = onJoinSession
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm)
    )
}

@Composable
private fun FriendItem(
    friend: Friend,
    isFocused: Boolean,
    onJoinSession: (Friend) -> Unit
) {
    val backgroundColor = if (isFocused) {
        LocalArgosyTheme.current.focusAccent.copy(alpha = 0.15f)
    } else {
        Color.Transparent
    }

    val avatarColor = try {
        Color(friend.avatarColor.toColorInt())
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }

    val isOnline = friend.presence == PresenceStatus.ONLINE ||
        friend.presence == PresenceStatus.IN_GAME ||
        friend.presence == PresenceStatus.WATCHING
    val isInGame = friend.presence == PresenceStatus.IN_GAME
    val isJoinable = friend.currentGame?.netplaySession?.joinable == true

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingMd, vertical = 2.dp)
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(backgroundColor)
            .then(
                if (isJoinable) Modifier.clickableNoFocus { onJoinSession(friend) }
                else Modifier
            )
            .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(avatarColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = friend.displayName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            if (isOnline) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF22C55E))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(Dimens.radiusLg))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = friend.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isFocused) {
                    lerp(LocalArgosyTheme.current.focusAccent, Color.White, 0.45f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
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
                        modifier = Modifier.size(12.dp),
                        tint = Color(0xFF22C55E)
                    )
                    Text(
                        text = if (friend.currentGame != null) {
                            if (friend.currentGame.netplaySession != null) {
                                "Hosting ${friend.currentGame.title}"
                            } else {
                                "Playing ${friend.currentGame.title}"
                            }
                        } else {
                            "In Game"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF22C55E),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (friend.isFavorite) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = "Favorite",
                modifier = Modifier.size(14.dp),
                tint = Color(0xFFFBBF24)
            )
        }
    }
}

/**
 * The drawer's account row. It is focusable, so it occupies drawer focus index
 * [DRAWER_ACCOUNT_ROW_INDEX] and every navigation item below it is offset by
 * [DRAWER_NAV_ITEM_OFFSET].
 */
@Composable
private fun DrawerStatusBar(
    isRommConnected: Boolean,
    localUser: SocialUser?,
    localAvatarDoodle: String? = null,
    rommUsername: String? = null,
    isFocused: Boolean = false,
    onOpenAccounts: () -> Unit = {}
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingSm)
            .clip(shape)
            .background(
                if (isFocused) theme.focusAccent.copy(alpha = DRAWER_FOCUS_WASH_ALPHA)
                else Color.Transparent
            )
            .clickableNoFocus(onClick = onOpenAccounts)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        if (localUser != null) {
            SocialAvatar(
                displayName = localUser.displayName,
                avatarColor = localUser.avatarColor,
                size = Dimens.iconLg,
                avatarDoodle = localAvatarDoodle,
                userId = localUser.id
            )
        } else {
            AccountInitialAvatar(name = rommUsername, size = Dimens.iconLg)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rommUsername ?: "No account",
                style = MaterialTheme.typography.labelLarge,
                color = if (isFocused) theme.focusAccent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Account",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                maxLines = 1
            )
        }
    }
}

/**
 * Stand-in avatar for a device with no social link: the RomM name's first character, or a
 * neutral glyph when there is no account at all.
 */
@Composable
private fun AccountInitialAvatar(name: String?, size: androidx.compose.ui.unit.Dp) {
    val initial = name?.trim()?.firstOrNull()?.uppercaseChar()?.toString()
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (initial != null) {
            Text(
                text = initial,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.6f)
            )
        }
    }
}

/**
 * The drawer's footer strip: connection, clock and battery. They describe the device rather than
 * the account, so they sit away from the account row rather than competing with it.
 */
@Composable
private fun DrawerDeviceStatus(isRommConnected: Boolean) {
    val mutedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(
            painter = painterResource(
                if (isRommConnected) R.drawable.ic_romm_connected
                else R.drawable.ic_romm_disconnected
            ),
            contentDescription = if (isRommConnected) "RomM Connected" else "RomM Offline",
            tint = if (isRommConnected) Color.Unspecified else mutedColor,
            modifier = Modifier.size(Dimens.iconMd)
        )
        SystemStatusBar(
            contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

private const val DRAWER_FOCUS_WASH_ALPHA = 0.15f

@Composable
private fun DrawerMenuItem(
    item: DrawerItem,
    icon: ImageVector,
    isFocused: Boolean,
    isSelected: Boolean,
    badge: Int? = null,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isFocused -> LocalArgosyTheme.current.focusAccent.copy(alpha = 0.15f)
        isSelected -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else -> Color.Transparent
    }

    val contentColor = when {
        isFocused -> MaterialTheme.colorScheme.primary
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    val indicatorWidth = if (isFocused) Dimens.spacingXs else 0.dp

    val shape = RoundedCornerShape(topEnd = Dimens.radiusMd, bottomEnd = Dimens.radiusMd)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = Dimens.spacingMd)
            .clip(shape)
            .background(backgroundColor)
            .clickableNoFocus(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .width(indicatorWidth)
                .height(Dimens.spacingXxl)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(if (isFocused) (Dimens.spacingLg - Dimens.spacingXs) else Dimens.spacingLg))
        Icon(
            imageVector = icon,
            contentDescription = item.label,
            tint = contentColor
        )
        Spacer(modifier = Modifier.width(Dimens.spacingMd))
        Text(
            text = item.label,
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
            modifier = Modifier.weight(1f)
        )
        if (badge != null) {
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = Dimens.iconMd, minHeight = Dimens.iconMd)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .padding(horizontal = Dimens.spacingXs),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badge.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.width(Dimens.spacingSm))
        }
    }
}

private fun getIconForRoute(route: String): ImageVector = when (route) {
    Screen.Home.route -> Icons.Filled.FeaturedPlayList
    Screen.Social.route -> Icons.Default.Groups
    Screen.Library.route -> Icons.Default.VideoLibrary
    Screen.MediaLibrary.route -> Icons.Default.Movie
    Screen.Downloads.route -> Icons.Default.Download
    Screen.SaveSync.route -> Icons.Default.CloudSync
    Screen.Apps.route -> Icons.Default.Apps
    Screen.QuayPass.route -> QuayPassIcons.Encounter
    Screen.Settings.route -> Icons.Default.Settings
    else -> Icons.Default.Apps
}
