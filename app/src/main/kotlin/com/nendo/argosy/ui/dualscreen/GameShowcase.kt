package com.nendo.argosy.ui.dualscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.data.social.FriendActivity
import com.nendo.argosy.domain.model.PresentationArt
import com.nendo.argosy.domain.model.PresentationLayout
import com.nendo.argosy.domain.model.PresentationScrim
import com.nendo.argosy.domain.model.PresentationStat
import com.nendo.argosy.domain.model.PresentationStyle
import com.nendo.argosy.ui.common.backgroundBlurDp
import com.nendo.argosy.ui.common.playerCountGlyph
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.data.model.ArtSlot
import com.nendo.argosy.ui.components.Box3dCover
import com.nendo.argosy.ui.components.BoxArtRoute
import com.nendo.argosy.ui.components.boxArtRoutes
import com.nendo.argosy.ui.components.firstWorking
import com.nendo.argosy.ui.components.LocalFrostedBackdrop
import com.nendo.argosy.ui.components.frostedBackdropSource
import com.nendo.argosy.ui.components.rememberFrostedBackdrop
import com.nendo.argosy.ui.components.PlatformIconAssets
import com.nendo.argosy.ui.components.friends.FriendsActivityBadge
import com.nendo.argosy.ui.theme.ALauncherColors
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.generated.ColorTokens
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.util.formatPlayTime
import com.nendo.argosy.util.formatTimeToBeat

private const val PERCENT = 100f
private const val DEFAULT_STRENGTH = 90f
private const val JOURNAL_LEFT_ALPHA = 0.96f
private const val JOURNAL_MID_ALPHA = 0.86f
private const val JOURNAL_RIGHT_ALPHA = 0.15f
private const val JOURNAL_MID_STOP = 0.42f
private const val JOURNAL_CLEAR_STOP = 0.8f
private const val LOGO_TOP_ALPHA = 0.1f
private const val LOGO_BOTTOM_ALPHA = 0.7f
private const val JOURNAL_COLUMN_WIDTH_SHARE = 0.56f
private const val PILL_ALPHA = 0.6f
private const val DIVIDER_ALPHA = 0.25f
private const val TRACK_ALPHA = 0.14f
private const val JOURNAL_CARD_ALPHA = 0.72f

/**
 * A focused game on the presentation screen, drawn in [style]'s layout.
 *
 * @param bottomInset the height of whatever the host draws over the bottom edge, such as relayed
 *   control hints; content stays above it.
 */
@Composable
fun GameShowcase(
    detail: CompanionDetail,
    style: PresentationStyle,
    bottomInset: Dp,
    backgroundBlur: Int = 0,
    modifier: Modifier = Modifier
) {
    val art = detail.gameId?.let { com.nendo.argosy.ui.common.rememberResolvedArt(it) }
    val liveDetail = remember(detail, art) { detail.withLiveArt(art) }
    GameShowcaseContent(liveDetail, style, bottomInset, backgroundBlur, modifier)
}

private fun CompanionDetail.withLiveArt(art: com.nendo.argosy.data.model.ResolvedGameArt?): CompanionDetail {
    if (art == null) return this
    return copy(
        artUrl = art.coverPath ?: artUrl,
        backdropUrl = art.backgroundPath ?: backdropUrl,
        logoUrl = art.logoPath?.takeIf { it.startsWith("/") } ?: logoUrl
    )
}

