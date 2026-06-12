package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.emulator.RetroArchPathResolver
import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.storage.FileAccessLayer
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RetroArchSaveHandlerTest {

    private lateinit var context: Context
    private lateinit var fal: FileAccessLayer
    private lateinit var resolver: RetroArchPathResolver
    private lateinit var handler: RetroArchSaveHandler

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        fal = mockk(relaxed = true)
        resolver = mockk(relaxed = true)
        handler = RetroArchSaveHandler(context, fal, resolver)
    }

    private fun saveContext(
        emulatorId: String = "retroarch",
        platformSlug: String = "snes",
        romPath: String? = "/storage/emulated/0/Roms/SNES/Mario.smc",
        gameTitle: String = "Super Mario World",
        saveExtensions: List<String> = listOf("srm"),
    ): SaveContext = SaveContext(
        config = mockk(relaxed = true) { every { this@mockk.saveExtensions } returns saveExtensions },
        romPath = romPath,
        saveId = null,
        emulatorPackage = null,
        gameId = 1L,
        gameTitle = gameTitle,
        platformSlug = platformSlug,
        emulatorId = emulatorId,
        localSavePath = null,
    )

    @Test
    fun `constructSavePath omits core subdir when sort_savefiles_enable is false`() = runTest {
        coEvery { resolver.buildSaveFilePath(any(), any(), any()) } answers {
            val baseName = secondArg<String>()
            val ext = thirdArg<String>()
            "/storage/emulated/0/RetroArch/saves/$baseName.$ext"
        }

        val ctx = saveContext()
        val path = handler.constructSavePath(ctx)

        assertNotNull(path)
        assertEquals(
            "When sort=false, save path must NOT contain the core subdirectory",
            "/storage/emulated/0/RetroArch/saves/Mario.srm",
            path,
        )
    }

    @Test
    fun `constructSavePath delegates to resolver with raw libretro core id`() = runTest {
        var capturedCore: String? = null
        coEvery { resolver.buildSaveFilePath(any(), any(), any()) } answers {
            capturedCore = firstArg<RetroArchPathResolver.Request>().coreName
            "/some/path/file.srm"
        }

        val ctx = saveContext(platformSlug = "snes")
        handler.constructSavePath(ctx)

        assertEquals(
            "Handler must pass raw libretro core id; resolver applies getRetroArchSaveDirName once",
            "snes9x",
            capturedCore,
        )
    }

    @Test
    fun `constructSavePath honors PSX mcd extension`() = runTest {
        var capturedExt: String? = null
        coEvery { resolver.buildSaveFilePath(any(), any(), any()) } answers {
            capturedExt = thirdArg<String>()
            "/some/path/file.${thirdArg<String>()}"
        }

        val ctx = saveContext(
            platformSlug = "psx",
            romPath = "/storage/emulated/0/Roms/PSX/FFVII.cue",
            saveExtensions = listOf("mcd"),
        )
        handler.constructSavePath(ctx)

        assertEquals(
            "PSX should pass mcd extension to resolver",
            "mcd",
            capturedExt,
        )
    }
}
