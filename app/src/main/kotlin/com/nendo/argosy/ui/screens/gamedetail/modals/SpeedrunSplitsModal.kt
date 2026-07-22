package com.nendo.argosy.ui.screens.gamedetail.modals

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.components.NestedModal
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem
import com.nendo.argosy.ui.screens.gamedetail.delegates.SpeedrunImport
import com.nendo.argosy.ui.screens.gamedetail.delegates.SpeedrunPrompt
import com.nendo.argosy.ui.screens.gamedetail.delegates.SpeedrunSplitsDelegate
import com.nendo.argosy.ui.screens.gamedetail.delegates.SpeedrunSplitsState
import com.nendo.argosy.ui.theme.Dimens

@Composable
fun SpeedrunSplitsModal(
    gameTitle: String,
    state: SpeedrunSplitsState,
    delegate: SpeedrunSplitsDelegate
) {
    val inputDispatcher = LocalInputDispatcher.current
    val currentState = rememberUpdatedState(state)
    var promptText by remember(state.prompt) {
        mutableStateOf((state.prompt as? SpeedrunPrompt.Text)?.initial ?: "")
    }
    val currentPromptText = rememberUpdatedState(promptText)

    val inputHandler = remember(delegate) {
        object : InputHandler {
            override fun onUp(): InputResult {
                val s = currentState.value
                when {
                    s.import != null -> delegate.moveImportFocus(-1)
                    s.prompt == null -> delegate.moveFocus(-1)
                }
                return InputResult.HANDLED
            }
            override fun onDown(): InputResult {
                val s = currentState.value
                when {
                    s.import != null -> delegate.moveImportFocus(1)
                    s.prompt == null -> delegate.moveFocus(1)
                }
                return InputResult.HANDLED
            }
            override fun onLeft(): InputResult {
                if (currentState.value.import is SpeedrunImport.Preview) delegate.cyclePreviewRunner(-1)
                return InputResult.HANDLED
            }
            override fun onRight(): InputResult {
                if (currentState.value.import is SpeedrunImport.Preview) delegate.cyclePreviewRunner(1)
                return InputResult.HANDLED
            }
            override fun onConfirm(): InputResult {
                val s = currentState.value
                when {
                    s.import != null -> delegate.confirmImport()
                    s.prompt != null -> delegate.confirmPrompt(currentPromptText.value)
                    else -> delegate.confirmFocused()
                }
                return InputResult.HANDLED
            }
            override fun onBack(): InputResult {
                delegate.dismiss()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }
            override fun onContextMenu(): InputResult {
                val s = currentState.value
                if (s.prompt == null && s.import == null) delegate.promptNew()
                return InputResult.HANDLED
            }
            override fun onSecondaryAction(): InputResult {
                val s = currentState.value
                if (s.prompt == null && s.import == null) delegate.promptDelete()
                return InputResult.HANDLED
            }
            override fun onPrevSection(): InputResult {
                if (currentState.value.import == null) delegate.moveSegment(-1)
                return InputResult.HANDLED
            }
            override fun onNextSection(): InputResult {
                if (currentState.value.import == null) delegate.moveSegment(1)
                return InputResult.HANDLED
            }
            override fun onMenu(): InputResult = InputResult.HANDLED
            override fun onSelect(): InputResult = InputResult.HANDLED
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.pushModal(inputHandler)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            inputDispatcher.removeModal(inputHandler)
        }
    }

    val editing = state.editingCategory
    val listState = rememberLazyListState()
    FocusedScroll(listState = listState, focusedIndex = state.focusIndex)

    Modal(
        title = if (editing != null) "${editing.name} — Segments" else "Speedrun Splits",
        subtitle = if (editing == null) gameTitle else null,
        onDismiss = { delegate.dismiss() },
        footerHints = if (editing != null) {
            listOf(
                InputButton.X to "Add Segment",
                InputButton.Y to "Delete",
                InputButton.LB_RB to "Move",
                InputButton.B to "Back"
            )
        } else {
            listOf(
                InputButton.A to "Open",
                InputButton.X to "New Category",
                InputButton.B to "Close"
            )
        }
    ) {
        if (editing != null) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                itemsIndexed(state.segments, key = { index, _ -> index }) { index, segment ->
                    OptionItem(
                        label = "${index + 1}.  $segment",
                        isFocused = state.focusIndex == index,
                        onClick = { delegate.confirmFocusedAt(index) }
                    )
                }
                item(key = "rename-category") {
                    OptionItem(
                        label = "Rename Category",
                        isFocused = state.focusIndex == state.segments.size,
                        onClick = { delegate.confirmFocusedAt(state.segments.size) }
                    )
                }
                item(key = "delete-category") {
                    OptionItem(
                        label = "Delete Category",
                        isDangerous = true,
                        isFocused = state.focusIndex == state.segments.size + 1,
                        onClick = { delegate.confirmFocusedAt(state.segments.size + 1) }
                    )
                }
            }
        } else {
            if (state.categories.isEmpty()) {
                Text(
                    text = "No categories yet. Press X to create one, or import from the community.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Dimens.spacingMd)
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                itemsIndexed(state.categories, key = { _, category -> category.id }) { index, category ->
                    OptionItem(
                        label = category.name,
                        value = "${category.segmentCount} segments · ${category.attemptCount} attempts",
                        isFocused = state.focusIndex == index,
                        onClick = { delegate.confirmFocusedAt(index) }
                    )
                }
                item(key = "import-splits") {
                    OptionItem(
                        icon = Icons.Default.CloudDownload,
                        label = "Import Splits...",
                        isFocused = state.focusIndex == state.categories.size,
                        onClick = { delegate.confirmFocusedAt(state.categories.size) }
                    )
                }
            }
        }
    }

    when (val import = state.import) {
        is SpeedrunImport.Loading -> NestedModal(title = "Searching...") {
            Text(
                text = "Looking up categories on therun.gg and speedrun.com",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        is SpeedrunImport.Importing -> NestedModal(title = "Importing...") {
            Text(
                text = "Fetching splits from a top run",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        is SpeedrunImport.Failed -> NestedModal(
            title = "Import",
            onDismiss = { delegate.dismiss() },
            footerHints = listOf(InputButton.B to "Back")
        ) {
            Text(
                text = import.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        is SpeedrunImport.Preview -> NestedModal(
            title = import.entry.label,
            onDismiss = { delegate.dismiss() },
            footerHints = listOf(
                InputButton.DPAD_HORIZONTAL to "Other Runner",
                InputButton.A to "Import",
                InputButton.B to "Back"
            )
        ) {
            Text(
                text = "Splits by ${import.template.runnerUsername} (#${import.runnerIndex + 1})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                itemsIndexed(import.template.segments, key = { index, _ -> index }) { index, segment ->
                    Text(
                        text = "${index + 1}.  $segment",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = Dimens.spacingXs)
                    )
                }
            }
        }
        is SpeedrunImport.Options -> {
            val importListState = rememberLazyListState()
            FocusedScroll(listState = importListState, focusedIndex = import.focusIndex)
            NestedModal(
                title = "Import Splits",
                onDismiss = { delegate.dismiss() },
                footerHints = listOf(
                    InputButton.A to "Import",
                    InputButton.B to "Cancel"
                )
            ) {
                LazyColumn(
                    state = importListState,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    itemsIndexed(import.entries, key = { index, _ -> index }) { index, entry ->
                        OptionItem(
                            label = entry.label,
                            value = when (entry.source) {
                                com.nendo.argosy.data.speedrun.SeedCategory.Source.THERUN -> "therun.gg · full splits"
                                com.nendo.argosy.data.speedrun.SeedCategory.Source.SPEEDRUN_COM -> "speedrun.com · name only"
                            },
                            isFocused = import.focusIndex == index,
                            onClick = { delegate.confirmImportAt(index) }
                        )
                    }
                }
            }
        }
        null -> Unit
    }

    when (val prompt = state.prompt) {
        is SpeedrunPrompt.Text -> NestedModal(
            title = prompt.title,
            onDismiss = { delegate.dismissPrompt() },
            footerHints = listOf(
                InputButton.A to "Confirm",
                InputButton.B to "Cancel"
            )
        ) {
            OutlinedTextField(
                value = promptText,
                onValueChange = { promptText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Name") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
        }
        is SpeedrunPrompt.ConfirmDelete -> NestedModal(
            title = "Delete \"${prompt.title}\"?",
            onDismiss = { delegate.dismissPrompt() },
            footerHints = listOf(
                InputButton.A to "Delete",
                InputButton.B to "Cancel"
            )
        ) {
            Text(
                text = if (prompt.isCategory) {
                    "This deletes the category, its segments, and all recorded attempts."
                } else {
                    "This removes the segment from the category."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        null -> Unit
    }
}
