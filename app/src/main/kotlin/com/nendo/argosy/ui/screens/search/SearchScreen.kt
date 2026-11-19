package com.nendo.argosy.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.flowWithLifecycle
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.ui.dualscreen.PresentOnCompanion
import com.nendo.argosy.ui.dualscreen.PresentationSlot
import com.nendo.argosy.ui.dualscreen.SlotOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import com.nendo.argosy.ui.components.keyboard.ControllerKeyboardSession
import com.nendo.argosy.ui.components.keyboard.ControllerKeyboardTapTarget
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.FooterSpacer
import com.nendo.argosy.ui.components.GameCard
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.RecentSearchesList
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.theme.reducedMotion
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalBoxArtStyle
import com.nendo.argosy.ui.util.clickableNoFocus

private const val SEARCH_SLOT_OWNER = "search"

private const val FOCUS_TINT_ALPHA = 0.15f

private const val PLACEHOLDER_ALPHA = 0.6f

private const val ROW_TILE_FOCUS_SCALE = 1f

private const val FOCUSED_TILE_Z = 1f

private const val RESTING_TILE_Z = 0f

@Composable
fun SearchScreen(
    onGameSelect: (Long) -> Unit,
    onMediaSelect: (String) -> Unit,
    onBack: () -> Unit,
    initialQuery: String? = null,
    viewModel: SearchViewModel = hiltViewModel()
) {
    LaunchedEffect(initialQuery) {
        if (initialQuery != null) viewModel.openWithQuery(initialQuery)
    }
    val uiState by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()

    val configuration = LocalConfiguration.current
    LaunchedEffect(configuration.screenWidthDp) {
        viewModel.updateScreenWidth(configuration.screenWidthDp)
    }

    val inputDispatcher = LocalInputDispatcher.current
    val inputHandler = remember(onGameSelect, onMediaSelect, onBack) {
        viewModel.createInputHandler(
            onGameSelect = onGameSelect,
            onMediaSelect = onMediaSelect,
            onBack = onBack
        )
    }
    val previewHandler = remember(onGameSelect) { viewModel.createPreviewInputHandler(onGameSelect) }
    val dualScreenHandler = remember(onGameSelect) { viewModel.createDualScreenInputHandler(onGameSelect) }

    val lifecycleOwner = LocalLifecycleOwner.current
    SearchOnPresentation(
        viewModel = viewModel,
        state = uiState,
        lifecycle = lifecycleOwner.lifecycle,
        onGameSelect = onGameSelect
    )
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_SEARCH)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_SEARCH)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    ControllerKeyboardSession(
        open = uiState.showKeyboard,
        label = if (uiState.mediaSearchable) {
            stringResource(R.string.library_search_placeholder_games_and_media)
        } else {
            stringResource(R.string.library_search_placeholder_games)
        },
        text = uiState.query,
        onTextChange = { viewModel.updateQuery(it) },
        onSubmit = { viewModel.submitQuery() },
        onClose = { viewModel.closeKeyboard() },
        submitLabel = stringResource(R.string.library_search_keyboard_submit),
        onSystemKeyboard = { focusRequester.requestFocus() },
        onTopEdge = { viewModel.enterPreview() },
        focusOutside = uiState.previewFocused,
        accessory = {
            SearchPreviewRow(viewModel = viewModel, onGameSelect = onGameSelect)
            SearchHistoryPanel(viewModel = viewModel)
        }
    )

    ModalInputEffect(
        active = !uiState.dualScreen && uiState.showKeyboard && uiState.previewFocused,
        handler = previewHandler
    )
    val dualScreenFocused = uiState.dualScreen && uiState.isAboveKeyboard
    ModalInputEffect(active = dualScreenFocused, handler = dualScreenHandler)
    val activeHandler = if (dualScreenFocused) dualScreenHandler else inputHandler

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SearchHeader(
            query = uiState.query,
            onQueryChange = { viewModel.updateQuery(it) },
            isSearching = uiState.isSearching,
            mediaSearchable = uiState.mediaSearchable,
            focusRequester = focusRequester,
            isFocused = uiState.isFieldFocused,
            onFieldFocused = { if (uiState.showKeyboard) viewModel.closeKeyboard() },
            onTap = { viewModel.focusKeyboard() }
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                uiState.dualScreen -> if (!uiState.showKeyboard) SearchHistoryPanel(viewModel = viewModel)
                uiState.isSearching -> LoadingState()
                uiState.isLanding && uiState.hasLandingContent -> SearchLanding(
                    state = uiState,
                    onRecentSearchSelect = { viewModel.selectRecentSearch(it) },
                    onOpen = { index -> viewModel.openAt(index, onGameSelect, onMediaSelect) }
                )
                uiState.isLanding -> EmptyState(
                    message = pluralStringResource(
                        R.plurals.library_search_min_query_hint,
                        SearchViewModel.MIN_QUERY_LENGTH,
                        SearchViewModel.MIN_QUERY_LENGTH
                    )
                )
                !uiState.hasResults -> EmptyState(
                    message = stringResource(R.string.library_search_no_results, uiState.query)
                )
                else -> SearchResults(
                    state = uiState,
                    gridState = gridState,
                    onSelect = { index -> viewModel.openAt(index, onGameSelect, onMediaSelect) }
                )
            }
        }

        SearchFooter(
            resultCount = uiState.resultCount,
            showGroupJump = uiState.hasBothKinds && !uiState.dualScreen,
            onHintClick = { button ->
                when (button) {
                    InputButton.A -> { activeHandler.onConfirm() }
                    InputButton.B -> { activeHandler.onBack() }
                    InputButton.LB_RB -> { viewModel.toggleGroup() }
                    else -> Unit
                }
            }
        )
    }
}

