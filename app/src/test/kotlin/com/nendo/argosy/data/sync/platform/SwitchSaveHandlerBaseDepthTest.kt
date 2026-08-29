package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.emulator.SwitchProfileParser
import com.nendo.argosy.data.storage.AndroidDataAccessor
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.sync.fixtures.realFsFal
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * A resolved base reaches the handler at either depth: the save root, or the profile folder that
 * [SwitchSaveHandler.findActiveProfileFolder] already descended to. Reading the profile folder as
 * a save root discards the exact title match as a device save, then searches its siblings for a
 * nesting that is not there, so a save that exists reports as missing.
 */
class SwitchSaveHandlerBaseDepthTest {

    private lateinit var tempDir: File
    private lateinit var handler: SwitchSaveHandler

    private val androidDataAccessor = mockk<AndroidDataAccessor>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val profileParser = mockk<SwitchProfileParser>(relaxed = true)

    private val userFolder = "0000000000000000"
    private val profileFolder = "08AF382FBF2A6337F6BD70AFA147EBC0"
    private val titleId = "01002DA013484000"

    @Before
    fun setUp() {
        tempDir = createTempDirectory("switch_base_depth").toFile()
        every { context.cacheDir } returns File(tempDir, "cache").apply { mkdirs() }
        every { context.filesDir } returns tempDir
        val fal = realFsFal()
        handler = SwitchSaveHandler(context, fal, SaveArchiver(androidDataAccessor, fal), profileParser)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun saveRoot(): File = File(tempDir, "Eden/user/save").apply { mkdirs() }

    private fun profileDir(): File =
        File(saveRoot(), "$userFolder/$profileFolder").apply { mkdirs() }

    @Test
    fun `finds the title folder when handed the profile folder`() {
        val expected = File(profileDir(), titleId).apply { mkdirs() }

        val result = handler.findSaveFolderBySaveId(profileDir().absolutePath, titleId)

        assertEquals(expected.absolutePath, result)
    }

    @Test
    fun `siblings at the profile level are not mistaken for user folders`() {
        val profile = profileDir()
        File(profile, "01007EF00011E000").apply { mkdirs() }
        File(profile, "0100000000010000").apply { mkdirs() }
        val expected = File(profile, titleId).apply { mkdirs() }

        assertEquals(expected.absolutePath, handler.findSaveFolderBySaveId(profile.absolutePath, titleId))
    }

    @Test
    fun `a profile folder without the title reports nothing`() {
        val profile = profileDir()
        File(profile, "01007EF00011E000").apply { mkdirs() }

        assertNull(handler.findSaveFolderBySaveId(profile.absolutePath, titleId))
    }

    @Test
    fun `the save root still resolves through the user folder`() {
        val expected = File(saveRoot(), "$userFolder/$profileFolder/$titleId").apply { mkdirs() }

        val result = handler.findSaveFolderBySaveId(saveRoot().absolutePath, titleId)

        assertEquals(expected.absolutePath, result)
    }

    @Test
    fun `the save root still skips a title id sitting directly under it`() {
        val root = saveRoot()
        File(root, titleId).apply { mkdirs() }

        assertNull(handler.findSaveFolderBySaveId(root.absolutePath, titleId))
    }
}
