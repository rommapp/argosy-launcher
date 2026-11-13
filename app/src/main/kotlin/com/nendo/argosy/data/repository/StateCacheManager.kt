package com.nendo.argosy.data.repository

import android.content.Context
import android.util.Log
import com.nendo.argosy.data.emulator.CoreVersionExtractor
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.RetroArchConfigParser
import com.nendo.argosy.data.emulator.provenStateKey
import com.nendo.argosy.data.emulator.StateNameKey
import com.nendo.argosy.data.emulator.StatePathConfig
import com.nendo.argosy.data.emulator.StatePathRegistry
import com.nendo.argosy.data.emulator.VersionValidationResult
import com.nendo.argosy.libretro.LibretroStateFormat
import com.nendo.argosy.data.local.dao.EmulatorSaveConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.dao.StateCacheDao
import com.nendo.argosy.data.local.dao.StateTombstoneDao
import com.nendo.argosy.data.local.entity.StateTombstoneEntity
import com.nendo.argosy.data.local.entity.PendingSyncQueueEntity
import com.nendo.argosy.data.local.entity.StateCacheEntity
import com.nendo.argosy.data.local.entity.SyncPriority
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.data.sync.SaveStatePayload
import com.nendo.argosy.data.sync.targetsRomOf
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMDeleteSavesRequest
import com.nendo.argosy.data.remote.romm.RomMDeleteStatesRequest
import com.nendo.argosy.data.remote.romm.RomMSave
import com.nendo.argosy.data.remote.romm.RomMState
import com.nendo.argosy.data.storage.StorageAttributionRepository
import com.nendo.argosy.data.storage.StorageCategory
import com.nendo.argosy.util.AppPaths
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

private const val HTTP_NOT_FOUND = 404

data class DiscoveredState(
    val file: File,
    val slotNumber: Int,
    val lastModified: Instant
)

