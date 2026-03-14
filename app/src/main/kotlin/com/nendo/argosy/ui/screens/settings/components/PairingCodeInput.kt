package com.nendo.argosy.ui.screens.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.Dimens

private const val CODE_LENGTH = 8
private const val GROUP_SIZE = 4

@Composable
fun PairingCodeInput(
    code: String,
    onCodeChange: (String) -> Unit,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(Dimens.radiusSm)
    val filledColor = if (isFocused) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val borderColor = if (isFocused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }
    val textColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    BasicTextField(
        value = code,
        onValueChange = { newValue ->
            val filtered = newValue.uppercase().filter { it.isLetterOrDigit() }
            if (filtered.length <= CODE_LENGTH) {
                onCodeChange(filtered)
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            capitalization = KeyboardCapitalization.Characters
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier.focusRequester(focusRequester),
        decorationBox = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
                verticalAlignment = Alignment.CenterVertically
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
                        MaterialTheme.colorScheme.primary
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
