package com.nendo.argosy.ui.screens.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.nendo.argosy.data.social.FeedEventDto
import com.nendo.argosy.data.social.FeedEventType
import com.nendo.argosy.data.social.SocialConnectionState
import com.nendo.argosy.ui.screens.doodle.CanvasSize
import com.nendo.argosy.ui.screens.doodle.DoodleEncoder
import com.nendo.argosy.ui.screens.doodle.DoodlePreview
import com.nendo.argosy.ui.components.FooterBarWithState
import com.nendo.argosy.ui.components.FooterHintItem
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.util.clickableNoFocus
import java.time.Duration
import java.time.Instant

@Composable
fun SocialScreen(
    onBack: () -> Unit,
    onDrawerToggle: () -> Unit,
    onOpenEventDetail: (String) -> Unit = {},
    onCreateDoodle: () -> Unit = {},
    onViewProfile: (String) -> Unit = {},
    viewModel: SocialViewModel = hiltViewModel()
) {
    val inputDispatcher = LocalInputDispatcher.current
    val inputHandler = remember(onBack, onDrawerToggle, onOpenEventDetail, onCreateDoodle, onViewProfile) {
        viewModel.createInputHandler(
            onBack = onBack,
            onOpenEventDetail = onOpenEventDetail,
            onCreateDoodle = onCreateDoodle,
            onViewProfile = onViewProfile,
            onShareScreenshot = {
                viewModel.notificationManager.show(title = "Share screenshot coming soon")
            },
            onDrawerToggle = onDrawerToggle
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
    val profileListState = rememberLazyListState()

    LaunchedEffect(uiState.isConnected) {
        if (uiState.isConnected && uiState.events.isEmpty()) {
            viewModel.loadFeed()
        }
    }

    LaunchedEffect(uiState.focusedEventIndex) {
        if (uiState.selectedTab == SocialTab.FEED && uiState.events.isNotEmpty() && uiState.focusedEventIndex in uiState.events.indices) {
            val layoutInfo = feedListState.layoutInfo
            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
            val itemHeight = layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 200
            val centerOffset = (viewportHeight - itemHeight) / 2
            feedListState.animateScrollToItem(uiState.focusedEventIndex, -centerOffset)
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

    LaunchedEffect(uiState.profileFocusIndex) {
        if (uiState.selectedTab == SocialTab.PROFILE) {
            val itemIndex = profileFocusToItemIndex(uiState.profileFocusIndex)
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
                                if (uiState.isLoading && uiState.events.isEmpty()) {
                                    LoadingContent("Loading feed...")
                                } else if (uiState.events.isEmpty()) {
                                    EmptyFeedContent()
                                } else {
                                    FeedContent(
                                        events = uiState.events,
                                        focusedIndex = uiState.focusedEventIndex,
                                        listState = feedListState
                                    )
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
                                onViewProfile = onViewProfile
                            )
                        }
                    }
                    SocialTab.PROFILE -> {
                        if (!uiState.isConnected) {
                            NotConnectedContent()
                        } else {
                            ProfileTabContent(
                                user = uiState.connectedUser,
                                focusIndex = uiState.profileFocusIndex,
                                listState = profileListState,
                                onlineStatus = uiState.socialOnlineStatus,
                                showNowPlaying = uiState.socialShowNowPlaying,
                                notifyFriendOnline = uiState.socialNotifyFriendOnline,
                                notifyFriendPlaying = uiState.socialNotifyFriendPlaying,
                                suppressInGame = uiState.socialSuppressNotificationsInGame,
                                onToggleOnlineStatus = { viewModel.setSocialOnlineStatus(it) },
                                onToggleShowNowPlaying = { viewModel.setSocialShowNowPlaying(it) },
                                onToggleNotifyFriendOnline = { viewModel.setSocialNotifyFriendOnline(it) },
                                onToggleNotifyFriendPlaying = { viewModel.setSocialNotifyFriendPlaying(it) },
                                onToggleSuppressInGame = { viewModel.setSocialSuppressNotificationsInGame(it) }
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
                            add(FooterHintItem(InputButton.SELECT, "Options"))
                        }
                        SocialTab.FRIENDS -> {
                            add(FooterHintItem(InputButton.A, "Profile", enabled = uiState.friends.isNotEmpty()))
                        }
                        SocialTab.PROFILE -> {
                            add(FooterHintItem(InputButton.A, "Toggle"))
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
                onAction = { option ->
                    viewModel.feedOptionsDelegate.hideOptionsModal()
                    when (option) {
                        FeedOption.CREATE_DOODLE -> onCreateDoodle()
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
    }
}

@Composable
private fun SocialTabBar(
    selectedTab: SocialTab,
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
                Text(
                    text = when (tab) {
                        SocialTab.FEED -> "Feed"
                        SocialTab.FRIENDS -> "Friends"
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
private fun EmptyFeedContent() {
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
                text = "Your friends' gaming activity will appear here",
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
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    Column(modifier = Modifier.fillMaxSize()) {
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
                    isFocused = index == focusedIndex
                )
            }
        }
    }
}

@Composable
private fun FeedEventCard(
    event: FeedEventDto,
    isFocused: Boolean
) {
    when (event.eventType) {
        FeedEventType.STARTED_PLAYING -> StartedPlayingCard(event = event, isFocused = isFocused)
        FeedEventType.DOODLE -> DoodleCard(event = event, isFocused = isFocused)
        else -> StandardFeedEventCard(event = event, isFocused = isFocused)
    }
}

@Composable
private fun StartedPlayingCard(
    event: FeedEventDto,
    isFocused: Boolean
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
                    isFocused = isFocused
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
    isFocused: Boolean
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
                    modifier = Modifier.zIndex(1f),
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
    isFocused: Boolean
) {
    val cornerRadius = 12.dp
    val shape = RoundedCornerShape(cornerRadius)
    val borderModifier = if (isFocused) {
        Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape)
    } else Modifier

    val doodleData = event.payload?.get("data") as? String
    val caption = event.payload?.get("caption") as? String

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

                        if (!caption.isNullOrBlank()) {
                            Text(
                                text = caption,
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
    isFocused: Boolean
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
        Column {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                isFocused = isFocused
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
    isFocused: Boolean
) {
    val userColor = user?.let { parseColor(it.avatarColor) } ?: MaterialTheme.colorScheme.primary
    val badgeShape = RoundedCornerShape(bottomStart = cornerRadius, topEnd = cornerRadius)
    val borderOffset = if (isFocused) 3.dp else 0.dp

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

private fun formatRelativeTime(timestamp: String): String {
    return try {
        val instant = parseTimestamp(timestamp)
        val now = Instant.now()
        val duration = Duration.between(instant, now)

        when {
            duration.isNegative -> "now"
            duration.toMinutes() < 1 -> "now"
            duration.toMinutes() < 60 -> "${duration.toMinutes()}m ago"
            duration.toHours() < 24 -> "${duration.toHours()}h ago"
            duration.toDays() < 7 -> "${duration.toDays()}d ago"
            duration.toDays() < 30 -> "${duration.toDays() / 7}w ago"
            else -> "${duration.toDays() / 30}mo ago"
        }
    } catch (e: Exception) {
        ""
    }
}

private fun parseTimestamp(timestamp: String): Instant {
    return try {
        Instant.parse(timestamp)
    } catch (e: Exception) {
        val epochValue = timestamp.toLongOrNull()
        if (epochValue != null) {
            when {
                epochValue > 1_000_000_000_000_000L -> Instant.ofEpochSecond(epochValue / 1_000_000_000)
                epochValue > 1_000_000_000_000L -> Instant.ofEpochMilli(epochValue)
                else -> Instant.ofEpochSecond(epochValue)
            }
        } else {
            throw IllegalArgumentException("Unknown timestamp format: $timestamp")
        }
    }
}

private fun formatEventDescription(event: FeedEventDto): String {
    return when (event.eventType) {
        FeedEventType.STARTED_PLAYING -> "started playing"
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
