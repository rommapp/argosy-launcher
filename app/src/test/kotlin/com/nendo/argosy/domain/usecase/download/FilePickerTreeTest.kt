package com.nendo.argosy.domain.usecase.download

import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.model.FilePickerRow
import com.nendo.argosy.data.model.FolderCheckState
import com.nendo.argosy.data.model.folderCheckStates
import com.nendo.argosy.data.model.initiallyCollapsedFolders
import com.nendo.argosy.data.model.selectableFileIdsWithin
import com.nendo.argosy.data.model.visibleWithCollapsed
import com.nendo.argosy.data.remote.romm.RomMRomFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilePickerTreeTest {

    private val root = "Skyrim Special Edition"

    private data class Fixture(val id: Long, val dir: String, val name: String, val category: String?)

    private val fixture = listOf(
        Fixture(1, root, "SkyrimSE.exe", null),
        Fixture(2, root, "Data.bsa", null),
        Fixture(10, "$root/mods/core", "core-a.7z", "mod"),
        Fixture(11, "$root/mods/core", "core-b.7z", "mod"),
        Fixture(12, "$root/mods/core", "core-c.7z", "mod"),
        Fixture(20, "$root/mods/custom", "custom-a.7z", "mod"),
        Fixture(21, "$root/mods/custom", "custom-b.7z", "mod"),
        Fixture(30, "$root/mods/collections/xxsqm4-r104", "col-a.7z", "mod"),
        Fixture(31, "$root/mods/collections/xxsqm4-r104", "col-b.7z", "mod"),
        Fixture(32, "$root/mods/collections/xxsqm4-r104", "col-c.7z", "mod"),
        Fixture(33, "$root/mods/collections/xxsqm4-r104", "col-d.7z", "mod"),
        Fixture(34, "$root/mods/collections/xxsqm4-r104", "col-e.7z", "mod"),
        Fixture(35, "$root/mods/collections/xxsqm4-r104", "col-f.7z", "mod"),
        Fixture(40, "$root/Extras/Art", "art-a.png", null),
        Fixture(41, "$root/Extras/Art", "art-b.png", null)
    )

    private fun romFiles(items: List<Fixture> = fixture) = items.map {
        RomMRomFile(
            id = it.id,
            romId = 27077,
            fileName = it.name,
            filePath = it.dir,
            fileSizeBytes = it.id * 100,
            fullPath = "${it.dir}/${it.name}",
            category = it.category
        )
    }

    private fun downloadTree(items: List<Fixture> = fixture): List<FilePickerRow> =
        FilePickerTree.build(romFiles(items).map { it.toPickerEntry(isDownloaded = false) }, lockSoleBaseFile = true)

    private fun manageTree(items: List<Fixture> = fixture): List<FilePickerRow> {
        val entities = items.map {
            val storedCategory = when {
                it.category != null -> it.category
                it.dir == root -> "game"
                else -> "unknown"
            }
            GameFileEntity(
                gameId = 1,
                rommFileId = it.id,
                fileName = it.name,
                filePath = it.dir,
                category = storedCategory,
                fileSize = it.id * 100
            )
        }
        return FilePickerTree.build(
            entities.map { it.toPickerEntry(it.rommFileId!!, isDownloaded = false, isLocked = false) },
            lockSoleBaseFile = false
        )
    }

    private fun List<FilePickerRow>.folder(path: String): FilePickerRow = single { it.folderPath == path }

    private fun List<FilePickerRow>.fileIds(): List<Long> = filter { !it.isFolder }.mapNotNull { it.rommFileId }

    @Test
    fun `base files lead unheaded then category then uncategorised folders`() {
        val rows = downloadTree()
        val outline = rows.map { (it.folderPath ?: it.label) to it.depth }
        assertEquals(
            listOf(
                "Data.bsa" to 0,
                "SkyrimSE.exe" to 0,
                "mod" to 0,
                "mod/collections/xxsqm4-r104" to 1,
                "col-a.7z" to 2,
                "col-b.7z" to 2,
                "col-c.7z" to 2,
                "col-d.7z" to 2,
                "col-e.7z" to 2,
                "col-f.7z" to 2,
                "mod/core" to 1,
                "core-a.7z" to 2,
                "core-b.7z" to 2,
                "core-c.7z" to 2,
                "mod/custom" to 1,
                "custom-a.7z" to 2,
                "custom-b.7z" to 2,
                "folder:Extras/Art" to 0,
                "art-a.png" to 1,
                "art-b.png" to 1
            ),
            outline
        )
    }

    @Test
    fun `folder rows carry parent, count and total size of everything beneath`() {
        val rows = downloadTree()
        val mod = rows.folder("mod")
        assertEquals(null, mod.parentPath)
        assertEquals(11, mod.fileCount)
        assertEquals((10L..12L).sum() * 100 + (20L..21L).sum() * 100 + (30L..35L).sum() * 100, mod.sizeBytes)
        val core = rows.folder("mod/core")
        assertEquals("mod", core.parentPath)
        assertEquals(3, core.fileCount)
        assertTrue(rows.filter { it.rommFileId in 10L..12L }.all { it.parentPath == "mod/core" })
    }

    @Test
    fun `single-child folder chain collapses into one row`() {
        val rows = downloadTree()
        assertEquals("collections / xxsqm4-r104", rows.folder("mod/collections/xxsqm4-r104").label)
        assertTrue(rows.none { it.folderPath == "mod/collections" })
        assertEquals("Extras / Art", rows.folder("folder:Extras/Art").label)
        assertTrue(rows.folder("folder:Extras/Art").isUncategorised)
    }

    @Test
    fun `category root comes from the common folder of its files`() {
        val coreOnly = fixture.filter { it.category == null || it.dir.endsWith("core") }
        val rows = downloadTree(coreOnly)
        assertTrue(rows.none { it.folderPath == "mod/core" })
        assertTrue(rows.filter { it.rommFileId in 10L..12L }.all { it.parentPath == "mod" && it.depth == 1 })
    }

    @Test
    fun `collapsing a folder hides every descendant and nothing else`() {
        val rows = downloadTree()
        val withoutMod = rows.visibleWithCollapsed(setOf("mod"))
        assertTrue(withoutMod.any { it.folderPath == "mod" })
        assertTrue(withoutMod.none { it.isWithin("mod") })
        assertEquals(listOf(2L, 1L, 40L, 41L), withoutMod.fileIds())

        val withoutCore = rows.visibleWithCollapsed(setOf("mod/core"))
        assertTrue(withoutCore.any { it.folderPath == "mod/core" })
        assertTrue(withoutCore.none { it.rommFileId in 10L..12L })
        assertTrue(withoutCore.any { it.rommFileId == 20L })
        assertTrue(withoutCore.any { it.folderPath == "mod/custom" })

        assertEquals(rows, rows.visibleWithCollapsed(emptySet()))
    }

    @Test
    fun `large groups start collapsed down the tree while small ones open`() {
        val collapsed = downloadTree().initiallyCollapsedFolders()
        assertEquals(setOf("mod", "mod/core", "mod/custom", "mod/collections/xxsqm4-r104"), collapsed)
        assertFalse("folder:Extras/Art" in collapsed)
    }

    @Test
    fun `folder selection is tri-state over every file beneath it`() {
        val rows = downloadTree()
        assertEquals(FolderCheckState.NONE, rows.folderCheckStates(emptySet())["mod"])

        val oneCore = rows.folderCheckStates(setOf(10L))
        assertEquals(FolderCheckState.SOME, oneCore["mod"])
        assertEquals(FolderCheckState.SOME, oneCore["mod/core"])
        assertEquals(FolderCheckState.NONE, oneCore["mod/custom"])

        val allCore = rows.folderCheckStates(setOf(10L, 11L, 12L))
        assertEquals(FolderCheckState.ALL, allCore["mod/core"])
        assertEquals(FolderCheckState.SOME, allCore["mod"])

        val allMods = rows.selectableFileIdsWithin("mod")
        assertEquals(((10L..12L) + (20L..21L) + (30L..35L)).toSet(), allMods)
        assertEquals(FolderCheckState.ALL, rows.folderCheckStates(allMods)["mod"])
        assertEquals(FolderCheckState.NONE, rows.folderCheckStates(allMods)["folder:Extras/Art"])
    }

    @Test
    fun `manage mode and download mode build the same tree for the same paths`() {
        assertEquals(downloadTree(), manageTree())
    }

    @Test
    fun `download mode locks a sole base file and manage mode does not`() {
        val soleBase = fixture.filter { it.id != 2L }
        assertTrue(downloadTree(soleBase).single { it.rommFileId == 1L }.isLocked)
        assertFalse(manageTree(soleBase).single { it.rommFileId == 1L }.isLocked)
        assertFalse(downloadTree().single { it.rommFileId == 1L }.isLocked)
    }
}
