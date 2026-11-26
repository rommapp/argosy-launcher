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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.LetterJumpOverlay
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.primitives.ArgosyConfirmModalHost
import com.nendo.argosy.ui.primitives.ArgosyProgressBar
import com.nendo.argosy.ui.screens.collections.dialogs.CollectionOptionsModal
import com.nendo.argosy.ui.screens.running.RunningGameCloseHost
import com.nendo.argosy.ui.theme.Dimens

private val CATEGORY_LABELS = GameSetLabels(
    loading = R.string.collections_category_loading,
    filteredEmptyTitle = R.string.collections_category_empty_filtered_title,
    filteredEmptyBody = R.string.collections_category_empty_filtered_body,
    filteredEmptyHint = R.string.collections_category_empty_filtered_hint
)

@Composable
fun VirtualCategoryScreen(
    onBack: () -> Unit,
    onGameClick: (Long) -> Unit,
    viewModel: VirtualCategoryViewModel = hiltViewModel()
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
                inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_VIRTUAL_BROWSER)
                viewModel.republishCompanionDetail()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_VIRTUAL_BROWSER)
        viewModel.republishCompanionDetail()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.clearCompanionDetail()
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val downloadAllConfirmVisible by viewModel.downloadAllConfirmVisible.collectAsState()
    val downloadIndicators by viewModel.downloadIndicators.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            VirtualCategoryHeader(
                categoryName = uiState.categoryName,
                gameCount = uiState.games.size,
                onBack = onBack,
                onSearchOpen = { viewModel.browser.openSearchPanel() }
            )
            GameSetFilterChips(
                filter = uiState.filter,
                label = stringResource(R.string.collections_category_active_filters_label),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingXs)
            )
            GameSetGames(
                listing = uiState.listing,
                layout = uiState.layout,
                filter = uiState.filter,
                focusedIndex = uiState.focusedIndex,
                isFocusShown = true,
                scrollTrigger = uiState.scrollTrigger,
                labels = CATEGORY_LABELS,
                downloadIndicators = downloadIndicators,
                onScreenWidth = viewModel.browser::updateScreenWidth,
                onItemClick = { viewModel.handleItemTap(it, onGameClick) },
                onItemLongClick = { viewModel.handleGameLongPress(it) },
                onSectionClick = { viewModel.jumpToSection(it) },
                emptyContent = { EmptyVirtualCategory() }
            )
        }

        val selectSwapMode = com.nendo.argosy.ui.dualscreen.selectSwapModeState()
        val selectSwapsRoles = selectSwapMode == com.nendo.argosy.data.preferences.SelectSwapMode.TAP
        val focusedRunning = uiState.focusedGame?.let {
            com.nendo.argosy.ui.common.LocalRunningGames.current.markFor(it.id)
        }
        val hasOptions = uiState.optionRowsFor(focusedRunning).isNotEmpty()
        val selectHintSwaps = selectSwapsRoles ||
            (selectSwapMode == com.nendo.argosy.data.preferences.SelectSwapMode.HOLD && !hasOptions)
        FooterHints(
            hints = buildList {
                add(InputButton.DPAD to stringResource(R.string.collections_category_hint_navigate))
                add(
                    InputButton.A to stringResource(
                        if (selectSwapsRoles && hasOptions) {
                            R.string.collections_category_hint_open_hold_options
                        } else {
                            R.string.collections_category_hint_open
                        }
                    )
                )
                add(InputButton.B to stringResource(R.string.collections_category_hint_back))
                add(InputButton.X to stringResource(R.string.collections_category_hint_filter))
                add(
                    InputButton.Y to stringResource(
                        if (uiState.isPinned) R.string.collections_category_hint_unpin else R.string.collections_category_hint_pin
                    )
                )
                if (uiState.sectionLabels.size > 1) {
                    add(InputButton.LT_RT to stringResource(R.string.collections_category_hint_jump))
                }
                add(
                    InputButton.START to stringResource(
                        if (uiState.isRefreshing) R.string.collections_category_hint_refreshing else R.string.collections_category_hint_refresh
                    )
                )
                when {
                    selectSwapsRoles ->
                        add(InputButton.SELECT to stringResource(R.string.collections_category_hint_swap_screens))
                    selectHintSwaps ->
                        add(InputButton.SELECT to stringResource(R.string.collections_category_hint_hold_swap_screens))
                    selectSwapMode != null && hasOptions -> add(
                        InputButton.SELECT to stringResource(R.string.collections_category_hint_options_hold_swap_screens)
                    )
                    hasOptions -> add(
                        InputButton.SELECT to stringResource(R.string.collections_category_hint_options)
                    )
                }
            },
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
            keyboardLabel = stringResource(R.string.collections_category_search_placeholder, uiState.categoryName),
            keyboardSubmitLabel = stringResource(R.string.collections_category_keyboard_submit),
            onDismiss = { viewModel.browser.toggleFilterMenu() },
            onCategorySelect = { viewModel.browser.setFilterCategory(it) },
            onOptionSelect = { viewModel.browser.selectFilterOption(it) },
            onSearchQueryChange = { viewModel.browser.updateSearchQuery(it) },
            onSearchFieldTap = { viewModel.browser.openSearchKeyboard() },
            onCloseKeyboard = { viewModel.browser.closeSearchKeyboard() }
        )

        ArgosyConfirmModalHost(
            visible = downloadAllConfirmVisible,
            title = stringResource(R.string.collections_category_downloadall_confirm_title),
            message = pluralStringResource(
                R.plurals.collections_category_downloadall_confirm_message,
                uiState.downloadableGamesCount,
                uiState.downloadableGamesCount
            ),
            confirmLabel = stringResource(R.string.collections_category_downloadall_confirm_action),
            onConfirm = viewModel::confirmDownloadAll,
            onDismiss = viewModel::dismissDownloadAllConfirm
        )

        if (uiState.downloadAllProgress.isActive) {
            DownloadAllModal(
                currentIndex = uiState.downloadAllProgress.currentIndex,
                totalCount = uiState.downloadAllProgress.totalCount
            )
        }
    }

    if (uiState.showOptionsModal) {
        CollectionOptionsModal(
            collectionName = uiState.categoryName,
            focusIndex = uiState.optionsModalFocusIndex,
            onOptionSelect = { option -> viewModel.selectOption(option) },
            onDismiss = { viewModel.hideOptionsModal() },
            rows = uiState.optionRows,
            downloadableCount = uiState.downloadableGamesCount,
            gameTitle = uiState.focusedGame?.title,
            onMoveToDisplay = { target -> viewModel.moveRunningGame(target) }
        )
    }

    RunningGameCloseHost(viewModel.runningGameMenu)
}