@Composable
private fun GameShowcaseContent(
    detail: CompanionDetail,
    style: PresentationStyle,
    bottomInset: Dp,
    backgroundBlur: Int,
    modifier: Modifier
) {
    val contentBottom = bottomInset + Dimens.spacingMd
    val theme = LocalArgosyTheme.current
    val friends = detail.stats?.friends.orEmpty().takeIf { style.shows(PresentationStat.FRIENDS) }.orEmpty()
    val backdrop = LocalFrostedBackdrop.current ?: rememberFrostedBackdrop()
    val backgroundBlurRadius = when (style.scrim) {
        PresentationScrim.GRADIENT -> backgroundBlur.backgroundBlurDp
        PresentationScrim.BLUR -> Motion.blurRadiusDrawer
        PresentationScrim.SOLID, PresentationScrim.NONE -> 0.dp
    }
    var viewportOrigin by remember { mutableStateOf(Offset.Zero) }
    var titleBounds by remember { mutableStateOf(Rect.Zero) }
    CompositionLocalProvider(LocalFrostedBackdrop provides backdrop) {
      BoxWithConstraints(modifier = modifier.fillMaxSize().clipToBounds().onGloballyPositioned {
          viewportOrigin = it.positionInRoot()
      }) {
        val gutter = maxWidth * GUTTER_SHARE
        Box(Modifier.fillMaxSize().frostedBackdropSource(backdrop).background(theme.surfaceBase)) {
            (detail.backdropUrl ?: detail.artUrl)?.let { background ->
                AsyncImage(
                    model = rememberFileImageModel(background),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (backgroundBlurRadius > 0.dp) Modifier.blur(backgroundBlurRadius) else Modifier)
                )
            }
            ShowcaseScrim(style = style, color = theme.surfaceBase)
            if (style.layout == PresentationLayout.CINEMATIC && style.scrim == PresentationScrim.GRADIENT) {
                val color = presentationScrimColor()
                val strength = style.scrimStrength / DEFAULT_STRENGTH
                Box(Modifier.fillMaxSize().drawBehind {
                    val bounds = titleBounds.translate(-viewportOrigin)
                    if (bounds.width > 0f && bounds.height > 0f && strength > 0f) {
                        val radii = presentationTitleRadii(
                            size.width, size.height, bounds.width, bounds.height,
                            ComponentDefaults.Presentation.titleOverscanRatio
                        )
                        val brush = Brush.radialGradient(
                            0f to color.copy(
                                alpha = presentationScrimAlpha(
                                    ComponentDefaults.Presentation.titleCenterAlpha,
                                    strength
                                )
                            ),
                            ComponentDefaults.Presentation.titleMidRatio to color.copy(
                                alpha = presentationScrimAlpha(ComponentDefaults.Presentation.titleMidAlpha, strength)
                            ),
                            1f to color.copy(alpha = 0f),
                            center = bounds.center,
                            radius = radii.height
                        )
                        scale(scaleX = radii.width / radii.height, scaleY = 1f, pivot = bounds.center) {
                            drawCircle(brush, radius = radii.height, center = bounds.center)
                        }
                    }
                })
            }
        }

        when (style.layout) {
            PresentationLayout.LOGO -> LogoShowcase(detail = detail, gutter = gutter)
            PresentationLayout.CINEMATIC -> CinematicShowcase(
                detail = detail,
                style = style,
                friends = friends,
                bottom = bottomInset,
                viewportWidth = maxWidth,
                viewportHeight = maxHeight,
                onTitleBoundsChanged = { titleBounds = it }
            )
            PresentationLayout.JOURNAL -> JournalShowcase(
                detail = detail,
                style = style,
                friends = friends,
                gutter = gutter,
                bottom = contentBottom,
                columnWidth = maxWidth * JOURNAL_COLUMN_WIDTH_SHARE
            )
        }
      }
    }
}

private const val GUTTER_SHARE = 0.0375f

@Composable
private fun ShowcaseScrim(style: PresentationStyle, color: Color) {
    val strength = style.scrimStrength / PERCENT
    val scale = style.scrimStrength / DEFAULT_STRENGTH
    fun tone(alpha: Float) = color.copy(alpha = (alpha * scale).coerceIn(0f, 1f))
    val cinematicColor = presentationScrimColor()
    val brush = when (style.scrim) {
        PresentationScrim.NONE -> return
        PresentationScrim.SOLID, PresentationScrim.BLUR -> SolidColor(color.copy(alpha = strength))
        PresentationScrim.GRADIENT -> when (style.layout) {
            PresentationLayout.CINEMATIC -> Brush.horizontalGradient(
                0f to cinematicColor.copy(alpha = (ComponentDefaults.Presentation.edgeAlpha * scale).coerceIn(0f, 1f)),
                ComponentDefaults.Presentation.edgeClearRatio to cinematicColor.copy(alpha = 0f),
                1f to cinematicColor.copy(alpha = 0f)
            )
            PresentationLayout.JOURNAL -> Brush.horizontalGradient(
                0f to tone(JOURNAL_LEFT_ALPHA),
                JOURNAL_MID_STOP to tone(JOURNAL_MID_ALPHA),
                JOURNAL_CLEAR_STOP to tone(JOURNAL_RIGHT_ALPHA),
                1f to tone(JOURNAL_RIGHT_ALPHA)
            )
            PresentationLayout.LOGO -> Brush.verticalGradient(
                0f to tone(LOGO_TOP_ALPHA),
                1f to tone(LOGO_BOTTOM_ALPHA)
            )
        }
    }
    Box(modifier = Modifier.fillMaxSize().background(brush))
}

