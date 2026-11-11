package com.nendo.argosy.ui.components.keyboard

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * Hosts a text field whose taps open the Argosy keyboard through [onTap]. Taps reach the field
 * itself only while the field holds focus, which happens after a hand-off to the system keyboard.
 */
@Composable
fun ControllerKeyboardTapTarget(
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    var fieldFocused by remember { mutableStateOf(false) }
    Box(modifier = modifier.onFocusChanged { fieldFocused = it.hasFocus }) {
        content()
        if (enabled && !fieldFocused) {
            Box(modifier = Modifier.matchParentSize().clickableNoFocus(onClick = onTap))
        }
    }
}
