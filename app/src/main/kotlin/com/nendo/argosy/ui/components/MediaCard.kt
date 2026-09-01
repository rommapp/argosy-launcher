package com.nendo.argosy.ui.components

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nendo.argosy.ui.common.AlwaysCrossfadeFactory
import com.nendo.argosy.ui.components.boxart.UnbadgedBoxArtBorder
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.screens.home.GameDownloadIndicator
import com.nendo.argosy.ui.screens.home.HomeMediaUi
import com.nendo.argosy.ui.screens.media.components.MediaDownloadBadge
import com.nendo.argosy.ui.screens.media.components.MediaProgressBar
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.generated.ComponentDefaults

/**
 * The shape a poster is drawn at, read off the poster tokens so the tile and the media screens that
 * reserve room for one cannot drift apart.
 */
val mediaPosterAspectRatio: Float
    @Composable get() = Dimens.mediaPosterWidth / Dimens.mediaPosterHeight

/**
 * The focused treatment for a media tile. Growth is deliberately not part of it: how far a focused
 * item grows is a layout setting, and the layout applies it through [MediaCard]'s focus scale, so a
 * lift baked into the indicator would sit on top of the reader's choice rather than obey it.
 */
private val MEDIA_TILE_FOCUS = FocusIndicators(halo = true)

private const val MEDIA_LABEL_SCRIM_ALPHA = 0.85f
private const val MEDIA_LABEL_RESTING_ALPHA = 0.72f

/**
 * How wide a tile has to be, against the poster token, before the episode line is worth drawing. A
 * layout is free to shrink its resting tiles to a fraction of a poster, and two lines of text over
 * one of those is not a caption, it is a smear across the artwork.
 */
private const val MEDIA_LABEL_MIN_WIDTH_FRACTION = 0.7f

private const val MEDIA_STUB_TITLE_MAX_LINES = 3

/**
 * One media tile, drawn to the same contract as a game card: the footprint arrives on the modifier
 * and the tile fills exactly that, which is what lets a rail or a grid place media by the same rules
 * it places games by.
 *
 * The episode line sits over the foot of the artwork rather than beneath it. A caption below the
 * picture would make the tile taller than the slot the layout measured, and geometry only media has
 * is how media ended up outside the layout system to begin with.
 */
@Composable
fun MediaCard(
    media: HomeMediaUi,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    focusScale: Float = ComponentDefaults.Focus.scaleFocused,
    scalePivotY: Float = 0.5f,
    scaleOverride: Float? = null,
    alphaOverride: Float? = null,
    downloadIndicator: GameDownloadIndicator = GameDownloadIndicator.NONE,
    onPosterLoaded: ((itemId: String, bitmap: Bitmap) -> Unit)? = null
) {
    val theme = LocalArgosyTheme.current

    BoxWithConstraints(
        modifier = modifier.boxArtFrame(
            isFocused = isFocused,
            focusScale = focusScale,
            scalePivotY = scalePivotY,
            scaleOverride = scaleOverride,
            alphaOverride = alphaOverride,
            artworkGradient = media.gradientColors,
            background = SolidColor(theme.surfaceRaised)
        )
    ) {
        val showLabel = maxWidth >= Dimens.mediaPosterWidth * MEDIA_LABEL_MIN_WIDTH_FRACTION
        var artworkFailed by remember(media.posterUrl) { mutableStateOf(false) }
        if (media.posterUrl.isBlank() || artworkFailed) {
            MediaPosterStub(title = media.title, showTitle = !showLabel)
        } else if (downloadIndicator.isShown) {
            DownloadProgressCover(
                imageData = media.posterUrl,
                progress = downloadIndicator.progress,
                badgeSize = Dimens.iconLg,
                paused = downloadIndicator.isPaused,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val context = LocalContext.current
            val posterRequest = remember(context, media.posterUrl) {
                ImageRequest.Builder(context)
                    .data(media.posterUrl)
                    .transitionFactory(AlwaysCrossfadeFactory(ComponentDefaults.MediaCover.crossfadeMs))
                    .build()
            }
            AsyncImage(
                model = posterRequest,
                contentDescription = media.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { state ->
                    val bitmap = (state.result.drawable as? BitmapDrawable)?.bitmap
                    if (bitmap != null) onPosterLoaded?.invoke(media.itemId, bitmap)
                },
                onError = { artworkFailed = true }
            )
        }
        UnbadgedBoxArtBorder(
            imageModel = media.posterUrl.takeIf { it.isNotBlank() && !artworkFailed },
            gradientColors = media.gradientColors,
            isFocused = isFocused
        )
        if (showLabel) {
            MediaTileLabel(
                media = media,
                isFocused = isFocused,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        MediaDownloadBadge(
            availability = media.availability,
            size = Dimens.iconSm,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(Dimens.spacingXs)
        )
        if (media.progressFraction > 0f) {
            MediaProgressBar(
                fraction = media.progressFraction,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

/**
 * What a tile shows when its poster does not arrive -- because the server holds no artwork for the
 * item, or because the request for it failed.
 *
 * Games answer this case with the title drawn across the cover, and media has the same problem to
 * solve: without it the tile is an unidentifiable hole in the row, indistinguishable from the one
 * next to it. The title is drawn only when the tile is too narrow for its own caption, so a tile that
 * already names itself along the foot does not name itself twice.
 */
@Composable
private fun MediaPosterStub(title: String, showTitle: Boolean) {
    val theme = LocalArgosyTheme.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.surfaceRaised)
            .padding(Dimens.spacingSm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Movie,
            contentDescription = null,
            tint = theme.textMute,
            modifier = Modifier.size(Dimens.iconLg)
        )
        if (showTitle) {
            Spacer(modifier = Modifier.height(Dimens.spacingXs))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = theme.textMute,
                textAlign = TextAlign.Center,
                maxLines = MEDIA_STUB_TITLE_MAX_LINES,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * The show and the episode a press will actually start. Both lines read against the artwork, so they
 * carry their own scrim and their own light text rather than the theme's, the way any caption laid
 * over a picture has to.
 */
@Composable
private fun MediaTileLabel(
    media: HomeMediaUi,
    isFocused: Boolean,
    modifier: Modifier = Modifier
) {
    val titleColor = if (isFocused) Color.White else Color.White.copy(alpha = MEDIA_LABEL_RESTING_ALPHA)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = MEDIA_LABEL_SCRIM_ALPHA)
                    )
                )
            )
            .padding(
                start = Dimens.spacingSm,
                end = Dimens.spacingSm,
                top = Dimens.spacingLg,
                bottom = Dimens.spacingSm
            )
    ) {
        Text(
            text = media.title,
            style = MaterialTheme.typography.bodySmall,
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (media.subtitle != null) {
            Text(
                text = media.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = MEDIA_LABEL_RESTING_ALPHA),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
