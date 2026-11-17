package com.nendo.argosy.ui.screens.settings.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.nendo.argosy.R
import com.nendo.argosy.ui.common.resolve
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.preferenceContentColor
import com.nendo.argosy.ui.components.preferenceModifier
import com.nendo.argosy.ui.components.preferenceSecondaryColor
import com.nendo.argosy.ui.screens.settings.SETTINGS_SEARCH_FIELD_INDEX
import com.nendo.argosy.ui.screens.settings.components.LocalSettingsTouchFocus
import com.nendo.argosy.ui.theme.Dimens

private const val PATH_SEPARATOR = " > "

@Composable
internal fun SettingsSearchRow(query: String, isFocused: Boolean, onClick: () -> Unit) {
    Row(
        modifier = preferenceModifier(isFocused, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = if (isFocused) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(Dimens.iconMd)
        )
        Spacer(modifier = Modifier.width(Dimens.spacingMd))
        Text(
            text = query.ifBlank { stringResource(R.string.settings_search_row_title) },
            style = MaterialTheme.typography.titleMedium,
            color = if (query.isBlank()) preferenceSecondaryColor(isFocused) else preferenceContentColor(isFocused)
        )
    }
}

@Composable
internal fun SettingsSearchResultsList(
    search: SettingsSearchState,
    focusedIndex: Int,
    listState: LazyListState,
    onSearchTap: () -> Unit,
    onResultTap: (Int) -> Unit
) {
    FocusedScroll(
        listState = listState,
        focusedIndex = focusedIndex,
        isTouchMode = LocalSettingsTouchFocus.current.isTouchMode
    )

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        item(key = SEARCH_ROW_KEY) {
            SettingsSearchRow(
                query = search.query,
                isFocused = focusedIndex == SETTINGS_SEARCH_FIELD_INDEX,
                onClick = onSearchTap
            )
        }
        if (search.results.isEmpty()) {
            item(key = EMPTY_ROW_KEY) {
                Text(
                    text = stringResource(R.string.settings_search_no_results, search.query.trim()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Dimens.spacingMd)
                )
            }
        }
        itemsIndexed(search.results, key = { _, result -> result.entry.target.id }) { index, result ->
            SettingsSearchResultRow(
                result = result,
                isFocused = focusedIndex == SETTINGS_SEARCH_FIELD_INDEX + 1 + index,
                onClick = { onResultTap(index) }
            )
        }
    }
}

@Composable
private fun SettingsSearchResultRow(result: SettingsSearchResult, isFocused: Boolean, onClick: () -> Unit) {
    val label = result.entry.label.resolve()
    val path = result.entry.path.map { it.resolve() }.joinToString(PATH_SEPARATOR)
    Column(modifier = preferenceModifier(isFocused, onClick = onClick)) {
        Text(
            text = path,
            style = MaterialTheme.typography.bodySmall,
            color = preferenceSecondaryColor(isFocused)
        )
        Text(
            text = highlighted(label, result.matchStart, result.matchLength),
            style = MaterialTheme.typography.titleMedium,
            color = preferenceContentColor(isFocused)
        )
    }
}

@Composable
private fun highlighted(label: String, start: Int, length: Int): AnnotatedString {
    val end = start + length
    if (start < 0 || length <= 0 || end > label.length) return AnnotatedString(label)
    val highlight = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    return buildAnnotatedString {
        append(label.substring(0, start))
        withStyle(highlight) { append(label.substring(start, end)) }
        append(label.substring(end))
    }
}

private const val SEARCH_ROW_KEY = "settingsSearchField"

private const val EMPTY_ROW_KEY = "settingsSearchEmpty"
