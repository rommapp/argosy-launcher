package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
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
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * What confirming a picker row does.
 *
 * [BROWSE_LOCAL_FILE] is a row that opens the device's own file browser rather than placing
 * anything. It lives among the entries instead of beside them because a curated grid is filled by
 * choosing from this list, and an action reachable only by a second control is an action a reader
 * on a television never finds.
 */
enum class TilePickerAction { PLACE, BROWSE_LOCAL_FILE }

/**
 * What the picker can offer. Held as a resolved target rather than an id so the list can mix the
 * kinds of thing a tile may point at, and so placing one is the same call whichever kind it is.
 *
 * [isSeries] is carried because a series is the one entry that cannot be placed from this list
 * alone: it stands for a whole show, and which part of it a tile plays is a question with four
 * answers that has to be asked before anything is stored.
 */
data class TilePickerEntry(
    val target: com.nendo.argosy.domain.model.HomeTileTargetRef,
    val title: String,
    val subtitle: String,
    val coverPath: String? = null,
    val packageName: String? = null,
    val posterUrl: String? = null,
    val action: TilePickerAction = TilePickerAction.PLACE,
    val isSeries: Boolean = false,
    val isLocal: Boolean = false
) {
    val gameId: Long?
        get() = (target as? com.nendo.argosy.domain.model.HomeTileTargetRef.Game)?.gameId

    val mediaItemId: String?
        get() = (target as? com.nendo.argosy.domain.model.HomeTileTargetRef.Media)?.itemId

    val key: String
        get() = "$action:${target::class.simpleName}:" +
            "${gameId ?: mediaItemId ?: packageName ?: title}"
}

/**
 * What the picker is currently listing. A curated page mixes games, collections, apps and titles,
 * and a flat list of all four would bury the games; each kind gets its own tab instead.
 *
 * Which tabs are actually offered is the caller's answer, not this enum's: with no media account
 * there is no Media tab, because a tab that lists nothing advertises a feature the reader has not
 * asked for.
 */
enum class TilePickerCategory {
    GAMES, COLLECTIONS, APPS, MEDIA, FEATURES;

    @get:StringRes
    val labelRes: Int
        get() = when (this) {
            GAMES -> R.string.tile_picker_category_games
            COLLECTIONS -> R.string.tile_picker_category_collections
            APPS -> R.string.tile_picker_category_apps
            MEDIA -> R.string.tile_picker_category_media
            FEATURES -> R.string.tile_picker_category_features
        }
}

/**
 * Chooses what goes in an empty cell. Only installed games are offered, because a curated grid is
 * somewhere you reach for something to play rather than something to fetch.
 *
 * Owns no state: the query and the focus index belong to the caller, so the gamepad drives it
 * through the same input handler as everything else on the screen.
 *
 * When [canDeletePage] the last focus index belongs to the destructive footer rather than to an
 * entry, which is why it is pinned below the list instead of scrolling with it.
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
    categories: List<TilePickerCategory> = TilePickerCategory.entries,
    onSelectCategory: (TilePickerCategory) -> Unit = {},
    canDeletePage: Boolean = false,
    onDeletePage: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    FocusedScroll(listState = listState, focusedIndex = focusIndex)

    Modal(
        title = stringResource(R.string.ui_tile_picker_title),
        subtitle = if (searchActive || query.isBlank()) {
            null
        } else {
            stringResource(R.string.ui_tile_picker_subtitle_matching, query)
        },
        baseWidth = Dimens.modalWidthLg,
        onDismiss = onDismiss
    ) {
        TilePickerTabs(
            category = category,
            categories = categories,
            onSelectCategory = onSelectCategory,
            modifier = Modifier.padding(bottom = Dimens.spacingSm)
        )
        if (searchActive) {
            ModalSearchField(
                query = query,
                onQueryChange = onQueryChange,
                modifier = Modifier.padding(bottom = Dimens.spacingSm)
            )
        }
        if (entries.isEmpty()) {
            Text(
                text = if (query.isBlank()) {
                    when (category) {
                        TilePickerCategory.GAMES ->
                            stringResource(R.string.ui_tile_picker_empty_games)
                        TilePickerCategory.COLLECTIONS ->
                            stringResource(R.string.ui_tile_picker_empty_collections)
                        TilePickerCategory.APPS ->
                            stringResource(R.string.ui_tile_picker_empty_apps)
                        TilePickerCategory.MEDIA ->
                            stringResource(R.string.ui_tile_picker_empty_media)
                        TilePickerCategory.FEATURES ->
                            stringResource(R.string.ui_tile_picker_empty_features)
                    }
                } else {
                    stringResource(R.string.ui_tile_picker_empty_search)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = LocalArgosyTheme.current.textDim,
                modifier = Modifier.padding(Dimens.spacingMd)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f, fill = false),
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
        if (canDeletePage) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = Dimens.spacingXs),
                color = LocalArgosyTheme.current.hairlineLow
            )
            TileDangerRow(
                label = stringResource(R.string.ui_tile_picker_delete_page),
                isFocused = focusIndex >= entries.size,
                onClick = onDeletePage
            )
        }
    }
}

/**
 * The destructive footer of a tile modal. Kept visually apart from the rows above it so the action
 * that throws work away never sits in the same run as the ones that make it.
 */
@Composable
private fun TileDangerRow(
    label: String,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isFocused) lerp(theme.destructive, Color.White, 0.45f) else theme.destructive,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .argosyFocusIndicators(
                focused = isFocused,
                indicators = FocusIndicators.ListRow,
                shape = shape
            )
            .clickableNoFocus(onClick = onClick)
            .padding(Dimens.spacingSm)
    )
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
            val poster = entry.posterUrl?.takeIf { it.isNotBlank() }
            when {
                appIcon != null -> AsyncImage(
                    model = appIcon,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(Dimens.spacingXs)
                )
                poster != null -> AsyncImage(
                    model = poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
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
 * The tabs across the top of the picker. They are a readout rather than a control: the bumpers move
 * between them, which keeps the modal's only focusable list the entries themselves.
 */
@Composable
private fun TilePickerTabs(
    category: TilePickerCategory,
    categories: List<TilePickerCategory>,
    onSelectCategory: (TilePickerCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusSm)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        categories.forEach { entry ->
            val isCurrent = entry == category
            Text(
                text = stringResource(entry.labelRes).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = if (isCurrent) theme.focusAccent else theme.textDim,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .clip(shape)
                    .background(if (isCurrent) theme.surfaceRaised else Color.Transparent)
                    .clickableNoFocus { onSelectCategory(entry) }
                    .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs)
            )
        }
    }
}
