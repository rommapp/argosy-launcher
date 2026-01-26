package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.nendo.argosy.ui.util.clickableNoFocus
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
    val filteredCollections = collections.filter { it.name.isNotBlank() }

    Modal(
        title = "ADD TO COLLECTION",
        onDismiss = onDismiss
    ) {
        LazyColumn {
            itemsIndexed(filteredCollections, key = { _, c -> c.id }) { index, collection ->
                CollectionCheckRow(
                    collection = collection,
                    isFocused = focusIndex == index,
                    onClick = { onToggleCollection(collection.id) }
                )
            }

            if (filteredCollections.isEmpty() && !showCreateOption) {
                item {
                    Text(
                        text = "No collections yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Dimens.spacingMd)
                    )
                }
            }

            if (showCreateOption) {
                item(key = "create") {
                    CreateCollectionRow(
                        isFocused = focusIndex == filteredCollections.size,
                        onClick = onCreate
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
    val shape = RoundedCornerShape(Dimens.radiusMd)
    val borderModifier = if (isFocused) {
        Modifier.border(Dimens.borderMedium, MaterialTheme.colorScheme.primary, shape)
    } else Modifier

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(borderModifier)
            .background(
                if (isFocused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                shape
            )
            .clickableNoFocus(onClick = onClick)
            .padding(Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(Dimens.iconMd)
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
    val shape = RoundedCornerShape(Dimens.radiusMd)
    val borderModifier = if (isFocused) {
        Modifier.border(Dimens.borderMedium, MaterialTheme.colorScheme.primary, shape)
    } else Modifier

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.borderMedium)
            .then(borderModifier)
            .background(
                if (isFocused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                shape
            )
            .clickableNoFocus(onClick = onClick)
            .padding(Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Dimens.iconMd)
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
                modifier = Modifier.size(Dimens.iconMd)
            )
        }
    }
}