@Composable
private fun presentationScrimColor(): Color = if (LocalLauncherTheme.current.isDarkTheme) {
    ColorTokens.Domain.PresentationScrim.dark
} else {
    ColorTokens.Domain.PresentationScrim.light
}

@Composable
private fun LogoShowcase(detail: CompanionDetail, gutter: Dp) {
    val theme = LocalArgosyTheme.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = gutter, vertical = Dimens.spacingMd),
        contentAlignment = Alignment.Center
    ) {
        var logoFailed by remember(detail.logoUrl) { mutableStateOf(false) }
        val logo = detail.logoUrl?.takeIf { !logoFailed }
        if (logo != null) {
            val glow = theme.surfaceBase
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val brush = Brush.radialGradient(
                            0f to glow.copy(alpha = LOGO_GLOW_ALPHA),
                            LOGO_GLOW_HOLD to glow.copy(alpha = LOGO_GLOW_ALPHA * LOGO_GLOW_HOLD_FADE),
                            1f to Color.Transparent,
                            center = center,
                            radius = size.height / 2f
                        )
                        scale(scaleX = size.width / size.height, scaleY = 1f, pivot = center) {
                            drawCircle(brush = brush, radius = size.height / 2f, center = center)
                        }
                    }
            )
            AsyncImage(
                model = rememberFileImageModel(logo),
                contentDescription = detail.title,
                contentScale = ContentScale.Fit,
                onError = { logoFailed = true },
                modifier = Modifier
                    .fillMaxWidth(LOGO_WIDTH_SHARE)
                    .fillMaxHeight(LOGO_HEIGHT_SHARE)
            )
        } else {
            Text(
                text = detail.title,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = theme.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

private const val LOGO_WIDTH_SHARE = 0.6f
private const val LOGO_HEIGHT_SHARE = 0.5f
private const val LOGO_GLOW_ALPHA = 0.75f
private const val LOGO_GLOW_HOLD = 0.5f
private const val LOGO_GLOW_HOLD_FADE = 0.7f

@Composable
private fun CinematicShowcase(
    detail: CompanionDetail,
    style: PresentationStyle,
    friends: List<FriendActivity>,
    bottom: Dp,
    viewportWidth: Dp,
    viewportHeight: Dp,
    onTitleBoundsChanged: (Rect) -> Unit
) {
    val theme = LocalArgosyTheme.current
    val scrollState = rememberLazyListState()
    LaunchedEffect(detail.gameId) {
        scrollState.scrollToItem(0)
    }
    val gutter = if (viewportWidth.value < ComponentDefaults.Carousel.compactWidthDp) {
        Dimens.spacingMd
    } else {
        Dimens.spacingLg
    }
    val zero = 0.dp
    val availableHeight = (viewportHeight - bottom - gutter * 2).coerceAtLeast(zero)
    val availableWidth = (viewportWidth - gutter * 2).coerceAtLeast(zero)
    val textDirection = LocalLayoutDirection.current
    val items = detail.stats?.let { showcaseRailItems(it, style, journeyShown = false) }.orEmpty()
    val developer = items.firstOrNull { it.first == PresentationStat.DEVELOPER }?.second?.text
    val rows = if (detail.stats == null) {
        listOf(RailGroup.FACTS to detail.facts.map { RailItem(null, null, it.value) })
            .filter { it.second.isNotEmpty() }
    } else {
        RailGroup.entries.mapNotNull { group ->
            items.filter { it.first.railGroup == group && it.first != PresentationStat.DEVELOPER }
                .map { it.second }.takeIf { it.isNotEmpty() }
                ?.let { group to it }
        }
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
      Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = bottom)
            .padding(gutter),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXl),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShowcaseCover(
            detail = detail,
            art = style.art,
            height = availableHeight,
            width = ((availableWidth - Dimens.spacingXl) / 3).coerceAtLeast(zero)
        )
        Box(Modifier.weight(1f).heightIn(max = availableHeight)) {
          CompositionLocalProvider(LocalLayoutDirection provides textDirection) {
            LazyColumn(
                state = scrollState,
                modifier = Modifier.onGloballyPositioned {
                    onTitleBoundsChanged(
                        Rect(it.positionInRoot(), Size(it.size.width.toFloat(), it.size.height.toFloat()))
                    )
                }
            ) {
                item(key = "heading") {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)) {
                        ShowcaseTitle(detail = detail)
                        developer?.let {
                            Text(text = it, style = MaterialTheme.typography.titleMedium, color = theme.textDim)
                        }
                    }
                }
                itemsIndexed(rows, key = { _, row -> row.first.name }) { index, row ->
                    Box(Modifier.padding(top = if (index == 0) Dimens.spacingMd else Dimens.spacingSm)) {
                        ShowcaseRailRow(row.second)
                    }
                }
                if (friends.isNotEmpty()) {
                    item(key = "friends") {
                        Box(Modifier.padding(top = Dimens.spacingMd)) {
                            FriendsActivityBadge(friends = friends, textColor = theme.textPrimary)
                        }
                    }
                }
            }
          }
        }
      }
    }
}

