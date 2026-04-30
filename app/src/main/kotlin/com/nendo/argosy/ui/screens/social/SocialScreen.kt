package com.nendo.argosy.ui.screens.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.offset
import com.nendo.argosy.data.social.SocialUser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.nendo.argosy.data.social.CommunityFollow
import com.nendo.argosy.data.social.FeedEventDto
import com.nendo.argosy.data.social.FeedEventType
import com.nendo.argosy.data.social.SocialConnectionState
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.screens.doodle.GamePickerItem
import com.nendo.argosy.ui.screens.doodle.CanvasSize
import com.nendo.argosy.ui.screens.doodle.DoodleEncoder
import com.nendo.argosy.ui.screens.doodle.DoodlePreview
import com.nendo.argosy.ui.components.FooterBarWithState
import com.nendo.argosy.ui.components.FooterHintItem
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.util.formatRelativeTime

@Composable
fun SocialScreen(
    onBack: () -> Unit,
    onDrawerToggle: () -> Unit,
    onOpenEventDetail: (String) -> Unit = {},
    onCreatePost: () -> Unit = {},
    onViewProfile: (String) -> Unit = {},
    onNavigateToGameDetail: (Int) -> Unit = {},
    onNavigateToSocialSettings: () -> Unit = {},
    viewModel: SocialViewModel = hiltViewModel()
) {
    val inputDispatcher = LocalInputDispatcher.current
    val inputHandler = remember(onBack, onDrawerToggle, onOpenEventDetail, onCreatePost, onViewProfile, onNavigateToGameDetail, onNavigateToSocialSettings) {
        viewModel.createInputHandler(
            onBack = onBack,
            onOpenEventDetail = onOpenEventDetail,
            onCreatePost = onCreatePost,
            onViewProfile = onViewProfile,
            onShareScreenshot = {
                viewModel.notificationManager.show(title = "Share screenshot coming soon")
            },
            onDrawerToggle = onDrawerToggle,
            onNavigateToGameDetail = onNavigateToGameDetail,
            onNavigateToSocialSettings = onNavigateToSocialSettings
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_SOCIAL)
                if (viewModel.uiState.value.isConnected) {
                    viewModel.refresh()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_SOCIAL)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val optionsState by viewModel.feedOptionsDelegate.state.collectAsState()
    val feedListState = rememberLazyListState()
    val friendsListState = rememberLazyListState()
    val notificationsListState = rememberLazyListState()
    val profileListState = rememberLazyListState()

    val launchContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.launchEvents.collect { event ->
            when (event) {
                is SocialLaunchEvent.LaunchIntent -> {
                    event.intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    launchContext.startActivity(event.intent)
                }
                is SocialLaunchEvent.LaunchError -> {
                    viewModel.notificationManager.show(
                        title = event.message,
                        type = com.nendo.argosy.core.notification.NotificationType.ERROR
                    )
                }
            }
        }
    }

    LaunchedEffect(uiState.isConnected) {
        if (uiState.isConnected && uiState.activeFeedEvents.isEmpty()) {
            viewModel.loadFeed()
        }
    }

    LaunchedEffect(uiState.focusedEventIndex, uiState.feedMode) {
        val events = uiState.activeFeedEvents
        if (uiState.selectedTab == SocialTab.FEED && events.isNotEmpty() && uiState.focusedEventIndex in events.indices) {
            val layoutInfo = feedListState.layoutInfo
            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
            val itemHeight = layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 200
            val centerOffset = (viewportHeight - itemHeight) / 2
            feedListState.animateScrollToItem(uiState.focusedEventIndex, -centerOffset)
        }
    }

    LaunchedEffect(uiState.selectedTab) {
        if (uiState.selectedTab == SocialTab.NOTIFICATIONS && uiState.isConnected && uiState.notifications.isEmpty()) {
            viewModel.loadNotifications()
        }
    }

    LaunchedEffect(uiState.focusedNotificationIndex) {
        if (uiState.selectedTab == SocialTab.NOTIFICATIONS && uiState.notifications.isNotEmpty() && uiState.focusedNotificationIndex in uiState.notifications.indices) {
            val layoutInfo = notificationsListState.layoutInfo
            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
            val itemHeight = layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 80
            val centerOffset = (viewportHeight - itemHeight) / 2
            notificationsListState.animateScrollToItem(uiState.focusedNotificationIndex, -centerOffset)
        }
    }

    LaunchedEffect(uiState.focusedFriendIndex) {
        if (uiState.selectedTab == SocialTab.FRIENDS && uiState.friends.isNotEmpty() && uiState.focusedFriendIndex in uiState.friends.indices) {
            val layoutInfo = friendsListState.layoutInfo
            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
            val itemHeight = layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 80
            val centerOffset = (viewportHeight - itemHeight) / 2
            friendsListState.animateScrollToItem(uiState.focusedFriendIndex, -centerOffset)
        }
    }

    LaunchedEffect(uiState.selectedTab, uiState.isConnected) {
        if (uiState.selectedTab == SocialTab.PROFILE && uiState.isConnected) {
            val needsLoad = uiState.userProfile == null ||
                uiState.userProfile?.user?.id != uiState.connectedUser?.id
            if (needsLoad) viewModel.loadProfile()
        }
    }

    LaunchedEffect(uiState.profileFocusIndex, uiState.userProfile?.mostPlayed?.size) {
        if (uiState.selectedTab == SocialTab.PROFILE) {
            val mostPlayedCount = uiState.userProfile?.mostPlayed?.size ?: 0
            val itemIndex = profileFocusToItemIndex(uiState.profileFocusIndex, mostPlayedCount)
            val layoutInfo = profileListState.layoutInfo
            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
            val itemHeight = layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 60
            val centerOffset = (viewportHeight - itemHeight) / 2
            profileListState.animateScrollToItem(itemIndex, -centerOffset)
        }
    }

    val focusedEvent = uiState.focusedEvent
    val isLiked = focusedEvent?.isLikedByMe == true

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SocialTabBar(
                selectedTab = uiState.selectedTab,
                unreadCount = uiState.unreadCount,
                onTabSelected = { tab ->
                    val delta = tab.ordinal - uiState.selectedTab.ordinal
                    if (delta != 0) viewModel.switchTab(delta)
                }
            )

            Box(modifier = Modifier.weight(1f)) {
                when (uiState.selectedTab) {
                    SocialTab.FEED -> {
                        when (val state = uiState.connectionState) {
                            is SocialConnectionState.Disconnected,
                            is SocialConnectionState.AwaitingAuth -> {
                                NotConnectedContent()
                            }
                            is SocialConnectionState.Connecting -> {
                                LoadingContent("Connecting...")
                            }
                            is SocialConnectionState.Failed -> {
                                ErrorContent(state.reason)
                            }
                            is SocialConnectionState.Connected -> {
                                val feedEvents = uiState.activeFeedEvents
                                val feedLoading = uiState.activeFeedLoading
                                if (feedLoading && feedEvents.isEmpty()) {
                                    LoadingContent(
                                        if (uiState.feedMode == FeedMode.COMMUNITY) "Loading community feed..."
                                        else "Loading feed..."
                                    )
                                } else if (feedEvents.isEmpty()) {
                                    EmptyFeedContent(isCommunity = uiState.feedMode == FeedMode.COMMUNITY)
                                } else {
                                    Column {
                                        if (uiState.feedMode == FeedMode.COMMUNITY) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = "Community Feed",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                        FeedContent(
                                            events = feedEvents,
                                            focusedIndex = uiState.focusedEventIndex,
                                            listState = feedListState,
                                            modifier = Modifier.weight(1f),
                                            onViewProfile = onViewProfile
                                        )
                                    }
                                }
                            }
                        }
                    }
                    SocialTab.FRIENDS -> {
                        if (!uiState.isConnected) {
                            NotConnectedContent()
                        } else {
                            FriendsTabContent(
                                friends = uiState.friends,
                                focusedIndex = uiState.focusedFriendIndex,
                                listState = friendsListState,
                                onViewProfile = onViewProfile,
                                onToggleFavorite = { friendId -> viewModel.toggleFavoriteFriend(friendId) },
                                netplayPreflight = { session -> viewModel.runNetplayPreflight(session) },
                                onJoinNetplaySession = { friend, session ->
                                    viewModel.launchNetplayJoin(friend, session)
                                },
                                onJoinableChanged = { friendId, joinable ->
                                    viewModel.reportFriendNetplayJoinable(friendId, joinable)
                                }
                            )
                        }
                    }
                    SocialTab.NOTIFICATIONS -> {
                        if (!uiState.isConnected) {
                            NotConnectedContent()
                        } else if (uiState.isLoadingNotifications && uiState.notifications.isEmpty()) {
                            LoadingContent("Loading notifications...")
                        } else {
                            NotificationsTabContent(
                                notifications = uiState.notifications,
                                focusedIndex = uiState.focusedNotificationIndex,
                                listState = notificationsListState,
                                onNotificationTap = { notif ->
                                    viewModel.markNotificationRead(notif.id)
                                    when (notif.type) {
                                        "comment", "like_milestone" -> notif.eventId?.let { onOpenEventDetail(it) }
                                        "friend_request", "friend_accepted", "friend_added" -> {
                                            viewModel.switchTab(SocialTab.FRIENDS.ordinal - uiState.selectedTab.ordinal)
                                        }
                                    }
                                }
                            )
                        }
                    }
                    SocialTab.PROFILE -> {
                        if (!uiState.isConnected) {
                            NotConnectedContent()
                        } else {
                            ProfileTabContent(
                                user = uiState.connectedUser,
                                userProfile = uiState.userProfile,
                                isLoadingProfile = uiState.isLoadingProfile,
                                focusIndex = uiState.profileFocusIndex,
                                listState = profileListState
                            )
                        }
                    }
                }
            }

            FooterBarWithState(
                hints = buildList {
                    when (uiState.selectedTab) {
                        SocialTab.FEED -> {
                            add(FooterHintItem(InputButton.A, "View"))
                            add(FooterHintItem(InputButton.Y, if (isLiked) "Unlike" else "Like"))
                            add(FooterHintItem(
                                InputButton.X,
                                if (uiState.feedMode == FeedMode.COMMUNITY) "Friends" else "Community"
                            ))
                            add(FooterHintItem(InputButton.SELECT, "Options"))
                        }
                        SocialTab.FRIENDS -> {
                            val focusedFriend = uiState.friends.getOrNull(uiState.focusedFriendIndex)
                            val canJoin = focusedFriend != null && focusedFriend.id in uiState.joinableFriendIds
                            val primaryLabel = if (canJoin) "Join" else "Profile"
                            add(FooterHintItem(InputButton.A, primaryLabel, enabled = uiState.friends.isNotEmpty()))
                            add(FooterHintItem(InputButton.Y, "Favorite", enabled = uiState.friends.isNotEmpty()))
                        }
                        SocialTab.NOTIFICATIONS -> {
                            add(FooterHintItem(InputButton.A, "Open", enabled = uiState.notifications.isNotEmpty()))
                            add(FooterHintItem(InputButton.Y, "Read All", enabled = uiState.unreadCount > 0))
                        }
                        SocialTab.PROFILE -> {
                            add(FooterHintItem(InputButton.A, "View Game", enabled = uiState.focusedGameInLibrary))
                            add(FooterHintItem(InputButton.SELECT, "Settings"))
                        }
                    }
                }
            )
        }

        if (optionsState.showOptionsModal) {
            FeedOptionsModal(
                focusIndex = optionsState.optionsModalFocusIndex,
                userName = focusedEvent?.user?.displayName,
                hasEvent = focusedEvent != null,
                isCommunityMode = uiState.feedMode == FeedMode.COMMUNITY,
                onAction = { option ->
                    viewModel.feedOptionsDelegate.hideOptionsModal()
                    when (option) {
                        FeedOption.CREATE_POST -> onCreatePost()
                        FeedOption.FIND_COMMUNITIES -> viewModel.showCommunitySearch()
                        FeedOption.VIEW_PROFILE -> focusedEvent?.user?.id?.let { onViewProfile(it) }
                        FeedOption.SHARE_SCREENSHOT -> {
                            viewModel.notificationManager.show(title = "Share screenshot coming soon")
                        }
                        FeedOption.REPORT_POST -> viewModel.feedOptionsDelegate.showReportReasonModal()
                        FeedOption.HIDE_POST -> viewModel.hideCurrentEvent()
                    }
                },
                onDismiss = { viewModel.feedOptionsDelegate.hideOptionsModal() }
            )
        }

        if (optionsState.showReportReasonModal) {
            ReportReasonModal(
                focusIndex = optionsState.reportReasonFocusIndex,
                onReasonSelect = { reason ->
                    viewModel.feedOptionsDelegate.hideReportReasonModal()
                    viewModel.reportCurrentEvent(reason)
                    viewModel.notificationManager.show(title = "Post reported and hidden")
                },
                onDismiss = { viewModel.feedOptionsDelegate.hideReportReasonModal() }
            )
        }

        if (uiState.showCommunitySearch) {
            CommunitySearchDialog(
                query = uiState.communitySearchQuery,
                results = uiState.communitySearchResults,
                focusIndex = uiState.communitySearchFocusIndex,
                searchFocused = uiState.communitySearchFieldFocused,
                communityFollows = uiState.communityFollows,
                onQueryChange = { viewModel.updateCommunitySearchQuery(it) },
                onToggleFollow = { igdbId ->
                    viewModel.toggleCommunityFollow(igdbId)
                    viewModel.hideCommunitySearch()
                },
                onDismiss = { viewModel.hideCommunitySearch() }
            )
        }
    }
}

