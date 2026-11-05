package com.nendo.argosy.data.sync.snapshot

import android.content.Context
import com.nendo.argosy.data.emulator.ArchiveRomNaming
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.libretro.LibretroStateSlots
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SnapshotChannelDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.local.entity.SigilSyncStateEntity
import com.nendo.argosy.data.local.entity.SnapshotChannelEntity
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMRomFile
import com.nendo.argosy.data.remote.romm.RomMSnapshot
import com.nendo.argosy.data.remote.romm.RomMSnapshotConflict
import com.nendo.argosy.data.remote.romm.RomMSnapshotSave
import com.nendo.argosy.data.remote.romm.RomMSnapshotState
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveDownloader
import com.nendo.argosy.data.repository.SaveSyncApiClient
import com.nendo.argosy.data.repository.SaveSyncResult
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.sync.platform.SigilCollect
import com.nendo.argosy.data.sync.platform.SigilPendingState
import com.nendo.argosy.data.sync.platform.SigilSaveHandler
import com.nendo.argosy.util.Logger
import com.squareup.moshi.Moshi
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import android.os.Build
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

sealed class SnapshotSyncResult {
    data object NotEligible : SnapshotSyncResult()
    data object NoConnection : SnapshotSyncResult()
    data object UpToDate : SnapshotSyncResult()
    data class Pushed(val snapshotId: Long) : SnapshotSyncResult()
    data class Branched(val snapshotId: Long, val channelLabel: String) : SnapshotSyncResult()
    data class Applied(val snapshotId: Long) : SnapshotSyncResult()
    data class Conflict(
        val current: RomMSnapshot?,
        val currentId: Long?,
        val localSavePath: String? = null
    ) : SnapshotSyncResult()
    data class HardcoreDowngrade(val currentId: Long?) : SnapshotSyncResult()
    data class Failed(val reason: String) : SnapshotSyncResult()
}

/**
 * A save as it travels in a push: [format] is `neutral` for a unit Sigil built and `native` for the
 * files the emulator wrote, packed the way the legacy upload packs them.
 */
data class SnapshotUnit(
    val data: ByteArray,
    val name: String,
    val contentHash: String,
    val identityHash: String,
    val shape: String,
    val format: String
)

/**
 * Save sync against RomM's snapshot API for every game RomM knows, on every Argosy save channel:
 * list the channel's current, decide on the device, then push or download. States are never
 * listed in a push, so every snapshot carries its parent's bank.
 */
