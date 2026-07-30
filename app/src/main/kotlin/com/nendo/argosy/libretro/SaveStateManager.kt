package com.nendo.argosy.libretro

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
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
    private val usesExternalMemcard: Boolean = false,
    private val channelName: String? = null,
    private val isVariant: Boolean = false
) {
    private var lastSramHash: String? = null
    var hasQuickSave by mutableStateOf(false)
        private set

    private val romBaseName: String = File(romPath).nameWithoutExtension

    data class RestoreResult(
        val sramData: ByteArray?,
        val switchToHardcore: Boolean = false,
        val casualSaveInHardcore: Boolean = false
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
        migrateChannelStatesToFlat()
        statesDir.mkdirs()
        hasQuickSave = quickRingEntries().isNotEmpty()
    }

    fun getSlotFile(slotNumber: Int): File {
        val fileName = buildSlotFileName(slotNumber)
        return File(statesDir, fileName)
    }

    fun getSlotScreenshotFile(slotNumber: Int): File {
        return File(statesDir, "${buildSlotFileName(slotNumber)}.png")
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

    /**
     * Resolves the SRAM to load for [launchMode], writes it to the live .srm, and returns the bytes
     * plus mode hints. Two non-obvious rules: an explicit save-management restore (activeSaveApplied)
     * wins over the default resume pick; and RESUME_HARDCORE falls back to the active save when no
     * hardcore save exists, because RetroAchievements forbids only save states in hardcore, not SRAM
     * battery saves (that fallback is flagged casualSaveInHardcore so the UI can surface it).
     */
    suspend fun restoreSaveForLaunchMode(launchMode: LaunchMode): RestoreResult {
        if (usesExternalMemcard) {
            Log.d(TAG, "External memcard (GCI folder) - skipping flat .srm restore")
            return RestoreResult(null)
        }
        if (isVariant) {
            return when (launchMode) {
                LaunchMode.NEW_HARDCORE, LaunchMode.NEW_CASUAL -> {
                    getSramFile().delete()
                    deleteAllStates()
                    RestoreResult(null)
                }
                else -> RestoreResult(getSramFile().takeIf { it.exists() }?.readBytes())
            }
        }
        if (gameId < 0) {
            Log.w(TAG, "No valid gameId, using existing save")
            val bytes = getSramFile().takeIf { it.exists() }?.readBytes()
            return RestoreResult(bytes)
        }

        val game = if (launchMode == LaunchMode.RESUME || launchMode == LaunchMode.RESUME_HARDCORE) {
            gameDao.getById(gameId)
        } else {
            null
        }

        if (game?.activeSaveApplied == true) {
            val sramFile = getSramFile()
            if (sramFile.exists()) {
                val bytes = sramFile.readBytes()
                Log.i(TAG, "Honoring explicit restore (activeSaveApplied): on-disk .srm ${bytes.size} bytes")
                return RestoreResult(bytes)
            }
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
                deleteAllStates()
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
                    Log.d(TAG, "No hardcore save; using active save for hardcore session")
                    val fallback = restoreResumeSave(game)
                    if (fallback.sramData != null && !fallback.switchToHardcore) {
                        fallback.copy(casualSaveInHardcore = true)
                    } else {
                        fallback
                    }
                }
            }
            LaunchMode.RESUME -> restoreResumeSave(game)
        }
    }

    private suspend fun restoreResumeSave(game: GameEntity?): RestoreResult {
        val activeSaveTimestamp = game?.activeSaveTimestamp
        val namedChannel = com.nendo.argosy.data.repository.SaveSyncApiClient
            .namedChannelOrNull(game?.activeSaveChannel)

        val targetSave = when {
            activeSaveTimestamp != null -> {
                Log.d(TAG, "RESUME: Looking for activated save at timestamp $activeSaveTimestamp")
                saveCacheManager.getByTimestamp(gameId, activeSaveTimestamp)
            }
            namedChannel != null -> {
                Log.d(TAG, "RESUME: Looking for most recent save in channel '$namedChannel'")
                saveCacheManager.getMostRecentInChannel(gameId, namedChannel)
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

    @Synchronized
    fun saveSram(retroView: GLRetroView) {
        if (usesExternalMemcard) return
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

    private fun quickRingEntries(): List<Pair<Int, File>> {
        return (0 until QUICK_RING_SIZE).mapNotNull { i ->
            val file = getSlotFile(QUICK_SLOT_BASE + i)
            if (file.exists()) (QUICK_SLOT_BASE + i) to file else null
        }
    }

    fun getQuickRingInfoList(): List<SlotInfo> {
        return quickRingEntries()
            .sortedByDescending { it.second.lastModified() }
            .map { (slotNumber, file) ->
                val screenshotFile = getSlotScreenshotFile(slotNumber)
                SlotInfo(
                    slotNumber = slotNumber,
                    file = file,
                    timestamp = file.lastModified(),
                    size = file.length(),
                    screenshotFile = screenshotFile.takeIf { it.exists() }
                )
            }
    }

    fun performQuickSave(stateData: ByteArray, screenshot: Bitmap? = null): Boolean {
        val emptyIndex = (0 until QUICK_RING_SIZE).firstOrNull {
            !getSlotFile(QUICK_SLOT_BASE + it).exists()
        }
        val targetIndex = emptyIndex
            ?: (0 until QUICK_RING_SIZE).minByOrNull { getSlotFile(QUICK_SLOT_BASE + it).lastModified() }
            ?: 0
        return performSlotSave(QUICK_SLOT_BASE + targetIndex, stateData, screenshot)
    }

    fun performQuickLoad(retroView: GLRetroView): Boolean {
        val newest = quickRingEntries().maxByOrNull { it.second.lastModified() }?.first ?: return false
        return performSlotLoad(retroView, newest)
    }

    fun performSlotSave(slotNumber: Int, stateData: ByteArray, screenshot: Bitmap? = null): Boolean {
        return try {
            statesDir.mkdirs()
            val stateFile = getSlotFile(slotNumber)
            stateFile.writeBytes(stateData)

            writeScreenshot(slotNumber, screenshot)

            if (slotNumber in QUICK_SLOT_BASE until QUICK_SLOT_BASE + QUICK_RING_SIZE) {
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
                if (!retroView.unserializePersistedState(stateData)) {
                    Log.e(TAG, "Core rejected state from slot $slotNumber (${stateData.size} bytes)")
                    return false
                }
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
            if (slotNumber in QUICK_SLOT_BASE until QUICK_SLOT_BASE + QUICK_RING_SIZE) {
                hasQuickSave = quickRingEntries().isNotEmpty()
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

    private fun deleteAllStates() {
        for (slot in LibretroStateSlots.ALL_SLOTS) {
            val stateFile = getSlotFile(slot)
            val screenshotFile = getSlotScreenshotFile(slot)
            if (stateFile.exists()) {
                stateFile.delete()
                Log.d(TAG, "Deleted state slot $slot for fresh start")
            }
            if (screenshotFile.exists()) {
                screenshotFile.delete()
                Log.d(TAG, "Deleted screenshot for slot $slot")
            }
        }
        hasQuickSave = false
    }

    private fun buildSlotFileName(slotNumber: Int): String =
        LibretroStateSlots.fileName(romBaseName, slotNumber)

    /**
     * One-shot migration: live states are now flat under statesDir (mirroring SRAM and external
     * emulators), with channels living only in the cache. Lifts this game's active-channel states out
     * of the legacy {statesDir}/{channel}/ subdir up to the flat statesDir; named channels not
     * migrated here are re-materialized from the cache on channel switch.
     */
    private fun migrateChannelStatesToFlat() {
        val legacyChannelDir = File(statesDir, channelName ?: "default")
        if (legacyChannelDir == statesDir || !legacyChannelDir.isDirectory) return
        val files = legacyChannelDir.listFiles { file ->
            file.isFile && file.name.startsWith("$romBaseName.state")
        } ?: return
        for (file in files) {
            val target = File(statesDir, file.name)
            if (!target.exists() && file.renameTo(target)) {
                Log.d(TAG, "Migrated ${file.name} from ${legacyChannelDir.name}/ to flat")
            }
        }
    }

    private fun hashBytes(data: ByteArray): String {
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "SaveStateManager"
        const val AUTO_SLOT = LibretroStateSlots.AUTO_SLOT
        const val RESUME_SLOT = LibretroStateSlots.RESUME_SLOT
        const val MAX_SLOT = LibretroStateSlots.MAX_SLOT
        const val QUICK_SLOT_BASE = LibretroStateSlots.QUICK_SLOT_BASE
        const val QUICK_RING_SIZE = LibretroStateSlots.QUICK_RING_SIZE
        private const val SCREENSHOT_MAX_WIDTH = 480
    }
}
