package com.nendo.argosy.data.repository

import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SaveSyncApiClientTest {

    private lateinit var emulatorResolver: EmulatorResolver
    private lateinit var client: SaveSyncApiClient

    private val game = GameEntity(
        id = 1L,
        platformId = 10L,
        platformSlug = "psx",
        title = "Game",
        sortTitle = "game",
        localPath = null,
        rommId = null,
        igdbId = null,
        source = GameSource.LOCAL_ONLY
    )

    @Before
    fun setup() {
        emulatorResolver = mockk(relaxed = true)
        client = SaveSyncApiClient(
            saveSyncDao = mockk(relaxed = true),
            emulatorConfigDao = mockk(relaxed = true),
            emulatorResolver = emulatorResolver,
            builtinCoreResolver = mockk(relaxed = true),
            gameDao = mockk(relaxed = true),
            savePathResolver = mockk(relaxed = true),
            userPreferencesRepository = mockk(relaxed = true),
            fal = mockk(relaxed = true),
            switchSaveHandler = mockk(relaxed = true),
            gciSaveHandler = mockk(relaxed = true),
            saveHandlerRegistry = mockk(relaxed = true),
            conflictDetector = mockk(relaxed = true),
            saveUploader = mockk(relaxed = true),
            saveDownloader = mockk(relaxed = true),
            syncPreferencesRepository = mockk(relaxed = true),
            snapshotRouter = mockk(relaxed = true)
        )
        coEvery { emulatorResolver.getPreferredEmulatorId("psx") } returns "retroarch"
    }

    @Test
    fun `a launch package that names a known emulator resolves to that emulator`() = runTest {
        coEvery { emulatorResolver.getEmulatorPackageForGame(1L, 10L, "psx") } returns "com.github.stenzek.duckstation"
        every { emulatorResolver.resolveEmulatorId("com.github.stenzek.duckstation") } returns "duckstation"

        assertEquals("duckstation", client.resolveEmulatorForGame(game))
    }

    @Test
    fun `an ad-hoc launch package resolves to no emulator and never borrows the preferred one`() = runTest {
        coEvery { emulatorResolver.getEmulatorPackageForGame(1L, 10L, "psx") } returns "com.custom.emulator"
        every { emulatorResolver.resolveEmulatorId("com.custom.emulator") } returns null

        assertNull(client.resolveEmulatorForGame(game))
        coVerify(exactly = 0) { emulatorResolver.getPreferredEmulatorId(any()) }
    }

    @Test
    fun `a game with no launch package falls back to the platform's preferred emulator`() = runTest {
        coEvery { emulatorResolver.getEmulatorPackageForGame(1L, 10L, "psx") } returns null

        assertEquals("retroarch", client.resolveEmulatorForGame(game))
    }
}
