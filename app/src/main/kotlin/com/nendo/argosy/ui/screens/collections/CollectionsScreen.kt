package com.nendo.argosy.ui.screens.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.nendo.argosy.ui.util.clickableNoFocus
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
import androidx.compose.material.icons.filled.PushPin
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.domain.usecase.collection.CollectionWithCount
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.collection.CollectionCell
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.components.withNavBarClearance
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.screens.collections.dialogs.CollectionOptionsModal
import com.nendo.argosy.ui.screens.collections.dialogs.CreateCollectionDialog
import com.nendo.argosy.ui.screens.collections.dialogs.DeleteCollectionDialog
import com.nendo.argosy.ui.screens.collections.dialogs.EditCollectionDialog
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

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
                viewModel.refreshLocal()
                viewModel.republishCompanionDetail()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_COLLECTIONS)
        viewModel.republishCompanionDetail()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.clearCompanionDetail()
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.focusedSection, uiState.focusedIndex) {
        val targetIndex = when (uiState.focusedSection) {
            CollectionSection.MY_COLLECTIONS -> uiState.focusedIndex + 1
            CollectionSection.BROWSE_BY -> uiState.collections.size + 3 + uiState.focusedIndex
        }
        listState.animateScrollToItemCentered(targetIndex)
    }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = { viewModel.refreshCollections(force = true) },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = Dimens.spacingLg,
                end = Dimens.spacingLg,
                top = Dimens.spacingLg,
                bottom = withNavBarClearance(Dimens.footerHeight + Dimens.spacingLg)
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            item {
                Text(
                    text = stringResource(R.string.collections_section_mine),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = Dimens.spacingSm)
                )
            }

            val hasDialogOpen = uiState.showCreateDialog || uiState.showDeleteDialog
            if (uiState.collections.isEmpty()) {
                item {
                    NewCollectionRow(
                        isFocused = !hasDialogOpen && uiState.isNewCollectionItemFocused,
                        onClick = { viewModel.showCreateDialog() }
                    )
                }
            } else {
                itemsIndexed(uiState.collections, key = { _, c -> c.id }) { index, collection ->
                    CollectionCell(
                        name = collection.name,
                        gameCountLabel = pluralStringResource(
                            R.plurals.collections_game_count,
                            collection.summary.gameCount,
                            collection.summary.gameCount
                        ),
                        summary = collection.summary,
                        coverPaths = collection.coverPaths,
                        placeholderIcon = Icons.Default.Folder,
                        isFocused = !hasDialogOpen && uiState.focusedSection == CollectionSection.MY_COLLECTIONS && uiState.focusedIndex == index,
                        pinnedDescription = stringResource(R.string.collections_pinned_description)
                            .takeIf { collection.id in uiState.pinnedCollectionIds },
                        onClick = { onCollectionClick(collection.id) },
                        onLongClick = { viewModel.handleCollectionLongPress(index) }
                    )
                }
                item(key = "new_collection") {
                    NewCollectionRow(
                        isFocused = !hasDialogOpen && uiState.isNewCollectionItemFocused,
                        onClick = { viewModel.showCreateDialog() }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(Dimens.spacingLg))
                Text(
                    text = stringResource(R.string.collections_section_browse_by),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = Dimens.spacingSm)
                )
            }

            item {
                BrowseByRow(
                    icon = Icons.Default.Style,
                    label = stringResource(VirtualBrowseKind.GENRES.labelRes),
                    count = uiState.genres.size,
                    isFocused = !hasDialogOpen && uiState.focusedSection == CollectionSection.BROWSE_BY && uiState.focusedIndex == 0,
                    onClick = { onVirtualBrowseClick(VirtualBrowseKind.GENRES.route) }
                )
            }

            item {
                BrowseByRow(
                    icon = Icons.Default.SportsEsports,
                    label = stringResource(VirtualBrowseKind.GAME_MODES.labelRes),
                    count = uiState.gameModes.size,
                    isFocused = !hasDialogOpen && uiState.focusedSection == CollectionSection.BROWSE_BY && uiState.focusedIndex == 1,
                    onClick = { onVirtualBrowseClick(VirtualBrowseKind.GAME_MODES.route) }
                )
            }

            if (uiState.series.isNotEmpty()) {
                item {
                    BrowseByRow(
                        icon = Icons.Default.Style,
                        label = stringResource(VirtualBrowseKind.SERIES.labelRes),
                        count = uiState.series.size,
                        isFocused = !hasDialogOpen && uiState.focusedSection == CollectionSection.BROWSE_BY && uiState.focusedIndex == 2,
                        onClick = { onVirtualBrowseClick(VirtualBrowseKind.SERIES.route) }
                    )
                }
            }
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            val selectSwapMode = com.nendo.argosy.ui.dualscreen.selectSwapModeState()
            val selectSwapsRoles = selectSwapMode == com.nendo.argosy.data.preferences.SelectSwapMode.TAP
            val hasOptions = uiState.focusedSection == CollectionSection.MY_COLLECTIONS && uiState.focusedCollection != null
            val selectHintSwaps = selectSwapsRoles ||
                (selectSwapMode == com.nendo.argosy.data.preferences.SelectSwapMode.HOLD && !hasOptions)
            val baseHints = listOf(
                InputButton.DPAD to stringResource(R.string.collections_screen_hint_navigate),
                InputButton.A to stringResource(
                    if (selectSwapsRoles && hasOptions) {
                        R.string.collections_screen_hint_select_hold_options
                    } else {
                        R.string.collections_screen_hint_select
                    }
                ),
                InputButton.B to stringResource(R.string.collections_screen_hint_back),
                InputButton.X to stringResource(
                    if (uiState.isRefreshing) R.string.collections_screen_hint_refreshing else R.string.collections_screen_hint_refresh
                )
            )
            val contextHints = if (hasOptions) {
                listOf(
                    InputButton.Y to stringResource(
                        if (uiState.isFocusedCollectionPinned) R.string.collections_screen_hint_unpin else R.string.collections_screen_hint_pin
                    )
                )
            } else {
                emptyList()
            }
            val selectHints = when {
                selectSwapsRoles -> listOf(InputButton.SELECT to stringResource(R.string.collections_screen_hint_swap_screens))
                selectHintSwaps -> listOf(InputButton.SELECT to stringResource(R.string.collections_screen_hint_hold_swap_screens))
                selectSwapMode != null && hasOptions -> listOf(
                    InputButton.SELECT to stringResource(R.string.collections_screen_hint_options_hold_swap_screens)
                )
                hasOptions -> listOf(InputButton.SELECT to stringResource(R.string.collections_screen_hint_options))
                else -> emptyList()
            }
            FooterHints(
                hints = baseHints + contextHints + selectHints,
                onHintClick = { button ->
                    when (button) {
                        InputButton.A -> { inputHandler.onConfirm() }
                        InputButton.B -> { inputHandler.onBack() }
                        InputButton.X -> { inputHandler.onContextMenu() }
                        InputButton.Y -> { inputHandler.onSecondaryAction() }
                        InputButton.SELECT -> if (selectHintSwaps) {
                            com.nendo.argosy.DualScreenManagerHolder.instance?.swapRoles()
                        } else {
                            inputHandler.onSelect()
                        }
                        else -> Unit
                    }
                }
            )
        }
    }

    if (uiState.showCreateDialog) {
        if (uiState.editingCollection != null) {
            EditCollectionDialog(
                currentName = uiState.editingCollection!!.name,
                onDismiss = { viewModel.hideCreateDialog() },
                onSave = { name -> viewModel.updateCollection(uiState.editingCollection!!.id, name) }
            )
        } else {
            CreateCollectionDialog(
                onDismiss = { viewModel.hideCreateDialog() },
                onCreate = { name -> viewModel.createCollection(name) }
            )
        }
    }

    if (uiState.showDeleteDialog && uiState.editingCollection != null) {
        DeleteCollectionDialog(
            collectionName = uiState.editingCollection!!.name,
            onDismiss = { viewModel.hideDeleteDialog() },
            onConfirm = { viewModel.deleteCollection(uiState.editingCollection!!.id) }
        )
    }

    if (uiState.showOptionsModal && uiState.focusedCollection != null) {
        CollectionOptionsModal(
            collectionName = uiState.focusedCollection!!.name,
            focusIndex = uiState.optionsFocusedIndex,
            onOptionSelect = { viewModel.selectOption() },
            onDismiss = { viewModel.hideOptionsModal() }
        )
    }
}

