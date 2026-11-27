package com.nendo.argosy.data.mod

import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.storage.FileInfo
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Assume.assumeNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.OutputStream

class SwitchModInstallerTest {
    @get:Rule val temporary = TemporaryFolder()
    private val title = "0100F2C0115B6000"
    private val relative = "load/$title/UltraCam/exefs/subsdk3"
    private lateinit var dataRoot: File
    private lateinit var stateRoot: File
    private lateinit var fal: FileAccessLayer
    private lateinit var installer: SwitchModInstaller
    private var failCommitOnce = false
    private var refuseDeletion = false
    private var failWrites = false

    @Before
    fun setUp() {
        dataRoot = temporary.newFolder("eden")
        stateRoot = temporary.newFolder("state")
        fal = mockk()
        every { fal.getTransformedFile(any()) } answers { File(firstArg<String>()) }
        every { fal.isListable(any()) } answers { File(firstArg<String>()).list() != null }
        every { fal.listFilesUnion(any()) } answers {
            File(firstArg<String>()).listFiles().orEmpty().map {
                FileInfo(it.absolutePath, it.name, it.isDirectory, it.isFile, it.length(), it.lastModified())
            }
        }
        every { fal.mkdirs(any()) } answers { File(firstArg<String>()).mkdirs() }
        every { fal.isDirectory(any()) } answers { File(firstArg<String>()).isDirectory }
        every { fal.getInputStream(any()) } answers { File(firstArg<String>()).takeIf { it.isFile }?.inputStream() }
        every { fal.getOutputStream(any()) } answers {
            val target = File(firstArg<String>())
            if (failWrites) object : OutputStream() {
                override fun write(value: Int) { target.writeBytes(byteArrayOf(value.toByte())); throw IOException("interrupted") }
            } else target.outputStream()
        }
        every { fal.delete(any()) } answers { !refuseDeletion && File(firstArg<String>()).delete() }
        every { fal.prepareSaveAccess(*anyVararg()) } returns true
        every { fal.commitSaveAccess(*anyVararg()) } answers {
            if (failCommitOnce) { failCommitOnce = false; false } else true
        }
        installer = SwitchModInstaller(fal)
    }

    @Test
    fun `installs updates and removes only its own files`() = runTest {
        install(zip(relative to "first"))
        assertEquals("first", target().readText())
        install(zip(relative to "second"))
        assertEquals("second", target().readText())
        installer.remove(title, "mod", dataRoot.path, stateRoot, true)
        assertFalse(target().exists())
    }

    @Test
    fun `preserves identical preexisting files on removal and refuses differing files`() = runTest {
        target().parentFile!!.mkdirs()
        target().writeText("existing")
        install(zip(relative to "existing"))
        expectFailure(ModInstallFailure.CONFLICT) { install(zip(relative to "different")) }
        assertEquals("existing", target().readText())
        installer.remove(title, "mod", dataRoot.path, stateRoot, true)
        assertEquals("existing", target().readText())
        expectFailure(ModInstallFailure.CONFLICT) { install(zip(relative to "different")) }
        assertEquals("existing", target().readText())
    }

    @Test
    fun `a supplied real package installs handheld config and removes without changing its source`() = runTest {
        val source = System.getenv("ARGOSY_MOD_TEST_SOURCE")
        assumeNotNull(source)
        val archive = File(source!!)
        val digest = modHash(archive)
        val result = installer.install(archive, title, "real-package", dataRoot.path, stateRoot, true, SwitchModProfile.HANDHELD_720)
        assertTrue(result.skippedDesktopSettings)
        val config = File(dataRoot, UltraCamHandheldProfile.CONFIG_PATH).readText()
        assertTrue(Regex("(?m)^MaxFPS\\s*=\\s*60\\s*$").containsMatchIn(config))
        assertTrue(Regex("(?m)^ShadowResolution\\s*=\\s*1024\\s*$").containsMatchIn(config))
        assertFalse(File(dataRoot, "config/custom/$title.ini").exists())
        installer.remove(title, "real-package", dataRoot.path, stateRoot, true)
        assertTrue(dataRoot.walkTopDown().none { it.isFile })
        assertEquals(digest, modHash(archive))
    }

