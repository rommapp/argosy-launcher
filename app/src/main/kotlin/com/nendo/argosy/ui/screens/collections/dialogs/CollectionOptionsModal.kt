package com.nendo.argosy.ui.screens.collections.dialogs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem
import com.nendo.argosy.ui.screens.running.RunningMoveTarget

enum class CollectionOption {
    DOWNLOAD_ALL,
    RENAME,
    DELETE,
    REMOVE_GAME,
    CLOSE_GAME
}

sealed interface CollectionOptionRow {
    data class Action(val option: CollectionOption) : CollectionOptionRow
    data class MoveTo(val target: RunningMoveTarget) : CollectionOptionRow
}

/**
 * The options modal's rows in order: one Move to Display row per [moveTargets] entry first, then
 * the collection's own options, then the focused game's, with Close last when [showCloseGame].
 */
fun collectionOptionRows(
    showDownloadAll: Boolean = false,
    downloadableCount: Int = 0,
    showRemoveGame: Boolean = false,
    moveTargets: List<RunningMoveTarget> = emptyList(),
    showCloseGame: Boolean = false
): List<CollectionOptionRow> = buildList {
    moveTargets.forEach { add(CollectionOptionRow.MoveTo(it)) }
    if (showDownloadAll && downloadableCount > 0) add(CollectionOptionRow.Action(CollectionOption.DOWNLOAD_ALL))
    add(CollectionOptionRow.Action(CollectionOption.RENAME))
    add(CollectionOptionRow.Action(CollectionOption.DELETE))
    if (showRemoveGame) add(CollectionOptionRow.Action(CollectionOption.REMOVE_GAME))
    if (showCloseGame) add(CollectionOptionRow.Action(CollectionOption.CLOSE_GAME))
}

@Composable
fun CollectionOptionsModal(
    collectionName: String,
    focusIndex: Int,
    onOptionSelect: (CollectionOption) -> Unit,
    onDismiss: () -> Unit,
    rows: List<CollectionOptionRow> = collectionOptionRows(),
    downloadableCount: Int = 0,
    gameTitle: String? = null,
    onMoveToDisplay: (RunningMoveTarget) -> Unit = {}
) {
    Modal(title = collectionName, onDismiss = onDismiss) {
        rows.forEachIndexed { index, row ->
            val isFocused = focusIndex == index
            when (row) {
                is CollectionOptionRow.MoveTo -> OptionItem(
                    icon = Icons.Default.Tv,
                    label = stringResource(R.string.collections_options_move_to_display, row.target.screenNumber),
                    isFocused = isFocused,
                    onClick = { onMoveToDisplay(row.target) }
                )
                is CollectionOptionRow.Action -> when (row.option) {
                    CollectionOption.DOWNLOAD_ALL -> OptionItem(
                        icon = Icons.Default.Download,
                        label = stringResource(R.string.collections_options_download_all, downloadableCount),
                        isFocused = isFocused,
                        onClick = { onOptionSelect(CollectionOption.DOWNLOAD_ALL) }
                    )
                    CollectionOption.RENAME -> OptionItem(
                        icon = Icons.Default.Edit,
                        label = stringResource(R.string.collections_options_rename),
                        isFocused = isFocused,
                        onClick = { onOptionSelect(CollectionOption.RENAME) }
                    )
                    CollectionOption.DELETE -> OptionItem(
                        icon = Icons.Default.DeleteOutline,
                        label = stringResource(R.string.collections_options_delete),
                        isFocused = isFocused,
                        isDangerous = true,
                        onClick = { onOptionSelect(CollectionOption.DELETE) }
                    )
                    CollectionOption.REMOVE_GAME -> OptionItem(
                        icon = Icons.Default.RemoveCircleOutline,
                        label = stringResource(
                            R.string.collections_options_remove_game,
                            gameTitle ?: stringResource(R.string.collections_options_remove_game_fallback)
                        ),
                        isFocused = isFocused,
                        isDangerous = true,
                        onClick = { onOptionSelect(CollectionOption.REMOVE_GAME) }
                    )
                    CollectionOption.CLOSE_GAME -> OptionItem(
                        icon = Icons.Default.Close,
                        label = stringResource(R.string.collections_options_close_game),
                        isFocused = isFocused,
                        isDangerous = true,
                        onClick = { onOptionSelect(CollectionOption.CLOSE_GAME) }
                    )
                }
            }
        }
    }
}
