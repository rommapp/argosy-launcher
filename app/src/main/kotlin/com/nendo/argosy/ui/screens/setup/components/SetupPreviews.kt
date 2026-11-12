package com.nendo.argosy.ui.screens.setup.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.nendo.argosy.data.preferences.BoxArtShape
import com.nendo.argosy.data.preferences.LibraryLayout
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

private const val PREVIEW_GRID_COVERS = 4
private const val PREVIEW_LIST_ROWS = 3
private const val PREVIEW_TITLE_SHARE = 0.6f
private const val PREVIEW_DETAIL_SHARE = 0.35f

/**
 * Sample covers in the chosen library arrangement and box art shape. Showcase only: it takes no
 * input and is never a focus stop.
 */
@Composable
fun LibraryLookPreview(layout: LibraryLayout, shape: BoxArtShape, modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    val cover = RoundedCornerShape(Dimens.radiusSm)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.radiusPanel))
            .background(theme.surfaceRaised)
            .padding(Dimens.spacingMd)
            .clearAndSetSemantics {},
    ) {
        when (layout) {
            LibraryLayout.GRID -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                verticalAlignment = Alignment.Bottom,
            ) {
                repeat(PREVIEW_GRID_COVERS) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(shape.aspectRatio)
                            .clip(cover)
                            .background(theme.surfaceElevated),
                    )
                }
            }
            LibraryLayout.LIST -> Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
                repeat(PREVIEW_LIST_ROWS) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .height(Dimens.timelineCoverHeight)
                                .aspectRatio(shape.aspectRatio)
                                .clip(cover)
                                .background(theme.surfaceElevated),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
                        ) {
                            PreviewBar(PREVIEW_TITLE_SHARE)
                            PreviewBar(PREVIEW_DETAIL_SHARE)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewBar(share: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth(share)
            .height(Dimens.spacingSm)
            .clip(RoundedCornerShape(Dimens.radiusPill))
            .background(LocalArgosyTheme.current.surfaceElevated),
    )
}
