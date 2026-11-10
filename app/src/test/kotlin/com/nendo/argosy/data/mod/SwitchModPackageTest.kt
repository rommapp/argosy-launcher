package com.nendo.argosy.data.mod

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SwitchModPackageTest {
    @get:Rule val temporary = TemporaryFolder()
    private val title = "0100F2C0115B6000"
    private val payload = "load/$title/UltraCam/exefs/subsdk3"

    @Test
    fun `matches the shared cross client path contract`() {
        javaClass.getResourceAsStream("/switch-mod-paths.tsv")!!.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val (expected, path) = line.split('\t', limit = 2)
                val actual = try {
                    if (SwitchModPackage.canonicalPath(path, title) == null) "skip" else "install"
                } catch (_: ModInstallException) { "reject" }
                assertEquals(path, expected, actual)
            }
        }
    }

    @Test
    fun `stages payload and title bound UltraCam config without importing desktop emulator settings`() {
        val archive = zip(
            payload to "binary",
            "sdmc/UltraCam/TOTK/Config/maxlastbreath.ini" to "[UltraCamFPS]\nMaxFPS=30",
            "config/custom/$title.ini" to "[Controls]\ncontrols=desktop",
            "README.txt" to "description",
            "__MACOSX/._file" to "metadata"
        )
        val staged = SwitchModPackage.stage(archive, title.lowercase(), temporary.newFolder())
        assertEquals(2, staged.files.size)
        assertTrue(staged.skippedDesktopSettings)
        assertEquals("binary", staged.files.first { it.relativePath == payload }.file.readText())
        assertEquals("[UltraCamFPS]\nMaxFPS=30", staged.files.first { it.relativePath.startsWith("sdmc/") }.file.readText())
        assertFalse(staged.files.any { it.relativePath.startsWith("config/") })
    }

    @Test
    fun `rejects traversal and Windows alternate paths before writing staged payloads`() {
        listOf("../escape", "/absolute", "load/$title/mod/romfs/../escape", "load/$title/mod/romfs/C:drive", "load/$title/mod/romfs/back\\slash").forEach { unsafe ->
            val stage = temporary.newFolder()
            expectFailure(ModInstallFailure.UNSAFE_PATH) {
                SwitchModPackage.stage(zip(payload to "valid", unsafe to "bad"), title, stage)
            }
            assertTrue(stage.listFiles().orEmpty().isEmpty())
        }
    }

    @Test
    fun `rejects another title and another UltraCam game`() {
        expectFailure(ModInstallFailure.WRONG_TITLE) {
            SwitchModPackage.stage(zip("load/01007EF00011E000/mod/exefs/main" to "bad"), title, temporary.newFolder())
        }
        expectFailure(ModInstallFailure.UNSUPPORTED_PATH) {
            SwitchModPackage.stage(zip(payload to "valid", "sdmc/UltraCam/BOTW/Config/settings.ini" to "bad"), title, temporary.newFolder())
        }
    }

    @Test
    fun `rejects unknown files and case insensitive destination collisions`() {
        expectFailure(ModInstallFailure.UNSUPPORTED_PATH) {
            SwitchModPackage.stage(zip(payload to "valid", "nand/save/file" to "bad"), title, temporary.newFolder())
        }
        expectFailure(ModInstallFailure.DUPLICATE_PATH) {
            SwitchModPackage.stage(zip(payload to "valid", payload.replace("UltraCam", "ultracam") to "bad"), title, temporary.newFolder())
        }
        expectFailure(ModInstallFailure.DUPLICATE_PATH) {
            SwitchModPackage.stage(zip(payload to "valid", "$payload/child" to "bad"), title, temporary.newFolder())
        }
        expectFailure(ModInstallFailure.DUPLICATE_PATH) {
            SwitchModPackage.stage(zip(payload to "valid", "$payload.b" to "between", "$payload/child" to "bad"), title, temporary.newFolder())
        }
        expectFailure(ModInstallFailure.DUPLICATE_PATH) {
            SwitchModPackage.stage(zip(payload to "valid", "load/$title/ultracam/exefs/other" to "bad"), title, temporary.newFolder())
        }
    }

    @Test
    fun `rejects symbolic links and special Unix entries`() {
        listOf(0xa1ff, 0x11ff).forEach { mode ->
            val archive = temporary.newFile()
            ZipArchiveOutputStream(archive).use { output ->
                val entry = ZipArchiveEntry(payload).apply { unixMode = mode }
                output.putArchiveEntry(entry)
                output.write("target".toByteArray())
                output.closeArchiveEntry()
            }
            expectFailure(ModInstallFailure.INVALID_ARCHIVE) { SwitchModPackage.stage(archive, title, temporary.newFolder()) }
        }
    }

    @Test
    fun `rejects an archive without installable content`() {
        expectFailure(ModInstallFailure.EMPTY_PACKAGE) {
            SwitchModPackage.stage(zip("README.txt" to "description"), title, temporary.newFolder())
        }
    }

    @Test
    fun `rejects excessive entry counts before extraction`() {
        val archive = temporary.newFile()
        ZipArchiveOutputStream(archive).use { output ->
            repeat(SwitchModPackage.MAX_ENTRIES + 1) { index ->
                output.putArchiveEntry(ZipArchiveEntry("load/$title/mod/romfs/$index"))
                output.closeArchiveEntry()
            }
        }
        val stage = temporary.newFolder()
        expectFailure(ModInstallFailure.TOO_LARGE) { SwitchModPackage.stage(archive, title, stage) }
        assertTrue(stage.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `rejects an oversized declared entry before decompression`() {
        val archive = zip(payload to "small")
        val bytes = archive.readBytes()
        val central = (0..bytes.size - 4).first { offset ->
            bytes[offset] == 0x50.toByte() && bytes[offset + 1] == 0x4b.toByte() &&
                bytes[offset + 2] == 1.toByte() && bytes[offset + 3] == 2.toByte()
        }
        val size = SwitchModPackage.MAX_FILE_BYTES + 1
        repeat(4) { index -> bytes[central + 24 + index] = (size shr (index * 8)).toByte() }
        archive.writeBytes(bytes)
        val stage = temporary.newFolder()
        expectFailure(ModInstallFailure.TOO_LARGE) { SwitchModPackage.stage(archive, title, stage) }
        assertTrue(stage.listFiles().orEmpty().isEmpty())
    }

    private fun zip(vararg files: Pair<String, String>): File = temporary.newFile().also { file ->
        ZipArchiveOutputStream(file).use { output ->
            files.forEach { (path, text) ->
                output.putArchiveEntry(ZipArchiveEntry(path))
                output.write(text.toByteArray())
                output.closeArchiveEntry()
            }
        }
    }

    private fun expectFailure(reason: ModInstallFailure, action: () -> Unit) {
        try {
            action()
            throw AssertionError("Expected $reason")
        } catch (failure: ModInstallException) {
            assertEquals(reason, failure.reason)
        }
    }
}