@Singleton
class SnapshotSyncEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao,
    private val channelDao: SnapshotChannelDao,
    private val saveCacheDao: SaveCacheDao,
    private val sigilSaveHandler: SigilSaveHandler,
    private val saveCacheManager: Lazy<SaveCacheManager>,
    private val saveDownloader: Lazy<SaveDownloader>,
    private val apiClient: Lazy<SaveSyncApiClient>,
    private val emulatorResolver: EmulatorResolver,
    private val saveArchiver: SaveArchiver,
    private val syncPreferencesRepository: SyncPreferencesRepository,
    private val pusher: SnapshotPusher,
    private val fileResolver: SnapshotFileResolver,
    private val saveScreenshots: com.nendo.argosy.hardware.SaveScreenshotCapture,
    private val builtinCoreResolver: com.nendo.argosy.data.emulator.BuiltinCoreResolver,
    private val statePaths: com.nendo.argosy.data.emulator.LibretroStatePathResolver,
    private val emulatorStamper: SnapshotEmulatorStamper,
    private val activeSaveRepository: com.nendo.argosy.data.repository.ActiveSaveRepository
) {
    private val locks = ConcurrentHashMap<Long, Mutex>()

    private class ChannelView(
        val api: RomMApi,
        val deviceId: String,
        val ownerUserId: Long,
        val game: GameEntity,
        val file: RomMRomFile,
        val label: String,
        val stored: SnapshotChannelEntity?,
        val current: RomMSnapshot?,
        val channelId: String,
        val isNewChannel: Boolean,
        val ownChannelLabels: List<String>
    )

    private sealed class LocalSave {
        data object None : LocalSave()
        data class Unreadable(val reason: String) : LocalSave()
        data class Sigil(val unit: SigilCollect.Found) : LocalSave()
        data class Native(val savePath: String, val contentHash: String) : LocalSave()

        val hashes: SaveHashes?
            get() = when (this) {
                is Sigil -> SaveHashes(unit.contentHash, unit.identityHash)
                is Native -> SaveHashes(contentHash, contentHash)
                else -> null
            }

        val path: String?
            get() = when (this) {
                is Sigil -> unit.root
                is Native -> savePath
                else -> null
            }

        val pending: SigilPendingState?
            get() = (this as? Sigil)?.unit?.pending
    }

    suspend fun isEligible(gameId: Long): Boolean =
        apiClient.get().getCapabilities().supportsSnapshots && gameDao.getById(gameId)?.rommId != null

    suspend fun sync(gameId: Long, emulatorId: String, channelName: String?, isHardcore: Boolean = false): SnapshotSyncResult =
        locked(gameId) {
            val ctx = load(gameId, channelName) ?: return@locked notReady(gameId)
            val local = localSave(ctx.game, emulatorId)
            if (local is LocalSave.Unreadable) return@locked SnapshotSyncResult.Failed(local.reason)
            val action = SnapshotDecision.decide(held(ctx), currentPoint(ctx.current), local.hashes)
            Logger.info(TAG, "[SaveSync] SNAPSHOT gameId=$gameId channel=${ctx.label}/${ctx.channelId} held=${ctx.stored?.heldSnapshotId} current=${ctx.current?.id} | $action")
            when (action) {
                SnapshotAction.Nothing -> {
                    commitSigilState(local)
                    markReached(ctx, local.hashes?.contentHash)
                    SnapshotSyncResult.UpToDate
                }
                is SnapshotAction.Push ->
                    pushLocal(ctx, local, emulatorId, channelName, action.expectedCurrentId, isHardcore, false, action.parentSnapshotId)
                is SnapshotAction.Adopt -> {
                    val result = adopt(ctx, ctx.current ?: return@locked SnapshotSyncResult.UpToDate)
                    commitSigilState(local)
                    markReached(ctx, local.hashes?.contentHash)
                    result
                }
                is SnapshotAction.Download -> apply(ctx, action.snapshotId, emulatorId, channelName)
                is SnapshotAction.Conflict -> SnapshotSyncResult.Conflict(ctx.current, action.currentId, local.path)
            }
        }

    /**
     * Resolves a conflict for the local save: pushed on top of the channel's current, which
     * rewrites no history.
     */
    suspend fun keepLocal(
        gameId: Long,
        emulatorId: String,
        channelName: String?,
        isHardcore: Boolean = false,
        approveHardcoreDowngrade: Boolean = false
    ): SnapshotSyncResult = locked(gameId) {
        val ctx = load(gameId, channelName) ?: return@locked notReady(gameId)
        val local = localSave(ctx.game, emulatorId)
        pushLocal(ctx, local, emulatorId, channelName, ctx.current?.id, isHardcore, approveHardcoreDowngrade, ctx.stored?.heldSnapshotId)
    }

    /**
     * Pushes the local save as the first snapshot of a new channel named for this device and day,
     * forked from the snapshot this device held on [sourceChannel]. The source channel is left as
     * the server has it.
     */
    suspend fun branch(
        gameId: Long,
        emulatorId: String,
        sourceChannel: String?,
        isHardcore: Boolean = false
    ): SnapshotSyncResult =
        locked(gameId) {
            val source = load(gameId, sourceChannel) ?: return@locked notReady(gameId)
            val label = SnapshotChannels.branchLabel(
                deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
                day = LocalDate.now(),
                taken = activeSaveRepository.registeredChannels(gameId) + source.ownChannelLabels
            )
            val ctx = load(gameId, label) ?: return@locked notReady(gameId)
            val local = localSave(ctx.game, emulatorId)
            val parent = source.stored?.heldSnapshotId ?: source.current?.id
            when (val pushed = pushLocal(ctx, local, emulatorId, label, null, isHardcore, false, parent)) {
                is SnapshotSyncResult.Pushed -> SnapshotSyncResult.Branched(pushed.snapshotId, label)
                else -> pushed
            }
        }

    suspend fun revert(gameId: Long, emulatorId: String, channelName: String?): SnapshotSyncResult = locked(gameId) {
        val ctx = load(gameId, channelName) ?: return@locked notReady(gameId)
        val held = ctx.stored?.heldSnapshotId ?: ctx.current?.id ?: return@locked SnapshotSyncResult.UpToDate
        apply(ctx, held, emulatorId, channelName, byChoice = held != ctx.current?.id)
    }

    /**
     * Pushes one cached save from an offline chain. The first push of a chain the user chose to
     * keep goes on top of the channel's current ([onTopOfCurrent]); every later one expects the
     * snapshot the push before it made, so the chain lands in order or stops at the first conflict.
     * The push carries the format recorded on cache row [cacheId], and marks that row synced once
     * it lands.
     */
    suspend fun pushCached(
        gameId: Long,
        emulatorId: String,
        channelName: String?,
        cacheFile: File,
        onTopOfCurrent: Boolean,
        cacheId: Long?,
        isHardcore: Boolean = false,
        approveHardcoreDowngrade: Boolean = false
    ): SnapshotSyncResult = locked(gameId) {
        val ctx = load(gameId, channelName) ?: return@locked notReady(gameId)
        val format = cacheId?.let { saveCacheDao.getById(it) }?.saveFormat ?: SaveCacheEntity.FORMAT_NATIVE
        val unit = unitFromCache(cacheFile, format) ?: return@locked SnapshotSyncResult.Failed("cached save ${cacheFile.name} is empty")
        val expected = if (onTopOfCurrent) ctx.current?.id else ctx.stored?.heldSnapshotId ?: ctx.current?.id
        push(ctx, unit, PushSource(emulatorId, cacheId = cacheId), expected, isHardcore, approveHardcoreDowngrade, ctx.stored?.heldSnapshotId)
    }

    suspend fun keepServer(gameId: Long, emulatorId: String, channelName: String?): SnapshotSyncResult = locked(gameId) {
        val ctx = load(gameId, channelName) ?: return@locked notReady(gameId)
        val current = ctx.current ?: return@locked SnapshotSyncResult.UpToDate
        apply(ctx, current.id, emulatorId, channelName)
    }

    /**
     * Places [snapshotId] on this device without changing the channel on the server. The device
     * keeps it until it is played on, and the next push names it as the parent.
     */
    suspend fun restoreLocally(
        gameId: Long,
        emulatorId: String,
        channelName: String?,
        snapshotId: Long
    ): SnapshotSyncResult = locked(gameId) {
        val ctx = load(gameId, channelName) ?: return@locked notReady(gameId)
        apply(ctx, snapshotId, emulatorId, channelName, byChoice = snapshotId != ctx.current?.id)
    }

    private suspend fun <T> locked(gameId: Long, block: suspend () -> T): T = withContext(Dispatchers.IO) {
        locks.computeIfAbsent(gameId) { Mutex() }.withLock { block() }
    }

    private suspend fun notReady(gameId: Long): SnapshotSyncResult =
        if (!isEligible(gameId)) SnapshotSyncResult.NotEligible else SnapshotSyncResult.NoConnection

    private suspend fun load(gameId: Long, channelName: String?): ChannelView? {
        if (!isEligible(gameId)) return null
        val client = apiClient.get()
        val api = client.getApi() ?: return null
        val deviceId = client.getDeviceId() ?: return null
        val game = gameDao.getById(gameId) ?: return null
        val file = fileResolver.launchedFile(api, game) ?: run {
            Logger.warn(TAG, "[SaveSync] SNAPSHOT gameId=$gameId | the launched file has no RomM file record")
            return null
        }
        val label = SnapshotChannels.labelOf(channelName)
        val ownerUserId = syncPreferencesRepository.getRommUserId() ?: SigilSyncStateEntity.NO_OWNER
        val stored = channelDao.get(ownerUserId, gameId, label)?.takeIf { it.romFileId == file.id }
        val channels = runCatching { api.listChannels(listOf(file.id)) }.getOrNull()
            ?.takeIf { it.isSuccessful }?.body() ?: return null
        val channel = channels.firstOrNull { it.id == stored?.channelId }
            ?: channels.filter { it.isOwn && it.label.equals(label, ignoreCase = true) }
                .maxByOrNull { it.current?.createdAt.orEmpty() }
        val channelId = channel?.id ?: UUID.randomUUID().toString()
        return ChannelView(
            api = api,
            deviceId = deviceId,
            ownerUserId = ownerUserId,
            game = game,
            file = file,
            label = label,
            stored = stored?.takeIf { it.channelId == channelId },
            current = channel?.current,
            channelId = channelId,
            isNewChannel = channel == null,
            ownChannelLabels = channels.filter { it.isOwn }.map { it.label }
        )
    }

    private suspend fun localSave(game: GameEntity, emulatorId: String): LocalSave =
        when (val collected = sigilSaveHandler.collect(game.id, emulatorId)) {
            is SigilCollect.Found -> LocalSave.Sigil(collected)
            SigilCollect.Absent -> LocalSave.None
            is SigilCollect.Unreadable -> LocalSave.Unreadable(collected.reason)
            SigilCollect.NotRouted -> nativeSave(game, emulatorId)
        }

    private suspend fun nativeSave(game: GameEntity, emulatorId: String): LocalSave {
        val client = apiClient.get()
        val savePath = client.discoverSavePath(
            emulatorId = emulatorId,
            gameTitle = game.title,
            platformSlug = game.platformSlug,
            romPath = game.localPath,
            cachedSaveId = game.saveId ?: game.titleId,
            coreName = client.resolveCoreForGame(game, emulatorId),
            emulatorPackage = emulatorResolver.getEmulatorPackageForGame(game.id, game.platformId, game.platformSlug),
            gameId = game.id
        ) ?: return LocalSave.None
        val hash = saveCacheManager.get().calculateLocalSaveHash(savePath, game.id, emulatorId) ?: return LocalSave.None
        return LocalSave.Native(savePath, hash)
    }

    private fun held(ctx: ChannelView): SnapshotPoint? {
        val stored = ctx.stored ?: return null
        val id = stored.heldSnapshotId ?: return null
        val save = stored.heldSaveHash?.let { SaveHashes(it, stored.heldSaveIdentityHash ?: it) }
        return SnapshotPoint(id, save, stored.heldByChoice)
    }

    private fun currentPoint(current: RomMSnapshot?): SnapshotPoint? = current?.let { snapshot ->
        SnapshotPoint(snapshot.id, snapshot.save?.hashes())
    }

    private suspend fun pushLocal(
        ctx: ChannelView,
        local: LocalSave,
        emulatorId: String,
        channelName: String?,
        expectedCurrentId: Long?,
        isHardcore: Boolean,
        approveHardcoreDowngrade: Boolean,
        parentSnapshotId: Long? = null
    ): SnapshotSyncResult {
        val cached = when (local) {
            is LocalSave.Sigil -> CachedUnit(
                SnapshotUnit(
                    local.unit.data, local.unit.artifact, local.unit.contentHash, local.unit.identityHash,
                    local.unit.shape, SaveCacheEntity.FORMAT_NEUTRAL
                ),
                cacheId = null
            )
            is LocalSave.Native -> nativeUnit(ctx.game.id, emulatorId, channelName, local.savePath, isHardcore)
                ?: return SnapshotSyncResult.Failed("could not pack the save at ${local.savePath}")
            is LocalSave.Unreadable -> return SnapshotSyncResult.Failed(local.reason)
            LocalSave.None -> return SnapshotSyncResult.Failed("no local save to push")
        }
        val source = PushSource(emulatorId, local.path, local.pending, cached.cacheId, isSaveOnDisk = true)
        return push(ctx, cached.unit, source, expectedCurrentId, isHardcore, approveHardcoreDowngrade, parentSnapshotId)
    }

    private class CachedUnit(val unit: SnapshotUnit, val cacheId: Long?)

    private suspend fun nativeUnit(
        gameId: Long,
        emulatorId: String,
        channelName: String?,
        savePath: String,
        isHardcore: Boolean
    ): CachedUnit? {
        val cache = saveCacheManager.get()
        val cacheId = when (val cached = cache.cacheCurrentSave(gameId, emulatorId, savePath, channelName, isHardcore = isHardcore)) {
            is SaveCacheManager.CacheResult.Created -> cached.cacheId
            is SaveCacheManager.CacheResult.Duplicate -> cached.cacheId
            SaveCacheManager.CacheResult.Failed -> return null
        }
        val entity = cache.getCacheById(cacheId) ?: return null
        val unit = unitFromCache(cache.getCacheFile(entity), SaveCacheEntity.FORMAT_NATIVE) ?: return null
        return CachedUnit(unit, cacheId)
    }

    private fun unitFromCache(file: File, format: String): SnapshotUnit? {
        val data = saveArchiver.readBytesWithoutTrailer(file)?.takeIf { saveArchiver.hasHardcoreTrailer(file) }
            ?: file.takeIf { it.isFile }?.readBytes()
            ?: return null
        if (data.isEmpty()) return null
        val hashFile = File(context.cacheDir, "snapshot_hash_${System.nanoTime()}").apply { writeBytes(data) }
        val hash = try {
            saveArchiver.calculateContentHash(hashFile)
        } finally {
            hashFile.delete()
        }
        return SnapshotUnit(data, file.name, hash, hash, SigilSaveHandler.unitShape(null, data), format)
    }

    private suspend fun autoStates(ctx: ChannelView, emulatorId: String, isHardcore: Boolean): List<StatePart> {
        if (isHardcore || emulatorId != EmulatorRegistry.BUILTIN_ID) return emptyList()
        val romBaseName = liveBaseName(ctx.game) ?: return emptyList()
        val core = builtinCoreResolver.resolveCoreId(ctx.game.id, ctx.game.platformId, ctx.game.platformSlug)
            ?: return emptyList()
        val dir = statePaths.liveStateBaseDir(ctx.game.id)
        val file = statePaths.liveStateFile(dir, romBaseName, LibretroStateSlots.AUTO_SLOT)
            .takeIf { it.isFile && it.length() > 0 } ?: return emptyList()
        val hash = saveArchiver.calculateContentHash(file)
        val known = ctx.current?.states?.get(core)?.get(AUTO_SLOT)?.contentHash
        return listOf(
            StatePart(
                core = core,
                slot = AUTO_SLOT,
                file = file,
                hash = hash,
                screenshot = File(dir, "${file.name}$STATE_SCREENSHOT_EXTENSION").takeIf { it.isFile },
                serverHasIt = known == hash
            )
        )
    }

    private class PushSource(
        val emulatorId: String,
        val localSavePath: String? = null,
        val sigilState: SigilPendingState? = null,
        val cacheId: Long? = null,
        val isSaveOnDisk: Boolean = false
    )

    private suspend fun push(
        ctx: ChannelView,
        unit: SnapshotUnit,
        source: PushSource,
        expectedCurrentId: Long?,
        isHardcore: Boolean,
        approveHardcoreDowngrade: Boolean,
        parentSnapshotId: Long? = null
    ): SnapshotSyncResult {
        val states = autoStates(ctx, source.emulatorId, isHardcore)
        val manifest = JSONObject().apply {
            if (states.isNotEmpty()) {
                put("states", JSONObject().apply {
                    states.groupBy { it.core }.forEach { (core, slots) ->
                        put(core, JSONObject().apply { slots.forEach { put(it.slot, it.hash) } })
                    }
                })
            }
            put("rom_file_id", ctx.file.id)
            put("channel_id", ctx.channelId)
            if (ctx.isNewChannel) put("label", ctx.label)
            put("expected_current_id", expectedCurrentId ?: JSONObject.NULL)
            parentSnapshotId?.let { put("parent_snapshot_id", it) }
            put("save", JSONObject().apply {
                put("hash", unit.contentHash)
                put("shape", unit.shape)
                put("format", unit.format)
            })
            put("is_hardcore", isHardcore)
            if (approveHardcoreDowngrade) put("approve_hardcore_downgrade", true)
        }
        emulatorStamper.stampFor(ctx.game, source.emulatorId).writeTo(manifest)
        val screenshot = saveScreenshots.recentFor(ctx.game.id)
        val saveServerHasIt = ctx.current?.save?.contentHash == unit.contentHash
        return when (val outcome = pusher.push(ctx.api, ctx.deviceId, manifest, unit, screenshot, states, saveServerHasIt)) {
            is PushOutcome.Written -> {
                (screenshot ?: states.firstNotNullOfOrNull { it.screenshot })
                    ?.let { saveScreenshots.keepForSnapshot(outcome.snapshot.id, it) }
                screenshot?.delete()
                record(ctx, outcome.snapshot.id, outcome.snapshot.digest, SaveHashes(unit.contentHash, unit.identityHash))
                clearPushedDirtyFlags(ctx)
                markReached(ctx, unit.contentHash, source.cacheId)
                source.cacheId?.takeIf { source.isSaveOnDisk }?.let { activeSaveRepository.activateCache(ctx.game.id, it) }
                source.sigilState?.let { sigilSaveHandler.commit(it) }
                Logger.info(TAG, "[SaveSync] SNAPSHOT gameId=${ctx.game.id} | pushed #${outcome.snapshot.id} (${unit.format}) on ${ctx.label}/${ctx.channelId} expecting $expectedCurrentId")
                SnapshotSyncResult.Pushed(outcome.snapshot.id)
            }
            PushOutcome.HardcoreDowngrade -> SnapshotSyncResult.HardcoreDowngrade(expectedCurrentId)
            is PushOutcome.Conflict -> {
                Logger.info(TAG, "[SaveSync] SNAPSHOT gameId=${ctx.game.id} | push refused, current=${outcome.currentId}")
                SnapshotSyncResult.Conflict(null, outcome.currentId, source.localSavePath)
            }
            is PushOutcome.Failed -> SnapshotSyncResult.Failed(outcome.reason)
        }
    }

    private suspend fun commitSigilState(local: LocalSave) {
        local.pending?.let { sigilSaveHandler.commit(it) }
    }

    private suspend fun adopt(ctx: ChannelView, current: RomMSnapshot): SnapshotSyncResult {
        record(ctx, current.id, current.digest, current.save?.hashes())
        report(ctx, current.id)
        return SnapshotSyncResult.Applied(current.id)
    }

    private suspend fun apply(
        ctx: ChannelView,
        snapshotId: Long,
        emulatorId: String,
        channelName: String?,
        byChoice: Boolean = false
    ): SnapshotSyncResult {
        val snapshot = runCatching { ctx.api.getSnapshot(snapshotId, ctx.deviceId, hold = true) }
            .getOrNull()?.takeIf { it.isSuccessful }?.body()
            ?: return SnapshotSyncResult.Failed("could not read snapshot #$snapshotId")
        val staged = when (val stage = stageBank(ctx, snapshot, emulatorId)) {
            BankStage.None -> null
            is BankStage.Failed -> return SnapshotSyncResult.Failed(stage.reason)
            is BankStage.Staged -> stage.bank
        }
        val save = snapshot.save
        try {
            if (save != null) {
                val placed = saveDownloader.get().downloadSave(
                    gameId = ctx.game.id,
                    emulatorId = emulatorId,
                    channelName = channelName,
                    knownServerSaveId = save.id,
                    fromSnapshot = true
                )
                when (placed) {
                    is SaveSyncResult.Success -> Unit
                    is SaveSyncResult.Error -> return SnapshotSyncResult.Failed(placed.message)
                    else -> return SnapshotSyncResult.Failed("save ${save.id} was not placed: ${placed::class.simpleName}")
                }
            }
            if (staged != null && !commitBank(staged)) {
                return SnapshotSyncResult.Failed("the states of snapshot #${snapshot.id} could not be moved into place")
            }
        } finally {
            staged?.staging?.deleteRecursively()
        }
        staged?.let {
            Logger.info(TAG, "[SaveSync] SNAPSHOT gameId=${ctx.game.id} | bank of #${snapshot.id}: kept=${it.kept.size} written=${it.written} of ${it.size}")
        }
        save?.let { activatePlacedSave(ctx, it.id) }
        record(ctx, snapshot.id, snapshot.digest, save?.hashes(), byChoice)
        Logger.info(TAG, "[SaveSync] SNAPSHOT gameId=${ctx.game.id} | applied #${snapshot.id} from ${ctx.label}/${ctx.channelId}")
        return SnapshotSyncResult.Applied(snapshot.id)
    }

    private suspend fun activatePlacedSave(ctx: ChannelView, serverSaveId: Long) {
        val owner = syncPreferencesRepository.getRommUserId()
        val row = saveCacheDao.getByGameAndOwner(ctx.game.id, owner).firstOrNull { it.rommSaveId == serverSaveId }
        if (row == null) {
            Logger.warn(TAG, "[SaveSync] SNAPSHOT gameId=${ctx.game.id} | no cache row holds save $serverSaveId to make active")
            return
        }
        val activated = activeSaveRepository.activateCache(ctx.game.id, row.id)
        Logger.info(TAG, "[SaveSync] SNAPSHOT gameId=${ctx.game.id} | active cache id=${row.id} for save $serverSaveId (ok=$activated)")
    }

    private fun liveBaseName(game: GameEntity): String? =
        game.localPath?.let { ArchiveRomNaming.liveBaseName(File(it), game.platformSlug) }

    private fun bankSlot(key: String): Int? =
        if (key == AUTO_SLOT) LibretroStateSlots.AUTO_SLOT else key.toIntOrNull()?.takeIf { it in 0..LibretroStateSlots.MAX_SLOT }

    private class StagedBank(
        val dir: File,
        val romBaseName: String,
        val kept: Set<Int>,
        val staging: File,
        val placements: List<Pair<File, File>>,
        val written: Int,
        val size: Int
    )

    private sealed class BankStage {
        data object None : BankStage()
        class Staged(val bank: StagedBank) : BankStage()
        class Failed(val reason: String) : BankStage()
    }

    private fun liveSlots(dir: File, romBaseName: String): Map<Int, File> =
        dir.listFiles().orEmpty().filter { it.isFile }.mapNotNull { file ->
            LibretroStateSlots.parseSlotNumber(romBaseName, file.name)?.let { it to file }
        }.toMap()

    private suspend fun stageBank(ctx: ChannelView, snapshot: RomMSnapshot, emulatorId: String): BankStage {
        if (emulatorId != EmulatorRegistry.BUILTIN_ID) return BankStage.None
        val romBaseName = liveBaseName(ctx.game) ?: return BankStage.None
        val core = builtinCoreResolver.resolveCoreId(ctx.game.id, ctx.game.platformId, ctx.game.platformSlug)
            ?: return BankStage.None
        val dir = statePaths.liveStateBaseDir(ctx.game.id)
        val bank: Map<Int, RomMSnapshotState> = if (snapshot.isHardcore) {
            emptyMap()
        } else {
            snapshot.states[core].orEmpty().mapNotNull { (key, state) -> bankSlot(key)?.let { it to state } }.toMap()
        }
        val kept = liveSlots(dir, romBaseName).filter { (slot, file) ->
            val banked = bank[slot]?.contentHash
            banked != null && saveArchiver.calculateContentHash(file) == banked
        }.keys
        val staging = File(dir, "$STAGING_PREFIX${System.nanoTime()}")
        val placements = mutableListOf<Pair<File, File>>()
        var written = 0
        for ((slot, banked) in bank) {
            if (slot in kept) continue
            val path = banked.downloadPath ?: continue
            val target = statePaths.liveStateFile(dir, romBaseName, slot)
            val wanted = listOfNotNull(
                path to target,
                banked.screenshot?.downloadPath?.let { it to File(dir, "${target.name}$STATE_SCREENSHOT_EXTENSION") }
            )
            for ((download, destination) in wanted) {
                val temp = File(staging, destination.name)
                if (!fetchTo(ctx.api, download, temp)) {
                    staging.deleteRecursively()
                    return BankStage.Failed("could not fetch $download for slot $slot of snapshot #${snapshot.id} ($core)")
                }
                placements += temp to destination
            }
            written++
        }
        return BankStage.Staged(StagedBank(dir, romBaseName, kept, staging, placements, written, bank.size))
    }

    private fun commitBank(staged: StagedBank): Boolean {
        liveSlots(staged.dir, staged.romBaseName).filterKeys { it !in staged.kept }.values.forEach { file ->
            file.delete()
            File(staged.dir, "${file.name}$STATE_SCREENSHOT_EXTENSION").delete()
        }
        return staged.placements.all { (temp, target) ->
            temp.renameTo(target) || runCatching { temp.copyTo(target, overwrite = true); true }.getOrDefault(false)
        }
    }

    private suspend fun fetchTo(api: RomMApi, downloadPath: String, target: File): Boolean = withContext(Dispatchers.IO) {
        val response = runCatching { api.downloadRaw(downloadPath.trimStart('/')) }.getOrNull()
            ?.takeIf { it.isSuccessful } ?: return@withContext false
        val body = response.body() ?: return@withContext false
        val temp = File(target.parentFile, "${target.name}.part")
        runCatching {
            target.parentFile?.mkdirs()
            body.byteStream().use { input -> temp.outputStream().use { input.copyTo(it) } }
            temp.renameTo(target) || run { temp.copyTo(target, overwrite = true); temp.delete() }
        }.getOrElse { temp.delete(); false }
    }

    private fun RomMSnapshotSave.hashes(): SaveHashes? = contentHash?.let { content ->
        SaveHashes(content, identityHash.takeUnless { format == SaveCacheEntity.FORMAT_NATIVE } ?: content)
    }

    private suspend fun record(ctx: ChannelView, snapshotId: Long, digest: String, save: SaveHashes?, byChoice: Boolean = false) {
        channelDao.upsert(
            SnapshotChannelEntity(
                ownerUserId = ctx.ownerUserId,
                gameId = ctx.game.id,
                label = ctx.label,
                channelId = ctx.channelId,
                romFileId = ctx.file.id,
                heldSnapshotId = snapshotId,
                heldDigest = digest,
                heldSaveHash = save?.contentHash,
                heldSaveIdentityHash = save?.identityHash,
                heldByChoice = byChoice,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private fun cacheOwnerOf(ctx: ChannelView): Long? = ctx.ownerUserId.takeUnless { it == SigilSyncStateEntity.NO_OWNER }

    private fun cacheChannelsOf(ctx: ChannelView): List<String?> =
        if (SnapshotChannels.isDefaultLabel(ctx.label)) listOf(null, SaveSyncApiClient.AUTOSAVE_SLOT_NAME) else listOf(ctx.label)

    private suspend fun clearPushedDirtyFlags(ctx: ChannelView) {
        val owner = cacheOwnerOf(ctx)
        cacheChannelsOf(ctx).forEach { channel ->
            if (channel == null) {
                saveCacheDao.clearDirtyFlagForNoChannel(ctx.game.id, owner)
            } else {
                saveCacheDao.clearDirtyFlagForChannel(ctx.game.id, owner, channel, NO_CACHE_ID)
            }
        }
    }

    private suspend fun markReached(ctx: ChannelView, contentHash: String?, cacheId: Long? = null) {
        val owner = cacheOwnerOf(ctx)
        val matching = contentHash?.let { hash ->
            cacheChannelsOf(ctx).flatMap { saveCacheDao.getAllByGameChannelAndHash(ctx.game.id, owner, it, hash) }.map { it.id }
        }.orEmpty()
        val now = Instant.now()
        (matching + listOfNotNull(cacheId)).distinct().forEach { saveCacheDao.markSynced(it, now) }
    }

    private suspend fun report(ctx: ChannelView, snapshotId: Long) {
        runCatching { ctx.api.reportSnapshotHeld(snapshotId, ctx.deviceId) }
            .onFailure { Logger.warn(TAG, "[SaveSync] SNAPSHOT gameId=${ctx.game.id} | report of #$snapshotId failed: ${it.message}") }
    }

    companion object {
        private const val TAG = "SnapshotSyncEngine"
        private const val AUTO_SLOT = "auto"
        private const val STATE_SCREENSHOT_EXTENSION = ".png"
        private const val STAGING_PREFIX = ".snapshot-bank-"
        private const val NO_CACHE_ID = -1L
    }
}