@Singleton
class StateCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao,
    private val stateCacheDao: StateCacheDao,
    private val stateTombstoneDao: StateTombstoneDao,
    private val saveCacheDao: SaveCacheDao,
    private val saveSyncDao: SaveSyncDao,
    private val pendingSyncQueueDao: PendingSyncQueueDao,
    private val emulatorSaveConfigDao: EmulatorSaveConfigDao,
    private val preferencesRepository: UserPreferencesRepository,
    private val syncPreferencesRepository: SyncPreferencesRepository,
    private val coreVersionExtractor: CoreVersionExtractor,
    private val retroArchConfigParser: RetroArchConfigParser,
    private val retroArchPathResolver: com.nendo.argosy.data.emulator.RetroArchPathResolver,
    private val libretroStatePathResolver: com.nendo.argosy.data.emulator.LibretroStatePathResolver,
    private val saveSyncApiClient: SaveSyncApiClient,
    private val payloadCodec: com.nendo.argosy.data.sync.SyncPayloadCodec,
    private val attributionRepository: StorageAttributionRepository,
    private val stateOwnershipTracker: com.nendo.argosy.data.sync.StateOwnershipTracker
) {
    companion object {
        private const val TAG = "StateCacheManager"
        private const val UNKNOWN_CORE_DIR = "unknown"
        private const val MIN_UNLOCKED_SLOTS = 3
        private const val BUFFER_SIZE = 8192
        private const val DISPLACED_DIR = "displaced"
        private const val DISPLACED_RETENTION_DAYS = 14L
        private val LOCAL_ONLY_STATUSES = setOf(StateCacheEntity.STATUS_LOCAL_NEWER, StateCacheEntity.STATUS_PENDING_UPLOAD)
        private val UPLOAD_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
        private val STATE_NAME_TIMESTAMP =
            Regex("""\[(\d{4}-\d{2}-\d{2})[_ ](\d{2}-\d{2}-\d{2})(?:-(\d{3}))?]""")
        private val SLOT_REGEX = Regex("""\.state(\d+)?$""")
        private val UNDERSCORE_SLOT_REGEX = Regex("""_(\d+)\.[^.]+$""")
    }

    private val cacheBaseDir: File
        get() = AppPaths.stateCacheDir(context.filesDir)

    private val sessionStateStore by lazy { com.nendo.argosy.data.preferences.SessionStateStore(context) }

    @Volatile
    private var legacyCacheCleared = false

    private fun clearLegacyCacheIfNeeded() {
        if (legacyCacheCleared) return
        synchronized(this) {
            if (legacyCacheCleared) return
            if (!cacheBaseDir.exists()) {
                legacyCacheCleared = true
                return
            }
            val subdirs = cacheBaseDir.listFiles { file -> file.isDirectory } ?: emptyArray()
            for (subdir in subdirs) {
                if (subdir.name.toLongOrNull() != null) {
                    Log.d(TAG, "Clearing legacy cache directory: ${subdir.name}")
                    subdir.deleteRecursively()
                }
            }
            legacyCacheCleared = true
        }
    }

    suspend fun discoverStatesForGame(
        gameId: Long,
        emulatorId: String,
        romPath: String,
        platformId: String,
        serials: Set<String>,
        emulatorPackage: String? = null,
        coreName: String? = null
    ): List<DiscoveredState> = withContext(Dispatchers.IO) {
        Log.d(TAG, "discoverStatesForGame: gameId=$gameId, emulatorId=$emulatorId, platformId=$platformId")
        Log.d(TAG, "romPath=$romPath")

        val config = StatePathRegistry.getConfig(emulatorId)
        if (config == null) {
            Log.d(TAG, "No state config for emulator: $emulatorId")
            return@withContext emptyList()
        }

        val romFile = File(romPath)
        val romBaseNames = if (emulatorId == EmulatorRegistry.BUILTIN_ID) {
            com.nendo.argosy.data.emulator.ArchiveRomNaming.candidateBaseNames(romFile, platformId)
        } else {
            listOf(romFile.nameWithoutExtension)
        }
        Log.d(TAG, "romBaseNames=$romBaseNames")

        val userStateOverride = emulatorSaveConfigDao.getByEmulator(emulatorId)
            ?.takeIf { it.isUserStateOverride }
            ?.statePathPattern

        val statePaths = when {
            com.nendo.argosy.data.emulator.RetroArchPathResolver.isRetroArch(emulatorId) -> {
                val req = com.nendo.argosy.data.emulator.RetroArchPathResolver.Request(
                    emulatorId = emulatorId,
                    coreName = coreName,
                    romPath = romPath,
                )
                retroArchPathResolver.resolveStateDirectories(req)
            }
            emulatorId == EmulatorRegistry.BUILTIN_ID -> listOf(libretroStatePathResolver.liveStateBaseDir(gameId).absolutePath)
            userStateOverride != null -> listOf(userStateOverride)
            else -> StatePathRegistry.resolvePath(config, platformId)
        }
        Log.d(TAG, "Searching ${statePaths.size} paths: $statePaths")

        val discovered = mutableListOf<DiscoveredState>()

        for (statePath in statePaths) {
            val stateDir = File(statePath)
            Log.d(TAG, "Checking dir: $statePath exists=${stateDir.exists()} isDir=${stateDir.isDirectory}")
            if (stateDir.exists() && stateDir.isDirectory) {
                val files = stateDir.listFiles()?.map { it.name } ?: emptyList()
                Log.d(TAG, "Files in $statePath: $files")
            }
            val states = romBaseNames.firstNotNullOfOrNull { baseName ->
                StatePathRegistry.discoverStates(stateDir, StateNameKey(baseName, serials), config.slotPattern)
                    .takeIf { it.isNotEmpty() }
            }.orEmpty()
            Log.d(TAG, "Found ${states.size} matching states in $statePath")
            for ((file, slotNumber) in states) {
                discovered.add(
                    DiscoveredState(
                        file = file,
                        slotNumber = slotNumber,
                        lastModified = Instant.ofEpochMilli(file.lastModified())
                    )
                )
            }
            if (discovered.isNotEmpty() && emulatorId != EmulatorRegistry.BUILTIN_ID) break
        }

        Log.d(TAG, "Discovered ${discovered.size} states for game $gameId")
        discovered
    }

    /**
     * Copies a live state (and its paired screenshot) into the cache and records the live path as
     * belonging to the caching account.
     *
     * [ownerUserIdOverride] exists for the account-switch and account-removal teardowns, which
     * archive the outgoing account's states while a different account may be the signed-in one.
     * Re-owning the row afterwards is not equivalent: the slot lookup this method performs is
     * itself owner-scoped, and the unique index folds the owner in, so the archive has to be
     * written under the right account rather than moved to it.
     */
    suspend fun cacheState(
        gameId: Long,
        platformSlug: String,
        emulatorId: String,
        slotNumber: Int,
        statePath: String,
        coreId: String? = null,
        coreVersion: String? = null,
        channelName: String? = null,
        isLocked: Boolean = false,
        ownerUserIdOverride: Long? = null
    ): Long? = withContext(Dispatchers.IO) {
        clearLegacyCacheIfNeeded()

        val stateFile = File(statePath)
        if (!stateFile.exists()) {
            Log.w(TAG, "State file does not exist: $statePath")
            return@withContext null
        }

        val now = Instant.now()
        val ownerUserId = ownerUserIdOverride ?: syncPreferencesRepository.getRommUserId()
        val relativeDir = stateRelativeDir(ownerUserId, gameId, platformSlug, channelName, coreId)
        val coreDir = File(cacheBaseDir, relativeDir)

        try {
            coreDir.mkdirs()

            val cachedFile = File(coreDir, stateFile.name)
            stateFile.copyTo(cachedFile, overwrite = true)
            val cachePath = "$relativeDir/${stateFile.name}"
            val stateSize = cachedFile.length()

            var screenshotCachePath: String? = null
            val screenshotFile = StatePathRegistry.screenshotFileFor(emulatorId, stateFile)
            Log.d(TAG, "Looking for screenshot at: ${screenshotFile.absolutePath} exists=${screenshotFile.exists()}")
            if (screenshotFile.exists()) {
                val cachedScreenshot = File(coreDir, screenshotFile.name)
                screenshotFile.copyTo(cachedScreenshot, overwrite = true)
                screenshotCachePath = "$relativeDir/${screenshotFile.name}"
                Log.d(TAG, "Cached screenshot at $screenshotCachePath")
            } else {
                Log.d(TAG, "No screenshot found for state: $statePath")
            }

            val existing = stateCacheDao.getBySlotAndCore(
                gameId = gameId,
                emulatorId = emulatorId,
                slotNumber = slotNumber,
                channelName = channelName,
                coreId = coreId,
                ownerUserId = ownerUserId
            )

            val entity = StateCacheEntity(
                id = existing?.id ?: 0,
                gameId = gameId,
                platformSlug = platformSlug,
                emulatorId = emulatorId,
                slotNumber = slotNumber,
                channelName = channelName,
                cachedAt = now,
                stateSize = stateSize,
                cachePath = cachePath,
                screenshotPath = screenshotCachePath,
                coreId = coreId,
                coreVersion = coreVersion,
                isLocked = isLocked,
                note = existing?.note,
                rommSaveId = existing?.rommSaveId,
                syncStatus = existing?.syncStatus,
                serverUpdatedAt = existing?.serverUpdatedAt,
                lastUploadedHash = existing?.lastUploadedHash,
                ownerUserId = existing?.ownerUserId ?: ownerUserId
            )

            val id = stateCacheDao.upsert(entity)
            Log.d(TAG, "Cached state for game $gameId slot $slotNumber at $cachePath")

            if (isLiveStatePath(statePath)) {
                stateOwnershipTracker.record(
                    statePath = statePath,
                    emulatorId = emulatorId,
                    contentHash = calculateFileHash(cachedFile),
                    gameId = gameId,
                    slotNumber = slotNumber,
                    channelName = channelName,
                    coreId = coreId,
                    ownerUserIdOverride = ownerUserId
                )
            }

            pruneOldCaches(gameId, ownerUserId)
            id
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache state", e)
            coreDir.deleteRecursively()
            null
        }
    }

    suspend fun restoreState(cacheId: Long, targetPath: String): Boolean = withContext(Dispatchers.IO) {
        val entity = stateCacheDao.getById(cacheId)
        if (entity == null) {
            Log.e(TAG, "State cache entry not found: $cacheId")
            return@withContext false
        }

        val cacheFile = File(cacheBaseDir, entity.cachePath)
        if (!cacheFile.exists()) {
            Log.e(TAG, "State cache file not found: ${entity.cachePath}")
            return@withContext false
        }

        try {
            val targetFile = File(targetPath)
            targetFile.parentFile?.mkdirs()
            cacheFile.copyTo(targetFile, overwrite = true)
            restoreScreenshot(entity, targetFile)

            Log.d(TAG, "Restored state from cache $cacheId to $targetPath")
            stateOwnershipTracker.record(
                statePath = targetPath,
                emulatorId = entity.emulatorId,
                contentHash = calculateFileHash(targetFile),
                gameId = entity.gameId,
                slotNumber = entity.slotNumber,
                channelName = entity.channelName,
                coreId = entity.coreId,
                ownerUserIdOverride = entity.ownerUserId
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore state from cache", e)
            false
        }
    }

    /**
     * Hash of the bytes currently at a live state path, or null when nothing is there.
     *
     * Teardown compares this against the archive twice: once to prove the archive captured what
     * was on disk, and once immediately before the delete, so a state written between the two is
     * left alone instead of removed with no copy anywhere.
     */
    suspend fun calculateLiveStateHash(statePath: String): String? = withContext(Dispatchers.IO) {
        val file = File(statePath)
        if (!file.exists() || !file.isFile) return@withContext null
        try {
            calculateFileHash(file)
        } catch (e: Exception) {
            Log.w(TAG, "Could not hash live state $statePath", e)
            null
        }
    }

    /**
     * Reads the cached copy of [cacheId] back off disk and re-derives its hash.
     *
     * An archive is only evidence that the live bytes are safe to remove once it has been read
     * back and matched: a write that reported success but landed truncated or against a full
     * volume is indistinguishable from a good one without re-reading it.
     */
    suspend fun verifyCachedState(cacheId: Long, expectedHash: String?): Boolean =
        withContext(Dispatchers.IO) {
            if (expectedHash.isNullOrBlank()) return@withContext false
            val entity = stateCacheDao.getById(cacheId) ?: return@withContext false
            val file = File(cacheBaseDir, entity.cachePath)
            if (!file.exists() || file.length() == 0L) return@withContext false
            try {
                val actual = calculateFileHash(file)
                val match = actual == expectedHash
                if (!match) {
                    Log.e(TAG, "State archive verify failed for cache $cacheId: expected=$expectedHash, actual=$actual")
                }
                match
            } catch (e: Exception) {
                Log.e(TAG, "State archive verify threw for cache $cacheId", e)
                false
            }
        }

    suspend fun reassignCacheOwner(cacheId: Long, ownerUserId: Long?) =
        withContext(Dispatchers.IO) { stateCacheDao.updateOwner(cacheId, ownerUserId) }

    /**
     * Removes a live state file and its screenshot sidecar. Returns false when the state file was
     * there and could not be deleted, so a caller can leave the ownership row alone.
     */
    suspend fun deleteLiveState(statePath: String, emulatorId: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(statePath)
        val removed = !file.exists() || file.delete()
        StatePathRegistry.screenshotFileFor(emulatorId, file).takeIf { it.exists() }?.delete()
        removed
    }

    suspend fun validateCoreVersion(
        cacheId: Long,
        currentCoreId: String?,
        currentVersion: String?
    ): VersionValidationResult = withContext(Dispatchers.IO) {
        val entity = stateCacheDao.getById(cacheId)
        if (entity == null) {
            return@withContext VersionValidationResult.Unknown
        }

        if (entity.coreId != null && currentCoreId != null && entity.coreId != currentCoreId) {
            return@withContext VersionValidationResult.Mismatch(
                savedVersion = "${entity.coreId}:${entity.coreVersion ?: "unknown"}",
                currentVersion = "$currentCoreId:${currentVersion ?: "unknown"}"
            )
        }

        coreVersionExtractor.validateVersion(entity.coreVersion, currentVersion)
    }

    suspend fun deleteState(cacheId: Long) = withContext(Dispatchers.IO) {
        val entity = stateCacheDao.getById(cacheId) ?: return@withContext

        val cacheFile = File(cacheBaseDir, entity.cachePath)
        val parentDir = cacheFile.parentFile
        cacheFile.delete()
        entity.screenshotPath?.let { File(cacheBaseDir, it).delete() }
        if (parentDir?.listFiles()?.isEmpty() == true) {
            parentDir.delete()
        }

        stateCacheDao.deleteById(cacheId)
        Log.d(TAG, "Deleted state cache $cacheId")
    }

    /**
     * Removes a state the user asked to be rid of, wherever it lives: the cache row and its files,
     * and the server copy. The tombstone is written first and kept either way, so a server delete
     * that never lands cannot bring the state back on the next sync.
     */
    suspend fun purgeState(gameId: Long, cacheId: Long?, serverStateId: Long?) {
        cacheId?.let { deleteState(it) }
        if (serverStateId == null) return
        tombstoneServerState(gameId, serverStateId)

        val api = saveSyncApiClient.getApi()
        if (api == null) {
            Log.w(TAG, "No API to delete server state $serverStateId; tombstoned locally")
            return
        }
        try {
            val response = api.deleteStates(RomMDeleteStatesRequest(states = listOf(serverStateId)))
            if (response.isSuccessful) {
                Log.i(TAG, "Deleted server state $serverStateId")
            } else {
                Log.w(TAG, "Server refused delete of state $serverStateId: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete server state $serverStateId", e)
        }
    }

    suspend fun tombstoneServerState(gameId: Long, rommSaveId: Long) = withContext(Dispatchers.IO) {
        stateTombstoneDao.insert(
            StateTombstoneEntity(
                gameId = gameId,
                rommSaveId = rommSaveId,
                createdAt = Instant.now(),
                ownerUserId = syncPreferencesRepository.getRommUserId()
            )
        )
        Log.d(TAG, "Tombstoned server state $rommSaveId for game $gameId")
    }

    suspend fun clearStateTombstone(rommSaveId: Long) = withContext(Dispatchers.IO) {
        stateTombstoneDao.deleteByServerId(rommSaveId, syncPreferencesRepository.getRommUserId())
    }

    suspend fun getStateTombstones(gameId: Long): Set<Long> = withContext(Dispatchers.IO) {
        stateTombstoneDao.getServerIdsForGame(gameId, syncPreferencesRepository.getRommUserId()).toSet()
    }

    suspend fun pruneStateTombstones(gameId: Long, presentServerIds: Collection<Long>) = withContext(Dispatchers.IO) {
        val ownerUserId = syncPreferencesRepository.getRommUserId()
        val present = presentServerIds.toSet()
        val stale = stateTombstoneDao.getServerIdsForGame(gameId, ownerUserId).filter { it !in present }
        if (stale.isNotEmpty()) {
            stateTombstoneDao.deleteByServerIds(stale, ownerUserId)
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getStatesForGame(gameId: Long): Flow<List<StateCacheEntity>> =
        syncPreferencesRepository.preferences
            .map { it.rommUserId }
            .distinctUntilChanged()
            .flatMapLatest { stateCacheDao.observeByGame(gameId, it) }

    suspend fun getStatesForGameOnce(gameId: Long): List<StateCacheEntity> =
        stateCacheDao.getByGame(gameId, syncPreferencesRepository.getRommUserId())

    /**
     * Duplicates a cached state into another slot of the same game, channel and core, replacing
     * whatever occupied that slot. The copy is a new local row queued for upload; the replaced
     * slot's server state is tombstoned so it cannot come back on the next sync and resurrect the
     * state the copy was meant to overwrite.
     */
    suspend fun copyStateToSlot(cacheId: Long, targetSlot: Int): Boolean = withContext(Dispatchers.IO) {
        val source = stateCacheDao.getById(cacheId) ?: return@withContext false
        if (source.slotNumber == targetSlot) return@withContext false
        val sourceFile = getCacheFile(source) ?: return@withContext false

        val ownerUserId = source.ownerUserId ?: syncPreferencesRepository.getRommUserId()
        val occupant = stateCacheDao.getBySlotAndCore(
            gameId = source.gameId,
            emulatorId = source.emulatorId,
            slotNumber = targetSlot,
            channelName = source.channelName,
            coreId = source.coreId,
            ownerUserId = ownerUserId
        )
        occupant?.let { existing ->
            purgeState(source.gameId, existing.id, existing.rommSaveId)
        }

        val coreDir = coreDirFor(source)
        val targetName = StatePathRegistry.serialPatternFor(source.emulatorId)?.withSlot(sourceFile.name, targetSlot)
            ?: stateFileNameForSlot(sourceFile.name, targetSlot)
        val targetFile = File(coreDir, targetName)
        if (targetFile.absolutePath == sourceFile.absolutePath) return@withContext false

        return@withContext try {
            coreDir.mkdirs()
            sourceFile.copyTo(targetFile, overwrite = true)

            val relativeDir = source.cachePath.substringBeforeLast('/')
            var targetScreenshotPath: String? = null
            getScreenshotFile(source)?.let { shot ->
                val targetShot = StatePathRegistry.screenshotFileFor(source.emulatorId, targetFile)
                shot.copyTo(targetShot, overwrite = true)
                targetScreenshotPath = "$relativeDir/${targetShot.name}"
            }

            stateCacheDao.upsert(
                source.copy(
                    id = 0,
                    slotNumber = targetSlot,
                    cachedAt = Instant.now(),
                    stateSize = targetFile.length(),
                    cachePath = "$relativeDir/$targetName",
                    screenshotPath = targetScreenshotPath,
                    rommSaveId = null,
                    syncStatus = StateCacheEntity.STATUS_PENDING_UPLOAD,
                    serverUpdatedAt = null,
                    lastUploadedHash = null,
                    ownerUserId = ownerUserId
                )
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy state $cacheId into slot $targetSlot", e)
            targetFile.delete()
            false
        }
    }

    private fun stateFileNameForSlot(sourceName: String, targetSlot: Int): String {
        val stem = sourceName
            .removeSuffix(".auto")
            .replace(Regex("""\.state\d*$"""), "")
        val suffix = if (targetSlot < 0) ".state.auto" else ".state$targetSlot"
        return "$stem$suffix"
    }

    /**
     * Where a channel's states for one core are cached, relative to the cache root.
     *
     * The channel name is a path segment, so it is part of a state's address and not only a column
     * on its row. Anything that changes which channel a state belongs to has to move the file too.
     */
    private fun channelRelativeDir(cachePath: String, coreId: String?, targetChannel: String): String {
        val gameRelativeDir = cachePath
            .substringBeforeLast('/')
            .substringBeforeLast('/')
            .substringBeforeLast('/')
        return "$gameRelativeDir/$targetChannel/${coreId ?: UNKNOWN_CORE_DIR}"
    }

    /**
     * Carries a channel's states over to a new name, files and rows together.
     *
     * Renaming a channel without this strands its states under a name nothing will ask for again.
     * A state whose file has already gone still has its row moved: the row is what the slot list
     * reads, so leaving it behind would show the state under the old name forever.
     */
    suspend fun moveStatesToChannel(
        gameId: Long,
        sourceChannel: String?,
        targetChannel: String
    ): Int = withContext(Dispatchers.IO) {
        if (sourceChannel == targetChannel) return@withContext 0
        val ownerUserId = syncPreferencesRepository.getRommUserId()
        val sourceStates = if (sourceChannel != null) {
            stateCacheDao.getByChannel(gameId, sourceChannel, ownerUserId)
        } else {
            stateCacheDao.getDefaultChannel(gameId, ownerUserId)
        }
        if (sourceStates.isEmpty()) return@withContext 0

        var moved = 0
        for (source in sourceStates) {
            val targetRelativeDir = channelRelativeDir(source.cachePath, source.coreId, targetChannel)
            File(cacheBaseDir, targetRelativeDir).mkdirs()

            val sourceFile = File(cacheBaseDir, source.cachePath)
            val targetCachePath = "$targetRelativeDir/${sourceFile.name}"
            if (sourceFile.exists()) {
                sourceFile.copyTo(File(cacheBaseDir, targetCachePath), overwrite = true)
                sourceFile.delete()
            }

            val targetScreenshotPath = source.screenshotPath?.let { screenshot ->
                val screenshotFile = File(cacheBaseDir, screenshot)
                val movedPath = "$targetRelativeDir/${screenshotFile.name}"
                if (screenshotFile.exists()) {
                    screenshotFile.copyTo(File(cacheBaseDir, movedPath), overwrite = true)
                    screenshotFile.delete()
                }
                movedPath
            }

            stateCacheDao.update(
                source.copy(
                    channelName = targetChannel,
                    cachePath = targetCachePath,
                    screenshotPath = targetScreenshotPath
                )
            )
            moved++
        }
        Log.d(TAG, "Moved $moved states from ${sourceChannel ?: "default"} to $targetChannel for game $gameId")
        moved
    }

    suspend fun getStatesForChannel(gameId: Long, channelName: String): List<StateCacheEntity> =
        stateCacheDao.getByChannel(gameId, channelName, syncPreferencesRepository.getRommUserId())

    suspend fun getDefaultChannelStates(gameId: Long): List<StateCacheEntity> =
        stateCacheDao.getDefaultChannel(gameId, syncPreferencesRepository.getRommUserId())

    suspend fun getStateBySlot(
        gameId: Long,
        emulatorId: String,
        slotNumber: Int,
        channelName: String?,
        coreId: String?
    ): StateCacheEntity? = stateCacheDao.getBySlotAndCore(
        gameId,
        emulatorId,
        slotNumber,
        channelName,
        coreId,
        syncPreferencesRepository.getRommUserId()
    )

    suspend fun getStateById(cacheId: Long): StateCacheEntity? =
        stateCacheDao.getById(cacheId)

    fun getScreenshotPath(entity: StateCacheEntity): String? {
        val relativePath = entity.screenshotPath ?: return null
        return File(cacheBaseDir, relativePath).absolutePath
    }

    /**
     * Trims one account's state history for a game. The budget is per account, so an account
     * filling its slots cannot evict another's states for the same rom, and the rows selected for
     * deletion are named by id rather than re-derived so the delete cannot widen past them.
     */
    suspend fun pruneOldCaches(gameId: Long, ownerUserId: Long?) = withContext(Dispatchers.IO) {
        val prefs = preferencesRepository.userPreferences.first()
        val limit = prefs.saveCacheLimit

        val totalCount = stateCacheDao.countByGameAndOwner(gameId, ownerUserId)
        if (totalCount <= limit) return@withContext

        val lockedCount = stateCacheDao.countLockedByGameAndOwner(gameId, ownerUserId)
        val effectiveLimit = maxOf(limit, lockedCount + MIN_UNLOCKED_SLOTS)

        val toDeleteCount = totalCount - effectiveLimit
        if (toDeleteCount <= 0) return@withContext

        val toDelete = stateCacheDao.getOldestUnlockedForOwner(gameId, ownerUserId, toDeleteCount)
        if (toDelete.isEmpty()) return@withContext
        for (entity in toDelete) {
            val cacheFile = File(cacheBaseDir, entity.cachePath)
            cacheFile.delete()
            entity.screenshotPath?.let { File(cacheBaseDir, it).delete() }
            val parentDir = cacheFile.parentFile
            if (parentDir?.listFiles()?.isEmpty() == true) {
                parentDir.delete()
            }
        }

        stateCacheDao.deleteByIds(toDelete.map { it.id })
        Log.d(TAG, "Pruned ${toDelete.size} old state caches for game $gameId owner $ownerUserId (files cleaned)")
    }

    /**
     * Deletes the cached states the server also holds and keeps every state that exists only on
     * this device. Returns the number of states kept.
     */
    suspend fun deleteServerHeldStatesForGame(gameId: Long): Int = withContext(Dispatchers.IO) {
        val (localOnly, serverHeld) = stateCacheDao.getAllByGameForTeardown(gameId)
            .partition { it.rommSaveId == null || it.syncStatus in LOCAL_ONLY_STATUSES }
        for (cache in serverHeld) {
            val cacheFile = File(cacheBaseDir, cache.cachePath)
            val parentDir = cacheFile.parentFile
            cacheFile.delete()
            if (parentDir?.listFiles()?.isEmpty() == true) {
                parentDir.delete()
            }
        }
        stateCacheDao.deleteByIds(serverHeld.map { it.id })

        if (localOnly.isEmpty()) {
            stateOwnershipTracker.clearForGame(gameId)
            stateTombstoneDao.deleteByGame(gameId)
            val gameDir = File(cacheBaseDir, gameId.toString())
            if (gameDir.exists() && gameDir.isDirectory) {
                gameDir.deleteRecursively()
            }
        }

        Log.d(TAG, "Deleted ${serverHeld.size} server-held cached states for game $gameId, kept ${localOnly.size} local-only")
        localOnly.size
    }

    /**
     * Where a cached state for [slotNumber] goes live, or null when no location or no name can be
     * proven to belong to the game. [cachedFileName] is the name the state was cached under.
     */
    suspend fun buildStateTargetPath(
        config: StatePathConfig,
        platformId: String,
        key: StateNameKey,
        slotNumber: Int,
        cachedFileName: String?,
        emulatorId: String? = null,
        coreName: String? = null,
        romPath: String? = null,
        gameId: Long? = null,
        cachedFile: File? = null,
    ): String? {
        if (emulatorId == EmulatorRegistry.BUILTIN_ID && gameId != null) {
            val baseDir = libretroStatePathResolver.liveStateBaseDir(gameId)
            return libretroStatePathResolver.liveStateFile(baseDir, key.romBaseName, slotNumber).absolutePath
        }
        val placedKey = if (cachedFile != null) {
            withContext(Dispatchers.IO) {
                provenStateKey(config.slotPattern, key, cachedFile)
            }
        } else {
            key
        }
        if (placedKey == null) {
            Log.w(TAG, "State ${cachedFile?.name} carries no serial of this game in its header; not placed")
            return null
        }
        val fileName = config.slotPattern.targetFileName(placedKey, slotNumber, cachedFileName)
        if (fileName == null) {
            Log.w(TAG, "No state name belongs to this game for slot $slotNumber (cached as $cachedFileName)")
            return null
        }
        val paths = if (emulatorId != null &&
            com.nendo.argosy.data.emulator.RetroArchPathResolver.isRetroArch(emulatorId)
        ) {
            val req = com.nendo.argosy.data.emulator.RetroArchPathResolver.Request(
                emulatorId = emulatorId,
                coreName = coreName,
                romPath = romPath,
            )
            retroArchPathResolver.resolveStateDirectories(req)
        } else {
            StatePathRegistry.resolvePath(config, platformId)
        }
        val baseDir = paths.firstOrNull { File(it).exists() } ?: paths.firstOrNull() ?: return null
        return "$baseDir/$fileName"
    }

    /**
     * The directory an existing cache row's files live in, read off the row rather than rebuilt.
     * Rows written before the cache was partitioned per account carry no owner segment, so
     * reconstructing the path from its parts would miss them.
     */
    fun coreDirFor(entity: StateCacheEntity): File =
        File(cacheBaseDir, entity.cachePath).parentFile ?: cacheBaseDir

    /**
     * Whether a path names a live state rather than one already inside the cache.
     *
     * [cacheState] is also called cache-to-cache (replacing the auto slot with a numbered one),
     * and an ownership row for a cache path would make an account switch archive and then delete
     * the account's own cache file.
     */
    private fun isLiveStatePath(statePath: String): Boolean =
        !statePath.startsWith(cacheBaseDir.absolutePath)

    private fun stateRelativeDir(
        ownerUserId: Long?,
        gameId: Long,
        platformSlug: String,
        channelName: String?,
        coreId: String?
    ): String {
        val channelDirName = channelName ?: "default"
        val coreDirName = coreId ?: UNKNOWN_CORE_DIR
        return "${AppPaths.ownerCacheSegment(ownerUserId)}$platformSlug/$gameId/$channelDirName/$coreDirName"
    }

    fun getCacheFile(entity: StateCacheEntity): File? {
        val file = File(cacheBaseDir, entity.cachePath)
        return if (file.exists()) file else null
    }

    fun getScreenshotFile(entity: StateCacheEntity): File? {
        val relativePath = entity.screenshotPath ?: return null
        val file = File(cacheBaseDir, relativePath)
        return if (file.exists()) file else null
    }

    /**
     * Puts the cached screenshot beside a restored live state under the sidecar name its emulator
     * reads, or removes a stale one so it cannot caption different content.
     */
    fun restoreScreenshot(entity: StateCacheEntity, liveStateFile: File) {
        val target = StatePathRegistry.screenshotFileFor(entity.emulatorId, liveStateFile)
        try {
            val cached = getScreenshotFile(entity)
            if (cached != null) {
                cached.copyTo(target, overwrite = true)
            } else {
                target.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not place the state screenshot at ${target.absolutePath}: ${e.message}")
        }
    }

    suspend fun deleteStatesForChannel(gameId: Long, channelName: String?) = withContext(Dispatchers.IO) {
        val ownerUserId = syncPreferencesRepository.getRommUserId()
        val states = if (channelName != null) {
            stateCacheDao.getByChannel(gameId, channelName, ownerUserId)
        } else {
            stateCacheDao.getDefaultChannel(gameId, ownerUserId)
        }

        for (state in states) {
            val cacheFile = File(cacheBaseDir, state.cachePath)
            cacheFile.delete()
            state.screenshotPath?.let { File(cacheBaseDir, it).delete() }
        }

        stateCacheDao.deleteByChannel(gameId, channelName, ownerUserId)
        Log.d(TAG, "Deleted ${states.size} states for game $gameId channel ${channelName ?: "default"}")
    }

    suspend fun deleteStatesForChannelAndCore(gameId: Long, channelName: String?, coreId: String?) = withContext(Dispatchers.IO) {
        val ownerUserId = syncPreferencesRepository.getRommUserId()
        val states = stateCacheDao.getByChannelAndCore(gameId, channelName, coreId, ownerUserId)

        for (state in states) {
            val cacheFile = File(cacheBaseDir, state.cachePath)
            cacheFile.delete()
            state.screenshotPath?.let { File(cacheBaseDir, it).delete() }
        }

        stateCacheDao.deleteByChannelAndCore(gameId, channelName, coreId, ownerUserId)
        Log.d(TAG, "Deleted ${states.size} states for game $gameId channel ${channelName ?: "default"} core ${coreId ?: "unknown"}")
    }

    suspend fun getStatesForChannelAndCore(gameId: Long, channelName: String?, coreId: String?): List<StateCacheEntity> =
        stateCacheDao.getByChannelAndCore(gameId, channelName, coreId, syncPreferencesRepository.getRommUserId())

    suspend fun deleteAutoResumeStatesFromDisk(
        emulatorId: String,
        romPath: String,
        platformSlug: String,
        emulatorPackage: String?,
        coreId: String?,
        serials: Set<String>,
        gameId: Long? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        val files = autoResumeStateFiles(emulatorId, romPath, platformSlug, coreId, serials, gameId)
            ?: return@withContext false
        deleteLiveStates(files, romPath, emulatorId)
    }

    /**
     * Deletes the auto-resume states of [romPath] last written before [cutoff], leaving any
     * written at or after it. True when one was deleted.
     */
    suspend fun deleteAutoResumeStatesOlderThan(
        emulatorId: String,
        romPath: String,
        platformSlug: String,
        coreId: String?,
        serials: Set<String>,
        gameId: Long?,
        cutoff: java.time.Instant,
    ): Boolean = withContext(Dispatchers.IO) {
        val files = autoResumeStateFiles(emulatorId, romPath, platformSlug, coreId, serials, gameId)
            ?: return@withContext false
        deleteLiveStates(files.filter { it.lastModified() < cutoff.toEpochMilli() }, romPath, emulatorId)
    }

    private fun deleteLiveStates(files: List<File>, romPath: String, emulatorId: String): Boolean {
        var deletedAny = false
        for (file in files) {
            if (displaceLiveState(file, emulatorId)) deletedAny = true
        }
        if (!deletedAny) Log.d(TAG, "deleteLiveStates: nothing to delete for ${File(romPath).name}")
        return deletedAny
    }

    /**
     * Moves a live state and its screenshot out of the emulator's folder into the displaced-state
     * store, where it stays for [DISPLACED_RETENTION_DAYS] days. False when nothing was moved.
     */
    fun displaceLiveState(file: File, emulatorId: String): Boolean {
        if (!file.exists()) return false
        val displacedDir = File(cacheBaseDir, "$DISPLACED_DIR/${System.currentTimeMillis()}")
        return try {
            displacedDir.mkdirs()
            val screenshot = StatePathRegistry.screenshotFileFor(emulatorId, file)
            file.copyTo(File(displacedDir, file.name), overwrite = true)
            if (screenshot.exists()) screenshot.copyTo(File(displacedDir, screenshot.name), overwrite = true)
            file.delete()
            screenshot.delete()
            Log.d(TAG, "Displaced live state ${file.absolutePath} to ${displacedDir.absolutePath}")
            pruneDisplacedStates()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not displace ${file.absolutePath}, leaving it in place", e)
            false
        }
    }

    private fun pruneDisplacedStates() {
        val cutoff = System.currentTimeMillis() - DISPLACED_RETENTION_DAYS * 24 * 60 * 60 * 1000L
        File(cacheBaseDir, DISPLACED_DIR).listFiles()
            ?.filter { it.isDirectory && (it.name.toLongOrNull() ?: Long.MAX_VALUE) < cutoff }
            ?.forEach { it.deleteRecursively() }
    }

    private suspend fun autoResumeStateFiles(
        emulatorId: String,
        romPath: String,
        platformSlug: String,
        coreId: String?,
        serials: Set<String>,
        gameId: Long?,
    ): List<File>? {
        val config = StatePathRegistry.getConfig(emulatorId)
        if (config == null) {
            Log.d(TAG, "autoResumeStateFiles: No state config for emulator: $emulatorId")
            return null
        }

        val romFile = File(romPath)
        val romBaseNames = if (emulatorId == EmulatorRegistry.BUILTIN_ID) {
            com.nendo.argosy.data.emulator.ArchiveRomNaming.candidateBaseNames(romFile, platformSlug)
        } else {
            listOf(romFile.nameWithoutExtension)
        }

        val userStateOverride = emulatorSaveConfigDao.getByEmulator(emulatorId)
            ?.takeIf { it.isUserStateOverride }
            ?.statePathPattern

        val statePaths = when {
            com.nendo.argosy.data.emulator.RetroArchPathResolver.isRetroArch(emulatorId) -> {
                val req = com.nendo.argosy.data.emulator.RetroArchPathResolver.Request(
                    emulatorId = emulatorId,
                    coreName = coreId,
                    romPath = romPath,
                )
                retroArchPathResolver.resolveStateDirectories(req)
            }
            userStateOverride != null -> listOf(userStateOverride)
            emulatorId == EmulatorRegistry.BUILTIN_ID && gameId != null ->
                listOf(libretroStatePathResolver.liveStateBaseDir(gameId).absolutePath)
            else -> StatePathRegistry.resolvePath(config, platformSlug)
        }

        val fileNames = romBaseNames.flatMap { romBaseName ->
            config.slotPattern.resumeFileNames(StateNameKey(romBaseName, serials))
        }

        return statePaths.map(::File).filter { it.exists() }.flatMap { stateDir ->
            fileNames.map { File(stateDir, it) }.filter { it.exists() }
        }
    }

    /** Deletes all state cache rows AND files; returns false when blocked by an active session. */
    suspend fun clearAllCache(): Boolean = withContext(Dispatchers.IO) {
        if (sessionStateStore.hasActiveSession()) {
            Log.w(TAG, "clearAllCache blocked: active game session")
            return@withContext false
        }
        pendingSyncQueueDao.deleteBySyncType(SyncType.SAVE_STATE)
        stateCacheDao.deleteAll()
        stateTombstoneDao.deleteAll()
        if (cacheBaseDir.exists()) {
            cacheBaseDir.deleteRecursively()
            cacheBaseDir.mkdirs()
        }
        Log.d(TAG, "Cleared all state cache")
        attributionRepository.markDirty(StorageCategory.SAVE_STATE_CACHE)
        true
    }

    sealed class StateCloudResult {
        data object Success : StateCloudResult()
        data object NotConfigured : StateCloudResult()
        data object NoStateFound : StateCloudResult()
        data object AlreadySynced : StateCloudResult()
        data class Conflict(val localTime: Instant, val serverTime: Instant) : StateCloudResult()
        data class Error(val message: String) : StateCloudResult()
        data class OtherCoreFormat(val writerCore: String?) : StateCloudResult()
    }

    suspend fun uploadStateToRomM(
        state: StateCacheEntity,
        rommId: Long,
        romBaseName: String,
        api: RomMApi
    ): StateCloudResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "[StateSync] UPLOAD stateId=${state.id} gameId=${state.gameId} slot=${state.slotNumber}")

        val cacheFile = getCacheFile(state)
        if (cacheFile == null || !cacheFile.exists()) {
            Log.w(TAG, "[StateSync] UPLOAD stateId=${state.id} | Cache file not found")
            return@withContext StateCloudResult.NoStateFound
        }

        try {
            val contentHash = calculateFileHash(cacheFile)
            if (state.lastUploadedHash == contentHash) {
                Log.d(TAG, "[StateSync] UPLOAD stateId=${state.id} | Skipped - content unchanged (hash=$contentHash)")
                if (state.rommSaveId != null) stateCacheDao.updateSyncStatus(state.id, StateCacheEntity.STATUS_SYNCED)
                return@withContext StateCloudResult.AlreadySynced
            }

            val uploadFileName = buildUploadFileName(state, romBaseName)

            fun buildStatePart(): MultipartBody.Part {
                val requestBody = cacheFile.asRequestBody("application/octet-stream".toMediaType())
                return MultipartBody.Part.createFormData("stateFile", uploadFileName, requestBody)
            }

            fun buildScreenshotPart(stateName: String): MultipartBody.Part? = getScreenshotFile(state)?.let { ssFile ->
                val ssBody = ssFile.asRequestBody("image/png".toMediaType())
                MultipartBody.Part.createFormData("screenshotFile", "$stateName.png", ssBody)
            }

            val firstResponse = if (state.rommSaveId != null) {
                val serverName = if (getScreenshotFile(state) != null) {
                    runCatching { api.getState(state.rommSaveId).body()?.fileName }.getOrNull()
                } else {
                    null
                }
                api.updateState(
                    state.rommSaveId,
                    stateFile = buildStatePart(),
                    screenshotFile = buildScreenshotPart(serverName ?: uploadFileName)
                )
            } else {
                api.uploadState(
                    rommId,
                    serverEmulatorTag(state),
                    stateFile = buildStatePart(),
                    screenshotFile = buildScreenshotPart(uploadFileName)
                )
            }

            val response = if (
                !firstResponse.isSuccessful &&
                firstResponse.code() == HTTP_NOT_FOUND &&
                state.rommSaveId != null
            ) {
                Log.w(
                    TAG,
                    "[StateSync] UPLOAD stateId=${state.id} | Server state ${state.rommSaveId} is gone, re-creating"
                )
                stateCacheDao.updateSyncState(
                    id = state.id,
                    rommSaveId = null,
                    syncStatus = StateCacheEntity.STATUS_PENDING_UPLOAD,
                    serverUpdatedAt = null,
                    lastUploadedHash = null
                )
                api.uploadState(
                    rommId,
                    serverEmulatorTag(state),
                    stateFile = buildStatePart(),
                    screenshotFile = buildScreenshotPart(uploadFileName)
                )
            } else {
                firstResponse
            }

            if (response.isSuccessful) {
                val serverState = response.body()
                if (serverState == null) {
                    Log.e(TAG, "[StateSync] UPLOAD stateId=${state.id} | Empty response")
                    return@withContext StateCloudResult.Error("Empty response from server")
                }

                val serverTimestamp = SaveSyncApiClient.parseTimestampOrNull(serverState.updatedAt)
                stateCacheDao.updateSyncState(
                    id = state.id,
                    rommSaveId = serverState.id,
                    syncStatus = StateCacheEntity.STATUS_SYNCED,
                    serverUpdatedAt = serverTimestamp?.toEpochMilli(),
                    lastUploadedHash = contentHash
                )

                Log.i(TAG, "[StateSync] UPLOAD stateId=${state.id} | Complete | serverStateId=${serverState.id}")
                StateCloudResult.Success
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "[StateSync] UPLOAD stateId=${state.id} | HTTP failed | status=${response.code()}, body=$errorBody")
                StateCloudResult.Error("Upload failed: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[StateSync] UPLOAD stateId=${state.id} | Exception", e)
            StateCloudResult.Error(e.message ?: "Upload failed")
        }
    }

    suspend fun downloadStateFromRomM(
        rommStateId: Long,
        fileName: String,
        api: RomMApi,
        gameId: Long,
        platformSlug: String,
        emulatorId: String,
        coreId: String? = null,
        serverState: RomMState,
        readableByCore: String? = null
    ): StateCloudResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "[StateSync] DOWNLOAD rommStateId=$rommStateId gameId=$gameId")
        val ownerUserId = syncPreferencesRepository.getRommUserId()

        try {
            val downloadPath = serverState.downloadPath
            if (downloadPath == null) {
                Log.e(TAG, "[StateSync] DOWNLOAD rommStateId=$rommStateId | No download path")
                return@withContext StateCloudResult.Error("No download path for state")
            }
            val response = api.downloadRaw(downloadPath)
            if (!response.isSuccessful) {
                Log.e(TAG, "[StateSync] DOWNLOAD rommStateId=$rommStateId | HTTP failed | status=${response.code()}")
                return@withContext StateCloudResult.Error("Download failed: ${response.code()}")
            }

            val body = response.body()
            if (body == null) {
                Log.e(TAG, "[StateSync] DOWNLOAD rommStateId=$rommStateId | Empty response body")
                return@withContext StateCloudResult.Error("Empty response body")
            }

            val parsed = parseStateFileName(fileName)
            val channelName = parsed.channelName
            val parsedSlot = parsed.slotNumber

            val relativeDir = stateRelativeDir(ownerUserId, gameId, platformSlug, channelName, coreId)
            val coreDir = File(cacheBaseDir, relativeDir)
            coreDir.mkdirs()

            val cachedFile = File(coreDir, fileName)
            body.byteStream().use { input ->
                cachedFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            val header = LibretroStateFormat.headerOf(cachedFile)
            if (!LibretroStateFormat.readableBy(readableByCore, header)) {
                cachedFile.delete()
                val writer = LibretroStateFormat.writerOf(header)
                Log.w(
                    TAG,
                    "[StateSync] DOWNLOAD rommStateId=$rommStateId | written by ${writer ?: "another core"}, " +
                        "$readableByCore cannot load it; not cached"
                )
                return@withContext StateCloudResult.OtherCoreFormat(writer)
            }

            var screenshotCachePath: String? = null
            val screenshot = serverState?.screenshot
            if (screenshot?.downloadPath != null && screenshot.fileName != null) {
                try {
                    val ssResponse = api.downloadRaw(screenshot.downloadPath)
                    if (ssResponse.isSuccessful) {
                        val ssFile = File(coreDir, screenshot.fileName)
                        ssResponse.body()?.byteStream()?.use { input ->
                            ssFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        screenshotCachePath = "$relativeDir/${screenshot.fileName}"
                        Log.d(TAG, "[StateSync] DOWNLOAD screenshot for rommStateId=$rommStateId")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "[StateSync] DOWNLOAD screenshot failed for rommStateId=$rommStateId", e)
                }
            }

            val contentHash = calculateFileHash(cachedFile)
            val cachePath = "$relativeDir/$fileName"
            val now = Instant.now()

            val existing = stateCacheDao.getBySlotAndCore(
                gameId = gameId,
                emulatorId = emulatorId,
                slotNumber = parsedSlot,
                channelName = channelName,
                coreId = coreId,
                ownerUserId = ownerUserId
            )

            val entity = StateCacheEntity(
                id = existing?.id ?: 0,
                gameId = gameId,
                platformSlug = platformSlug,
                emulatorId = emulatorId,
                slotNumber = parsedSlot,
                channelName = channelName,
                cachedAt = now,
                stateSize = cachedFile.length(),
                cachePath = cachePath,
                screenshotPath = screenshotCachePath,
                coreId = coreId,
                coreVersion = existing?.coreVersion,
                isLocked = existing?.isLocked ?: false,
                note = existing?.note,
                rommSaveId = rommStateId,
                syncStatus = StateCacheEntity.STATUS_SYNCED,
                serverUpdatedAt = SaveSyncApiClient.parseTimestampOrNull(serverState.updatedAt) ?: now,
                lastUploadedHash = contentHash,
                ownerUserId = existing?.ownerUserId ?: ownerUserId
            )

            stateCacheDao.upsert(entity)
            Log.i(TAG, "[StateSync] DOWNLOAD rommStateId=$rommStateId | Complete | cached at $cachePath")
            StateCloudResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "[StateSync] DOWNLOAD rommStateId=$rommStateId | Exception", e)
            StateCloudResult.Error(e.message ?: "Download failed")
        }
    }

    /**
     * Drops a cached state's link to a server object that is no longer there, leaving the cached
     * file untouched and queued to be re-created. A server rom's own state list is the authority
     * on which ids are live, so a link missing from it can never be repaired by retrying.
     */
    suspend fun clearServerLink(stateId: Long) = withContext(Dispatchers.IO) {
        stateCacheDao.updateSyncState(
            id = stateId,
            rommSaveId = null,
            syncStatus = StateCacheEntity.STATUS_PENDING_UPLOAD,
            serverUpdatedAt = null,
            lastUploadedHash = null
        )
    }

    /**
     * Detaches this game's cached states whose name carries a disc serial outside [serials],
     * keeping row and file, and tombstones their server copies for the game. Returns the count.
     */
    suspend fun detachForeignSerialStates(gameId: Long, serials: Set<String>): Int = withContext(Dispatchers.IO) {
        if (serials.isEmpty()) return@withContext 0
        var detached = 0
        for (row in stateCacheDao.getByGame(gameId, syncPreferencesRepository.getRommUserId())) {
            val pattern = StatePathRegistry.serialPatternFor(row.emulatorId) ?: continue
            val name = File(row.cachePath).name
            if (pattern.serialOf(name) == null || pattern.ownsSerial(name, serials)) continue
            if (stateCacheDao.detachFromGame(row.id) == 0) {
                Log.w(TAG, "Could not detach foreign state ${row.id} ($name) from game $gameId")
                continue
            }
            row.rommSaveId?.let { tombstoneServerState(gameId, it) }
            detached++
            Log.i(TAG, "Detached state ${row.id} ($name) from game $gameId: its serial is not one of $serials")
        }
        detached
    }

    suspend fun checkServerStates(rommId: Long, api: RomMApi): List<RomMState> = withContext(Dispatchers.IO) {
        try {
            val response = api.getStatesByRom(rommId)
            if (!response.isSuccessful) {
                Log.w(TAG, "[StateSync] checkServerStates rommId=$rommId | HTTP failed | status=${response.code()}")
                return@withContext emptyList()
            }

            val states = response.body() ?: emptyList()
            Log.d(TAG, "[StateSync] checkServerStates rommId=$rommId | Found ${states.size} states")
            states
        } catch (e: Exception) {
            Log.e(TAG, "[StateSync] checkServerStates rommId=$rommId | Exception", e)
            emptyList()
        }
    }

    suspend fun validateAllSaveStates(api: RomMApi): Int = withContext(Dispatchers.IO) {
        Log.i(TAG, "[StateMigrate] Validating save states across all ROMs")

        val response = try {
            api.getAllSaves()
        } catch (e: Exception) {
            Log.w(TAG, "[StateMigrate] Failed to fetch all saves", e)
            return@withContext 0
        }

        if (!response.isSuccessful) {
            Log.w(TAG, "[StateMigrate] Failed to fetch all saves: ${response.code()}")
            return@withContext 0
        }

        val allSaves = response.body() ?: return@withContext 0
        val stateSlotPattern = Regex("""^state_(\w+)$""")
        val misplaced = allSaves.filter { it.slot != null && stateSlotPattern.matches(it.slot) }

        if (misplaced.isEmpty()) {
            Log.i(TAG, "[StateMigrate] No misplaced states found")
            return@withContext 0
        }

        Log.i(TAG, "[StateMigrate] Found ${misplaced.size} misplaced states across ${misplaced.map { it.romId }.distinct().size} ROMs")

        val byRom = misplaced.groupBy { it.romId }
        var totalMigrated = 0

        for ((romId, saves) in byRom) {
            val migrated = migrateStatesFromSaves(romId, api, saves)
            totalMigrated += migrated
        }

        val staleChannelPattern = Regex("""^state_|\.state""")
        var cleaned = 0

        val allStateCaches = stateCacheDao.getAll()
        for (cache in allStateCaches) {
            val ch = cache.channelName ?: continue
            if (staleChannelPattern.containsMatchIn(ch)) {
                stateCacheDao.deleteById(cache.id)
                cleaned++
                Log.d(TAG, "[StateMigrate] Cleaned stale state_cache id=${cache.id} channelName=$ch")
            }
        }

        val cleanedSaveCaches = saveCacheDao.deleteStaleStateEntries()
        cleaned += cleanedSaveCaches
        if (cleanedSaveCaches > 0) {
            Log.d(TAG, "[StateMigrate] Cleaned $cleanedSaveCaches stale save_cache entries")
        }

        val cleanedSaveSyncs = saveSyncDao.deleteStaleStateEntries()
        cleaned += cleanedSaveSyncs
        if (cleanedSaveSyncs > 0) {
            Log.d(TAG, "[StateMigrate] Cleaned $cleanedSaveSyncs stale save_sync entries")
        }

        if (cleaned > 0) {
            Log.i(TAG, "[StateMigrate] Cleaned $cleaned total stale entries")
        }

        Log.i(TAG, "[StateMigrate] Validation complete: migrated $totalMigrated, cleaned $cleaned")
        totalMigrated + cleaned
    }

    suspend fun migrateStatesFromSaves(rommId: Long, api: RomMApi): Int =
        migrateStatesFromSaves(rommId, api, saves = null)

    suspend fun migrateStatesFromSaves(rommId: Long, api: RomMApi, saves: List<RomMSave>?): Int = withContext(Dispatchers.IO) {
        val stateSlotPattern = Regex("""^state_(\w+)$""")

        val allSaves = if (saves != null) {
            saves
        } else {
            val savesResponse = api.getSavesByRom(rommId)
            if (!savesResponse.isSuccessful) {
                Log.w(TAG, "[StateMigrate] Failed to fetch saves for rommId=$rommId")
                return@withContext 0
            }
            savesResponse.body() ?: emptyList()
        }

        val misplaced = allSaves.filter { it.slot != null && stateSlotPattern.matches(it.slot) }
        if (misplaced.isEmpty()) return@withContext 0

        Log.i(TAG, "[StateMigrate] Found ${misplaced.size} states in saves for rommId=$rommId")
        var migratedCount = 0

        for (save in misplaced) {
            try {
                val newFileName = buildMigratedFileName(save)
                if (newFileName == null) {
                    Log.d(TAG, "[StateMigrate] Skipping non-state file ${save.id} (${save.fileName})")
                    continue
                }

                val downloadPath = save.downloadPath
                if (downloadPath == null) {
                    Log.w(TAG, "[StateMigrate] No download path for save ${save.id}")
                    continue
                }

                val downloadResponse = api.downloadRaw(downloadPath)
                if (!downloadResponse.isSuccessful) {
                    Log.w(TAG, "[StateMigrate] Failed to download save ${save.id}: ${downloadResponse.code()}")
                    continue
                }

                val body = downloadResponse.body() ?: continue
                val tempFile = File.createTempFile("state_migrate_", ".tmp", cacheBaseDir)
                try {
                    body.byteStream().use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }

                    val requestBody = tempFile.asRequestBody("application/octet-stream".toMediaType())
                    val filePart = MultipartBody.Part.createFormData("stateFile", newFileName, requestBody)

                    val uploadResponse = api.uploadState(
                        rommId,
                        save.emulator?.let { if (it == EmulatorRegistry.LEGACY_BUILTIN_ID) EmulatorRegistry.BUILTIN_ID else it },
                        stateFile = filePart
                    )

                    if (uploadResponse.isSuccessful) {
                        val serverState = uploadResponse.body()
                        api.deleteSaves(RomMDeleteSavesRequest(saves = listOf(save.id)))

                        val oldCache = save.id.let {
                            stateCacheDao.getByRommSaveId(it, syncPreferencesRepository.getRommUserId())
                        }
                        if (oldCache != null && serverState != null) {
                            val serverTimestamp = SaveSyncApiClient.parseTimestampOrNull(serverState.updatedAt)
                            stateCacheDao.updateSyncState(
                                id = oldCache.id,
                                rommSaveId = serverState.id,
                                syncStatus = StateCacheEntity.STATUS_SYNCED,
                                serverUpdatedAt = serverTimestamp?.toEpochMilli(),
                                lastUploadedHash = oldCache.lastUploadedHash
                            )
                        }

                        migratedCount++
                        Log.i(TAG, "[StateMigrate] Migrated save ${save.id} (${save.fileName}) -> $newFileName | newStateId=${serverState?.id}")
                    } else {
                        Log.w(TAG, "[StateMigrate] Upload failed for save ${save.id}: ${uploadResponse.code()}")
                    }
                } finally {
                    tempFile.delete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "[StateMigrate] Error migrating save ${save.id}", e)
            }
        }

        Log.i(TAG, "[StateMigrate] Migrated $migratedCount/${misplaced.size} states for rommId=$rommId")
        migratedCount
    }

    suspend fun markForUpload(stateId: Long) {
        stateCacheDao.updateSyncStatus(stateId, StateCacheEntity.STATUS_PENDING_UPLOAD)
    }

    suspend fun getByRommSaveId(rommSaveId: Long): StateCacheEntity? =
        stateCacheDao.getByRommSaveId(rommSaveId, syncPreferencesRepository.getRommUserId())

    suspend fun getByGameAndEmulator(gameId: Long, emulatorId: String): List<StateCacheEntity> =
        stateCacheDao.getByGameAndEmulator(gameId, emulatorId, syncPreferencesRepository.getRommUserId())

    suspend fun hasSameContent(state: StateCacheEntity, file: File): Boolean = withContext(Dispatchers.IO) {
        val cached = getCacheFile(state) ?: return@withContext false
        cached.length() == file.length() && calculateFileHash(cached) == calculateFileHash(file)
    }

    /**
     * The libretro core [emulatorId] runs [gameId] with: the built-in core the launcher would pick,
     * or the RetroArch core configured for it. Null for standalone emulators and unknown games.
     */
    suspend fun runningCore(gameId: Long, emulatorId: String): String? {
        val game = gameDao.getById(gameId) ?: return null
        return saveSyncApiClient.resolveCoreForGame(game, emulatorId)
    }

    /**
     * Whether the core running [state]'s game can load its cached file. False only when the file
     * carries another core's fixed header.
     */
    suspend fun loadableByRunningCore(state: StateCacheEntity, emulatorId: String): Boolean {
        val file = getCacheFile(state) ?: return true
        return LibretroStateFormat.readableBy(runningCore(state.gameId, emulatorId), file)
    }

    internal suspend fun serverEmulatorTag(state: StateCacheEntity): String {
        val emulatorId = if (state.emulatorId == EmulatorRegistry.LEGACY_BUILTIN_ID) {
            EmulatorRegistry.BUILTIN_ID
        } else {
            state.emulatorId
        }
        val writer = getCacheFile(state)?.let { LibretroStateFormat.writerOf(LibretroStateFormat.headerOf(it)) }
        val game = gameDao.getById(state.gameId)
        val coreId = writer
            ?: game?.let { saveSyncApiClient.resolveCoreForGame(it, emulatorId) }
            ?: state.coreId?.takeIf { it != state.emulatorId }
        return EmulatorRegistry.toServerEmulator(emulatorId, coreId)
    }

    private fun calculateFileHash(file: File): String {
        val md = MessageDigest.getInstance("MD5")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                md.update(buffer, 0, bytesRead)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * The capture time a state's name carries: `[yyyy-MM-dd_HH-mm-ss]` from Argosy, or RomM's
     * `[yyyy-MM-dd HH-mm-ss-SSS]` (web player) and `[yyyy-MM-dd_HH-mm-ss-SSS]` (server tag).
     */
    fun parseStateFileTimestamp(fileName: String): Instant? {
        val match = STATE_NAME_TIMESTAMP.find(fileName) ?: return null
        val (date, time, millis) = match.destructured
        return try {
            java.time.LocalDateTime.parse("${date}_$time", UPLOAD_TIMESTAMP_FORMAT)
                .plusNanos((millis.toLongOrNull() ?: 0L) * 1_000_000L)
                .atZone(java.time.ZoneId.systemDefault()).toInstant()
        } catch (e: Exception) {
            null
        }
    }

    fun serverStateWrittenAt(state: RomMState): Instant? =
        parseStateFileTimestamp(state.fileName) ?: SaveSyncApiClient.parseTimestampOrNull(state.updatedAt)

    fun buildUploadFileName(state: StateCacheEntity, romBaseName: String): String {
        val stateExt = when (state.slotNumber) {
            -1 -> "state.auto"
            else -> "state${state.slotNumber}"
        }
        val timestamp = UPLOAD_TIMESTAMP_FORMAT.format(
            ZonedDateTime.ofInstant(state.cachedAt, java.time.ZoneOffset.UTC)
        )
        val channel = state.channelName
        return if (channel != null) {
            "$romBaseName [$timestamp].$channel.$stateExt"
        } else {
            "$romBaseName [$timestamp].$stateExt"
        }
    }

    fun buildMigratedFileName(save: RomMSave): String? {
        val slot = save.slot ?: return null
        val stateSlotMatch = Regex("""^state_(\w+)$""").find(slot) ?: return null
        val ext = save.fileExtension?.lowercase() ?: File(save.fileName).extension.lowercase()
        if (ext == "srm" || ext == "zip") return null
        val slotValue = stateSlotMatch.groupValues[1]
        val stateExt = when (slotValue) {
            "auto" -> "state.auto"
            else -> "state$slotValue"
        }
        val baseName = save.fileName
            .replace(Regex("""\.(state\d*|auto)$"""), "")
            .replace(Regex("""\.state(?= \[)"""), "")
            .replace(Regex("""\.state$"""), "")
        return "$baseName.$stateExt"
    }

    data class ParsedStateFileName(
        val romBaseName: String?,
        val channelName: String?,
        val slotNumber: Int
    )

    fun parseStateFileName(fileName: String): ParsedStateFileName {
        val ts = """ \[\d{4}-\d{2}-\d{2}[_ ]\d{2}-\d{2}-\d{2}(?:-\d{3})?]"""

        val channelAutoMatch = Regex("""^(.+)$ts\.(.+?)\.state\.auto$""").find(fileName)
        if (channelAutoMatch != null) {
            return ParsedStateFileName(channelAutoMatch.groupValues[1], channelAutoMatch.groupValues[2], -1)
        }
        val autoMatch = Regex("""^(.+?)(?:\.state)?(?:$ts)?\.state\.auto$|^(.+?)\.state$ts\.auto$""").find(fileName)
        if (autoMatch != null) {
            val rom = autoMatch.groupValues[1].ifEmpty { autoMatch.groupValues[2] }
            return ParsedStateFileName(rom, null, -1)
        }
        val channelNumberedMatch = Regex("""^(.+)$ts\.(.+?)\.state(\d+)$""").find(fileName)
        if (channelNumberedMatch != null) {
            return ParsedStateFileName(channelNumberedMatch.groupValues[1], channelNumberedMatch.groupValues[2], channelNumberedMatch.groupValues[3].toIntOrNull() ?: 0)
        }
        val numberedMatch = Regex("""^(.+?)(?:$ts)?\.state(\d+)$""").find(fileName)
        if (numberedMatch != null) {
            return ParsedStateFileName(numberedMatch.groupValues[1], null, numberedMatch.groupValues[2].toIntOrNull() ?: 0)
        }
        val legacyMatch = Regex("""^(.+?)(?:$ts)?\.state$""").find(fileName)
        if (legacyMatch != null) {
            return ParsedStateFileName(legacyMatch.groupValues[1], null, 0)
        }
        return ParsedStateFileName(null, null, parseSlotFromFileName(fileName))
    }

    fun parseSlotFromFileName(fileName: String): Int {
        val ext = File(fileName).extension.lowercase()

        if (ext == "auto" || fileName.endsWith(".state.auto")) return -1

        val slotMatch = SLOT_REGEX.find(fileName)
        if (slotMatch != null) {
            val slotStr = slotMatch.groupValues.getOrNull(1)
            return slotStr?.toIntOrNull() ?: 0
        }

        val underscoreMatch = UNDERSCORE_SLOT_REGEX.find(fileName)
        if (underscoreMatch != null) {
            return underscoreMatch.groupValues[1].toIntOrNull() ?: 0
        }

        return 0
    }

    suspend fun queueStateForUpload(
        stateCacheId: Long,
        gameId: Long,
        rommId: Long,
        emulatorId: String
    ): Boolean {
        val state = stateCacheDao.getById(stateCacheId) ?: return false

        val payload = SaveStatePayload(stateCacheId, emulatorId)
        pendingSyncQueueDao.insert(
            PendingSyncQueueEntity(
                gameId = gameId,
                rommId = rommId,
                syncType = SyncType.SAVE_STATE,
                priority = SyncPriority.SAVE_STATE,
                payloadJson = payloadCodec.encode(payload),
                ownerUserId = syncPreferencesRepository.getRommUserId()
            )
        )
        Log.d(TAG, "[StateSync] QUEUED stateId=$stateCacheId gameId=$gameId")
        return true
    }

    suspend fun processPendingStateUploads(): Int = withContext(Dispatchers.IO) {
        val api = saveSyncApiClient.getApi()
        if (api == null) {
            Log.d(TAG, "[StateSync] processPendingStateUploads | No API connection")
            return@withContext 0
        }

        val activeOwnerId = syncPreferencesRepository.getRommUserId()
        val pending = pendingSyncQueueDao.getRetryableBySyncType(SyncType.SAVE_STATE)
            .filter { it.ownerUserId == null || it.ownerUserId == activeOwnerId }
        if (pending.isEmpty()) {
            return@withContext 0
        }

        Log.d(TAG, "[StateSync] processPendingStateUploads | Processing ${pending.size} pending uploads")

        var uploadedCount = 0
        for (item in pending) {
            val payload = payloadCodec.decodeSaveState(item.payloadJson) ?: continue
            val state = stateCacheDao.getById(payload.stateCacheId)
            if (state == null) {
                Log.w(TAG, "[StateSync] UPLOAD itemId=${item.id} | State cache not found, removing from queue")
                pendingSyncQueueDao.deleteById(item.id)
                continue
            }

            if (state.gameId != item.gameId) {
                Log.w(
                    TAG,
                    "[StateSync] UPLOAD itemId=${item.id} | stateId=${state.id} no longer belongs to gameId=${item.gameId}; keeping it local, removing from queue"
                )
                pendingSyncQueueDao.deleteById(item.id)
                continue
            }

            val game = gameDao.getById(item.gameId)
            if (!item.targetsRomOf(game)) {
                Log.w(
                    TAG,
                    "[StateSync] UPLOAD itemId=${item.id} | queue rommId=${item.rommId} no longer matches gameId=${item.gameId} rommId=${game?.rommId}; keeping stateId=${state.id} local, removing from queue"
                )
                pendingSyncQueueDao.deleteById(item.id)
                continue
            }
            val romBaseName = game?.localPath?.let { File(it).nameWithoutExtension } ?: state.platformSlug
            val result = uploadStateToRomM(state, item.rommId, romBaseName, api)
            when (result) {
                is StateCloudResult.Success -> {
                    pendingSyncQueueDao.deleteById(item.id)
                    uploadedCount++
                    Log.i(TAG, "[StateSync] UPLOADED stateId=${state.id} slot=${state.slotNumber}")
                }
                is StateCloudResult.AlreadySynced -> {
                    pendingSyncQueueDao.deleteById(item.id)
                    Log.d(TAG, "[StateSync] UPLOAD stateId=${state.id} | Already synced, removed from queue")
                }
                is StateCloudResult.NoStateFound -> {
                    pendingSyncQueueDao.deleteById(item.id)
                    Log.w(TAG, "[StateSync] UPLOAD stateId=${state.id} | Cache file not found, removed from queue")
                }
                is StateCloudResult.Error -> {
                    pendingSyncQueueDao.markFailed(item.id, result.message)
                    Log.w(TAG, "[StateSync] UPLOAD FAILED stateId=${state.id} | ${result.message} | retry=${item.retryCount + 1}")
                }
                else -> {
                    Log.w(TAG, "[StateSync] UPLOAD stateId=${state.id} | Unexpected result: $result")
                }
            }
        }

        Log.d(TAG, "[StateSync] processPendingStateUploads | Completed | uploaded=$uploadedCount")
        uploadedCount
    }
}
