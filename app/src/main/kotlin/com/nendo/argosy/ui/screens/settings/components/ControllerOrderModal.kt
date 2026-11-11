package com.nendo.argosy.ui.screens.settings.components

import android.hardware.BatteryState
import android.os.Build
import android.view.InputDevice
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.R
import com.nendo.argosy.core.input.ControllerDetector
import com.nendo.argosy.core.input.isPhysicalGamepad
import com.nendo.argosy.ui.common.ControllerArt
import com.nendo.argosy.ui.components.BatteryIcon
import com.nendo.argosy.ui.components.FooterBarWithState
import com.nendo.argosy.ui.components.FooterHintItem
import com.nendo.argosy.ui.components.FooterHintsWithState
import com.nendo.argosy.ui.components.FooterSpacer
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.LocalGamepadInputHandler
import com.nendo.argosy.ui.input.ModalPresenceEffect
import com.nendo.argosy.ui.primitives.InputGlyph
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

private const val PLAYER_DOT_COUNT = 4

/**
 * Full-screen controller seating. [drawsOwnFooter] is for hosts without the app's root guide bar,
 * such as the game screen; everywhere else the hints go to that bar.
 */
@Composable
fun ControllerOrderModal(
    controller: ControllerSeatController,
    onDismiss: () -> Unit,
    drawsOwnFooter: Boolean = false
) {
    ModalPresenceEffect()
    val state by controller.state.collectAsState()
    val gamepadInputHandler = LocalGamepadInputHandler.current
    val currentOnDismiss = rememberUpdatedState(onDismiss)

    DisposableEffect(gamepadInputHandler, controller) {
        val listener: (KeyEvent) -> Boolean = listener@{ event ->
            val isDown = event.action == KeyEvent.ACTION_DOWN
            if (isDown && event.repeatCount > 0) return@listener true
            if (!isDown && event.action != KeyEvent.ACTION_UP) return@listener true
            val pad = event.device?.takeIf { it.isPhysicalGamepad() }
            val outcome = controller.onPadKey(gamepadInputHandler?.mapKeyToEvent(event.keyCode), isDown, pad)
            playFeedback(outcome)
            if (outcome == SeatKeyOutcome.READY || outcome == SeatKeyOutcome.CLOSE) currentOnDismiss.value()
            true
        }
        gamepadInputHandler?.setRawKeyEventListener(listener)
        onDispose { gamepadInputHandler?.setRawKeyEventListener(null) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickableNoFocus {},
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.settings_controller_order_title),
            style = MaterialTheme.typography.titleMedium,
            color = LocalArgosyTheme.current.textDim,
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.spacingLg)
        )
        Spacer(modifier = Modifier.weight(1f))
        GlyphSentence(
            template = stringResource(R.string.settings_controller_order_prompt, GLYPH_1, GLYPH_2, GLYPH_3),
            glyphs = listOf(InputButton.LB, InputButton.RB, InputButton.A),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(Dimens.spacingXl))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spacingXl),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd, Alignment.CenterHorizontally)
        ) {
            state.seats.forEachIndexed { port, holder ->
                SeatCard(
                    port = port,
                    holder = holder,
                    onRelease = { controller.releaseSeat(port) },
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .widthIn(max = Dimens.seatCardMaxWidth)
                        .fillMaxWidth()
                        .height(Dimens.seatCardHeight)
                )
            }
        }
        Spacer(modifier = Modifier.height(Dimens.spacingXl))
        GlyphSentence(
            template = stringResource(R.string.settings_controller_order_ready, GLYPH_1),
            glyphs = listOf(InputButton.A),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.clickableNoFocus { onDismiss() }
        )
        Spacer(modifier = Modifier.weight(1f))
        val hints = listOf(
            FooterHintItem(InputButton.Y, stringResource(R.string.settings_controller_order_hint_clear_all))
        )
        val onHintClick: (InputButton) -> Unit = { button -> if (button == InputButton.Y) controller.clearAll() }
        if (drawsOwnFooter) {
            FooterBarWithState(hints = hints, onHintClick = onHintClick, forceVisible = true)
        } else {
            FooterHintsWithState(hints = hints, onHintClick = onHintClick)
            FooterSpacer()
        }
    }
}

