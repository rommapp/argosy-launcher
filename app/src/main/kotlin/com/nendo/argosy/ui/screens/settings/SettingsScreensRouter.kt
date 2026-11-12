package com.nendo.argosy.ui.screens.settings

import androidx.lifecycle.viewModelScope
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
    preferencesRepository.setScreenLayouts(stored.with(setKey, layout))
    com.nendo.argosy.DualScreenManagerHolder.instance?.applyStoredScreenLayout()
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
