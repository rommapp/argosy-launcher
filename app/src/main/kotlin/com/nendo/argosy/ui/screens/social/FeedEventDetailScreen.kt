package com.nendo.argosy.ui.screens.social

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.social.FeedComment
import com.nendo.argosy.data.social.FeedEventDto
import com.nendo.argosy.data.social.FeedEventType
import com.nendo.argosy.data.social.SocialRepository
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.ui.util.parseInlineMarkdown
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.util.formatRelativeTime
import com.nendo.argosy.ui.screens.doodle.CanvasSize
import com.nendo.argosy.ui.screens.doodle.DoodleEncoder
import com.nendo.argosy.ui.screens.doodle.DoodlePreview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FeedEventDetailUiState(
    val event: FeedEventDto? = null,
    val comments: List<FeedComment> = emptyList(),
    val isLoading: Boolean = true,
    val focusedCommentIndex: Int = -1,
    val commentText: String = "",
    val isCommentInputFocused: Boolean = false,
    val scrollToCommentInput: Boolean = false,
    val localGameId: Long? = null
) {
    val focusedComment: FeedComment?
        get() = comments.getOrNull(focusedCommentIndex)
}

@HiltViewModel
class FeedEventDetailViewModel @Inject constructor(
    private val socialRepository: SocialRepository,
    private val gameRepository: GameRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedEventDetailUiState())
    val uiState: StateFlow<FeedEventDetailUiState> = _uiState.asStateFlow()

    fun loadEvent(eventId: String) {
        socialRepository.getEvent(eventId)
        socialRepository.requestEventComments(eventId)
        viewModelScope.launch {
            combine(
                socialRepository.feedEvents,
                socialRepository.eventComments
            ) { events, commentsMap ->
                val event = events.find { it.id == eventId }
                val embeddedComments = event?.comments ?: emptyList()
                val realtimeComments = commentsMap[eventId] ?: emptyList()
                val allComments = mergeComments(embeddedComments, realtimeComments)
                event to allComments
            }.collect { (event, comments) ->
                val state = _uiState.value
                val igdbId = event?.igdbId ?: event?.game?.igdbId
                val localGameId = if (igdbId != null && state.localGameId == null) {
                    gameRepository.getByIgdbId(igdbId.toLong())?.id
                } else {
                    state.localGameId
                }
                _uiState.value = state.copy(
                    event = event,
                    isLoading = event == null,
                    comments = comments,
                    focusedCommentIndex = state.focusedCommentIndex.coerceIn(
                        -1, comments.size.coerceAtLeast(1) - 1
                    ),
                    localGameId = localGameId
                )
            }
        }
    }

    private fun mergeComments(
        embedded: List<FeedComment>,
        realtime: List<FeedComment>
    ): List<FeedComment> {
        if (realtime.isEmpty()) return embedded
        if (embedded.isEmpty()) return realtime
        val embeddedIds = embedded.map { it.id }.toSet()
        val newComments = realtime.filter { it.id !in embeddedIds }
        return embedded + newComments
    }

    fun likeEvent() {
        _uiState.value.event?.let { event ->
            socialRepository.likeEvent(event.id)
        }
    }

    fun updateCommentText(text: String) {
        _uiState.value = _uiState.value.copy(commentText = text)
    }

    fun submitComment() {
        val state = _uiState.value
        val event = state.event ?: return
        val text = state.commentText.trim()
        if (text.isEmpty()) return

        socialRepository.commentEvent(event.id, text)
        _uiState.value = state.copy(commentText = "", isCommentInputFocused = false)
    }

    fun focusCommentInput() {
        _uiState.value = _uiState.value.copy(
            scrollToCommentInput = true,
            focusedCommentIndex = -1
        )
    }

    fun onCommentInputScrolled() {
        _uiState.value = _uiState.value.copy(
            scrollToCommentInput = false,
            isCommentInputFocused = true
        )
    }

    fun unfocusCommentInput() {
        _uiState.value = _uiState.value.copy(isCommentInputFocused = false)
    }

    private fun moveFocus(delta: Int): Boolean {
        val state = _uiState.value
        val comments = state.comments
        if (comments.isEmpty()) return false

        val newIndex = (state.focusedCommentIndex + delta).coerceIn(-1, comments.size - 1)
        if (newIndex != state.focusedCommentIndex) {
            _uiState.value = state.copy(focusedCommentIndex = newIndex)
            return true
        }
        return false
    }

    fun createInputHandler(
        onBack: () -> Unit,
        onNavigateToGame: (Long) -> Unit,
        onViewProfile: (String) -> Unit = {}
    ): InputHandler = object : InputHandler {
        override fun onUp(): InputResult {
            if (_uiState.value.isCommentInputFocused) return InputResult.UNHANDLED
            return if (moveFocus(-1)) InputResult.HANDLED else InputResult.UNHANDLED
        }

        override fun onDown(): InputResult {
            if (_uiState.value.isCommentInputFocused) return InputResult.UNHANDLED
            return if (moveFocus(1)) InputResult.HANDLED else InputResult.UNHANDLED
        }

        override fun onLeft(): InputResult = InputResult.UNHANDLED
        override fun onRight(): InputResult = InputResult.UNHANDLED

        override fun onConfirm(): InputResult {
            if (_uiState.value.isCommentInputFocused) {
                submitComment()
                return InputResult.HANDLED
            }
            focusCommentInput()
            return InputResult.HANDLED
        }

        override fun onBack(): InputResult {
            if (_uiState.value.isCommentInputFocused) {
                unfocusCommentInput()
                return InputResult.HANDLED
            }
            onBack()
            return InputResult.HANDLED
        }

        override fun onSecondaryAction(): InputResult {
            likeEvent()
            return InputResult.HANDLED
        }

        override fun onSelect(): InputResult {
            val userId = _uiState.value.event?.user?.id ?: return InputResult.UNHANDLED
            onViewProfile(userId)
            return InputResult.HANDLED
        }

        override fun onContextMenu(): InputResult {
            val gameId = _uiState.value.localGameId ?: return InputResult.UNHANDLED
            onNavigateToGame(gameId)
            return InputResult.HANDLED
        }
    }
}

