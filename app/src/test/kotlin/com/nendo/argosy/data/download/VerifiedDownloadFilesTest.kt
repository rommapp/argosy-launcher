package com.nendo.argosy.data.download

import com.nendo.argosy.data.download.transfer.TransferCheckpoint
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VerifiedDownloadFilesTest {
    @get:Rule val storage = TemporaryFolder()

    @Test
    fun `catalog size alone cannot complete a file`() {
        val part = storage.newFile("game.tmp").apply { writeBytes(ByteArray(1024)) }
        assertFalse(VerifiedDownloadFiles.matches(part, TransferCheckpoint(totalBytes = 1024)))
    }

    @Test
    fun `partial range marker cannot complete a representation`() {
        val part = storage.newFile("game.tmp").apply { writeBytes(ByteArray(1024)) }
        assertFalse(VerifiedDownloadFiles.matches(part, TransferCheckpoint(totalBytes = 2048, validatedBytes = 1024)))
    }

    @Test
    fun `verified part is renamed in its existing directory`() {
        val part = storage.newFile("game.tmp").apply { writeBytes(ByteArray(1024)) }
        val target = File(part.parentFile, "game.rom")
        val marker = TransferCheckpoint(totalBytes = 1024, validatedBytes = 1024)
        VerifiedDownloadFiles.promote(part, target, marker)
        assertFalse(part.exists())
        assertTrue(VerifiedDownloadFiles.matches(target, marker))
    }

    @Test(expected = IllegalStateException::class)
    fun `cross directory promotion has no copy fallback`() {
        val part = storage.newFile("game.tmp").apply { writeBytes(ByteArray(1024)) }
        VerifiedDownloadFiles.promote(part, File(storage.newFolder(), "game.rom"),
            TransferCheckpoint(validatedBytes = 1024))
    }
}
