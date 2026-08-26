package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.sync.fixtures.realFsFal
import com.nendo.argosy.data.storage.AndroidDataAccessor
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * Which part of `<userDir>/sdmc/Nintendo 3DS/<id0>/<id1>/title/...` a user thinks of as "the
 * save path" differs per emulator, so every level of it resolves to the same scan root.
 */
class N3dsBasePathTest {

    private lateinit var tempDir: File
    private lateinit var handler: PlatformSaveHandler
    private lateinit var sdRoot: File

    private val androidDataAccessor = mockk<AndroidDataAccessor>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    private val config = SavePathConfig(
        emulatorId = "azahar",
        defaultPaths = emptyList(),
        saveExtensions = listOf("*"),
        usesFolderBasedSaves = true
    )

    @Before
    fun setUp() {
        tempDir = createTempDirectory("n3ds_base").toFile()
        every { context.cacheDir } returns File(tempDir, "cache").apply { mkdirs() }
        val fal = realFsFal()
        val registry = PlatformSaveHandlerRegistry(
            context = context,
            fal = fal,
            saveArchiver = SaveArchiver(androidDataAccessor, fal),
            switchSaveHandler = mockk(relaxed = true),
            gciSaveHandler = mockk(relaxed = true),
            retroArchSaveHandler = mockk(relaxed = true),
            defaultSaveHandler = mockk(relaxed = true),
            dreamcastSaveHandler = mockk(relaxed = true),
        )
        handler = registry.getFolderHandler("3ds") ?: error("3DS handler not registered")
        sdRoot = File(tempDir, "Azahar/sdmc/Nintendo 3DS").apply { mkdirs() }
        File(sdRoot, "00000000000000000000000000000000/00000000000000000000000000000000/title/00040000/00033500/data")
            .mkdirs()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun resolve(path: String) = handler.resolveBasePath(config, path)

    @Test
    fun `the sd root itself is taken as given`() {
        assertEquals(sdRoot.absolutePath, resolve(sdRoot.absolutePath))
    }

    @Test
    fun `a trailing separator does not change the result`() {
        assertEquals(sdRoot.absolutePath, resolve(sdRoot.absolutePath + "/"))
    }

    @Test
    fun `pointing at sdmc walks down to the sd root`() {
        assertEquals(sdRoot.absolutePath, resolve(File(tempDir, "Azahar/sdmc").absolutePath))
    }

    @Test
    fun `pointing at the emulator folder walks down to the sd root`() {
        assertEquals(sdRoot.absolutePath, resolve(File(tempDir, "Azahar").absolutePath))
    }

    @Test
    fun `pointing inside the title tree trims back to the sd root`() {
        val deep = File(sdRoot, "00000000000000000000000000000000/00000000000000000000000000000000/title/00040000")
        assertEquals(sdRoot.absolutePath, resolve(deep.absolutePath))
    }

    @Test
    fun `a path with no sd root anywhere is left alone`() {
        val unrelated = File(tempDir, "somewhere/else").apply { mkdirs() }
        assertEquals(unrelated.absolutePath, resolve(unrelated.absolutePath))
    }
}
