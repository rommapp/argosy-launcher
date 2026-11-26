package com.nendo.argosy.ui.screens.music

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.MusicArt
import com.nendo.argosy.domain.model.MusicColumn
import com.nendo.argosy.domain.model.MusicColumnKind
import com.nendo.argosy.ui.components.nowplaying.artScrimColor
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.theme.reducedMotion

private const val MAX_SLEEVES = 3

@Composable
internal fun BoxScope.MusicSideColumnArt(column: MusicColumn) {
    val model = rememberMusicArtModel(column.photo ?: column.heroArt ?: column.sleeves.firstOrNull())
    if (model == null) {
        MusicNoArtGround(column = column, large = false)
        return
    }
    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.matchParentSize()
    )
}

@Composable
internal fun BoxScope.MusicFocusedColumnArt(column: MusicColumn, stack: MusicStackUi?) {
    var lastIndex by remember(column.key) { mutableIntStateOf(stack?.index ?: 0) }
    val flipDirection = when {
        stack == null || stack.index == lastIndex -> 1
        stack.index == (lastIndex + 1).mod(stack.albums.size) -> 1
        else -> -1
    }
    SideEffect { lastIndex = stack?.index ?: 0 }
    val photo = column.photo.takeIf { column.kind == MusicColumnKind.ARTIST && stack == null }
    if (photo != null) {
        AsyncImage(
            model = rememberMusicArtModel(photo),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )
        return
    }
    val (sleeves, titles) = remember(column, stack) {
        val shownAlbums = stack?.albums?.drop(stack.index)?.take(MAX_SLEEVES)?.filter { it.art != null }
        val sleeves = shownAlbums?.mapNotNull { it.art } ?: column.sleeves.take(MAX_SLEEVES)
        val titles = shownAlbums?.map { it.title } ?: column.sleeveTitles
        sleeves to titles
    }
    val hero = stack?.front?.art ?: column.heroArt ?: column.sleeves.firstOrNull()
    if (hero == null) {
        MusicNoArtGround(column = column, large = true)
        return
    }
    if (column.kind == MusicColumnKind.ALBUM) {
        AsyncImage(
            model = rememberMusicArtModel(hero),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )
        return
    }
    BlurredHero(hero)
    if (sleeves.isNotEmpty()) {
        MusicSleeveStack(
            sleeves = sleeves,
            titles = titles,
            hiddenCount = if (stack == null) column.hiddenAlbumCount else 0,
            flipDirection = flipDirection,
            positionLabel = stack?.let {
                stringResource(R.string.music_library_contents_position, it.index + 1, it.albums.size)
            }
        )
    }
}

@Composable
internal fun BoxScope.BlurredHero(art: MusicArt) {
    val filter = remember {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(ComponentDefaults.MusicColumns.heroSaturation) })
    }
    AsyncImage(
        model = rememberMusicArtModel(art),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        colorFilter = filter,
        modifier = Modifier
            .matchParentSize()
            .blur(ComponentDefaults.MusicColumns.heroBlurDp.dp)
    )
    Box(
        Modifier
            .matchParentSize()
            .background(artScrimColor())
    )
}