@Composable
fun FeedEventDetailScreen(
    eventId: String,
    onBack: () -> Unit,
    onNavigateToGame: (Long) -> Unit = {},
    onViewProfile: (String) -> Unit = {},
    viewModel: FeedEventDetailViewModel = hiltViewModel()
) {
    val inputDispatcher = LocalInputDispatcher.current
    val inputHandler = remember(onBack, onNavigateToGame, onViewProfile) {
        viewModel.createInputHandler(
            onBack = onBack,
            onNavigateToGame = onNavigateToGame,
            onViewProfile = onViewProfile
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.subscribeView(inputHandler, forRoute = "social/event")
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler, forRoute = "social/event")
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(eventId) {
        viewModel.loadEvent(eventId)
    }

    val uiState by viewModel.uiState.collectAsState()
    val commentFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(uiState.isCommentInputFocused) {
        if (uiState.isCommentInputFocused) {
            keyboardController?.show()
        } else {
            keyboardController?.hide()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                uiState.event?.let { event ->
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val isLandscape = maxWidth > maxHeight

                        if (isLandscape) {
                            LandscapeLayout(
                                event = event,
                                comments = uiState.comments,
                                focusedCommentIndex = uiState.focusedCommentIndex,
                                commentText = uiState.commentText,
                                isCommentInputFocused = uiState.isCommentInputFocused,
                                scrollToCommentInput = uiState.scrollToCommentInput,
                                localGameId = uiState.localGameId,
                                commentFocusRequester = commentFocusRequester,
                                onCommentTextChange = viewModel::updateCommentText,
                                onSubmitComment = viewModel::submitComment,
                                onCommentInputScrolled = viewModel::onCommentInputScrolled,
                                onNavigateToGame = onNavigateToGame,
                                onViewProfile = onViewProfile
                            )
                        } else {
                            PortraitLayout(
                                event = event,
                                comments = uiState.comments,
                                focusedCommentIndex = uiState.focusedCommentIndex,
                                commentText = uiState.commentText,
                                isCommentInputFocused = uiState.isCommentInputFocused,
                                scrollToCommentInput = uiState.scrollToCommentInput,
                                localGameId = uiState.localGameId,
                                commentFocusRequester = commentFocusRequester,
                                onCommentTextChange = viewModel::updateCommentText,
                                onSubmitComment = viewModel::submitComment,
                                onCommentInputScrolled = viewModel::onCommentInputScrolled,
                                onNavigateToGame = onNavigateToGame,
                                onViewProfile = onViewProfile
                            )
                        }
                    }
                }
            }
        }

        val isLiked = uiState.event?.isLikedByMe == true
        FooterBar(
            hints = buildList {
                add(InputButton.B to "Back")
                add(InputButton.Y to if (isLiked) "Unlike" else "Like")
                add(InputButton.A to "Comment")
                if (uiState.localGameId != null) {
                    add(InputButton.X to "View Game")
                }
                if (uiState.event?.user != null) {
                    add(InputButton.SELECT to "Profile")
                }
            }
        )
    }
}

