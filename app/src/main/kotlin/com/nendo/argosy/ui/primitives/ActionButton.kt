package com.nendo.argosy.ui.primitives

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.ui.util.pressScale

/** V2 action button: wrapped fill (fill inside a brighter rim) that solidifies on focus; never moves. */
@Composable
fun ActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focused: Boolean = false,
    primary: Boolean = false,
    accentColor: Color? = null,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val theme = LocalArgosyTheme.current
    val accent = accentColor ?: theme.focusAccent
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(Dimens.radiusControl)
    val restFill = if (primary) accent.copy(alpha = ComponentDefaults.ActionButton.restFillAlpha) else theme.surfaceElevated
    val focusedFill = if (primary) lerp(accent, Color.White, 0.18f)
        else accent.copy(alpha = 0.2f).compositeOver(theme.surfaceElevated)
    val restRim = if (primary) accent.copy(alpha = ComponentDefaults.ActionButton.restRimAlpha) else theme.hairlineHigh
    val focusedRim = if (primary) lerp(accent, Color.White, 0.6f) else accent
    val fill by animateColorAsState(
        targetValue = when {
            !enabled -> restFill.copy(alpha = restFill.alpha * ComponentDefaults.ActionButton.disabledFillAlpha)
            focused -> focusedFill
            else -> restFill
        },
        animationSpec = Motion.focusColorSpec,
        label = "action-fill",
    )
    val rim by animateColorAsState(
        targetValue = when {
            !enabled -> restRim.copy(alpha = restRim.alpha * ComponentDefaults.ActionButton.disabledFillAlpha)
            focused -> focusedRim
            else -> restRim
        },
        animationSpec = Motion.focusColorSpec,
        label = "action-rim",
    )
    Box(
        modifier = modifier
            .pressScale(interaction)
            .heightIn(min = Dimens.buttonHeight)
            .clip(shape)
            .background(fill, shape)
            .border(width = Dimens.borderThin, color = rim, shape = shape)
            .clickableNoFocus(interactionSource = interaction, enabled = enabled, onClick = onClick)
            .padding(horizontal = Dimens.buttonPaddingH, vertical = Dimens.buttonPaddingV),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

@Composable
fun actionButtonLabelColor(
    focused: Boolean,
    primary: Boolean = false,
    accentColor: Color? = null,
    enabled: Boolean = true,
): Color {
    val theme = LocalArgosyTheme.current
    return when {
        !enabled -> theme.textMute
        primary -> Color.White
        focused -> lerp(accentColor ?: theme.focusAccent, Color.White, 0.45f)
        else -> theme.textPrimary
    }
}

@Composable
fun ActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focused: Boolean = false,
    primary: Boolean = false,
    accentColor: Color? = null,
    enabled: Boolean = true,
    glyph: InputButton? = null,
) {
    val labelColor = actionButtonLabelColor(focused = focused, primary = primary, accentColor = accentColor, enabled = enabled)
    ActionButton(
        onClick = onClick,
        modifier = modifier,
        focused = focused,
        primary = primary,
        accentColor = accentColor,
        enabled = enabled,
    ) {
        if (glyph != null) {
            InputGlyph(button = glyph, tint = labelColor)
            Spacer(modifier = Modifier.width(Dimens.spacingSm))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
