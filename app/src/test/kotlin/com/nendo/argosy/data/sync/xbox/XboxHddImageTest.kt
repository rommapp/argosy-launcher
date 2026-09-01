package com.nendo.argosy.data.sync.xbox

import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.storage.SeekableFile
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class XboxHddImageTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val titleId = "4D530064"
    private val saveBytes = "xbox save payload".toByteArray()

    @Test
    fun `lists the save ids the console wrote`() {
        val image = openFixture()

        assertEquals(listOf(titleId), image.listSaveIds())
        assertTrue(image.hasSave(titleId))
        assertFalse(image.hasSave("DEADBEEF"))
    }

    @Test
    fun `extracts a save directory to disk`() {
        val image = openFixture()
        val destination = temporaryFolder.newFolder("staged")

        assertTrue(image.extractSave(titleId, destination))
        assertArrayEquals(saveBytes, File(destination, "savegame.dat").readBytes())
    }

    @Test
    fun `staged files carry the time the console wrote them`() {
        val image = openFixture()
        val destination = temporaryFolder.newFolder("timestamped")
        assertTrue(image.extractSave(titleId, destination))

        val written = java.time.LocalDateTime.of(2018, 6, 13, 1, 5, 50)
            .toInstant(java.time.ZoneOffset.UTC)
            .toEpochMilli()
        assertEquals(written, File(destination, "savegame.dat").lastModified())
    }

    @Test
    fun `extracting a game with no save reports absence`() {
        val image = openFixture()

        assertFalse(image.extractSave("DEADBEEF", temporaryFolder.newFolder("empty")))
    }

    @Test
    fun `writes a staged save back into the image`() {
        val file = buildImage()
        val image = openFixture(file)
        val staged = temporaryFolder.newFolder("roundtrip")
        assertTrue(image.extractSave(titleId, staged))

        val replacement = "XBOX SAVE PAYLOAD".toByteArray()
        File(staged, "savegame.dat").writeBytes(replacement)
        image.writeSaveInPlace(titleId, staged)

        val reopened = openFixture(file)
        val verified = temporaryFolder.newFolder("verify")
        assertTrue(reopened.extractSave(titleId, verified))
        assertArrayEquals(replacement, File(verified, "savegame.dat").readBytes())
    }

    @Test
    fun `refuses a staged file the image does not already hold`() {
        val image = openFixture()
        val staged = temporaryFolder.newFolder("extra")
        assertTrue(image.extractSave(titleId, staged))
        File(staged, "unknown.dat").writeBytes(ByteArray(4))

        assertTrue(runCatching { image.writeSaveInPlace(titleId, staged) }.isFailure)
    }

    @Test
    fun `refuses a staged file that changed size`() {
        val image = openFixture()
        val staged = temporaryFolder.newFolder("grown")
        assertTrue(image.extractSave(titleId, staged))
        File(staged, "savegame.dat").writeBytes(ByteArray(saveBytes.size + 1))

        assertTrue(runCatching { image.writeSaveInPlace(titleId, staged) }.isFailure)
    }

    @Test
    fun `an unreachable path opens nothing`() {
        val fal = mockk<FileAccessLayer>()
        every { fal.openSeekable(any(), any()) } returns null

        assertNull(XboxHddImage.open(fal, "/nowhere/hdd.img", writable = false))
    }

    private fun openFixture(file: SeekableFile = buildImage()): XboxHddImage {
        val fal = mockk<FileAccessLayer>()
        every { fal.openSeekable(any(), any()) } returns file
        return XboxHddImage.open(fal, "/x1box/hdd.img", writable = true)!!
    }

    private fun buildImage(): SeekableFile {
        val partitionOffset = 0x55F400L * 512
        val partitionSize = 64L * 1024 * 1024
        val qcow2 = Qcow2Fixture(virtualSize = partitionOffset + partitionSize)
        val fatx = FatxFixture(qcow2, partitionOffset, partitionSize)

        fatx.writeSuperblock()
        val root = fatx.allocateCluster()
        val udata = fatx.allocateCluster()
        val save = fatx.allocateCluster()
        val payload = fatx.allocateCluster()

        fatx.writeDirectory(
            root,
            listOf(FatxFixture.Dirent(XboxHddImage.UDATA, isDirectory = true, firstCluster = udata))
        )
        fatx.writeDirectory(
            udata,
            listOf(FatxFixture.Dirent(titleId, isDirectory = true, firstCluster = save))
        )
        fatx.writeDirectory(
            save,
            listOf(
                FatxFixture.Dirent(
                    "savegame.dat",
                    isDirectory = false,
                    firstCluster = payload,
                    size = saveBytes.size
                )
            )
        )
        fatx.writeFile(payload, saveBytes)

        return qcow2.build()
    }
}