@Composable
private fun NewCollectionRow(
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(Dimens.radiusControl)
    val borderModifier = if (isFocused) {
        Modifier.border(Dimens.borderMedium, MaterialTheme.colorScheme.primary, shape)
    } else Modifier

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(borderModifier)
            .clickableNoFocus(onClick = onClick),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) {
                LocalArgosyTheme.current.focusAccent.copy(alpha = 0.15f)
                    .compositeOver(MaterialTheme.colorScheme.surface)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.spacingMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(Dimens.iconXl + Dimens.spacingMd)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Dimens.iconLg)
                )
            }

            Spacer(modifier = Modifier.width(Dimens.spacingMd))

            Text(
                text = stringResource(R.string.collections_new),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
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
    val shape = RoundedCornerShape(Dimens.radiusControl)
    val borderModifier = if (isFocused) {
        Modifier.border(Dimens.borderMedium, MaterialTheme.colorScheme.primary, shape)
    } else Modifier

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(borderModifier)
            .clickableNoFocus(onClick = onClick),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) {
                LocalArgosyTheme.current.focusAccent.copy(alpha = 0.15f)
                    .compositeOver(MaterialTheme.colorScheme.surface)
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
                modifier = Modifier.size(Dimens.iconLg)
            )

            Spacer(modifier = Modifier.width(Dimens.spacingMd))

            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = pluralStringResource(R.plurals.collections_category_count, count, count),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
