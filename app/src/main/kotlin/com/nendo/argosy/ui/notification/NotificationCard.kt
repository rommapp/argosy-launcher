package com.nendo.argosy.ui.notification

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.primitives.ArgosyProgressBar
import com.nendo.argosy.ui.primitives.InputGlyph
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.LocalMotionTier
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.MotionTier
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.theme.generated.DimensionTokens
import com.nendo.argosy.ui.theme.generated.MotionTokens
import kotlin.math.roundToInt

enum class NotificationSeverity { INFO, SUCCESS, WARNING, ERROR, PROGRESS }

@Composable
fun NotificationSeverity.accentColor(): Color {
    val semantic = LocalLauncherTheme.current.semanticColors
    return when (this) {
        NotificationSeverity.INFO -> MaterialTheme.colorScheme.primary
        NotificationSeverity.SUCCESS -> semantic.success
        NotificationSeverity.WARNING -> semantic.warning
        NotificationSeverity.ERROR -> LocalArgosyTheme.current.destructive
        NotificationSeverity.PROGRESS -> semantic.progress
    }
}

/**
 * Enters when [visible] turns true and exits when it turns false; the caller keeps passing the
 * last content until the exit finishes. The container grows from its end edge over content laid
 * out at its final size. A change of [title] or [body] while visible crossfades the text and does
 * not replay the entry. [icon] draws inside the icon slot and receives the severity color; null
 * draws the helm glyph. [accentOverride] replaces the severity color. [bodyGlyph] draws in place of
 * [NOTICE_GLYPH_MARKER] inside [body].
 */
