package com.nendo.argosy.ui.screens.settings

import androidx.lifecycle.viewModelScope
import com.nendo.argosy.domain.model.NavigationMenuPin
import com.nendo.argosy.domain.model.ScreenLayout
import com.nendo.argosy.domain.model.ScreenLayouts
import com.nendo.argosy.domain.model.ScreenRole
import com.nendo.argosy.util.DisplayAffinityHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal fun routeNavigateToScreens(vm: SettingsViewModel) {
    routePushSection(vm, SettingsSection.SCREENS)
    routeRefreshScreens(vm)
}

internal fun routeRefreshScreens(vm: SettingsViewModel) {
    vm.viewModelScope.launch { refreshScreens(vm) }
}

internal fun routeFocusScreen(vm: SettingsViewModel, index: Int) {
    vm.setFocusIndex(index)
}

internal fun routeOpenScreenRoleModal(vm: SettingsViewModel) {
    val display = vm._uiState.value.display
    val current = display.screens.getOrNull(vm._uiState.value.focusedIndex) ?: return
    vm.displayDelegate.updateState(
        display.copy(
            screenRoleModalOpen = true,
            screenRoleModalFocus = ScreenRole.entries.indexOf(current.role).coerceAtLeast(0)
        )
    )
}

internal fun routeCloseScreenRoleModal(vm: SettingsViewModel) {
    vm.displayDelegate.updateState(vm._uiState.value.display.copy(screenRoleModalOpen = false))
}

internal fun routeFocusScreenRole(vm: SettingsViewModel, index: Int) {
    vm.displayDelegate.updateState(
        vm._uiState.value.display.copy(
            screenRoleModalFocus = index.mod(ScreenRole.entries.size)
        )
    )
}

internal fun routeMoveScreenRoleFocus(vm: SettingsViewModel, delta: Int) {
    routeFocusScreenRole(vm, vm._uiState.value.display.screenRoleModalFocus + delta)
}

internal fun screenLayoutWithRole(screens: List<ScreenAssignment>, key: String, role: ScreenRole): ScreenLayout? {
    if (role == ScreenRole.OFF && screens.count { it.role != ScreenRole.OFF } <= 1) return null
    val next = ScreenLayout(screens.associate { it.key to it.role }).withRole(key, role)
    return next.takeIf { it.primaryKey != null }
}

internal suspend fun storeScreenLayout(
    preferencesRepository: com.nendo.argosy.data.preferences.UserPreferencesRepository,
    screens: List<ScreenAssignment>,
    layout: ScreenLayout
) {
    val stored = preferencesRepository.userPreferences.first().screenLayouts
    val setKey = ScreenLayouts.setKeyOf(screens.map { it.key })
    val pin = stored.layoutFor(setKey)?.navigationMenuPin ?: layout.navigationMenuPin
    preferencesRepository.setScreenLayouts(stored.with(setKey, layout.copy(navigationMenuPin = pin)))
    com.nendo.argosy.DualScreenManagerHolder.instance?.applyStoredScreenLayout()
}

private suspend fun storeNavigationMenuPin(
    preferencesRepository: com.nendo.argosy.data.preferences.UserPreferencesRepository,
    pin: NavigationMenuPin
) {
    val resolved = com.nendo.argosy.DualScreenManagerHolder.instance?.resolveScreenLayout() ?: return
    val next = resolved.layout.copy(navigationMenuPin = pin)
    preferencesRepository.setScreenLayouts(resolved.stored.with(resolved.setKey, next))
}

internal fun routeRefreshNavigationMenu(vm: SettingsViewModel) {
    vm.viewModelScope.launch {
        val resolved = com.nendo.argosy.DualScreenManagerHolder.instance?.resolveScreenLayout() ?: return@launch
        vm.displayDelegate.setNavigationMenu(
            NavigationMenuChoices(
                pin = resolved.layout.navigationMenuPin,
                screens = resolved.attached
                    .sortedBy { it.number }
                    .map { NavigationMenuScreen(key = it.key, number = it.number) }
            )
        )
    }
}

internal fun routeCycleNavigationMenu(vm: SettingsViewModel, direction: Int) {
    val choices = vm._uiState.value.display.navigationMenu
    val options = choices.options
    val current = options.indexOf(choices.pin)
    routeSelectNavigationMenu(vm, if (current < 0) 0 else (current + direction).mod(options.size))
}

internal fun routeSelectNavigationMenu(vm: SettingsViewModel, index: Int) {
    val choices = vm._uiState.value.display.navigationMenu
    val pin = choices.options.getOrNull(index) ?: return
    if (pin == choices.pin) return
    vm.displayDelegate.setNavigationMenu(choices.copy(pin = pin))
    vm.viewModelScope.launch { storeNavigationMenuPin(vm.preferencesRepository, pin) }
}

internal fun screenAssignmentsOf(
    resolved: com.nendo.argosy.DualScreenManager.ResolvedScreenLayout
): List<ScreenAssignment> = resolved.attached.map { screen ->
    ScreenAssignment(
        key = screen.key,
        displayId = screen.displayId,
        number = screen.number,
        widthPx = screen.widthPx,
        heightPx = screen.heightPx,
        builtIn = screen.builtIn,
        role = resolved.layout.roleFor(screen.key) ?: ScreenRole.PRESENTATION
    )
}

internal fun routeAssignScreenRole(vm: SettingsViewModel, role: ScreenRole) {
    val state = vm._uiState.value
    val screens = state.display.screens
    val target = screens.getOrNull(state.focusedIndex) ?: return
    val next = screenLayoutWithRole(screens, target.key, role)
    if (next == null) {
        routeCloseScreenRoleModal(vm)
        return
    }

    val updated = screens.map { it.copy(role = next.roleFor(it.key) ?: it.role) }
    val primaryDisplayId = updated.first { it.key == next.primaryKey }.displayId
    vm.displayDelegate.updateState(
        state.display.copy(
            screens = updated,
            screenRoleModalOpen = false,
            displayRoleOverride = state.display.displayRoleOverride.followingLayout(
                DisplayAffinityHelper.rolesSwappedForPrimary(primaryDisplayId)
            )
        )
    )

    vm.viewModelScope.launch { storeScreenLayout(vm.preferencesRepository, screens, next) }
}

private suspend fun refreshScreens(vm: SettingsViewModel) {
    val resolved = com.nendo.argosy.DualScreenManagerHolder.instance?.resolveScreenLayout() ?: return

    vm.displayDelegate.updateState(
        vm._uiState.value.display.copy(
            screens = screenAssignmentsOf(resolved),
            screenRoleModalOpen = false,
            screenRoleModalFocus = 0
        )
    )
}
