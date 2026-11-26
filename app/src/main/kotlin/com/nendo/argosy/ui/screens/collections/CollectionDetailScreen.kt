package com.nendo.argosy.ui.screens.collections

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.LetterJumpOverlay
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.screens.collections.dialogs.CollectionOptionsModal
import com.nendo.argosy.ui.screens.collections.dialogs.DeleteCollectionDialog
import com.nendo.argosy.ui.screens.collections.dialogs.EditCollectionDialog
import com.nendo.argosy.ui.screens.collections.dialogs.RemoveGameDialog
import com.nendo.argosy.ui.theme.Dimens

private val COLLECTION_LABELS = GameSetLabels(
    loading = R.string.collections_detail_loading,
    filteredEmptyTitle = R.string.collections_detail_empty_filtered_title,
    filteredEmptyBody = R.string.collections_detail_empty_filtered_body,
    filteredEmptyHint = R.string.collections_detail_empty_filtered_hint
)

@Composable
fun CollectionDetailScreen(
    onBack: () -> Unit,
    onGameClick: (Long) -> Unit,
    viewModel: CollectionDetailViewModel = hiltViewModel()
) {
    val inputDispatcher = LocalInputDispatcher.current
    val inputHandler = remember(onBack, onGameClick) {
        viewModel.createInputHandler(
            onBack = onBack,
            onGameClick = onGameClick
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_COLLECTION_DETAIL)
                viewModel.republishCompanionDetail()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_COLLECTION_DETAIL)
        viewModel.republishCompanionDetail()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.clearCompanionDetail()
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val downloadIndicators by viewModel.downloadIndicators.collectAsState()
    val collectionName = uiState.collection?.name ?: ""
    val hasDialogOpen = uiState.showEditDialog || uiState.showDeleteDialog || uiState.showRemoveGameDialog

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollectionDetailHeader(
                collectionName = collectionName,
                gameCount = uiState.games.size,
                onBack = onBack,
                onSearchOpen = { viewModel.browser.openSearchPanel() }
            )
            GameSetFilterChips(
                filter = uiState.filter,
                label = stringResource(R.string.collections_detail_active_filters_label),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingXs)
            )
            GameSetGames(
                listing = uiState.listing,
                layout = uiState.layout,
                filter = uiState.filter,
                focusedIndex = uiState.focusedIndex,
                isFocusShown = !hasDialogOpen,
                scrollTrigger = uiState.scrollTrigger,
                labels = COLLECTION_LABELS,
                downloadIndicators = downloadIndicators,
                onScreenWidth = viewModel.browser::updateScreenWidth,
                onItemClick = { viewModel.handleItemTap(it, onGameClick) },
                onItemLongClick = { viewModel.handleGameLongPress(it) },
                onSectionClick = { viewModel.jumpToSection(it) },
                emptyContent = { EmptyCollectionDetail() }
            )
        }

        val selectSwapMode = com.nendo.argosy.ui.dualscreen.selectSwapModeState()
        val selectSwapsRoles = selectSwapMode == com.nendo.argosy.data.preferences.SelectSwapMode.TAP
        val selectHintSwaps = selectSwapsRoles ||
            (selectSwapMode == com.nendo.argosy.data.preferences.SelectSwapMode.HOLD && uiState.collection == null)
        val hints = buildList {
            add(InputButton.DPAD to stringResource(R.string.collections_detail_hint_navigate))
            add(
                InputButton.A to stringResource(
                    if (selectSwapsRoles && uiState.collection != null) {
                        R.string.collections_detail_hint_open_hold_options
                    } else {
                        R.string.collections_detail_hint_open
                    }
                )
            )
            add(InputButton.B to stringResource(R.string.collections_detail_hint_back))
            add(InputButton.X to stringResource(R.string.collections_detail_hint_filter))
            if (uiState.collection != null) {
                add(
                    InputButton.Y to stringResource(
                        if (uiState.isPinned) R.string.collections_detail_hint_unpin else R.string.collections_detail_hint_pin
                    )
                )
            }
            if (uiState.sectionLabels.size > 1) {
                add(InputButton.LT_RT to stringResource(R.string.collections_detail_hint_jump))
            }
            when {
                selectSwapsRoles ->
                    add(InputButton.SELECT to stringResource(R.string.collections_detail_hint_swap_screens))
                selectHintSwaps ->
                    add(InputButton.SELECT to stringResource(R.string.collections_detail_hint_hold_swap_screens))
                selectSwapMode != null && uiState.collection != null -> add(
                    InputButton.SELECT to stringResource(R.string.collections_detail_hint_options_hold_swap_screens)
                )
                uiState.collection != null ->
                    add(InputButton.SELECT to stringResource(R.string.collections_detail_hint_options))
            }
            add(
                InputButton.START to stringResource(
                    if (uiState.isRefreshing) R.string.collections_detail_hint_refreshing else R.string.collections_detail_hint_refresh
                )
            )
        }
        FooterHints(
            hints = hints,
            onHintClick = { button ->
                when (button) {
                    InputButton.A -> { inputHandler.onConfirm() }
                    InputButton.B -> { inputHandler.onBack() }
                    InputButton.X -> { inputHandler.onContextMenu() }
                    InputButton.Y -> { inputHandler.onSecondaryAction() }
                    InputButton.LT_RT -> { inputHandler.onNextTrigger() }
                    InputButton.SELECT -> if (selectHintSwaps) {
                        com.nendo.argosy.DualScreenManagerHolder.instance?.swapRoles()
                    } else {
                        inputHandler.onSelect()
                    }
                    InputButton.START -> { inputHandler.onMenu() }
                    else -> Unit
                }
            }
        )

        LetterJumpOverlay(letter = uiState.overlayLetter, visible = uiState.showLetterOverlay)

        GameSetFilterHost(
            filter = uiState.filter,
            keyboardLabel = stringResource(R.string.collections_detail_search_placeholder, collectionName),
            keyboardSubmitLabel = stringResource(R.string.collections_detail_keyboard_submit),
            onDismiss = { viewModel.browser.toggleFilterMenu() },
            onCategorySelect = { viewModel.browser.setFilterCategory(it) },
            onOptionSelect = { viewModel.browser.selectFilterOption(it) },
            onSearchQueryChange = { viewModel.browser.updateSearchQuery(it) },
            onSearchFieldTap = { viewModel.browser.openSearchKeyboard() },
            onCloseKeyboard = { viewModel.browser.closeSearchKeyboard() }
        )
    }

    if (uiState.showEditDialog && uiState.collection != null) {
        EditCollectionDialog(
            currentName = uiState.collection!!.name,
            onDismiss = { viewModel.hideEditDialog() },
            onSave = { name -> viewModel.updateCollectionName(name) }
        )
    }

    if (uiState.showDeleteDialog && uiState.collection != null) {
        DeleteCollectionDialog(
            collectionName = uiState.collection!!.name,
            onDismiss = { viewModel.hideDeleteDialog() },
            onConfirm = { viewModel.deleteCollection(onDeleted = onBack) }
        )
    }

    if (uiState.showOptionsModal && uiState.collection != null) {
        CollectionOptionsModal(
            collectionName = uiState.collection!!.name,
            focusIndex = uiState.optionsModalFocusIndex,
            onOptionSelect = { option -> viewModel.selectOption(option) },
            onDismiss = { viewModel.hideOptionsModal() },
            rows = uiState.optionRows,
            downloadableCount = uiState.downloadableGamesCount,
            gameTitle = uiState.focusedGame?.title,
            onMoveToDisplay = { target -> viewModel.moveRunningGame(target) }
        )
    }

    com.nendo.argosy.ui.screens.running.RunningGameCloseHost(viewModel.runningGameMenu)

    if (uiState.showRemoveGameDialog && uiState.gameToRemove != null) {
        RemoveGameDialog(
            gameTitle = uiState.gameToRemove!!.title,
            collectionName = uiState.collection?.name ?: "",
            onDismiss = { viewModel.hideRemoveGameDialog() },
            onConfirm = { viewModel.confirmRemoveGame() }
        )
    }
}

@Composable
private fun CollectionDetailHeader(
    collectionName: String,
    gameCount: Int,
    onBack: () -> Unit,
    onSearchOpen: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.collections_detail_back_description),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.width(Dimens.spacingSm))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = collectionName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = pluralStringResource(R.plurals.collections_detail_header_game_count, gameCount, gameCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        CollectionSearchButton(
            isFocused = false,
            contentDescription = stringResource(R.string.collections_detail_search_description),
            onClick = onSearchOpen
        )
    }
}

@Composable
private fun EmptyCollectionDetail() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(Dimens.iconXl + Dimens.spacingMd)
            )
            Spacer(modifier = Modifier.height(Dimens.spacingMd))
            Text(
                text = stringResource(R.string.collections_detail_empty_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            Text(
                text = stringResource(R.string.collections_detail_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
