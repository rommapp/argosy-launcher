package com.nendo.argosy.ui.dualscreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import com.nendo.argosy.hardware.CompanionScreen
import com.nendo.argosy.hardware.DualGameDetailContent
import com.nendo.argosy.ui.dualscreen.gamedetail.DualGameDetailViewModel
import com.nendo.argosy.ui.dualscreen.gamedetail.GameDetailOption
import com.nendo.argosy.ui.dualscreen.home.DualFilterCategory
import com.nendo.argosy.ui.dualscreen.home.DualHomeLowerContent
import com.nendo.argosy.ui.dualscreen.home.DualHomeViewModel

@Composable
fun ControlRoleContent(
    currentScreen: CompanionScreen,
    dualHomeViewModel: DualHomeViewModel,
    dualGameDetailViewModel: DualGameDetailViewModel?,
    homeApps: List<String>,
    onGameSelected: (Long) -> Unit,
    onAppClick: (String) -> Unit,
    onCollectionsClick: () -> Unit,
    onLibraryToggle: () -> Unit,
    onViewAllClick: () -> Unit,
    onCollectionTapped: (Int) -> Unit,
    onGridGameTapped: (Int) -> Unit,
    onLetterClick: (String) -> Unit,
    onFilterOptionTapped: (Int) -> Unit,
    onFilterCategoryTapped: (DualFilterCategory) -> Unit,
    onOpenDrawer: () -> Unit,
    onDetailBack: () -> Unit,
    onOptionAction: (DualGameDetailViewModel, GameDetailOption) -> Unit,
    onScreenshotViewed: (Int) -> Unit,
    onDimTapped: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    when (currentScreen) {
        CompanionScreen.HOME -> {
            DualHomeLowerContent(
                viewModel = dualHomeViewModel,
                homeApps = homeApps,
                onGameSelected = onGameSelected,
                onAppClick = onAppClick,
                onCollectionsClick = onCollectionsClick,
                onLibraryToggle = onLibraryToggle,
                onViewAllClick = onViewAllClick,
                onCollectionTapped = onCollectionTapped,
                onGridGameTapped = onGridGameTapped,
                onLetterClick = onLetterClick,
                onFilterOptionTapped = onFilterOptionTapped,
                onFilterCategoryTapped = onFilterCategoryTapped,
                onOpenDrawer = onOpenDrawer,
                onDimTapped = onDimTapped,
                modifier = modifier
            )
        }
        CompanionScreen.GAME_DETAIL -> {
            if (dualGameDetailViewModel != null) {
                val detailState by dualGameDetailViewModel.uiState.collectAsState()
                key(detailState.gameId) {
                    DualGameDetailContent(
                        viewModel = dualGameDetailViewModel,
                        onOptionAction = { option ->
                            onOptionAction(dualGameDetailViewModel, option)
                        },
                        onScreenshotViewed = onScreenshotViewed,
                        onBack = onDetailBack,
                        onDimTapped = onDimTapped
                    )
                }
            }
        }
    }
}
