package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.entity.GameDiscEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StateSerialResolverTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val gameDao = mockk<GameDao>(relaxed = true)
    private val gameDiscDao = mockk<GameDiscDao>()
    private val extractor = mockk<TitleIdExtractor>()
    private val resolver = StateSerialResolver(gameDao, gameDiscDao, extractor)
    private lateinit var rom: File

    @Before
    fun setUp() {
        rom = temp.newFile("Game (USA).chd").apply { writeBytes(byteArrayOf(1)) }
        coEvery { gameDiscDao.getDiscsForGame(any()) } returns emptyList()
    }

    private fun game(titleId: String? = null, locked: Boolean = false, saveId: String? = null) = GameEntity(
        id = GAME_ID,
        title = "Game",
        sortTitle = "game",
        platformId = 1L,
        platformSlug = "psx",
        rommId = 10L,
        igdbId = null,
        localPath = rom.absolutePath,
        source = GameSource.ROMM_SYNCED,
        titleId = titleId,
        titleIdLocked = locked,
        saveId = saveId
    )

    private fun sigil(file: File, titleId: String, fromBinary: Boolean) {
        every { extractor.extractTitleIdWithSource(file, "psx", any()) } returns
            TitleIdResult(titleId = titleId, fromBinary = fromBinary)
    }

    @Test
    fun `a locked stored title id is never trusted in place of a disc read`() = runTest {
        coEvery { gameDao.getById(GAME_ID) } returns game(titleId = "ULJS00445", locked = true)
        sigil(rom, "SLUS-00001", fromBinary = true)

        assertEquals(setOf("SLUS-00001"), resolver.serialsFor(GAME_ID, "duckstation"))
        coVerify { gameDao.setTitleAndSaveIdWithLock(GAME_ID, "SLUS-00001", any(), true) }
    }

    @Test
    fun `a locked stored title id with no readable disc gives no serial`() = runTest {
        coEvery { gameDao.getById(GAME_ID) } returns game(titleId = "SLUS-00001", locked = true)
        sigil(rom, "SLUS-00001", fromBinary = false)

        assertEquals(emptySet<String>(), resolver.serialsFor(GAME_ID, "duckstation"))
    }

    @Test
    fun `a disc read that matches the locked title id stores nothing`() = runTest {
        coEvery { gameDao.getById(GAME_ID) } returns game(titleId = "SLUS-00001", locked = true)
        sigil(rom, "SLUS-00001", fromBinary = true)

        assertEquals(setOf("SLUS-00001"), resolver.serialsFor(GAME_ID, "duckstation"))
        coVerify(exactly = 0) { gameDao.setTitleAndSaveIdWithLock(any(), any(), any(), any()) }
    }

    @Test
    fun `an unlocked stored title id is ignored for a binary read, which is stored with the lock`() = runTest {
        coEvery { gameDao.getById(GAME_ID) } returns game(titleId = "SCUS-99999", locked = false)
        sigil(rom, "SLUS-00001", fromBinary = true)

        assertEquals(setOf("SLUS-00001"), resolver.serialsFor(GAME_ID, "duckstation"))
        coVerify { gameDao.setTitleAndSaveIdWithLock(GAME_ID, "SLUS-00001", "SLUS-00001", true) }
    }

    @Test
    fun `storing the binary serial keeps a save id the game already has`() = runTest {
        coEvery { gameDao.getById(GAME_ID) } returns game(saveId = "BASLUS-00001")
        sigil(rom, "SLUS-00001", fromBinary = true)

        resolver.serialsFor(GAME_ID, "duckstation")

        coVerify { gameDao.setTitleAndSaveIdWithLock(GAME_ID, "SLUS-00001", "BASLUS-00001", true) }
    }

    @Test
    fun `a filename fallback is refused and nothing is stored`() = runTest {
        coEvery { gameDao.getById(GAME_ID) } returns game(titleId = "SCUS-99999")
        sigil(rom, "SLUS-00001", fromBinary = false)

        assertEquals(emptySet<String>(), resolver.serialsFor(GAME_ID, "duckstation"))
        coVerify(exactly = 0) { gameDao.setTitleAndSaveIdWithLock(any(), any(), any(), any()) }
    }

    @Test
    fun `an unreadable or missing rom yields no serial`() = runTest {
        coEvery { gameDao.getById(GAME_ID) } returns game()
        every { extractor.extractTitleIdWithSource(rom, "psx", any()) } returns null

        assertEquals(emptySet<String>(), resolver.serialsFor(GAME_ID, "duckstation"))

        rom.delete()
        assertEquals(emptySet<String>(), resolver.serialsFor(GAME_ID, "duckstation"))
    }

    @Test
    fun `a multi-disc game reads every downloaded disc and keeps only binary reads`() = runTest {
        val disc1 = temp.newFile("Disc 1.chd").apply { writeBytes(byteArrayOf(1)) }
        val disc2 = temp.newFile("Disc 2.chd").apply { writeBytes(byteArrayOf(2)) }
        val disc3 = temp.newFile("Disc 3.chd").apply { writeBytes(byteArrayOf(3)) }
        coEvery { gameDao.getById(GAME_ID) } returns game()
        coEvery { gameDiscDao.getDiscsForGame(GAME_ID) } returns listOf(
            disc(1, disc1), disc(2, disc2), disc(3, disc3), disc(4, null)
        )
        sigil(disc1, "SLUS-00892", fromBinary = true)
        sigil(disc2, "SLUS-00908", fromBinary = true)
        sigil(disc3, "SLUS-00909", fromBinary = false)

        assertEquals(setOf("SLUS-00892", "SLUS-00908"), resolver.serialsFor(GAME_ID, "duckstation"))
    }

    @Test
    fun `an unchanged disc is read by Sigil once across calls`() = runTest {
        coEvery { gameDao.getById(GAME_ID) } returns game(titleId = "SLUS-00001", locked = true)
        sigil(rom, "SLUS-00001", fromBinary = true)

        repeat(3) { assertEquals(setOf("SLUS-00001"), resolver.serialsFor(GAME_ID, "duckstation")) }

        verify(exactly = 1) { extractor.extractTitleIdWithSource(rom, "psx", any()) }
    }

    @Test
    fun `a replaced disc file is read again`() = runTest {
        coEvery { gameDao.getById(GAME_ID) } returns game()
        sigil(rom, "SLUS-00001", fromBinary = true)
        resolver.serialsFor(GAME_ID, "duckstation")

        rom.writeBytes(byteArrayOf(1, 2, 3))
        sigil(rom, "SLUS-00002", fromBinary = true)

        assertEquals(setOf("SLUS-00002"), resolver.serialsFor(GAME_ID, "duckstation"))
        verify(exactly = 2) { extractor.extractTitleIdWithSource(rom, "psx", any()) }
    }

    @Test
    fun `a read that found no serial is tried again on the next call`() = runTest {
        coEvery { gameDao.getById(GAME_ID) } returns game()
        every { extractor.extractTitleIdWithSource(rom, "psx", any()) } returns null andThen
            TitleIdResult(titleId = "SLUS-00001", fromBinary = true)

        assertEquals(emptySet<String>(), resolver.serialsFor(GAME_ID, "duckstation"))
        assertEquals(setOf("SLUS-00001"), resolver.serialsFor(GAME_ID, "duckstation"))
        verify(exactly = 2) { extractor.extractTitleIdWithSource(rom, "psx", any()) }
    }

    @Test
    fun `a filename-only answer is not kept and the disc is read again`() = runTest {
        coEvery { gameDao.getById(GAME_ID) } returns game()
        sigil(rom, "SLUS-00001", fromBinary = false)

        repeat(2) { resolver.serialsFor(GAME_ID, "duckstation") }

        verify(exactly = 2) { extractor.extractTitleIdWithSource(rom, "psx", any()) }
    }

    @Test
    fun `a rom-named emulator needs no serial and reads nothing`() = runTest {
        assertEquals(emptySet<String>(), resolver.serialsFor(GAME_ID, "retroarch"))
        assertEquals(emptySet<String>(), resolver.serialsFor(GAME_ID, EmulatorRegistry.BUILTIN_ID))
        coVerify(exactly = 0) { gameDao.getById(any()) }
    }

    private fun disc(number: Int, file: File?) = GameDiscEntity(
        gameId = GAME_ID,
        discNumber = number,
        rommId = 100L + number,
        fileName = file?.name ?: "Disc $number.chd",
        localPath = file?.absolutePath,
        fileSize = 1L
    )

    private companion object {
        const val GAME_ID = 3L
    }
}
