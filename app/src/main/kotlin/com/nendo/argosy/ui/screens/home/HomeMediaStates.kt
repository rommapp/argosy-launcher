package com.nendo.argosy.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nendo.argosy.ui.screens.media.components.MediaMessageState
import com.nendo.argosy.ui.theme.Dimens

/**
 * What a media row says when it has nothing to show. The tiles themselves ride the same renderers
 * as the game rows, so these are all that is left of media's own presentation on home: an empty row
 * is worth saying out loud, since "nothing up next" is an answer and a row that quietly vanished
 * would read as media being broken.
 *
 * A library says something different again: an empty one is far more likely to be a library that has
 * not been read yet than a library the server is genuinely holding nothing in, so it offers the
 * refresh that would fill it.
 */
@Composable
internal fun MediaRowEmptyState(row: HomeRow, modifier: Modifier = Modifier) {
    val title = when (row) {
        HomeRow.NextUp -> "Nothing up next"
        HomeRow.ContinueWatching -> "Nothing to continue"
        else -> "Nothing in this library"
    }
    val message = when (row) {
        HomeRow.NextUp -> "Finish an episode and the next one shows up here."
        HomeRow.ContinueWatching -> "Anything you stop part way through shows up here."
        else -> "Refresh to fetch this library from your media server."
    }
    MediaMessageState(
        icon = Icons.Outlined.Inbox,
        title = title,
        message = message,
        modifier = modifier
    )
}

@Composable
internal fun MediaRowLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().height(Dimens.mediaPosterHeight),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(Dimens.iconXl),
            color = MaterialTheme.colorScheme.onSurface,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
        )
    }
}