@Composable
private fun SocialTabBar(
    selectedTab: SocialTab,
    unreadCount: Int = 0,
    onTabSelected: (SocialTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 24.dp, vertical = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        SocialTab.entries.forEach { tab ->
            val isSelected = tab == selectedTab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickableNoFocus { onTabSelected(tab) }
                    .then(
                        if (isSelected) {
                            Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                        } else Modifier
                    )
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = when (tab) {
                            SocialTab.FEED -> "Feed"
                            SocialTab.FRIENDS -> "Friends"
                            SocialTab.NOTIFICATIONS -> "Alerts"
                            SocialTab.PROFILE -> "Profile"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center
                    )

                    if (tab == SocialTab.NOTIFICATIONS && unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onError,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun NotConnectedContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Not Connected",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Link your account in Settings to see friend activity",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun LoadingContent(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun ErrorContent(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Error: $message",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun EmptyFeedContent(isCommunity: Boolean = false) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No Activity Yet",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isCommunity) "Follow game communities to see posts here"
                else "Your friends' gaming activity will appear here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun FeedContent(
    events: List<FeedEventDto>,
    focusedIndex: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
    onViewProfile: (String) -> Unit = {}
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Activity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${events.size} events",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 24.dp,
                vertical = 8.dp
            )
        ) {
            itemsIndexed(events, key = { _, event -> event.id }) { index, event ->
                FeedEventCard(
                    event = event,
                    isFocused = index == focusedIndex,
                    onViewProfile = onViewProfile
                )
            }
        }
    }
}

@Composable
private fun FeedEventCard(
    event: FeedEventDto,
    isFocused: Boolean,
    onViewProfile: (String) -> Unit = {}
) {
    when (event.eventType) {
        FeedEventType.STARTED_PLAYING -> StartedPlayingCard(event = event, isFocused = isFocused, onViewProfile = onViewProfile)
        FeedEventType.DOODLE -> DoodleCard(event = event, isFocused = isFocused, onViewProfile = onViewProfile)
        else -> StandardFeedEventCard(event = event, isFocused = isFocused, onViewProfile = onViewProfile)
    }
}

@Composable
private fun StartedPlayingCard(
    event: FeedEventDto,
    isFocused: Boolean,
    onViewProfile: (String) -> Unit = {}
) {
    val cornerRadius = 12.dp
    val shape = RoundedCornerShape(cornerRadius)
    val borderModifier = if (isFocused) {
        Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape)
    } else Modifier

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(borderModifier),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = cornerRadius, bottomStart = cornerRadius))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                val coverThumb = event.game?.coverThumb
                val bitmap = remember(coverThumb) {
                    coverThumb?.let {
                        try {
                            val bytes = Base64.decode(it, Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                        } catch (e: Exception) { null }
                    }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = event.game?.title ?: event.fallbackTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.SportsEsports,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        if (event.fallbackTitle.isNotEmpty()) {
                            Text(
                                text = event.fallbackTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text(
                            text = formatRelativeTime(event.createdAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    Text(
                        text = formatEventDescription(event),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                StartedPlayingFooter(
                    user = event.user,
                    likeCount = event.likeCount,
                    commentCount = event.commentCount,
                    isLikedByMe = event.isLikedByMe,
                    cornerRadius = cornerRadius,
                    isFocused = isFocused,
                    onViewProfile = onViewProfile
                )
            }
        }
    }
}

@Composable
private fun StartedPlayingFooter(
    user: SocialUser?,
    likeCount: Int,
    commentCount: Int,
    isLikedByMe: Boolean,
    cornerRadius: Dp,
    isFocused: Boolean,
    onViewProfile: (String) -> Unit = {}
) {
    val userColor = user?.let { parseColor(it.avatarColor) } ?: MaterialTheme.colorScheme.primary
    val badgeShape = RoundedCornerShape(topEnd = cornerRadius)
    val borderOffset = if (isFocused) 3.dp else 0.dp
    val earSize = cornerRadius

    Column(modifier = Modifier.fillMaxWidth()) {
        user?.let {
            Box(
                modifier = Modifier
                    .offset(y = 1.dp)
                    .size(earSize)
                    .clip(remember(cornerRadius) { BottomLeftTopEarShape(cornerRadius) })
                    .background(userColor)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(end = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isLikedByMe) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Likes",
                    modifier = Modifier.size(16.dp),
                    tint = if (isLikedByMe) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    }
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = likeCount.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(16.dp))
                Icon(
                    imageVector = Icons.Filled.Comment,
                    contentDescription = "Comments",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = commentCount.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            user?.let {
                Row(
                    modifier = Modifier
                        .zIndex(1f)
                        .clickableNoFocus { onViewProfile(it.id) },
                    verticalAlignment = Alignment.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .clip(badgeShape)
                            .background(userColor)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = it.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                    Box(
                        modifier = Modifier
                            .offset(x = (-1).dp, y = -borderOffset)
                            .size(earSize)
                            .clip(remember(cornerRadius) { BottomLeftRightEarShape(cornerRadius) })
                            .background(userColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun DoodleCard(
    event: FeedEventDto,
    isFocused: Boolean,
    onViewProfile: (String) -> Unit = {}
) {
    val cornerRadius = 12.dp
    val shape = RoundedCornerShape(cornerRadius)
    val borderModifier = if (isFocused) {
        Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape)
    } else Modifier

    val doodleData = event.payload?.get("data") as? String
    val caption = event.payload?.get("caption") as? String
    val body = event.payload?.get("body") as? String
    val displayText = caption?.takeIf { it.isNotBlank() } ?: body

    val decodedDoodle = remember(doodleData) {
        doodleData?.let {
            try {
                DoodleEncoder.decodeFromBase64(it)
            } catch (e: Exception) {
                null
            }
        }
    }

    val pixelGap = when (decodedDoodle?.size ?: CanvasSize.MEDIUM) {
        CanvasSize.SMALL -> 2f
        CanvasSize.MEDIUM -> 1f
        CanvasSize.LARGE -> 0f
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(borderModifier),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        BoxWithConstraints {
            val doodleFraction = if (maxWidth > 400.dp) 0.3f else 0.4f
            val doodleSize = maxWidth * doodleFraction

            Row {
                Box(
                    modifier = Modifier
                        .size(doodleSize)
                        .clip(RoundedCornerShape(topStart = cornerRadius, bottomStart = cornerRadius))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    if (decodedDoodle != null) {
                        DoodlePreview(
                            canvasSize = decodedDoodle.size,
                            pixels = decodedDoodle.pixels,
                            pixelGap = pixelGap,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(doodleSize)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "shared a doodle",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = formatRelativeTime(event.createdAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }

                        if (!displayText.isNullOrBlank()) {
                            Text(
                                text = displayText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (event.game != null || event.fallbackTitle.isNotEmpty()) {
                            Spacer(modifier = Modifier.weight(1f))

                            val coverThumb = event.game?.coverThumb
                            val gameBitmap = remember(coverThumb) {
                                coverThumb?.let {
                                    try {
                                        val bytes = Base64.decode(it, Base64.DEFAULT)
                                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                                    } catch (e: Exception) {
                                        null
                                    }
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(6.dp)
                            ) {
                                if (gameBitmap != null) {
                                    Image(
                                        bitmap = gameBitmap,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .height(40.dp)
                                            .aspectRatio(3f / 4f)
                                            .clip(RoundedCornerShape(4.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .height(40.dp)
                                            .aspectRatio(3f / 4f)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.SportsEsports,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = event.game?.title ?: event.fallbackTitle,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val platform = event.game?.platform
                                    if (platform != null) {
                                        Text(
                                            text = platform,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }

                    StartedPlayingFooter(
                        user = event.user,
                        likeCount = event.likeCount,
                        commentCount = event.commentCount,
                        isLikedByMe = event.isLikedByMe,
                        cornerRadius = cornerRadius,
                        isFocused = isFocused
                    )
                }
            }
        }
    }
}

@Composable
private fun StandardFeedEventCard(
    event: FeedEventDto,
    isFocused: Boolean,
    onViewProfile: (String) -> Unit = {}
) {
    val cornerRadius = 12.dp
    val shape = RoundedCornerShape(cornerRadius)
    val borderModifier = if (isFocused) {
        Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape)
    } else Modifier

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(borderModifier),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        val hasGameInfo = event.game != null || event.fallbackTitle.isNotEmpty()

        Column {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (hasGameInfo) {
                    Box(
                        modifier = Modifier
                            .width(72.dp)
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        val coverThumb = event.game?.coverThumb
                        val bitmap = remember(coverThumb) {
                            coverThumb?.let {
                                try {
                                    val bytes = Base64.decode(it, Base64.DEFAULT)
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                                } catch (e: Exception) {
                                    null
                                }
                            }
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = event.game?.title ?: event.fallbackTitle,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                Icons.Default.SportsEsports,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        if (event.fallbackTitle.isNotEmpty()) {
                            Text(
                                text = event.fallbackTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text(
                            text = formatRelativeTime(event.createdAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    Text(
                        text = formatEventDescription(event),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            FeedEventCardFooter(
                user = event.user,
                likeCount = event.likeCount,
                commentCount = event.commentCount,
                isLikedByMe = event.isLikedByMe,
                cornerRadius = cornerRadius,
                isFocused = isFocused,
                onViewProfile = onViewProfile
            )
        }
    }
}

@Composable
private fun FeedEventCardFooter(
    user: com.nendo.argosy.data.social.SocialUser?,
    likeCount: Int,
    commentCount: Int,
    isLikedByMe: Boolean,
    cornerRadius: Dp,
    isFocused: Boolean,
    onViewProfile: (String) -> Unit = {}
) {
    val userColor = user?.let { parseColor(it.avatarColor) } ?: MaterialTheme.colorScheme.primary
    val badgeShape = RoundedCornerShape(topEnd = cornerRadius)
    val borderOffset = if (isFocused) 3.dp else 0.dp
    val earSize = cornerRadius

    Column(modifier = Modifier.fillMaxWidth()) {
        user?.let {
            Box(
                modifier = Modifier
                    .offset(y = 1.dp)
                    .size(earSize)
                    .clip(remember(cornerRadius) { BottomLeftTopEarShape(cornerRadius) })
                    .background(userColor)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(end = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isLikedByMe) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Likes",
                    modifier = Modifier.size(16.dp),
                    tint = if (isLikedByMe) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    }
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = likeCount.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(16.dp))
                Icon(
                    imageVector = Icons.Filled.Comment,
                    contentDescription = "Comments",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = commentCount.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            user?.let {
                Row(
                    modifier = Modifier
                        .zIndex(1f)
                        .clickableNoFocus { onViewProfile(it.id) },
                    verticalAlignment = Alignment.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .clip(badgeShape)
                            .background(userColor)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = it.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                    Box(
                        modifier = Modifier
                            .offset(x = (-1).dp, y = -borderOffset)
                            .size(earSize)
                            .clip(remember(cornerRadius) { BottomLeftRightEarShape(cornerRadius) })
                            .background(userColor)
                    )
                }
            }
        }
    }
}

private fun parseColor(hexColor: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hexColor))
    } catch (e: Exception) {
        Color(0xFF6366F1)
    }
}

private fun formatEventDescription(event: FeedEventDto): String {
    return when (event.eventType) {
        FeedEventType.STARTED_PLAYING -> "First Play"
        FeedEventType.PLAY_MILESTONE -> {
            val hours = (event.payload?.get("total_hours") as? Number)?.toInt() ?: 0
            "reached $hours hours in"
        }
        FeedEventType.MARATHON_SESSION -> {
            val mins = (event.payload?.get("duration_mins") as? Number)?.toInt() ?: 0
            "had a ${mins / 60}h marathon session in"
        }
        FeedEventType.COMPLETED -> "completed"
        FeedEventType.ACHIEVEMENT_UNLOCKED -> {
            val name = event.payload?.get("achievement_name") as? String ?: "an achievement"
            "unlocked \"$name\" in"
        }
        FeedEventType.ACHIEVEMENT_MILESTONE -> {
            val count = (event.payload?.get("total_unlocked") as? Number)?.toInt() ?: 0
            "earned $count achievements in"
        }
        FeedEventType.PERFECT_GAME -> "mastered"
        FeedEventType.GAME_ADDED -> "added to their library"
        FeedEventType.GAME_FAVORITED -> "favorited"
        FeedEventType.GAME_RATED -> {
            val rating = (event.payload?.get("rating") as? Number)?.toInt() ?: 0
            "rated ${"*".repeat(rating)}"
        }
        FeedEventType.FRIEND_ADDED -> {
            val friendName = event.payload?.get("friend_name") as? String ?: "someone"
            "became friends with $friendName"
        }
        FeedEventType.COLLECTION_SHARED -> {
            val name = event.payload?.get("collection_name") as? String ?: "a collection"
            "shared collection \"$name\""
        }
        FeedEventType.COLLECTION_SAVED -> {
            val name = event.payload?.get("collection_name") as? String ?: "a collection"
            "saved collection \"$name\""
        }
        FeedEventType.COLLECTION_CREATED -> {
            val name = event.payload?.get("collection_name") as? String ?: "a collection"
            "created collection \"$name\""
        }
        FeedEventType.COLLECTION_UPDATED -> {
            val name = event.payload?.get("collection_name") as? String ?: "a collection"
            val count = (event.payload?.get("game_count") as? Number)?.toInt() ?: 0
            "added $count games to \"$name\""
        }
        FeedEventType.DOODLE -> "shared a doodle"
        FeedEventType.DISCUSSION -> {
            val body = event.payload?.get("body") as? String ?: ""
            if (body.length > 100) body.take(100) + "..." else body.ifEmpty { "shared a post" }
        }
        null -> event.type
    }
}

private class BottomLeftTopEarShape(
    private val cornerRadius: Dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val r = with(density) { cornerRadius.toPx() }
        val path = Path().apply {
            moveTo(0f, r)
            lineTo(r, r)
            arcTo(Rect(0f, -r, r * 2, r), 90f, 90f, false)
            close()
        }
        return Outline.Generic(path)
    }
}

private class BottomLeftRightEarShape(
    private val cornerRadius: Dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val r = with(density) { cornerRadius.toPx() }
        val path = Path().apply {
            moveTo(0f, r)
            lineTo(r, r)
            arcTo(Rect(0f, -r, r * 2, r), 90f, 90f, false)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
private fun CommunitySearchDialog(
    query: String,
    results: List<GamePickerItem>,
    focusIndex: Int,
    searchFocused: Boolean,
    communityFollows: List<CommunityFollow>,
    onQueryChange: (String) -> Unit,
    onToggleFollow: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val searchFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(searchFocused) {
        if (searchFocused) {
            searchFocusRequester.requestFocus()
        } else {
            focusManager.clearFocus()
        }
    }

    Modal(
        title = "Find Communities",
        baseWidth = 420.dp,
        onDismiss = onDismiss,
        footerHints = buildList {
            if (searchFocused) {
                add(InputButton.DPAD_DOWN to "Browse")
            } else {
                add(InputButton.DPAD to "Navigate")
                add(InputButton.A to "Follow/Unfollow")
            }
            add(InputButton.B to "Close")
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (searchFocused) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
                .then(
                    if (searchFocused) Modifier.border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(8.dp)
                    )
                    else Modifier
                )
                .padding(12.dp)
        ) {
            if (query.isEmpty()) {
                Text(
                    text = "Search for a game...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(searchFocusRequester)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn {
            itemsIndexed(results) { index, item ->
                val isItemFocused = !searchFocused && focusIndex == index
                val igdbId = item.igdbId
                val isFollowed = igdbId != null && communityFollows.any { it.igdbGameId == igdbId }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isItemFocused) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                        .clickableNoFocus(onClick = {
                            if (igdbId != null) onToggleFollow(igdbId)
                        })
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (item.coverPath != null) {
                        val imageData: Any = if (item.coverPath.startsWith("/")) {
                            java.io.File(item.coverPath)
                        } else {
                            item.coverPath
                        }
                        AsyncImage(
                            model = imageData,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isItemFocused) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.platform != null) {
                            Text(
                                text = item.platform,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isItemFocused) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                    if (igdbId != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (isFollowed) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isFollowed) "Following" else "Follow",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isFollowed) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (results.isEmpty() && query.isNotBlank()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No games found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}