@Composable
private fun JournalShowcase(
    detail: CompanionDetail,
    style: PresentationStyle,
    friends: List<FriendActivity>,
    gutter: Dp,
    bottom: Dp,
    columnWidth: Dp
) {
    val theme = LocalArgosyTheme.current
    val stats = detail.stats
    val items = stats?.let { showcaseRailItems(it, style, journeyShown = true) }.orEmpty()
    val facts = items.filter { it.first.railGroup == RailGroup.FACTS && it.first != PresentationStat.DEVELOPER }
        .map { it.second }
    val ratings = items.filter { it.first.railGroup == RailGroup.RATINGS }.map { it.second }
    val developer = items.firstOrNull { it.first == PresentationStat.DEVELOPER }?.second?.text
    val factRow = if (stats == null) detail.facts.map { RailItem(null, null, it.value) } else facts
    Column(
        modifier = Modifier
            .width(columnWidth)
            .fillMaxHeight()
            .padding(start = gutter, top = Dimens.spacingXl, bottom = bottom + Dimens.spacingLg),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
    ) {
        detail.subtitle?.let { ShowcaseSubtitle(it, detail.platformSlug) }
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
            Text(
                text = detail.title,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = theme.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            developer?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    color = theme.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (factRow.isNotEmpty()) {
            ShowcaseRailRow(factRow, textStyle = MaterialTheme.typography.titleSmall, textColor = theme.textDim)
        }
        Box(modifier = Modifier.weight(1f))
        if (ratings.isNotEmpty()) ShowcaseRailRow(ratings)
        stats?.let { JournalJourneyCard(stats = it, style = style) }
        if (friends.isNotEmpty()) {
            FriendsActivityBadge(friends = friends, textColor = theme.textPrimary)
        }
    }
}

@Composable
private fun JournalJourneyCard(stats: CompanionGameStats, style: PresentationStyle) {
    val theme = LocalArgosyTheme.current
    val context = LocalContext.current
    val played = stats.playTimeMinutes.takeIf { it > 0 && style.shows(PresentationStat.PLAY_TIME) }
        ?.let { formatPlayTime(context, it) }
    val mainStory = stats.timeToBeatMainSec?.takeIf { it > 0 && style.shows(PresentationStat.TIME_TO_BEAT) }
    val mainStoryLabel = mainStory?.let { formatTimeToBeat(context, it) }
    val fraction = if (mainStory != null) {
        (stats.playTimeMinutes * SECONDS_PER_MINUTE / mainStory.toFloat()).coerceIn(0f, 1f)
    } else {
        null
    }
    val achievements = stats.takeIf { style.shows(PresentationStat.ACHIEVEMENTS) && it.achievementCount > 0 }
    if (played == null && mainStoryLabel == null && achievements == null) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusLg))
            .background(theme.surfaceBase.copy(alpha = JOURNAL_CARD_ALPHA))
            .padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.dual_showcase_journey_header).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = theme.textDim,
                modifier = Modifier.weight(1f)
            )
            fraction?.let {
                Text(
                    text = "${(it * PERCENT).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = theme.focusAccent
                )
            }
        }
        fraction?.let { ShowcaseTrack(fraction = it, color = theme.focusAccent) }
        if (played != null || mainStoryLabel != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = played?.let { stringResource(R.string.dual_showcase_played, it) }.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    color = theme.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                mainStoryLabel?.let {
                    Text(
                        text = stringResource(R.string.dual_showcase_journey_main, it),
                        style = MaterialTheme.typography.titleSmall,
                        color = theme.textDim
                    )
                }
            }
        }
        achievements?.let {
            ShowcaseProgressBar(
                fraction = it.earnedAchievementCount.toFloat() / it.achievementCount,
                color = ALauncherColors.TrophyAmber,
                label = "${it.earnedAchievementCount}/${it.achievementCount}",
                icon = Icons.Filled.EmojiEvents
            )
        }
    }
}

