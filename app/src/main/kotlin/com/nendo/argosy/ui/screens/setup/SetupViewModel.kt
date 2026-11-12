package com.nendo.argosy.ui.screens.setup

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.permissions.PermissionGrant
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.setup.SetupCoreDownloads
import com.nendo.argosy.data.setup.SetupStateRepository
import com.nendo.argosy.domain.model.ArgosyPermission
import com.nendo.argosy.domain.model.PermissionAvailability
import com.nendo.argosy.domain.model.ScreenRole
import com.nendo.argosy.domain.setup.SetupRouter
import com.nendo.argosy.ui.input.HapticPattern
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.common.ClientCertPasswordInputHandler
import com.nendo.argosy.ui.screens.settings.screenAssignmentsOf
import com.nendo.argosy.ui.screens.settings.screenLayoutWithRole
import com.nendo.argosy.ui.screens.settings.storeScreenLayout
import com.nendo.argosy.ui.screens.setup.components.ButtonCheckController
import com.nendo.argosy.ui.screens.setup.components.ButtonCheckExit
import com.nendo.argosy.ui.screens.setup.components.SetupDirection
import com.nendo.argosy.util.PlatformFilterLogic
import com.nendo.argosy.util.SecondaryHomeComponent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

sealed interface SetupEffect {
    data class Grant(val permission: ArgosyPermission) : SetupEffect
    data object PickRomFolder : SetupEffect
    data object PickCoverFolder : SetupEffect
    data object PickCertificate : SetupEffect
    data object PickClientCertificate : SetupEffect
    data class OpenBrowser(val url: String) : SetupEffect
    data object RequestHomeRole : SetupEffect
    data object Finish : SetupEffect
}

private val BOUNDARY = InputResult(
    handled = true,
    soundOverride = SoundType.BOUNDARY,
    hapticOverride = HapticPattern.BOUNDARY_HIT
)