    @Test
    fun `refuses updates and removal after the user changes an installed file`() = runTest {
        install(zip(relative to "installed"))
        target().writeText("user edit")
        expectFailure(ModInstallFailure.MODIFIED_FILE) { install(zip(relative to "update")) }
        expectFailure(ModInstallFailure.MODIFIED_FILE) { installer.remove(title, "mod", dataRoot.path, stateRoot, true) }
        assertEquals("user edit", target().readText())
    }

    @Test
    fun `rejects overlapping packages even when payloads match`() = runTest {
        install(zip(relative to "installed"))
        expectFailure(ModInstallFailure.CONFLICT) {
            installer.install(zip(relative to "installed"), title, "other", dataRoot.path, stateRoot, true)
        }
        assertEquals("installed", target().readText())
    }

    @Test
    fun `rolls back an update when managed storage refuses the commit`() = runTest {
        install(zip(relative to "original"))
        failCommitOnce = true
        expectFailure(ModInstallFailure.ACCESS_DENIED) { install(zip(relative to "update")) }
        assertEquals("original", target().readText())
        installer.remove(title, "mod", dataRoot.path, stateRoot, true)
        assertFalse(target().exists())
    }

    @Test
    fun `persists failed rollback and recovers it through a new installer instance`() = runTest {
        failWrites = true
        refuseDeletion = true
        try { install(zip(relative to "interrupted")) } catch (_: IOException) { }
        assertTrue(target().exists())
        assertTrue(stateRoot.walk().any { it.name == "pending.properties" })
        failWrites = false
        refuseDeletion = false
        installer = SwitchModInstaller(fal)
        expectFailure(ModInstallFailure.NEEDS_RECOVERY) { install(zip(relative to "replacement")) }
        expectFailure(ModInstallFailure.MODIFIED_FILE) {
            installer.recover(title, "mod", dataRoot.path, stateRoot, true)
        }
        installer.recover(title, "mod", dataRoot.path, stateRoot, true, restoreInterruptedFiles = true)
        assertFalse(target().exists())
        assertFalse(stateRoot.walk().any { it.name == "pending.properties" })
    }

    @Test
    fun `requires close acknowledgement before any install or remove`() = runTest {
        expectFailure(ModInstallFailure.EMULATOR_CLOSE_REQUIRED) {
            installer.install(zip(relative to "payload"), title, "mod", dataRoot.path, stateRoot, false)
        }
        assertFalse(target().exists())
        assertTrue(stateRoot.listFiles().orEmpty().isEmpty())
        expectFailure(ModInstallFailure.EMULATOR_CLOSE_REQUIRED) { installer.remove(title, "mod", dataRoot.path, stateRoot, false) }
    }

    @Test
    fun `denied directory listing never becomes permission to overwrite`() = runTest {
        target().parentFile!!.mkdirs()
        target().writeText("existing")
        every { fal.isListable(dataRoot.path) } returns false
        expectFailure(ModInstallFailure.ACCESS_DENIED) { install(zip(relative to "replacement")) }
        assertEquals("existing", target().readText())
    }

    private suspend fun install(archive: File) = installer.install(archive, title, "mod", dataRoot.path, stateRoot, true)

    private fun target(): File = File(dataRoot, relative)

    private fun zip(vararg files: Pair<String, String>): File = temporary.newFile().also { file ->
        ZipArchiveOutputStream(file).use { output -> files.forEach { (path, text) ->
            output.putArchiveEntry(ZipArchiveEntry(path))
            output.write(text.toByteArray())
            output.closeArchiveEntry()
        } }
    }

    private suspend fun expectFailure(reason: ModInstallFailure, action: suspend () -> Unit) {
        try {
            action()
            throw AssertionError("Expected $reason")
        } catch (failure: ModInstallException) {
            assertEquals(reason, failure.reason)
        }
    }
}
