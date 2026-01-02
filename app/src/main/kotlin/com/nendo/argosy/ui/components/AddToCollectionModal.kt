package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.Dimens

data class CollectionItem(
    val id: Long,
    val name: String,
    val isInCollection: Boolean
)

@Composable
fun AddToCollectionModal(
    collections: List<CollectionItem>,
    focusIndex: Int,
    showCreateOption: Boolean = true,
    onToggleCollection: (Long) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit
) {
    val createOptionOffset = if (showCreateOption) 1 else 0

    Modal(
        title = "ADD TO COLLECTION",
        onDismiss = onDismiss,
        footerHints = listOf(
            InputButton.SOUTH to "Toggle",
            InputButton.EAST to "Close"
        )
    ) {
        LazyColumn {
            if (showCreateOption) {
                item(key = "create") {
                    CreateCollectionRow(
                        isFocused = focusIndex == 0,
                        onClick = onCreate
                    )
                }
            }

            itemsIndexed(collections, key = { _, c -> c.id }) { index, collection ->
                CollectionCheckRow(
                    collection = collection,
                    isFocused = focusIndex == index + createOptionOffset,
                    onClick = { onToggleCollection(collection.id) }
                )
            }

            if (collections.isEmpty() && !showCreateOption) {
                item {
                    Text(
                        text = "No collections yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Dimens.spacingMd)
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateCollectionRow(
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)
    val borderModifier = if (isFocused) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
    } else Modifier

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(borderModifier)
            .background(
                if (isFocused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                shape
            )
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .padding(Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(Dimens.spacingMd))

        Text(
            text = "Create New Collection",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun CollectionCheckRow(
    collection: CollectionItem,
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)
    val borderModifier = if (isFocused) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
    } else Modifier

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .then(borderModifier)
            .background(
                if (isFocused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                shape
            )
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .padding(Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(Dimens.spacingMd))

        Text(
            text = collection.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        if (collection.isInCollection) {
            Icon(
                Icons.Default.Check,
                contentDescription = "In collection",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
