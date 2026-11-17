package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

private const val FOCUS_TINT_ALPHA = 0.15f
private const val FOCUS_TEXT_LIGHTEN = 0.45f

/**
 * Recent search queries under [heading], newest first. [focusedIndex] is the position within
 * [searches]; the row only draws focus while [isFocused] is true.
 */
@Composable
fun RecentSearchesList(
    heading: String,
    searches: List<String>,
    focusedIndex: Int,
    isFocused: Boolean,
    onRecentSearchSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(focusedIndex) {
        if (focusedIndex in searches.indices) {
            listState.animateScrollToItemCentered(focusedIndex)
        }
    }

    Column(modifier = modifier) {
        Text(
            text = heading,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Dimens.spacingSm)
        )

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            itemsIndexed(searches, key = { _, query -> query }) { index, query ->
                RecentSearchRow(
                    query = query,
                    isFocused = isFocused && index == focusedIndex,
                    onRecentSearchSelect = onRecentSearchSelect
                )
            }
        }
    }
}

@Composable
private fun RecentSearchRow(
    query: String,
    isFocused: Boolean,
    onRecentSearchSelect: (String) -> Unit
) {
    val shape = RoundedCornerShape(Dimens.radiusMd)
    val focusAccent = LocalArgosyTheme.current.focusAccent
    val borderModifier = if (isFocused) {
        Modifier.border(Dimens.borderMedium, MaterialTheme.colorScheme.primary, shape)
    } else {
        Modifier
    }
    val contentColor = if (isFocused) {
        lerp(focusAccent, Color.White, FOCUS_TEXT_LIGHTEN)
    } else {
        null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(borderModifier)
            .background(
                if (isFocused) {
                    focusAccent.copy(alpha = FOCUS_TINT_ALPHA).compositeOver(MaterialTheme.colorScheme.surface)
                } else {
                    MaterialTheme.colorScheme.surface
                },
                shape
            )
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
            .clickableNoFocus(onClick = { onRecentSearchSelect(query) }),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Icon(
            Icons.Default.History,
            contentDescription = null,
            tint = contentColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Dimens.iconSm)
        )
        Text(
            text = query,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
