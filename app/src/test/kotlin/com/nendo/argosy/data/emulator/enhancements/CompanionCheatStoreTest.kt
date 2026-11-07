package com.nendo.argosy.data.emulator.enhancements

import com.nendo.argosy.data.local.dao.CheatDao
import com.nendo.argosy.data.local.entity.CheatEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CompanionCheatStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val cheatDao = mockk<CheatDao>(relaxed = true)
    private val store = CompanionCheatStore(cheatDao, mockk(relaxed = true))

    @Test
    fun `importing again keeps the row the player already has`() = runTest {
        val existing = CheatEntity(id = 1, gameId = 7, cheatIndex = 3, description = "60 FPS", code = "old", enabled = false)
        coEvery { cheatDao.getCheatsForGame(7) } returns listOf(existing)
        coEvery { cheatDao.getMaxCheatIndex(7) } returns 3
        val inserted = slot<List<CheatEntity>>()
        coEvery { cheatDao.insertAll(capture(inserted)) } returns Unit

        store.add(
            7,
            listOf(
                ImportedCheat("60 FPS", "new", enabled = true),
                ImportedCheat("Classic Controller", "code", enabled = true)
            )
        )

        assertEquals(listOf("Classic Controller"), inserted.captured.map { it.description })
        assertEquals(4, inserted.captured.single().cheatIndex)
        assertEquals(true, inserted.captured.single().isUserCreated)
    }

    @Test
    fun `nothing new inserts nothing`() = runTest {
        val existing = CheatEntity(id = 1, gameId = 7, cheatIndex = 0, description = "60 FPS", code = "old")
        coEvery { cheatDao.getCheatsForGame(7) } returns listOf(existing)

        store.add(7, listOf(ImportedCheat("60 FPS", "new", enabled = true)))

        coVerify(exactly = 0) { cheatDao.insertAll(any()) }
    }

    @Test
    fun `archives yield only the wanted extension under their base name`() {
        val archive = File(tempFolder.root, "pack.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("GameSettings/RMCE01.ini"))
            zip.write("[Gecko]".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("RMCE01/tex1_a.png"))
            zip.write(1)
            zip.closeEntry()
        }

        val found = store.readArchive(archive, "ini")

        assertEquals(listOf(CompanionText("RMCE01.ini", "[Gecko]")), found)
    }

    @Test
    fun `an unreadable archive yields nothing instead of failing the launch`() {
        val archive = File(tempFolder.root, "broken.zip").apply { writeText("not a zip") }
        assertEquals(emptyList<CompanionText>(), store.readArchive(archive, "ini"))
    }
}
