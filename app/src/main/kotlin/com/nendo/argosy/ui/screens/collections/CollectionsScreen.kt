package com.nendo.argosy.ui.screens.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.nendo.argosy.domain.usecase.collection.CollectionWithCount
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.screens.collections.dialogs.CreateCollectionDialog
import com.nendo.argosy.ui.screens.collections.dialogs.DeleteCollectionDialog
import com.nendo.argosy.ui.screens.collections.dialogs.EditCollectionDialog
import com.nendo.argosy.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(
    onBack: () -> Unit,
    onCollectionClick: (Long) -> Unit,
    onVirtualBrowseClick: (String) -> Unit,
    viewModel: CollectionsViewModel = hiltViewModel()
) {
    val inputDispatcher = LocalInputDispatcher.current
    val inputHandler = remember(onBack, onCollectionClick, onVirtualBrowseClick) {
        viewModel.createInputHandler(
            onBack = onBack,
            onCollectionClick = onCollectionClick,
            onVirtualBrowseClick = onVirtualBrowseClick
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_COLLECTIONS)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_COLLECTIONS)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.focusedSection, uiState.focusedIndex) {
        val targetIndex = when (uiState.focusedSection) {
            CollectionSection.MY_COLLECTIONS -> uiState.focusedIndex + 1
            CollectionSection.BROWSE_BY -> uiState.collections.size + 2 + uiState.focusedIndex
        }
        if (targetIndex >= 0) {
            listState.animateScrollToItem(targetIndex)
        }
    }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = { viewModel.refreshCollections() },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = Dimens.spacingLg,
                end = Dimens.spacingLg,
                top = Dimens.spacingLg,
                bottom = 80.dp
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            item {
                Text(
                    text = "MY COLLECTIONS",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = Dimens.spacingSm)
                )
            }

            if (uiState.collections.isEmpty()) {
                item {
                    EmptyCollectionsCard(
                        onClick = { viewModel.showCreateDialog() }
                    )
                }
            } else {
                itemsIndexed(uiState.collections, key = { _, c -> c.id }) { index, collection ->
                    CollectionRow(
                        collection = collection,
                        isFocused = uiState.focusedSection == CollectionSection.MY_COLLECTIONS && uiState.focusedIndex == index,
                        onClick = { onCollectionClick(collection.id) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(Dimens.spacingLg))
                Text(
                    text = "BROWSE BY",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = Dimens.spacingSm)
                )
            }

            item {
                BrowseByRow(
                    icon = Icons.Default.Style,
                    label = "Genres",
                    count = uiState.genres.size,
                    isFocused = uiState.focusedSection == CollectionSection.BROWSE_BY && uiState.focusedIndex == 0,
                    onClick = { onVirtualBrowseClick("genres") }
                )
            }

            item {
                BrowseByRow(
                    icon = Icons.Default.SportsEsports,
                    label = "Game Modes",
                    count = uiState.gameModes.size,
                    isFocused = uiState.focusedSection == CollectionSection.BROWSE_BY && uiState.focusedIndex == 1,
                    onClick = { onVirtualBrowseClick("modes") }
                )
            }
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            val baseHints = listOf(
                InputButton.DPAD to "Navigate",
                InputButton.SOUTH to "Select",
                InputButton.NORTH to "New",
                InputButton.EAST to "Back"
            )
            val contextHints = if (uiState.focusedSection == CollectionSection.MY_COLLECTIONS && uiState.focusedCollection != null) {
                listOf(
                    InputButton.WEST to "Edit",
                    InputButton.SELECT to "Delete"
                )
            } else {
                emptyList()
            }
            val refreshHint = listOf(InputButton.RB to "Refresh")
            FooterBar(hints = baseHints + contextHints + refreshHint)
        }
    }

    if (uiState.showCreateDialog) {
        CreateCollectionDialog(
            onDismiss = { viewModel.hideCreateDialog() },
            onCreate = { name -> viewModel.createCollection(name) }
        )
    }

    if (uiState.showEditDialog && uiState.editingCollection != null) {
        EditCollectionDialog(
            currentName = uiState.editingCollection!!.name,
            onDismiss = { viewModel.hideEditDialog() },
            onSave = { name -> viewModel.updateCollection(uiState.editingCollection!!.id, name) }
        )
    }

    if (uiState.showDeleteDialog && uiState.editingCollection != null) {
        DeleteCollectionDialog(
            collectionName = uiState.editingCollection!!.name,
            onDismiss = { viewModel.hideDeleteDialog() },
            onConfirm = { viewModel.deleteCollection(uiState.editingCollection!!.id) }
        )
    }
}

@Composable
private fun CollectionRow(
    collection: CollectionWithCount,
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    val borderModifier = if (isFocused) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
    } else Modifier

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(borderModifier)
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.spacingMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoverMosaic(
                coverPaths = collection.coverPaths,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.width(Dimens.spacingMd))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = collection.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${collection.gameCount} games",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CoverMosaic(
    coverPaths: List<String>,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        when {
            coverPaths.isEmpty() -> {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(32.dp)
                )
            }
            coverPaths.size == 1 -> {
                AsyncImage(
                    model = coverPaths[0],
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                val displayed = coverPaths.take(4)
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f)) {
                        displayed.getOrNull(0)?.let { path ->
                            AsyncImage(
                                model = path,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                            )
                        }
                        displayed.getOrNull(1)?.let { path ->
                            AsyncImage(
                                model = path,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                            )
                        }
                    }
                    if (displayed.size > 2) {
                        Row(modifier = Modifier.weight(1f)) {
                            displayed.getOrNull(2)?.let { path ->
                                AsyncImage(
                                    model = path,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                )
                            }
                            displayed.getOrNull(3)?.let { path ->
                                AsyncImage(
                                    model = path,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                )
                            } ?: Box(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCollectionsCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.spacingLg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(Dimens.spacingSm))
            Text(
                text = "Create your first collection",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BrowseByRow(
    icon: ImageVector,
    label: String,
    count: Int,
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    val borderModifier = if (isFocused) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
    } else Modifier

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(borderModifier)
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.spacingMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(Dimens.spacingMd))

            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = "$count categories",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
