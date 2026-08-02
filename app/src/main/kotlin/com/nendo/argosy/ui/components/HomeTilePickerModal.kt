package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * What the picker can offer. Held as a resolved target rather than an id so the list can mix the
 * kinds of thing a tile may point at, and so placing one is the same call whichever kind it is.
 */
data class TilePickerEntry(
    val target: com.nendo.argosy.domain.model.HomeTileTargetRef,
    val title: String,
    val subtitle: String,
    val coverPath: String? = null,
    val packageName: String? = null
) {
    val gameId: Long?
        get() = (target as? com.nendo.argosy.domain.model.HomeTileTargetRef.Game)?.gameId

    val key: String get() = "${target::class.simpleName}:${gameId ?: packageName ?: title}"
}

/**
 * What the picker is currently listing. A curated page mixes games, collections and apps, and a
 * flat list of all three would bury the games; each kind gets its own tab instead.
 */
enum class TilePickerCategory(val label: String) {
    GAMES("Games"),
    COLLECTIONS("Collections"),
    APPS("Apps")
}

/**
 * Chooses what goes in an empty cell. Only installed games are offered, because a curated grid is
 * somewhere you reach for something to play rather than something to fetch.
 *
 * Owns no state: the query and the focus index belong to the caller, so the gamepad drives it
 * through the same input handler as everything else on the screen.
 */
@Composable
fun HomeTilePickerModal(
    entries: List<TilePickerEntry>,
    query: String,
    focusIndex: Int,
    onSelect: (TilePickerEntry) -> Unit,
    onDismiss: () -> Unit,
    searchActive: Boolean = false,
    onQueryChange: (String) -> Unit = {},
    category: TilePickerCategory = TilePickerCategory.GAMES,
    onSelectCategory: (TilePickerCategory) -> Unit = {}
) {
    val listState = rememberLazyListState()
    FocusedScroll(listState = listState, focusedIndex = focusIndex)

    Modal(
        title = "ADD TO GRID",
        subtitle = if (searchActive || query.isBlank()) null else "Matching \"$query\"",
        baseWidth = Dimens.modalWidthLg,
        onDismiss = onDismiss
    ) {
        TilePickerTabs(
            category = category,
            onSelectCategory = onSelectCategory,
            modifier = Modifier.padding(bottom = Dimens.spacingSm)
        )
        if (searchActive) {
            TilePickerSearchField(
                query = query,
                onQueryChange = onQueryChange,
                modifier = Modifier.padding(bottom = Dimens.spacingSm)
            )
        }
        if (entries.isEmpty()) {
            Text(
                text = if (query.isBlank()) {
                    when (category) {
                    TilePickerCategory.GAMES -> "No installed games to add"
                    TilePickerCategory.COLLECTIONS -> "No collections yet"
                    TilePickerCategory.APPS -> "No apps found"
                }
                } else {
                    "Nothing matches that search"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = LocalArgosyTheme.current.textDim,
                modifier = Modifier.padding(Dimens.spacingMd)
            )
            return@Modal
        }
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(Dimens.listGap)
        ) {
            itemsIndexed(entries, key = { _, entry -> entry.key }) { index, entry ->
                TilePickerRow(
                    entry = entry,
                    isFocused = index == focusIndex,
                    onClick = { onSelect(entry) }
                )
            }
        }
    }
}

@Composable
private fun TilePickerRow(
    entry: TilePickerEntry,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .argosyFocusIndicators(
                focused = isFocused,
                indicators = FocusIndicators.ListRow,
                shape = shape
            )
            .clickableNoFocus(onClick = onClick)
            .padding(Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Box(
            modifier = Modifier
                .width(Dimens.iconXl)
                .aspectRatio(COVER_ASPECT)
                .clip(RoundedCornerShape(Dimens.radiusSm))
                .background(theme.surfaceRaised)
        ) {
            val cover = rememberFileImageModel(entry.coverPath)
            val appIcon = entry.packageName?.let { com.nendo.argosy.ui.coil.AppIconData(it) }
            when {
                appIcon != null -> AsyncImage(
                    model = appIcon,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(Dimens.spacingXs)
                )
                cover != null -> AsyncImage(
                    model = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = entry.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = theme.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private const val COVER_ASPECT = 0.7f

/**
 * The one place the grid gives focus to Compose. A soft keyboard has to reach a text field, and
 * nothing else on this modal competes for it, so the exception stays contained to typing.
 */
@Composable
private fun TilePickerSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val focusRequester = remember { FocusRequester() }
    val shape = RoundedCornerShape(Dimens.radiusControl)

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(theme.surfaceRaised)
            .padding(Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = theme.textDim,
            modifier = Modifier.size(Dimens.iconSm)
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            textStyle = TextStyle(
                color = theme.textPrimary,
                fontSize = MaterialTheme.typography.bodyMedium.fontSize
            ),
            cursorBrush = SolidColor(theme.focusAccent),
            singleLine = true,
            decorationBox = { innerTextField ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = theme.textDim
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

/**
 * The tabs across the top of the picker. They are a readout rather than a control: the bumpers move
 * between them, which keeps the modal's only focusable list the entries themselves.
 */
@Composable
private fun TilePickerTabs(
    category: TilePickerCategory,
    onSelectCategory: (TilePickerCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusSm)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        TilePickerCategory.entries.forEach { entry ->
            val isCurrent = entry == category
            Text(
                text = entry.label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = if (isCurrent) theme.focusAccent else theme.textDim,
                modifier = Modifier
                    .clip(shape)
                    .background(if (isCurrent) theme.surfaceRaised else Color.Transparent)
                    .clickableNoFocus { onSelectCategory(entry) }
                    .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs)
            )
        }
    }
}
