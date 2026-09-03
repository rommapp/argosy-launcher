package com.nendo.argosy.data.emulator

import android.os.Environment
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * The built-in core's entries name their base with `{builtinSaves}` so one resolved directory
 * feeds the launcher, discovery and the download target alike. These pin down how the token
 * expands and what a caller with no resolved base gets.
 */
class SavePathRegistryBuiltinTest {

    @Before
    fun setUp() {
        mockkStatic(Environment::class)
        every { Environment.getExternalStorageDirectory() } returns File("/storage/emulated/0")
    }

    @After
    fun tearDown() {
        unmockkStatic(Environment::class)
    }

    private fun config(id: String) = SavePathRegistry.getConfigForPlatform("builtin", id)
        ?: error("no builtin config for $id")

    @Test
    fun `a resolved base replaces the token and keeps the platform tree below it`() {
        val paths = SavePathRegistry.resolvePath(
            config("psp"), "psp", filesDir = "/data/app/files", builtinSavesDir = "/sd/argosy-saves"
        )

        assertEquals(listOf("/sd/argosy-saves/PSP/SAVEDATA"), paths)
    }

    @Test
    fun `without a resolved base the token falls back to the packaged default under filesDir`() {
        val paths = SavePathRegistry.resolvePath(config("psp"), "psp", filesDir = "/data/app/files")

        assertEquals(listOf("/data/app/files/libretro/saves/PSP/SAVEDATA"), paths)
    }

    @Test
    fun `a flat platform resolves to the base itself`() {
        val paths = SavePathRegistry.resolvePathWithPackage(
            config("gba"), null, builtinSavesDir = "/sd/argosy-saves"
        )

        assertEquals(listOf("/sd/argosy-saves"), paths)
    }

    @Test
    fun `every builtin entry is expressed through the token`() {
        val builtinConfigs = listOf("gba", "psp", "3ds", "gc", "nds", "dreamcast", "dsi").map { config(it) }

        builtinConfigs.forEach { config ->
            config.defaultPaths.forEach { path ->
                assertEquals("${config.emulatorId} path should start with the token: $path", true, path.startsWith(BUILTIN_SAVES_TOKEN))
            }
        }
    }

    @Test
    fun `ppsspp lists the shared memory stick before its private folder`() {
        val config = SavePathRegistry.getConfigForPlatform("ppsspp", "psp") ?: error("no ppsspp config")
        val paths = SavePathRegistry.resolvePathWithPackage(
            config, "org.ppsspp.ppsspp", externalStorageRoots = listOf("/storage/emulated/0", "/storage/1234-5678")
        )

        assertEquals(
            listOf(
                "/storage/emulated/0/PSP/SAVEDATA",
                "/storage/1234-5678/PSP/SAVEDATA",
                "/storage/emulated/0/Android/data/org.ppsspp.ppsspp/files/PSP/SAVEDATA"
            ),
            paths
        )
    }
}
