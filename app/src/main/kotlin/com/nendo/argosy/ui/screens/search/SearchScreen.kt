package com.nendo.argosy.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.FooterSpacer
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

private const val FOCUS_TINT_ALPHA = 0.15f

private const val PLACEHOLDER_ALPHA = 0.6f

@Composable
fun SearchScreen(
    onGameSelect: (Long) -> Unit,
    onMediaSelect: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    val inputDispatcher = LocalInputDispatcher.current
    val inputHandler = remember(onGameSelect, onMediaSelect, onBack) {
        viewModel.createInputHandler(
            onGameSelect = onGameSelect,
            onMediaSelect = onMediaSelect,
            onBack = onBack
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
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

    LaunchedEffect(uiState.showKeyboard) {
        if (uiState.showKeyboard) {
            focusRequester.requestFocus()
        }
    }

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
            focusRequester = focusRequester
        )

        when {
            uiState.isSearching -> LoadingState()
            uiState.query.length < MIN_QUERY_LENGTH -> {
                EmptyState(
                    message = pluralStringResource(
                        R.plurals.library_search_min_query_hint,
                        MIN_QUERY_LENGTH,
                        MIN_QUERY_LENGTH
                    )
                )
            }
            !uiState.hasResults -> EmptyState(
                message = stringResource(R.string.library_search_no_results, uiState.query)
            )
            else -> {
                SearchResults(
                    state = uiState,
                    listState = listState,
                    onSelect = { index ->
                        viewModel.openAt(index, onGameSelect, onMediaSelect)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        SearchFooter(
            resultCount = uiState.resultCount,
            showGroupJump = uiState.hasBothKinds,
            onHintClick = { button ->
                when (button) {
                    InputButton.A -> { inputHandler.onConfirm() }
                    InputButton.B -> { inputHandler.onBack() }
                    InputButton.LB_RB -> { viewModel.toggleGroup() }
                    else -> Unit
                }
            }
        )
    }
}

@Composable
private fun SearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean,
    mediaSearchable: Boolean,
    focusRequester: FocusRequester
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(Dimens.radiusLg)
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

/**
 * Games first, then media, under headings that appear only when both kinds are present. A single
 * heading over the only group there is labels nothing, and the rows already say which kind they are.
 */
@Composable
private fun SearchResults(
    state: SearchUiState,
    listState: LazyListState,
    onSelect: (Int) -> Unit
) {
    val showHeadings = state.hasBothKinds
    val focusedListIndex = remember(
        state.focusedIndex,
        state.gameResults.size,
        state.mediaResults.size,
        showHeadings
    ) {
        listIndexOfFocus(state, showHeadings)
    }

    FocusedScroll(listState = listState, focusedIndex = focusedListIndex)

    LazyColumn(
        state = listState,
        modifier = Modifier.padding(horizontal = Dimens.spacingMd),
        contentPadding = PaddingValues(vertical = Dimens.spacingSm),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        if (showHeadings && state.gameResults.isNotEmpty()) {
            item(key = "heading:games") {
                GroupHeading(text = stringResource(R.string.library_search_heading_games))
            }
        }
        itemsIndexed(state.gameResults, key = { _, result -> result.key }) { index, result ->
            SearchResultRow(
                isFocused = index == state.focusedIndex,
                onClick = { onSelect(index) }
            ) {
                GameResultContent(result = result)
            }
        }

        if (showHeadings && state.mediaResults.isNotEmpty()) {
            item(key = "heading:media") {
                GroupHeading(text = stringResource(R.string.library_search_heading_media))
            }
        }
        itemsIndexed(state.mediaResults, key = { _, result -> result.key }) { index, result ->
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

@Composable
private fun RowScope.GameResultContent(result: SearchResultUi.Game) {
    AsyncImage(
        model = rememberFileImageModel(result.coverPath),
        contentDescription = result.title,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(Dimens.searchResultArtwork)
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    )

    Spacer(modifier = Modifier.width(Dimens.spacingMd))

    ResultLabels(
        title = result.title,
        primaryDetail = result.platformName
            ?: stringResource(R.string.library_search_result_platform_unknown),
        details = listOfNotNull(result.releaseYear?.toString(), result.developer)
    )
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

private const val MIN_QUERY_LENGTH = 2
private const val WEIGHT_FILL = 1f
