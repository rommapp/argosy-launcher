package com.nendo.argosy.libretro

import android.graphics.Bitmap
import android.util.Log
import com.nendo.argosy.data.emulator.M3uManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.repository.ActiveSaveDiskJudge
import com.nendo.argosy.data.repository.ActiveSaveRepository
import com.nendo.argosy.data.repository.DiskVerdict
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.sync.platform.SigilRestore
import com.swordfish.libretrodroid.GLRetroView
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
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
    private val diskJudge: ActiveSaveDiskJudge,
    private val secureSaves: Boolean,
    private val ownerUserId: Long?,
    private val usesExternalMemcard: Boolean = false,
    private val channelName: String? = null,
    private val isVariant: Boolean = false,
    primarySavePath: String? = null,
    private val onLiveStateWritten: ((Int, File) -> Unit)? = null,
    private val onLiveStateRemoved: ((Int, File) -> Unit)? = null
) {
    private var lastSramHash: String? = null
    var hasQuickSave by mutableStateOf(false)
        private set

    private val romBaseName: String = File(romPath).nameWithoutExtension
    private val primarySaveFile: File = primarySavePath?.let { File(it) } ?: File(savesDir, "$romBaseName.srm")
    private val coreOwnsPrimary: Boolean = primarySaveFile.name != "$romBaseName.srm"

    data class RestoreResult(
        val sramData: ByteArray?,
        val switchToHardcore: Boolean = false,
        val casualSaveInHardcore: Boolean = false,
        val rtcData: ByteArray? = null
    )

    fun getRtcFile(): File = File(savesDir, "$romBaseName.rtc")

    fun withRtcFromDisk(result: RestoreResult): RestoreResult =
        result.copy(rtcData = getRtcFile().takeIf { it.isFile && it.length() > 0 }?.readBytes())

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

    @Volatile
    private var writtenSaveBaseline: Map<String, Pair<Long, Long>>? = null

    @Volatile
    private var coreSaveFiles: List<File> = emptyList()

    /**
     * Adds [files] the core writes itself, such as a memory card shared by every game, to the
     * files whose writes count as this game saving. The next check re-seeds its baseline.
     */
    fun watchCoreSaveFiles(files: List<File>) {
        coreSaveFiles = files
        writtenSaveBaseline = null
    }

    private fun gameSaveFiles(): List<File> {
        val inSavesDir = savesDir.walkTopDown().maxDepth(2)
            .filter { it.isFile && it.name.startsWith(romBaseName) }
            .filterNot { it.extension.lowercase() in IGNORED_SAVE_EXTENSIONS }
        return (inSavesDir + primarySaveFile + coreSaveFiles).filter { it.isFile }.distinctBy { it.absolutePath }.toList()
    }

    private fun fingerprints(): Map<String, Pair<Long, Long>> =
        gameSaveFiles().associate { it.absolutePath to (it.lastModified() to it.length()) }

    fun seedWrittenSaves() {
        writtenSaveBaseline = fingerprints()
    }

    fun takeNewlyWrittenSaves(): Boolean {
        val baseline = writtenSaveBaseline ?: run {
            seedWrittenSaves()
            return false
        }
        val current = fingerprints()
        writtenSaveBaseline = current
        return current.filter { (path, print) -> baseline[path] != print }
            .keys
            .any { path -> holdsSaveData(File(path)) }
    }

    private fun holdsSaveData(file: File): Boolean = try {
        val bytes = file.readBytes()
        bytes.isNotEmpty() && bytes.any { it != bytes[0] }
    } catch (e: Exception) {
        false
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
     * PSX cards written before the switch to `.srm` are the same shape of problem: the bytes are
     * identical, only the name the core looks for moved.
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

    /**
     * Copies a shared `scd_<region>.brm` or `<size>_cart.brm` to the per-game name Genesis Plus
     * GX reads under per-game BRAM. Copies only, and only when the per-game file is absent.
     */
    fun adoptSharedSegaCdBramIfMissing() {
        val internalTarget = File(savesDir, "$romBaseName.brm")
        if (!internalTarget.exists()) {
            sharedSegaCdRegionFile()?.let { source ->
                copyAdopted(source, internalTarget)
            }
        }
        val sharedCart = savesDir.listFiles()
            ?.filter { it.isFile && SHARED_CART_BRAM.matches(it.name) }
            ?.maxByOrNull { it.lastModified() }
            ?: return
        val cartTarget = File(savesDir, "${romBaseName}_${sharedCart.name}")
        if (!cartTarget.exists()) copyAdopted(sharedCart, cartTarget)
    }

    private fun sharedSegaCdRegionFile(): File? {
        val candidates = SEGACD_REGION_TAGS.mapNotNull { (tag, region) ->
            File(savesDir, "scd_$region.brm").takeIf { it.isFile && it.length() > 0 }?.let { tag to it }
        }
        if (candidates.isEmpty()) return null
        val name = romBaseName.lowercase()
        return candidates.firstOrNull { (tag, _) -> tag in name }?.second
            ?: candidates.maxByOrNull { (_, file) -> file.lastModified() }?.second
    }

    private fun copyAdopted(source: File, target: File) {
        try {
            source.copyTo(target, overwrite = false)
            Log.w(
                TAG,
                "[SRAM] adopted shared save | ${source.absolutePath} -> ${target.absolutePath} " +
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
            names.forEach { name -> add(File(savesDir, "$name$LEGACY_CARD_SUFFIX")) }
            variantDirs.forEach { dir -> names.forEach { name -> add(File(dir, "$name$LEGACY_CARD_SUFFIX")) } }
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
     * The SRAM to load for [launchMode], placed on the live .srm as [ActiveSaveDiskJudge] decides
     * against the save already there. An explicit save-management restore wins over the resume
     * pick; RESUME_HARDCORE with no hardcore save falls back to the active save, flagged
     * casualSaveInHardcore.
     */
    suspend fun restoreSaveForLaunchMode(launchMode: LaunchMode): RestoreResult {
        if (usesExternalMemcard) {
            Log.d(TAG, "External memcard (GCI folder) - skipping flat .srm restore")
            return RestoreResult(null)
        }
        if (isVariant) {
            return when (launchMode) {
                LaunchMode.NEW_HARDCORE, LaunchMode.NEW_CASUAL -> {
                    primarySaveFile.delete()
                    deleteAllStates()
                    RestoreResult(null)
                }
                else -> RestoreResult(coreBytes(primarySaveFile.takeIf { it.exists() }?.readBytes()))
            }
        }
        if (gameId < 0) {
            Log.w(TAG, "No valid gameId, using existing save")
            val bytes = primarySaveFile.takeIf { it.exists() }?.readBytes()
            return RestoreResult(coreBytes(bytes))
        }

        val activeSave = if (launchMode == LaunchMode.RESUME || launchMode == LaunchMode.RESUME_HARDCORE) {
            activeSaveRepository.getActiveRow(gameId)
        } else {
            null
        }

        if (activeSave?.activeSaveApplied == true) {
            if (primarySaveFile.exists()) {
                val bytes = primarySaveFile.readBytes()
                Log.i(TAG, "Honoring explicit restore (activeSaveApplied): on-disk ${primarySaveFile.name} ${bytes.size} bytes")
                return RestoreResult(coreBytes(bytes))
            }
        }

        return when (launchMode) {
            LaunchMode.NEW_HARDCORE, LaunchMode.NEW_CASUAL -> {
                Log.d(TAG, "New game mode - starting fresh (no save)")
                clearSaveForFreshStart()
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
                    val placement = placeCachedSave(hardcoreSave)
                    Log.d(TAG, "Hardcore save cache=${hardcoreSave.id} valid=$isValid inPlay=${placement.targetInPlay} bytes=${placement.bytes?.size ?: -1}")
                    val sram = coreBytes(placement.bytes)
                    RestoreResult(sram, casualSaveInHardcore = sram != null && !placement.targetInPlay)
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

    private class Placement(val bytes: ByteArray?, val targetInPlay: Boolean)

    private suspend fun placeCachedSave(entity: SaveCacheEntity): Placement {
        val primaryPath = primarySaveFile.absolutePath
        val diskHash = when (val disk = saveCacheManager.probeLocalSave(primaryPath, gameId, EmulatorRegistry.BUILTIN_ID)) {
            SaveCacheManager.LocalSaveProbe.Absent -> return Placement(writeCachedSave(entity), targetInPlay = true)
            SaveCacheManager.LocalSaveProbe.Unreadable -> {
                Log.w(TAG, "[SRAM] save on disk could not be read, writing nothing over it | cache=${entity.id} file=$primaryPath")
                return keepDisk()
            }
            is SaveCacheManager.LocalSaveProbe.Present -> disk.contentHash
        }
        val judgement = diskJudge.judge(
            gameId, EmulatorRegistry.BUILTIN_ID, channelName, primaryPath, diskHash, entity, secureSaves, ownerUserId
        )
        return when (val verdict = judgement.verdict) {
            DiskVerdict.Matches -> Placement(diskBytes(), targetInPlay = true)
            is DiskVerdict.Progress -> {
                Log.i(TAG, "[SRAM] disk matches unsynced cache=${verdict.cacheId}, keeping it as the active version")
                activeSaveRepository.activateCache(gameId, verdict.cacheId)
                keepDisk()
            }
            DiskVerdict.Adopt -> {
                val adopted = withContext(NonCancellable) {
                    saveCacheManager.cacheCurrentSave(
                        gameId = gameId,
                        emulatorId = EmulatorRegistry.BUILTIN_ID,
                        savePath = primaryPath,
                        channelName = channelName,
                        needsRemoteSync = true
                    )
                }
                Log.i(TAG, "[SRAM] disk differs from cache=${entity.id}, adopting it (Secure Saves off) | result=${adopted::class.simpleName}")
                keepDisk()
            }
            DiskVerdict.Foreign -> {
                Log.i(TAG, "[SRAM] disk save belongs to another account, leaving it | cache=${entity.id}")
                keepDisk()
            }
            DiskVerdict.ProtectThenRestore -> protectThenWrite(entity, primaryPath)
        }
    }

    private suspend fun protectThenWrite(entity: SaveCacheEntity, primaryPath: String): Placement =
        withContext(NonCancellable) {
            when (saveCacheManager.cacheAsRollback(gameId, EmulatorRegistry.BUILTIN_ID, primaryPath)) {
                is SaveCacheManager.CacheResult.Created, is SaveCacheManager.CacheResult.Duplicate -> {
                    Log.i(TAG, "[SRAM] disk differs from cache=${entity.id}, kept as rollback, restoring (Secure Saves on)")
                    Placement(writeCachedSave(entity), targetInPlay = true)
                }
                SaveCacheManager.CacheResult.Failed -> {
                    Log.e(TAG, "[SRAM] rollback of the disk save failed, writing nothing over it | cache=${entity.id}")
                    keepDisk()
                }
            }
        }

    private fun diskBytes(): ByteArray? = primarySaveFile.takeIf { it.exists() }?.readBytes()

    private fun keepDisk(): Placement = Placement(diskBytes(), targetInPlay = false)

    private suspend fun writeCachedSave(entity: SaveCacheEntity): ByteArray? {
        when (val restored = saveCacheManager.restoreThroughSigil(entity)) {
            is SigilRestore.Restored -> {
                Log.i(TAG, "[SRAM] cache=${entity.id} restored through Sigil -> ${savesDir.absolutePath}")
                return diskBytes()
            }
            is SigilRestore.Refused -> {
                Log.w(TAG, "[SRAM] Sigil refused cache=${entity.id}, keeping the save on disk | ${restored.reason}")
                return diskBytes()
            }
            SigilRestore.NotRouted -> Unit
        }
        val bytes = saveCacheManager.getSaveBytesFromEntity(entity) ?: return null
        primarySaveFile.writeBytes(bytes)
        materializeUnitMembers(entity)
        return bytes
    }

    private suspend fun materializeUnitMembers(entity: SaveCacheEntity) {
        if (!saveCacheManager.isUnitCache(entity)) return
        val ok = saveCacheManager.materializeUnit(entity, primarySaveFile.absolutePath)
        Log.i(TAG, "[SRAM] unit members ${if (ok) "placed" else "NOT placed"} | cache=${entity.id}")
    }

    /**
     * Keeps the on-disk save the core refused at load as a rollback entry, so the session's first
     * write cannot be the only copy left. True when a copy exists in the cache afterwards.
     */
    @Volatile private var sramWritesHeld = false

    /**
     * Holds every save memory write from the moment the core refuses the save on disk. The hold
     * lifts only once [preserveUnloadedSave] has kept a copy, so the refused save is never
     * overwritten without one.
     */
    fun holdWritesForRefusedSave() {
        sramWritesHeld = true
    }

    suspend fun preserveUnloadedSave(): Boolean {
        val sramFile = primarySaveFile
        val kept = !sramFile.exists() ||
            when (saveCacheManager.cacheAsRollback(gameId, EmulatorRegistry.BUILTIN_ID, sramFile.absolutePath)) {
                is SaveCacheManager.CacheResult.Created, is SaveCacheManager.CacheResult.Duplicate -> true
                is SaveCacheManager.CacheResult.Failed -> false
            }
        if (kept) sramWritesHeld = false
        else Log.e(TAG, "[SRAM] refused save could not be copied; save memory writes stay held this session")
        return kept
    }

    private suspend fun clearSaveForFreshStart() {
        val sramFile = primarySaveFile
        val members = saveCacheManager.unitMemberPaths(gameId, EmulatorRegistry.BUILTIN_ID, sramFile.absolutePath)
        if (sramFile.exists()) {
            val result = saveCacheManager.cacheAsRollback(
                gameId,
                EmulatorRegistry.BUILTIN_ID,
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
        members.filter { it != sramFile.absolutePath }.forEach { path ->
            val removed = File(path).delete()
            Log.d(TAG, "Fresh start removed unit member | path=$path removed=$removed")
        }
    }

    private suspend fun restoreResumeSave(activeSave: SaveCacheEntity?): RestoreResult {
        val chosenChannel = channelName
        val targetSave = if (activeSave != null) {
            Log.d(TAG, "RESUME: Using active save cacheId=${activeSave.id} channel='${activeSave.channelName}'")
            activeSave
        } else if (chosenChannel != null) {
            val latestInChannel = saveCacheManager.getMostRecentInChannel(gameId, chosenChannel)
            if (latestInChannel == null) {
                Log.d(TAG, "RESUME: Active slot '$chosenChannel' has no saves - starting it blank")
                clearSaveForFreshStart()
                return RestoreResult(null)
            }
            Log.d(TAG, "RESUME: No active row, using latest in slot '$chosenChannel' cacheId=${latestInChannel.id}")
            latestInChannel
        } else {
            Log.d(TAG, "RESUME: No active save, looking for most recent save overall")
            saveCacheManager.getMostRecentSave(gameId)
        }

        if (targetSave != null && !isBuiltinSave(targetSave)) {
            val bytes = primarySaveFile.takeIf { it.exists() }?.readBytes()
            Log.i(TAG, "RESUME: cache=${targetSave.id} was written by ${targetSave.emulatorId}; using the save the launch placed on disk (${bytes?.size ?: -1} bytes)")
            return RestoreResult(coreBytes(bytes))
        }
        if (targetSave != null) {
            val placement = placeCachedSave(targetSave)
            var switchToHardcore = false
            if (targetSave.isHardcore && placement.targetInPlay) {
                val isValid = saveCacheManager.isValidHardcoreSave(targetSave)
                if (isValid) {
                    switchToHardcore = true
                    Log.d(TAG, "RESUME: Loading hardcore save, switching to hardcore mode")
                } else {
                    Log.w(TAG, "RESUME: Hardcore save missing trailer, loading as casual")
                }
            }
            Log.d(TAG, "RESUME: cache=${targetSave.id} inPlay=${placement.targetInPlay} bytes=${placement.bytes?.size ?: -1} hardcore=${targetSave.isHardcore} -> ${primarySaveFile.name}")
            return RestoreResult(coreBytes(placement.bytes), switchToHardcore)
        } else {
            val f = primarySaveFile
            val bytes = f.takeIf { it.exists() }?.readBytes()
            Log.i(
                TAG,
                "[SRAM] restore fallback | no cached saves, using on-disk primary | " +
                    "file=${f.absolutePath} exists=${f.exists()} bytes=${bytes?.size ?: -1} " +
                    "nonZero=${bytes?.count { it != 0.toByte() } ?: -1}"
            )
            return RestoreResult(coreBytes(bytes))
        }
    }

    private fun coreBytes(bytes: ByteArray?): ByteArray? = if (coreOwnsPrimary) null else bytes

    private fun isBuiltinSave(entity: SaveCacheEntity): Boolean =
        entity.emulatorId == EmulatorRegistry.BUILTIN_ID || entity.emulatorId == EmulatorRegistry.LEGACY_BUILTIN_ID

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
        if (sramWritesHeld) {
            Log.w(TAG, "[SRAM] saveSram SKIP | the refused save has no copy yet, file left alone")
            return
        }
        saveRtc(retroView)
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
            writeAtomically(target, sramData)
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

    private var lastRtcHash: String? = null

    private fun writeAtomically(target: File, bytes: ByteArray) {
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeBytes(bytes)
        if (!temp.renameTo(target)) {
            target.writeBytes(bytes)
            temp.delete()
        }
    }

    private fun saveRtc(retroView: GLRetroView) {
        val target = getRtcFile()
        try {
            val rtc = retroView.getRtcData() ?: return
            val hash = hashBytes(rtc)
            if (hash == lastRtcHash) return
            writeAtomically(target, rtc)
            lastRtcHash = hash
            Log.i(TAG, "[RTC] wrote ${rtc.size} bytes -> ${target.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "[RTC] write FAILED | ${target.absolutePath}", e)
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

    /**
     * When a state was last written this session, on the monotonic clock so a device clock change
     * cannot make it read as the future. Null until one is written; the rolling SRAM save does not
     * count, because it fires on a timer whether or not the game saved anything.
     */
    var lastStateWriteRealtimeMs: Long? by androidx.compose.runtime.mutableStateOf(null)
        private set

    /**
     * Writes the quick-save ring, and the auto slot with it.
     *
     * The ring is local history and is deliberately never cached or synced, so a player who only
     * ever quick-saves would have nothing on the server at all. The auto slot is the one slot sync
     * carries, and it holds what it says: the most recent quick-save.
     */
    fun performQuickSave(stateData: ByteArray, screenshot: Bitmap? = null): Boolean {
        val emptyIndex = (0 until QUICK_RING_SIZE).firstOrNull {
            !getSlotFile(QUICK_SLOT_BASE + it).exists()
        }
        val targetIndex = emptyIndex
            ?: (0 until QUICK_RING_SIZE).minByOrNull { getSlotFile(QUICK_SLOT_BASE + it).lastModified() }
            ?: 0
        val written = performSlotSave(QUICK_SLOT_BASE + targetIndex, stateData, screenshot)
        if (written) {
            performSlotSave(AUTO_SLOT, stateData, screenshot)
        }
        return written
    }

    fun performQuickLoad(retroView: GLRetroView): Boolean {
        val newest = quickRingEntries().maxByOrNull { it.second.lastModified() }?.first ?: return false
        return performSlotLoad(retroView, newest)
    }

    fun performSlotSave(slotNumber: Int, stateData: ByteArray, screenshot: Bitmap? = null): Boolean {
        if (stateData.isEmpty()) {
            Log.e(TAG, "Refusing to save an empty state to slot $slotNumber; the core produced nothing")
            return false
        }
        return try {
            statesDir.mkdirs()
            val stateFile = getSlotFile(slotNumber)
            writeAtomically(stateFile, stateData)

            writeScreenshot(slotNumber, screenshot)

            if (slotNumber in QUICK_SLOT_BASE until QUICK_SLOT_BASE + QUICK_RING_SIZE) {
                hasQuickSave = true
            }
            lastStateWriteRealtimeMs = android.os.SystemClock.elapsedRealtime()
            onLiveStateWritten?.invoke(slotNumber, stateFile)
            Log.d(TAG, "Saved state to slot $slotNumber (${stateData.size} bytes)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save state to slot $slotNumber", e)
            false
        }
    }

    fun slotSavedAtMillis(slotNumber: Int): Long = getSlotFile(slotNumber).lastModified()

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

    enum class AutoLoadOutcome { LOADED, KEPT_SAVE, FAILED }

    /**
     * Loads the auto slot only when the save memory it carries matches the save on disk. On a
     * mismatch the save on disk is written into the core and the game boots fresh from it; the
     * booted game is restored only when the core refuses the save.
     */
    fun loadAutoSlotMatchingSave(retroView: GLRetroView): AutoLoadOutcome {
        val saveHash = lastSramHash
        if (saveHash == null || usesExternalMemcard) {
            return if (performSlotLoad(retroView, AUTO_SLOT)) AutoLoadOutcome.LOADED else AutoLoadOutcome.FAILED
        }
        val bootState = retroView.serializeState()
        if (bootState.isEmpty()) {
            Log.w(TAG, "[SRAM] auto state skipped | the core could not snapshot the booted game to compare against")
            return AutoLoadOutcome.KEPT_SAVE
        }
        if (!performSlotLoad(retroView, AUTO_SLOT)) return AutoLoadOutcome.FAILED
        val stateSram = retroView.serializeSRAM()
        if (stateSram.isEmpty() || hashBytes(stateSram) == saveHash) return AutoLoadOutcome.LOADED

        Log.i(TAG, "[SRAM] auto state skipped | its save memory ${hashBytes(stateSram)} differs from the save $saveHash")
        val disk = coreBytes(getSramFile().takeIf { it.isFile }?.readBytes())
        if (disk != null && retroView.unserializeSRAM(disk)) {
            retroView.reset()
            return AutoLoadOutcome.KEPT_SAVE
        }
        Log.e(TAG, "[SRAM] the save on disk could not be written into the core; returning to the booted game")
        retroView.unserializeState(bootState)
        return AutoLoadOutcome.KEPT_SAVE
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
        val screenshotFile = getSlotScreenshotFile(slotNumber)
        if (bitmap == null) {
            screenshotFile.delete()
            return
        }
        try {
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
        private const val LEGACY_CARD_SUFFIX = "_1.mcd"
        private val IGNORED_SAVE_EXTENSIONS = setOf("rtc", "tmp", "bak", "png")
        private val SHARED_CART_BRAM = Regex("""^(128Kbit|256Kbit|512Kbit|1Mbit|2Mbit|4Mbit)_cart\.brm$""")
        private val SEGACD_REGION_TAGS = listOf("(usa)" to "U", "(europe)" to "E", "(japan)" to "J")
        const val AUTO_SLOT = LibretroStateSlots.AUTO_SLOT
        const val RESUME_SLOT = LibretroStateSlots.RESUME_SLOT
        const val MAX_SLOT = LibretroStateSlots.MAX_SLOT
        const val QUICK_SLOT_BASE = LibretroStateSlots.QUICK_SLOT_BASE
        const val QUICK_RING_SIZE = LibretroStateSlots.QUICK_RING_SIZE
        private const val SCREENSHOT_MAX_WIDTH = 480
    }
}