@Composable
private fun ShowcaseTrack(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.spacingXs)
            .clip(RoundedCornerShape(Dimens.radiusPill))
            .background(theme.textPrimary.copy(alpha = TRACK_ALPHA))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .clip(RoundedCornerShape(Dimens.radiusPill))
                .background(color)
        )
    }
}

@Composable
private fun ShowcaseTitle(detail: CompanionDetail) {
    val theme = LocalArgosyTheme.current
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
        detail.subtitle?.let { ShowcaseSubtitle(it, detail.platformSlug, pill = false) }
        Text(
            text = detail.title,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = theme.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ShowcaseSubtitle(subtitle: String, platformSlug: String?, pill: Boolean = true) {
    val theme = LocalArgosyTheme.current
    val context = LocalContext.current
    val iconUri = platformSlug?.let { slug -> remember(slug) { PlatformIconAssets.resolveAssetUri(context, slug) } }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        modifier = if (pill) Modifier
            .clip(RoundedCornerShape(Dimens.radiusPill))
            .background(theme.surfaceBase.copy(alpha = PILL_ALPHA))
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs)
        else Modifier
    ) {
        iconUri?.let { uri ->
            AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(Dimens.iconSm)
            )
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelLarge,
            color = theme.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ShowcaseCover(detail: CompanionDetail, art: PresentationArt, height: Dp, width: Dp) {
    if (art == PresentationArt.TITLE) return
    val artUrl = detail.artUrl
    val repairArt = com.nendo.argosy.ui.common.rememberArtRepair()
    val repair: (ArtSlot) -> Unit = { slot -> detail.gameId?.let { repairArt(it, slot) } }
    var failedRoutes by remember(detail.spineUrl, detail.box3dUrl, artUrl) {
        mutableStateOf(emptySet<BoxArtRoute>())
    }
    val routes = boxArtRoutes(art == PresentationArt.BOX_3D, detail.spineUrl, detail.box3dUrl, artUrl)
    when (routes.firstWorking(failedRoutes)) {
        BoxArtRoute.SPINE_RENDER -> Box3dCover(
            frontPath = artUrl.orEmpty(),
            spinePath = detail.spineUrl.orEmpty(),
            isInteractive = false,
            modifier = Modifier.heightIn(max = height).widthIn(max = width),
            onUnavailable = {
                failedRoutes = failedRoutes + BoxArtRoute.SPINE_RENDER
                repair(ArtSlot.BOX_SPINE)
                repair(ArtSlot.COVER)
            }
        )
        BoxArtRoute.BOX_3D_IMAGE -> ShowcaseImage(
            model = rememberFileImageModel(detail.box3dUrl),
            width = width,
            height = height,
            onError = {
                failedRoutes = failedRoutes + BoxArtRoute.BOX_3D_IMAGE
                repair(ArtSlot.BOX_3D)
            }
        )
        BoxArtRoute.FLAT_COVER -> ShowcaseImage(
            model = rememberFileImageModel(artUrl),
            width = width,
            height = height,
            modifier = Modifier.clip(RoundedCornerShape(Dimens.radiusLg)),
            onError = {
                failedRoutes = failedRoutes + BoxArtRoute.FLAT_COVER
                repair(ArtSlot.COVER)
            }
        )
        BoxArtRoute.TEXT -> Unit
    }
}

@Composable
private fun ShowcaseImage(
    model: Any?,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    onError: () -> Unit
) {
    var nativeRatio by remember(model) { mutableStateOf<Float?>(null) }
    val fitted = nativeRatio?.let { fitPresentationArt(width.value, height.value, it) }
    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.then(
            if (fitted == null) Modifier.widthIn(max = width).heightIn(max = height)
            else Modifier.size(fitted.width.dp, fitted.height.dp)
        ),
        onSuccess = {
            val drawable = it.result.drawable
            if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                nativeRatio = drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight
            }
        },
        onError = { onError() }
    )
}

