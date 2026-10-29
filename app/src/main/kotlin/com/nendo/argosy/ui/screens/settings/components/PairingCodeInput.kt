package com.nendo.argosy.ui.screens.settings.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

private const val CODE_LENGTH = 8
private const val GROUP_SIZE = 4

fun pairingCodeFilter(raw: String): String? {
    val filtered = raw.uppercase().filter { it.isLetterOrDigit() }
    return filtered.takeIf { it.length <= CODE_LENGTH }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PairingCodeInput(
    code: String,
    onCodeChange: (String) -> Unit,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onImeAction: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(Dimens.radiusSm)
    val focusAccent = LocalArgosyTheme.current.focusAccent
    val keyboard = LocalSoftwareKeyboardController.current
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var hasInputFocus by remember { mutableStateOf(false) }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    val filledColor = if (isFocused) {
        focusAccent.copy(alpha = 0.15f).compositeOver(MaterialTheme.colorScheme.surface)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val borderColor = if (isFocused) {
        focusAccent
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }
    val textColor = if (isFocused) {
        lerp(focusAccent, Color.White, 0.45f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    LaunchedEffect(hasInputFocus, imeBottom) {
        if (hasInputFocus && imeBottom > 0) {
            bringIntoViewRequester.bringIntoView()
        }
    }

    BasicTextField(
        value = code,
        onValueChange = { newValue ->
            pairingCodeFilter(newValue)?.let(onCodeChange)
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            capitalization = KeyboardCapitalization.Characters,
            imeAction = if (onImeAction != null) ImeAction.Go else ImeAction.Default
        ),
        keyboardActions = KeyboardActions(onGo = { onImeAction?.invoke() }),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .focusRequester(focusRequester)
            .onFocusChanged { hasInputFocus = it.isFocused },
        decorationBox = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickableNoFocus {
                    focusRequester.requestFocus()
                    keyboard?.show()
                }
            ) {
                for (i in 0 until CODE_LENGTH) {
                    if (i == GROUP_SIZE) {
                        Text(
                            text = "-",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val char = code.getOrNull(i)
                    val isCurrentSlot = i == code.length && isFocused
                    val slotBorder = if (isCurrentSlot) {
                        focusAccent
                    } else {
                        borderColor
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(width = 36.dp, height = 44.dp)
                            .clip(shape)
                            .background(if (char != null) filledColor else MaterialTheme.colorScheme.surface)
                            .border(
                                width = if (isCurrentSlot) Dimens.borderMedium else Dimens.borderThin,
                                color = slotBorder,
                                shape = shape
                            )
                    ) {
                        Text(
                            text = char?.toString() ?: "",
                            style = MaterialTheme.typography.titleLarge,
                            color = textColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    )
}