@Composable
private fun SearchOnPresentation(
    viewModel: SearchViewModel,
    state: SearchUiState,
    lifecycle: Lifecycle,
    onGameSelect: (Long) -> Unit
) {
    val manager = DualScreenManagerHolder.instance
    val owner = remember(viewModel) { SlotOwner.of(SEARCH_SLOT_OWNER, viewModel) }
    val presentationUp by (
        manager?.presentationShowsHints ?: remember { MutableStateFlow(false) }
        ).collectAsState()
    val shownSlot by (
        manager?.presentationSlot ?: remember { MutableStateFlow<PresentationSlot>(PresentationSlot.Fallback) }
        ).collectAsState()
    val usesPresentation = searchUsesPresentation(presentationUp, shownSlot)
    LaunchedEffect(usesPresentation) { viewModel.setDualScreen(usesPresentation) }

    if (manager == null || !presentationUp) return
    PresentOnCompanion(owner = owner, slot = PresentationSlot.Search(owner, state.toPresentationState()))

    val currentOnGameSelect by rememberUpdatedState(onGameSelect)
    LaunchedEffect(manager, owner, lifecycle) {
        manager.presentationTap
            .flowWithLifecycle(lifecycle)
            .filterNotNull()
            .filter { it.owner == owner }
            .collect { tap ->
                if (manager.consumePresentationTap(tap)) viewModel.openCoverAt(tap.index, currentOnGameSelect)
            }
    }
}

@Composable
private fun SearchHistoryPanel(viewModel: SearchViewModel) {
    val state by viewModel.uiState.collectAsState()
    val entries = state.historyEntries
    if (!state.dualScreen || entries.isEmpty()) return
    RecentSearchesList(
        heading = stringResource(R.string.library_search_history_heading),
        searches = entries,
        focusedIndex = state.historyIndex,
        isFocused = state.historyFocused,
        onRecentSearchSelect = { viewModel.selectRecentSearch(it) },
        modifier = Modifier
            .fillMaxSize()
            .background(LocalArgosyTheme.current.surfaceBase)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
    )
}

