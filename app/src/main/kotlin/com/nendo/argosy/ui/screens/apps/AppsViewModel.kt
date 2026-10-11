package com.nendo.argosy.ui.screens.apps

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.data.cache.ImageCacheManager
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.platform.LocalPlatformIds
import com.nendo.argosy.data.preferences.GridDensity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.playstore.PlayStoreService
import com.nendo.argosy.data.repository.AppsRepository
import com.nendo.argosy.data.repository.InstalledApp
import com.nendo.argosy.ui.components.AppContextMenuItem
import com.nendo.argosy.ui.components.AppMenuRow
import com.nendo.argosy.ui.components.ListReorder
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.util.GridUtils
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
    val isOnHome: Boolean = false,
    val isOnSecondaryHome: Boolean = false
)

data class AppsUiState(
    val apps: List<AppUi> = emptyList(),
    val focusedIndex: Int = 0,
    val gridDensity: GridDensity = GridDensity.NORMAL,
    val isLoading: Boolean = true,
    val showHiddenApps: Boolean = false,
    val showContextMenu: Boolean = false,
    val contextMenuFocusIndex: Int = 0,
    val reorder: ListReorder<AppUi>? = null,
    val isTouchMode: Boolean = false,
    val hasSelectedApp: Boolean = false,
    val screenWidthDp: Int = 0,
    val hasSecondaryDisplay: Boolean = false,
    val launchableScreens: List<AppMenuRow.OpenOnScreen> = emptyList()
) {
    val columnsCount: Int
        get() = GridUtils.getAppGridColumns(gridDensity, screenWidthDp)

    val isReorderMode: Boolean
        get() = reorder != null

    val focusedApp: AppUi?
        get() = apps.getOrNull(focusedIndex)

    val contextMenuItems: List<AppMenuRow>
        get() = buildList {
            add(AppMenuRow.Action(AppContextMenuItem.APP_INFO))
            if (launchableScreens.size > 1) addAll(launchableScreens)
            add(AppMenuRow.Action(AppContextMenuItem.TOGGLE_HOME))
            if (hasSecondaryDisplay) {
                add(AppMenuRow.Action(AppContextMenuItem.TOGGLE_SECONDARY_HOME))
            }
            add(AppMenuRow.Action(AppContextMenuItem.TOGGLE_VISIBILITY))
            add(AppMenuRow.Action(AppContextMenuItem.REORDER))
            add(AppMenuRow.Action(AppContextMenuItem.UNINSTALL))
        }
}

sealed class AppsEvent {
    data class Launch(val intent: Intent, val options: android.os.Bundle? = null) : AppsEvent()
    data class OpenAppInfo(val packageName: String) : AppsEvent()
    data class RequestUninstall(val packageName: String) : AppsEvent()
}

