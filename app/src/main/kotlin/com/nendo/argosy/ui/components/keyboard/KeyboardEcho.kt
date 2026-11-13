package com.nendo.argosy.ui.components.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.primitives.InputGlyph
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.generated.ComponentDefaults

@Composable
internal fun KeyboardEcho(
    state: KeyboardUiState,
    compact: Boolean,
    multiline: Boolean,
    height: Dp,
    onCaretTap: (Int) -> Unit,
    onPasteTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val pasteLabel = stringResource(R.string.ui_keyboard_paste)
    val shape = RoundedCornerShape(Dimens.radiusControl)
    val error = state.error
    val labelText = error ?: state.label
    val labelColor = if (error != null) theme.destructive else theme.textDim
    val password = state.fieldType == KeyboardFieldType.PASSWORD
    val mask = remember { PasswordVisualTransformation().mask }
    val shown = if (password) String(CharArray(state.text.length) { mask }) else state.text
    val announcement = if (password) {
        pluralStringResource(R.plurals.ui_keyboard_password_announce, state.text.length, state.text.length)
    } else {
        state.text
    }
    val counter = echoCounter(state)
    val rim = if (error != null) Modifier.border(Dimens.borderThin, theme.destructive, shape) else Modifier

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(theme.surfaceRaised)
            .then(rim)
            .padding(horizontal = Dimens.spacingSm)
            .clearAndSetSemantics {
                contentDescription = labelText
                stateDescription = announcement
                liveRegion = LiveRegionMode.Polite
                if (state.pasteAvailable) {
                    customActions = listOf(
                        CustomAccessibilityAction(pasteLabel) {
                            onPasteTap()
                            true
                        }
                    )
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        InputGlyph(button = InputButton.LB, size = Dimens.iconMd)
        if (compact) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                EchoLabel(labelText, labelColor)
                EchoText(shown, state.caret, state.placeholder, multiline = false, onCaretTap, Modifier.weight(1f))
            }
        } else {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                EchoLabel(labelText, labelColor)
                EchoText(shown, state.caret, state.placeholder, multiline, onCaretTap, Modifier.fillMaxWidth())
            }
        }
        if (counter != null) {
            Text(
                text = counter,
                style = MaterialTheme.typography.labelMedium,
                color = LocalLauncherTheme.current.semanticColors.warning,
                maxLines = 1
            )
        }
        InputGlyph(button = InputButton.RB, size = Dimens.iconMd)
        if (state.pasteAvailable) {
            KeySurface(
                fill = theme.surfaceElevated,
                focused = state.pasteFocused,
                enabled = state.enabled,
                onTap = onPasteTap,
                onLongPress = onPasteTap,
                modifier = Modifier.fillMaxHeight().padding(vertical = Dimens.spacingXs)
            ) {
                InputGlyph(button = InputButton.RT, size = Dimens.iconMd)
                Text(
                    text = pasteLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (state.pasteFocused) theme.focusAccent else theme.textPrimary,
                    maxLines = 1,
                    modifier = Modifier.padding(start = Dimens.spacingXs)
                )
            }
        }
    }
}

@Composable
private fun echoCounter(state: KeyboardUiState): String? {
    val max = state.maxLength ?: return null
    val length = state.text.length
    val nearLimit = (max - length) <= max * ComponentDefaults.ControllerKeyboard.counterNearLimitPercent / 100f
    return if (nearLimit) stringResource(R.string.ui_keyboard_counter, length, max) else null
}

@Composable
private fun EchoLabel(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun EchoText(
    text: String,
    caret: Int,
    placeholder: String?,
    multiline: Boolean,
    onCaretTap: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val caretColor = theme.focusAccent
    val caretWidth = Dimens.borderMedium
    val scroll = rememberScrollState()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val empty = text.isEmpty()
    val caretIndex = if (empty) 0 else caret.coerceIn(0, text.length)

    LaunchedEffect(caretIndex, layout, multiline) {
        val result = layout
        if (multiline || result == null) return@LaunchedEffect
        val rect = result.getCursorRect(caretIndex)
        val start = scroll.value
        val end = start + scroll.viewportSize
        val target = when {
            rect.left < start -> rect.left.toInt()
            rect.right > end -> (rect.right - scroll.viewportSize).toInt()
            else -> start
        }
        scroll.scrollTo(target)
    }

    Box(modifier = if (multiline) modifier else modifier.horizontalScroll(scroll)) {
        Text(
            text = if (empty) placeholder.orEmpty() else text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (empty) theme.textMute else theme.textPrimary,
            softWrap = multiline,
            maxLines = if (multiline) Int.MAX_VALUE else 1,
            onTextLayout = { layout = it },
            modifier = Modifier
                .padding(end = caretWidth)
                .pointerInput(empty, onCaretTap) {
                    detectTapGestures { position ->
                        val result = layout ?: return@detectTapGestures
                        onCaretTap(if (empty) 0 else result.getOffsetForPosition(position))
                    }
                }
                .drawWithContent {
                    drawContent()
                    val result = layout ?: return@drawWithContent
                    val rect = result.getCursorRect(caretIndex)
                    drawRect(
                        color = caretColor,
                        topLeft = Offset(rect.left, rect.top),
                        size = Size(caretWidth.toPx(), rect.height)
                    )
                }
        )
    }
}
