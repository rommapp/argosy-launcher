/**
 * DUAL-SCREEN COMPONENT - Lower display home content.
 * Runs in :companion process (SecondaryHomeActivity).
 * Footer hints are on upper screen only.
 */
package com.nendo.argosy.ui.dualscreen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.nendo.argosy.ui.util.touchOnly

@Composable
fun DualHomeLowerContent(
    viewModel: DualHomeViewModel,
    homeApps: List<String>,
    onGameSelected: (Long) -> Unit,
    onAppClick: (String) -> Unit,
    onDimTapped: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val isForwarding by viewModel.isForwardingToDrawer.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
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
            onAppClick = onAppClick
        )

        if (isForwarding) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .touchOnly { onDimTapped() }
            )
        }
    }
}