@Composable
fun NotificationCard(
    visible: Boolean,
    severity: NotificationSeverity,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    bodyGlyph: InputButton? = null,
    badge: String? = null,
    progress: Float? = null,
    accentOverride: Color? = null,
    icon: (@Composable (accent: Color) -> Unit)? = null
) {
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = visible
    val reduced = LocalMotionTier.current == MotionTier.Reduced
    val accent = accentOverride ?: severity.accentColor()

    AnimatedVisibility(
        visibleState = visibleState,
        modifier = modifier,
        enter = EnterTransition.None,
        exit = ExitTransition.None
    ) {
        val alpha by transition.animateFloat(
            transitionSpec = {
                noticeAlphaSpec(entering = targetState == EnterExitState.Visible, reduced = reduced)
            },
            label = "noticeAlpha"
        ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
        val reveal by transition.animateFloat(
            transitionSpec = { tween(MotionTokens.Tween.noticeRevealMs, easing = Motion.argosyEase) },
            label = "noticeReveal"
        ) { state -> if (reduced || state == EnterExitState.Visible) 1f else 0f }

        NotificationCardContainer(
            accent = accent,
            alpha = { alpha },
            reveal = { reveal }
        ) { tint ->
            NotificationCardContent(
                tint = tint,
                text = NotificationCardText(title = title, body = body, bodyGlyph = bodyGlyph),
                badge = badge,
                progress = progress,
                icon = icon
            )
        }
    }
}

internal fun noticeAlphaSpec(entering: Boolean, reduced: Boolean): FiniteAnimationSpec<Float> = when {
    reduced -> tween(Motion.durationContent)
    entering -> tween(MotionTokens.Tween.noticeFadeMs)
    else -> tween(
        durationMillis = MotionTokens.Tween.noticeFadeMs,
        delayMillis = MotionTokens.Tween.noticeRevealMs - MotionTokens.Tween.noticeFadeMs
    )
}

@Composable
private fun NotificationCardContainer(
    accent: Color,
    alpha: () -> Float,
    reveal: () -> Float,
    content: @Composable (tint: Color) -> Unit
) {
    val tint by animateColorAsState(
        targetValue = accent,
        animationSpec = tween(Motion.durationContent),
        label = "noticeTint"
    )
    val surface = MaterialTheme.colorScheme.surfaceVariant
        .copy(alpha = ComponentDefaults.Notification.surfaceAlpha)
    val edgeWidth = Dimens.borderMedium

    Box(
        modifier = Modifier
            .graphicsLayer { this.alpha = alpha() }
            .clip(RoundedCornerShape(Dimens.radiusPanel))
            .drawWithContent {
                drawRect(tint.copy(alpha = ComponentDefaults.Notification.tintAlpha).compositeOver(surface))
                drawContent()
                val edgePx = edgeWidth.toPx()
                val edgeLeft = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width - edgePx
                drawRect(color = tint, topLeft = Offset(edgeLeft, 0f), size = Size(edgePx, size.height))
            }
            .revealFromEnd(reveal)
    ) {
        content(tint)
    }
}

private fun Modifier.revealFromEnd(fraction: () -> Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val visibleWidth = constraints.constrainWidth((placeable.width * fraction()).roundToInt())
    layout(visibleWidth, placeable.height) {
        placeable.placeRelative(visibleWidth - placeable.width, 0)
    }
}

@Composable
private fun NotificationCardContent(
    tint: Color,
    text: NotificationCardText,
    badge: String?,
    progress: Float?,
    icon: (@Composable (accent: Color) -> Unit)?
) {
    val bounds = noticeWidthBounds()
    Column(
        modifier = Modifier
            .widthIn(min = bounds.min, max = bounds.max)
            .width(IntrinsicSize.Max)
    ) {
        Row(
            modifier = Modifier.padding(
                start = Dimens.spacingSm + Dimens.borderMedium,
                top = Dimens.spacingSm,
                end = Dimens.spacingSm,
                bottom = Dimens.spacingSm
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NotificationIconSlot(tint = tint, badge = badge, icon = icon)
            Spacer(modifier = Modifier.width(Dimens.spacingSm))
            Crossfade(
                targetState = text,
                animationSpec = tween(Motion.durationContent),
                label = "noticeText"
            ) { shown ->
                NotificationCardTexts(shown)
            }
        }
        progress?.let { fraction ->
            ArgosyProgressBar(progress = animatedProgress(fraction), tint = tint)
        }
    }
}

@Composable
private fun animatedProgress(target: Float): Float {
    val reduced = LocalMotionTier.current == MotionTier.Reduced
    val shown by animateFloatAsState(
        targetValue = target,
        animationSpec = if (reduced) snap() else tween(MotionTokens.Tween.noticeProgressMs, easing = Motion.argosyEase),
        label = "noticeProgress"
    )
    return shown
}

/**
 * Stands in a notice body for the button glyph the card draws there. Passed as the format argument
 * of a body string that names a button.
 */
const val NOTICE_GLYPH_MARKER = ""

private data class NotificationCardText(val title: String, val body: String?, val bodyGlyph: InputButton?)

@Composable
private fun NotificationCardTexts(text: NotificationCardText) {
    val textColor = MaterialTheme.colorScheme.onSurface
    Column {
        Text(
            text = text.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = textColor,
            softWrap = true
        )
        text.body?.let { body ->
            NotificationBodyText(
                body = body,
                glyph = text.bodyGlyph,
                color = textColor.copy(alpha = ComponentDefaults.Notification.bodyAlpha)
            )
        }
    }
}

@Composable
private fun NotificationBodyText(body: String, glyph: InputButton?, color: Color) {
    val markerAt = body.indexOf(NOTICE_GLYPH_MARKER)
    if (glyph == null || markerAt < 0) {
        Text(text = body, style = MaterialTheme.typography.bodySmall, color = color, softWrap = true)
        return
    }
    val annotated = buildAnnotatedString {
        append(body.substring(0, markerAt))
        appendInlineContent(NOTICE_GLYPH_MARKER)
        append(body.substring(markerAt + NOTICE_GLYPH_MARKER.length))
    }
    val glyphSize = ComponentDefaults.Notification.bodyGlyphSizeRatio.em
    val inline = mapOf(
        NOTICE_GLYPH_MARKER to InlineTextContent(
            Placeholder(glyphSize, glyphSize, PlaceholderVerticalAlign.Center)
        ) {
            InputGlyph(button = glyph, modifier = Modifier.fillMaxSize(), tint = color)
        }
    )
    Text(
        text = annotated,
        inlineContent = inline,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        softWrap = true
    )
}

@Composable
private fun NotificationIconSlot(
    tint: Color,
    badge: String?,
    icon: (@Composable (accent: Color) -> Unit)?
) {
    Box(
        modifier = Modifier.size(Dimens.avatarMd),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            icon(tint)
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_helm),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(Dimens.iconMd)
            )
        }
        badge?.let { text ->
            NotificationBadge(
                text = text,
                tint = tint,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}

@Composable
private fun NotificationBadge(text: String, tint: Color, modifier: Modifier = Modifier) {
    val scale = LocalUiScale.current.scale
    val pill = RoundedCornerShape(Dimens.radiusPill)
    val fill = tint.copy(alpha = ComponentDefaults.Notification.badgeFillAlpha)
        .compositeOver(MaterialTheme.colorScheme.surfaceVariant)
    Box(
        modifier = modifier
            .height(ComponentDefaults.Notification.badgeHeightDp.dp * scale)
            .widthIn(min = ComponentDefaults.Notification.badgeMinWidthDp.dp * scale)
            .clip(pill)
            .background(fill)
            .border(Dimens.borderThin, tint, pill)
            .padding(horizontal = Dimens.spacingXs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontSize = ComponentDefaults.Notification.badgeFontSizeSp.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            softWrap = false,
            maxLines = 1
        )
    }
}

private data class NoticeWidthBounds(val min: Dp, val max: Dp)

@Composable
private fun noticeWidthBounds(): NoticeWidthBounds {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    if (configuration.screenWidthDp > configuration.screenHeightDp) {
        return NoticeWidthBounds(
            min = screenWidth * (DimensionTokens.Layout.noticeWidthLandscapeMinPct / 100f),
            max = screenWidth * (DimensionTokens.Layout.noticeWidthLandscapeMaxPct / 100f)
        )
    }
    val portraitWidth = minOf(
        screenWidth - Dimens.spacingMd * 2,
        DimensionTokens.Layout.noticeWidthPortraitMax.dp
    )
    return NoticeWidthBounds(min = portraitWidth, max = portraitWidth)
}