@Composable
private fun DownloadAllModal(
    currentIndex: Int,
    totalCount: Int
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.spacingXl),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(Dimens.radiusXl),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = Dimens.elevationLg)
        ) {
            Column(
                modifier = Modifier
                    .padding(Dimens.spacingLg)
                    .width(Dimens.modalWidthLg - 170.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.iconXl),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(Dimens.spacingMd))

                Text(
                    text = stringResource(R.string.collections_category_downloadall_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(Dimens.spacingSm))

                Text(
                    text = pluralStringResource(
                        R.plurals.collections_category_downloadall_progress,
                        totalCount,
                        currentIndex,
                        totalCount
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(Dimens.spacingMd))

                ArgosyProgressBar(progress = if (totalCount > 0) currentIndex.toFloat() / totalCount else 0f)
            }
        }
    }
}

@Composable
private fun VirtualCategoryHeader(
    categoryName: String,
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
                contentDescription = stringResource(R.string.collections_category_back_description),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.width(Dimens.spacingSm))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = categoryName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = pluralStringResource(R.plurals.collections_category_header_game_count, gameCount, gameCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        CollectionSearchButton(
            isFocused = false,
            contentDescription = stringResource(R.string.collections_category_search_description),
            onClick = onSearchOpen
        )
    }
}

@Composable
private fun EmptyVirtualCategory() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Category,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(Dimens.iconXl + Dimens.spacingMd)
            )
            Spacer(modifier = Modifier.height(Dimens.spacingMd))
            Text(
                text = stringResource(R.string.collections_category_empty_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
