/**
 * DUAL-SCREEN COMPONENT - Lower display home content.
 * Runs in :companion process (SecondaryHomeActivity).
 * Footer hints are on upper screen only.
 */
package com.nendo.argosy.ui.dualscreen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier

@Composable
fun DualHomeLowerContent(
    viewModel: DualHomeViewModel,
    homeApps: List<String>,
    onGameSelected: (Long) -> Unit,
    onAppClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    DualHomeLowerScreen(
        games = uiState.games,
        selectedIndex = uiState.selectedIndex,
        platformName = uiState.platformName,
        totalCount = uiState.totalCount,
        homeApps = homeApps,
        appBarFocused = uiState.focusZone == DualHomeFocusZone.APP_BAR,
        appBarIndex = uiState.appBarIndex,
        onGameTapped = { index -> viewModel.setSelectedIndex(index) },
        onGameSelected = onGameSelected,
        onAppClick = onAppClick,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    )
}
