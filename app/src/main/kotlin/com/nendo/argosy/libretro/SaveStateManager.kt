package com.nendo.argosy.libretro

import android.graphics.Bitmap
import android.util.Log
import com.nendo.argosy.data.emulator.M3uManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.repository.ActiveSaveRepository
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
    private val activeSaveRepository: ActiveSaveRepository,
    private val saveCacheManager: SaveCacheManager,
    private val usesExternalMemcard: Boolean = false,
    private val channelName: String? = null,
    private val isVariant: Boolean = false,
    private val onLiveStateWritten: ((Int, File) -> Unit)? = null,
    private val onLiveStateRemoved: ((Int, File) -> Unit)? = null
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
        val f = getSramFile()
        Log.i(
            TAG,
            "[SRAM] init | gameId=$gameId rom=$romBaseName restoredBytes=${existingSram?.size ?: -1} " +
                "seededHash=$lastSramHash savesDir=${savesDir.absolutePath} " +
                "file=${f.absolutePath} exists=${f.exists()} size=${if (f.exists()) f.length() else -1L}"
        )
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

    /**
     * Carries a battery save forward when the file it lives under stops being the one we look for.
     *
     * A folder-based multi-disc game used to launch as a version variant of its first disc, so its
     * save sat in `saves/variants/<fileId>` under that disc's filename. Once the discs are
     * registered the game launches from an m3u instead, and both halves of the name change at
     * once, leaving a real save under a path nothing reads. The upgrade is invisible to the user:
     * the card simply comes up blank.
     *
     * Only ever copies, and only when nothing exists at the destination, so an existing save is
     * never overwritten and the original stays where it was.
     */
    fun adoptLegacySaveIfMissing() {
        val target = getSramFile()
        if (target.exists()) return

        val source = legacySaveCandidates().firstOrNull { it.isFile && it.length() > 0 }
        if (source == null) {
            Log.d(TAG, "[SRAM] adopt | nothing at ${target.name} and no predecessor found")
            return
        }
        try {
            target.parentFile?.mkdirs()
            source.copyTo(target, overwrite = false)
            Log.w(
                TAG,
                "[SRAM] adopted legacy save | ${source.absolutePath} -> ${target.absolutePath} " +
                    "(${source.length()} bytes). Original left in place."
            )
        } catch (e: Exception) {
            Log.e(TAG, "[SRAM] adopt FAILED | ${source.absolutePath} -> ${target.absolutePath}", e)
        }
    }

    private fun legacySaveCandidates(): List<File> {
        val names = buildList {
            add(romBaseName)
            M3uManager.parseAllDiscs(File(romPath)).forEach { add(it.nameWithoutExtension) }
        }.distinct()

        val variantDirs = File(savesDir, "variants").listFiles()?.filter { it.isDirectory }.orEmpty()
        return buildList {
            names.forEach { name -> add(File(savesDir, "$name.srm")) }
            variantDirs.forEach { dir -> names.forEach { name -> add(File(dir, "$name.srm")) } }
        }.filter { it.absolutePath != getSramFile().absolutePath }
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

        val activeSave = if (launchMode == LaunchMode.RESUME || launchMode == LaunchMode.RESUME_HARDCORE) {
            activeSaveRepository.getActiveRow(gameId)
        } else {
            null
        }

        if (activeSave?.activeSaveApplied == true) {
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
                    val fallback = restoreResumeSave(activeSave)
                    if (fallback.sramData != null && !fallback.switchToHardcore) {
                        fallback.copy(casualSaveInHardcore = true)
                    } else {
                        fallback
                    }
                }
            }
            LaunchMode.RESUME -> restoreResumeSave(activeSave)
        }
    }

    private suspend fun restoreResumeSave(activeSave: SaveCacheEntity?): RestoreResult {
        val targetSave = if (activeSave != null) {
            Log.d(TAG, "RESUME: Using active save cacheId=${activeSave.id} channel='${activeSave.channelName}'")
            activeSave
        } else {
            Log.d(TAG, "RESUME: No active save, looking for most recent save overall")
            saveCacheManager.getMostRecentSave(gameId)
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
            val f = getSramFile()
            val bytes = f.takeIf { it.exists() }?.readBytes()
            Log.i(
                TAG,
                "[SRAM] restore fallback | no cached saves, using on-disk .srm | " +
                    "file=${f.absolutePath} exists=${f.exists()} bytes=${bytes?.size ?: -1} " +
                    "nonZero=${bytes?.count { it != 0.toByte() } ?: -1}"
            )
            return RestoreResult(bytes)
        }
    }

    @Synchronized
    fun saveSram(retroView: GLRetroView) {
        val target = getSramFile()
        val beforeSize = if (target.exists()) target.length() else -1L
        Log.i(
            TAG,
            "[SRAM] saveSram ENTER | gameId=$gameId rom=$romBaseName external=$usesExternalMemcard " +
                "variant=$isVariant channel=$channelName file=${target.absolutePath} " +
                "exists=${target.exists()} sizeBefore=$beforeSize lastHash=$lastSramHash"
        )
        if (usesExternalMemcard) {
            Log.w(TAG, "[SRAM] saveSram SKIP | external memcard platform, nothing written")
            return
        }
        try {
            val sramData = retroView.serializeSRAM()
            Log.i(TAG, "[SRAM] serializeSRAM returned ${sramData.size} bytes")
            if (sramData.isEmpty()) {
                Log.w(
                    TAG,
                    "[SRAM] saveSram SKIP | core returned 0 bytes of save RAM. If this core owns " +
                        "its own memcard file the frontend copy will never be written"
                )
                return
            }

            val nonZero = sramData.count { it != 0.toByte() }
            val currentHash = hashBytes(sramData)
            Log.i(TAG, "[SRAM] content | hash=$currentHash nonZeroBytes=$nonZero/${sramData.size}")
            if (currentHash == lastSramHash) {
                Log.w(TAG, "[SRAM] saveSram SKIP | hash unchanged since last write, file left alone")
                return
            }

            target.parentFile?.let { dir ->
                if (!dir.exists()) {
                    Log.w(TAG, "[SRAM] saves dir missing, creating ${dir.absolutePath} -> ${dir.mkdirs()}")
                }
            }
            target.writeBytes(sramData)
            lastSramHash = currentHash
            Log.i(
                TAG,
                "[SRAM] saveSram WROTE | ${sramData.size} bytes -> ${target.absolutePath} " +
                    "sizeAfter=${target.length()} hash=$currentHash"
            )
        } catch (e: Exception) {
            Log.e(TAG, "[SRAM] saveSram FAILED | ${target.absolutePath}", e)
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
            onLiveStateWritten?.invoke(slotNumber, stateFile)
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
            if (deleted) onLiveStateRemoved?.invoke(slotNumber, stateFile)
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
                onLiveStateRemoved?.invoke(slot, stateFile)
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