@Composable
private fun LandscapeLayout(
    event: FeedEventDto,
    comments: List<FeedComment>,
    focusedCommentIndex: Int,
    commentText: String,
    isCommentInputFocused: Boolean,
    scrollToCommentInput: Boolean,
    localGameId: Long?,
    commentFocusRequester: FocusRequester,
    onCommentTextChange: (String) -> Unit,
    onSubmitComment: () -> Unit,
    onCommentInputScrolled: () -> Unit,
    onNavigateToGame: (Long) -> Unit,
    onViewProfile: (String) -> Unit = {}
) {
    val isDoodle = event.eventType == FeedEventType.DOODLE
    val isDiscussion = event.eventType == FeedEventType.DISCUSSION
    val hasMedia = !isDiscussion
    val mediaWeight = if (isDoodle) 0.5f else 0.4f

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (hasMedia) {
            Box(
                modifier = Modifier
                    .weight(mediaWeight)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                EventMediaContent(event, fillWidth = isDoodle)
            }
        }

        Column(
            modifier = Modifier
                .weight(if (hasMedia) 1f - mediaWeight else 1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EventMetadata(event, localGameId, onNavigateToGame, onViewProfile)

            CommentsSection(
                comments = comments,
                focusedCommentIndex = focusedCommentIndex,
                commentText = commentText,
                isCommentInputFocused = isCommentInputFocused,
                scrollToCommentInput = scrollToCommentInput,
                commentFocusRequester = commentFocusRequester,
                onCommentTextChange = onCommentTextChange,
                onSubmitComment = onSubmitComment,
                onCommentInputScrolled = onCommentInputScrolled,
                modifier = Modifier.weight(1f),
                onViewProfile = onViewProfile
            )
        }
    }
}

