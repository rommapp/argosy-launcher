package com.nendo.argosy.libretro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.util.clickableNoFocus

@Composable
fun DebugNetplayJoinDialog(
    onDismiss: () -> Unit,
    onConfirm: (sessionId: String, hostUserId: String) -> Unit
): InputHandler {
    var sessionId by rememberSaveable { mutableStateOf("") }
    var hostUserId by rememberSaveable { mutableStateOf("") }
    var focusedIndex by rememberSaveable { mutableStateOf(0) }

    val currentOnDismiss = rememberUpdatedState(onDismiss)
    val currentOnConfirm = rememberUpdatedState(onConfirm)
    val currentFocused = rememberUpdatedState(focusedIndex)

    val isDark = isSystemInDarkTheme()
    val overlay = if (isDark) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)

    val inputHandler = remember {
        object : InputHandler {
            override fun onUp(): InputResult {
                focusedIndex = (focusedIndex - 1).coerceAtLeast(0)
                return InputResult.HANDLED
            }
            override fun onDown(): InputResult {
                focusedIndex = (focusedIndex + 1).coerceAtMost(3)
                return InputResult.HANDLED
            }
            override fun onConfirm(): InputResult {
                when (currentFocused.value) {
                    2 -> currentOnConfirm.value(sessionId, hostUserId)
                    3 -> currentOnDismiss.value()
                }
                return InputResult.HANDLED
            }
            override fun onBack(): InputResult {
                currentOnDismiss.value()
                return InputResult.HANDLED
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(overlay)
            .focusProperties { canFocus = false },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 400.dp).padding(32.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "[DEBUG] Join Netplay",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                DebugField(
                    label = "Session ID",
                    value = sessionId,
                    isFocused = focusedIndex == 0,
                    onValueChange = { sessionId = it }
                )
                DebugField(
                    label = "Host User ID",
                    value = hostUserId,
                    isFocused = focusedIndex == 1,
                    onValueChange = { hostUserId = it }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DebugButton(
                        text = "Join",
                        isFocused = focusedIndex == 2,
                        modifier = Modifier.weight(1f)
                    ) { onConfirm(sessionId, hostUserId) }
                    DebugButton(
                        text = "Cancel",
                        isFocused = focusedIndex == 3,
                        modifier = Modifier.weight(1f)
                    ) { onDismiss() }
                }
            }
        }
    }

    return inputHandler
}

@Composable
private fun DebugField(
    label: String,
    value: String,
    isFocused: Boolean,
    onValueChange: (String) -> Unit
) {
    val border = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(border.copy(alpha = 0.25f))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
        }
    }
}

@Composable
private fun DebugButton(
    text: String,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickableNoFocus(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = fg, style = MaterialTheme.typography.bodyMedium)
    }
}