private data class RailItem(val icon: ImageVector?, val tint: Color?, val text: String)

private enum class RailGroup { FACTS, RATINGS, PROGRESS }

private val PresentationStat.railGroup: RailGroup
    get() = when (this) {
        PresentationStat.DEVELOPER, PresentationStat.RELEASE_YEAR, PresentationStat.PLAYERS,
        PresentationStat.GENRE, PresentationStat.FRIENDS -> RailGroup.FACTS
        PresentationStat.COMMUNITY_RATING, PresentationStat.USER_RATING,
        PresentationStat.DIFFICULTY -> RailGroup.RATINGS
        PresentationStat.PLAY_TIME, PresentationStat.TIME_TO_BEAT,
        PresentationStat.ACHIEVEMENTS -> RailGroup.PROGRESS
    }

@Composable
private fun showcaseRailItems(
    stats: CompanionGameStats,
    style: PresentationStyle,
    journeyShown: Boolean
): List<Pair<PresentationStat, RailItem>> {
    val context = LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val playedTemplate = stringResource(R.string.dual_showcase_played)
    return PresentationStat.entries.filter { style.shows(it) }.mapNotNull { stat ->
        when (stat) {
            PresentationStat.DEVELOPER -> stats.developer?.let { RailItem(null, null, it) }
            PresentationStat.RELEASE_YEAR ->
                stats.releaseYear?.let { RailItem(Icons.Default.CalendarToday, null, it.toString()) }
            PresentationStat.PLAYERS ->
                stats.players?.takeIf { it.isNotBlank() }?.let { RailItem(playerCountGlyph(it), null, it) }
            PresentationStat.GENRE -> stats.genre?.split(",")?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { RailItem(Icons.Default.Sell, null, it) }
            PresentationStat.COMMUNITY_RATING ->
                stats.communityRating?.let { RailItem(Icons.Default.Public, primary, "${it.toInt()}%") }
            PresentationStat.USER_RATING -> stats.userRating.takeIf { it > 0 }
                ?.let { RailItem(Icons.Default.Star, ALauncherColors.StarGold, "$it/10") }
            PresentationStat.DIFFICULTY -> stats.userDifficulty.takeIf { it > 0 }
                ?.let { RailItem(Icons.Default.Whatshot, ALauncherColors.DifficultyRed, "$it/10") }
            PresentationStat.PLAY_TIME -> stats.playTimeMinutes.takeIf { it > 0 && !journeyShown }
                ?.let { RailItem(Icons.Default.SportsEsports, null, playedTemplate.format(formatPlayTime(context, it))) }
            PresentationStat.TIME_TO_BEAT -> formatTimeToBeat(context, stats.timeToBeatMainSec)
                ?.takeIf { !journeyShown }
                ?.let { RailItem(Icons.Default.Schedule, null, it) }
            PresentationStat.ACHIEVEMENTS -> stats.achievementCount.takeIf {
                it > 0 && style.layout != PresentationLayout.JOURNAL
            }?.let { RailItem(Icons.Filled.EmojiEvents, ALauncherColors.TrophyAmber, "${stats.earnedAchievementCount}/$it") }
            PresentationStat.FRIENDS -> null
        }?.let { stat to it }
    }
}

