package com.nendo.argosy.data.download

import com.nendo.argosy.data.local.entity.DownloadQueueEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DownloadRequestIdentityTest {
    private val base = DownloadQueueEntity(
        gameId = 1, rommId = 2, fileName = "game.zip", gameTitle = "Game",
        platformSlug = "nes", coverPath = null, bytesDownloaded = 0, totalBytes = 0,
        state = DownloadState.FAILED.name, errorReason = null, tempFilePath = null
    )

    private fun identity(row: DownloadQueueEntity) = DownloadRequestIdentity.from(row)

    @Test fun `identity ignores attribution and presentation but preserves null selection semantics`() {
        assertEquals(identity(base), identity(base.copy(ownerUserId = 9, gameTitle = "Other", coverPath = "cover", totalBytes = 900)))
        assertEquals(identity(base), identity(base.copy(platformSlug = "NES")))
        assertNotEquals(identity(base), identity(base.copy(selectedFileIds = "")))
        assertEquals(identity(base.copy(selectedFileIds = "9,3,9")), identity(base.copy(selectedFileIds = "3,9")))
    }

    @Test fun `independent kinds never match or share logical slot`() {
        val disc = identity(base.copy(discId = 7, discNumber = 1))
        val addon = identity(base.copy(gameFileId = 8, fileCategory = "update"))
        assertNotEquals(identity(base), disc)
        assertNotEquals(identity(base), addon)
        assertFalse(identity(base).sharesSlot(disc))
        assertFalse(disc.sharesSlot(addon))
        assertFalse(addon.sharesSlot(identity(base)))
    }

    @Test fun `selection trims valid tokens and retains malformed tokens fail closed`() {
        val valid = identity(base.copy(selectedFileIds = "3,9"))
        assertEquals(valid, identity(base.copy(selectedFileIds = " 9, 03 ,9 ")))
        assertNotEquals(valid, identity(base.copy(selectedFileIds = "3,invalid,9")))
        assertNotEquals(valid, identity(base.copy(selectedFileIds = "3,,9")))
        assertNotEquals(identity(base.copy(selectedFileIds = "")), identity(base.copy(selectedFileIds = "bad")))
    }

    @Test fun `disc and file metadata differences do not match but same logical IDs conflict`() {
        val disc = base.copy(discId = 7, discNumber = 1)
        val file = base.copy(gameFileId = 8, fileCategory = "update")
        for (changed in listOf(disc.copy(rommId = 3), disc.copy(discNumber = 2), disc.copy(gameFolderName = "Other"))) {
            assertNotEquals(identity(disc), identity(changed))
            assertTrue(identity(disc).sharesSlot(identity(changed)))
        }
        for (changed in listOf(file.copy(rommId = 3), file.copy(fileCategory = "dlc"), file.copy(gameFolderName = "Other"))) {
            assertNotEquals(identity(file), identity(changed))
            assertTrue(identity(file).sharesSlot(identity(changed)))
        }
        assertFalse(identity(disc).sharesSlot(identity(disc.copy(discId = 9))))
        assertFalse(identity(file).sharesSlot(identity(file.copy(gameFileId = 9))))
    }

    @Test fun `different retained part names still reserve the same verified target`() {
        val directory = File("synthetic-downloads").absoluteFile
        val first = base.copy(tempFilePath = File(directory, "first.tmp").path)
        val second = base.copy(tempFilePath = File(directory, "second.tmp").path)
        val common = first.transferFiles(File(directory, "first-intended.tmp"))
            .intersect(second.transferFiles(File(directory, "second-intended.tmp")))
        assertEquals(setOf(File(directory, base.fileName).canonicalFile), common)
    }
}
