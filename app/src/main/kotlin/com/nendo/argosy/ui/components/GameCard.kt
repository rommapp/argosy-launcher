package com.nendo.argosy.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.nendo.argosy.data.model.ArtSlot
import com.nendo.argosy.ui.components.boxart.BoxArtGeometry
import com.nendo.argosy.ui.components.boxart.GlassBorderOverlay
import com.nendo.argosy.ui.components.boxart.GlassCombinedShape
import com.nendo.argosy.ui.components.boxart.GradientBorderOverlay
import com.nendo.argosy.ui.components.boxart.GradientMaskShape
import com.nendo.argosy.ui.components.boxart.InnerEffectShape
import com.nendo.argosy.ui.components.boxart.glassColorFilterFor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import coil.compose.AsyncImage
import com.nendo.argosy.data.preferences.BoxArtBorderStyle
import com.nendo.argosy.data.preferences.BoxArtInnerEffect
import com.nendo.argosy.data.preferences.SystemIconPosition
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.screens.home.GameDownloadIndicator
import com.nendo.argosy.ui.screens.home.HomeGameUi
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalBoxArtStyle
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.generated.ComponentDefaults

@Composable
fun GameCard(
    game: HomeGameUi,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    focusScale: Float = ComponentDefaults.Focus.scaleFocused,
    scalePivotY: Float = 0.5f,
    downloadIndicator: GameDownloadIndicator = GameDownloadIndicator.NONE,
    showPlatformBadge: Boolean = true,
    showStatusOverlays: Boolean = true,
    coverPathOverride: String? = null,
    onCoverLoadFailed: ((gameId: Long, failedPath: String) -> Unit)? = null,
    onCoverLoaded: ((gameId: Long, bitmap: Bitmap) -> Unit)? = null,
    scaleOverride: Float? = null,
    alphaOverride: Float? = null,
    saturationOverride: Float? = null,
    useBoxArt: Boolean = false
) {
    val boxArtStyle = LocalBoxArtStyle.current
    val resolvedArt = com.nendo.argosy.ui.common.rememberResolvedArt(game.id)
    val resolvedCoverPath = resolvedArt?.coverPath ?: game.coverPath
    val effectiveCoverPath = (coverPathOverride ?: resolvedCoverPath).orEmpty()
    val coverGradientColors = game.gradientColors

    val saturation by animateFloatAsState(
        targetValue = saturationOverride ?: 1f,
        animationSpec = Motion.focusSpring,
        label = "saturation"
    )
    val saturationColorFilter = if (saturation < 1f) {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(saturation) })
    } else null

    val borderColor = MaterialTheme.colorScheme.primary

    val box3dImagePath = resolvedArt?.box3dPath ?: game.box3dPath
    val spinePath = resolvedArt?.boxSpinePath ?: game.boxSpinePath
    val repairArt = com.nendo.argosy.ui.common.rememberArtRepair()
    var failedRoutes by remember(useBoxArt, spinePath, box3dImagePath, effectiveCoverPath) {
        mutableStateOf(emptySet<BoxArtRoute>())
    }
    val route = boxArtRoutes(useBoxArt, spinePath, box3dImagePath, effectiveCoverPath).firstWorking(failedRoutes)
    val drawsAs3d = route == BoxArtRoute.SPINE_RENDER || route == BoxArtRoute.BOX_3D_IMAGE

    val spineActiveForBackground = !drawsAs3d && showPlatformBadge &&
        boxArtStyle.platformIndicatorStyle == com.nendo.argosy.data.preferences.PlatformIndicatorStyle.SPINE
    val cardBackgroundBrush: androidx.compose.ui.graphics.Brush = if (drawsAs3d) {
        SolidColor(Color.Transparent)
    } else if (spineActiveForBackground) {
        val accent = boxArtStyle.accentColor
        val secondary = boxArtStyle.secondaryColor
        val fallbackPrimary = accent ?: MaterialTheme.colorScheme.primary
        val fallbackSecondary = secondary ?: fallbackPrimary
        when (boxArtStyle.borderStyle) {
            BoxArtBorderStyle.GRADIENT -> {
                val (a, b) = coverGradientColors ?: (fallbackPrimary to fallbackSecondary)
                androidx.compose.ui.graphics.Brush.linearGradient(listOf(a, b))
            }
            BoxArtBorderStyle.GLASS -> {
                val base = coverGradientColors?.first ?: fallbackPrimary
                val highlight = coverGradientColors?.second ?: base
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(highlight.copy(alpha = 0.85f), base.copy(alpha = 0.6f))
                )
            }
            else -> androidx.compose.ui.graphics.SolidColor(fallbackPrimary)
        }
    } else {
        androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surfaceVariant)
    }

    BoxWithConstraints(
        modifier = modifier
            .boxArtFrame(
                isFocused = isFocused,
                focusScale = focusScale,
                scalePivotY = scalePivotY,
                scaleOverride = scaleOverride,
                alphaOverride = alphaOverride,
                artworkGradient = coverGradientColors,
                background = cardBackgroundBrush,
                drawBorder = !spineActiveForBackground && route != BoxArtRoute.BOX_3D_IMAGE
            )
    ) {
        val spineBlurredBackdrop = spineActiveForBackground &&
            boxArtStyle.borderStyle == BoxArtBorderStyle.GLASS &&
            effectiveCoverPath.isNotEmpty()
        if (spineBlurredBackdrop) {
            AsyncImage(
                model = rememberFileImageModel(effectiveCoverPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(16.dp)
            )
        }

        val density = LocalDensity.current
        val outerCornerRadiusPx = with(density) { boxArtStyle.cornerRadiusDp.toPx() }
        val frameWidthPx = with(density) { boxArtStyle.borderThicknessDp.toPx() }
        val oneDpPx = with(density) { 1.dp.toPx() }
        val useGlassBorder = !drawsAs3d && !spineActiveForBackground &&
            isFocused && boxArtStyle.borderStyle == BoxArtBorderStyle.GLASS
        val useGradientBorder = !drawsAs3d && !spineActiveForBackground &&
            isFocused && boxArtStyle.borderStyle == BoxArtBorderStyle.GRADIENT

        val gradientColors = game.gradientColors
        val hasGradientColors = gradientColors != null
        val accentPair = (boxArtStyle.accentColor ?: borderColor).let { accent ->
            accent to (boxArtStyle.secondaryColor ?: accent)
        }
        val borderGradient = gradientColors ?: accentPair
        val gradientBorderProgress = if (useGradientBorder) 1f else 0f

        val cardWidthDp = this@BoxWithConstraints.maxWidth
        val baseWidthDp = 150.dp
        val baseFontSizeSp = 11f
        val baseHorizontalPaddingDp = 4.dp
        val baseVerticalPaddingDp = 2.dp
        val badgeScale = (cardWidthDp / baseWidthDp).coerceIn(0.5f, 2f)
        val scaledCornerRadius = boxArtStyle.cornerRadiusDp * badgeScale
        val userPadding = boxArtStyle.systemIconPaddingDp
        val borderPadding = boxArtStyle.borderThicknessDp * 1.5f
        val horizontalPadding = (baseHorizontalPaddingDp + userPadding + borderPadding) * badgeScale
        val verticalPadding = (baseVerticalPaddingDp + userPadding / 2 + borderPadding / 2) * badgeScale

        val displayName = if (showPlatformBadge) {
            platformBadgeLabel(game.platformSlug, game.platformSlug).take(8)
        } else {
            ""
        }
        val fontSizePx = with(density) { (baseFontSizeSp * badgeScale).dp.toPx() }
        val estimatedTextWidthPx = displayName.length * fontSizePx * 0.7f
        val platformContent = boxArtStyle.platformIndicatorContent
        val iconExtraPx = when (platformContent) {
            com.nendo.argosy.data.preferences.PlatformIndicatorContent.ICON -> fontSizePx * 1.5f
            com.nendo.argosy.data.preferences.PlatformIndicatorContent.NAME_AND_ICON ->
                fontSizePx * 1.3f + with(density) { 6.dp.toPx() }
            else -> 0f
        }
        val contentTextPx = if (platformContent == com.nendo.argosy.data.preferences.PlatformIndicatorContent.ICON) 0f else estimatedTextWidthPx
        val badgeWidthPx = with(density) { contentTextPx + iconExtraPx + horizontalPadding.toPx() * 2 }
        val badgeHeightPx = with(density) { fontSizePx + verticalPadding.toPx() * 2 }
        val scaledCornerRadiusPx = with(density) { scaledCornerRadius.toPx() }

        val indicatorActive = showPlatformBadge &&
            boxArtStyle.platformIndicatorStyle != com.nendo.argosy.data.preferences.PlatformIndicatorStyle.OFF
        val spineActive = !drawsAs3d && indicatorActive &&
            boxArtStyle.platformIndicatorStyle == com.nendo.argosy.data.preferences.PlatformIndicatorStyle.SPINE
        // Skip the cover's inner edge effect when the spine container wraps the cover;
        // the stroke at the cover's spine-side edge reads as a hard line between spine and cover.
        val innerEffect = if (spineActive) BoxArtInnerEffect.OFF else boxArtStyle.innerEffect
        val innerEffectWidth = boxArtStyle.innerEffectThicknessPx
        val effectiveBadgePosition = if (indicatorActive &&
            boxArtStyle.platformIndicatorStyle == com.nendo.argosy.data.preferences.PlatformIndicatorStyle.TAB) {
            boxArtStyle.systemIconPosition
        } else {
            SystemIconPosition.OFF
        }
        val geometry = BoxArtGeometry(
            outerCornerRadiusPx = outerCornerRadiusPx,
            frameWidthPx = frameWidthPx,
            oneDpPx = oneDpPx,
            badgeWidthPx = badgeWidthPx,
            badgeHeightPx = badgeHeightPx,
            scaledCornerRadiusPx = scaledCornerRadiusPx,
            innerEffect = innerEffect,
            innerEffectWidth = innerEffectWidth,
            effectiveBadgePosition = effectiveBadgePosition
        )

        val shineTransition = if (innerEffect == BoxArtInnerEffect.SHINE && (useGlassBorder || useGradientBorder)) {
            rememberInfiniteTransition(label = "innerShine")
        } else null
        val sweepOffset by shineTransition?.animateFloat(
            initialValue = -0.5f,
            targetValue = 1.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "shine"
        ) ?: remember { mutableStateOf(0f) }

        val box3dBody: @Composable (@Composable () -> Unit) -> Unit = { art ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                art()
                if (downloadIndicator.isShown) {
                    DownloadProgressBadge(
                        progress = downloadIndicator.progress,
                        badgeSize = Dimens.iconLg,
                        paused = downloadIndicator.isPaused
                    )
                }
            }
        }

        val coverBody: @Composable () -> Unit = {
            when (route) {
                BoxArtRoute.SPINE_RENDER -> box3dBody {
                    Box3dCover(
                        frontPath = effectiveCoverPath,
                        spinePath = spinePath.orEmpty(),
                        isInteractive = false,
                        modifier = Modifier.fillMaxHeight(),
                        onUnavailable = {
                            failedRoutes = failedRoutes + BoxArtRoute.SPINE_RENDER
                            repairArt(game.id, ArtSlot.BOX_SPINE)
                            repairArt(game.id, ArtSlot.COVER)
                        }
                    )
                }
                BoxArtRoute.BOX_3D_IMAGE -> box3dBody {
                    AsyncImage(
                        model = rememberFileImageModel(box3dImagePath),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        colorFilter = saturationColorFilter,
                        modifier = Modifier.fillMaxSize(),
                        onError = {
                            failedRoutes = failedRoutes + BoxArtRoute.BOX_3D_IMAGE
                            repairArt(game.id, ArtSlot.BOX_3D)
                        }
                    )
                }
                BoxArtRoute.FLAT_COVER -> CoverContent(
                    game = game,
                    effectiveCoverPath = effectiveCoverPath,
                    downloadIndicator = downloadIndicator,
                    saturationColorFilter = saturationColorFilter,
                    useGlassBorder = useGlassBorder,
                    glassBorderTintAlpha = boxArtStyle.glassBorderTintAlpha,
                    hasGradientColors = hasGradientColors,
                    gradientColors = gradientColors,
                    borderColor = borderColor,
                    geometry = geometry,
                    sweepOffset = sweepOffset,
                    onCoverLoaded = onCoverLoaded,
                    onCoverLoadFailed = { gameId, path ->
                        failedRoutes = failedRoutes + BoxArtRoute.FLAT_COVER
                        if (path.startsWith("/")) {
                            onCoverLoadFailed?.invoke(gameId, path) ?: repairArt(gameId, ArtSlot.COVER)
                        }
                    }
                )
                BoxArtRoute.TEXT -> StubCover(
                    gameTitle = game.title,
                    useSolidStub = boxArtStyle.borderStyle == BoxArtBorderStyle.SOLID
                )
            }
        }

        if (spineActive) {
            PlatformSpinePlacement(
                platformDisplayName = game.platformSlug,
                platformSlug = game.platformSlug,
                isFocused = isFocused,
                modifier = Modifier.fillMaxSize()
            ) { _ ->
                Box(modifier = Modifier.fillMaxSize()) {
                    coverBody()
                    if (showStatusOverlays) {
                        StatusIndicators(
                            isFavorite = game.isFavorite,
                            isDownloaded = game.isDownloaded
                        )
                    }
                }
            }
        } else {
            coverBody()
        }

        if (gradientBorderProgress > 0f) {
            GradientBorderOverlay(
                imageModel = rememberFileImageModel(effectiveCoverPath),
                gradientColors = borderGradient,
                gradientBorderProgress = gradientBorderProgress,
                geometry = geometry,
                sweepOffset = sweepOffset
            )
        }

        if (indicatorActive &&
            boxArtStyle.platformIndicatorStyle == com.nendo.argosy.data.preferences.PlatformIndicatorStyle.TAB) {
            val badgeAlignment = when (boxArtStyle.systemIconPosition) {
                SystemIconPosition.TOP_LEFT -> Alignment.TopStart
                SystemIconPosition.TOP_RIGHT -> Alignment.TopEnd
                SystemIconPosition.BOTTOM_LEFT -> Alignment.BottomStart
                SystemIconPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
                else -> Alignment.TopStart
            }

            PlatformBadge(
                platformDisplayName = game.platformSlug,
                platformSlug = game.platformSlug,
                cardWidthDp = maxWidth,
                isFocused = isFocused,
                modifier = Modifier.align(badgeAlignment)
            )
        }

        if (!spineActive && showStatusOverlays) {
            val tabAtBottom = boxArtStyle.platformIndicatorStyle == com.nendo.argosy.data.preferences.PlatformIndicatorStyle.TAB &&
                (boxArtStyle.systemIconPosition == SystemIconPosition.BOTTOM_LEFT ||
                 boxArtStyle.systemIconPosition == SystemIconPosition.BOTTOM_RIGHT)
            StatusIndicators(
                isFavorite = game.isFavorite,
                isDownloaded = game.isDownloaded,
                tabAtBottom = tabAtBottom
            )
        }
    }
}

