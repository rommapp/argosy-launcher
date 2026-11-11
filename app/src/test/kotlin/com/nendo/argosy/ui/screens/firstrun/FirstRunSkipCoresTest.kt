package com.nendo.argosy.ui.screens.firstrun

import android.app.Application
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.data.remote.ssl.UserCertStore
import com.nendo.argosy.data.setup.SetupStateRepository
import com.nendo.argosy.domain.model.SetupFacts
import com.nendo.argosy.libretro.LibretroCoreManager
import com.nendo.argosy.libretro.LibretroCoreRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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

class FirstRunSkipCoresTest {

    private val dispatcher = StandardTestDispatcher()
    private val preferences = mockk<UserPreferencesRepository>(relaxed = true)
    private val coreManager = mockk<LibretroCoreManager>(relaxed = true)
    private val certStore = mockk<UserCertStore>(relaxed = true)
    private val romM = mockk<RomMRepository>(relaxed = true)
    private val setupState = mockk<SetupStateRepository>(relaxed = true)
    private val snes = PlatformEntity(
        id = 1L,
        slug = "snes",
        name = "SNES",
        shortName = "SNES",
        romExtensions = "sfc",
        syncEnabled = true
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { certStore.certs } returns MutableStateFlow(emptyList())
        every { preferences.userPreferences } returns flowOf(UserPreferences())
        every { setupState.facts } returns flowOf(SetupFacts())
        coEvery { romM.getLibrarySummary() } returns RomMResult.Success(3 to 40)
        coEvery { romM.fetchAndStorePlatforms(any()) } returns RomMResult.Success(listOf(snes))
        every { coreManager.getMissingCoresForPlatforms(emptySet()) } returns emptyList()
        every { coreManager.getMissingCoresForPlatforms(setOf("snes")) } returns
            listOf(mockk<LibretroCoreRegistry.CoreInfo>(relaxed = true))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = FirstRunViewModel(
        application = mockk<Application>(relaxed = true),
        preferencesRepository = preferences,
        romMRepository = romM,
        platformRepository = mockk(relaxed = true),
        setupStateRepository = setupState,
        coreManager = coreManager,
        gamepadInputHandler = mockk(relaxed = true),
        hapticManager = mockk(relaxed = true),
        userCertStore = certStore
    )

    private fun FirstRunViewModel.advanceTo(step: FirstRunStep) {
        repeat(FirstRunStep.entries.size) {
            if (uiState.value.currentStep != step) nextStep()
        }
        assertEquals(step, uiState.value.currentStep)
    }

    @Test
    fun `skipping the core download leaves the built-in emulator enabled`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.advanceTo(FirstRunStep.CORE_PROMPT)
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.missingCoreCount)

        viewModel.skipCorePrompt()
        advanceUntilIdle()

        assertEquals(FirstRunStep.COMPLETE, viewModel.uiState.value.currentStep)
        coVerify(exactly = 0) { preferences.setBuiltinLibretroEnabled(any()) }
    }

    @Test
    fun `with ten or fewer platforms the core prompt still sees their cores`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.advanceTo(FirstRunStep.SAVE_SYNC)
        viewModel.nextStep()
        advanceUntilIdle()

        assertEquals(FirstRunStep.CORE_PROMPT, viewModel.uiState.value.currentStep)
        assertEquals(1, viewModel.uiState.value.missingCoreCount)
        coVerify(exactly = 1) { romM.fetchAndStorePlatforms(true) }
        verify { coreManager.getMissingCoresForPlatforms(setOf("snes")) }
    }

    @Test
    fun `back on the platform list goes back instead of forward`() = runTest(dispatcher) {
        coEvery { romM.getLibrarySummary() } returns RomMResult.Success(12 to 40)
        every { setupState.facts } returns flowOf(SetupFacts(librarySignedIn = true))
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.advanceTo(FirstRunStep.PLATFORM_SELECT)
        advanceUntilIdle()

        FirstRunInputHandler(viewModel, {}, {}, {}, {}, {}, {}, {}, {}, {}).onBack()

        assertEquals(FirstRunStep.SAVE_SYNC, viewModel.uiState.value.currentStep)
    }

    @Test
    fun `answering save sync writes the answer and never forces sync on at the end`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.advanceTo(FirstRunStep.SAVE_SYNC)
        advanceUntilIdle()

        viewModel.selectSaveSync(false)
        viewModel.proceedFromSaveSync()
        viewModel.completeSetup()
        advanceUntilIdle()

        coVerify(exactly = 1) { setupState.answerSaveSync(false) }
        coVerify(exactly = 0) { preferences.setSaveSyncEnabled(any()) }
    }
}
