package com.nendo.argosy.domain.usecase.game

import com.nendo.argosy.data.emulator.EmulatorDef
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.EmulatorConfigEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConfigureEmulatorUseCaseTest {

    private lateinit var emulatorConfigDao: EmulatorConfigDao
    private lateinit var saveSyncDao: SaveSyncDao
    private lateinit var useCase: ConfigureEmulatorUseCase

    @Before
    fun setup() {
        emulatorConfigDao = mockk(relaxed = true)
        saveSyncDao = mockk(relaxed = true)
        useCase = ConfigureEmulatorUseCase(emulatorConfigDao, saveSyncDao)
    }

    @Test
    fun `setForGame deletes existing override`() = runTest {
        useCase.setForGame(123L, 1L, "nes", null)

        coVerify { emulatorConfigDao.deleteGameOverride(123L) }
    }

    @Test
    fun `setForGame saves game-specific config when emulator provided`() = runTest {
        val emulator = createEmulator("com.retroarch", "RetroArch")
        val configSlot = slot<EmulatorConfigEntity>()
        coEvery { emulatorConfigDao.insert(capture(configSlot)) } returns 1L

        useCase.setForGame(123L, 1L, "nes", emulator)

        assertTrue(configSlot.isCaptured)
        val config = configSlot.captured
        assertEquals(1L, config.platformId)
        assertEquals(123L, config.gameId)
        assertEquals("com.retroarch", config.packageName)
        assertEquals("RetroArch", config.displayName)
        assertFalse(config.isDefault)
    }

    @Test
    fun `setForGame with null emulator only deletes override`() = runTest {
        useCase.setForGame(123L, 1L, "nes", null)

        coVerify { emulatorConfigDao.deleteGameOverride(123L) }
        coVerify(exactly = 0) { emulatorConfigDao.insert(any()) }
    }

    @Test
    fun `setForPlatform clears platform defaults`() = runTest {
        useCase.setForPlatform(1L, "nes", null)

        coVerify { emulatorConfigDao.clearPlatformDefaults(1L) }
    }

    @Test
    fun `setForPlatform saves platform default when emulator provided`() = runTest {
        val emulator = createEmulator("com.snes9x", "Snes9x")
        val configSlot = slot<EmulatorConfigEntity>()
        coEvery { emulatorConfigDao.insert(capture(configSlot)) } returns 1L

        useCase.setForPlatform(2L, "snes", emulator)

        assertTrue(configSlot.isCaptured)
        val config = configSlot.captured
        assertEquals(2L, config.platformId)
        assertNull(config.gameId)
        assertEquals("com.snes9x", config.packageName)
        assertEquals("Snes9x", config.displayName)
        assertTrue(config.isDefault)
    }

    @Test
    fun `setForPlatform with null emulator only clears defaults`() = runTest {
        useCase.setForPlatform(1L, "nes", null)

        coVerify { emulatorConfigDao.clearPlatformDefaults(1L) }
        coVerify(exactly = 0) { emulatorConfigDao.insert(any()) }
    }

    @Test
    fun `clearForGame deletes game override`() = runTest {
        useCase.clearForGame(456L)

        coVerify { emulatorConfigDao.deleteGameOverride(456L) }
    }

    @Test
    fun `clearForPlatform clears platform defaults`() = runTest {
        useCase.clearForPlatform(3L)

        coVerify { emulatorConfigDao.clearPlatformDefaults(3L) }
    }

    @Test
    fun `setForGame recreates the row without a savePath`() = runTest {
        val emulator = createEmulator("com.retroarch", "RetroArch")
        val configSlot = slot<EmulatorConfigEntity>()
        coEvery { emulatorConfigDao.insert(capture(configSlot)) } returns 1L

        useCase.setForGame(123L, 1L, "nes", emulator)

        coVerify { emulatorConfigDao.deleteGameOverride(123L) }
        assertNull(configSlot.captured.savePath)
    }

    @Test
    fun `setSavePathForGame patches an existing per-game row and clears cached sync paths`() = runTest {
        coEvery { emulatorConfigDao.getByGameId(123L) } returns
            EmulatorConfigEntity(id = 5L, platformId = 1L, gameId = 123L, packageName = "com.retroarch", displayName = "RetroArch", coreName = null)

        useCase.setSavePathForGame(123L, "/storage/saves")

        coVerify { emulatorConfigDao.updateSavePathForGame(123L, "/storage/saves") }
        coVerify(exactly = 0) { emulatorConfigDao.insert(any()) }
        coVerify { saveSyncDao.clearLocalPathsForGame(123L) }
    }

    @Test
    fun `setSavePathForGame creates a null-package row when no per-game row exists`() = runTest {
        coEvery { emulatorConfigDao.getByGameId(123L) } returns null
        val configSlot = slot<EmulatorConfigEntity>()
        coEvery { emulatorConfigDao.insert(capture(configSlot)) } returns 1L

        useCase.setSavePathForGame(123L, "/storage/saves")

        val config = configSlot.captured
        assertEquals(123L, config.gameId)
        assertNull(config.platformId)
        assertNull(config.packageName)
        assertNull(config.displayName)
        assertFalse(config.isDefault)
        assertEquals("/storage/saves", config.savePath)
        coVerify { saveSyncDao.clearLocalPathsForGame(123L) }
    }

    @Test
    fun `clearSavePathForGame nulls the path and clears cached sync paths`() = runTest {
        coEvery { emulatorConfigDao.getByGameId(123L) } returns
            EmulatorConfigEntity(id = 5L, platformId = null, gameId = 123L, packageName = null, displayName = null, coreName = null, savePath = "/storage/saves")

        useCase.clearSavePathForGame(123L)

        coVerify { emulatorConfigDao.updateSavePathForGame(123L, null) }
        coVerify { saveSyncDao.clearLocalPathsForGame(123L) }
    }

    @Test
    fun `clearSavePathForGame is a no-op when nothing is set`() = runTest {
        coEvery { emulatorConfigDao.getByGameId(123L) } returns null

        useCase.clearSavePathForGame(123L)

        coVerify(exactly = 0) { emulatorConfigDao.updateSavePathForGame(any(), any()) }
        coVerify(exactly = 0) { saveSyncDao.clearLocalPathsForGame(any()) }
    }

    private fun createEmulator(packageName: String, displayName: String): InstalledEmulator {
        val def = EmulatorDef(
            id = packageName,
            packageName = packageName,
            displayName = displayName,
            supportedPlatforms = setOf("nes", "snes", "gba")
        )
        return InstalledEmulator(def = def, versionName = "1.0", versionCode = 1L)
    }
}