@Composable
private fun ShowcaseRailRow(
    items: List<RailItem>,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
    textColor: Color = LocalArgosyTheme.current.textPrimary
) {
    val theme = LocalArgosyTheme.current
    DividedFlow(
        count = items.size,
        lineSpacing = Dimens.spacingXs,
        divider = {
            Box(
                modifier = Modifier
                    .padding(horizontal = Dimens.spacingMd)
                    .width(Dimens.borderThin)
                    .height(Dimens.iconSm)
                    .background(theme.textPrimary.copy(alpha = DIVIDER_ALPHA))
            )
        }
    ) { index ->
        val item = items[index]
        Row(verticalAlignment = Alignment.CenterVertically) {
            item.icon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = item.tint ?: textColor,
                    modifier = Modifier.padding(end = Dimens.spacingXs).size(Dimens.iconSm)
                )
            }
            Text(
                text = item.text,
                style = textStyle,
                color = textColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DividedFlow(
    count: Int,
    lineSpacing: Dp,
    divider: @Composable () -> Unit,
    item: @Composable (Int) -> Unit
) {
    if (count == 0) return
    Layout(
        contents = listOf(
            { repeat(count) { item(it) } },
            { repeat((count - 1).coerceAtLeast(0)) { divider() } }
        )
    ) { (itemMeasurables, dividerMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val items = itemMeasurables.map { it.measure(loose) }
        val dividers = dividerMeasurables.map { it.measure(loose) }
        val spacing = lineSpacing.roundToPx()

        val lines = mutableListOf(mutableListOf(0))
        var lineWidth = items.firstOrNull()?.width ?: 0
        for (index in 1 until items.size) {
            val joined = lineWidth + dividers[index - 1].width + items[index].width
            if (joined <= constraints.maxWidth) {
                lines.last() += index
                lineWidth = joined
            } else {
                lines += mutableListOf(index)
                lineWidth = items[index].width
            }
        }
        val lineHeights = lines.map { line -> line.maxOf { items[it].height } }
        val width = if (items.isEmpty()) 0 else lines.maxOf { line ->
            line.sumOf { items[it].width } + line.drop(1).sumOf { dividers[it - 1].width }
        }.coerceAtMost(constraints.maxWidth)
        val height = lineHeights.sum() + spacing * (lines.size - 1).coerceAtLeast(0)

        layout(width, height) {
            var y = 0
            lines.forEachIndexed { lineIndex, line ->
                val lineHeight = lineHeights[lineIndex]
                var x = 0
                line.forEachIndexed { position, index ->
                    if (position > 0) {
                        val gap = dividers[index - 1]
                        gap.place(x, y + (lineHeight - gap.height) / 2)
                        x += gap.width
                    }
                    items[index].place(x, y + (lineHeight - items[index].height) / 2)
                    x += items[index].width
                }
                y += lineHeight + spacing
            }
        }
    }
}

private const val SECONDS_PER_MINUTE = 60f

@Composable
private fun ShowcaseProgressBar(fraction: Float, color: Color, label: String, icon: ImageVector) {
    val theme = LocalArgosyTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(Dimens.iconSm)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(Dimens.spacingXs)
                .clip(RoundedCornerShape(Dimens.radiusPill))
                .background(theme.textPrimary.copy(alpha = TRACK_ALPHA))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(Dimens.radiusPill))
                    .background(color)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = theme.textDim,
            maxLines = 1
        )
    }
}
