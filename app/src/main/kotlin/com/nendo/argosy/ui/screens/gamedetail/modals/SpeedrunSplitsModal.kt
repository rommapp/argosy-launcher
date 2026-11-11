package com.nendo.argosy.ui.screens.gamedetail.modals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.components.NestedModal
import com.nendo.argosy.ui.components.TEXT_ENTRY_CANCEL_BUTTON
import com.nendo.argosy.ui.components.TEXT_ENTRY_CONFIRM_BUTTON
import com.nendo.argosy.ui.components.TextEntryModal
import com.nendo.argosy.ui.components.TextEntryRow
import com.nendo.argosy.ui.components.dragReorderContainer
import com.nendo.argosy.ui.components.dragReorderItem
import com.nendo.argosy.ui.components.liftedReorderHints
import com.nendo.argosy.ui.components.rememberDragReorderState
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.primitives.InputGlyph
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
    val textPrompt = state.prompt as? SpeedrunPrompt.Text
    var promptText by remember(textPrompt?.title, textPrompt?.initial, textPrompt?.target) {
        mutableStateOf(textPrompt?.initial ?: "")
    }
    val currentPromptText = rememberUpdatedState(promptText)

    val inputHandler = remember(delegate) {
        object : InputHandler {
            override fun onUp(): InputResult {
                val s = currentState.value
                when {
                    s.import != null -> delegate.moveImportFocus(-1)
                    s.prompt is SpeedrunPrompt.Text -> delegate.movePromptRow(TextEntryRow.FIELD)
                    s.prompt == null -> delegate.moveFocus(-1)
                }
                return InputResult.HANDLED
            }
            override fun onDown(): InputResult {
                val s = currentState.value
                when {
                    s.import != null -> delegate.moveImportFocus(1)
                    s.prompt is SpeedrunPrompt.Text -> delegate.movePromptRow(TextEntryRow.BUTTONS)
                    s.prompt == null -> delegate.moveFocus(1)
                }
                return InputResult.HANDLED
            }
            override fun onLeft(): InputResult {
                val s = currentState.value
                when {
                    s.import is SpeedrunImport.Preview -> delegate.cyclePreviewRunner(-1)
                    s.prompt is SpeedrunPrompt.Text -> delegate.movePromptButton(TEXT_ENTRY_CANCEL_BUTTON)
                }
                return InputResult.HANDLED
            }
            override fun onRight(): InputResult {
                val s = currentState.value
                when {
                    s.import is SpeedrunImport.Preview -> delegate.cyclePreviewRunner(1)
                    s.prompt is SpeedrunPrompt.Text -> delegate.movePromptButton(TEXT_ENTRY_CONFIRM_BUTTON)
                }
                return InputResult.HANDLED
            }
            override fun onConfirm(): InputResult {
                val s = currentState.value
                val prompt = s.prompt
                when {
                    s.import != null -> delegate.confirmImport()
                    prompt is SpeedrunPrompt.Text -> when {
                        prompt.focus.row == TextEntryRow.FIELD -> delegate.requestPromptKeyboard()
                        prompt.focus.buttonIndex == TEXT_ENTRY_CANCEL_BUTTON -> delegate.dismissPrompt()
                        else -> delegate.confirmPrompt(currentPromptText.value)
                    }
                    prompt != null -> delegate.confirmPrompt(currentPromptText.value)
                    else -> delegate.confirmFocused()
                }
                return InputResult.HANDLED
            }
            override fun onBack(): InputResult {
                val reordering = currentState.value.isReordering
                delegate.dismiss()
                return InputResult.handled(if (reordering) SoundType.BACK else SoundType.CLOSE_MODAL)
            }
            override fun onContextMenu(): InputResult {
                val s = currentState.value
                if (s.prompt == null && s.import == null) delegate.promptDelete()
                return InputResult.HANDLED
            }
            override fun onSecondaryAction(): InputResult {
                val s = currentState.value
                if (s.prompt != null || s.import != null || s.editingCategory == null) return InputResult.HANDLED
                if (s.isReordering) delegate.dropSegment() else delegate.liftSegment()
                return InputResult.handled(SoundType.SELECT)
            }
            override fun onSelect(): InputResult {
                val s = currentState.value
                if (s.prompt == null && s.import == null) delegate.promptNew()
                return InputResult.HANDLED
            }
            override fun onPrevSection(): InputResult = InputResult.HANDLED
            override fun onNextSection(): InputResult = InputResult.HANDLED
            override fun onMenu(): InputResult = InputResult.HANDLED
        }
    }

    com.nendo.argosy.ui.input.ModalInputEffect(active = true, handler = inputHandler, inputDispatcher = inputDispatcher)

    val editing = state.editingCategory
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val dragState = rememberDragReorderState(
        listState = listState,
        canDrag = { key -> key is Long && currentState.value.segments.any { it.key == key } },
        onLift = { key ->
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            delegate.liftSegmentAt(key as Long)
        },
        onMove = { key, index -> delegate.moveHeldSegmentTo(key as Long, index) },
        onDrop = { delegate.dropSegment() }
    )
    if (dragState.draggingKey == null) {
        FocusedScroll(listState = listState, focusedIndex = state.focusIndex)
    }

    Modal(
        title = if (editing != null) {
            stringResource(R.string.gamedetail_speedrun_segments_title, editing.name)
        } else {
            stringResource(R.string.gamedetail_speedrun_title)
        },
        subtitle = if (editing == null) gameTitle else null,
        onDismiss = { delegate.dismiss() },
        footerHints = when {
            state.isReordering -> liftedReorderHints(
                move = stringResource(R.string.gamedetail_speedrun_footer_move),
                cancel = stringResource(R.string.gamedetail_speedrun_footer_cancel)
            )
            editing != null -> listOf(
                InputButton.SELECT to stringResource(R.string.gamedetail_speedrun_footer_add_segment),
                InputButton.Y to stringResource(R.string.gamedetail_speedrun_footer_reorder),
                InputButton.X to stringResource(R.string.gamedetail_speedrun_footer_delete),
                InputButton.B to stringResource(R.string.gamedetail_speedrun_footer_back)
            )
            else -> listOf(
                InputButton.A to stringResource(R.string.gamedetail_speedrun_footer_open),
                InputButton.SELECT to stringResource(R.string.gamedetail_speedrun_footer_new_category),
                InputButton.B to stringResource(R.string.gamedetail_speedrun_footer_close)
            )
        },
        onFooterHintClick = { button ->
            when (button) {
                InputButton.A -> inputHandler.onConfirm()
                InputButton.B -> inputHandler.onBack()
                InputButton.X -> inputHandler.onContextMenu()
                InputButton.Y -> inputHandler.onSecondaryAction()
                InputButton.SELECT -> inputHandler.onSelect()
                else -> Unit
            }
        }
    ) {
        if (editing != null) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .dragReorderContainer(dragState)
            ) {
                itemsIndexed(state.segments, key = { _, segment -> segment.key }) { index, segment ->
                    Box(
                        modifier = Modifier
                            .then(
                                if (dragState.draggingKey == null || dragState.draggingKey == segment.key) {
                                    Modifier
                                } else {
                                    Modifier.animateItem()
                                }
                            )
                            .dragReorderItem(dragState, segment.key)
                    ) {
                        OptionItem(
                            label = stringResource(
                                R.string.gamedetail_speedrun_segment_row,
                                index + 1,
                                segment.name
                            ),
                            isFocused = state.focusIndex == index,
                            trailingIcon = if (state.reorder?.heldIndex == index) Icons.Default.DragHandle else null,
                            trailingTint = MaterialTheme.colorScheme.primary,
                            onClick = { delegate.confirmFocusedAt(index) }
                        )
                    }
                }
                item(key = "add-segment") {
                    OptionItem(
                        icon = Icons.Default.Add,
                        label = stringResource(R.string.gamedetail_speedrun_add_segment_row),
                        isFocused = state.focusIndex == state.segments.size,
                        onClick = { delegate.confirmFocusedAt(state.segments.size) }
                    )
                }
                item(key = "rename-category") {
                    OptionItem(
                        label = stringResource(R.string.gamedetail_speedrun_rename_category),
                        isFocused = state.focusIndex == state.segments.size + 1,
                        onClick = { delegate.confirmFocusedAt(state.segments.size + 1) }
                    )
                }
                item(key = "delete-category") {
                    OptionItem(
                        label = stringResource(R.string.gamedetail_speedrun_delete_category),
                        isDangerous = true,
                        isFocused = state.focusIndex == state.segments.size + 2,
                        onClick = { delegate.confirmFocusedAt(state.segments.size + 2) }
                    )
                }
            }
        } else {
            if (state.categories.isEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
                    modifier = Modifier.padding(vertical = Dimens.spacingMd)
                ) {
                    Text(
                        text = stringResource(R.string.gamedetail_speedrun_empty_prefix),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    InputGlyph(button = InputButton.SELECT)
                    Text(
                        text = stringResource(R.string.gamedetail_speedrun_empty_suffix),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                itemsIndexed(state.categories, key = { _, category -> category.id }) { index, category ->
                    OptionItem(
                        label = category.name,
                        value = stringResource(
                            R.string.gamedetail_speedrun_category_summary,
                            category.segmentCount,
                            category.attemptCount
                        ),
                        isFocused = state.focusIndex == index,
                        onClick = { delegate.confirmFocusedAt(index) }
                    )
                }
                item(key = "new-category") {
                    OptionItem(
                        icon = Icons.Default.Add,
                        label = stringResource(R.string.gamedetail_speedrun_new_category_row),
                        isFocused = state.focusIndex == state.categories.size,
                        onClick = { delegate.confirmFocusedAt(state.categories.size) }
                    )
                }
                item(key = "import-splits") {
                    OptionItem(
                        icon = Icons.Default.CloudDownload,
                        label = stringResource(R.string.gamedetail_speedrun_import_row),
                        isFocused = state.focusIndex == state.categories.size + 1,
                        onClick = { delegate.confirmFocusedAt(state.categories.size + 1) }
                    )
                }
            }
        }
    }

    when (val import = state.import) {
        is SpeedrunImport.Loading -> NestedModal(
            title = stringResource(R.string.gamedetail_speedrun_import_loading_title)
        ) {
            Text(
                text = stringResource(R.string.gamedetail_speedrun_import_loading_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        is SpeedrunImport.Importing -> NestedModal(
            title = stringResource(R.string.gamedetail_speedrun_import_importing_title)
        ) {
            Text(
                text = stringResource(R.string.gamedetail_speedrun_import_importing_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        is SpeedrunImport.Failed -> NestedModal(
            title = stringResource(R.string.gamedetail_speedrun_import_failed_title),
            onDismiss = { delegate.dismiss() },
            footerHints = listOf(
                InputButton.B to
                    stringResource(R.string.gamedetail_speedrun_import_failed_footer_back)
            )
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
                InputButton.DPAD_HORIZONTAL to
                    stringResource(R.string.gamedetail_speedrun_preview_footer_other_runner),
                InputButton.A to
                    stringResource(R.string.gamedetail_speedrun_preview_footer_import),
                InputButton.B to stringResource(R.string.gamedetail_speedrun_preview_footer_back)
            )
        ) {
            Text(
                text = stringResource(
                    R.string.gamedetail_speedrun_preview_attribution,
                    import.template.runnerUsername,
                    import.runnerIndex + 1
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                itemsIndexed(import.template.segments, key = { index, _ -> index }) { index, segment ->
                    Text(
                        text = stringResource(
                            R.string.gamedetail_speedrun_preview_segment_row,
                            index + 1,
                            segment
                        ),
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
                title = stringResource(R.string.gamedetail_speedrun_import_options_title),
                onDismiss = { delegate.dismiss() },
                footerHints = listOf(
                    InputButton.A to
                        stringResource(R.string.gamedetail_speedrun_import_options_footer_import),
                    InputButton.B to
                        stringResource(R.string.gamedetail_speedrun_import_options_footer_cancel)
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
                                com.nendo.argosy.data.speedrun.SeedCategory.Source.THERUN ->
                                    stringResource(R.string.gamedetail_speedrun_source_therun)
                                com.nendo.argosy.data.speedrun.SeedCategory.Source.SPEEDRUN_COM ->
                                    stringResource(R.string.gamedetail_speedrun_source_speedruncom)
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
        is SpeedrunPrompt.Text -> TextEntryModal(
            title = prompt.title,
            label = prompt.title,
            confirmLabel = stringResource(R.string.gamedetail_speedrun_prompt_confirm),
            cancelLabel = stringResource(R.string.gamedetail_speedrun_prompt_cancel),
            text = promptText,
            onTextChange = { promptText = it },
            onDismiss = { delegate.dismissPrompt() },
            onSubmit = { delegate.confirmPrompt(promptText) },
            focus = prompt.focus,
            placeholder = stringResource(R.string.gamedetail_speedrun_prompt_placeholder)
        )
        is SpeedrunPrompt.ConfirmDelete -> NestedModal(
            title = stringResource(
                R.string.gamedetail_speedrun_confirm_delete_title,
                prompt.title
            ),
            onDismiss = { delegate.dismissPrompt() },
            footerHints = listOf(
                InputButton.A to
                    stringResource(R.string.gamedetail_speedrun_confirm_delete_footer_delete),
                InputButton.B to
                    stringResource(R.string.gamedetail_speedrun_confirm_delete_footer_cancel)
            )
        ) {
            Text(
                text = if (prompt.isCategory) {
                    stringResource(
                        R.string.gamedetail_speedrun_confirm_delete_category_message
                    )
                } else {
                    stringResource(
                        R.string.gamedetail_speedrun_confirm_delete_segment_message
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        null -> Unit
    }
}
