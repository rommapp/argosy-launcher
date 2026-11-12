package com.nendo.argosy.ui.screens.setup

import android.app.Application
import com.nendo.argosy.DualScreenManager
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.R
import com.nendo.argosy.core.input.ConnectedControllerTracker
import com.nendo.argosy.core.input.ConnectedPad
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.permissions.PermissionRegistry
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.ConnectionState
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.remote.ssl.UserCertStore
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.setup.SetupCoreDownloads
import com.nendo.argosy.data.setup.SetupFolderProbe
import com.nendo.argosy.data.setup.SetupStateRepository
import com.nendo.argosy.domain.model.CoreDownloadProgress
import com.nendo.argosy.domain.model.PermissionSnapshot
import com.nendo.argosy.domain.model.ScreenLayout
import com.nendo.argosy.domain.model.ScreenLayouts
import com.nendo.argosy.domain.model.ScreenRole
import com.nendo.argosy.domain.model.SetupFacts
import com.nendo.argosy.ui.screens.setup.components.ButtonCheckFocus
import com.nendo.argosy.ui.screens.setup.components.SetupDirection
import com.nendo.argosy.util.AttachedScreen
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SetupViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val application = mockk<Application>(relaxed = true)
    private val preferences = mockk<UserPreferencesRepository>(relaxed = true)
    private val setupState = mockk<SetupStateRepository>(relaxed = true)
    private val registry = mockk<PermissionRegistry>(relaxed = true)
    private val romM = mockk<RomMRepository>(relaxed = true)
    private val certStore = mockk<UserCertStore>(relaxed = true)
    private val coreDownloads = mockk<SetupCoreDownloads>(relaxed = true)
    private val probe = mockk<SetupFolderProbe>(relaxed = true)
    private val platformRepository = mockk<PlatformRepository>(relaxed = true)
    private val controllerTracker = mockk<ConnectedControllerTracker>(relaxed = true)
    private val pads = MutableStateFlow<List<ConnectedPad>>(emptyList())

    private val requiredMet = SetupFacts(
        librarySignedIn = true,
        librarySourceConfirmed = true,
        allFilesAccess = true,
        usageAccess = true,
        notifications = true,
        romFolderPresent = true,
        romFolderConfirmed = true,
        pagesSeen = setOf("accessibility")
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { certStore.certs } returns MutableStateFlow(emptyList())
        every { preferences.userPreferences } returns flowOf(UserPreferences())
        every { coreDownloads.progress } returns MutableStateFlow(CoreDownloadProgress())
        every { coreDownloads.missingCoreIds(any()) } returns emptyList()
        every { registry.snapshot(any()) } returns PermissionSnapshot()
        every { registry.hasRootRoute() } returns false
        every { registry.restrictedSettingsApply() } returns false
        coEvery { romM.getLibrarySummary() } returns RomMResult.Success(3 to 40)
        coEvery { romM.fetchAndStorePlatforms(any()) } returns RomMResult.Success(emptyList())
        coEvery { setupState.answerSaveSync(any()) } returns true
        every { registry.accessibilityScreen() } returns null
        every { controllerTracker.controllers } returns pads
        every { controllerTracker.activeLayout } returns MutableStateFlow(null)
        every { romM.connectionState } returns connection
    }

    private val connection = MutableStateFlow<ConnectionState>(ConnectionState.Connected("4.0.0"))

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val handoff = SetupHandoff()

    private fun viewModel(facts: SetupFacts): SetupViewModel {
        every { setupState.facts } returns flowOf(facts)
        return SetupViewModel(
            application = application,
            preferencesRepository = preferences,
            setupStateRepository = setupState,
            coreDownloads = coreDownloads,
            signIn = RommSignInDelegate(application, romM, certStore),
            access = SetupAccessDelegate(application, registry, setupState).also { it.ioDispatcher = dispatcher },
            folders = SetupFoldersDelegate(probe, preferences, setupState).also { it.ioDispatcher = dispatcher },
            platforms = SetupPlatformsDelegate(romM, platformRepository, coreDownloads).also { it.ioDispatcher = dispatcher },
            personalize = SetupPersonalizeDelegate(preferences, controllerTracker),
            handoff = handoff
        )
    }

    @Test
    fun `a setup composed again after a swap opens on the recorded page and target`() = runTest(dispatcher) {
        val facts = requiredMet
        val first = viewModel(facts)
        advanceUntilIdle()
        assertEquals(SetupPage.SAVE_SYNC, first.currentState().page)
        first.tap(SetupTarget.SaveSyncChoice(false))
        first.back()
        first.tap(SetupTarget.RomFolder(RomFolderKind.CUSTOM))
        first.recordPosition()
        assertEquals(SetupPage.FOLDERS, handoff.position.value?.page)

        val rebuilt = viewModel(facts)
        advanceUntilIdle()

        assertEquals(SetupPage.FOLDERS, rebuilt.currentState().page)
        assertEquals(SetupTarget.RomFolder(RomFolderKind.CUSTOM), rebuilt.currentState().focused)
    }

    @Test
    fun `a setup that has not loaded records nothing`() = runTest(dispatcher) {
        val vm = viewModel(requiredMet)
        vm.recordPosition()
        assertEquals(null, handoff.position.value)
    }

    @Test
    fun `finishing the run clears the recorded position`() = runTest(dispatcher) {
        every { setupState.needsSetup } returns flowOf(false)
        val vm = viewModel(requiredMet.copy(saveSyncAnswered = true, defaultHome = true, pagesSeen = allOptionalSeen))
        advanceUntilIdle()
        vm.recordPosition()
        assertEquals(SetupPage.DONE, handoff.position.value?.page)

        vm.tap(SetupTarget.Action(SetupActionId.PRIMARY))
        advanceUntilIdle()

        assertEquals(null, handoff.position.value)
    }

    @Test
    fun `a dual-screen device already home goes from save sync straight to screens`() = runTest(dispatcher) {
        val vm = viewModel(requiredMet.copy(dualScreenDevice = true, defaultHome = true))
        advanceUntilIdle()
        assertEquals(SetupPage.SAVE_SYNC, vm.currentState().page)
        assertEquals(false, SetupPage.DEFAULT_HOME in vm.currentState().plan)

        vm.tap(SetupTarget.Action(SetupActionId.PRIMARY))
        advanceUntilIdle()

        assertEquals(SetupPage.SCREENS, vm.currentState().page)
    }

    @Test
    fun `back on welcome is silent and stays put, back elsewhere returns a page`() = runTest(dispatcher) {
        val vm = viewModel(SetupFacts())
        advanceUntilIdle()
        assertEquals(SetupPage.WELCOME, vm.currentState().page)

        assertEquals(SoundType.SILENT, vm.back().soundOverride)
        assertEquals(SetupPage.WELCOME, vm.currentState().page)

        vm.confirm()
        assertEquals(SetupPage.LIBRARY, vm.currentState().page)
        assertEquals(SetupDirection.FORWARD, vm.currentState().direction)

        assertEquals(SoundType.BACK, vm.back().soundOverride)
        assertEquals(SetupPage.WELCOME, vm.currentState().page)
        assertEquals(SetupDirection.BACK, vm.currentState().direction)
    }

    @Test
    fun `back inside the library page steps back one view before leaving it`() = runTest(dispatcher) {
        val vm = viewModel(SetupFacts())
        advanceUntilIdle()
        vm.confirm()
        assertEquals(SetupTarget.RommSource, focusedTarget(vm.currentState()))

        vm.move(SetupMove.DOWN)
        vm.confirm()
        assertEquals(LibraryView.ADDRESS, vm.currentState().signIn.view)
        assertEquals(SetupTarget.ServerField, focusedTarget(vm.currentState()))

        vm.back()
        assertEquals(SetupPage.LIBRARY, vm.currentState().page)
        assertEquals(LibraryView.CHOICE, vm.currentState().signIn.view)

        vm.back()
        assertEquals(SetupPage.WELCOME, vm.currentState().page)
    }

    @Test
    fun `the address field opens on A and a blank address asks for one`() = runTest(dispatcher) {
        val vm = viewModel(SetupFacts())
        advanceUntilIdle()
        vm.confirm()
        vm.move(SetupMove.DOWN)
        vm.confirm()

        vm.confirm()
        assertEquals(SetupField.SERVER_ADDRESS, vm.currentState().openField)

        vm.submitServerAddress()
        assertEquals(null, vm.currentState().openField)
        assertEquals(R.string.setup_library_address_error_blank, vm.currentState().signIn.error?.textRes)
        coVerify(exactly = 0) { romM.probeServerVersion(any()) }
    }

    @Test
    fun `next on the signed-in library writes its confirmation and moves on`() = runTest(dispatcher) {
        val vm = viewModel(SetupFacts(librarySignedIn = true))
        advanceUntilIdle()
        assertEquals(SetupPage.LIBRARY, vm.currentState().page)
        assertEquals(LibraryView.SIGNED_IN, vm.currentState().signIn.view)

        vm.confirm()
        advanceUntilIdle()

        coVerify(exactly = 1) { setupState.confirmLibrarySource() }
        assertEquals(SetupPage.PERMISSIONS, vm.currentState().page)
        coVerify(exactly = 1) { romM.fetchAndStorePlatforms(true) }
    }

    @Test
    fun `next on save sync records the answer and never turns sync on by itself`() = runTest(dispatcher) {
        val vm = viewModel(requiredMet)
        advanceUntilIdle()
        assertEquals(SetupPage.SAVE_SYNC, vm.currentState().page)
        assertEquals(SetupTarget.SaveSyncChoice(true), focusedTarget(vm.currentState()))

        vm.tap(SetupTarget.SaveSyncChoice(false))
        vm.tap(SetupTarget.Action(SetupActionId.PRIMARY))
        advanceUntilIdle()

        coVerify(exactly = 1) { setupState.answerSaveSync(false) }
        coVerify(exactly = 0) { preferences.setSaveSyncEnabled(any()) }
        assertEquals(SetupPage.CONTROLLER, vm.currentState().page)
    }

    @Test
    fun `a run killed after save sync resumes at the next optional page`() = runTest(dispatcher) {
        val vm = viewModel(requiredMet.copy(saveSyncAnswered = true))
        advanceUntilIdle()
        assertEquals(SetupPage.CONTROLLER, vm.currentState().page)
    }

    @Test
    fun `leaving an optional page forward records it as seen`() = runTest(dispatcher) {
        val vm = viewModel(requiredMet.copy(saveSyncAnswered = true))
        advanceUntilIdle()

        vm.tap(SetupTarget.Action(SetupActionId.PRIMARY))
        advanceUntilIdle()

        coVerify(exactly = 1) { setupState.markPagesSeen(setOf("controller")) }
        assertEquals(SetupPage.THEME, vm.currentState().page)
    }

    @Test
    fun `skip customization jumps to done and marks all three pages seen`() = runTest(dispatcher) {
        val vm = viewModel(requiredMet.copy(saveSyncAnswered = true, pagesSeen = setOf("accessibility", "controller")))
        advanceUntilIdle()
        assertEquals(SetupPage.THEME, vm.currentState().page)

        vm.tap(SetupTarget.Action(SetupActionId.SKIP_CUSTOMIZATION))
        advanceUntilIdle()

        coVerify(exactly = 1) { setupState.markPagesSeen(CUSTOMIZATION_SEEN_TOKENS) }
        assertEquals(SetupPage.DONE, vm.currentState().page)
    }

    @Test
    fun `done writes the finished marker before it leaves setup`() = runTest(dispatcher) {
        every { setupState.needsSetup } returns flowOf(false)
        val vm = viewModel(requiredMet.copy(saveSyncAnswered = true, defaultHome = true, pagesSeen = allOptionalSeen))
        advanceUntilIdle()
        assertEquals(SetupPage.DONE, vm.currentState().page)

        vm.tap(SetupTarget.Action(SetupActionId.PRIMARY))
        advanceUntilIdle()

        coVerify(exactly = 1) { setupState.finishFlow() }
    }

    @Test
    fun `a connected gamepad adds the button check after the controller page`() = runTest(dispatcher) {
        pads.value = listOf(mockk(relaxed = true))
        val vm = viewModel(requiredMet.copy(saveSyncAnswered = true))
        advanceUntilIdle()
        assertEquals(SetupPage.CONTROLLER, vm.currentState().page)
        assertEquals(true, vm.currentState().gamepadConnected)

        vm.tap(SetupTarget.Action(SetupActionId.PRIMARY))
        advanceUntilIdle()

        assertEquals(SetupPage.BUTTON_CHECK, vm.currentState().page)
    }

    @Test
    fun `declining home on a dual-screen device records it and skips screens`() = runTest(dispatcher) {
        val dual = requiredMet.copy(saveSyncAnswered = true, dualScreenDevice = true)
        val vm = viewModel(dual)
        advanceUntilIdle()
        assertEquals(SetupPage.DEFAULT_HOME, vm.currentState().page)

        vm.tap(SetupTarget.Action(SetupActionId.DECLINE_HOME))
        advanceUntilIdle()

        coVerify(exactly = 1) { setupState.declineDefaultHome() }
        coVerify(exactly = 0) { preferences.setDualScreenEnabled(any()) }
        assertEquals(SetupPage.CONTROLLER, vm.currentState().page)
        assertEquals(false, SetupPage.SCREENS in vm.currentState().plan)
    }

    private val allOptionalSeen: Set<String> = SetupPage.entries.mapNotNull { it.seenToken }.toSet()

    @Test
    fun `turning sync on stays on the page while the gate refuses`() = runTest(dispatcher) {
        coEvery { setupState.answerSaveSync(true) } returns false
        val vm = viewModel(requiredMet)
        advanceUntilIdle()

        vm.tap(SetupTarget.Action(SetupActionId.PRIMARY))
        advanceUntilIdle()

        coVerify(exactly = 1) { setupState.answerSaveSync(true) }
        assertEquals(SetupPage.SAVE_SYNC, vm.currentState().page)
    }

    @Test
    fun `back from save sync returns to the folder page`() = runTest(dispatcher) {
        val vm = viewModel(requiredMet)
        advanceUntilIdle()
        vm.back()
        assertEquals(SetupPage.FOLDERS, vm.currentState().page)
    }

    @Test
    fun `start moves focus to the primary and a second start activates it`() = runTest(dispatcher) {
        val vm = viewModel(requiredMet)
        advanceUntilIdle()
        assertEquals(SetupTarget.SaveSyncChoice(true), focusedTarget(vm.currentState()))

        vm.startPress()
        assertEquals(SetupTarget.Action(SetupActionId.PRIMARY), focusedTarget(vm.currentState()))
        assertEquals(SetupPage.SAVE_SYNC, vm.currentState().page)
        coVerify(exactly = 0) { setupState.answerSaveSync(any()) }

        vm.startPress()
        advanceUntilIdle()
        coVerify(exactly = 1) { setupState.answerSaveSync(true) }
        assertEquals(SetupPage.CONTROLLER, vm.currentState().page)
    }

    @Test
    fun `start on the button check moves to continue, then continues`() = runTest(dispatcher) {
        pads.value = listOf(mockk(relaxed = true))
        val vm = viewModel(requiredMet.copy(saveSyncAnswered = true))
        advanceUntilIdle()
        vm.tap(SetupTarget.Action(SetupActionId.PRIMARY))
        advanceUntilIdle()
        assertEquals(SetupPage.BUTTON_CHECK, vm.currentState().page)

        vm.startPress()
        assertEquals(ButtonCheckFocus.CONTINUE, vm.buttonCheck.state.value.focus)
        assertEquals(SetupPage.BUTTON_CHECK, vm.currentState().page)

        vm.startPress()
        advanceUntilIdle()
        assertEquals(SetupPage.THEME, vm.currentState().page)
    }

    @Test
    fun `back while testing buttons ends the test and stays on the page`() = runTest(dispatcher) {
        pads.value = listOf(mockk(relaxed = true))
        val vm = viewModel(requiredMet.copy(saveSyncAnswered = true))
        advanceUntilIdle()
        vm.tap(SetupTarget.Action(SetupActionId.PRIMARY))
        advanceUntilIdle()
        assertEquals(SetupPage.BUTTON_CHECK, vm.currentState().page)
        assertEquals(ButtonCheckFocus.TEST, vm.buttonCheck.state.value.focus)

        vm.confirm()
        assertTrue(vm.buttonCheck.state.value.testing)

        vm.back()
        assertFalse(vm.buttonCheck.state.value.testing)
        assertEquals(SetupPage.BUTTON_CHECK, vm.currentState().page)

        vm.back()
        assertEquals(SetupPage.CONTROLLER, vm.currentState().page)
    }

    @Test
    fun `the screens page re-reads the roles after a swap made while it is open`() = runTest(dispatcher) {
        val swapped = MutableStateFlow(false)
        val manager = mockk<DualScreenManager>(relaxed = true)
        every { manager.isRolesSwapped } returns swapped
        coEvery { manager.resolveScreenLayout() } returnsMany listOf(twoScreens(primary = "top"), twoScreens(primary = "bottom"))
        DualScreenManagerHolder.instance = manager
        try {
            val vm = viewModel(requiredMet.copy(dualScreenDevice = true, defaultHome = true))
            advanceUntilIdle()
            vm.tap(SetupTarget.Action(SetupActionId.PRIMARY))
            advanceUntilIdle()
            assertEquals(SetupPage.SCREENS, vm.currentState().page)
            assertEquals(ScreenRole.PRIMARY, vm.currentState().screens.first { it.key == "top" }.role)
            assertEquals(SetupTarget.SwapScreens, focusedTarget(vm.currentState()))

            swapped.value = true
            advanceUntilIdle()

            assertEquals(ScreenRole.PRIMARY, vm.currentState().screens.first { it.key == "bottom" }.role)
            assertEquals(ScreenRole.PRESENTATION, vm.currentState().screens.first { it.key == "top" }.role)
        } finally {
            DualScreenManagerHolder.instance = null
        }
    }

    private fun twoScreens(primary: String): DualScreenManager.ResolvedScreenLayout {
        val attached = listOf(
            AttachedScreen(key = "top", displayId = 0, number = 1, widthPx = 1920, heightPx = 1080, builtIn = true),
            AttachedScreen(key = "bottom", displayId = 1, number = 2, widthPx = 1080, heightPx = 1240, builtIn = true)
        )
        val roles = attached.associate { it.key to if (it.key == primary) ScreenRole.PRIMARY else ScreenRole.PRESENTATION }
        return DualScreenManager.ResolvedScreenLayout(
            attached = attached,
            setKey = "bottom|top",
            stored = ScreenLayouts(),
            known = null,
            layout = ScreenLayout(roles)
        )
    }

    @Test
    fun `an already signed-in library shows the server counts once they load`() = runTest(dispatcher) {
        val vm = viewModel(SetupFacts(librarySignedIn = true))
        advanceUntilIdle()

        val signIn = vm.currentState().signIn
        assertEquals(LibraryView.SIGNED_IN, signIn.view)
        assertEquals(true, signIn.summaryLoaded)
        assertEquals(40, signIn.gameCount)
        assertEquals(3, signIn.platformCount)
    }

    @Test
    fun `a summary asked for before RomM connects loads once the connection lands`() = runTest(dispatcher) {
        connection.value = ConnectionState.Connecting
        coEvery { romM.getLibrarySummary() } returnsMany listOf(
            RomMResult.Error("Not connected"),
            RomMResult.Success(31 to 11_842)
        )
        val vm = viewModel(SetupFacts(librarySignedIn = true))
        advanceTimeBy(1_000)
        assertEquals(false, vm.currentState().signIn.summaryLoaded)
        assertEquals(null, vm.currentState().signIn.error)
        coVerify(atLeast = 1) { romM.initialize(any()) }

        connection.value = ConnectionState.Connected("4.0.0")
        runCurrent()

        val signIn = vm.currentState().signIn
        assertEquals(true, signIn.summaryLoaded)
        assertEquals(11_842, signIn.gameCount)
        assertEquals(31, signIn.platformCount)
    }

    @Test
    fun `focus stops at the edges instead of wrapping`() = runTest(dispatcher) {
        val vm = viewModel(requiredMet)
        advanceUntilIdle()
        val boundary = vm.move(SetupMove.UP)
        assertEquals(SoundType.BOUNDARY, boundary.soundOverride)
        assertEquals(SetupTarget.SaveSyncChoice(true), focusedTarget(vm.currentState()))
    }
}
