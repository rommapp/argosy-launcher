package com.nendo.argosy.libretro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.util.clickableNoFocus

sealed class InGameMenuAction {
    data object Resume : InGameMenuAction()
    data object QuickSave : InGameMenuAction()
    data object QuickLoad : InGameMenuAction()
    data object Settings : InGameMenuAction()
    data object Cheats : InGameMenuAction()
    data object Quit : InGameMenuAction()
}

@Composable
fun InGameMenu(
    gameName: String,
    hasQuickSave: Boolean,
    cheatsAvailable: Boolean = false,
    focusedIndex: Int,
    onFocusChange: (Int) -> Unit,
    onAction: (InGameMenuAction) -> Unit,
    isHardcoreMode: Boolean = false
): InputHandler {
    val menuItems = remember(hasQuickSave, cheatsAvailable, isHardcoreMode) {
        buildList {
            add("Resume" to InGameMenuAction.Resume)
            if (!isHardcoreMode) {
                add("Quick Save" to InGameMenuAction.QuickSave)
                if (hasQuickSave) {
                    add("Quick Load" to InGameMenuAction.QuickLoad)
                }
            }
            add("Settings" to InGameMenuAction.Settings)
            if (cheatsAvailable) {
                add("Cheats" to InGameMenuAction.Cheats)
            }
            add("Quit Game" to InGameMenuAction.Quit)
        }
    }

    val isDarkTheme = isSystemInDarkTheme()
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)

    val currentFocusedIndex = rememberUpdatedState(focusedIndex)
    val currentOnFocusChange = rememberUpdatedState(onFocusChange)
    val currentOnAction = rememberUpdatedState(onAction)

    val inputHandler = remember(menuItems) {
        object : InputHandler {
            override fun onUp(): InputResult {
                val idx = currentFocusedIndex.value
                val newIndex = (idx - 1).coerceAtLeast(0)
                if (newIndex != idx) currentOnFocusChange.value(newIndex)
                return InputResult.HANDLED
            }
            override fun onDown(): InputResult {
                val idx = currentFocusedIndex.value
                val newIndex = (idx + 1).coerceAtMost(menuItems.lastIndex)
                if (newIndex != idx) currentOnFocusChange.value(newIndex)
                return InputResult.HANDLED
            }
            override fun onConfirm(): InputResult {
                menuItems.getOrNull(currentFocusedIndex.value)?.let { currentOnAction.value(it.second) }
                return InputResult.HANDLED
            }
            override fun onBack(): InputResult {
                currentOnAction.value(InGameMenuAction.Resume)
                return InputResult.HANDLED
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor)
            .focusProperties { canFocus = false },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .padding(32.dp)
                .focusProperties { canFocus = false },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isHardcoreMode) {
                    Text(
                        text = "HARDCORE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700),
                        modifier = Modifier
                            .background(
                                Color(0xFFFFD700).copy(alpha = 0.15f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = gameName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    menuItems.forEachIndexed { index, (label, action) ->
                        MenuButton(
                            text = label,
                            isFocused = index == focusedIndex,
                            onClick = { onAction(action) }
                        )
                    }
                }
            }
        }
    }

    return inputHandler
}

@Composable
private fun MenuButton(
    text: String,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isFocused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickableNoFocus(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal
        )
    }
}
