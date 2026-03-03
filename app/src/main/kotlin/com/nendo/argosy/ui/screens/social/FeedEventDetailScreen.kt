package com.nendo.argosy.ui.screens.social

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import com.nendo.argosy.data.social.FeedComment
import com.nendo.argosy.data.social.FeedEventDto
import com.nendo.argosy.data.social.FeedEventType
import com.nendo.argosy.data.social.SocialRepository
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.screens.doodle.CanvasSize
import com.nendo.argosy.ui.screens.doodle.DoodleEncoder
import com.nendo.argosy.ui.screens.doodle.DoodlePreview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

data class FeedEventDetailUiState(
    val event: FeedEventDto? = null,
    val comments: List<FeedComment> = emptyList(),
    val isLoading: Boolean = true,
    val focusedCommentIndex: Int = -1,
    val commentText: String = "",
    val isCommentInputFocused: Boolean = false
) {
    val focusedComment: FeedComment?
        get() = comments.getOrNull(focusedCommentIndex)
}

@HiltViewModel
class FeedEventDetailViewModel @Inject constructor(
    private val socialRepository: SocialRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedEventDetailUiState())
    val uiState: StateFlow<FeedEventDetailUiState> = _uiState.asStateFlow()

    fun loadEvent(eventId: String) {
        viewModelScope.launch {
            socialRepository.feedEvents.collect { events ->
                val event = events.find { it.id == eventId }
                _uiState.value = _uiState.value.copy(
                    event = event,
                    isLoading = event == null
                )
            }
        }
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
        _uiState.value = _uiState.value.copy(isCommentInputFocused = true)
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

    fun createInputHandler(onBack: () -> Unit): InputHandler = object : InputHandler {
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
    }
}

@Composable
fun FeedEventDetailScreen(
    eventId: String,
    onBack: () -> Unit,
    viewModel: FeedEventDetailViewModel = hiltViewModel()
) {
    val inputDispatcher = LocalInputDispatcher.current
    val inputHandler = remember(onBack) {
        viewModel.createInputHandler(onBack = onBack)
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
            commentFocusRequester.requestFocus()
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
                                commentFocusRequester = commentFocusRequester,
                                onCommentTextChange = viewModel::updateCommentText,
                                onSubmitComment = viewModel::submitComment
                            )
                        } else {
                            PortraitLayout(
                                event = event,
                                comments = uiState.comments,
                                focusedCommentIndex = uiState.focusedCommentIndex,
                                commentText = uiState.commentText,
                                isCommentInputFocused = uiState.isCommentInputFocused,
                                commentFocusRequester = commentFocusRequester,
                                onCommentTextChange = viewModel::updateCommentText,
                                onSubmitComment = viewModel::submitComment
                            )
                        }
                    }
                }
            }
        }

        val isLiked = uiState.event?.isLikedByMe == true
        FooterBar(
            hints = listOf(
                InputButton.B to "Back",
                InputButton.Y to if (isLiked) "Unlike" else "Like",
                InputButton.A to "Comment"
            )
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
    commentFocusRequester: FocusRequester,
    onCommentTextChange: (String) -> Unit,
    onSubmitComment: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            EventMediaContent(event)
        }

        Column(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EventMetadata(event)

            CommentsSection(
                comments = comments,
                focusedCommentIndex = focusedCommentIndex,
                commentText = commentText,
                isCommentInputFocused = isCommentInputFocused,
                commentFocusRequester = commentFocusRequester,
                onCommentTextChange = onCommentTextChange,
                onSubmitComment = onSubmitComment,
                modifier = Modifier.weight(1f)
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
    commentFocusRequester: FocusRequester,
    onCommentTextChange: (String) -> Unit,
    onSubmitComment: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                EventMediaContent(event)
            }
        }

        item {
            EventMetadata(event)
        }

        item {
            Text(
                text = "Comments (${event.commentCount})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        item {
            CommentInput(
                commentText = commentText,
                isCommentInputFocused = isCommentInputFocused,
                commentFocusRequester = commentFocusRequester,
                onCommentTextChange = onCommentTextChange,
                onSubmitComment = onSubmitComment
            )
        }

        if (comments.isEmpty()) {
            item {
                Text(
                    text = "No comments yet. Be the first!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        } else {
            itemsIndexed(comments) { index, comment ->
                CommentCard(
                    comment = comment,
                    isFocused = index == focusedCommentIndex
                )
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun EventMediaContent(event: FeedEventDto) {
    when (event.eventType) {
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

            Card(
                modifier = Modifier.size(280.dp),
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

@Composable
private fun EventMetadata(event: FeedEventDto) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            event.user?.let { user ->
                val userColor = parseColor(user.avatarColor)
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
        }

        if (event.eventType == FeedEventType.DOODLE) {
            val caption = event.payload?.get("caption") as? String
            if (!caption.isNullOrBlank()) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            if (event.game != null || event.fallbackTitle.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.SportsEsports,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = event.game?.title ?: event.fallbackTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
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

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (event.isLikedByMe) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Likes",
                    modifier = Modifier.size(20.dp),
                    tint = if (event.isLikedByMe) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${event.likeCount}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun CommentsSection(
    comments: List<FeedComment>,
    focusedCommentIndex: Int,
    commentText: String,
    isCommentInputFocused: Boolean,
    commentFocusRequester: FocusRequester,
    onCommentTextChange: (String) -> Unit,
    onSubmitComment: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Comments",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        CommentInput(
            commentText = commentText,
            isCommentInputFocused = isCommentInputFocused,
            commentFocusRequester = commentFocusRequester,
            onCommentTextChange = onCommentTextChange,
            onSubmitComment = onSubmitComment
        )

        if (comments.isEmpty()) {
            Text(
                text = "No comments yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(comments) { index, comment ->
                    CommentCard(
                        comment = comment,
                        isFocused = index == focusedCommentIndex
                    )
                }
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
    isFocused: Boolean
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
                        color = parseColor(user.avatarColor)
                    )
                }
                Text(
                    text = formatRelativeTime(comment.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Text(
                text = comment.content,
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
        null -> event.type
    }
}