@HiltViewModel
class AppsViewModel @Inject constructor(
    private val appsRepository: AppsRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val soundManager: SoundFeedbackManager,
    private val gameRepository: GameRepository,
    private val playStoreService: PlayStoreService,
    private val imageCacheManager: ImageCacheManager,
    private val metadataFetcher: com.nendo.argosy.data.scanner.AndroidAppMetadataFetcher,
    private val androidGameScanner: com.nendo.argosy.data.scanner.AndroidGameScanner,
    private val displayAffinityHelper: com.nendo.argosy.util.DisplayAffinityHelper,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppsUiState())
    val uiState: StateFlow<AppsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AppsEvent>()
    val events: SharedFlow<AppsEvent> = _events.asSharedFlow()

    private var hiddenApps: Set<String> = emptySet()
    private var visibleSystemApps: Set<String> = emptySet()
    private var secondaryHomeApps: Set<String> = emptySet()
    private var customOrder: List<String> = emptyList()

    init {
        _uiState.update { it.copy(hasSecondaryDisplay = displayAffinityHelper.hasSecondaryDisplay) }
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
            secondaryHomeApps = prefs.secondaryHomeApps
            customOrder = prefs.appOrder

            val showHidden = _uiState.value.showHiddenApps
            val allApps = appsRepository.getInstalledApps(includeSystemApps = true)

            val homePackages = gameRepository.getBySource(GameSource.ANDROID_APP)
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
                            isOnHome = app.packageName in homePackages,
                            isOnSecondaryHome = app.packageName in secondaryHomeApps
                        )
                    },
                    isLoading = false,
                    focusedIndex = 0,
                    reorder = null
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
        _uiState.update {
            it.copy(
                focusedIndex = index,
                showContextMenu = true,
                contextMenuFocusIndex = 0,
                launchableScreens = launchableScreens()
            )
        }
        showScreenBadges()
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun showContextMenu() {
        if (_uiState.value.focusedApp == null) return
        _uiState.update {
            it.copy(
                showContextMenu = true,
                contextMenuFocusIndex = 0,
                launchableScreens = launchableScreens()
            )
        }
        showScreenBadges()
        soundManager.play(SoundType.OPEN_MODAL)
    }

    fun dismissContextMenu() {
        _uiState.update { it.copy(showContextMenu = false, contextMenuFocusIndex = 0) }
        DualScreenManagerHolder.instance?.hideScreenNumbers()
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    private fun launchableScreens(): List<AppMenuRow.OpenOnScreen> =
        DualScreenManagerHolder.instance
            ?.focusableDisplays()
            ?.map { screen -> AppMenuRow.OpenOnScreen(screen.displayId, screen.number, screen.key) }
            .orEmpty()

    private fun showScreenBadges() {
        if (_uiState.value.launchableScreens.size <= 1) return
        DualScreenManagerHolder.instance
            ?.showScreenNumbers(com.nendo.argosy.hardware.DisplayBadgeSize.SMALL)
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

        if (item is AppMenuRow.OpenOnScreen) {
            launchApp(app.packageName, pinnedScreenKey = item.screenKey)
            dismissContextMenu()
            return
        }
        when ((item as AppMenuRow.Action).item) {
            AppContextMenuItem.APP_INFO -> {
                viewModelScope.launch {
                    _events.emit(AppsEvent.OpenAppInfo(app.packageName))
                }
            }
            AppContextMenuItem.TOGGLE_HOME -> {
                toggleHomeStatus(app.packageName, app.label, app.isOnHome)
            }
            AppContextMenuItem.TOGGLE_SECONDARY_HOME -> {
                toggleSecondaryHomeStatus(app.packageName, app.isOnSecondaryHome)
            }
            AppContextMenuItem.TOGGLE_VISIBILITY -> {
                toggleAppVisibility(app.packageName, app.isHidden, app.isSystemApp)
            }
            AppContextMenuItem.REORDER -> {
                enterReorderMode()
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
            val existing = gameRepository.getByPackageName(packageName)

            if (isCurrentlyOnHome) {
                existing?.let { game ->
                    if (game.rommId != null) {
                        gameRepository.update(game.copy(
                            packageName = null,
                            source = GameSource.ROMM_REMOTE
                        ))
                    } else {
                        gameRepository.delete(game)
                    }
                }
            } else {
                val gameId: Long
                if (existing != null) {
                    gameId = existing.id
                } else {
                    androidGameScanner.ensureAndroidPlatformExists()
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
                        packageName = packageName
                    )
                    gameId = gameRepository.insert(game)
                }
                fetchMetadataForApp(gameId, packageName)
            }
            androidGameScanner.refreshGameCount()

            soundManager.play(if (isCurrentlyOnHome) SoundType.UNFAVORITE else SoundType.FAVORITE)
            loadApps()
        }
    }

    private fun toggleSecondaryHomeStatus(packageName: String, isCurrentlyOnSecondaryHome: Boolean) {
        viewModelScope.launch {
            val newSecondaryHomeApps = if (isCurrentlyOnSecondaryHome) {
                secondaryHomeApps - packageName
            } else {
                secondaryHomeApps + packageName
            }
            preferencesRepository.setSecondaryHomeApps(newSecondaryHomeApps)
            secondaryHomeApps = newSecondaryHomeApps
            soundManager.play(if (isCurrentlyOnSecondaryHome) SoundType.UNFAVORITE else SoundType.FAVORITE)

            _uiState.update { state ->
                state.copy(
                    apps = state.apps.map { app ->
                        if (app.packageName == packageName) {
                            app.copy(isOnSecondaryHome = !isCurrentlyOnSecondaryHome)
                        } else {
                            app
                        }
                    }
                )
            }
        }
    }

    private suspend fun fetchMetadataForApp(gameId: Long, packageName: String) =
        metadataFetcher.fetch(gameId, packageName)

    fun handleSecondaryAction() {
        val state = _uiState.value
        if (state.showContextMenu) return
        if (state.isReorderMode) {
            saveReorderAndExit()
            return
        }
        if (state.hasSecondaryDisplay) {
            val topDisplayId = launchableScreens().firstOrNull { it.number == 1 }?.displayId
                ?: return
            state.focusedApp?.let {
                launchApp(it.packageName, overrideDisplayId = topDisplayId)
            }
        } else {
            enterReorderMode()
        }
    }

    fun enterReorderMode() {
        _uiState.update { state ->
            if (state.isReorderMode) return@update state
            val reorder = ListReorder.lift(state.apps, state.focusedIndex) ?: return@update state
            state.copy(reorder = reorder)
        }
    }

    fun saveReorderAndExit() {
        var committed: List<AppUi>? = null
        _uiState.update { state ->
            val reorder = state.reorder ?: return@update state
            committed = reorder.changedOrder(state.apps)
            state.copy(reorder = null)
        }
        val apps = committed ?: return
        saveCustomOrder(apps.map { it.packageName })
    }

    fun cancelReorderAndExit() {
        _uiState.update { state ->
            val reorder = state.reorder ?: return@update state
            state.copy(
                reorder = null,
                apps = reorder.backup,
                focusedIndex = reorder.originIndex
            )
        }
    }

    private fun saveCustomOrder(order: List<String>) {
        viewModelScope.launch {
            preferencesRepository.setAppOrder(order)
            customOrder = order
        }
    }

    fun moveAppInReorderMode(direction: FocusDirection) {
        _uiState.update { state ->
            val reorder = state.reorder ?: return@update state
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

            val step = reorder.moveTo(state.apps, targetIndex)
            if (!step.moved) return@update state
            state.copy(apps = step.items, reorder = step.reorder, focusedIndex = step.reorder.heldIndex)
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

        if (state.isReorderMode) {
            _uiState.update { current ->
                val reorder = current.reorder ?: return@update current
                val step = reorder.moveTo(current.apps, index)
                current.copy(apps = step.items, reorder = step.reorder, focusedIndex = step.reorder.heldIndex)
            }
            return
        }

        _uiState.update { it.copy(focusedIndex = index, hasSelectedApp = true, isTouchMode = true) }
        launchAppAt(index)
    }

    fun handleAppLongPress(index: Int) {
        val state = _uiState.value
        if (index < 0 || index >= state.apps.size) return
        if (state.isReorderMode) return

        if (index != state.focusedIndex) {
            _uiState.update { it.copy(focusedIndex = index, hasSelectedApp = true, isTouchMode = true) }
        }
        showContextMenu()
    }

    private fun launchApp(
        packageName: String,
        pinnedScreenKey: String? = null,
        overrideDisplayId: Int? = null
    ) {
        val intent = appsRepository.getLaunchIntent(packageName) ?: return
        viewModelScope.launch {
            if (pinnedScreenKey != null) {
                preferencesRepository.setAppDisplayTarget(packageName, pinnedScreenKey)
            }
            val options = if (overrideDisplayId != null) {
                displayAffinityHelper.getActivityOptions(
                    forEmulator = false,
                    overrideDisplayId = overrideDisplayId
                )
            } else {
                val dsm = DualScreenManagerHolder.instance
                displayAffinityHelper.getAppLaunchOptions(
                    preferredScreenKey = pinnedScreenKey
                        ?: preferencesRepository.preferences.first().appDisplayTargets[packageName],
                    rolesSwapped = dsm?.isRolesSwapped?.value == true,
                    occupiedDisplayId = dsm?.emulatorDisplayId
                )
            }
            _events.emit(AppsEvent.Launch(intent, options))
        }
    }

    private fun InstalledApp.toUi(
        isHidden: Boolean = false,
        isSystemApp: Boolean = false,
        isOnHome: Boolean = false,
        isOnSecondaryHome: Boolean = false
    ) = AppUi(
        packageName = packageName,
        label = label,
        isHidden = isHidden,
        isSystemApp = isSystemApp,
        isOnHome = isOnHome,
        isOnSecondaryHome = isOnSecondaryHome
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
            return when {
                state.showContextMenu -> InputResult.HANDLED
                state.isReorderMode -> {
                    moveAppInReorderMode(FocusDirection.LEFT)
                    InputResult.HANDLED
                }
                state.apps.isEmpty() -> InputResult.UNHANDLED
                else -> {
                    val cols = state.columnsCount.coerceAtLeast(1)
                    if (state.focusedIndex % cols == 0) {
                        InputResult.UNHANDLED
                    } else {
                        moveFocus(FocusDirection.LEFT)
                        InputResult.HANDLED
                    }
                }
            }
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
            val state = _uiState.value
            if (state.isReorderMode || state.showContextMenu) return InputResult.HANDLED
            if (com.nendo.argosy.ui.dualscreen.selectSwapsRoles()) return InputResult.UNHANDLED
            showContextMenu()
            return InputResult.HANDLED
        }

        override fun onLongSelect(): InputResult {
            val state = _uiState.value
            if (state.isReorderMode || state.showContextMenu) return InputResult.HANDLED
            return InputResult.UNHANDLED
        }

        override fun onLongConfirm(): InputResult {
            val state = _uiState.value
            if (state.isReorderMode || state.showContextMenu) return InputResult.HANDLED
            showContextMenu()
            return InputResult.HANDLED
        }

        override fun onSecondaryAction(): InputResult {
            handleSecondaryAction()
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
