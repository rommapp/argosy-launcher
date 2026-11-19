package com.nendo.argosy.data.sync.platform

import android.content.Context
import android.os.Environment
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.savepath.Pcsx2DataFolder
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.repository.EmulatorSaveConfigRepository
import com.nendo.argosy.data.sync.fixtures.realFsFal
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class SigilPcsx2SlotOptionTest {

    private lateinit var tempDir: File
    private lateinit var dataRoot: File

    private val context = mockk<Context>(relaxed = true)
    private val gameDao = mockk<GameDao>(relaxed = true)
    private val emulatorResolver = mockk<EmulatorResolver>(relaxed = true)
    private val emulatorSaveConfigRepository = mockk<EmulatorSaveConfigRepository>(relaxed = true)
    private val emulatorConfigDao = mockk<EmulatorConfigDao>(relaxed = true)

    private lateinit var handler: SigilSaveHandler

    @Before
    fun setUp() {
        tempDir = createTempDirectory("sigil_pcsx2_slot").toFile()
        dataRoot = File(tempDir, "Android/data/com.armsx2/files").apply {
            File(this, "memcards/maincard").mkdirs()
            File(this, "gamesettings").mkdirs()
            File(this, Pcsx2DataFolder.INI_NAME).writeText("[MemoryCards]\nSlot1_Enable = true\nSlot1_Filename = maincard\n")
        }

        mockkStatic(Environment::class)
        every { Environment.getExternalStorageDirectory() } returns File(tempDir, "emulated/0").apply { mkdirs() }
        every { context.filesDir } returns File(tempDir, "files")
        coEvery { gameDao.getById(GAME_ID) } returns GAME
        coEvery { emulatorResolver.getEmulatorPackageForGame(any(), any(), any()) } returns null
        coEvery { emulatorSaveConfigRepository.resolveUserSavePath(any(), any()) } returns "${dataRoot.absolutePath}/memcards"
        coEvery { emulatorConfigDao.getSelectedMemcardForGame(any()) } returns null
        coEvery { emulatorSaveConfigRepository.getByEmulator(any()) } returns null

        handler = SigilSaveHandler(
            context = context,
            gameDao = gameDao,
            emulatorResolver = emulatorResolver,
            emulatorSaveConfigRepository = emulatorSaveConfigRepository,
            switchProfileParser = mockk(relaxed = true),
            titleDbRepository = mockk(relaxed = true),
            emulatorConfigDao = emulatorConfigDao,
            fal = realFsFal(),
            connectionManager = { mockk(relaxed = true) },
            saveUnitResolver = mockk(relaxed = true),
            builtinSaveBase = mockk(relaxed = true),
            retroArchPathResolver = mockk(relaxed = true),
            sigilSyncStateDao = mockk(relaxed = true),
            syncPreferencesRepository = mockk(relaxed = true),
            cartFeatureScanner = mockk(relaxed = true),
            saveLocator = mockk(relaxed = true)
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(Environment::class)
        tempDir.deleteRecursively()
    }

    private suspend fun slot1(): String? = handler.localRoute(GAME_ID, ARMSX2)?.options?.get("Slot1_Filename")

    @Test
    fun `slot 1 is the card the emulator's ini names when Argosy has no card picked`() = runTest {
        assertEquals("maincard", slot1())
    }

    @Test
    fun `a game's own card in the emulator's per-game ini outranks the global slot 1`() = runTest {
        File(dataRoot, "gamesettings/SLUS-20675_ABCD1234.ini").writeText("[MemoryCards]\nSlot1_Filename = DarkCloud\n")

        assertEquals("DarkCloud", slot1())
    }

    @Test
    fun `the card picked in Argosy outranks the emulator's ini`() = runTest {
        File(dataRoot, "gamesettings/SLUS-20675_ABCD1234.ini").writeText("[MemoryCards]\nSlot1_Filename = DarkCloud\n")
        coEvery { emulatorConfigDao.getSelectedMemcardForGame(GAME_ID) } returns "${dataRoot.absolutePath}/memcards/Picked"

        assertEquals("Picked", slot1())
    }

    private companion object {
        const val GAME_ID = 7L
        const val ARMSX2 = "armsx2"
        val GAME = GameEntity(
            id = GAME_ID,
            title = "Dark Cloud",
            sortTitle = "dark cloud",
            platformId = 2L,
            platformSlug = "ps2",
            rommId = 9L,
            igdbId = null,
            localPath = "/roms/Dark Cloud (USA).chd",
            source = GameSource.ROMM_SYNCED,
            titleId = "SLUS-20675"
        )
    }
}
