package com.nendo.argosy.ui.screens.media.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.screens.media.MediaLibraryUi
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * The libraries the account can see, as a row of tabs. Selection is state the ViewModel owns; the
 * tabs are a touch path onto the same shoulder-button action, never a second source of truth.
 */
@Composable
fun MediaLibraryTabs(
    libraries: List<MediaLibraryUi>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        if (selectedIndex in libraries.indices) listState.animateScrollToItem(selectedIndex)
    }

    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth().height(Dimens.mediaSeasonTabHeight),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(libraries, key = { _, library -> library.libraryId }) { index, library ->
            val selected = index == selectedIndex
            val shape = RoundedCornerShape(Dimens.radiusPill)
            Box(
                modifier = Modifier
                    .clip(shape)
                    .argosyFocusIndicators(
                        focused = selected,
                        indicators = FocusIndicators.Pill,
                        shape = shape
                    )
                    .clickableNoFocus { onSelect(index) }
                    .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = library.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) theme.textPrimary else theme.textDim
                )
            }
        }
    }
}