private fun playFeedback(outcome: SeatKeyOutcome) {
    val event = when (outcome) {
        SeatKeyOutcome.CLAIMED, SeatKeyOutcome.READY -> GamepadEvent.Confirm
        SeatKeyOutcome.CLOSE, SeatKeyOutcome.RELEASED -> GamepadEvent.Back
        SeatKeyOutcome.CLEARED -> GamepadEvent.SecondaryAction
        SeatKeyOutcome.NONE -> return
    }
    DualScreenManagerHolder.instance?.inputFeedback?.play(event, InputResult.HANDLED)
}

@Composable
private fun SeatCard(
    port: Int,
    holder: SeatHolder?,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusLg)
    val ink = MaterialTheme.colorScheme.onSurface
    val base = modifier.clip(shape)
    val card = if (holder != null) {
        base.background(MaterialTheme.colorScheme.surfaceVariant).clickableNoFocus(onClick = onRelease)
    } else {
        base.border(Dimens.borderThin, MaterialTheme.colorScheme.outlineVariant, shape)
    }
    Box(modifier = card.padding(Dimens.spacingMd), contentAlignment = Alignment.Center) {
        if (holder == null) {
            Text(
                text = (port + 1).toString(),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Box
        }
        val context = LocalContext.current
        val device = remember(holder.deviceId) { holder.deviceId?.let { InputDevice.getDevice(it) } }
        val kind = remember(device) { ControllerArt.kindOf(context, device) }
        val builtIn = device != null && ControllerDetector.isBuiltInPad(device)
        val name = if (builtIn) Build.MODEL else holder.name ?: stringResource(R.string.settings_controller_order_default_name)
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            PlayerDots(port = port)
            Icon(
                painter = painterResource(kind.iconRes),
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(Dimens.seatArtSize)
            )
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            PadBattery(device = device, tint = theme.textDim)
        }
    }
}

@Composable
private fun PlayerDots(port: Int) {
    val accent = LocalArgosyTheme.current.focusAccent
    val unlit = MaterialTheme.colorScheme.outlineVariant
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
        repeat(PLAYER_DOT_COUNT) { index ->
            Box(
                modifier = Modifier
                    .size(Dimens.spacingSm)
                    .background(if (index == port) accent else unlit, RoundedCornerShape(Dimens.radiusSm))
            )
        }
    }
}

@Composable
private fun PadBattery(device: InputDevice?, tint: Color) {
    val battery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) device?.batteryState else null
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || battery == null || !battery.isPresent || battery.capacity.isNaN()) {
        Spacer(modifier = Modifier.size(width = Dimens.iconMd, height = Dimens.radiusLg))
        return
    }
    val capacity = battery.capacity
    BatteryIcon(
        level = (capacity * 100).toInt(),
        isCharging = battery.status == BatteryState.STATUS_CHARGING,
        color = tint,
        modifier = Modifier.size(width = Dimens.iconMd, height = Dimens.radiusLg)
    )
}

private const val GLYPH_1 = ""
private const val GLYPH_2 = ""
private const val GLYPH_3 = ""
private val glyphMarkers = listOf(GLYPH_1, GLYPH_2, GLYPH_3)
private val SHOULDER_GLYPH_WIDTH = 1.8.em
private val FACE_GLYPH_WIDTH = 1.1.em
private val GLYPH_HEIGHT = 1.1.em

@Composable
private fun GlyphSentence(
    template: String,
    glyphs: List<InputButton>,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    val color = MaterialTheme.colorScheme.onSurface
    val text = buildAnnotatedString {
        var rest = template
        while (rest.isNotEmpty()) {
            val next = glyphMarkers.take(glyphs.size)
                .mapNotNull { marker -> rest.indexOf(marker).takeIf { it >= 0 }?.let { it to marker } }
                .minByOrNull { it.first }
            if (next == null) {
                append(rest)
                break
            }
            append(rest.substring(0, next.first))
            appendInlineContent(next.second)
            rest = rest.substring(next.first + next.second.length)
        }
    }
    val inline = glyphs.mapIndexed { index, button ->
        val width = if (button == InputButton.LB || button == InputButton.RB) SHOULDER_GLYPH_WIDTH else FACE_GLYPH_WIDTH
        glyphMarkers[index] to InlineTextContent(
            Placeholder(width, GLYPH_HEIGHT, PlaceholderVerticalAlign.Center)
        ) {
            InputGlyph(button = button, modifier = Modifier.fillMaxSize(), tint = color)
        }
    }.toMap()
    Text(
        text = text,
        inlineContent = inline,
        style = style,
        color = color,
        textAlign = TextAlign.Center,
        modifier = modifier.padding(horizontal = Dimens.spacingLg)
    )
}
