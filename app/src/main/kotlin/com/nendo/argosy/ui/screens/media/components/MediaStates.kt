package com.nendo.argosy.ui.screens.media.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.nendo.argosy.R
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

@Composable
fun MediaMessageState(
    icon: ImageVector,
    title: String,
    message: String?,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    Column(
        modifier = modifier.fillMaxSize().padding(Dimens.spacingXl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = theme.textMute,
            modifier = Modifier.size(Dimens.iconXl)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = theme.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Dimens.spacingMd)
        )
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textDim,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Dimens.spacingSm)
            )
        }
    }
}

@Composable
fun MediaEmptyState(modifier: Modifier = Modifier) {
    MediaMessageState(
        icon = Icons.Outlined.Inbox,
        title = stringResource(R.string.media_empty_state_title),
        message = stringResource(R.string.media_empty_state_message),
        modifier = modifier
    )
}

@Composable
fun MediaErrorState(message: String, modifier: Modifier = Modifier) {
    MediaMessageState(
        icon = Icons.Outlined.CloudOff,
        title = stringResource(R.string.media_error_state_title),
        message = message,
        modifier = modifier
    )
}

@Composable
fun MediaSignedOutState(modifier: Modifier = Modifier) {
    MediaMessageState(
        icon = Icons.Outlined.Movie,
        title = stringResource(R.string.media_signed_out_title),
        message = stringResource(R.string.media_signed_out_message),
        modifier = modifier
    )
}

@Composable
fun MediaLibrarySkeleton(modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    val placeholders = remember { List(SKELETON_TILE_COUNT) { it } }
    LazyRow(
        modifier = modifier.fillMaxWidth().padding(Dimens.spacingLg),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
    ) {
        items(placeholders, key = { it }) {
            Box(
                modifier = Modifier
                    .width(Dimens.mediaPosterWidth)
                    .height(Dimens.mediaPosterHeight)
                    .clip(RoundedCornerShape(Dimens.radiusMd))
                    .background(theme.surfaceRaised)
            )
        }
    }
}

@Composable
fun MediaDetailSkeleton(modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    Row(
        modifier = modifier.fillMaxWidth().padding(Dimens.spacingLg),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg)
    ) {
        Box(
            modifier = Modifier
                .width(Dimens.mediaPosterWidth)
                .height(Dimens.mediaPosterHeight)
                .clip(RoundedCornerShape(Dimens.radiusMd))
                .background(theme.surfaceRaised)
        )
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
            repeat(SKELETON_LINE_COUNT) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Dimens.menuRowHeight)
                        .clip(RoundedCornerShape(Dimens.radiusSm))
                        .background(theme.surfaceRaised)
                )
            }
        }
    }
}

private const val SKELETON_TILE_COUNT = 6
private const val SKELETON_LINE_COUNT = 3
