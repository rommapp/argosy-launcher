package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nendo.argosy.ui.components.HomeLayoutPicker
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.theme.Dimens

/**
 * Hosts the layout picker. The picker owns no state of its own so the same composable can be
 * dropped into a setup wizard; this screen supplies the stored settings and persists what comes
 * back, with no apply step so the preview above always matches what is saved.
 */
@Composable
fun HomeLayoutSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
) {
    HomeLayoutPicker(
        settings = uiState.display.homeLayout,
        focusedIndex = uiState.focusedIndex,
        onSettingsChange = { viewModel.setHomeLayout(it) },
        onFocusIndex = { viewModel.setFocusIndex(it) },
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.spacingLg)
    )
}
