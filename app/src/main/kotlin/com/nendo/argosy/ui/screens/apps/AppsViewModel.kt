package com.nendo.argosy.ui.screens.apps

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.platform.LocalPlatformIds
import com.nendo.argosy.data.preferences.GridDensity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.AppsRepository
import com.nendo.argosy.data.repository.InstalledApp
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.input.SoundType
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
    val isHidden: Boolean = false,
    val isSystemApp: Boolean = false,
    val isOnHome: Boolean = false
)

enum class AppContextMenuItem {
    APP_INFO,
    TOGGLE_HOME,
    TOGGLE_VISIBILITY,
    UNINSTALL
}

data class AppsUiState(
    val apps: List<AppUi> = emptyList(),
    val focusedIndex: Int = 0,
    val gridDensity: GridDensity = GridDensity.NORMAL,
    val isLoading: Boolean = true,
    val showHiddenApps: Boolean = false,
    val showContextMenu: Boolean = false,
    val contextMenuFocusIndex: Int = 0,
    val isReorderMode: Boolean = false,
    val isTouchMode: Boolean = false,
    val hasSelectedApp: Boolean = false,
    val screenWidthDp: Int = 0
) {
    val columnsCount: Int
        get() {
            val baseColumns = when (gridDensity) {
                GridDensity.COMPACT -> 5
                GridDensity.NORMAL -> 4
                GridDensity.SPACIOUS -> 3
            }
            return if (screenWidthDp > 900) {
                (baseColumns * 1.5f).toInt()
            } else {
                baseColumns
            }
        }

    val focusedApp: AppUi?
        get() = apps.getOrNull(focusedIndex)

    val contextMenuItems: List<AppContextMenuItem>
        get() = listOf(
            AppContextMenuItem.APP_INFO,
            AppContextMenuItem.TOGGLE_HOME,
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
    private val soundManager: SoundFeedbackManager,
    private val gameDao: GameDao,
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
        observeGridDensity()
    }

    private fun observePackageChanges() {
        viewModelScope.launch {
            appsRepository.packageChanges.collect {
                loadApps()
            }
        }
    }

    private fun observeGridDensity() {
        viewModelScope.launch {
            preferencesRepository.preferences.collect { prefs ->
                _uiState.update { it.copy(gridDensity = prefs.gridDensity) }
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

            val homePackages = gameDao.getBySource(GameSource.ANDROID_APP)
                .filter { it.isFavorite && it.packageName != null }
                .mapNotNull { it.packageName }
                .toSet()

            val apps = allApps
                .filter { app -> shouldShowApp(app, showHidden) }
                .let { appList -> sortApps(appList) }

            _uiState.update { state ->
                state.copy(
                    apps = apps.map { app ->
                        val isHidden = app.packageName in hiddenApps ||
                            (app.isSystemApp && app.packageName !in visibleSystemApps)
                        app.toUi(
                            isHidden = isHidden,
                            isSystemApp = app.isSystemApp,
                            isOnHome = app.packageName in homePackages
                        )
                    },
                    isLoading = false,
                    focusedIndex = 0
                )
            }
        }
    }

    private fun shouldShowApp(app: InstalledApp, showHidden: Boolean): Boolean {
        val isExplicitlyHidden = app.packageName in hiddenApps
        val isSystemAppVisible = app.isSystemApp && app.packageName in visibleSystemApps
        val isSystemAppHidden = app.isSystemApp && app.packageName !in visibleSystemApps

        return if (showHidden) {
            isExplicitlyHidden || isSystemAppHidden
        } else {
            (!isExplicitlyHidden && !app.isSystemApp) || isSystemAppVisible
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

    fun launchAppAt(index: Int) {
        val app = _uiState.value.apps.getOrNull(index) ?: return
        launchApp(app.packageName)
    }

    fun showContextMenuAt(index: Int) {
        val apps = _uiState.value.apps
        if (index < 0 || index >= apps.size) return
        _uiState.update { it.copy(focusedIndex = index, showContextMenu = true, contextMenuFocusIndex = 0) }
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun showContextMenu() {
        if (_uiState.value.focusedApp == null) return
        _uiState.update { it.copy(showContextMenu = true, contextMenuFocusIndex = 0) }
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun dismissContextMenu() {
        _uiState.update { it.copy(showContextMenu = false, contextMenuFocusIndex = 0) }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun selectContextMenuItem(index: Int) {
        _uiState.update { it.copy(contextMenuFocusIndex = index) }
        confirmContextMenuSelection()
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
            AppContextMenuItem.TOGGLE_HOME -> {
                toggleHomeStatus(app.packageName, app.label, app.isOnHome)
            }
            AppContextMenuItem.TOGGLE_VISIBILITY -> {
                toggleAppVisibility(app.packageName, app.isHidden, app.isSystemApp)
            }
            AppContextMenuItem.UNINSTALL -> {
                viewModelScope.launch {
                    _events.emit(AppsEvent.RequestUninstall(app.packageName))
                }
            }
        }
        dismissContextMenu()
    }

    private fun toggleAppVisibility(packageName: String, isCurrentlyHidden: Boolean, isSystemApp: Boolean) {
        viewModelScope.launch {
            if (isSystemApp) {
                val newVisible = if (isCurrentlyHidden) {
                    visibleSystemApps + packageName
                } else {
                    visibleSystemApps - packageName
                }
                preferencesRepository.setVisibleSystemApps(newVisible)
                visibleSystemApps = newVisible
            } else {
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

    private fun toggleHomeStatus(packageName: String, label: String, isCurrentlyOnHome: Boolean) {
        viewModelScope.launch {
            val existing = gameDao.getByPackageName(packageName)

            if (isCurrentlyOnHome) {
                existing?.let { gameDao.updateFavorite(it.id, false) }
            } else {
                if (existing != null) {
                    gameDao.updateFavorite(existing.id, true)
                } else {
                    val sortTitle = label.lowercase()
                        .removePrefix("the ")
                        .removePrefix("a ")
                        .removePrefix("an ")
                        .trim()
                    val game = GameEntity(
                        platformId = LocalPlatformIds.ANDROID,
                        platformSlug = "android",
                        title = label,
                        sortTitle = sortTitle,
                        localPath = null,
                        rommId = null,
                        igdbId = null,
                        source = GameSource.ANDROID_APP,
                        packageName = packageName,
                        isFavorite = true
                    )
                    gameDao.insert(game)
                }
            }

            soundManager.play(if (isCurrentlyOnHome) SoundType.UNFAVORITE else SoundType.FAVORITE)
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

            state.copy(focusedIndex = newIndex, isTouchMode = false)
        }
    }

    fun enterTouchMode() {
        _uiState.update { it.copy(isTouchMode = true, hasSelectedApp = false) }
    }

    fun updateScreenWidth(widthDp: Int) {
        if (_uiState.value.screenWidthDp != widthDp) {
            _uiState.update { it.copy(screenWidthDp = widthDp) }
        }
    }

    fun handleAppTap(index: Int) {
        val state = _uiState.value
        if (index < 0 || index >= state.apps.size) return

        _uiState.update { it.copy(focusedIndex = index, hasSelectedApp = true, isTouchMode = true) }
        launchAppAt(index)
    }

    fun handleAppLongPress(index: Int) {
        val state = _uiState.value
        if (index < 0 || index >= state.apps.size) return

        if (index != state.focusedIndex) {
            _uiState.update { it.copy(focusedIndex = index, hasSelectedApp = true, isTouchMode = true) }
        }
        showContextMenu()
    }

    private fun launchApp(packageName: String) {
        val intent = appsRepository.getLaunchIntent(packageName) ?: return
        viewModelScope.launch {
            _events.emit(AppsEvent.Launch(intent))
        }
    }

    private fun InstalledApp.toUi(
        isHidden: Boolean = false,
        isSystemApp: Boolean = false,
        isOnHome: Boolean = false
    ) = AppUi(
        packageName = packageName,
        label = label,
        isHidden = isHidden,
        isSystemApp = isSystemApp,
        isOnHome = isOnHome
    )

    fun createInputHandler(
        onDrawerToggle: () -> Unit,
        onBack: () -> Unit
    ): InputHandler = object : InputHandler {
        override fun onUp(): InputResult {
            val state = _uiState.value
            when {
                state.showContextMenu -> moveContextMenuFocus(-1)
                state.isReorderMode -> moveAppInReorderMode(FocusDirection.UP)
                else -> moveFocus(FocusDirection.UP)
            }
            return InputResult.HANDLED
        }

        override fun onDown(): InputResult {
            val state = _uiState.value
            when {
                state.showContextMenu -> moveContextMenuFocus(1)
                state.isReorderMode -> moveAppInReorderMode(FocusDirection.DOWN)
                else -> moveFocus(FocusDirection.DOWN)
            }
            return InputResult.HANDLED
        }

        override fun onLeft(): InputResult {
            val state = _uiState.value
            when {
                state.showContextMenu -> return InputResult.UNHANDLED
                state.isReorderMode -> moveAppInReorderMode(FocusDirection.LEFT)
                else -> moveFocus(FocusDirection.LEFT)
            }
            return InputResult.HANDLED
        }

        override fun onRight(): InputResult {
            val state = _uiState.value
            when {
                state.showContextMenu -> return InputResult.UNHANDLED
                state.isReorderMode -> moveAppInReorderMode(FocusDirection.RIGHT)
                else -> moveFocus(FocusDirection.RIGHT)
            }
            return InputResult.HANDLED
        }

        override fun onConfirm(): InputResult {
            val state = _uiState.value
            when {
                state.showContextMenu -> confirmContextMenuSelection()
                state.isReorderMode -> saveReorderAndExit()
                else -> state.focusedApp?.let { launchApp(it.packageName) }
            }
            return InputResult.HANDLED
        }

        override fun onBack(): InputResult {
            val state = _uiState.value
            when {
                state.showContextMenu -> dismissContextMenu()
                state.isReorderMode -> cancelReorderAndExit()
                else -> onBack()
            }
            return InputResult.HANDLED
        }

        override fun onMenu(): InputResult {
            if (_uiState.value.showContextMenu) {
                dismissContextMenu()
                return InputResult.UNHANDLED
            }
            onDrawerToggle()
            return InputResult.HANDLED
        }

        override fun onSelect(): InputResult {
            if (!_uiState.value.isReorderMode && !_uiState.value.showContextMenu) {
                showContextMenu()
            }
            return InputResult.HANDLED
        }

        override fun onSecondaryAction(): InputResult {
            val state = _uiState.value
            if (!state.showContextMenu && !state.isReorderMode) {
                enterReorderMode()
            }
            return InputResult.HANDLED
        }

        override fun onContextMenu(): InputResult {
            toggleShowHidden()
            return InputResult.HANDLED
        }
    }
}

enum class FocusDirection {
    UP, DOWN, LEFT, RIGHT
}
