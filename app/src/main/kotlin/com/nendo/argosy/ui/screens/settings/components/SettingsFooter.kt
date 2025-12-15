package com.nendo.argosy.ui.screens.settings.components

import androidx.compose.runtime.Composable
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton

@Composable
fun SettingsFooter() {
    FooterBar(
        hints = listOf(
            InputButton.DPAD to "Navigate",
            InputButton.SOUTH to "Select",
            InputButton.EAST to "Back"
        )
    )
}
