package com.nendo.argosy.ui.quickmenu

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.nendo.argosy.ui.components.frostedVisualChromeHeight
import com.nendo.argosy.ui.quickmenu.components.QuickMenuPreviewPane
import com.nendo.argosy.ui.quickmenu.components.toCard
import com.nendo.argosy.ui.screens.search.SearchPresentation
import com.nendo.argosy.ui.screens.search.SearchPresentationState
import com.nendo.argosy.ui.screens.search.SearchResultUi
import com.nendo.argosy.ui.theme.Dimens

/**
 * What the presentation screen draws for the quick menu: the focused game's [preview], or on the
 * Search tab the [search] grid Search itself draws.
 */
data class QuickMenuPresentationState(
    val preview: QuickMenuPreview?,
    val search: SearchPresentationState? = null
)

internal fun QuickMenuUiState.toPresentationState(): QuickMenuPresentationState =
    QuickMenuPresentationState(preview = preview, search = searchPresentation())

private fun QuickMenuUiState.searchPresentation(): SearchPresentationState? {
    if (selectedOrb != QuickMenuOrb.SEARCH || focusedGame() != null) return null
    val results = searchResults?.takeIf { it.query == focusedSearch?.query }
    val covers = if (results == null) recent.rows.map { it.game } else results.games.orEmpty()
    return SearchPresentationState(
        query = results?.query.orEmpty(),
        isSearching = results != null && results.games == null,
        isLanding = results == null,
        covers = covers.map { SearchResultUi.Game(key = "game:${it.id}", title = it.title, gameId = it.id, card = it.toCard()) },
        columns = presentationColumns,
        spacingDp = gridSpacingDp,
        focusIndex = 0,
        focusOnTop = false
    )
}

@Composable
fun QuickMenuPresentation(state: QuickMenuPresentationState, bottomInset: Dp, onTap: () -> Unit) {
    val search = state.search
    if (search != null) {
        SearchPresentation(state = search, bottomInset = bottomInset, onTap = { onTap() })
        return
    }
    val preview = state.preview ?: return
    Box(modifier = Modifier.fillMaxSize().padding(bottom = bottomInset)) {
        QuickMenuPreviewPane(
            preview = preview,
            large = true,
            onPrimary = onTap,
            modifier = Modifier.fillMaxSize(),
            contentTopInset = Dimens.spacingLg + frostedVisualChromeHeight
        )
    }
}
