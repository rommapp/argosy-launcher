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
 * PPSSPP's setup has the user create a memory stick folder, and what they later point Argosy at
 * can be that folder, its `PSP` folder or `SAVEDATA` inside it. Every level resolves to
 * `SAVEDATA`, the folder the PSP handler scans.
 */
class PspBasePathTest {

    private lateinit var tempDir: File
    private lateinit var handler: PlatformSaveHandler
    private lateinit var memStick: File
    private lateinit var saveData: File

    private val androidDataAccessor = mockk<AndroidDataAccessor>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)

    private val config = SavePathConfig(
        emulatorId = "ppsspp",
        defaultPaths = emptyList(),
        saveExtensions = listOf("*"),
        usesFolderBasedSaves = true
    )

    @Before
    fun setUp() {
        tempDir = createTempDirectory("psp_base").toFile()
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
        handler = registry.getFolderHandler("psp") ?: error("PSP handler not registered")
        memStick = File(tempDir, "MemStick").apply { mkdirs() }
        saveData = File(memStick, "PSP/SAVEDATA").apply { mkdirs() }
        File(memStick, "PSP/SYSTEM").mkdirs()
        File(saveData, "ULUS10064DATA00").mkdirs()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun resolve(path: String) = handler.resolveBasePath(config, path)

    @Test
    fun `savedata itself is taken as given`() {
        assertEquals(saveData.absolutePath, resolve(saveData.absolutePath))
    }

    @Test
    fun `a trailing separator does not change the result`() {
        assertEquals(saveData.absolutePath, resolve(saveData.absolutePath + "/"))
    }

    @Test
    fun `pointing at the psp folder walks down to savedata`() {
        assertEquals(saveData.absolutePath, resolve(File(memStick, "PSP").absolutePath))
    }

    @Test
    fun `pointing at the memory stick walks down to savedata`() {
        assertEquals(saveData.absolutePath, resolve(memStick.absolutePath))
    }

    @Test
    fun `pointing inside a save folder trims back to savedata`() {
        assertEquals(saveData.absolutePath, resolve(File(saveData, "ULUS10064DATA00").absolutePath))
    }

    @Test
    fun `a path with no savedata anywhere is left alone`() {
        val unrelated = File(tempDir, "somewhere/else").apply { mkdirs() }
        assertEquals(unrelated.absolutePath, resolve(unrelated.absolutePath))
    }
}
