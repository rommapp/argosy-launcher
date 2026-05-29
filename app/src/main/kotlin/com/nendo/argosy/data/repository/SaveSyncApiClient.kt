package com.nendo.argosy.data.repository

import android.os.StatFs
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.dao.getByIdsChunked
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMCapabilities
import com.nendo.argosy.data.remote.romm.RomMDeleteSavesRequest
import com.nendo.argosy.data.remote.romm.RomMSave
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.SavePathResolver
import com.nendo.argosy.data.sync.platform.GciSaveHandler
import com.nendo.argosy.data.sync.platform.PlatformSaveHandler
import com.nendo.argosy.data.sync.platform.PlatformSaveHandlerRegistry
import com.nendo.argosy.data.sync.platform.SaveContext
import com.nendo.argosy.data.sync.platform.SwitchSaveHandler
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.Normalizer
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaveSyncApiClient @Inject constructor(
    private val saveSyncDao: SaveSyncDao,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val emulatorResolver: EmulatorResolver,
    private val gameDao: GameDao,
    private val savePathResolver: SavePathResolver,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val fal: FileAccessLayer,
    private val switchSaveHandler: SwitchSaveHandler,
    private val gciSaveHandler: GciSaveHandler,
    private val saveHandlerRegistry: PlatformSaveHandlerRegistry,
    private val conflictDetector: ConflictDetector,
    private val saveUploader: dagger.Lazy<SaveUploader>,
    private val saveDownloader: dagger.Lazy<SaveDownloader>
) {
    private var api: RomMApi? = null
    private var deviceId: String? = null
    private var capabilities: RomMCapabilities = RomMCapabilities.NONE

    fun setApi(api: RomMApi?) {
        this.api = api
    }

    fun getApi(): RomMApi? = api

    fun setDeviceId(id: String?) {
        deviceId = id
    }

    fun getDeviceId(): String? = deviceId

    fun setCapabilities(caps: RomMCapabilities) {
        capabilities = caps
    }

    fun getCapabilities(): RomMCapabilities = capabilities

    internal fun getHandler(
        config: SavePathConfig?,
        platformSlug: String,
        emulatorId: String
    ): PlatformSaveHandler = saveHandlerRegistry.getHandler(config, platformSlug, emulatorId)

    suspend fun resolveEmulatorForGame(game: GameEntity): String? {
        val gameConfig = emulatorConfigDao.getByGameId(game.id)
        if (gameConfig?.packageName != null) {
            val resolved = emulatorResolver.resolveEmulatorId(gameConfig.packageName)
            if (resolved != null) {
                Logger.debug(TAG, "[SaveSync] DISCOVER gameId=${game.id} | Resolved emulator from game config | package=${gameConfig.packageName}, emulatorId=$resolved")
                return resolved
            }
        }

        val platformConfig = emulatorConfigDao.getDefaultForPlatform(game.platformId)
        if (platformConfig?.packageName != null) {
            val resolved = emulatorResolver.resolveEmulatorId(platformConfig.packageName)
            if (resolved != null) {
                Logger.debug(TAG, "[SaveSync] DISCOVER gameId=${game.id} | Resolved emulator from platform default | package=${platformConfig.packageName}, emulatorId=$resolved")
                return resolved
            }
        }

        emulatorResolver.ensureDetected()

        val preferred = emulatorResolver.getPreferredEmulator(game.platformSlug)
        if (preferred != null) {
            Logger.debug(TAG, "[SaveSync] DISCOVER gameId=${game.id} | Using preferred emulator for platform=${game.platformSlug} | emulatorId=${preferred.def.id}")
            return preferred.def.id
        }

        val installedEmulators = emulatorResolver.getInstalledForPlatform(game.platformSlug)
        if (installedEmulators.isNotEmpty()) {
            val emulatorId = installedEmulators.first().def.id
            Logger.debug(TAG, "[SaveSync] DISCOVER gameId=${game.id} | Falling back to first installed for platform=${game.platformSlug} | emulatorId=$emulatorId, installed=${installedEmulators.map { it.def.id }}")
            return emulatorId
        }

        Logger.warn(TAG, "[SaveSync] DISCOVER gameId=${game.id} | Cannot resolve emulator | platform=${game.platformSlug}, no config and no installed emulators")
        return null
    }

    internal suspend fun resolveCoreForGame(game: GameEntity): String? {
        val gameConfig = emulatorConfigDao.getByGameId(game.id)
        if (gameConfig?.coreName != null) return gameConfig.coreName
        val platformConfig = emulatorConfigDao.getDefaultForPlatform(game.platformId)
        return platformConfig?.coreName
    }

    suspend fun resolveCoreForGame(gameId: Long): String? {
        val game = gameDao.getById(gameId) ?: return null
        return resolveCoreForGame(game)
    }

    suspend fun rekeySaveSyncToLocalEmulators(): Int = withContext(Dispatchers.IO) {
        val gameIds = saveSyncDao.getAllGameIds()
        var rewritten = 0
        for (gameId in gameIds) {
            val game = gameDao.getById(gameId) ?: continue
            val localEmulator = resolveEmulatorForGame(game) ?: continue
            val updated = saveSyncDao.rekeyEmulatorForGame(gameId, localEmulator)
            if (updated > 0) {
                rewritten += updated
                Logger.debug(TAG, "[SaveSync] REKEY gameId=$gameId | rewrote $updated rows to emulatorId=$localEmulator")
            }
        }
        if (rewritten > 0) Logger.info(TAG, "[SaveSync] REKEY | Rewrote $rewritten save_sync rows to local emulator")
        saveSyncDao.deleteDuplicateRows()
        rewritten
    }

    suspend fun deleteServerSaves(saveIds: List<Long>): Boolean = withContext(Dispatchers.IO) {
        if (saveIds.isEmpty()) return@withContext true
        val api = this@SaveSyncApiClient.api ?: return@withContext false
        try {
            val response = api.deleteSaves(RomMDeleteSavesRequest(saveIds))
            response.isSuccessful
        } catch (e: Exception) {
            Logger.error(TAG, "deleteServerSaves: failed for $saveIds", e)
            false
        }
    }

    suspend fun checkSavesForGame(gameId: Long, rommId: Long): List<RomMSave> = withContext(Dispatchers.IO) {
        val api = this@SaveSyncApiClient.api ?: return@withContext emptyList()

        val response = try {
            if (deviceId != null) api.getSavesByRomWithDevice(rommId, deviceId!!) else api.getSavesByRom(rommId)
        } catch (e: Exception) {
            Logger.error(TAG, "[SaveSync] UPLOAD | getSavesByRom failed | gameId=$gameId, rommId=$rommId", e)
            return@withContext emptyList()
        }

        if (!response.isSuccessful) {
            Logger.warn(TAG, "[SaveSync] UPLOAD | getSavesByRom HTTP error | gameId=$gameId, rommId=$rommId, status=${response.code()}")
            return@withContext emptyList()
        }

        response.body() ?: emptyList()
    }

    suspend fun checkForServerUpdates(platformId: Long): List<SaveSyncEntity> = withContext(Dispatchers.IO) {
        val api = this@SaveSyncApiClient.api ?: return@withContext emptyList()

        val response = try {
            api.getSavesByPlatform(platformId)
        } catch (e: Exception) {
            Logger.error(TAG, "[SaveSync] WORKER | getSavesByPlatform failed | platformId=$platformId", e)
            return@withContext emptyList()
        }

        if (!response.isSuccessful) {
            Logger.warn(TAG, "[SaveSync] WORKER | getSavesByPlatform HTTP error | platformId=$platformId, status=${response.code()}")
            return@withContext emptyList()
        }

        val serverSaves = response.body() ?: return@withContext emptyList()
        val updatedEntities = mutableListOf<SaveSyncEntity>()

        val downloadedGames = gameDao.getByIdsChunked(gameDao.getDownloadedRommGameIds())

        for (serverSave in serverSaves) {
            if (isStateShapedSave(serverSave)) continue
            val game = downloadedGames.find { it.rommId == serverSave.romId } ?: continue
            val emulatorId = resolveEmulatorForGame(game)
            if (emulatorId == null) {
                Logger.warn(TAG, "[SaveSync] WORKER gameId=${game.id} | Skipping save - cannot resolve emulator | serverSaveId=${serverSave.id}, fileName=${serverSave.fileName}")
                continue
            }
            val channelName = serverSave.slot ?: parseServerChannelName(serverSave.fileName)

            if (channelName == null) continue

            val existing = saveSyncDao.getByGameEmulatorAndChannel(game.id, emulatorId, channelName)

            val serverTime = parseTimestamp(serverSave.updatedAt)

            if (existing == null || serverTime.isAfter(existing.serverUpdatedAt)) {
                val uploaderDeviceSync = serverSave.deviceSyncs
                    ?.filter { !it.isCurrent }
                    ?.maxByOrNull { it.lastSyncedAt ?: "" }
                val entity = SaveSyncEntity(
                    id = existing?.id ?: 0,
                    gameId = game.id,
                    rommId = game.rommId!!,
                    emulatorId = emulatorId,
                    channelName = channelName,
                    rommSaveId = serverSave.id,
                    localSavePath = existing?.localSavePath,
                    localUpdatedAt = existing?.localUpdatedAt,
                    serverUpdatedAt = serverTime,
                    lastSyncedAt = existing?.lastSyncedAt,
                    syncStatus = conflictDetector.determineSyncStatus(existing?.localUpdatedAt, serverTime),
                    lastUploadedHash = existing?.lastUploadedHash,
                    lastSyncDeviceId = uploaderDeviceSync?.deviceId ?: existing?.lastSyncDeviceId,
                    lastSyncDeviceName = uploaderDeviceSync?.deviceName ?: existing?.lastSyncDeviceName
                )
                saveSyncDao.upsert(entity)
                if (entity.syncStatus == SaveSyncEntity.STATUS_SERVER_NEWER) {
                    updatedEntities.add(entity)
                }
            }
        }

        updatedEntities
    }

    suspend fun checkForAllServerUpdates(): List<SaveSyncEntity> = withContext(Dispatchers.IO) {
        if (api == null) return@withContext emptyList()
        val downloadedGames = gameDao.getByIdsChunked(gameDao.getDownloadedRommGameIds())
        val platformIds = downloadedGames.map { it.platformId }.distinct()

        val allUpdates = mutableListOf<SaveSyncEntity>()
        for (platformId in platformIds) {
            allUpdates.addAll(checkForServerUpdates(platformId))
        }
        allUpdates
    }

    suspend fun uploadSave(
        gameId: Long,
        emulatorId: String,
        channelName: String? = null,
        forceOverwrite: Boolean = false,
        isHardcore: Boolean = false,
        uploadedCacheId: Long? = null
    ): SaveSyncResult = saveUploader.get().uploadSave(gameId, emulatorId, channelName, forceOverwrite, isHardcore, uploadedCacheId)

    suspend fun uploadCacheEntry(
        gameId: Long,
        rommId: Long,
        emulatorId: String,
        channelName: String,
        cacheFile: File,
        contentHash: String?,
        overwrite: Boolean = false,
        uploadedCacheId: Long? = null
    ): SaveSyncResult = saveUploader.get().uploadCacheEntry(gameId, rommId, emulatorId, channelName, cacheFile, contentHash, overwrite, uploadedCacheId)

    suspend fun downloadSave(
        gameId: Long,
        emulatorId: String,
        channelName: String? = null,
        skipBackup: Boolean = false,
        knownServerSaveId: Long? = null
    ): SaveSyncResult = saveDownloader.get().downloadSave(gameId, emulatorId, channelName, skipBackup, knownServerSaveId)

    suspend fun downloadSaveById(
        serverSaveId: Long,
        targetPath: String,
        emulatorId: String,
        emulatorPackage: String? = null,
        gameId: Long? = null,
        romPath: String? = null
    ): Boolean = saveDownloader.get().downloadSaveById(serverSaveId, targetPath, emulatorId, emulatorPackage, gameId, romPath)

    suspend fun downloadSaveAsChannel(
        gameId: Long,
        serverSaveId: Long,
        channelName: String,
        emulatorId: String?,
        skipDeviceId: Boolean = false
    ): Boolean = saveDownloader.get().downloadSaveAsChannel(gameId, serverSaveId, channelName, emulatorId, skipDeviceId)

    suspend fun downloadAndCacheSave(
        serverSaveId: Long,
        gameId: Long,
        channelName: String?
    ): Boolean = saveDownloader.get().downloadAndCacheSave(serverSaveId, gameId, channelName)

    suspend fun confirmDeviceSynced(saveId: Long) = saveDownloader.get().confirmDeviceSynced(saveId)

    suspend fun flushPendingDeviceSync(gameId: Long) = saveDownloader.get().flushPendingDeviceSync(gameId)

    suspend fun clearSaveAtPath(targetPath: String): Boolean = withContext(Dispatchers.IO) {
        if (!fal.exists(targetPath)) return@withContext true
        val deleted = if (fal.isDirectory(targetPath)) {
            fal.deleteRecursively(targetPath)
        } else {
            fal.delete(targetPath)
        }
        if (!deleted || fal.exists(targetPath)) {
            Logger.error(TAG, "clearSaveAtPath: Failed to delete $targetPath")
            return@withContext false
        }
        true
    }

    /**
     * Prefix-aware variant of [clearSaveAtPath]. For platforms whose disc id maps to multiple
     * sibling folders under a parent (PSP), enumerates and deletes every match. Falls back to
     * single-path delete when the handler doesn't expose a multi-folder layout.
     */
    suspend fun clearSavesForTitle(
        targetPath: String,
        platformSlug: String,
        titleId: String?
    ): Boolean = withContext(Dispatchers.IO) {
        val handler = saveHandlerRegistry.getFolderHandler(platformSlug)
        if (handler == null) {
            return@withContext clearSaveAtPath(targetPath)
        }
        if (titleId.isNullOrBlank()) {
            Logger.warn(TAG, "clearSavesForTitle: refusing to clear $targetPath -- folder-based platform=$platformSlug requires a titleId to scope the delete")
            return@withContext false
        }

        val matchesAtTarget = handler.findAllSaveFoldersBySaveId(targetPath, titleId)
        val (parentPath, matches) = if (matchesAtTarget.isNotEmpty()) {
            targetPath to matchesAtTarget
        } else {
            val p = File(targetPath).parent ?: return@withContext true
            p to handler.findAllSaveFoldersBySaveId(p, titleId)
        }
        if (matches.isEmpty()) {
            Logger.debug(TAG, "clearSavesForTitle: no prefix matches for titleId=$titleId at parent=$parentPath, nothing to clear")
            return@withContext true
        }

        Logger.debug(TAG, "clearSavesForTitle: deleting ${matches.size} folder(s) | titleId=$titleId, parent=$parentPath")
        var allOk = true
        for (path in matches) {
            if (!clearSaveAtPath(path)) allOk = false
        }
        allOk
    }

    suspend fun discoverSavePath(
        emulatorId: String,
        gameTitle: String,
        platformSlug: String,
        romPath: String? = null,
        cachedSaveId: String? = null,
        coreName: String? = null,
        emulatorPackage: String? = null,
        gameId: Long? = null
    ): String? = savePathResolver.discoverSavePath(
        emulatorId = emulatorId,
        gameTitle = gameTitle,
        platformSlug = platformSlug,
        romPath = romPath,
        cachedSaveId = cachedSaveId,
        coreName = coreName,
        emulatorPackage = emulatorPackage,
        gameId = gameId
    )

    suspend fun constructSavePath(
        emulatorId: String,
        gameTitle: String,
        platformSlug: String,
        romPath: String?,
        coreName: String? = null,
        cachedSaveId: String? = null
    ): String? = savePathResolver.constructSavePath(emulatorId, gameTitle, platformSlug, romPath, coreName, cachedSaveId)

    internal suspend fun <T> withRetry(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 500,
        tag: String = "",
        block: suspend () -> T
    ): T {
        var lastException: Exception? = null
        repeat(maxAttempts) { attempt ->
            try {
                return block()
            } catch (e: IOException) {
                lastException = e
                if (attempt < maxAttempts - 1) {
                    val delayMs = initialDelayMs * (1 shl attempt)
                    Logger.debug(TAG, "$tag retry ${attempt + 1}/$maxAttempts after ${delayMs}ms: ${e.message}")
                    delay(delayMs)
                }
            }
        }
        throw lastException ?: IOException("Retry failed")
    }

    internal fun hasEnoughDiskSpace(targetPath: String, requiredBytes: Long): Boolean {
        if (requiredBytes <= 0) return true
        return try {
            val parentDir = File(targetPath).parentFile ?: File(targetPath)
            val existingDir = generateSequence(parentDir) { it.parentFile }
                .firstOrNull { it.exists() } ?: return true
            val stat = StatFs(existingDir.absolutePath)
            val availableBytes = stat.availableBytes
            val hasSpace = availableBytes > requiredBytes * 2
            if (!hasSpace) {
                Logger.warn(TAG, "Insufficient disk space: available=${availableBytes}bytes, required=${requiredBytes * 2}bytes")
            }
            hasSpace
        } catch (e: Exception) {
            Logger.warn(TAG, "Failed to check disk space", e)
            true
        }
    }

    internal fun determineSyncStatus(localTime: Instant?, serverTime: Instant): String =
        conflictDetector.determineSyncStatus(localTime, serverTime)

    fun setSessionOnOlderSave(gameId: Long, isOlder: Boolean) =
        conflictDetector.setSessionOnOlderSave(gameId, isOlder)

    fun clearSessionOnOlderSave(gameId: Long) =
        conflictDetector.clearSessionOnOlderSave(gameId)

    fun isSessionOnOlderSave(gameId: Long): Boolean =
        conflictDetector.isSessionOnOlderSave(gameId)

    companion object {
        private const val TAG = "SaveSyncApiClient"
        const val DEFAULT_SAVE_NAME = "argosy-latest"
        const val AUTOSAVE_SLOT_NAME = "autosave"
        internal const val MIN_VALID_SAVE_SIZE_BYTES = 100L
        internal const val AUTOCLEANUP_LIMIT = 10

        fun computeUploadFileName(localSavePath: String?, channelName: String?, romBaseName: String?): String {
            val baseName = when {
                channelName == null || channelName.equals(AUTOSAVE_SLOT_NAME, ignoreCase = true) ->
                    romBaseName ?: DEFAULT_SAVE_NAME
                else -> channelName
            }
            val ext = localSavePath?.let { java.io.File(it) }?.let { file ->
                when {
                    file.isDirectory -> "zip"
                    file.extension.equals("gci", ignoreCase = true) -> "zip"
                    else -> file.extension
                }
            } ?: "zip"
            return if (ext.isNotEmpty()) "$baseName.$ext" else baseName
        }
        internal val TIMESTAMP_ONLY_PATTERN = Regex("""^\d{4}-\d{2}-\d{2}[_-]\d{2}[_-]\d{2}[_-]\d{2}$""")
        internal val ROMM_TIMESTAMP_TAG = Regex("""^\[\d{4}-\d{2}-\d{2}[ _]\d{2}-\d{2}-\d{2}(-\d+)?\]$""")
        internal val SWITCH_EMULATOR_IDS = setOf(
            "yuzu", "ryujinx", "citron", "strato", "eden", "sudachi", "skyline"
        )

        private val DIACRITICS_PATTERN = Regex("\\p{InCombiningDiacriticalMarks}+")

        internal fun stripAccents(s: String): String =
            DIACRITICS_PATTERN.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "")

        internal fun equalsNormalized(a: String, b: String): Boolean =
            stripAccents(a).equals(stripAccents(b), ignoreCase = true)

        internal fun startsWithNormalized(text: String, prefix: String): Boolean =
            stripAccents(text).startsWith(stripAccents(prefix), ignoreCase = true)

        internal fun dropPrefixNormalized(text: String, prefix: String): String {
            val stripped = stripAccents(text)
            val strippedPrefix = stripAccents(prefix)
            return if (stripped.startsWith(strippedPrefix, ignoreCase = true)) {
                text.drop(prefix.length)
            } else text
        }

        internal fun parseTimestamp(timestamp: String): Instant {
            return try {
                Instant.parse(timestamp)
            } catch (_: Exception) {
                try {
                    java.time.OffsetDateTime.parse(timestamp).toInstant()
                } catch (_: Exception) {
                    try {
                        java.time.ZonedDateTime.parse(timestamp).toInstant()
                    } catch (_: Exception) {
                        Logger.warn(TAG, "Failed to parse timestamp: $timestamp, using current time")
                        Instant.now()
                    }
                }
            }
        }

        internal fun parseServerChannelName(fileName: String): String? {
            val baseName = File(fileName).nameWithoutExtension
            if (isTimestampSaveName(baseName)) return null
            return baseName
        }

        internal fun parseServerChannelNameForSync(fileName: String, romBaseName: String?): String? {
            val baseName = File(fileName).nameWithoutExtension
            if (isTimestampSaveName(baseName)) return null
            if (isLatestSaveFileName(fileName, romBaseName)) return null
            return baseName
        }

        internal fun isLatestSaveFileName(fileName: String, romBaseName: String?): Boolean {
            val baseName = File(fileName).nameWithoutExtension
            if (baseName.equals(DEFAULT_SAVE_NAME, ignoreCase = true)) return true
            if (baseName.equals(AUTOSAVE_SLOT_NAME, ignoreCase = true)) return true
            if (romBaseName == null) return false
            if (equalsNormalized(baseName, romBaseName)) return true
            if (startsWithNormalized(baseName, romBaseName)) {
                val suffix = dropPrefixNormalized(baseName, romBaseName).trim()
                if (suffix.isEmpty()) return true
                if (ROMM_TIMESTAMP_TAG.matches(suffix)) return true
            }
            return false
        }

        internal fun isTimestampSaveName(baseName: String): Boolean {
            return TIMESTAMP_ONLY_PATTERN.matches(baseName)
        }

        private val STATE_SLOT_PATTERN = Regex("""^state_""", RegexOption.IGNORE_CASE)
        private val STATE_FILE_PATTERN = Regex("""\.state\d*(\.zip)?$""", RegexOption.IGNORE_CASE)

        internal fun isStateShapedSave(save: RomMSave): Boolean {
            val slot = save.slot
            if (slot != null && STATE_SLOT_PATTERN.containsMatchIn(slot)) return true
            return STATE_FILE_PATTERN.containsMatchIn(save.fileName)
        }
    }
}