@Composable
internal fun BoxScope.MusicSleeveStack(
    sleeves: List<MusicArt>,
    titles: List<String>,
    hiddenCount: Int,
    positionLabel: String? = null,
    flipDirection: Int = 1,
    sizeRatio: Float = ComponentDefaults.MusicColumns.sleeveSizeRatio,
    centerBias: Float = ComponentDefaults.MusicColumns.sleeveCenterBiasRatio
) {
    val front = sleeves.firstOrNull() ?: return
    val reduced = reducedMotion()
    val flipMs = ComponentDefaults.MusicColumns.flipMs
    BoxWithConstraints(Modifier.matchParentSize()) {
        val sleeveSize = min(maxWidth, maxHeight) * sizeRatio
        val step = sleeveSize * ComponentDefaults.MusicColumns.sleeveStackOffsetRatio
        Box(
            Modifier
                .align(BiasAlignment(centerBias, 0f))
                .size(sleeveSize)
        ) {
            for (index in sleeves.indices.reversed()) {
                if (index == 0) continue
                Crossfade(
                    targetState = sleeves[index],
                    animationSpec = tween(if (reduced) 0 else flipMs),
                    modifier = Modifier
                        .matchParentSize()
                        .offset(x = -step * index, y = -step * index),
                    label = "music-back-sleeve"
                ) { art ->
                    MusicSleeve(
                        art = art,
                        title = titles.getOrNull(index),
                        dim = ComponentDefaults.MusicColumns.sleeveBackDimAlpha,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            AnimatedContent(
                targetState = front,
                contentKey = { it.stableKey },
                transitionSpec = {
                    if (reduced) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        val toward = if (flipDirection >= 0) 1 else -1
                        val easing = Motion.argosyEase
                        val enter = fadeIn(tween(flipMs, easing = easing)) +
                            slideInVertically(tween(flipMs, easing = easing)) { -toward * it / FLIP_TRAVEL_DIVISOR } +
                            scaleIn(tween(flipMs, easing = easing), initialScale = FLIP_ENTER_SCALE)
                        val exit = fadeOut(tween(flipMs * 2 / 3, easing = easing)) +
                            slideOutVertically(tween(flipMs, easing = easing)) { toward * it / FLIP_TRAVEL_DIVISOR }
                        enter togetherWith exit using SizeTransform(clip = false)
                    }
                },
                modifier = Modifier.matchParentSize(),
                label = "music-front-sleeve"
            ) { art ->
                FrontSleeve(art = art, title = titles.firstOrNull(), reduced = reduced)
            }
            val chip = positionLabel
                ?: if (hiddenCount > 0) stringResource(R.string.music_library_more_albums, hiddenCount) else null
            if (chip != null) {
                StackChip(
                    text = chip,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(Dimens.spacingXs)
                )
            }
        }
    }
}

@Composable
private fun FrontSleeve(art: MusicArt, title: String?, reduced: Boolean) {
    val slide = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(reduced) {
        if (reduced) {
            slide.snapTo(1f)
        } else {
            slide.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = ComponentDefaults.MusicColumns.recordSlideMs,
                    delayMillis = ComponentDefaults.MusicColumns.recordSlideDelayMs,
                    easing = Motion.argosyEase
                )
            )
        }
    }
    Box(Modifier.fillMaxSize()) {
        MusicRecord(
            art = art,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    translationX = slide.value * size.width * ComponentDefaults.MusicColumns.recordSlideRatio
                }
        )
        MusicSleeve(art = art, title = title, dim = 0f, modifier = Modifier.matchParentSize())
    }
}

private val MusicArt.stableKey: String
    get() = when (this) {
        is MusicArt.Remote -> cacheKey
        is MusicArt.Local -> path
    }

private const val FLIP_TRAVEL_DIVISOR = 6
private const val FLIP_ENTER_SCALE = 0.94f

@Composable
internal fun MusicSleeve(art: MusicArt?, title: String?, dim: Float, modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusSm)
    Box(
        modifier
            .clip(shape)
            .background(theme.surfaceRaised)
            .border(Dimens.borderThin, theme.hairlineHigh, shape)
    ) {
        val model = rememberMusicArtModel(art)
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
        if (dim > 0f) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = dim)))
        }
    }
}

@Composable
private fun MusicRecord(art: MusicArt, modifier: Modifier = Modifier) {
    val hole = LocalArgosyTheme.current.surfaceBase
    val groove = Dimens.borderThin
    val labelRatio = ComponentDefaults.MusicColumns.recordLabelRatio
    val grooveCount = ComponentDefaults.MusicColumns.recordGrooveCount
    val grooveColor = Color.White.copy(alpha = ComponentDefaults.MusicColumns.recordGrooveAlpha)
    val sheen = Color.White.copy(alpha = ComponentDefaults.MusicColumns.recordSheenAlpha)
    Box(
        modifier
            .clip(CircleShape)
            .drawWithContent {
                val radius = size.minDimension / 2f
                drawCircle(Color.Black, radius = radius)
                for (ring in 1..grooveCount) {
                    val fraction = labelRatio + (1f - labelRatio) * ring / (grooveCount + 1)
                    drawCircle(grooveColor, radius = radius * fraction, style = Stroke(width = groove.toPx()))
                }
                drawCircle(
                    brush = Brush.linearGradient(listOf(sheen, Color.Transparent, sheen)),
                    radius = radius
                )
                drawContent()
                drawCircle(hole, radius = radius * ComponentDefaults.MusicColumns.recordHoleRatio)
            }
    ) {
        AsyncImage(
            model = rememberMusicArtModel(art),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxSize(labelRatio)
                .clip(CircleShape)
        )
    }
}

@Composable
private fun StackChip(text: String, modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    Box(
        modifier
            .clip(RoundedCornerShape(Dimens.radiusPill))
            .background(theme.surfaceElevated)
            .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = theme.textPrimary
        )
    }
}
