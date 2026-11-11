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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.collection.CollectionCell
import com.nendo.argosy.domain.usecase.collection.CategoryWithCount
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.components.AlphabetSidebar
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.keyboard.ControllerKeyboardSession
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import androidx.compose.ui.graphics.compositeOver
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

@Composable
fun VirtualBrowserScreen(
    onBack: () -> Unit,
    onCategoryClick: (String) -> Unit,
    viewModel: VirtualBrowserViewModel = hiltViewModel()
) {
    val inputDispatcher = LocalInputDispatcher.current
    val inputHandler = remember(onBack, onCategoryClick) {
        viewModel.createInputHandler(
            onBack = onBack,
            onCategoryClick = onCategoryClick
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
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.focusedIndex, uiState.isSearchStopFocused) {
        if (uiState.isSearchStopFocused) {
            listState.animateScrollToItem(0)
        } else if (uiState.categories.isNotEmpty() && uiState.focusedIndex in uiState.categories.indices) {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val viewportHeight = listState.layoutInfo.viewportEndOffset
            val avgItemHeight = if (visibleItems.isNotEmpty()) {
                visibleItems.sumOf { it.size } / visibleItems.size
            } else 80
            val targetOffset = (viewportHeight / 2) - (avgItemHeight / 2)
            listState.animateScrollToItem(uiState.focusedIndex, -targetOffset)
        }
    }

    val title = stringResource(uiState.titleRes)
    val searchFieldFocus = remember { FocusRequester() }
    ControllerKeyboardSession(
        open = uiState.isSearchActive && uiState.isSearchKeyboardOpen,
        label = stringResource(R.string.collections_browser_search_placeholder, title),
        text = uiState.searchQuery,
        onTextChange = { viewModel.setSearchQuery(it) },
        onSubmit = { viewModel.closeSearchKeyboard() },
        onClose = { viewModel.closeSearchKeyboard() },
        submitLabel = stringResource(R.string.collections_browser_keyboard_submit),
        onSystemKeyboard = {
            viewModel.closeSearchKeyboard()
            searchFieldFocus.requestFocus()
        }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            VirtualBrowserHeader(
                title = title,
                categoryCount = uiState.categories.size,
                isSearchActive = uiState.isSearchActive,
                searchQuery = uiState.searchQuery,
                searchFieldFocus = searchFieldFocus,
                isSearchStopFocused = uiState.isSearchStopFocused,
                onBack = { if (uiState.isSearchActive) viewModel.closeSearch() else onBack() },
                onSearchOpen = { viewModel.openSearch() },
                onSearchChange = { viewModel.setSearchQuery(it) },
                onSearchFieldFocused = { viewModel.closeSearchKeyboard() },
                onSearchClose = { viewModel.closeSearch() }
            )

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.collections_browser_loading),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                uiState.categories.isEmpty() -> {
                    EmptyVirtualBrowser(type = title)
                }
                else -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = Dimens.spacingLg,
                                end = if (uiState.showSectionSidebar) Dimens.spacingLg + 44.dp else Dimens.spacingLg,
                                top = Dimens.spacingSm,
                                bottom = 80.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                        ) {
                            itemsIndexed(uiState.categories, key = { _, c -> c.name }) { index, category ->
                                CategoryRow(
                                    category = category,
                                    isFocused = !uiState.isSearchStopFocused && uiState.focusedIndex == index,
                                    isPinned = category.name in uiState.pinnedCategories,
                                    onClick = { onCategoryClick(category.name) }
                                )
                            }
                        }

                        if (uiState.showSectionSidebar) {
                            AlphabetSidebar(
                                availableLetters = uiState.sectionLabels,
                                currentLetter = uiState.currentSectionLabel,
                                onLetterClick = { viewModel.jumpToSection(it) },
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight(),
                                topPadding = Dimens.spacingSm,
                                bottomPadding = 80.dp
                            )
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            val hints = if (uiState.isSearchActive) {
                listOf(
                    InputButton.A to stringResource(R.string.collections_browser_hint_search_select),
                    InputButton.B to stringResource(R.string.collections_browser_hint_search_close)
                )
            } else {
                val baseHints = listOf(
                    InputButton.DPAD to stringResource(R.string.collections_browser_hint_navigate),
                    InputButton.A to stringResource(R.string.collections_browser_hint_select),
                    InputButton.B to stringResource(R.string.collections_browser_hint_back),
                    InputButton.X to stringResource(R.string.collections_browser_hint_search)
                )
                val pinHint = if (uiState.focusedCategory != null) {
                    listOf(
                        InputButton.Y to stringResource(
                            if (uiState.isFocusedCategoryPinned) R.string.collections_browser_hint_unpin else R.string.collections_browser_hint_pin
                        )
                    )
                } else {
                    emptyList()
                }
                val jumpHint = if (uiState.sectionLabels.size > 1) {
                    listOf(InputButton.LT_RT to stringResource(R.string.collections_browser_hint_jump))
                } else {
                    emptyList()
                }
                val refreshHint = listOf(
                    InputButton.START to stringResource(
                        if (uiState.isRefreshing) R.string.collections_browser_hint_refreshing else R.string.collections_browser_hint_refresh
                    )
                )
                baseHints + pinHint + jumpHint + refreshHint
            }
            FooterHints(
                hints = hints,
                onHintClick = { button ->
                    when (button) {
                        InputButton.A -> { inputHandler.onConfirm() }
                        InputButton.B -> { inputHandler.onBack() }
                        InputButton.X -> { inputHandler.onContextMenu() }
                        InputButton.Y -> { inputHandler.onSecondaryAction() }
                        InputButton.START -> { inputHandler.onMenu() }
                        else -> Unit
                    }
                }
            )
        }

        uiState.overlayLetter?.let { letter ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(Dimens.radiusLg))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = letter,
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun VirtualBrowserHeader(
    title: String,
    categoryCount: Int,
    isSearchActive: Boolean,
    searchQuery: String,
    searchFieldFocus: FocusRequester,
    isSearchStopFocused: Boolean,
    onBack: () -> Unit,
    onSearchOpen: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSearchFieldFocused: () -> Unit,
    onSearchClose: () -> Unit
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
                contentDescription = stringResource(R.string.collections_browser_back_description),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.width(Dimens.spacingSm))

        if (isSearchActive) {
            CollectionSearchField(
                query = searchQuery,
                placeholder = stringResource(R.string.collections_browser_search_placeholder, title),
                isFocused = isSearchStopFocused,
                focusRequester = searchFieldFocus,
                onQueryChange = onSearchChange,
                onFieldFocused = onSearchFieldFocused,
                onTap = onSearchOpen,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onSearchClose) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.collections_browser_close_search_description),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        } else {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = pluralStringResource(R.plurals.collections_browser_header_category_count, categoryCount, categoryCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            CollectionSearchButton(
                isFocused = isSearchStopFocused,
                contentDescription = stringResource(R.string.collections_browser_search_description),
                onClick = onSearchOpen
            )
        }
    }
}

@Composable
private fun CategoryRow(
    category: CategoryWithCount,
    isFocused: Boolean,
    isPinned: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CollectionCell(
        name = category.name,
        gameCountLabel = pluralStringResource(
            R.plurals.collections_browser_row_game_count,
            category.summary.gameCount,
            category.summary.gameCount
        ),
        summary = category.summary,
        coverPaths = category.coverPaths,
        placeholderIcon = Icons.Default.Category,
        isFocused = isFocused,
        pinnedDescription = stringResource(R.string.collections_browser_category_pinned_description)
            .takeIf { isPinned },
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
private fun EmptyVirtualBrowser(type: String) {
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
            Text(
                text = stringResource(R.string.collections_browser_empty, type),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
