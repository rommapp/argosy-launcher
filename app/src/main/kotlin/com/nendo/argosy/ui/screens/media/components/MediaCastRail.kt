package com.nendo.argosy.ui.screens.media.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.screens.media.MediaCastUi
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * Who is in this title, in the billing order the server sent.
 *
 * Nothing here is a destination -- there is no screen for a person -- so a press is not offered and
 * focus exists to walk the rail and read the roles, which are what the names alone do not say.
 */
@Composable
fun MediaCastRail(
    cast: List<MediaCastUi>,
    focusedIndex: Int,
    isSectionFocused: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    FocusedScroll(listState = listState, focusedIndex = focusedIndex)

    LazyRow(
        state = listState,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = Dimens.spacingLg
        )
    ) {
        itemsIndexed(items = cast, key = { _, person -> person.personId }) { index, person ->
            MediaCastTile(
                person = person,
                isFocused = isSectionFocused && index == focusedIndex,
                onClick = { onSelect(index) }
            )
        }
    }
}

@Composable
private fun MediaCastTile(
    person: MediaCastUi,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    Column(
        modifier = Modifier
            .width(Dimens.mediaCastTileWidth)
            .clickableNoFocus(onClick = onClick)
            .padding(vertical = Dimens.spacingXs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.mediaCastPortraitSize)
                .clip(CircleShape)
                .argosyFocusIndicators(
                    focused = isFocused,
                    indicators = FocusIndicators.Tile,
                    shape = CircleShape
                )
                .background(theme.surfaceRaised),
            contentAlignment = Alignment.Center
        ) {
            if (person.imageUrl.isNotEmpty()) {
                AsyncImage(
                    model = person.imageUrl,
                    contentDescription = person.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(Dimens.mediaCastPortraitSize).clip(CircleShape)
                )
            } else {
                Text(
                    text = person.initials(),
                    style = MaterialTheme.typography.titleMedium,
                    color = theme.textMute
                )
            }
        }

        Text(
            text = person.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (isFocused) theme.textPrimary else theme.textDim,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        person.role?.let { role ->
            Text(
                text = role,
                style = MaterialTheme.typography.labelSmall,
                color = theme.textMute,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun MediaCastUi.initials(): String = name
    .split(" ")
    .filter { it.isNotBlank() }
    .take(2)
    .map { it.first().uppercaseChar() }
    .joinToString("")
