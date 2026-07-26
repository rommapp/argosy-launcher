package com.nendo.argosy.data.repository

import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.ResolvedCoreSelection
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SaveSyncResolutionTest {

    private lateinit var emulatorResolver: EmulatorResolver
    private lateinit var client: SaveSyncApiClient

    @Before
    fun setup() {
        emulatorResolver = mockk()
        client = SaveSyncApiClient(
            saveSyncDao = mockk(relaxed = true),
            emulatorResolver = emulatorResolver,
            gameDao = mockk(relaxed = true),
            savePathResolver = mockk(relaxed = true),
            userPreferencesRepository = mockk(relaxed = true),
            fal = mockk(relaxed = true),
            switchSaveHandler = mockk(relaxed = true),
            gciSaveHandler = mockk(relaxed = true),
            saveHandlerRegistry = mockk(relaxed = true),
            conflictDetector = mockk(relaxed = true),
            saveUploader = dagger.Lazy { mockk(relaxed = true) },
            saveDownloader = dagger.Lazy { mockk(relaxed = true) }
        )
    }

    @Test
    fun `save sync uses effective emulator and its normalized core`() = runTest {
        val game = GameEntity(
            id = 1L,
            platformId = 10L,
            platformSlug = "saturn",
            title = "Test Game",
            sortTitle = "test game",
            localPath = null,
            rommId = 100L,
            igdbId = null,
            source = GameSource.ROMM_SYNCED
        )
        val builtIn = EmulatorRegistry.getByPackage(EmulatorRegistry.BUILTIN_PACKAGE)!!
        val availableCores = EmulatorRegistry.getSelectableCores("saturn", isBuiltIn = true)
        val selectedCore = availableCores.single { it.id == "mednafen_saturn" }
        coEvery {
            emulatorResolver.getEmulatorForGame(game.id, game.platformId, game.platformSlug)
        } returns builtIn
        coEvery {
            emulatorResolver.resolveCoreSelectionForGame(
                game.id,
                game.platformId,
                game.platformSlug,
                builtIn
            )
        } returns ResolvedCoreSelection(availableCores, selectedCore)

        assertEquals("builtin", client.resolveEmulatorForGame(game))
        assertEquals("mednafen_saturn", client.resolveCoreForGame(game))
        coVerify(exactly = 2) {
            emulatorResolver.getEmulatorForGame(game.id, game.platformId, game.platformSlug)
        }
    }
}
