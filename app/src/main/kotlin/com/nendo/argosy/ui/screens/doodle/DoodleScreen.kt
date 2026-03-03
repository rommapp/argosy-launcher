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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.input.LocalInputDispatcher

@Composable
fun DoodleScreen(
    onBack: () -> Unit,
    onPosted: () -> Unit,
    viewModel: DoodleViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val captionFocusRequester = remember { FocusRequester() }
    var openKeyboard by remember { mutableStateOf(false) }

    val inputDispatcher = LocalInputDispatcher.current
    val inputHandler = remember(viewModel, onBack) {
        DoodleInputHandler(
            viewModel = viewModel,
            onOpenKeyboard = { openKeyboard = true },
            onBack = onBack
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

    LaunchedEffect(openKeyboard) {
        if (openKeyboard) {
            captionFocusRequester.requestFocus()
            openKeyboard = false
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DoodleEvent.Posted -> onPosted()
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
            LandscapeLayout(
                uiState = uiState,
                viewModel = viewModel,
                captionFocusRequester = captionFocusRequester
            )
        } else {
            PortraitLayout(
                uiState = uiState,
                viewModel = viewModel,
                captionFocusRequester = captionFocusRequester
            )
        }
    }

    if (uiState.showPostMenu) {
        PostMenuDialog(
            isPosting = uiState.isPosting,
            focusIndex = uiState.postMenuFocusIndex,
            onPost = { viewModel.post() },
            onCancel = { viewModel.hidePostMenu() }
        )
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
}

@Composable
private fun LandscapeLayout(
    uiState: DoodleUiState,
    viewModel: DoodleViewModel,
    captionFocusRequester: FocusRequester
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

                CaptionInput(
                    caption = uiState.caption,
                    onCaptionChange = { viewModel.setCaption(it) },
                    isFocused = uiState.currentSection == DoodleSection.CAPTION,
                    focusRequester = captionFocusRequester,
                    linkedGameTitle = uiState.linkedGameTitle
                )

                if (uiState.zoomLevel != ZoomLevel.FIT) {
                    ZoomIndicator(zoomLevel = uiState.zoomLevel)
                }
            }
        }

        DoodleFooter(currentSection = uiState.currentSection)
    }
}

@Composable
private fun PortraitLayout(
    uiState: DoodleUiState,
    viewModel: DoodleViewModel,
    captionFocusRequester: FocusRequester
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

        CaptionInput(
            caption = uiState.caption,
            onCaptionChange = { viewModel.setCaption(it) },
            isFocused = uiState.currentSection == DoodleSection.CAPTION,
            focusRequester = captionFocusRequester,
            linkedGameTitle = uiState.linkedGameTitle
        )

        Spacer(modifier = Modifier.height(8.dp))

        DoodleFooter(currentSection = uiState.currentSection)
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
                Text(
                    text = when (tool) {
                        DoodleTool.PEN -> "P"
                        DoodleTool.LINE -> "L"
                        DoodleTool.FILL -> "F"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
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
private fun CaptionInput(
    caption: String,
    onCaptionChange: (String) -> Unit,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    linkedGameTitle: String?
) {
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
            .padding(12.dp)
    ) {
        Box {
            if (caption.isEmpty()) {
                Text(
                    text = "Add a caption...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            BasicTextField(
                value = caption,
                onValueChange = onCaptionChange,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        }

        if (linkedGameTitle != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Game: $linkedGameTitle",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
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
private fun DoodleFooter(currentSection: DoodleSection) {
    val hints = buildList {
        add(InputButton.LB_RB to "Section")
        when (currentSection) {
            DoodleSection.CANVAS -> {
                add(InputButton.DPAD to "Move")
                add(InputButton.A to "Draw")
                add(InputButton.X to "Tool")
            }
            DoodleSection.PALETTE -> {
                add(InputButton.DPAD to "Select")
                add(InputButton.A to "Pick")
            }
            DoodleSection.SIZE -> {
                add(InputButton.DPAD_HORIZONTAL to "Size")
                add(InputButton.A to "Confirm")
            }
            DoodleSection.CAPTION -> {
                add(InputButton.A to "Edit")
            }
        }
        add(InputButton.START to "Post")
        add(InputButton.B to "Back")
    }

    FooterBar(hints = hints)
}

@Composable
private fun PostMenuDialog(
    isPosting: Boolean,
    focusIndex: Int,
    onPost: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isPosting) onCancel() },
        title = {
            Text(
                text = "Post Doodle",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            if (isPosting) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text("Posting...")
                }
            } else {
                Text("Share your doodle with friends?")
            }
        },
        confirmButton = {
            Button(
                onClick = onPost,
                enabled = !isPosting,
                colors = if (focusIndex == 0) ButtonDefaults.buttonColors()
                else ButtonDefaults.outlinedButtonColors()
            ) {
                Text("Post")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onCancel,
                enabled = !isPosting,
                colors = if (focusIndex == 1) ButtonDefaults.buttonColors()
                else ButtonDefaults.outlinedButtonColors()
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DiscardDialog(
    focusIndex: Int,
    onDiscard: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = "Discard Doodle?",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text("You have unsaved changes. Are you sure you want to discard your doodle?")
        },
        confirmButton = {
            Button(
                onClick = onDiscard,
                colors = if (focusIndex == 0) ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
                else ButtonDefaults.outlinedButtonColors()
            ) {
                Text("Discard")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onCancel,
                colors = if (focusIndex == 1) ButtonDefaults.buttonColors()
                else ButtonDefaults.outlinedButtonColors()
            ) {
                Text("Keep Editing")
            }
        }
    )
}