@Composable
private fun CoverContent(
    game: HomeGameUi,
    effectiveCoverPath: String,
    downloadIndicator: GameDownloadIndicator,
    saturationColorFilter: ColorFilter?,
    useGlassBorder: Boolean,
    glassBorderTintAlpha: Float,
    hasGradientColors: Boolean,
    gradientColors: Pair<Color, Color>?,
    borderColor: Color,
    geometry: BoxArtGeometry,
    sweepOffset: Float,
    onCoverLoaded: ((Long, Bitmap) -> Unit)?,
    onCoverLoadFailed: (Long, String) -> Unit
) {
    val imageData = rememberFileImageModel(effectiveCoverPath)

    if (downloadIndicator.isShown && imageData != null) {
        DownloadProgressCover(
            imageData = imageData,
            progress = downloadIndicator.progress,
            badgeSize = Dimens.iconLg,
            paused = downloadIndicator.isPaused,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        AsyncImage(
            model = imageData,
            contentDescription = game.title,
            contentScale = ContentScale.Crop,
            colorFilter = saturationColorFilter,
            modifier = Modifier.fillMaxSize(),
            onSuccess = { state ->
                val bitmap = (state.result.drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null) onCoverLoaded?.invoke(game.id, bitmap)
            },
            onError = { onCoverLoadFailed(game.id, effectiveCoverPath) }
        )
    }

    if (!useGlassBorder) return

    GlassBorderOverlay(
        imageModel = imageData,
        geometry = geometry,
        glassColorFilter = glassColorFilterFor(
            gradientColors = if (hasGradientColors) gradientColors else null,
            borderColor = borderColor,
            glassBorderTintAlpha = glassBorderTintAlpha
        ),
        sweepOffset = sweepOffset
    )
}

@Composable
private fun StubCover(gameTitle: String, useSolidStub: Boolean) {
    val stubBackground = if (useSolidStub) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val stubTextColor = if (useSolidStub) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(stubBackground),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = gameTitle,
            style = MaterialTheme.typography.bodyMedium,
            color = stubTextColor,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(Dimens.spacingSm)
        )
    }
}

