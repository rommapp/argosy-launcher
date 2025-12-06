package com.nendo.argosy.ui.screens.apps

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.AppsRepository
import com.nendo.argosy.data.repository.InstalledApp
import com.nendo.argosy.ui.input.InputHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppUi(
    val packageName: String,
    val label: String,
    val icon: Drawable,
    val isHidden: Boolean = false,
    val isSystemApp: Boolean = false
)

enum class AppContextMenuItem {
    APP_INFO,
    UNINSTALL,
    TOGGLE_VISIBILITY
}

data class AppsUiState(
    val apps: List<AppUi> = emptyList(),
    val focusedIndex: Int = 0,
    val columnsCount: Int = 5,
    val isLoading: Boolean = true,
    val showHiddenApps: Boolean = false,
    val showContextMenu: Boolean = false,
    val contextMenuFocusIndex: Int = 0,
    val isReorderMode: Boolean = false
) {
    val focusedApp: AppUi?
        get() = apps.getOrNull(focusedIndex)

    val contextMenuItems: List<AppContextMenuItem>
        get() = listOf(
            AppContextMenuItem.APP_INFO,
            AppContextMenuItem.TOGGLE_VISIBILITY,
            AppContextMenuItem.UNINSTALL
        )
}

sealed class AppsEvent {
    data class Launch(val intent: Intent) : AppsEvent()
    data class OpenAppInfo(val packageName: String) : AppsEvent()
    data class RequestUninstall(val packageName: String) : AppsEvent()
}

