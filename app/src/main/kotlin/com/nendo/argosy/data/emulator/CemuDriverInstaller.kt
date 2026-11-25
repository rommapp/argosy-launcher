package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.emulator.config.CemuConfigSpec
import com.nendo.argosy.data.emulator.config.ConfigWriteFailure
import com.nendo.argosy.data.emulator.config.ConfigWriteResult
import com.nendo.argosy.data.emulator.config.EmulatorConfigWriter
import com.nendo.argosy.data.emulator.config.EmulatorDataAccess
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

internal object CemuDriverPackages {
    private const val SCHEMA_VERSION = 1
    private const val NO_VALUE = -1

    fun isCompatible(metaJson: String): Boolean {
        val meta = runCatching { JSONObject(metaJson) }.getOrNull() ?: return false
        val library = meta.optString("libraryName").trim()
        return meta.optInt("schemaVersion", NO_VALUE) == SCHEMA_VERSION &&
            meta.optInt("minApi", NO_VALUE) > 0 &&
            library.isNotEmpty() && '/' !in library && '\\' !in library && library != "." && library != ".."
    }

    fun folderId(driverId: String, metaJson: String): String =
        UUID.nameUUIDFromBytes("$driverId\n$metaJson".toByteArray(Charsets.UTF_8)).toString()

    fun folderPath(dataRoot: String, folderId: String): String =
        "${dataRoot.trimEnd('/')}/${CemuConfigSpec.DRIVERS_DIR}/$folderId"
}

data class CemuDriverChoice(val driver: InstalledVulkanDriver, val path: String)

@Singleton
class CemuDriverInstaller @Inject constructor(
    private val drivers: VulkanDriverRepository,
    private val access: EmulatorDataAccess,
    private val writer: EmulatorConfigWriter,
    private val fal: FileAccessLayer
) {
    /**
     * The Vulkan drivers Argosy holds whose package Cemu accepts; empty on a device Cemu offers no
     * custom driver for.
     */
    suspend fun compatibleDrivers(): List<InstalledVulkanDriver> = withContext(Dispatchers.IO) {
        if (!drivers.customDriversSupported()) return@withContext emptyList()
        drivers.listInstalled().filter { driver -> metaOf(driver)?.let(CemuDriverPackages::isCompatible) == true }
    }

    /**
     * Each compatible driver with the `custom_driver_path` value [select] writes for it under
     * [dataRoot].
     */
    suspend fun choices(dataRoot: String): List<CemuDriverChoice> = withContext(Dispatchers.IO) {
        compatibleDrivers().mapNotNull { driver ->
            val meta = metaOf(driver) ?: return@mapNotNull null
            CemuDriverChoice(driver, CemuDriverPackages.folderPath(dataRoot, CemuDriverPackages.folderId(driver.id, meta)))
        }
    }

    /**
     * Places [driver] in Cemu's `customDrivers` folder and points `custom_driver_path` at it; a
     * null [driver] clears the key so Cemu runs the system driver.
     */
    suspend fun select(emulatorId: String, packageName: String, driver: InstalledVulkanDriver?): ConfigWriteResult =
        withContext(Dispatchers.IO) {
            val option = CemuConfigSpec.CUSTOM_DRIVER_PATH
            if (driver == null) return@withContext writer.write(emulatorId, packageName, option, "")
            if (writer.holdsGame(packageName)) {
                return@withContext ConfigWriteResult.Failed(ConfigWriteFailure.GAME_RUNNING)
            }
            val capabilities = access.capabilities(emulatorId, packageName)
            val dataRoot = capabilities.dataRoot
                ?: return@withContext ConfigWriteResult.Failed(ConfigWriteFailure.FILE_MISSING)
            if (!capabilities.canWriteAppData) {
                return@withContext ConfigWriteResult.Failed(ConfigWriteFailure.NO_EDIT_ROUTE)
            }
            val meta = metaOf(driver)?.takeIf(CemuDriverPackages::isCompatible)
                ?: return@withContext ConfigWriteResult.Failed(ConfigWriteFailure.INVALID_VALUE)
            val target = CemuDriverPackages.folderPath(dataRoot, CemuDriverPackages.folderId(driver.id, meta))
            if (!place(driver.directory, target)) {
                Logger.warn(TAG, "driver copy failed | driver=${driver.id} target=$target")
                return@withContext ConfigWriteResult.Failed(ConfigWriteFailure.WRITE_FAILED)
            }
            writer.write(emulatorId, packageName, option, target)
        }

    private fun metaOf(driver: InstalledVulkanDriver): String? =
        runCatching { File(driver.directory, VulkanDriverPackages.META_FILE).readText() }.getOrNull()

    private fun place(source: File, target: String): Boolean {
        val files = source.listFiles()?.filter { it.isFile }?.takeIf { it.isNotEmpty() } ?: return false
        if (!fal.mkdirs(target) && !fal.isDirectory(target)) return false
        return files.all { fal.copyFile(it.absolutePath, "$target/${it.name}") } && fal.commitSaveAccess(target)
    }

    private companion object {
        const val TAG = "CemuDriverInstaller"
    }
}