@Composable
private fun BoxScope.StatusIndicators(
    isFavorite: Boolean,
    isDownloaded: Boolean,
    tabAtBottom: Boolean = false
) {
    if (!isFavorite && !isDownloaded) return

    Row(
        modifier = Modifier
            .align(if (tabAtBottom) Alignment.TopCenter else Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingSm)
            .then(
                if (tabAtBottom) Modifier.padding(top = Dimens.spacingXs)
                else Modifier.padding(bottom = Dimens.spacingXs)
            ),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IndicatorBadge(
            visible = isFavorite,
            icon = Icons.Default.Favorite,
            contentDescription = stringResource(R.string.ui_game_card_favorite)
        )
        IndicatorBadge(
            visible = isDownloaded,
            icon = Icons.Default.CheckCircle,
            contentDescription = stringResource(R.string.ui_game_card_downloaded)
        )
    }
}

@Composable
private fun IndicatorBadge(
    visible: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String
) {
    if (visible) {
        Box(
            modifier = Modifier
                .size(Dimens.iconSm + Dimens.borderMedium)
                .background(Color.Black.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(Dimens.iconXs)
            )
        }
    } else {
        Box(modifier = Modifier.size(Dimens.iconSm + Dimens.borderMedium))
    }
}

@Composable
fun GameCardWithBadge(
    game: HomeGameUi,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    badge: @Composable (() -> Unit)? = null
) {
    Box(modifier = modifier) {
        GameCard(
            game = game,
            isFocused = isFocused,
            modifier = Modifier.fillMaxSize()
        )

        if (badge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Dimens.spacingSm)
            ) {
                badge()
            }
        }
    }
}

