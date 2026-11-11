package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.sections.InterfaceItem
import com.nendo.argosy.ui.screens.settings.sections.InterfaceLayoutState
import com.nendo.argosy.ui.screens.settings.sections.interfaceItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.interfaceMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.interfaceSections

internal class InterfaceSectionInput(
    private val viewModel: SettingsViewModel
) : InputHandler {

    private fun layoutState() = InterfaceLayoutState.from(viewModel.uiState.value)

    override fun onUp(): InputResult {
        return if (viewModel.moveFocusWrapped(-1, interfaceMaxFocusIndex(layoutState()))) {
            InputResult.HANDLED
        } else {
            InputResult.handled(SoundType.BOUNDARY)
        }
    }

    override fun onDown(): InputResult {
        return if (viewModel.moveFocusWrapped(1, interfaceMaxFocusIndex(layoutState()))) {
            InputResult.HANDLED
        } else {
            InputResult.handled(SoundType.BOUNDARY)
        }
    }

    override fun onLeft(): InputResult = cycle(-1)

    override fun onRight(): InputResult = cycle(1)

    override fun onPrevSection(): InputResult {
        val layoutState = layoutState()
        if (viewModel.jumpToPrevSection(interfaceSections(layoutState))) {
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    override fun onNextSection(): InputResult {
        val layoutState = layoutState()
        if (viewModel.jumpToNextSection(interfaceSections(layoutState))) {
            return InputResult.HANDLED
        }
        return InputResult.UNHANDLED
    }

    private fun cycle(direction: Int): InputResult {
        val state = viewModel.uiState.value
        val layoutState = layoutState()
        when (interfaceItemAtFocusIndex(state.focusedIndex, layoutState)) {
            InterfaceItem.Language -> { viewModel.cycleAppLanguage(direction); return InputResult.HANDLED }
            InterfaceItem.UiScale -> { viewModel.adjustUiScale(direction * 5); return InputResult.HANDLED }
            InterfaceItem.ScreenOrientation -> {
                viewModel.cycleScreenOrientation(direction)
                return InputResult.HANDLED
            }
            InterfaceItem.CompactFooter -> return toggleLeftRight(direction, state.display.compactFooter) {
                viewModel.setCompactFooter(it)
            }
            InterfaceItem.HideSystemNavigationBar ->
                return toggleLeftRight(direction, state.display.hideSystemNavigationBar) {
                    viewModel.setHideSystemNavigationBar(it)
                }
            InterfaceItem.HideSystemStatusBar ->
                return toggleLeftRight(direction, state.display.hideSystemStatusBar) {
                    viewModel.setHideSystemStatusBar(it)
                }
            InterfaceItem.QuickPanelInEmulators ->
                return toggleLeftRight(direction, state.display.quickPanelInEmulators) {
                    viewModel.setQuickPanelInEmulators(it)
                }
            InterfaceItem.LockScreenArt -> return toggleLeftRight(direction, state.display.lockScreenArt) {
                viewModel.setLockScreenArt(it)
            }
            InterfaceItem.HomeApp -> return toggleLeftRight(direction, state.display.homeAppAvailable) {
                viewModel.setHomeAppAvailable(it)
            }
            InterfaceItem.StatusClock -> return toggleLeftRight(direction, state.display.showStatusClock) {
                viewModel.setShowStatusClock(it)
            }
            InterfaceItem.StatusBattery -> return toggleLeftRight(direction, state.display.showStatusBattery) {
                viewModel.setShowStatusBattery(it)
            }
            InterfaceItem.StatusNetwork -> return toggleLeftRight(direction, state.display.showStatusNetwork) {
                viewModel.setShowStatusNetwork(it)
            }
            InterfaceItem.PreLaunchRecaps -> return toggleLeftRight(direction, state.display.preLaunchRecaps) {
                viewModel.setPreLaunchRecaps(it)
            }
            InterfaceItem.MonthlyRecap -> return toggleLeftRight(direction, state.display.monthlyRecap) {
                viewModel.setMonthlyRecap(it)
            }
            InterfaceItem.Notifications -> return toggleLeftRight(direction, state.display.notificationsEnabled) {
                viewModel.setNotificationsEnabled(it)
            }
            InterfaceItem.NotifyDownloadsFinished ->
                return toggleLeftRight(direction, state.display.notifyDownloadsFinished) {
                    viewModel.setNotifyDownloadsFinished(it)
                }
            InterfaceItem.NotifyBattery -> return toggleLeftRight(direction, state.display.notifyBattery) {
                viewModel.setNotifyBattery(it)
            }
            InterfaceItem.NotifyCharging -> return toggleLeftRight(direction, state.display.notifyCharging) {
                viewModel.setNotifyCharging(it)
            }
            InterfaceItem.NotifyControllers -> return toggleLeftRight(direction, state.display.notifyControllers) {
                viewModel.setNotifyControllers(it)
            }
            else -> {}
        }
        return InputResult.UNHANDLED
    }
}