private val SILENT = InputResult.handled(SoundType.SILENT)

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val application: Application,
    private val preferencesRepository: UserPreferencesRepository,
    private val setupStateRepository: SetupStateRepository,
    private val coreDownloads: SetupCoreDownloads,
    private val signIn: RommSignInDelegate,
    private val access: SetupAccessDelegate,
    private val folders: SetupFoldersDelegate,
    private val platforms: SetupPlatformsDelegate,
    private val personalize: SetupPersonalizeDelegate,
    private val handoff: SetupHandoff
) : ViewModel() {

    private val _core = MutableStateFlow(SetupUiState())

    val uiState: StateFlow<SetupUiState> = combine(
        combine(_core, signIn.state, access.state) { core, signInState, accessState ->
            core.copy(signIn = signInState, access = accessState)
        },
        folders.state,
        platforms.state,
        coreDownloads.progress,
        personalize.state
    ) { base, folderState, platformState, cores, personal ->
        base.withPersonal(personal).copy(folders = folderState, platforms = platformState, cores = cores)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SetupUiState())

    val buttonCheck: ButtonCheckController get() = personalize.buttonCheck

    private val _effects = MutableSharedFlow<SetupEffect>(extraBufferCapacity = 4)
    val effects: SharedFlow<SetupEffect> = _effects.asSharedFlow()

    private var platformsRequested = false

    init {
        signIn.bind(viewModelScope)
        access.bind(viewModelScope)
        folders.bind(viewModelScope)
        platforms.bind(viewModelScope)
        personalize.bind(viewModelScope)
        viewModelScope.launch {
            setupStateRepository.facts.collect { facts ->
                _core.update { it.copy(facts = facts, offers = it.offers.withDevice(facts)) }
            }
        }
        viewModelScope.launch {
            personalize.state.map { it.gamepadConnected }.distinctUntilChanged().collect { connected ->
                _core.update {
                    it.copy(
                        offers = it.offers.copy(buttonCheck = connected),
                        focused = if (it.page == SetupPage.CONTROLLER) null else it.focused
                    )
                }
            }
        }
        viewModelScope.launch {
            access.state.map { it.accessibilityOffered }.distinctUntilChanged().collect { offered ->
                _core.update { it.copy(offers = it.offers.copy(accessibility = offered)) }
            }
        }
        viewModelScope.launch {
            signIn.state.map { Triple(it.view, it.error?.certificate == true, it.error?.clientCertificate == true) }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    _core.update { core ->
                        if (core.page == SetupPage.LIBRARY) core.copy(focused = null, openField = null) else core
                    }
                }
        }
        followLiveScreenRoles()
        viewModelScope.launch { openAtFirstMissingItem() }
    }

    fun currentState(): SetupUiState = _core.value.withPersonal(personalize.state.value).copy(
        signIn = signIn.state.value,
        access = access.state.value,
        folders = folders.state.value,
        platforms = platforms.state.value,
        cores = coreDownloads.progress.value
    )

    private suspend fun openAtFirstMissingItem() {
        val facts = setupStateRepository.facts.first()
        val prefs = preferencesRepository.userPreferences.first()
        signIn.prefill(prefs.rommBaseUrl, facts.librarySignedIn)
        if (facts.librarySignedIn) requestPlatforms()
        _core.update {
            it.copy(
                facts = facts,
                offers = it.offers.withDevice(facts).copy(buttonCheck = personalize.gamepadConnected)
            )
        }
        val derived = SetupRouter.derive(facts)
        val plan = currentState().plan
        val carried = handoff.position.value
        val page = resumePage(entryPage(derived, facts, plan), carried?.page, plan, derived)
        _core.update { it.copy(loaded = true) }
        goTo(page, SetupDirection.FORWARD)
        if (carried != null && carried.page == page) _core.update { it.copy(focused = carried.focused) }
    }

    /**
     * Records where this run stands for a setup composed on the other screen. Called by the setup
     * on screen only, once it has loaded.
     */
    fun recordPosition() {
        val core = _core.value
        if (core.loaded) handoff.record(SetupPosition(core.page, core.focused))
    }

    private fun goTo(page: SetupPage, direction: SetupDirection) {
        val leaving = _core.value.page
        if (leaving == SetupPage.PERMISSIONS || leaving == SetupPage.ACCESSIBILITY) access.leave()
        _core.update { it.copy(page = page, direction = direction, focused = null, openField = null) }
        when (page) {
            SetupPage.LIBRARY -> if (signIn.state.value.view == LibraryView.SIGNED_IN) signIn.refreshSummary()
            SetupPage.PERMISSIONS -> access.enterPermissions()
            SetupPage.ACCESSIBILITY -> access.enterAccessibility()
            SetupPage.FOLDERS -> folders.enter()
            SetupPage.SAVE_SYNC -> showSavedSaveSyncAnswer(onlyWhenAnswered = true)
            SetupPage.SCREENS -> refreshScreens()
            SetupPage.BUTTON_CHECK -> buttonCheck.reset()
            SetupPage.DONE -> {
                folders.enter()
                showSavedSaveSyncAnswer(onlyWhenAnswered = false)
            }
            else -> Unit
        }
    }

    private fun refreshScreens() {
        viewModelScope.launch { resolveScreens() }
    }

    private suspend fun resolveScreens() {
        val resolved = DualScreenManagerHolder.instance?.resolveScreenLayout() ?: return
        _core.update { it.copy(screens = screenAssignmentsOf(resolved)) }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun followLiveScreenRoles() {
        viewModelScope.launch {
            combine(
                DualScreenManagerHolder.instanceFlow.flatMapLatest { it?.isRolesSwapped ?: flowOf(false) },
                preferencesRepository.userPreferences.map { it.screenLayouts }.distinctUntilChanged()
            ) { swapped, layouts -> swapped to layouts }
                .distinctUntilChanged()
                .drop(1)
                .collectLatest { if (_core.value.page == SetupPage.SCREENS) resolveScreens() }
        }
    }

    private fun swapScreens(): InputResult {
        val manager = DualScreenManagerHolder.instance ?: return SILENT
        manager.swapRoles()
        return InputResult.HANDLED
    }

    private fun makeControlsScreen(key: String): InputResult {
        val screens = _core.value.screens
        val target = screens.firstOrNull { it.key == key } ?: return SILENT
        if (target.role == ScreenRole.PRIMARY) return SILENT
        val next = screenLayoutWithRole(screens, key, ScreenRole.PRIMARY) ?: return SILENT
        _core.update { state ->
            state.copy(screens = screens.map { it.copy(role = next.roleFor(it.key) ?: it.role) })
        }
        viewModelScope.launch { storeScreenLayout(preferencesRepository, screens, next) }
        return InputResult.HANDLED
    }

    private fun showSavedSaveSyncAnswer(onlyWhenAnswered: Boolean) {
        viewModelScope.launch {
            if (onlyWhenAnswered && !setupStateRepository.facts.first().saveSyncAnswered) return@launch
            val enabled = preferencesRepository.userPreferences.first().saveSyncEnabled
            _core.update { it.copy(saveSyncOn = enabled) }
        }
    }

    fun recheckPage() {
        when (_core.value.page) {
            SetupPage.FOLDERS -> folders.refresh()
            SetupPage.PERMISSIONS, SetupPage.ACCESSIBILITY -> access.refresh()
            else -> Unit
        }
    }

    private fun advance() {
        val state = currentState()
        markSeen(setOfNotNull(state.page.seenToken))
        goTo(nextPage(state.page, state.plan, SetupRouter.derive(state.facts)), SetupDirection.FORWARD)
    }

    private fun markSeen(tokens: Set<String>) {
        if (tokens.isEmpty()) return
        viewModelScope.launch { setupStateRepository.markPagesSeen(tokens) }
    }

    private fun skipCustomization() {
        markSeen(CUSTOMIZATION_SEEN_TOKENS)
        goTo(SetupPage.DONE, SetupDirection.FORWARD)
    }

    private fun finishSetup() {
        viewModelScope.launch {
            setupStateRepository.finishFlow()
            handoff.clear()
            withTimeoutOrNull(FINISH_SETTLE_MS) { setupStateRepository.needsSetup.first { !it } }
            _effects.tryEmit(SetupEffect.Finish)
        }
    }

    private fun declineDefaultHome() {
        _core.update { it.copy(offers = it.offers.copy(homeDeclined = true)) }
        viewModelScope.launch { setupStateRepository.declineDefaultHome() }
        advance()
    }

    private fun requestPlatforms() {
        if (platformsRequested) return
        platformsRequested = true
        viewModelScope.launch {
            val offered = platforms.load(signIn.state.value.platformCount)
            _core.update { it.copy(offers = it.offers.copy(platforms = offered)) }
        }
    }

    fun onResume() {
        access.onResume()
        if (_core.value.page == SetupPage.FOLDERS) folders.refresh()
    }

    fun move(move: SetupMove): InputResult {
        val state = currentState()
        if (state.page == SetupPage.PLATFORMS) platformMove(state, move)?.let { return it }
        if (state.page == SetupPage.BUTTON_CHECK) return buttonCheck.move(move.dx, move.dy)
        personalMove(state, move)?.let { return it }
        val next = moveFocus(state, move) ?: return BOUNDARY
        _core.update { it.copy(focused = next) }
        return InputResult.HANDLED
    }

    private fun personalMove(state: SetupUiState, move: SetupMove): InputResult? {
        val horizontal = move == SetupMove.LEFT || move == SetupMove.RIGHT
        return when (val target = focusedTarget(state)) {
            is SetupTarget.ControllerLayoutChoice -> when {
                horizontal -> {
                    val nintendo = move == SetupMove.RIGHT
                    if (nintendo == target.nintendo) return BOUNDARY
                    _core.update { it.copy(focused = SetupTarget.ControllerLayoutChoice(nintendo)) }
                    InputResult.HANDLED
                }
                move == SetupMove.DOWN -> {
                    _core.update { it.copy(focused = SetupTarget.Action(SetupActionId.PRIMARY)) }
                    InputResult.HANDLED
                }
                else -> BOUNDARY
            }
            SetupTarget.ThemeMode, SetupTarget.Accent, SetupTarget.HomeLayout,
            SetupTarget.LibraryLayout, SetupTarget.BoxArtShape -> if (horizontal) {
                if (personalize.step(target, if (move == SetupMove.RIGHT) 1 else -1)) InputResult.HANDLED else BOUNDARY
            } else {
                null
            }
            is SetupTarget.Action -> if (move == SetupMove.UP && state.page == SetupPage.CONTROLLER && state.gamepadConnected) {
                _core.update { it.copy(focused = SetupTarget.ControllerLayoutChoice(state.controllerNintendo)) }
                InputResult.HANDLED
            } else {
                null
            }
            else -> null
        }
    }

    private fun selectControllerLayout(nintendo: Boolean) {
        personalize.selectLayout(nintendo)
        _core.update { it.copy(focused = SetupTarget.ControllerLayoutChoice(nintendo)) }
    }

    private fun platformMove(state: SetupUiState, move: SetupMove): InputResult? {
        val list = state.platforms
        return when {
            list.sortMenuOpen -> {
                when (move) {
                    SetupMove.UP -> platforms.moveSortMenu(-1)
                    SetupMove.DOWN -> platforms.moveSortMenu(1)
                    else -> Unit
                }
                InputResult.HANDLED
            }
            list.searchActive -> InputResult.HANDLED
            focusedTarget(state) == SetupTarget.PlatformHeader && (move == SetupMove.LEFT || move == SetupMove.RIGHT) -> {
                platforms.moveHeader(if (move == SetupMove.LEFT) -1 else 1)
                InputResult.HANDLED
            }
            else -> null
        }
    }

    fun confirm(): InputResult {
        val state = currentState()
        if (state.page == SetupPage.PLATFORMS && state.platforms.sortMenuOpen) {
            platforms.confirmSortMenu()
            return InputResult.HANDLED
        }
        if (state.page == SetupPage.PLATFORMS && state.platforms.searchActive) {
            platforms.closeSearch()
            return InputResult.HANDLED
        }
        if (state.page == SetupPage.BUTTON_CHECK) {
            val (result, exit) = buttonCheck.confirm()
            exit?.let(::exitButtonCheck)
            return result
        }
        val target = focusedTarget(state) ?: return SILENT
        return activate(state, target)
    }

    /**
     * Start moves focus to the page's primary action, and activates it once focus is already there.
     */
    fun startPress(): InputResult {
        val state = currentState()
        if (state.page == SetupPage.BUTTON_CHECK) {
            buttonCheck.startPress()?.let(::exitButtonCheck)
            return InputResult.HANDLED
        }
        if (state.page == SetupPage.PLATFORMS && (state.platforms.sortMenuOpen || state.platforms.searchActive)) {
            return InputResult.HANDLED
        }
        val primary = primaryTarget(state) ?: return SILENT
        if (focusedTarget(state) == primary) return activate(state, primary)
        _core.update { it.copy(focused = primary) }
        return InputResult.HANDLED
    }

    fun exitButtonCheck(exit: ButtonCheckExit) {
        buttonCheck.reset()
        when (exit) {
            ButtonCheckExit.BACK -> goTo(previousPage(SetupPage.BUTTON_CHECK, currentState().plan), SetupDirection.BACK)
            ButtonCheckExit.CONTINUE -> advance()
        }
    }

    fun tap(target: SetupTarget) {
        _core.update { it.copy(focused = target) }
        activate(currentState(), target)
    }

    private fun activate(state: SetupUiState, target: SetupTarget): InputResult = when (target) {
        SetupTarget.RommSource -> SILENT
        SetupTarget.ServerField -> openField(SetupField.SERVER_ADDRESS)
        SetupTarget.PairingField -> openField(SetupField.PAIRING_CODE)
        is SetupTarget.Permission -> activatePermission(state, target.permission)
        is SetupTarget.AccessibilityStep -> {
            access.openStep(target.step)
            InputResult.HANDLED
        }
        is SetupTarget.RomFolder -> activateRomFolder(state, target.kind)
        is SetupTarget.CoverStorage -> activateCover(state, target.kind)
        is SetupTarget.SaveSyncChoice -> {
            _core.update { it.copy(saveSyncOn = target.enabled) }
            InputResult.HANDLED
        }
        SetupTarget.PlatformHeader -> {
            platforms.confirmHeader()
            InputResult.HANDLED
        }
        is SetupTarget.Platform -> {
            val enabled = state.platforms.all.firstOrNull { it.id == target.id }?.syncEnabled == true
            platforms.toggle(target.id)
            InputResult.toggled(!enabled)
        }
        SetupTarget.CoreDownloads -> {
            platforms.toggleDownloadCores()
            InputResult.toggled(!state.platforms.downloadCores)
        }
        SetupTarget.SelectAllPlatforms -> {
            platforms.toggleAll()
            InputResult.HANDLED
        }
        is SetupTarget.Screen -> makeControlsScreen(target.key)
        SetupTarget.SwapScreens -> swapScreens()
        is SetupTarget.ControllerLayoutChoice -> {
            selectControllerLayout(target.nintendo)
            InputResult.HANDLED
        }
        SetupTarget.ThemeMode, SetupTarget.Accent, SetupTarget.HomeLayout,
        SetupTarget.LibraryLayout, SetupTarget.BoxArtShape -> SILENT
        is SetupTarget.Action -> activateAction(state, target.id)
    }

    fun setThemeMode(mode: com.nendo.argosy.data.preferences.ThemeMode) {
        _core.update { it.copy(focused = SetupTarget.ThemeMode) }
        personalize.setThemeMode(mode)
    }

    fun setAccent(color: Int?) {
        _core.update { it.copy(focused = SetupTarget.Accent) }
        personalize.setAccent(color)
    }

    fun setHomeLayout(kind: com.nendo.argosy.domain.model.HomeLayoutKind) {
        _core.update { it.copy(focused = SetupTarget.HomeLayout) }
        personalize.setHomeLayout(kind)
    }

    fun setLibraryLayout(layout: com.nendo.argosy.data.preferences.LibraryLayout) {
        _core.update { it.copy(focused = SetupTarget.LibraryLayout) }
        personalize.setLibraryLayout(layout)
    }

    fun setBoxArtShape(shape: com.nendo.argosy.data.preferences.BoxArtShape) {
        _core.update { it.copy(focused = SetupTarget.BoxArtShape) }
        personalize.setBoxArtShape(shape)
    }

    fun swapAllowed(): Boolean = setupSwapAllowed(_core.value)

    private fun openField(field: SetupField): InputResult {
        _core.update { it.copy(openField = field) }
        return InputResult.HANDLED
    }

    private fun activatePermission(state: SetupUiState, permission: ArgosyPermission): InputResult {
        if (state.access.availabilityOf(permission) != PermissionAvailability.MISSING) return SILENT
        _effects.tryEmit(SetupEffect.Grant(permission))
        return InputResult.HANDLED
    }

    private fun activateRomFolder(state: SetupUiState, kind: RomFolderKind): InputResult {
        val picks = kind == RomFolderKind.CUSTOM &&
            (state.folders.customPath == null || state.folders.selectedRom == RomFolderKind.CUSTOM)
        if (picks) _effects.tryEmit(SetupEffect.PickRomFolder) else folders.select(kind)
        return InputResult.HANDLED
    }

    private fun activateCover(state: SetupUiState, kind: CoverStorageKind): InputResult {
        val picks = kind == CoverStorageKind.PUBLIC &&
            (state.folders.publicCoverPath == null || state.folders.selectedCover == CoverStorageKind.PUBLIC)
        if (picks) _effects.tryEmit(SetupEffect.PickCoverFolder) else folders.selectCover(kind)
        return InputResult.HANDLED
    }

    private fun activateAction(state: SetupUiState, id: SetupActionId): InputResult {
        when (id) {
            SetupActionId.PRIMARY -> activatePrimary(state)
            SetupActionId.IMPORT_CERTIFICATE -> _effects.tryEmit(SetupEffect.PickCertificate)
            SetupActionId.IMPORT_CLIENT_CERTIFICATE -> _effects.tryEmit(SetupEffect.PickClientCertificate)
            SetupActionId.USE_PAIRING_CODE -> {
                signIn.useManualPairingCode()
                resetFocus()
            }
            SetupActionId.EDIT_ADDRESS, SetupActionId.DIFFERENT_SERVER -> {
                signIn.useDifferentServer()
                resetFocus()
            }
            SetupActionId.APPROVE_ON_DEVICE -> signIn.useDevicePairing()
            SetupActionId.NOT_NOW -> finishSetup()
            SetupActionId.DECLINE_HOME -> declineDefaultHome()
            SetupActionId.CHECK_AGAIN -> personalize.recheckControllers()
            SetupActionId.SKIP_CUSTOMIZATION -> skipCustomization()
        }
        return InputResult.HANDLED
    }

    private fun activatePrimary(state: SetupUiState) {
        when (state.page) {
            SetupPage.WELCOME, SetupPage.ACCESSIBILITY -> advance()
            SetupPage.LIBRARY -> activateLibraryPrimary(state)
            SetupPage.PERMISSIONS -> {
                val missing = firstUnmetRequired(state.access)
                if (missing == null) advance() else if (state.access.availabilityOf(missing) == PermissionAvailability.MISSING) {
                    _effects.tryEmit(SetupEffect.Grant(missing))
                }
            }
            SetupPage.FOLDERS -> viewModelScope.launch {
                if (folders.confirm()) advance() else _core.update { it.copy(focused = null) }
            }
            SetupPage.SAVE_SYNC -> viewModelScope.launch {
                if (setupStateRepository.answerSaveSync(state.saveSyncOn)) advance()
            }
            SetupPage.PLATFORMS -> {
                platforms.startCoreDownloads()
                advance()
            }
            SetupPage.DEFAULT_HOME -> if (state.facts.defaultHome) advance() else _effects.tryEmit(SetupEffect.RequestHomeRole)
            SetupPage.SCREENS, SetupPage.CONTROLLER, SetupPage.BUTTON_CHECK,
            SetupPage.THEME, SetupPage.HOME_LAYOUT, SetupPage.LIBRARY_LOOK -> advance()
            SetupPage.DONE -> if (offersHomeRole(state)) _effects.tryEmit(SetupEffect.RequestHomeRole) else finishSetup()
        }
    }

    private fun activateLibraryPrimary(state: SetupUiState) {
        when (state.signIn.view) {
            LibraryView.CHOICE -> {
                signIn.chooseRomm()
                resetFocus()
            }
            LibraryView.ADDRESS -> signIn.commitUrl()
            LibraryView.APPROVE -> state.signIn.deviceVerificationUrl?.let { _effects.tryEmit(SetupEffect.OpenBrowser(it)) }
            LibraryView.PAIRING -> signIn.connectWithPairingCode()
            LibraryView.SIGNED_IN -> viewModelScope.launch {
                setupStateRepository.confirmLibrarySource()
                requestPlatforms()
                advance()
            }
        }
    }

    fun back(): InputResult {
        val state = currentState()
        when {
            state.page == SetupPage.WELCOME -> return SILENT
            state.page == SetupPage.PLATFORMS && state.platforms.sortMenuOpen -> platforms.closeSortMenu()
            state.page == SetupPage.PLATFORMS && state.platforms.searchActive -> platforms.closeSearch()
            state.page == SetupPage.LIBRARY && signIn.back() -> resetFocus()
            state.page == SetupPage.BUTTON_CHECK && buttonCheck.state.value.testing -> buttonCheck.finishTest()
            else -> goTo(previousPage(state.page, state.plan), SetupDirection.BACK)
        }
        return InputResult.handled(SoundType.BACK)
    }

    fun toggleAllPlatforms(): InputResult {
        if (_core.value.page != SetupPage.PLATFORMS) return InputResult.HANDLED
        platforms.toggleAll()
        return InputResult.HANDLED
    }

    fun setServerAddress(url: String) = signIn.setUrl(url)

    fun submitServerAddress() {
        closeField()
        signIn.commitUrl()
    }

    fun setPairingCode(code: String) {
        signIn.setPairingCode(code)
        if (signIn.pairingCodeComplete) {
            _core.update { it.copy(openField = null, focused = SetupTarget.Action(SetupActionId.PRIMARY)) }
        }
    }

    fun submitPairingCode() {
        closeField()
        signIn.connectWithPairingCode()
    }

    fun closeField() {
        _core.update { it.copy(openField = null) }
    }

    private fun resetFocus() {
        _core.update { it.copy(focused = null, openField = null) }
    }

    fun onCertificatePicked(path: String) = signIn.importCertificate(path)

    fun onClientCertificatePicked(path: String) = signIn.clientCert.onFilePicked(path)

    fun setClientCertPassword(draft: String) = signIn.clientCert.setPasswordDraft(draft)

    fun submitClientCertPassword() = signIn.clientCert.submitPassword(viewModelScope)

    fun closeClientCertPassword() = signIn.clientCert.closePasswordEntry()

    val clientCertPasswordInput: InputHandler by lazy { ClientCertPasswordInputHandler(signIn.clientCert, viewModelScope) }

    fun onRomFolderPicked(path: String) = folders.setCustomPath(path)

    fun onCoverFolderPicked(path: String) = folders.setPublicCoverPath(path)

    fun grantFor(permission: ArgosyPermission): PermissionGrant = access.grantFor(permission)

    fun grantOnRoot(permission: ArgosyPermission) = access.grantOnRoot(permission)

    fun refreshAccess() = access.refresh()

    fun setPlatformSearch(query: String) = platforms.setSearchQuery(query)

    fun setPlatformSort(mode: PlatformFilterLogic.SortMode) = platforms.setSortMode(mode)

    fun cyclePlatformFilter() = platforms.cycleFilterMode()

    fun openPlatformSearch() = platforms.openSearch()

    fun closePlatformSearch() = platforms.closeSearch()

    fun openPlatformSort() = platforms.openSortMenu()

    fun closePlatformSort() = platforms.closeSortMenu()

    fun onHomeRoleReturned() {
        setupStateRepository.refresh()
        viewModelScope.launch {
            val isHome = withContext(Dispatchers.IO) { SecondaryHomeComponent.isDefaultHome(application) }
            if (!isHome) return@launch
            if (_core.value.page == SetupPage.DEFAULT_HOME) {
                _core.update { it.copy(facts = it.facts.copy(defaultHome = true)) }
                advance()
            } else {
                finishSetup()
            }
        }
    }

    fun createInputHandler() = SetupInputHandler(this)

    private companion object {
        const val FINISH_SETTLE_MS = 2_000L
    }
}