/**
 * How far the new-game badge rises above the card it belongs to. The badge is laid out inside the
 * card's own bounds so nothing clips it, which makes a badged card this much taller than a plain
 * one; a caller that lines cards up has to take the difference back out.
 */
val NEW_BADGE_TOP_OVERFLOW = ComponentDefaults.Carousel.newBadgeOverflowDp.dp

@Composable
fun GameCardWithNewBadge(
    game: HomeGameUi,
    isFocused: Boolean,
    cardWidth: Dp,
    cardHeight: Dp,
    modifier: Modifier = Modifier,
    focusScale: Float = ComponentDefaults.Focus.scaleFocused,
    scalePivotY: Float = 0.5f,
    downloadIndicator: GameDownloadIndicator = GameDownloadIndicator.NONE,
    showPlatformBadge: Boolean = true,
    coverPathOverride: String? = null,
    onCoverLoadFailed: ((gameId: Long, failedPath: String) -> Unit)? = null,
    onCoverLoaded: ((gameId: Long, bitmap: Bitmap) -> Unit)? = null,
    scaleOverride: Float? = null,
    alphaOverride: Float? = null,
    useBoxArt: Boolean = false
) {
    val showNewBadge = game.isNew && !downloadIndicator.isActive
    val badgeWidthDp = ComponentDefaults.Carousel.newBadgeWidthDp.dp
    val badgeHeightDp = ComponentDefaults.Carousel.newBadgeHeightDp.dp


    val scaleTarget = scaleOverride ?: if (isFocused) focusScale else ComponentDefaults.Focus.scaleDefault
    val scale by animateFloatAsState(
        targetValue = scaleTarget,
        animationSpec = Motion.focusSpring,
        label = "wrapperScale"
    )

    val alpha by animateFloatAsState(
        targetValue = alphaOverride ?: if (isFocused) ComponentDefaults.Focus.alphaFocused else ComponentDefaults.Focus.alphaUnfocused,
        animationSpec = Motion.focusSpring,
        label = "wrapperAlpha"
    )

    Layout(
        content = {
            GameCard(
                game = game,
                isFocused = isFocused,
                focusScale = 1f,
                scalePivotY = scalePivotY,
                downloadIndicator = downloadIndicator,
                showPlatformBadge = showPlatformBadge,
                useBoxArt = useBoxArt,
                coverPathOverride = coverPathOverride,
                onCoverLoadFailed = onCoverLoadFailed,
                onCoverLoaded = onCoverLoaded,
                scaleOverride = 1f,
                alphaOverride = 1f,
                modifier = Modifier
            )
            if (showNewBadge) {
                NewBadge(
                    width = badgeWidthDp,
                    height = badgeHeightDp,
                    rotation = ComponentDefaults.Carousel.newBadgeRotationDegrees.toFloat()
                )
            }
        },
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            val overflowPx = NEW_BADGE_TOP_OVERFLOW.toPx()
            val artHeight = (size.height - overflowPx).coerceAtLeast(1f)
            transformOrigin = TransformOrigin(
                pivotFractionX = 0.5f,
                pivotFractionY = if (size.height <= 0f) {
                    scalePivotY
                } else {
                    (overflowPx + scalePivotY * artHeight) / size.height
                }
            )
            this.alpha = alpha
        }
    ) { measurables, constraints ->
        val cardWidthPx = cardWidth.toPx().toInt()
        val cardHeightPx = cardHeight.toPx().toInt()
        val cardConstraints = Constraints.fixed(cardWidthPx, cardHeightPx)

        val cardPlaceable = measurables[0].measure(cardConstraints)
        val badgePlaceable = if (showNewBadge && measurables.size > 1) {
            measurables[1].measure(Constraints())
        } else null

        val topOverflow = NEW_BADGE_TOP_OVERFLOW.roundToPx()
        val layoutHeight = cardPlaceable.height + topOverflow

        layout(cardPlaceable.width, layoutHeight) {
            cardPlaceable.placeRelative(0, topOverflow)
            badgePlaceable?.placeRelative(
                x = cardPlaceable.width - badgePlaceable.width,
                y = topOverflow - (badgePlaceable.height / 2)
            )
        }
    }
}

@Composable
fun SourceBadge(
    isLocal: Boolean,
    isSynced: Boolean
) {
    val (text, color) = when {
        isLocal && !isSynced -> "F" to LocalLauncherTheme.current.semanticColors.warning
        isSynced -> "C" to MaterialTheme.colorScheme.primary
        else -> return
    }

    Box(
        modifier = Modifier
            .background(
                color = color,
                shape = RoundedCornerShape(Dimens.radiusSm)
            )
            .padding(horizontal = Dimens.spacingSm - Dimens.borderMedium, vertical = Dimens.borderMedium)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
    }
}

