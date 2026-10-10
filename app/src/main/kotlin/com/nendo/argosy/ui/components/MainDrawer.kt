package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FeaturedPlayList
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.R
import com.nendo.argosy.data.social.SocialUser
import com.nendo.argosy.ui.ACCOUNTS_SECTION_NAME
import com.nendo.argosy.ui.DRAWER_ACCOUNT_ROW_INDEX
import com.nendo.argosy.ui.DRAWER_NAV_ITEM_OFFSET
import com.nendo.argosy.ui.DrawerItem
import com.nendo.argosy.ui.DrawerState
import com.nendo.argosy.ui.components.friends.SocialAvatar
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
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(modifier = modifier.width(Dimens.navDrawerWidth)) {
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
                rommAvatarUrl = drawerState.rommAvatarUrl,
                isFocused = drawerState.navFocusIndex == DRAWER_ACCOUNT_ROW_INDEX,
                onOpenAccounts = {
                    onNavigate(Screen.Settings.createRoute(section = ACCOUNTS_SECTION_NAME))
                }
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = Dimens.spacingLg, vertical = Dimens.radiusLg),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            NavigationContent(
                items = items,
                currentRoute = currentRoute,
                focusedIndex = drawerState.navFocusIndex - DRAWER_NAV_ITEM_OFFSET,
                badgeFor = drawerState::badgeCountFor,
                isRommConnected = drawerState.rommConnected,
                onNavigate = onNavigate,
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (isOpen) {
        FooterHints(hints = emptyList())
    }
}

@Composable
private fun NavigationContent(
    items: List<DrawerItem>,
    currentRoute: String?,
    focusedIndex: Int,
    badgeFor: (String) -> Int?,
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

                val badge = badgeFor(item.route)

                NavDrawerRow(
                    icon = getIconForRoute(item.route),
                    label = stringResource(item.labelRes),
                    isFocused = index == focusedIndex,
                    isSelected = com.nendo.argosy.ui.navigation.NavRing.routeMatches(item.route, currentRoute),
                    onClick = {
                        android.util.Log.d("MainDrawer", "Menu item clicked: ${item.route}")
                        onNavigate(item.route)
                    },
                    modifier = Modifier.padding(end = Dimens.spacingMd),
                    trailing = { badge?.let { DrawerBadge(it) } }
                )
            }
        }

        DrawerDeviceStatus(isRommConnected = isRommConnected)
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
    rommAvatarUrl: String? = null,
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
            AccountAvatar(
                name = rommUsername,
                avatarUrl = rommAvatarUrl,
                size = Dimens.iconLg
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rommUsername ?: stringResource(R.string.ui_drawer_account_none),
                style = MaterialTheme.typography.labelLarge,
                color = if (isFocused) theme.focusAccent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.ui_drawer_account_caption),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AccountAvatar(
    name: String?,
    size: androidx.compose.ui.unit.Dp,
    avatarUrl: String? = null
) {
    val initial = name?.trim()?.firstOrNull()?.uppercaseChar()?.toString()
    if (!avatarUrl.isNullOrBlank()) {
        coil.compose.AsyncImage(
            model = avatarUrl,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        return
    }
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
            contentDescription = if (isRommConnected) {
                stringResource(R.string.ui_drawer_server_connected)
            } else {
                stringResource(R.string.ui_drawer_server_offline)
            },
            tint = if (isRommConnected) Color.Unspecified else mutedColor,
            modifier = Modifier.size(Dimens.iconMd)
        )
        SystemStatusBar(
            contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            scrim = false,
            allowWrap = true
        )
    }
}

@Composable
private fun DrawerBadge(count: Int) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = Dimens.iconMd, minHeight = Dimens.iconMd)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .padding(horizontal = Dimens.spacingXs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1
        )
    }
    Spacer(modifier = Modifier.width(Dimens.spacingSm))
}

internal fun getIconForRoute(route: String): ImageVector = when (route) {
    Screen.Home.route -> Icons.Filled.FeaturedPlayList
    Screen.Social.route -> Icons.Default.Groups
    Screen.Library.route -> Icons.Default.VideoLibrary
    Screen.Collections.route -> Icons.Default.CollectionsBookmark
    Screen.MediaLibrary.route -> Icons.Default.Movie
    Screen.Downloads.route -> Icons.Default.Download
    Screen.SyncMonitor.route -> Icons.Default.Sync
    Screen.SaveSync.route -> Icons.Default.CloudSync
    Screen.Apps.route -> Icons.Default.Apps
    Screen.QuayPass.route -> QuayPassIcons.Encounter
    Screen.Settings.route -> Icons.Default.Settings
    else -> Icons.Default.Apps
}
