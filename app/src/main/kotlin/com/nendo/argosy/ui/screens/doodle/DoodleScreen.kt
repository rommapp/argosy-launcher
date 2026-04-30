package com.nendo.argosy.ui.screens.doodle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Timeline
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem
import com.nendo.argosy.ui.util.clickableNoFocus

@Composable
fun DoodleScreen(
    onBack: () -> Unit,
    onDone: (doodleData: String, canvasSize: Int, gameId: Int?, gameTitle: String?, gameCoverPath: String?) -> Unit,
    initialGameId: Int? = null,
    initialGameTitle: String? = null,
    initialGameCoverPath: String? = null,
    viewModel: DoodleViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(initialGameId) {
        viewModel.initGame(initialGameId, initialGameTitle, initialGameCoverPath)
    }

    val inputDispatcher = LocalInputDispatcher.current
    val inputHandler = remember(viewModel, onBack) {
        DoodleInputHandler(
            viewModel = viewModel,
            onNavigateBack = onBack
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.subscribeView(inputHandler, forRoute = "doodle")
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler, forRoute = "doodle")
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DoodleEvent.Done -> onDone(
                    event.doodleData, event.canvasSize,
                    event.gameId, event.gameTitle, event.gameCoverPath
                )
                is DoodleEvent.Error -> {}
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isLandscape = maxWidth > maxHeight

        if (isLandscape) {
            LandscapeLayout(uiState = uiState, viewModel = viewModel)
        } else {
            PortraitLayout(uiState = uiState, viewModel = viewModel)
        }
    }

    if (uiState.showDiscardDialog) {
        DiscardDialog(
            focusIndex = uiState.discardDialogFocusIndex,
            onDiscard = {
                viewModel.hideDiscardDialog()
                onBack()
            },
            onCancel = { viewModel.hideDiscardDialog() }
        )
    }

    if (uiState.showGamePicker) {
        GamePickerDialog(
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
private fun LandscapeLayout(
    uiState: DoodleUiState,
    viewModel: DoodleViewModel
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(end = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                DoodleCanvas(
                    canvasSize = uiState.canvasSize,
                    pixels = uiState.pixels,
                    cursorX = uiState.cursorX,
                    cursorY = uiState.cursorY,
                    showCursor = uiState.currentSection == DoodleSection.CANVAS,
                    linePreview = uiState.linePreview,
                    selectedColor = uiState.selectedColor,
                    zoomLevel = uiState.zoomLevel,
                    panOffsetX = uiState.panOffsetX,
                    panOffsetY = uiState.panOffsetY,
                    onTap = { x, y -> viewModel.tapAt(x, y) },
                    onDrag = { x, y -> viewModel.drawAt(x, y) },
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            width = if (uiState.currentSection == DoodleSection.CANVAS) 2.dp else 1.dp,
                            color = if (uiState.currentSection == DoodleSection.CANVAS)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(8.dp)
                        )
                )
            }

            Column(
                modifier = Modifier
                    .width(200.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ToolSelector(
                    selectedTool = uiState.selectedTool,
                    onToolSelect = { viewModel.cycleTool() }
                )

                PaletteGrid(
                    selectedColor = uiState.selectedColor,
                    focusIndex = uiState.paletteFocusIndex,
                    isFocused = uiState.currentSection == DoodleSection.PALETTE,
                    onColorSelect = { viewModel.selectColor(it) },
                    columns = 8
                )

                SizeSelector(
                    selectedSize = uiState.canvasSize,
                    focusIndex = uiState.sizeFocusIndex,
                    isFocused = uiState.currentSection == DoodleSection.SIZE,
                    onSizeSelect = { viewModel.setCanvasSize(it) }
                )

                UndoRedoButtons(
                    canUndo = uiState.canUndo,
                    canRedo = uiState.canRedo,
                    undoFocused = uiState.currentSection == DoodleSection.UNDO,
                    redoFocused = uiState.currentSection == DoodleSection.REDO,
                    onUndo = { viewModel.undo() },
                    onRedo = { viewModel.redo() }
                )

                GameSection(
                    linkedGameTitle = uiState.linkedGameTitle,
                    linkedGameCoverPath = uiState.linkedGameCoverPath,
                    isFocused = uiState.currentSection == DoodleSection.GAME,
                    onClick = { viewModel.showGamePicker() }
                )

                if (uiState.zoomLevel != ZoomLevel.FIT) {
                    ZoomIndicator(zoomLevel = uiState.zoomLevel)
                }
            }
        }

        DoodleFooter(
            currentSection = uiState.currentSection,
            selectedTool = uiState.selectedTool,
            isDrawing = uiState.isDrawing,
            hasContent = uiState.hasContent,
            canUndo = uiState.canUndo,
            canRedo = uiState.canRedo,
            linkedGameTitle = uiState.linkedGameTitle
        )
    }
}

@Composable
private fun PortraitLayout(
    uiState: DoodleUiState,
    viewModel: DoodleViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolSelector(
                selectedTool = uiState.selectedTool,
                onToolSelect = { viewModel.cycleTool() }
            )

            SizeSelector(
                selectedSize = uiState.canvasSize,
                focusIndex = uiState.sizeFocusIndex,
                isFocused = uiState.currentSection == DoodleSection.SIZE,
                onSizeSelect = { viewModel.setCanvasSize(it) }
            )

            if (uiState.zoomLevel != ZoomLevel.FIT) {
                ZoomIndicator(zoomLevel = uiState.zoomLevel)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            DoodleCanvas(
                canvasSize = uiState.canvasSize,
                pixels = uiState.pixels,
                cursorX = uiState.cursorX,
                cursorY = uiState.cursorY,
                showCursor = uiState.currentSection == DoodleSection.CANVAS,
                linePreview = uiState.linePreview,
                selectedColor = uiState.selectedColor,
                zoomLevel = uiState.zoomLevel,
                panOffsetX = uiState.panOffsetX,
                panOffsetY = uiState.panOffsetY,
                onTap = { x, y -> viewModel.tapAt(x, y) },
                onDrag = { x, y -> viewModel.drawAt(x, y) },
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        width = if (uiState.currentSection == DoodleSection.CANVAS) 2.dp else 1.dp,
                        color = if (uiState.currentSection == DoodleSection.CANVAS)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(8.dp)
                    )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        PaletteGrid(
            selectedColor = uiState.selectedColor,
            focusIndex = uiState.paletteFocusIndex,
            isFocused = uiState.currentSection == DoodleSection.PALETTE,
            onColorSelect = { viewModel.selectColor(it) },
            columns = 16
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            UndoRedoButtons(
                canUndo = uiState.canUndo,
                canRedo = uiState.canRedo,
                undoFocused = uiState.currentSection == DoodleSection.UNDO,
                redoFocused = uiState.currentSection == DoodleSection.REDO,
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                modifier = Modifier.weight(1f)
            )

            GameSection(
                linkedGameTitle = uiState.linkedGameTitle,
                linkedGameCoverPath = uiState.linkedGameCoverPath,
                isFocused = uiState.currentSection == DoodleSection.GAME,
                onClick = { viewModel.showGamePicker() },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        DoodleFooter(
            currentSection = uiState.currentSection,
            selectedTool = uiState.selectedTool,
            isDrawing = uiState.isDrawing,
            hasContent = uiState.hasContent,
            canUndo = uiState.canUndo,
            canRedo = uiState.canRedo,
            linkedGameTitle = uiState.linkedGameTitle
        )
    }
}

@Composable
private fun ToolSelector(
    selectedTool: DoodleTool,
    onToolSelect: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        DoodleTool.entries.forEach { tool ->
            val isSelected = tool == selectedTool
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface
                    )
                    .clickable { onToolSelect() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (tool) {
                        DoodleTool.PEN -> Icons.Default.Edit
                        DoodleTool.LINE -> Icons.Default.Timeline
                        DoodleTool.FILL -> Icons.Default.FormatColorFill
                    },
                    contentDescription = when (tool) {
                        DoodleTool.PEN -> "Pen"
                        DoodleTool.LINE -> "Line"
                        DoodleTool.FILL -> "Fill"
                    },
                    modifier = Modifier.size(18.dp),
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun PaletteGrid(
    selectedColor: DoodleColor,
    focusIndex: Int,
    isFocused: Boolean,
    onColorSelect: (DoodleColor) -> Unit,
    columns: Int
) {
    val rows = 16 / columns

    Column(
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
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(rows) { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                repeat(columns) { col ->
                    val colorIndex = row * columns + col
                    if (colorIndex < 16) {
                        val color = DoodleColor.fromIndex(colorIndex)
                        val isColorSelected = color == selectedColor
                        val isColorFocused = isFocused && colorIndex == focusIndex

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(color.color)
                                .then(
                                    when {
                                        isColorFocused -> Modifier.border(
                                            2.dp,
                                            MaterialTheme.colorScheme.primary,
                                            CircleShape
                                        )
                                        isColorSelected -> Modifier.border(
                                            2.dp,
                                            Color.White,
                                            CircleShape
                                        )
                                        else -> Modifier
                                    }
                                )
                                .clickable { onColorSelect(color) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SizeSelector(
    selectedSize: CanvasSize,
    focusIndex: Int,
    isFocused: Boolean,
    onSizeSelect: (CanvasSize) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
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
            .padding(4.dp)
    ) {
        CanvasSize.entries.forEach { size ->
            val isSelected = size == selectedSize
            val isSizeFocused = isFocused && size.sizeEnum == focusIndex

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when {
                            isSizeFocused -> MaterialTheme.colorScheme.primaryContainer
                            isSelected -> MaterialTheme.colorScheme.secondaryContainer
                            else -> Color.Transparent
                        }
                    )
                    .clickable { onSizeSelect(size) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${size.pixels}",
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        isSizeFocused -> MaterialTheme.colorScheme.onPrimaryContainer
                        isSelected -> MaterialTheme.colorScheme.onSecondaryContainer
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}

@Composable
private fun UndoRedoButtons(
    canUndo: Boolean,
    canRedo: Boolean,
    undoFocused: Boolean,
    redoFocused: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
                .then(
                    if (undoFocused) Modifier.border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(8.dp)
                    )
                    else Modifier
                )
                .clickableNoFocus(enabled = canUndo, onClick = onUndo)
                .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                    modifier = Modifier.size(18.dp),
                    tint = if (canUndo) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
                Text(
                    text = "Undo",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (canUndo) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
                .then(
                    if (redoFocused) Modifier.border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(8.dp)
                    )
                    else Modifier
                )
                .clickableNoFocus(enabled = canRedo, onClick = onRedo)
                .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "Redo",
                    modifier = Modifier.size(18.dp),
                    tint = if (canRedo) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
                Text(
                    text = "Redo",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (canRedo) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }
    }
}

@Composable
private fun ZoomIndicator(zoomLevel: ZoomLevel) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = "${zoomLevel.scale.toInt()}x",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun DoodleFooter(
    currentSection: DoodleSection,
    selectedTool: DoodleTool,
    isDrawing: Boolean,
    hasContent: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    linkedGameTitle: String? = null
) {
    val hints = buildList {
        when (currentSection) {
            DoodleSection.CANVAS -> {
                add(InputButton.DPAD to "Move")
                val drawLabel = when {
                    selectedTool == DoodleTool.LINE && isDrawing -> "End"
                    isDrawing -> "Stop"
                    selectedTool == DoodleTool.LINE -> "Start"
                    selectedTool == DoodleTool.FILL -> "Fill"
                    else -> "Draw"
                }
                add(InputButton.A to drawLabel)
                add(InputButton.Y to "Tool")
            }
            DoodleSection.PALETTE -> {
                add(InputButton.DPAD to "Select")
                add(InputButton.A to "Pick")
            }
            DoodleSection.SIZE -> {
                add(InputButton.DPAD_HORIZONTAL to "Size")
                add(InputButton.A to "Confirm")
            }
            DoodleSection.UNDO -> {
                if (canUndo) add(InputButton.A to "Undo")
            }
            DoodleSection.REDO -> {
                if (canRedo) add(InputButton.A to "Redo")
            }
            DoodleSection.GAME -> {
                add(InputButton.A to "Select")
                if (linkedGameTitle != null) {
                    add(InputButton.Y to "Clear")
                }
            }
        }
        if (hasContent) {
            add(InputButton.START to "Done")
        }
        val backLabel = when {
            isDrawing -> "Cancel"
            hasContent -> "Discard"
            else -> "Back"
        }
        add(InputButton.B to backLabel)
    }

    FooterBar(hints = hints)
}

@Composable
private fun GameSection(
    linkedGameTitle: String?,
    linkedGameCoverPath: String?,
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
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
            .clickableNoFocus(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (linkedGameCoverPath != null) {
            AsyncImage(
                model = rememberFileImageModel(linkedGameCoverPath),
                contentDescription = linkedGameTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        } else {
            Icon(
                imageVector = Icons.Default.SportsEsports,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        Text(
            text = linkedGameTitle ?: "Game",
            style = MaterialTheme.typography.labelMedium,
            color = if (linkedGameTitle != null) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            maxLines = 1
        )
    }
}

@Composable
private fun GamePickerDialog(
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

@Composable
private fun DiscardDialog(
    focusIndex: Int,
    onDiscard: () -> Unit,
    onCancel: () -> Unit
) {
    Modal(title = "Discard Doodle?") {
        Text(
            text = "You have unsaved changes. Are you sure you want to discard your doodle?",
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
