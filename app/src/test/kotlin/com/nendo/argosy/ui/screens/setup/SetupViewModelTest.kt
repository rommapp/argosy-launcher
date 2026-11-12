package com.nendo.argosy.ui.screens.setup

import android.app.Application
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.permissions.PermissionRegistry
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.remote.ssl.UserCertStore
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.setup.SetupCoreDownloads
import com.nendo.argosy.data.setup.SetupFolderProbe
import com.nendo.argosy.data.setup.SetupStateRepository
import com.nendo.argosy.domain.model.CoreDownloadProgress
import com.nendo.argosy.domain.model.PermissionSnapshot
import com.nendo.argosy.domain.model.SetupFacts
import com.nendo.argosy.ui.screens.setup.components.SetupDirection
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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

    private val requiredMet = SetupFacts(
        librarySignedIn = true,
        librarySourceConfirmed = true,
        allFilesAccess = true,
        usageAccess = true,
        notifications = true,
        romFolderPresent = true,
        romFolderConfirmed = true
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
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

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
            platforms = SetupPlatformsDelegate(romM, platformRepository, coreDownloads).also { it.ioDispatcher = dispatcher }
        )
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
        assertEquals(SetupPage.DONE, vm.currentState().page)
    }

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
    fun `focus stops at the edges instead of wrapping`() = runTest(dispatcher) {
        val vm = viewModel(requiredMet)
        advanceUntilIdle()
        val boundary = vm.move(SetupMove.UP)
        assertEquals(SoundType.BOUNDARY, boundary.soundOverride)
        assertEquals(SetupTarget.SaveSyncChoice(true), focusedTarget(vm.currentState()))
    }
}
