package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.emulator.config.CemuConfigSpec
import com.nendo.argosy.data.emulator.config.ConfigWriteFailure
import com.nendo.argosy.data.emulator.config.ConfigWriteResult
import com.nendo.argosy.data.emulator.config.EmulatorConfigWriter
import com.nendo.argosy.data.emulator.config.EmulatorDataAccess
import com.nendo.argosy.data.emulator.config.EmulatorDataCapabilities
import com.nendo.argosy.data.emulator.config.EmulatorDataRoute
import com.nendo.argosy.data.sync.fixtures.realFsFal
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CemuDriverInstallerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val meta = """{"schemaVersion":1,"name":"Turnip","minApi":28,"libraryName":"vulkan.turnip.so"}"""

    private fun driver(metaJson: String = meta): InstalledVulkanDriver {
        val dir = File(temp.root, "vulkan_drivers/turnip").apply { mkdirs() }
        File(dir, "meta.json").writeText(metaJson)
        File(dir, "vulkan.turnip.so").writeText("lib")
        return InstalledVulkanDriver("turnip", "Turnip", null, null, dir, "vulkan.turnip.so")
    }

    @Test
    fun `cemu accepts only schema 1 packages that name a minimum api and a plain library`() {
        assertTrue(CemuDriverPackages.isCompatible(meta))
        assertFalse(CemuDriverPackages.isCompatible(meta.replace("\"schemaVersion\":1", "\"schemaVersion\":2")))
        assertFalse(CemuDriverPackages.isCompatible(meta.replace("\"minApi\":28,", "")))
        assertFalse(CemuDriverPackages.isCompatible(meta.replace("vulkan.turnip.so", "../x.so")))
        assertFalse(CemuDriverPackages.isCompatible("not json"))
    }

    @Test
    fun `the folder id is stable for one driver package`() {
        assertEquals(CemuDriverPackages.folderId("turnip", meta), CemuDriverPackages.folderId("turnip", meta))
        assertFalse(CemuDriverPackages.folderId("turnip", meta) == CemuDriverPackages.folderId("turnip", "$meta "))
    }

    @Test
    fun `selecting a driver copies it into customDrivers and points the key at it`() = runBlocking {
        val dataRoot = File(temp.root, "Android/data/info.cemu.cemu/files").apply { mkdirs() }
        val access = mockk<EmulatorDataAccess> {
            coEvery { capabilities(any(), any()) } returns EmulatorDataCapabilities(
                dataRoot = dataRoot.absolutePath,
                readRoute = EmulatorDataRoute.FILE_ACCESS,
                writeAppDataRoute = EmulatorDataRoute.FILE_ACCESS,
                editRoute = null
            )
        }
        val writer = mockk<EmulatorConfigWriter> {
            coEvery { holdsGame(any()) } returns false
            coEvery { write(any(), any(), any(), any()) } returns ConfigWriteResult.Written
        }
        val installer = CemuDriverInstaller(mockk(relaxed = true), access, writer, realFsFal())
        val folder = CemuDriverPackages.folderPath(dataRoot.absolutePath, CemuDriverPackages.folderId("turnip", meta))

        val result = installer.select("cemu", "info.cemu.cemu", driver())

        assertEquals(ConfigWriteResult.Written, result)
        assertEquals(meta, File(folder, "meta.json").readText())
        assertTrue(File(folder, "vulkan.turnip.so").isFile)
        coVerify { writer.write("cemu", "info.cemu.cemu", CemuConfigSpec.CUSTOM_DRIVER_PATH, folder) }
    }

    @Test
    fun `an incompatible package or no write access is refused before anything is copied`() = runBlocking {
        val dataRoot = File(temp.root, "Android/data/info.cemu.cemu/files").apply { mkdirs() }
        fun access(writable: Boolean) = mockk<EmulatorDataAccess> {
            coEvery { capabilities(any(), any()) } returns EmulatorDataCapabilities(
                dataRoot = dataRoot.absolutePath,
                readRoute = EmulatorDataRoute.FILE_ACCESS,
                writeAppDataRoute = EmulatorDataRoute.FILE_ACCESS.takeIf { writable },
                editRoute = null
            )
        }
        val writer = mockk<EmulatorConfigWriter> {
            coEvery { holdsGame(any()) } returns false
        }

        val incompatible = CemuDriverInstaller(mockk(relaxed = true), access(true), writer, realFsFal())
            .select("cemu", "info.cemu.cemu", driver(meta.replace("\"minApi\":28,", "")))
        val blocked = CemuDriverInstaller(mockk(relaxed = true), access(false), writer, realFsFal())
            .select("cemu", "info.cemu.cemu", driver())

        assertEquals(ConfigWriteResult.Failed(ConfigWriteFailure.INVALID_VALUE), incompatible)
        assertEquals(ConfigWriteResult.Failed(ConfigWriteFailure.NO_EDIT_ROUTE), blocked)
        assertFalse(File(dataRoot, CemuConfigSpec.DRIVERS_DIR).exists())
    }

    @Test
    fun `a game running in Cemu refuses the driver before anything is copied`() = runBlocking {
        val dataRoot = File(temp.root, "Android/data/info.cemu.cemu/files").apply { mkdirs() }
        val access = mockk<EmulatorDataAccess> {
            coEvery { capabilities(any(), any()) } returns EmulatorDataCapabilities(
                dataRoot = dataRoot.absolutePath,
                readRoute = EmulatorDataRoute.FILE_ACCESS,
                writeAppDataRoute = EmulatorDataRoute.FILE_ACCESS,
                editRoute = null
            )
        }
        val writer = mockk<EmulatorConfigWriter> {
            coEvery { holdsGame("info.cemu.cemu") } returns true
        }

        val result = CemuDriverInstaller(mockk(relaxed = true), access, writer, realFsFal())
            .select("cemu", "info.cemu.cemu", driver())

        assertEquals(ConfigWriteResult.Failed(ConfigWriteFailure.GAME_RUNNING), result)
        assertFalse(File(dataRoot, CemuConfigSpec.DRIVERS_DIR).exists())
        coVerify(exactly = 0) { writer.write(any(), any(), any(), any()) }
    }
}