@Composable
private fun SearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean,
    mediaSearchable: Boolean,
    focusRequester: FocusRequester,
    isFocused: Boolean,
    onFieldFocused: () -> Unit,
    onTap: () -> Unit
) {
    val fieldShape = RoundedCornerShape(Dimens.radiusLg)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ControllerKeyboardTapTarget(onTap = onTap, modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isFocused) {
                            Modifier.border(Dimens.borderMedium, MaterialTheme.colorScheme.primary, fieldShape)
                        } else {
                            Modifier
                        }
                    )
                    .background(
                        if (isFocused) {
                            LocalArgosyTheme.current.focusAccent.copy(alpha = FOCUS_TINT_ALPHA)
                                .compositeOver(MaterialTheme.colorScheme.surfaceVariant)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        fieldShape
                    )
                    .padding(horizontal = Dimens.spacingMd, vertical = Dimens.radiusLg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Dimens.iconSm)
                )
                Spacer(modifier = Modifier.width(Dimens.radiusLg))

                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = if (mediaSearchable) {
                                stringResource(R.string.library_search_placeholder_games_and_media)
                            } else {
                                stringResource(R.string.library_search_placeholder_games)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                .copy(alpha = PLACEHOLDER_ALPHA)
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { if (it.isFocused) onFieldFocused() }
                    )
                }

                if (isSearching) {
                    Spacer(modifier = Modifier.width(Dimens.radiusLg))
                    CircularProgressIndicator(
                        modifier = Modifier.size(Dimens.iconSm),
                        strokeWidth = Dimens.borderMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchLanding(
    state: SearchUiState,
    onRecentSearchSelect: (String) -> Unit,
    onOpen: (Int) -> Unit
) {
    val searchCount = state.recentSearches.size
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingLg)
    ) {
        if (state.recentSearches.isNotEmpty()) {
            RecentSearchesList(
                heading = stringResource(R.string.library_search_heading_recent_searches),
                searches = state.recentSearches,
                focusedIndex = state.focusedIndex,
                isFocused = state.focusedIndex in 0 until searchCount,
                onRecentSearchSelect = onRecentSearchSelect,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
        if (state.recentGames.isNotEmpty()) {
            Column {
                GroupHeading(text = stringResource(R.string.library_search_heading_recently_played))
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val columns = state.gameColumns
                    val spacing = state.gridSpacingDp.dp
                    val tileWidth = (maxWidth - spacing * (columns - 1)) / columns
                    SearchCoverRow(
                        games = state.recentGames,
                        focusedIndex = state.focusedIndex - searchCount,
                        tileWidth = tileWidth,
                        tileHeight = tileWidth / LocalBoxArtStyle.current.aspectRatio,
                        spacing = spacing,
                        onClick = { index -> onOpen(searchCount + index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchPreviewRow(viewModel: SearchViewModel, onGameSelect: (Long) -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val games = state.previewGames
    if (state.dualScreen || games.isEmpty()) return
    val tileHeight = Dimens.searchPreviewCoverHeight
    SearchCoverRow(
        games = games,
        focusedIndex = if (state.previewFocused) state.previewIndex else -1,
        tileWidth = tileHeight * LocalBoxArtStyle.current.aspectRatio,
        tileHeight = tileHeight,
        spacing = Dimens.spacingSm,
        onClick = { index -> viewModel.openPreviewAt(index, onGameSelect) },
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalArgosyTheme.current.surfaceBase)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
    )
}

@Composable
private fun SearchCoverRow(
    games: List<SearchResultUi.Game>,
    focusedIndex: Int,
    tileWidth: Dp,
    tileHeight: Dp,
    spacing: Dp,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val reduced = reducedMotion()
    LaunchedEffect(focusedIndex) {
        if (focusedIndex in games.indices) listState.animateScrollToItemCentered(focusedIndex, reduced)
    }
    LazyRow(
        state = listState,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing)
    ) {
        itemsIndexed(games, key = { _, game -> game.key }) { index, game ->
            GameCard(
                game = game.card,
                isFocused = index == focusedIndex,
                focusScale = ROW_TILE_FOCUS_SCALE,
                modifier = Modifier
                    .width(tileWidth)
                    .height(tileHeight)
                    .clickableNoFocus { onClick(index) }
            )
        }
    }
}

@Composable
private fun SearchResults(
    state: SearchUiState,
    gridState: LazyGridState,
    onSelect: (Int) -> Unit
) {
    val showHeadings = state.hasBothKinds
    val spacing = state.gridSpacingDp.dp
    val aspectRatio = LocalBoxArtStyle.current.aspectRatio
    val focusedGridIndex = remember(
        state.focusedIndex,
        state.gameResults.size,
        state.mediaResults.size,
        showHeadings
    ) {
        listIndexOfFocus(state, showHeadings)
    }

    val reduced = reducedMotion()
    LaunchedEffect(state.focusedIndex, focusedGridIndex) {
        if (state.focusedIndex >= 0) gridState.animateScrollToItemCentered(focusedGridIndex, reduced)
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(state.gameColumns),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Dimens.spacingMd),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        if (showHeadings && state.gameResults.isNotEmpty()) {
            item(key = "heading:games", span = { GridItemSpan(maxLineSpan) }) {
                GroupHeading(text = stringResource(R.string.library_search_heading_games))
            }
        }
        itemsIndexed(state.gameResults, key = { _, result -> result.key }) { index, result ->
            val isFocused = index == state.focusedIndex
            GameCard(
                game = result.card,
                isFocused = isFocused,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .zIndex(if (isFocused) FOCUSED_TILE_Z else RESTING_TILE_Z)
                    .clickableNoFocus { onSelect(index) }
            )
        }

        if (showHeadings && state.mediaResults.isNotEmpty()) {
            item(key = "heading:media", span = { GridItemSpan(maxLineSpan) }) {
                GroupHeading(text = stringResource(R.string.library_search_heading_media))
            }
        }
        itemsIndexed(
            state.mediaResults,
            key = { _, result -> result.key },
            span = { _, _ -> GridItemSpan(maxLineSpan) }
        ) { index, result ->
            val focusIndex = state.gameResults.size + index
            SearchResultRow(
                isFocused = focusIndex == state.focusedIndex,
                onClick = { onSelect(focusIndex) }
            ) {
                MediaResultContent(result = result)
            }
        }
    }
}

/**
 * Where the focused result sits in the rendered list, which is not where it sits in the focus order
 * once headings are between the groups.
 */
private fun listIndexOfFocus(state: SearchUiState, showHeadings: Boolean): Int {
    val gamesHeading = if (showHeadings && state.gameResults.isNotEmpty()) 1 else 0
    val mediaHeading = if (showHeadings && state.mediaResults.isNotEmpty()) 1 else 0
    val focused = state.focusedIndex.coerceAtLeast(0)
    return if (focused < state.gameResults.size) {
        gamesHeading + focused
    } else {
        gamesHeading + state.gameResults.size + mediaHeading + (focused - state.gameResults.size)
    }
}

@Composable
private fun GroupHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = Dimens.spacingSm,
            top = Dimens.spacingSm,
            bottom = Dimens.spacingXs
        )
    )
}

@Composable
private fun SearchResultRow(
    isFocused: Boolean,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isFocused) {
                    Modifier.border(
                        Dimens.borderMedium,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(Dimens.radiusControl)
                    )
                } else {
                    Modifier
                }
            )
            .background(
                if (isFocused) {
                    LocalArgosyTheme.current.focusAccent.copy(alpha = FOCUS_TINT_ALPHA)
                        .compositeOver(MaterialTheme.colorScheme.surface)
                } else {
                    MaterialTheme.colorScheme.surface
                },
                RoundedCornerShape(Dimens.radiusControl)
            )
            .clickableNoFocus { onClick() }
            .padding(Dimens.radiusLg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

/**
 * A title identifies itself by its library the way a game identifies itself by its platform. Where
 * the server holds no poster the kind stands in, because an unlabelled grey square in a mixed list is
 * the one row the reader cannot place.
 */
@Composable
private fun RowScope.MediaResultContent(result: SearchResultUi.Media) {
    var posterFailed by remember(result.posterUrl) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(Dimens.searchResultArtwork)
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (result.posterUrl.isBlank() || posterFailed) {
            Icon(
                imageVector = Icons.Default.Movie,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimens.iconMd)
            )
        } else {
            AsyncImage(
                model = result.posterUrl,
                contentDescription = result.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { posterFailed = true }
            )
        }
    }

    Spacer(modifier = Modifier.width(Dimens.spacingMd))

    ResultLabels(
        title = result.title,
        primaryDetail = result.libraryName ?: stringResource(result.kindLabelRes),
        details = listOfNotNull(result.releaseYear?.toString())
    )
}

@Composable
private fun RowScope.ResultLabels(
    title: String,
    primaryDetail: String,
    details: List<String>
) {
    Column(modifier = Modifier.weight(WEIGHT_FILL)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = primaryDetail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            details.forEach { detail ->
                Text(
                    text = "|",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.spacingXl),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.spacingXl),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun SearchFooter(
    resultCount: Int,
    showGroupJump: Boolean,
    onHintClick: ((InputButton) -> Unit)? = null
) {
    val groupJumpHint = stringResource(R.string.library_search_hint_group_jump)
    val selectHint = stringResource(R.string.library_search_hint_select)
    val backHint = stringResource(R.string.library_search_hint_back)
    val hints = remember(showGroupJump, groupJumpHint, selectHint, backHint) {
        buildList {
            if (showGroupJump) add(InputButton.LB_RB to groupJumpHint)
            add(InputButton.A to selectHint)
            add(InputButton.B to backHint)
        }
    }
    FooterHints(
        hints = hints,
        onHintClick = onHintClick,
        trailingContent = if (resultCount > 0) {
            {
                Text(
                    text = pluralStringResource(
                        R.plurals.library_search_result_count,
                        resultCount,
                        resultCount
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            null
        }
    )
    FooterSpacer()
}

private const val WEIGHT_FILL = 1f
