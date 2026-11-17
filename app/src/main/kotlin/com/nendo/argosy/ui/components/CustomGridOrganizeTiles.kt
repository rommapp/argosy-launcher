package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.domain.model.FolderCoverStyle
import com.nendo.argosy.domain.model.TileCoverScale
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.clickableNoFocus

private const val MOSAIC_SIDE = 2

@Composable
internal fun DecorTileBox(
    placement: Modifier,
    decor: DecorTileContent,
    shape: Shape,
    isFocused: Boolean,
    isOverlapped: Boolean,
    editModeLabel: String?,
    interactive: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?
) {
    val theme = LocalArgosyTheme.current
    val touch = when {
        !interactive -> Modifier
        onLongClick == null -> Modifier.clickableNoFocus(onClick = onClick)
        else -> Modifier.clickableNoFocus(onClick = onClick, onLongClick = onLongClick)
    }
    Box(modifier = placement.alpha(if (isOverlapped) OVERLAPPED_ALPHA else 1f)) {
        val frame = Modifier
            .fillMaxSize()
            .then(if (decor.isSticker) Modifier else Modifier.clip(shape))
            .argosyFocusIndicators(focused = isFocused, indicators = FocusIndicators.Ring, shape = shape)
            .then(touch)
        Box(modifier = frame, contentAlignment = Alignment.Center) {
            val path = decor.path
            when {
                path == null -> if (interactive) {
                    Icon(
                        imageVector = Icons.Outlined.BrokenImage,
                        contentDescription = null,
                        tint = theme.textDim,
                        modifier = Modifier.size(Dimens.iconMd)
                    )
                }
                decor.isSticker -> StickerImage(
                    path = path,
                    outline = decor.outline,
                    modifier = Modifier.fillMaxSize()
                )
                else -> coil.compose.AsyncImage(
                    model = rememberFileImageModel(path),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        if (editModeLabel != null && isFocused) {
            TileModeTab(label = editModeLabel, modifier = Modifier.align(Alignment.TopEnd))
        }
    }
}

@Composable
internal fun FolderTileBox(
    placement: Modifier,
    label: String,
    folder: FolderTileContent,
    coverScale: TileCoverScale,
    isFocused: Boolean,
    isOverlapped: Boolean,
    focusScale: Float,
    editModeLabel: String?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?
) {
    val theme = LocalArgosyTheme.current
    val touch = if (onLongClick == null) {
        Modifier.clickableNoFocus(onClick = onClick)
    } else {
        Modifier.clickableNoFocus(onClick = onClick, onLongClick = onLongClick)
    }
    val picture = folder.picturePath?.takeIf { folder.coverStyle != FolderCoverStyle.MOSAIC }
    Box(modifier = placement) {
        when {
            picture != null && folder.coverStyle == FolderCoverStyle.STICKER -> FolderStickerFace(
                path = picture,
                label = label.takeIf { folder.showName },
                folder = folder,
                isFocused = isFocused,
                isOverlapped = isOverlapped,
                touch = touch
            )
            picture != null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .boxArtFrame(
                        isFocused = isFocused,
                        focusScale = focusScale,
                        alphaOverride = if (isOverlapped) OVERLAPPED_ALPHA else null,
                        background = SolidColor(theme.surfaceRaised)
                    )
                    .then(touch)
            ) {
                coil.compose.AsyncImage(
                    model = rememberFileImageModel(picture),
                    contentDescription = null,
                    contentScale = if (coverScale == TileCoverScale.FIT) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (folder.isAuto) AutoFolderBadge(Modifier.align(Alignment.TopStart).padding(Dimens.spacingXs))
                if (folder.showName) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = theme.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(theme.surfaceBase.copy(alpha = ComponentDefaults.FrostedSurface.fillAlpha))
                            .padding(Dimens.spacingXs)
                    )
                }
            }
            else -> FolderMosaicFace(
                label = label.takeIf { folder.showName },
                folder = folder,
                coverScale = coverScale,
                isFocused = isFocused,
                isOverlapped = isOverlapped,
                focusScale = focusScale,
                touch = touch
            )
        }
        if (editModeLabel != null && isFocused) {
            TileModeTab(label = editModeLabel, modifier = Modifier.align(Alignment.TopEnd))
        }
    }
}

@Composable
private fun FolderMosaicFace(
    label: String?,
    folder: FolderTileContent,
    coverScale: TileCoverScale,
    isFocused: Boolean,
    isOverlapped: Boolean,
    focusScale: Float,
    touch: Modifier
) {
    val theme = LocalArgosyTheme.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .boxArtFrame(
                isFocused = isFocused,
                focusScale = focusScale,
                alphaOverride = if (isOverlapped) OVERLAPPED_ALPHA else null,
                background = SolidColor(theme.surfaceRaised)
            )
            .then(touch)
            .padding(Dimens.spacingXs),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            if (folder.coverPaths.isEmpty()) {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = theme.textDim,
                    modifier = Modifier.size(Dimens.iconXl)
                )
            } else {
                FolderMosaic(coverPaths = folder.coverPaths, coverScale = coverScale)
            }
            if (folder.isAuto) AutoFolderBadge(Modifier.align(Alignment.TopStart))
        }
        if (label != null) FolderTileLabel(label)
    }
}

@Composable
private fun FolderStickerFace(
    path: String,
    label: String?,
    folder: FolderTileContent,
    isFocused: Boolean,
    isOverlapped: Boolean,
    touch: Modifier
) {
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .alpha(if (isOverlapped) OVERLAPPED_ALPHA else 1f)
            .argosyFocusIndicators(focused = isFocused, indicators = FocusIndicators.Ring, shape = shape)
            .then(touch),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            StickerImage(path = path, outline = folder.outline, modifier = Modifier.fillMaxSize())
            if (folder.isAuto) AutoFolderBadge(Modifier.align(Alignment.TopStart))
        }
        if (label != null) FolderTileLabel(label)
    }
}

@Composable
private fun FolderTileLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = LocalArgosyTheme.current.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun AutoFolderBadge(modifier: Modifier) {
    val theme = LocalArgosyTheme.current
    Icon(
        imageVector = Icons.Filled.AutoAwesome,
        contentDescription = null,
        tint = theme.textPrimary,
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(theme.surfaceBase)
            .padding(Dimens.spacingXs)
            .size(Dimens.iconSm)
    )
}

@Composable
private fun FolderMosaic(coverPaths: List<String>, coverScale: TileCoverScale) {
    val cellShape = RoundedCornerShape(Dimens.radiusSm)
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        repeat(MOSAIC_SIDE) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
            ) {
                repeat(MOSAIC_SIDE) { column ->
                    val path = coverPaths.getOrNull(row * MOSAIC_SIDE + column)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(cellShape)
                            .background(LocalArgosyTheme.current.surfaceBase)
                    ) {
                        if (path != null) {
                            coil.compose.AsyncImage(
                                model = rememberFileImageModel(path),
                                contentDescription = null,
                                contentScale = if (coverScale == TileCoverScale.FIT) {
                                    ContentScale.Fit
                                } else {
                                    ContentScale.Crop
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}
