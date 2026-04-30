package com.nendo.argosy.ui.screens.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.screens.doodle.DoodleEncoder
import com.nendo.argosy.ui.screens.doodle.DoodlePreview
import com.nendo.argosy.ui.screens.doodle.GamePickerItem
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem
import com.nendo.argosy.ui.util.clickableNoFocus

@Composable
fun PostEditorScreen(
    onBack: () -> Unit,
    onPosted: () -> Unit,
    onNavigateToDoodle: (gameId: Int?, gameTitle: String?, gameCoverPath: String?) -> Unit,
    initialDoodleData: String? = null,
    initialDoodleSize: Int? = null,
    initialDoodleGameId: Int? = null,
    initialDoodleGameTitle: String? = null,
    initialDoodleGameCoverPath: String? = null,
    viewModel: PostEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val bodyFocusRequester = remember { FocusRequester() }
    var openKeyboard by remember { mutableStateOf(false) }

    LaunchedEffect(initialDoodleData, initialDoodleSize) {
        if (initialDoodleData != null && initialDoodleSize != null) {
            viewModel.attachDoodle(initialDoodleData, initialDoodleSize)
        }
    }

    LaunchedEffect(initialDoodleGameId, initialDoodleGameTitle) {
        if (initialDoodleGameId != null || initialDoodleGameTitle != null) {
            viewModel.setLinkedGame(initialDoodleGameId, initialDoodleGameTitle, initialDoodleGameCoverPath)
        }
    }

    val inputDispatcher = LocalInputDispatcher.current
    val inputHandler = remember(viewModel, onBack, onNavigateToDoodle) {
        PostEditorInputHandler(
            viewModel = viewModel,
            onOpenKeyboard = { openKeyboard = true },
            onNavigateBack = onBack,
            onNavigateToDoodle = {
                val state = viewModel.uiState.value
                onNavigateToDoodle(state.linkedGameId, state.linkedGameTitle, state.linkedGameCoverPath)
            }
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.subscribeView(inputHandler, forRoute = "post_editor")
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler, forRoute = "post_editor")
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(openKeyboard) {
        if (openKeyboard) {
            bodyFocusRequester.requestFocus()
            openKeyboard = false
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PostEditorEvent.Posted -> onPosted()
                is PostEditorEvent.Error -> {}
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = "Create Post",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                BodyInput(
                    body = uiState.body,
                    onBodyChange = { viewModel.setBody(it) },
                    isFocused = uiState.currentSection == PostEditorSection.BODY,
                    focusRequester = bodyFocusRequester,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${uiState.body.length}/2000",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.End)
                )

                Spacer(modifier = Modifier.height(8.dp))

                GameSection(
                    linkedGameTitle = uiState.linkedGameTitle,
                    linkedGameCoverPath = uiState.linkedGameCoverPath,
                    isFocused = uiState.currentSection == PostEditorSection.GAME,
                    onClick = { viewModel.showGamePicker() }
                )

                if (uiState.showVisibility) {
                    Spacer(modifier = Modifier.height(8.dp))

                    VisibilityToggle(
                        isPublic = uiState.isPublic,
                        isFocused = uiState.currentSection == PostEditorSection.VISIBILITY,
                        onClick = { viewModel.togglePublic() }
                    )
                }
            }

            DoodleColumn(
                doodleData = uiState.doodleData,
                doodleSize = uiState.doodleSize,
                isFocused = uiState.currentSection == PostEditorSection.DOODLE,
                onAddDoodle = {
                    onNavigateToDoodle(uiState.linkedGameId, uiState.linkedGameTitle, uiState.linkedGameCoverPath)
                },
                onRemoveDoodle = { viewModel.removeDoodle() }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        PostEditorFooter(
            currentSection = uiState.currentSection,
            canPost = uiState.canPost,
            hasContent = uiState.hasContent,
            hasDoodle = uiState.hasDoodle,
            linkedGameTitle = uiState.linkedGameTitle
        )
    }

    if (uiState.showPostConfirm) {
        PostConfirmDialog(
            isPosting = uiState.isPosting,
            focusIndex = uiState.postConfirmFocusIndex,
            isPublic = uiState.isPublic,
            hasGame = uiState.linkedGameId != null,
            onPost = { viewModel.post() },
            onCancel = { viewModel.hidePostConfirm() }
        )
    }

    if (uiState.showDiscardDialog) {
        DiscardPostDialog(
            focusIndex = uiState.discardFocusIndex,
            onDiscard = {
                viewModel.hideDiscardDialog()
                onBack()
            },
            onCancel = { viewModel.hideDiscardDialog() }
        )
    }

    if (uiState.showGamePicker) {
        PostGamePickerDialog(
            query = uiState.gamePickerQuery,
            results = uiState.gamePickerResults,
            focusIndex = uiState.gamePickerFocusIndex,
            searchFocused = uiState.gamePickerSearchFocused,
            onQueryChange = { viewModel.updateGamePickerQuery(it) },
            onSelectItem = { index ->
                viewModel.moveGamePickerFocus(index - uiState.gamePickerFocusIndex)
                viewModel.selectGame()
            },
            onDismiss = { viewModel.hideGamePicker() }
        )
    }
}

@Composable
private fun BodyInput(
    body: String,
    onBodyChange: (String) -> Unit,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(
                if (isFocused) Modifier.border(
                    2.dp,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(8.dp)
                )
                else Modifier
            )
            .padding(12.dp)
    ) {
        if (body.isEmpty()) {
            Text(
                text = "What's on your mind?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        BasicTextField(
            value = body,
            onValueChange = onBodyChange,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.bodyMedium.fontSize
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = false,
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
        )
    }
}

@Composable
private fun DoodleColumn(
    doodleData: String?,
    doodleSize: Int?,
    isFocused: Boolean,
    onAddDoodle: () -> Unit,
    onRemoveDoodle: () -> Unit
) {
    Column(
        modifier = Modifier.width(140.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (doodleData != null && doodleSize != null) {
            val decoded = remember(doodleData) {
                DoodleEncoder.decodeFromBase64(doodleData)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .then(
                        if (isFocused) Modifier.border(
                            2.dp,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(8.dp)
                        )
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                DoodlePreview(
                    canvasSize = decoded.size,
                    pixels = decoded.pixels,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .clickableNoFocus(onClick = onRemoveDoodle)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove doodle",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "Remove",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .then(
                        if (isFocused) Modifier.border(
                            2.dp,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(8.dp)
                        )
                        else Modifier
                    )
                    .clickableNoFocus(onClick = onAddDoodle),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Brush,
                        contentDescription = "Add doodle",
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "Add Doodle",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun GameSection(
    linkedGameTitle: String?,
    linkedGameCoverPath: String?,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(
                if (isFocused) Modifier.border(
                    2.dp,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(8.dp)
                )
                else Modifier
            )
            .clickableNoFocus(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (linkedGameCoverPath != null) {
            AsyncImage(
                model = rememberFileImageModel(linkedGameCoverPath),
                contentDescription = linkedGameTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        } else {
            Icon(
                imageVector = Icons.Default.SportsEsports,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        Text(
            text = linkedGameTitle ?: "Link a game (optional)",
            style = MaterialTheme.typography.bodyMedium,
            color = if (linkedGameTitle != null) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun VisibilityToggle(
    isPublic: Boolean,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(
                if (isFocused) Modifier.border(
                    2.dp,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(8.dp)
                )
                else Modifier
            )
            .clickableNoFocus(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = if (isPublic) Icons.Default.Public else Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (isPublic) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column {
            Text(
                text = if (isPublic) "Public" else "Friends Only",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (isPublic) "Visible in game community"
                else "Only visible to friends",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun PostEditorFooter(
    currentSection: PostEditorSection,
    canPost: Boolean,
    hasContent: Boolean,
    hasDoodle: Boolean,
    linkedGameTitle: String?
) {
    val hints = buildList {
        when (currentSection) {
            PostEditorSection.BODY -> {
                add(InputButton.A to "Edit")
            }
            PostEditorSection.GAME -> {
                add(InputButton.A to "Select")
                if (linkedGameTitle != null) {
                    add(InputButton.Y to "Clear")
                }
            }
            PostEditorSection.VISIBILITY -> {
                add(InputButton.A to "Toggle")
            }
            PostEditorSection.DOODLE -> {
                if (hasDoodle) {
                    add(InputButton.A to "Remove")
                } else {
                    add(InputButton.A to "Add Doodle")
                }
            }
        }
        if (canPost) {
            add(InputButton.START to "Post")
        }
        val backLabel = if (hasContent) "Discard" else "Back"
        add(InputButton.B to backLabel)
    }
    FooterBar(hints = hints)
}

@Composable
private fun PostConfirmDialog(
    isPosting: Boolean,
    focusIndex: Int,
    isPublic: Boolean,
    hasGame: Boolean,
    onPost: () -> Unit,
    onCancel: () -> Unit
) {
    Modal(title = "Create Post") {
        if (isPosting) {
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Text(
                    text = "Posting...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        } else {
            val visibility = when {
                hasGame && isPublic -> "This post will be shared publicly in the game community."
                else -> "This post will be shared with your friends."
            }
            Text(
                text = visibility,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OptionItem(
                icon = Icons.Default.Send,
                label = "Post",
                isFocused = focusIndex == 0,
                onClick = onPost
            )
            OptionItem(
                icon = Icons.Default.Close,
                label = "Cancel",
                isFocused = focusIndex == 1,
                onClick = onCancel
            )
        }
    }
}

@Composable
private fun DiscardPostDialog(
    focusIndex: Int,
    onDiscard: () -> Unit,
    onCancel: () -> Unit
) {
    Modal(title = "Discard Post?") {
        Text(
            text = "You have unsaved changes. Are you sure you want to discard this post?",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OptionItem(
            icon = Icons.Default.Delete,
            label = "Discard",
            isFocused = focusIndex == 0,
            isDangerous = true,
            onClick = onDiscard
        )
        OptionItem(
            icon = Icons.Default.Edit,
            label = "Keep Editing",
            isFocused = focusIndex == 1,
            onClick = onCancel
        )
    }
}

@Composable
private fun PostGamePickerDialog(
    query: String,
    results: List<GamePickerItem>,
    focusIndex: Int,
    searchFocused: Boolean,
    onQueryChange: (String) -> Unit,
    onSelectItem: (Int) -> Unit,
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
        title = "Select Game",
        baseWidth = 400.dp,
        onDismiss = onDismiss,
        footerHints = buildList {
            if (searchFocused) {
                add(InputButton.DPAD_DOWN to "Browse")
            } else {
                add(InputButton.DPAD to "Navigate")
                add(InputButton.A to "Select")
            }
            add(InputButton.B to "Cancel")
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
                    text = "Search games...",
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
            item {
                val isNoneFocused = !searchFocused && focusIndex == 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isNoneFocused) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                        .clickableNoFocus(onClick = { onSelectItem(0) })
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "No game",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isNoneFocused) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            itemsIndexed(results) { index, item ->
                val displayIndex = index + 1
                val isItemFocused = !searchFocused && focusIndex == displayIndex

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isItemFocused) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                        .clickableNoFocus(onClick = { onSelectItem(displayIndex) })
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (item.coverPath != null) {
                        AsyncImage(
                            model = rememberFileImageModel(item.coverPath),
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                    Column {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isItemFocused) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
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
                }
            }
        }
    }
}

private class PostEditorInputHandler(
    private val viewModel: PostEditorViewModel,
    private val onOpenKeyboard: () -> Unit,
    private val onNavigateBack: () -> Unit,
    private val onNavigateToDoodle: () -> Unit
) : InputHandler {

    override fun onUp(): InputResult {
        val state = viewModel.uiState.value
        return when {
            state.showGamePicker && !state.gamePickerSearchFocused -> {
                if (state.gamePickerFocusIndex == 0) {
                    viewModel.focusGamePickerSearch()
                } else {
                    viewModel.moveGamePickerFocus(-1)
                }
                InputResult.HANDLED
            }
            state.showGamePicker -> InputResult.HANDLED
            state.showPostConfirm -> {
                viewModel.movePostConfirmFocus(-1)
                InputResult.HANDLED
            }
            state.showDiscardDialog -> {
                viewModel.moveDiscardFocus(-1)
                InputResult.HANDLED
            }
            else -> {
                viewModel.previousSection()
                InputResult.HANDLED
            }
        }
    }

    override fun onDown(): InputResult {
        val state = viewModel.uiState.value
        return when {
            state.showGamePicker && state.gamePickerSearchFocused -> {
                viewModel.focusGamePickerList()
                InputResult.HANDLED
            }
            state.showGamePicker -> {
                viewModel.moveGamePickerFocus(1)
                InputResult.HANDLED
            }
            state.showPostConfirm -> {
                viewModel.movePostConfirmFocus(1)
                InputResult.HANDLED
            }
            state.showDiscardDialog -> {
                viewModel.moveDiscardFocus(1)
                InputResult.HANDLED
            }
            else -> {
                viewModel.nextSection()
                InputResult.HANDLED
            }
        }
    }

    override fun onRight(): InputResult {
        val state = viewModel.uiState.value
        if (state.showGamePicker || state.showPostConfirm || state.showDiscardDialog) {
            return InputResult.UNHANDLED
        }
        if (state.currentSection != PostEditorSection.DOODLE) {
            viewModel.focusDoodle()
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onLeft(): InputResult {
        val state = viewModel.uiState.value
        if (state.showGamePicker || state.showPostConfirm || state.showDiscardDialog) {
            return InputResult.UNHANDLED
        }
        if (state.currentSection == PostEditorSection.DOODLE) {
            viewModel.focusFromDoodle()
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onConfirm(): InputResult {
        val state = viewModel.uiState.value
        return when {
            state.showGamePicker && !state.gamePickerSearchFocused -> {
                viewModel.selectGame()
                InputResult.HANDLED
            }
            state.showGamePicker -> InputResult.HANDLED
            state.showPostConfirm -> {
                if (state.postConfirmFocusIndex == 0) viewModel.post()
                else viewModel.hidePostConfirm()
                InputResult.HANDLED
            }
            state.showDiscardDialog -> {
                if (state.discardFocusIndex == 0) {
                    viewModel.hideDiscardDialog()
                    onNavigateBack()
                } else {
                    viewModel.hideDiscardDialog()
                }
                InputResult.HANDLED
            }
            state.currentSection == PostEditorSection.BODY -> {
                onOpenKeyboard()
                InputResult.HANDLED
            }
            state.currentSection == PostEditorSection.GAME -> {
                viewModel.showGamePicker()
                InputResult.HANDLED
            }
            state.currentSection == PostEditorSection.VISIBILITY -> {
                viewModel.togglePublic()
                InputResult.HANDLED
            }
            state.currentSection == PostEditorSection.DOODLE -> {
                if (state.hasDoodle) {
                    viewModel.removeDoodle()
                } else {
                    onNavigateToDoodle()
                }
                InputResult.HANDLED
            }
            else -> InputResult.UNHANDLED
        }
    }

    override fun onBack(): InputResult {
        val state = viewModel.uiState.value
        return when {
            state.showGamePicker -> {
                viewModel.hideGamePicker()
                InputResult.HANDLED
            }
            state.showPostConfirm -> {
                viewModel.hidePostConfirm()
                InputResult.HANDLED
            }
            state.showDiscardDialog -> {
                viewModel.hideDiscardDialog()
                InputResult.HANDLED
            }
            state.hasContent -> {
                viewModel.showDiscardDialog()
                InputResult.HANDLED
            }
            else -> {
                onNavigateBack()
                InputResult.HANDLED
            }
        }
    }

    override fun onMenu(): InputResult {
        val state = viewModel.uiState.value
        if (!state.showPostConfirm && !state.showDiscardDialog && !state.showGamePicker && state.canPost) {
            viewModel.showPostConfirm()
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onSelect(): InputResult = onMenu()

    override fun onSecondaryAction(): InputResult {
        val state = viewModel.uiState.value
        if (state.showPostConfirm || state.showDiscardDialog || state.showGamePicker) return InputResult.UNHANDLED
        if (state.currentSection == PostEditorSection.GAME && state.linkedGameTitle != null) {
            viewModel.clearLinkedGame()
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onPrevSection(): InputResult {
        val state = viewModel.uiState.value
        if (!state.showPostConfirm && !state.showDiscardDialog && !state.showGamePicker) {
            viewModel.previousSection()
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onNextSection(): InputResult {
        val state = viewModel.uiState.value
        if (!state.showPostConfirm && !state.showDiscardDialog && !state.showGamePicker) {
            viewModel.nextSection()
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }
}