@HiltViewModel
class AppsViewModel @Inject constructor(
    private val appsRepository: AppsRepository,
    private val preferencesRepository: UserPreferencesRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppsUiState())
    val uiState: StateFlow<AppsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AppsEvent>()
    val events: SharedFlow<AppsEvent> = _events.asSharedFlow()

    private var hiddenApps: Set<String> = emptySet()
    private var visibleSystemApps: Set<String> = emptySet()
    private var customOrder: List<String> = emptyList()
    private var originalAppsBeforeReorder: List<AppUi> = emptyList()

    init {
        loadApps()
        observePackageChanges()
    }

    private fun observePackageChanges() {
        viewModelScope.launch {
            appsRepository.packageChanges.collect {
                loadApps()
            }
        }
    }

    private fun loadApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val prefs = preferencesRepository.preferences.first()
            hiddenApps = prefs.hiddenApps
            visibleSystemApps = prefs.visibleSystemApps
            customOrder = prefs.appOrder

            val showHidden = _uiState.value.showHiddenApps
            val allApps = appsRepository.getInstalledApps(includeSystemApps = true)

            val apps = allApps.filter { app ->
                val isExplicitlyHidden = app.packageName in hiddenApps
                val isSystemAppVisible = app.isSystemApp && app.packageName in visibleSystemApps
                val isSystemAppHidden = app.isSystemApp && app.packageName !in visibleSystemApps

                if (showHidden) {
                    // Show: explicitly hidden apps + system apps that aren't explicitly shown
                    isExplicitlyHidden || isSystemAppHidden
                } else {
                    // Show: non-hidden non-system apps + system apps explicitly shown
                    (!isExplicitlyHidden && !app.isSystemApp) || isSystemAppVisible
                }
            }.let { appList -> sortApps(appList) }

            _uiState.update { state ->
                state.copy(
                    apps = apps.map { app ->
                        val isHidden = app.packageName in hiddenApps ||
                            (app.isSystemApp && app.packageName !in visibleSystemApps)
                        app.toUi(isHidden = isHidden, isSystemApp = app.isSystemApp)
                    },
                    isLoading = false,
                    focusedIndex = 0
                )
            }
        }
    }

    private fun sortApps(apps: List<InstalledApp>): List<InstalledApp> {
        if (customOrder.isEmpty()) return apps

        val orderMap = customOrder.withIndex().associate { it.value to it.index }
        return apps.sortedWith(compareBy(
            { orderMap[it.packageName] ?: Int.MAX_VALUE },
            { it.label.lowercase() }
        ))
    }

    fun toggleShowHidden() {
        _uiState.update { it.copy(showHiddenApps = !it.showHiddenApps) }
        loadApps()
    }

    fun showContextMenu() {
        if (_uiState.value.focusedApp == null) return
        _uiState.update { it.copy(showContextMenu = true, contextMenuFocusIndex = 0) }
    }

    fun dismissContextMenu() {
        _uiState.update { it.copy(showContextMenu = false, contextMenuFocusIndex = 0) }
    }

    fun moveContextMenuFocus(delta: Int) {
        _uiState.update { state ->
            val maxIndex = state.contextMenuItems.size - 1
            val newIndex = (state.contextMenuFocusIndex + delta).coerceIn(0, maxIndex)
            state.copy(contextMenuFocusIndex = newIndex)
        }
    }

    fun confirmContextMenuSelection() {
        val state = _uiState.value
        val app = state.focusedApp ?: return
        val item = state.contextMenuItems.getOrNull(state.contextMenuFocusIndex) ?: return

        when (item) {
            AppContextMenuItem.APP_INFO -> {
                viewModelScope.launch {
                    _events.emit(AppsEvent.OpenAppInfo(app.packageName))
                }
            }
            AppContextMenuItem.UNINSTALL -> {
                viewModelScope.launch {
                    _events.emit(AppsEvent.RequestUninstall(app.packageName))
                }
            }
            AppContextMenuItem.TOGGLE_VISIBILITY -> {
                toggleAppVisibility(app.packageName, app.isHidden, app.isSystemApp)
            }
        }
        dismissContextMenu()
    }

    private fun toggleAppVisibility(packageName: String, isCurrentlyHidden: Boolean, isSystemApp: Boolean) {
        viewModelScope.launch {
            if (isSystemApp) {
                // For system apps, toggle visibility via visibleSystemApps
                val newVisible = if (isCurrentlyHidden) {
                    visibleSystemApps + packageName
                } else {
                    visibleSystemApps - packageName
                }
                preferencesRepository.setVisibleSystemApps(newVisible)
                visibleSystemApps = newVisible
            } else {
                // For regular apps, toggle via hiddenApps
                val newHidden = if (isCurrentlyHidden) {
                    hiddenApps - packageName
                } else {
                    hiddenApps + packageName
                }
                preferencesRepository.setHiddenApps(newHidden)
                hiddenApps = newHidden
            }
            loadApps()
        }
    }

    fun enterReorderMode() {
        if (_uiState.value.apps.isEmpty()) return
        originalAppsBeforeReorder = _uiState.value.apps
        _uiState.update { it.copy(isReorderMode = true) }
    }

    fun saveReorderAndExit() {
        _uiState.update { it.copy(isReorderMode = false) }
        saveCustomOrder()
        originalAppsBeforeReorder = emptyList()
    }

    fun cancelReorderAndExit() {
        _uiState.update { state ->
            state.copy(
                isReorderMode = false,
                apps = originalAppsBeforeReorder,
                focusedIndex = 0
            )
        }
        originalAppsBeforeReorder = emptyList()
    }

    private fun saveCustomOrder() {
        viewModelScope.launch {
            val order = _uiState.value.apps.map { it.packageName }
            preferencesRepository.setAppOrder(order)
            customOrder = order
        }
    }

    fun moveAppInReorderMode(direction: FocusDirection) {
        _uiState.update { state ->
            if (state.apps.isEmpty()) return@update state

            val cols = state.columnsCount
            val total = state.apps.size
            val current = state.focusedIndex

            val targetIndex = when (direction) {
                FocusDirection.UP -> {
                    val target = current - cols
                    if (target >= 0) target else current
                }
                FocusDirection.DOWN -> {
                    val target = current + cols
                    if (target < total) target else current
                }
                FocusDirection.LEFT -> {
                    if (current > 0) current - 1 else current
                }
                FocusDirection.RIGHT -> {
                    if (current + 1 < total) current + 1 else current
                }
            }

            if (targetIndex == current) return@update state

            val mutableApps = state.apps.toMutableList()
            val movingApp = mutableApps.removeAt(current)
            mutableApps.add(targetIndex, movingApp)

            state.copy(apps = mutableApps, focusedIndex = targetIndex)
        }
    }

    private fun moveFocus(direction: FocusDirection) {
        _uiState.update { state ->
            if (state.apps.isEmpty()) return@update state

            val cols = state.columnsCount
            val total = state.apps.size
            val current = state.focusedIndex

            val newIndex = when (direction) {
                FocusDirection.UP -> {
                    val target = current - cols
                    if (target >= 0) target else current
                }
                FocusDirection.DOWN -> {
                    val target = current + cols
                    if (target < total) target else current
                }
                FocusDirection.LEFT -> {
                    if (current % cols > 0) current - 1 else current
                }
                FocusDirection.RIGHT -> {
                    if (current % cols < cols - 1 && current + 1 < total) current + 1 else current
                }
            }

            state.copy(focusedIndex = newIndex)
        }
    }

    private fun launchApp(packageName: String) {
        val intent = appsRepository.getLaunchIntent(packageName) ?: return
        viewModelScope.launch {
            _events.emit(AppsEvent.Launch(intent))
        }
    }

    private fun InstalledApp.toUi(isHidden: Boolean = false, isSystemApp: Boolean = false) = AppUi(
        packageName = packageName,
        label = label,
        icon = icon,
        isHidden = isHidden,
        isSystemApp = isSystemApp
    )

    fun createInputHandler(
        onDrawerToggle: () -> Unit,
        onBack: () -> Unit
    ): InputHandler = object : InputHandler {
        override fun onUp(): Boolean {
            val state = _uiState.value
            when {
                state.showContextMenu -> moveContextMenuFocus(-1)
                state.isReorderMode -> moveAppInReorderMode(FocusDirection.UP)
                else -> moveFocus(FocusDirection.UP)
            }
            return true
        }

        override fun onDown(): Boolean {
            val state = _uiState.value
            when {
                state.showContextMenu -> moveContextMenuFocus(1)
                state.isReorderMode -> moveAppInReorderMode(FocusDirection.DOWN)
                else -> moveFocus(FocusDirection.DOWN)
            }
            return true
        }

        override fun onLeft(): Boolean {
            val state = _uiState.value
            when {
                state.showContextMenu -> return false
                state.isReorderMode -> moveAppInReorderMode(FocusDirection.LEFT)
                else -> moveFocus(FocusDirection.LEFT)
            }
            return true
        }

        override fun onRight(): Boolean {
            val state = _uiState.value
            when {
                state.showContextMenu -> return false
                state.isReorderMode -> moveAppInReorderMode(FocusDirection.RIGHT)
                else -> moveFocus(FocusDirection.RIGHT)
            }
            return true
        }

        override fun onConfirm(): Boolean {
            val state = _uiState.value
            when {
                state.showContextMenu -> confirmContextMenuSelection()
                state.isReorderMode -> saveReorderAndExit()
                else -> state.focusedApp?.let { launchApp(it.packageName) }
            }
            return true
        }

        override fun onBack(): Boolean {
            val state = _uiState.value
            when {
                state.showContextMenu -> dismissContextMenu()
                state.isReorderMode -> cancelReorderAndExit()
                else -> onBack()
            }
            return true
        }

        override fun onMenu(): Boolean {
            if (_uiState.value.showContextMenu) {
                dismissContextMenu()
                return false
            }
            onDrawerToggle()
            return true
        }

        override fun onSelect(): Boolean {
            if (!_uiState.value.isReorderMode && !_uiState.value.showContextMenu) {
                showContextMenu()
            }
            return true
        }

        override fun onSecondaryAction(): Boolean {
            val state = _uiState.value
            if (!state.showContextMenu && !state.isReorderMode) {
                enterReorderMode()
            }
            return true
        }

        override fun onContextMenu(): Boolean {
            toggleShowHidden()
            return true
        }
    }
}

enum class FocusDirection {
    UP, DOWN, LEFT, RIGHT
}
