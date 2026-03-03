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
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import java.time.Duration
import java.time.Instant

@Composable
fun SocialScreen(
    onBack: () -> Unit,
    onDrawerToggle: () -> Unit,
    onOpenEventDetail: (String) -> Unit = {},
    onCreateDoodle: () -> Unit = {},
    onViewProfile: (String) -> Unit = {},
    onNavigateToQuayPass: () -> Unit = {},
    viewModel: SocialViewModel = hiltViewModel()
) {
    val inputDispatcher = LocalInputDispatcher.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val inputHandler = remember(onBack, onOpenEventDetail, onCreateDoodle, onViewProfile, onNavigateToQuayPass) {
        viewModel.createInputHandler(
            onBack = onBack,
            onOpenEventDetail = onOpenEventDetail,
            onCreateDoodle = onCreateDoodle,
            onViewProfile = onViewProfile,
            onShareScreenshot = {
                // TODO: Implement screenshot capture
                android.widget.Toast.makeText(context, "Share screenshot coming soon", android.widget.Toast.LENGTH_SHORT).show()
            },
            onNavigateToQuayPass = onNavigateToQuayPass
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_SOCIAL)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_SOCIAL)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.isConnected) {
        if (uiState.isConnected && uiState.events.isEmpty()) {
            viewModel.loadFeed()
        }
    }

    LaunchedEffect(uiState.focusedEventIndex) {
        if (uiState.events.isNotEmpty() && uiState.focusedEventIndex in uiState.events.indices) {
            val layoutInfo = listState.layoutInfo
            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
            val itemHeight = layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 200
            val centerOffset = (viewportHeight - itemHeight) / 2
            listState.animateScrollToItem(uiState.focusedEventIndex, -centerOffset)
        }
    }

    val focusedEvent = uiState.focusedEvent
    val isLiked = focusedEvent?.isLikedByMe == true

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
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
                                filterLabel = uiState.filterLabel,
                                listState = listState
                            )
                        }
                    }
                }
            }

            FooterBar(
                hints = listOf(
                    InputButton.LB to "Prev",
                    InputButton.RB to "Next",
                    InputButton.START to "Refresh",
                    InputButton.A to "View",
                    InputButton.Y to if (isLiked) "Unlike" else "Like",
                    InputButton.SELECT to "Options"
                )
            )
        }

        if (uiState.showOptionsModal) {
            FeedOptionsModal(
                focusIndex = uiState.optionsModalFocusIndex,
                userName = focusedEvent?.user?.displayName,
                hasEvent = focusedEvent != null,
                onOptionSelect = { option ->
                    viewModel.hideOptionsModal()
                    when (option) {
                        FeedOption.CREATE_DOODLE -> onCreateDoodle()
                        FeedOption.QUAYPASS_PLAZA -> onNavigateToQuayPass()
                        FeedOption.VIEW_PROFILE -> focusedEvent?.user?.id?.let { onViewProfile(it) }
                        FeedOption.SHARE_SCREENSHOT -> {
                            android.widget.Toast.makeText(context, "Share screenshot coming soon", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        FeedOption.REPORT_POST -> viewModel.showReportReasonModal()
                        FeedOption.HIDE_POST -> viewModel.hideCurrentEvent()
                    }
                },
                onDismiss = { viewModel.hideOptionsModal() }
            )
        }

        if (uiState.showReportReasonModal) {
            ReportReasonModal(
                focusIndex = uiState.reportReasonFocusIndex,
                onReasonSelect = { reason ->
                    viewModel.hideReportReasonModal()
                    viewModel.reportCurrentEvent(reason)
                    android.widget.Toast.makeText(context, "Post reported and hidden", android.widget.Toast.LENGTH_SHORT).show()
                },
                onDismiss = { viewModel.hideReportReasonModal() }
            )
        }
    }
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
    filterLabel: String,
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
                text = filterLabel,
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
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.SportsEsports,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = event.game?.title ?: event.fallbackTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
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
