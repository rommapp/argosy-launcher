package com.nendo.argosy.ui.screens.collections.dialogs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.runtime.Composable
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem

enum class CollectionOption {
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
    showRemoveGame: Boolean = false,
    gameTitle: String? = null
) {
    Modal(title = collectionName, onDismiss = onDismiss) {
        OptionItem(
            icon = Icons.Default.Edit,
            label = "Rename Collection",
            isFocused = focusIndex == 0,
            onClick = { onOptionSelect(CollectionOption.RENAME) }
        )
        OptionItem(
            icon = Icons.Default.DeleteOutline,
            label = "Delete Collection",
            isFocused = focusIndex == 1,
            isDangerous = true,
            onClick = { onOptionSelect(CollectionOption.DELETE) }
        )
        if (showRemoveGame) {
            OptionItem(
                icon = Icons.Default.RemoveCircleOutline,
                label = "Remove \"${gameTitle ?: "Game"}\"",
                isFocused = focusIndex == 2,
                isDangerous = true,
                onClick = { onOptionSelect(CollectionOption.REMOVE_GAME) }
            )
        }
    }
}