@Composable
private fun PortraitLayout(
    event: FeedEventDto,
    comments: List<FeedComment>,
    focusedCommentIndex: Int,
    commentText: String,
    isCommentInputFocused: Boolean,
    scrollToCommentInput: Boolean,
    localGameId: Long?,
    commentFocusRequester: FocusRequester,
    onCommentTextChange: (String) -> Unit,
    onSubmitComment: () -> Unit,
    onCommentInputScrolled: () -> Unit,
    onNavigateToGame: (Long) -> Unit,
    onViewProfile: (String) -> Unit = {}
) {
    val listState = rememberLazyListState()
    val headerItemCount = 4
    val commentInputIndex = 3

    LaunchedEffect(scrollToCommentInput) {
        if (scrollToCommentInput) {
            listState.animateScrollToItem(commentInputIndex)
            onCommentInputScrolled()
        }
    }

    LaunchedEffect(focusedCommentIndex) {
        if (focusedCommentIndex >= 0) {
            val targetIndex = focusedCommentIndex + headerItemCount
            val layoutInfo = listState.layoutInfo
            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
            val targetItem = layoutInfo.visibleItemsInfo.find { it.index == targetIndex }
            val scrollOffset = if (targetItem != null) {
                targetItem.offset - (viewportHeight - targetItem.size) / 2
            } else {
                null
            }
            if (scrollOffset != null) {
                listState.animateScrollToItem(targetIndex, scrollOffset)
            } else {
                listState.animateScrollToItem(targetIndex)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "media") {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                EventMediaContent(
                    event = event,
                    fillWidth = event.eventType == FeedEventType.DOODLE
                )
            }
        }

        item(key = "metadata") {
            EventMetadata(event, localGameId, onNavigateToGame, onViewProfile)
        }

        item(key = "comments_header") {
            Text(
                text = "Comments (${comments.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        item(key = "comment_input") {
            CommentInput(
                commentText = commentText,
                isCommentInputFocused = isCommentInputFocused,
                commentFocusRequester = commentFocusRequester,
                onCommentTextChange = onCommentTextChange,
                onSubmitComment = onSubmitComment
            )
        }

        if (comments.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = "No comments yet. Be the first!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        } else {
            itemsIndexed(comments, key = { _, comment -> comment.id }) { index, comment ->
                CommentCard(
                    comment = comment,
                    isFocused = index == focusedCommentIndex,
                    onViewProfile = onViewProfile
                )
            }
        }

        item(key = "spacer") { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun EventMediaContent(event: FeedEventDto, fillWidth: Boolean = false) {
    when (event.eventType) {
        FeedEventType.DISCUSSION -> {
            // Discussion posts are text-only -- no media content
        }
        FeedEventType.DOODLE -> {
            val doodleData = event.payload?.get("data") as? String
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

            val cardModifier = if (fillWidth) {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            } else {
                Modifier.size(280.dp)
            }

            Card(
                modifier = cardModifier,
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
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
        }
        else -> {
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

            if (bitmap != null || (event.game != null || event.fallbackTitle.isNotEmpty())) {
                Card(
                    modifier = Modifier
                        .width(180.dp)
                        .height(240.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = event.game?.title ?: event.fallbackTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.SportsEsports,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventMetadata(
    event: FeedEventDto,
    localGameId: Long? = null,
    onNavigateToGame: (Long) -> Unit = {},
    onViewProfile: (String) -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            event.user?.let { user ->
                val userColor = parseColor(user.avatarColor)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickableNoFocus { onViewProfile(user.id) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(userColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = user.displayName.take(2).uppercase(),
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White
                        )
                    }

                    Column {
                        Text(
                            text = user.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = formatRelativeTime(event.createdAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (event.isLikedByMe) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Likes",
                        modifier = Modifier.size(18.dp),
                        tint = if (event.isLikedByMe) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${event.likeCount}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (event.eventType == FeedEventType.DISCUSSION) {
            val body = event.payload?.get("body") as? String
            if (!body.isNullOrBlank()) {
                Text(
                    text = parseInlineMarkdown(
                        text = body,
                        linkColor = MaterialTheme.colorScheme.primary
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            if (event.game != null || event.fallbackTitle.isNotEmpty()) {
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
                val gameTagModifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .then(
                        if (localGameId != null) Modifier.clickableNoFocus { onNavigateToGame(localGameId) }
                        else Modifier
                    )
                    .padding(8.dp)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = gameTagModifier
                ) {
                    if (gameBitmap != null) {
                        Image(
                            bitmap = gameBitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .height(56.dp)
                                .aspectRatio(3f / 4f)
                                .clip(RoundedCornerShape(6.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .height(56.dp)
                                .aspectRatio(3f / 4f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.SportsEsports,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = event.game?.title ?: event.fallbackTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val platform = event.game?.platform
                        if (platform != null) {
                            Text(
                                text = platform,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else if (event.eventType == FeedEventType.DOODLE) {
            val caption = event.payload?.get("caption") as? String
            val body = event.payload?.get("body") as? String
            val displayText = caption?.takeIf { it.isNotBlank() } ?: body
            if (!displayText.isNullOrBlank()) {
                Text(
                    text = parseInlineMarkdown(
                        text = displayText,
                        linkColor = MaterialTheme.colorScheme.primary
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            if (event.game != null || event.fallbackTitle.isNotEmpty()) {
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
                val gameTagModifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .then(
                        if (localGameId != null) Modifier.clickableNoFocus { onNavigateToGame(localGameId) }
                        else Modifier
                    )
                    .padding(8.dp)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = gameTagModifier
                ) {
                    if (gameBitmap != null) {
                        Image(
                            bitmap = gameBitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .height(56.dp)
                                .aspectRatio(3f / 4f)
                                .clip(RoundedCornerShape(6.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .height(56.dp)
                                .aspectRatio(3f / 4f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.SportsEsports,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = event.game?.title ?: event.fallbackTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val platform = event.game?.platform
                        if (platform != null) {
                            Text(
                                text = platform,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            if (event.fallbackTitle.isNotEmpty()) {
                Text(
                    text = event.fallbackTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = formatEventDescription(event),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CommentsSection(
    comments: List<FeedComment>,
    focusedCommentIndex: Int,
    commentText: String,
    isCommentInputFocused: Boolean,
    scrollToCommentInput: Boolean,
    commentFocusRequester: FocusRequester,
    onCommentTextChange: (String) -> Unit,
    onSubmitComment: () -> Unit,
    onCommentInputScrolled: () -> Unit,
    modifier: Modifier = Modifier,
    onViewProfile: (String) -> Unit = {}
) {
    val listState = rememberLazyListState()
    val commentInputIndex = 1

    LaunchedEffect(scrollToCommentInput) {
        if (scrollToCommentInput) {
            listState.animateScrollToItem(commentInputIndex)
            onCommentInputScrolled()
        }
    }

    LaunchedEffect(focusedCommentIndex) {
        if (focusedCommentIndex >= 0) {
            val targetIndex = focusedCommentIndex + 2
            val layoutInfo = listState.layoutInfo
            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
            val targetItem = layoutInfo.visibleItemsInfo.find { it.index == targetIndex }
            val scrollOffset = if (targetItem != null) {
                targetItem.offset - (viewportHeight - targetItem.size) / 2
            } else {
                null
            }
            if (scrollOffset != null) {
                listState.animateScrollToItem(targetIndex, scrollOffset)
            } else {
                listState.animateScrollToItem(targetIndex)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "comments_header") {
            Text(
                text = "Comments",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        item(key = "comment_input") {
            CommentInput(
                commentText = commentText,
                isCommentInputFocused = isCommentInputFocused,
                commentFocusRequester = commentFocusRequester,
                onCommentTextChange = onCommentTextChange,
                onSubmitComment = onSubmitComment
            )
        }

        if (comments.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = "No comments yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        } else {
            itemsIndexed(comments, key = { _, comment -> comment.id }) { index, comment ->
                CommentCard(
                    comment = comment,
                    isFocused = index == focusedCommentIndex,
                    onViewProfile = onViewProfile
                )
            }
        }
    }
}

@Composable
private fun CommentInput(
    commentText: String,
    isCommentInputFocused: Boolean,
    commentFocusRequester: FocusRequester,
    onCommentTextChange: (String) -> Unit,
    onSubmitComment: () -> Unit
) {
    LaunchedEffect(isCommentInputFocused) {
        if (isCommentInputFocused) {
            kotlinx.coroutines.delay(50)
            try {
                commentFocusRequester.requestFocus()
            } catch (_: IllegalStateException) {
                // FocusRequester not yet attached to layout
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isCommentInputFocused) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BasicTextField(
            value = commentText,
            onValueChange = onCommentTextChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(commentFocusRequester),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSubmitComment() }),
            decorationBox = { innerTextField ->
                if (commentText.isEmpty()) {
                    Text(
                        text = "Add a comment...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                innerTextField()
            }
        )

        IconButton(
            onClick = onSubmitComment,
            enabled = commentText.isNotBlank()
        ) {
            Icon(
                Icons.Default.Send,
                contentDescription = "Send",
                tint = if (commentText.isNotBlank()) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                }
            )
        }
    }
}

@Composable
private fun CommentCard(
    comment: FeedComment,
    isFocused: Boolean,
    onViewProfile: (String) -> Unit = {}
) {
    val borderModifier = if (isFocused) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
    } else Modifier

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(borderModifier),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                comment.user?.let { user ->
                    Text(
                        text = user.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = parseColor(user.avatarColor),
                        modifier = Modifier.clickableNoFocus { onViewProfile(user.id) }
                    )
                }
                Text(
                    text = formatRelativeTime(comment.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Text(
                text = parseInlineMarkdown(
                    text = comment.content,
                    linkColor = MaterialTheme.colorScheme.primary
                ),
                style = MaterialTheme.typography.bodyMedium
            )
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
            "reached $hours hours"
        }
        FeedEventType.MARATHON_SESSION -> {
            val mins = (event.payload?.get("duration_mins") as? Number)?.toInt() ?: 0
            "had a ${mins / 60}h marathon session"
        }
        FeedEventType.COMPLETED -> "completed"
        FeedEventType.ACHIEVEMENT_UNLOCKED -> {
            val name = event.payload?.get("achievement_name") as? String ?: "an achievement"
            "unlocked \"$name\""
        }
        FeedEventType.ACHIEVEMENT_MILESTONE -> {
            val count = (event.payload?.get("total_unlocked") as? Number)?.toInt() ?: 0
            "earned $count achievements"
        }
        FeedEventType.PERFECT_GAME -> "mastered"
        FeedEventType.GAME_ADDED -> "added to library"
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
