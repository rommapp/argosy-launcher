package com.nendo.argosy.ui.screens.collections.dialogs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem

enum class CollectionOption {
    DOWNLOAD_ALL,
    RENAME,
    DELETE,
    REMOVE_GAME
}

@Composable
fun CollectionOptionsModal(
    collectionName: String,
    focusIndex: Int,
    onOptionSelect: (CollectionOption) -> Unit,
    onDismiss: () -> Unit,
    showDownloadAll: Boolean = false,
    downloadableCount: Int = 0,
    showRemoveGame: Boolean = false,
    gameTitle: String? = null
) {
    var currentIndex = 0
    Modal(title = collectionName, onDismiss = onDismiss) {
        if (showDownloadAll && downloadableCount > 0) {
            OptionItem(
                icon = Icons.Default.Download,
                label = stringResource(R.string.collections_options_download_all, downloadableCount),
                isFocused = focusIndex == currentIndex,
                onClick = { onOptionSelect(CollectionOption.DOWNLOAD_ALL) }
            )
            currentIndex++
        }
        OptionItem(
            icon = Icons.Default.Edit,
            label = stringResource(R.string.collections_options_rename),
            isFocused = focusIndex == currentIndex,
            onClick = { onOptionSelect(CollectionOption.RENAME) }
        )
        currentIndex++
        OptionItem(
            icon = Icons.Default.DeleteOutline,
            label = stringResource(R.string.collections_options_delete),
            isFocused = focusIndex == currentIndex,
            isDangerous = true,
            onClick = { onOptionSelect(CollectionOption.DELETE) }
        )
        currentIndex++
        if (showRemoveGame) {
            OptionItem(
                icon = Icons.Default.RemoveCircleOutline,
                label = stringResource(
                    R.string.collections_options_remove_game,
                    gameTitle ?: stringResource(R.string.collections_options_remove_game_fallback)
                ),
                isFocused = focusIndex == currentIndex,
                isDangerous = true,
                onClick = { onOptionSelect(CollectionOption.REMOVE_GAME) }
            )
        }
    }
}
