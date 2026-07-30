package com.nendo.argosy.ui.screens.quaypass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.friends.SocialAvatar
import com.nendo.argosy.ui.theme.Dimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Arrival toast for a new traveler. Rides in the app's standard notification
 * slot (bottom-end, above the footer): the notice slides in with the traveler's
 * name and greeting, and a ticket springs up out of it, flips end over end like
 * a struck coin block, and drops back as it fades. Restored from the original
 * QuayPass ticket-reward animation. [trigger] plays it on each change above zero.
 */
@Composable
fun QuayPassArrivalOverlay(
    trigger: Int,
    name: String,
    greeting: String?,
    avatarBitmap: ImageBitmap?,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var cardVisible by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }

    LaunchedEffect(trigger) {
        if (trigger <= 0) return@LaunchedEffect
        playing = true
        cardVisible = true
        delay(CARD_HOLD_MS)
        cardVisible = false
        delay(CARD_EXIT_MS)
        playing = false
        onFinished()
    }

    if (!playing) return

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = Dimens.spacingMd, bottom = FOOTER_CLEARANCE.dp)
        ) {
            AnimatedVisibility(
                visible = cardVisible,
                enter = slideInHorizontally(initialOffsetX = { it }) +
                    fadeIn(tween(200)) +
                    scaleIn(initialScale = 0.92f, animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f)),
                exit = slideOutHorizontally(targetOffsetX = { it / 3 }) +
                    fadeOut(tween(150)) +
                    scaleOut(targetScale = 0.95f)
            ) {
                ArrivalCard(name = name, greeting = greeting, avatarBitmap = avatarBitmap)
            }
            FlyingTicket(
                playKey = trigger,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = Dimens.spacingSm, top = Dimens.spacingSm)
            )
        }
    }
}

@Composable
private fun ArrivalCard(
    name: String,
    greeting: String?,
    avatarBitmap: ImageBitmap?,
    modifier: Modifier = Modifier
) {
    val baseColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
    val backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f).compositeOver(baseColor)
    val textColor = MaterialTheme.colorScheme.onSurface

    Row(
        modifier = modifier
            .widthIn(max = Dimens.modalWidth - Dimens.headerHeight + Dimens.spacingSm)
            .clip(RoundedCornerShape(Dimens.spacingSm + Dimens.borderMedium))
            .background(backgroundColor)
            .padding(Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (avatarBitmap != null) {
            Image(
                bitmap = avatarBitmap,
                contentDescription = null,
                filterQuality = FilterQuality.None,
                modifier = Modifier
                    .size(Dimens.iconLg)
                    .clip(RoundedCornerShape(Dimens.spacingSm - Dimens.borderMedium))
            )
        } else {
            SocialAvatar(
                displayName = name,
                avatarColor = null,
                size = Dimens.iconLg
            )
        }
        Spacer(Modifier.width(Dimens.spacingSm))
        Column {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!greeting.isNullOrBlank()) {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * A single ticket that pops in over the cover-art slot, arcs up and settles,
 * flips twice about its vertical axis, then fades. Choreography preserved from
 * the original QuayPass reward animation.
 */
@Composable
private fun FlyingTicket(
    playKey: Int,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val offsetY = remember { Animatable(0f) }
    val rotation = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(playKey) {
        offsetY.snapTo(0f)
        rotation.snapTo(0f)
        alpha.snapTo(1f)
        launch {
            offsetY.animateTo(-80f, tween(350, easing = FastOutSlowInEasing))
            offsetY.animateTo(-30f, tween(250, easing = FastOutSlowInEasing))
        }
        launch {
            rotation.animateTo(720f, tween(500, easing = LinearEasing))
        }
        launch {
            delay(400)
            alpha.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
        }
    }

    Box(
        modifier = modifier
            .offset { IntOffset(0, with(density) { offsetY.value.dp.roundToPx() }) }
            .alpha(alpha.value)
            .graphicsLayer { rotationY = rotation.value },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_ticket),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier
                .rotate(TICKET_BASE_DEGREES)
                .size(Dimens.iconLg)
        )
    }
}

private const val CARD_HOLD_MS = 1500L
private const val CARD_EXIT_MS = 220L
private const val FOOTER_CLEARANCE = 56f
private const val TICKET_BASE_DEGREES = 90f
