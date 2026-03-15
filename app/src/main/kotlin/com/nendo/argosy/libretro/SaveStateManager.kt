package com.nendo.argosy.libretro

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.repository.SaveCacheManager
import com.swordfish.libretrodroid.GLRetroView
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class SaveStateManager(
    private val savesDir: File,
    private val statesDir: File,
    private val romPath: String,
    private val gameId: Long,
    private val gameDao: GameDao,
    private val saveCacheManager: SaveCacheManager,
    channelName: String? = null
) {
    private var lastSramHash: String? = null
    var hasQuickSave by mutableStateOf(false)
        private set

    private val channelDir: File = resolveChannelDir(channelName)
    private val romBaseName: String = File(romPath).nameWithoutExtension

    data class RestoreResult(
        val sramData: ByteArray?,
        val switchToHardcore: Boolean = false
    )

    data class SlotInfo(
        val slotNumber: Int,
        val file: File?,
        val timestamp: Long?,
        val size: Long,
        val screenshotFile: File?
    )

    fun initializeFromExistingSave(existingSram: ByteArray?) {
        lastSramHash = existingSram?.let { hashBytes(it) }
        migrateExistingFlatFiles()
        channelDir.mkdirs()
        hasQuickSave = getSlotFile(AUTO_SLOT).exists()
    }

    fun getSlotFile(slotNumber: Int): File {
        val fileName = buildSlotFileName(slotNumber)
        return File(channelDir, fileName)
    }

    fun getSlotScreenshotFile(slotNumber: Int): File {
        return File(channelDir, "${buildSlotFileName(slotNumber)}.png")
    }

    fun getSramFile(): File {
        return File(savesDir, "$romBaseName.srm")
    }

    fun getSlotInfoList(): List<SlotInfo> {
        val slots = mutableListOf<SlotInfo>()
        for (slot in AUTO_SLOT..MAX_SLOT) {
            val file = getSlotFile(slot)
            val screenshotFile = getSlotScreenshotFile(slot)
            if (file.exists()) {
                slots.add(
                    SlotInfo(
                        slotNumber = slot,
                        file = file,
                        timestamp = file.lastModified(),
                        size = file.length(),
                        screenshotFile = screenshotFile.takeIf { it.exists() }
                    )
                )
            } else {
                slots.add(
                    SlotInfo(
                        slotNumber = slot,
                        file = null,
                        timestamp = null,
                        size = 0,
                        screenshotFile = null
                    )
                )
            }
        }
        return slots
    }

    suspend fun restoreSaveForLaunchMode(launchMode: LaunchMode): RestoreResult {
        if (gameId < 0) {
            Log.w(TAG, "No valid gameId, using existing save")
            val bytes = getSramFile().takeIf { it.exists() }?.readBytes()
            return RestoreResult(bytes)
        }

        return when (launchMode) {
            LaunchMode.NEW_HARDCORE, LaunchMode.NEW_CASUAL -> {
                Log.d(TAG, "New game mode - starting fresh (no save)")
                val sramFile = getSramFile()
                if (sramFile.exists()) {
                    val result = saveCacheManager.cacheAsRollback(
                        gameId,
                        EmulatorRegistry.BUILTIN_PACKAGE,
                        sramFile.absolutePath
                    )
                    when (result) {
                        is SaveCacheManager.CacheResult.Created ->
                            Log.d(TAG, "Created rollback backup before fresh start")
                        is SaveCacheManager.CacheResult.Duplicate ->
                            Log.d(TAG, "Rollback skipped - identical save already cached")
                        is SaveCacheManager.CacheResult.Failed ->
                            Log.w(TAG, "Failed to create rollback backup")
                    }
                    sramFile.delete()
                    Log.d(TAG, "Deleted existing save file for fresh start")
                }
                deleteAutoState()
                RestoreResult(null)
            }
            LaunchMode.RESUME_HARDCORE -> {
                Log.d(TAG, "Resuming hardcore - restoring hardcore save")
                val hardcoreSave = saveCacheManager.getLatestHardcoreSave(gameId)
                if (hardcoreSave != null) {
                    val isValid = saveCacheManager.isValidHardcoreSave(hardcoreSave)
                    if (!isValid) {
                        Log.w(TAG, "Hardcore save missing trailer - save may have been modified externally")
                    }
                    val bytes = saveCacheManager.getSaveBytesFromEntity(hardcoreSave)
                    if (bytes != null) {
                        getSramFile().writeBytes(bytes)
                        Log.d(TAG, "Restored hardcore save (${bytes.size} bytes, valid=$isValid)")
                    }
                    RestoreResult(bytes)
                } else {
                    Log.w(TAG, "No hardcore save found, starting fresh")
                    RestoreResult(null)
                }
            }
            LaunchMode.RESUME -> restoreResumeSave()
        }
    }

    private suspend fun restoreResumeSave(): RestoreResult {
        val game = gameDao.getById(gameId)
        val activeSaveTimestamp = game?.activeSaveTimestamp
        val activeChannel = game?.activeSaveChannel

        val targetSave = when {
            activeSaveTimestamp != null -> {
                Log.d(TAG, "RESUME: Looking for activated save at timestamp $activeSaveTimestamp")
                saveCacheManager.getByTimestamp(gameId, activeSaveTimestamp)
            }
            activeChannel != null -> {
                Log.d(TAG, "RESUME: Looking for most recent save in channel '$activeChannel'")
                saveCacheManager.getMostRecentInChannel(gameId, activeChannel)
            }
            else -> {
                Log.d(TAG, "RESUME: Looking for most recent save overall")
                saveCacheManager.getMostRecentSave(gameId)
            }
        }

        if (targetSave != null) {
            var switchToHardcore = false
            if (targetSave.isHardcore) {
                val isValid = saveCacheManager.isValidHardcoreSave(targetSave)
                if (isValid) {
                    switchToHardcore = true
                    Log.d(TAG, "RESUME: Loading hardcore save, switching to hardcore mode")
                } else {
                    Log.w(TAG, "RESUME: Hardcore save missing trailer, loading as casual")
                }
            }
            val bytes = saveCacheManager.getSaveBytesFromEntity(targetSave)
            if (bytes != null) {
                getSramFile().writeBytes(bytes)
                Log.d(TAG, "RESUME: Restored save (${bytes.size} bytes, hardcore=${targetSave.isHardcore})")
            }
            return RestoreResult(bytes, switchToHardcore)
        } else {
            Log.d(TAG, "RESUME: No cached saves, using existing .srm if present")
            val bytes = getSramFile().takeIf { it.exists() }?.readBytes()
            return RestoreResult(bytes)
        }
    }

    fun saveSram(retroView: GLRetroView) {
        try {
            val sramData = retroView.serializeSRAM()
            if (sramData.isEmpty()) return

            val currentHash = hashBytes(sramData)
            if (currentHash == lastSramHash) return

            getSramFile().writeBytes(sramData)
            lastSramHash = currentHash
        } catch (_: Exception) {
        }
    }

    fun performQuickSave(stateData: ByteArray, screenshot: Bitmap? = null): Boolean {
        return performSlotSave(AUTO_SLOT, stateData, screenshot)
    }

    fun performQuickLoad(retroView: GLRetroView): Boolean {
        return performSlotLoad(retroView, AUTO_SLOT)
    }

    fun performSlotSave(slotNumber: Int, stateData: ByteArray, screenshot: Bitmap? = null): Boolean {
        return try {
            channelDir.mkdirs()
            val stateFile = getSlotFile(slotNumber)
            stateFile.writeBytes(stateData)

            writeScreenshot(slotNumber, screenshot)

            if (slotNumber == AUTO_SLOT) {
                hasQuickSave = true
            }
            Log.d(TAG, "Saved state to slot $slotNumber (${stateData.size} bytes)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save state to slot $slotNumber", e)
            false
        }
    }

    fun performSlotLoad(retroView: GLRetroView, slotNumber: Int): Boolean {
        return try {
            val stateFile = getSlotFile(slotNumber)
            if (stateFile.exists()) {
                val stateData = stateFile.readBytes()
                retroView.unserializeState(stateData)
                Log.d(TAG, "Loaded state from slot $slotNumber (${stateData.size} bytes)")
                true
            } else {
                Log.w(TAG, "No state file for slot $slotNumber")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load state from slot $slotNumber", e)
            false
        }
    }

    fun deleteSlot(slotNumber: Int): Boolean {
        return try {
            val stateFile = getSlotFile(slotNumber)
            val screenshotFile = getSlotScreenshotFile(slotNumber)
            val deleted = stateFile.delete()
            screenshotFile.delete()
            if (slotNumber == AUTO_SLOT) {
                hasQuickSave = false
            }
            Log.d(TAG, "Deleted slot $slotNumber: $deleted")
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete slot $slotNumber", e)
            false
        }
    }

    private fun writeScreenshot(slotNumber: Int, bitmap: Bitmap?) {
        if (bitmap == null) return
        try {
            val screenshotFile = getSlotScreenshotFile(slotNumber)
            val scaled = scaleScreenshot(bitmap)
            FileOutputStream(screenshotFile).use { out ->
                scaled.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            if (scaled !== bitmap) {
                scaled.recycle()
            }
            Log.d(TAG, "Saved screenshot for slot $slotNumber")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save screenshot for slot $slotNumber", e)
        }
    }

    private fun scaleScreenshot(bitmap: Bitmap): Bitmap {
        if (bitmap.width <= SCREENSHOT_MAX_WIDTH) return bitmap
        val ratio = SCREENSHOT_MAX_WIDTH.toFloat() / bitmap.width
        val newHeight = (bitmap.height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, SCREENSHOT_MAX_WIDTH, newHeight, true)
    }

    private fun deleteAutoState() {
        val autoFile = getSlotFile(AUTO_SLOT)
        val autoScreenshot = getSlotScreenshotFile(AUTO_SLOT)
        if (autoFile.exists()) {
            autoFile.delete()
            Log.d(TAG, "Deleted auto-save state for fresh start")
        }
        if (autoScreenshot.exists()) {
            autoScreenshot.delete()
            Log.d(TAG, "Deleted auto-save screenshot for fresh start")
        }
        hasQuickSave = false
    }

    private fun buildSlotFileName(slotNumber: Int): String {
        return when (slotNumber) {
            AUTO_SLOT -> "$romBaseName.state.auto"
            0 -> "$romBaseName.state"
            else -> "$romBaseName.state$slotNumber"
        }
    }

    private fun resolveChannelDir(channelName: String?): File {
        val dirName = channelName ?: "default"
        return File(statesDir, dirName)
    }

    private fun migrateExistingFlatFiles() {
        val flatStateFile = File(statesDir, "$romBaseName.state")
        if (flatStateFile.exists() && flatStateFile.parentFile == statesDir) {
            val defaultDir = File(statesDir, "default")
            defaultDir.mkdirs()

            val filesToMigrate = statesDir.listFiles { file ->
                file.isFile && file.name.startsWith("$romBaseName.state")
            } ?: return

            for (file in filesToMigrate) {
                val target = File(defaultDir, file.name)
                if (!target.exists()) {
                    file.renameTo(target)
                    Log.d(TAG, "Migrated ${file.name} to default/")
                }
            }
        }
    }

    private fun hashBytes(data: ByteArray): String {
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "SaveStateManager"
        const val AUTO_SLOT = -1
        const val MAX_SLOT = 9
        private const val SCREENSHOT_MAX_WIDTH = 480
    }
}
