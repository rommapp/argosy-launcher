package com.nendo.argosy.ui.screens.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import com.nendo.argosy.ui.components.GridFocusedScroll
import com.nendo.argosy.ui.components.PlatformIconAssets
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.screens.library.LibraryCellUi
import com.nendo.argosy.ui.screens.library.MediaCellKind
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * The chrome the landing draws over its own grid. One row of header - a title and a count - rather
 * than the stacked header the games list needs, so the grid starts a row higher than it used to.
 */
val LibraryPlatformGridHeaderHeight: Dp
    @Composable get() = Dimens.headerHeight

/**
 * The library's landing: every collection the user actually has - platforms first, then the media
 * libraries a signed-in media account can see - one jump from what is inside each.
 *
 * All Games leads rather than being replaced by the grid, so the unfiltered library stays a
 * destination for anyone who wants to browse everything at once.
 *
 * The header is the only thing drawn over the grid - the landing carries no footer, since A, B and
 * the d-pad are all the cursor needs here - so [LibraryPlatformGridHeaderHeight] is reserved twice:
 * once as content padding, which holds the top of the list clear, and once as the scroll inset that
 * keeps the focused cell out from under it on the way up.
 */
@Composable
fun LibraryPlatformGrid(
    cells: List<LibraryCellUi>,
    focusedIndex: Int,
    columns: Int,
    gridState: LazyGridState,
    onCellClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    GridFocusedScroll(
        gridState = gridState,
        focusedIndex = focusedIndex,
        topInset = LibraryPlatformGridHeaderHeight
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns.coerceAtLeast(1)),
        state = gridState,
        contentPadding = PaddingValues(
            start = Dimens.spacingMd,
            end = Dimens.spacingMd,
            top = LibraryPlatformGridHeaderHeight,
            bottom = Dimens.spacingXl
        ),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        modifier = modifier.fillMaxSize()
    ) {
        itemsIndexed(
            items = cells,
            key = { _, cell -> cell.key }
        ) { index, cell ->
            PlatformCell(
                cell = cell,
                isFocused = index == focusedIndex,
                onClick = { onCellClick(index) }
            )
        }
    }
}

/**
 * A collection badge: the mark, its name, and one line of small print.
 *
 * The cell takes the height its content asks for instead of squaring off, because the square spent
 * most of a row on air and this screen is worth having only while a platform stays one jump away.
 * Both text lines are capped at one line, so the icons sit on one baseline across a row and the
 * names sit on the next however much any one platform is called.
 */
@Composable
private fun PlatformCell(
    cell: LibraryCellUi,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(Dimens.radiusLg)
    val context = LocalContext.current
    val iconUri = remember(cell.slug) {
        if (cell.slug.isBlank()) null else PlatformIconAssets.resolveAssetUri(context, cell.slug)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .argosyFocusIndicators(
                focused = isFocused,
                indicators = FocusIndicators.Ring,
                shape = shape
            )
            .clip(shape)
            .background(
                if (isFocused) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                }
            )
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.spacingXs, vertical = Dimens.spacingSm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PlatformCellIcon(
            cell = cell,
            iconUri = iconUri,
            isFocused = isFocused
        )

        Spacer(modifier = Modifier.height(Dimens.spacingXs))

        Text(
            text = cell.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (isFocused) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Text(
            text = cell.metaLine,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * A cell's mark, in the order it can be trusted: the bundled slug asset, then the logo the server
 * sent, then the short name as text. A cell that draws nothing would be unrecognisable, which is the
 * one thing this grid exists to avoid.
 *
 * A media library takes a tinted vector for what it holds rather than the poster its server offers.
 * The poster is a photograph, and at this size it would read as a different kind of object sitting
 * among flat monochrome marks - the same reason All Games draws a glyph instead of a cover collage.
 */
@Composable
private fun PlatformCellIcon(
    cell: LibraryCellUi,
    iconUri: String?,
    isFocused: Boolean
) {
    val tint = if (isFocused) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val mediaIcon = when (cell.mediaKind) {
        MediaCellKind.MOVIES -> Icons.Default.Movie
        MediaCellKind.SHOWS -> Icons.Default.Tv
        null -> null
    }

    when {
        cell.isAllGames -> Icon(
            imageVector = Icons.Default.GridView,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(Dimens.iconXl)
        )
        mediaIcon != null -> Icon(
            imageVector = mediaIcon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(Dimens.iconXl)
        )
        iconUri != null -> AsyncImage(
            model = iconUri,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(Dimens.iconXl)
        )
        cell.logoPath != null -> AsyncImage(
            model = cell.logoPath,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(Dimens.iconXl)
        )
        else -> Box(
            modifier = Modifier.size(Dimens.iconXl),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = cell.name.take(3).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = tint,
                maxLines = 1
            )
        }
    }
}

@Composable
fun LibraryPlatformGridEmpty(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No platforms yet",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            Text(
                text = "Sync your library from Rom Manager in Settings",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}
