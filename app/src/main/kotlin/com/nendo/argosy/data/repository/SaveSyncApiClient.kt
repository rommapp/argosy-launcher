package com.nendo.argosy.data.repository

import android.content.Context
import android.os.StatFs
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.GameCubeHeaderParser
import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMDeleteSavesRequest
import com.nendo.argosy.data.remote.romm.RomMDeviceIdRequest
import com.nendo.argosy.data.remote.romm.RomMSave
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.sync.SavePathResolver
import com.nendo.argosy.data.sync.platform.DefaultSaveHandler
import com.nendo.argosy.data.sync.platform.GciSaveHandler
import com.nendo.argosy.data.sync.platform.N3dsSaveHandler
import com.nendo.argosy.data.sync.platform.PlatformSaveHandler
import com.nendo.argosy.data.sync.platform.PspSaveHandler
import com.nendo.argosy.data.sync.platform.RetroArchSaveHandler
import com.nendo.argosy.data.sync.platform.SaveContext
import com.nendo.argosy.data.sync.platform.SwitchSaveHandler
import com.nendo.argosy.data.sync.platform.VitaSaveHandler
import com.nendo.argosy.data.sync.platform.WiiUSaveHandler
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.titledb.TitleDbRepository
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.SaveDebugLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaveSyncApiClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val saveSyncDao: SaveSyncDao,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val emulatorResolver: EmulatorResolver,
    private val gameDao: GameDao,
    private val titleDbRepository: TitleDbRepository,
    private val saveArchiver: SaveArchiver,
    private val savePathResolver: SavePathResolver,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val saveCacheManager: dagger.Lazy<SaveCacheManager>,
    private val fal: FileAccessLayer,
    private val switchSaveHandler: SwitchSaveHandler,
    private val gciSaveHandler: GciSaveHandler,
    private val n3dsSaveHandler: N3dsSaveHandler,
    private val vitaSaveHandler: VitaSaveHandler,
    private val pspSaveHandler: PspSaveHandler,
    private val wiiUSaveHandler: WiiUSaveHandler,
    private val retroArchSaveHandler: RetroArchSaveHandler,
    private val defaultSaveHandler: DefaultSaveHandler
) {
    private var api: RomMApi? = null
    private var deviceId: String? = null

    fun setApi(api: RomMApi?) {
        this.api = api
    }

    fun getApi(): RomMApi? = api

    fun setDeviceId(id: String?) {
        deviceId = id
    }

    fun getDeviceId(): String? = deviceId

    internal fun getHandler(
        config: SavePathConfig?,
        platformSlug: String,
        emulatorId: String
    ): PlatformSaveHandler {
        return when {
            emulatorId in listOf("retroarch", "retroarch_64") -> retroArchSaveHandler
            config?.usesGciFormat == true -> gciSaveHandler
            platformSlug == "switch" -> switchSaveHandler
            platformSlug == "3ds" -> n3dsSaveHandler
            platformSlug in listOf("vita", "psvita") -> vitaSaveHandler
            platformSlug == "psp" -> pspSaveHandler
            platformSlug == "wiiu" -> wiiUSaveHandler
            else -> defaultSaveHandler
        }
    }

    internal suspend fun isFolderSaveSyncEnabled(): Boolean {
        val prefs = userPreferencesRepository.preferences.first()
        return prefs.saveSyncEnabled && prefs.experimentalFolderSaveSync
    }

    internal suspend fun resolveEmulatorForGame(game: GameEntity): String? {
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

        val installedEmulators = emulatorResolver.getInstalledForPlatform(game.platformSlug)
        if (installedEmulators.isNotEmpty()) {
            val emulatorId = installedEmulators.first().def.id
            Logger.debug(TAG, "[SaveSync] DISCOVER gameId=${game.id} | Using first installed emulator for platform=${game.platformSlug} | emulatorId=$emulatorId, installed=${installedEmulators.map { it.def.id }}")
            return emulatorId
        }

        Logger.warn(TAG, "[SaveSync] DISCOVER gameId=${game.id} | Cannot resolve emulator | platform=${game.platformSlug}, no config and no installed emulators")
        return null
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

        val downloadedGames = gameDao.getGamesWithLocalPath()
            .filter { it.rommId != null }

        for (serverSave in serverSaves) {
            val game = downloadedGames.find { it.rommId == serverSave.romId } ?: continue
            val emulatorId = serverSave.emulator?.takeIf { it != "default" && it.isNotBlank() }
                ?: resolveEmulatorForGame(game)
            if (emulatorId == null) {
                Logger.warn(TAG, "[SaveSync] WORKER gameId=${game.id} | Skipping save - cannot resolve emulator | serverSaveId=${serverSave.id}, fileName=${serverSave.fileName}")
                continue
            }
            val channelName = parseServerChannelName(serverSave.fileName)

            if (channelName == null) continue

            val existing = saveSyncDao.getByGameEmulatorAndChannel(game.id, emulatorId, channelName)

            val serverTime = parseTimestamp(serverSave.updatedAt)

            if (existing == null || serverTime.isAfter(existing.serverUpdatedAt)) {
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
                    syncStatus = determineSyncStatus(existing?.localUpdatedAt, serverTime)
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
        val api = this@SaveSyncApiClient.api ?: return@withContext emptyList()
        val downloadedGames = gameDao.getGamesWithLocalPath().filter { it.rommId != null }
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
        isHardcore: Boolean = false
    ): SaveSyncResult = withContext(Dispatchers.IO) {
        Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId emulator=$emulatorId channel=$channelName | Starting upload")
        val api = this@SaveSyncApiClient.api
        if (api == null) {
            Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | No API connection")
            return@withContext SaveSyncResult.NotConfigured
        }

        val syncEntity = if (channelName != null) {
            saveSyncDao.getByGameEmulatorAndChannel(gameId, emulatorId, channelName)
        } else {
            saveSyncDao.getByGameAndEmulatorWithDefault(gameId, emulatorId, DEFAULT_SAVE_NAME)
        }

        val game = gameDao.getById(gameId)
        if (game == null) {
            Logger.warn(TAG, "[SaveSync] UPLOAD gameId=$gameId | Game not found in database")
            return@withContext SaveSyncResult.Error("Game not found")
        }
        val rommId = game.rommId
        if (rommId == null) {
            Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | No rommId, game not synced with RomM")
            return@withContext SaveSyncResult.NotConfigured
        }

        val resolvedEmulatorId = if (emulatorId == "default" || emulatorId.isBlank()) {
            resolveEmulatorForGame(game) ?: run {
                Logger.warn(TAG, "[SaveSync] UPLOAD gameId=$gameId | Cannot resolve emulator from config")
                return@withContext SaveSyncResult.Error("Cannot determine emulator")
            }
        } else {
            emulatorId
        }
        Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Using emulator=$resolvedEmulatorId (original=$emulatorId)")

        val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(gameId, game.platformId, game.platformSlug)

        val folderSyncEnabled = isFolderSaveSyncEnabled()
        val cachedPath = syncEntity?.localSavePath?.takeIf { path ->
            if (game.platformSlug == "switch") switchSaveHandler.isValidCachedSavePath(path) else true
        }
        if (syncEntity?.localSavePath != null && cachedPath == null) {
            Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Rejecting invalid cached Switch path=${syncEntity.localSavePath}")
        }
        var localPath = cachedPath
            ?: savePathResolver.discoverSavePath(
                emulatorId = resolvedEmulatorId,
                gameTitle = game.title,
                platformSlug = game.platformSlug,
                romPath = game.localPath,
                cachedTitleId = game.titleId,
                emulatorPackage = emulatorPackage,
                gameId = gameId,
                isFolderSaveSyncEnabled = folderSyncEnabled
            )

        if (localPath == null && (game.titleId != null || titleDbRepository.getCachedCandidates(gameId).isNotEmpty())) {
            Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Clearing stale titleId cache and retrying")
            titleDbRepository.clearTitleIdCache(gameId)
            localPath = savePathResolver.discoverSavePath(
                emulatorId = resolvedEmulatorId,
                gameTitle = game.title,
                platformSlug = game.platformSlug,
                romPath = game.localPath,
                cachedTitleId = null,
                emulatorPackage = emulatorPackage,
                gameId = gameId,
                isFolderSaveSyncEnabled = folderSyncEnabled
            )
        }

        if (localPath == null) {
            Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Could not discover save path | emulator=$emulatorId, platform=${game.platformSlug}")
            return@withContext SaveSyncResult.NoSaveFound
        }

        if (!fal.exists(localPath)) {
            Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Save file does not exist | path=$localPath")
            return@withContext SaveSyncResult.NoSaveFound
        }

        val config = SavePathRegistry.getConfigIncludingUnsupported(resolvedEmulatorId)
        val isDirectory = fal.isDirectory(localPath)
        val isFolderBased = config?.usesFolderBasedSaves == true && isDirectory
        val isGciBundle = config?.usesGciFormat == true

        if (isFolderBased && !isFolderSaveSyncEnabled()) {
            Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Folder save sync disabled, skipping")
            return@withContext SaveSyncResult.NotConfigured
        }

        val localModified = if (isDirectory) {
            Instant.ofEpochMilli(savePathResolver.findNewestFileTime(localPath))
        } else {
            Instant.ofEpochMilli(fal.lastModified(localPath))
        }
        Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Local modified time | localModified=$localModified")

        val handler = getHandler(config, game.platformSlug, resolvedEmulatorId)
        val saveContext = SaveContext(
            config = config ?: SavePathConfig(
                emulatorId = resolvedEmulatorId,
                defaultPaths = emptyList(),
                saveExtensions = listOf("sav", "srm")
            ),
            romPath = game.localPath,
            titleId = game.titleId,
            emulatorPackage = emulatorPackage,
            gameId = gameId,
            gameTitle = game.title,
            platformSlug = game.platformSlug,
            emulatorId = resolvedEmulatorId,
            localSavePath = localPath
        )

        val prepared = handler.prepareForUpload(localPath, saveContext)
        if (prepared == null) {
            Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Handler returned no prepared save")
            return@withContext SaveSyncResult.NoSaveFound
        }

        val fileToUpload = prepared.file
        Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Save prepared | file=${fileToUpload.absolutePath}, size=${fileToUpload.length()}bytes, isTemporary=${prepared.isTemporary}")

        if (fileToUpload.length() <= MIN_VALID_SAVE_SIZE_BYTES) {
            Logger.warn(TAG, "[SaveSync] UPLOAD gameId=$gameId | Rejecting empty save | size=${fileToUpload.length()}bytes, minRequired=$MIN_VALID_SAVE_SIZE_BYTES")
            if (prepared.isTemporary) fileToUpload.delete()
            return@withContext SaveSyncResult.NoSaveFound
        }

        var tempTrailerFile: File? = null

        try {
            val uploadFile = if (isHardcore) {
                if (prepared.isTemporary) {
                    saveArchiver.appendHardcoreTrailer(fileToUpload)
                    Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Appended hardcore trailer to prepared file")
                    fileToUpload
                } else {
                    tempTrailerFile = File(context.cacheDir, "upload_${fileToUpload.name}")
                    fileToUpload.copyTo(tempTrailerFile, overwrite = true)
                    saveArchiver.appendHardcoreTrailer(tempTrailerFile)
                    Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Appended hardcore trailer to copy")
                    tempTrailerFile
                }
            } else {
                fileToUpload
            }

            val contentHash = saveArchiver.calculateContentHash(uploadFile)
            if (syncEntity?.lastUploadedHash == contentHash) {
                Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Skipped - content unchanged (hash=$contentHash)")
                if (prepared.isTemporary) fileToUpload.delete()
                tempTrailerFile?.delete()
                return@withContext SaveSyncResult.Success()
            }

            val romFile = game.localPath?.let { File(it) }
            val romBaseName = romFile?.nameWithoutExtension

            val uploadFileName = if (channelName != null) {
                val ext = fileToUpload.extension
                if (ext.isNotEmpty()) "$channelName.$ext" else channelName
            } else {
                val baseName = romBaseName ?: DEFAULT_SAVE_NAME
                val ext = fileToUpload.extension
                if (ext.isNotEmpty()) "$baseName.$ext" else baseName
            }

            val serverSaves = checkSavesForGame(gameId, rommId)
            Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Server saves found | count=${serverSaves.size}, files=${serverSaves.map { it.fileName }}")

            val latestServerSave = if (channelName != null) {
                serverSaves
                    .filter { it.slot == channelName }
                    .maxByOrNull { parseTimestamp(it.updatedAt) }
                    ?: serverSaves.find { File(it.fileName).nameWithoutExtension.equals(channelName, ignoreCase = true) }
            } else {
                val candidates = serverSaves.filter { isLatestSaveFileName(it.fileName, romBaseName) }
                if (isGciBundle && candidates.size > 1) {
                    candidates.find { it.fileName.endsWith(".zip", ignoreCase = true) }
                        ?: candidates.firstOrNull()
                } else {
                    candidates.firstOrNull()
                }
            }

            val existingServerSave = run {
                val candidates = serverSaves.filter { serverSave ->
                    val baseName = File(serverSave.fileName).nameWithoutExtension
                    if (channelName != null) {
                        baseName.equals(channelName, ignoreCase = true)
                    } else {
                        baseName.equals(DEFAULT_SAVE_NAME, ignoreCase = true) ||
                            romBaseName != null && baseName.equals(romBaseName, ignoreCase = true)
                    }
                }
                if (isGciBundle && candidates.size > 1) {
                    candidates.find { it.fileName.endsWith(".zip", ignoreCase = true) }
                        ?: candidates.firstOrNull()
                } else {
                    candidates.firstOrNull()
                }
            }

            if (!forceOverwrite && channelName != null && deviceId != null) {
                if (sessionOnOlderSave[gameId] == true) {
                    Logger.warn(TAG, "[SaveSync] UPLOAD gameId=$gameId | Session started on older save -- conflict for channel=$channelName")
                    val serverTime = latestServerSave?.let { parseTimestamp(it.updatedAt) } ?: Instant.now()
                    return@withContext SaveSyncResult.Conflict(gameId, localModified, serverTime)
                }
            }

            if (!forceOverwrite && channelName == null && latestServerSave != null) {
                val serverTime = parseTimestamp(latestServerSave.updatedAt)
                val deltaMs = serverTime.toEpochMilli() - localModified.toEpochMilli()
                val deltaStr = if (deltaMs >= 0) "+${deltaMs/1000}s" else "${deltaMs/1000}s"
                Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Conflict check | local=$localModified, server=$serverTime, delta=$deltaStr")
                if (serverTime.isAfter(localModified)) {
                    Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Decision=CONFLICT | Server is newer, blocking upload")
                    return@withContext SaveSyncResult.Conflict(gameId, localModified, serverTime)
                }
                Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Decision=PROCEED | Local is newer or equal")
            } else if (forceOverwrite) {
                Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Skipping conflict check (force overwrite)")
            }

            val needsGciMigration = isGciBundle && existingServerSave != null &&
                !existingServerSave.fileName.endsWith(".gci.zip", ignoreCase = true) &&
                existingServerSave.fileName.endsWith(".gci", ignoreCase = true)

            if (needsGciMigration) {
                Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | GCI migration: deleting old single-file save | saveId=${existingServerSave!!.id}, fileName=${existingServerSave.fileName}")
                try {
                    val deleteResponse = api.deleteSaves(RomMDeleteSavesRequest(listOf(existingServerSave.id)))
                    if (deleteResponse.isSuccessful) {
                        Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | GCI migration: old save deleted successfully")
                    } else {
                        Logger.warn(TAG, "[SaveSync] UPLOAD gameId=$gameId | GCI migration: failed to delete old save | status=${deleteResponse.code()}")
                    }
                } catch (e: Exception) {
                    Logger.warn(TAG, "[SaveSync] UPLOAD gameId=$gameId | GCI migration: failed to delete old save", e)
                }
            }

            val serverSaveIdToUpdate = if (needsGciMigration) {
                null
            } else if (channelName != null) {
                null
            } else if (serverSaves.isNotEmpty()) {
                existingServerSave?.id
            } else {
                syncEntity?.rommSaveId
            }
            val isUpdate = serverSaveIdToUpdate != null
            val uploadStartTime = System.currentTimeMillis()
            Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | HTTP request | isUpdate=$isUpdate, saveIdToUpdate=$serverSaveIdToUpdate, fileName=$uploadFileName, size=${uploadFile.length()}bytes, serverSavesCount=${serverSaves.size}")

            SaveDebugLogger.logSyncUploadStarted(
                gameId = gameId,
                gameName = game.title,
                channel = channelName,
                sizeBytes = uploadFile.length(),
                contentHash = contentHash
            )

            val requestBody = uploadFile.asRequestBody("application/octet-stream".toMediaType())
            val filePart = MultipartBody.Part.createFormData("saveFile", uploadFileName, requestBody)

            val useAutocleanup = channelName != null
            var response = if (serverSaveIdToUpdate != null) {
                if (deviceId != null) api.updateSaveWithDevice(serverSaveIdToUpdate, deviceId!!, slot = channelName, filePart)
                else api.updateSave(serverSaveIdToUpdate, slot = channelName, filePart)
            } else {
                if (deviceId != null) api.uploadSaveWithDevice(rommId, resolvedEmulatorId, deviceId!!, overwrite = forceOverwrite, slot = channelName, autocleanup = useAutocleanup, autocleanupLimit = if (useAutocleanup) AUTOCLEANUP_LIMIT else null, saveFile = filePart)
                else api.uploadSave(rommId, resolvedEmulatorId, slot = channelName, autocleanup = useAutocleanup, autocleanupLimit = if (useAutocleanup) AUTOCLEANUP_LIMIT else null, filePart)
            }

            if (!response.isSuccessful && response.code() != 409 && serverSaveIdToUpdate != null) {
                Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Update failed, retrying as new upload | status=${response.code()}")
                val retryRequestBody = uploadFile.asRequestBody("application/octet-stream".toMediaType())
                val retryFilePart = MultipartBody.Part.createFormData("saveFile", uploadFileName, retryRequestBody)
                response = if (deviceId != null) api.uploadSaveWithDevice(rommId, resolvedEmulatorId, deviceId!!, overwrite = forceOverwrite, slot = channelName, autocleanup = useAutocleanup, autocleanupLimit = if (useAutocleanup) AUTOCLEANUP_LIMIT else null, saveFile = retryFilePart)
                else api.uploadSave(rommId, resolvedEmulatorId, slot = channelName, autocleanup = useAutocleanup, autocleanupLimit = if (useAutocleanup) AUTOCLEANUP_LIMIT else null, retryFilePart)
            }

            if (response.code() == 409) {
                val serverTime = latestServerSave?.let { parseTimestamp(it.updatedAt) } ?: Instant.now()
                Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Decision=CONFLICT | Server returned 409 (device out of sync)")
                return@withContext SaveSyncResult.Conflict(gameId, localModified, serverTime)
            }

            if (response.isSuccessful) {
                val serverSave = response.body()
                    ?: return@withContext SaveSyncResult.Error("Empty response from server")
                Logger.info(TAG, "[SaveSync] UPLOAD gameId=$gameId | Complete | serverSaveId=${serverSave.id}, fileName=$uploadFileName")
                val serverTimestamp = parseTimestamp(serverSave.updatedAt)
                saveSyncDao.upsert(
                    SaveSyncEntity(
                        id = syncEntity?.id ?: 0,
                        gameId = gameId,
                        rommId = rommId,
                        emulatorId = resolvedEmulatorId,
                        channelName = channelName,
                        rommSaveId = serverSave.id,
                        localSavePath = localPath,
                        localUpdatedAt = serverTimestamp,
                        serverUpdatedAt = serverTimestamp,
                        lastSyncedAt = Instant.now(),
                        syncStatus = SaveSyncEntity.STATUS_SYNCED,
                        lastUploadedHash = contentHash
                    )
                )

                SaveDebugLogger.logSyncUploadCompleted(
                    gameId = gameId,
                    gameName = game.title,
                    channel = channelName,
                    serverId = serverSave.id,
                    durationMs = System.currentTimeMillis() - uploadStartTime
                )

                SaveSyncResult.Success(rommSaveId = serverSave.id, serverTimestamp = serverTimestamp)
            } else {
                val errorBody = response.errorBody()?.string()
                Logger.error(TAG, "[SaveSync] UPLOAD gameId=$gameId | HTTP failed | status=${response.code()}, body=$errorBody")

                SaveDebugLogger.logSyncUploadFailed(
                    gameId = gameId,
                    gameName = game.title,
                    channel = channelName,
                    error = "HTTP ${response.code()}: $errorBody"
                )

                SaveSyncResult.Error("Upload failed: ${response.code()}")
            }
        } catch (e: Exception) {
            Logger.error(TAG, "[SaveSync] UPLOAD gameId=$gameId | Exception during upload", e)

            SaveDebugLogger.logSyncUploadFailed(
                gameId = gameId,
                gameName = game.title,
                channel = channelName,
                error = e.message ?: "Unknown exception"
            )

            SaveSyncResult.Error(e.message ?: "Upload failed")
        } finally {
            if (prepared.isTemporary) fileToUpload.delete()
            tempTrailerFile?.delete()
        }
    }

    suspend fun uploadCacheEntry(
        gameId: Long,
        rommId: Long,
        emulatorId: String,
        channelName: String,
        cacheFile: File,
        contentHash: String?,
        overwrite: Boolean = false
    ): SaveSyncResult = withContext(Dispatchers.IO) {
        Logger.debug(TAG, "[SaveSync] UPLOAD_CACHE gameId=$gameId channel=$channelName | Starting cache upload | file=${cacheFile.name}, size=${cacheFile.length()}, overwrite=$overwrite")
        val api = this@SaveSyncApiClient.api
            ?: return@withContext SaveSyncResult.NotConfigured

        if (!cacheFile.exists() || cacheFile.length() <= MIN_VALID_SAVE_SIZE_BYTES) {
            Logger.warn(TAG, "[SaveSync] UPLOAD_CACHE gameId=$gameId | Cache file missing or empty | exists=${cacheFile.exists()}, size=${cacheFile.length()}")
            return@withContext SaveSyncResult.Error("Cache file not valid")
        }

        val game = gameDao.getById(gameId)
            ?: return@withContext SaveSyncResult.Error("Game not found")

        val resolvedEmulatorId = if (emulatorId == "default" || emulatorId.isBlank()) {
            resolveEmulatorForGame(game) ?: return@withContext SaveSyncResult.Error("Cannot determine emulator")
        } else {
            emulatorId
        }

        if (!overwrite && deviceId != null) {
            if (sessionOnOlderSave[gameId] == true) {
                val serverSaves = checkSavesForGame(gameId, rommId)
                val latestForSlot = serverSaves
                    .filter { it.slot == channelName }
                    .maxByOrNull { parseTimestamp(it.updatedAt) }
                val serverTime = latestForSlot?.let { parseTimestamp(it.updatedAt) } ?: Instant.now()
                val localTime = Instant.ofEpochMilli(cacheFile.lastModified())
                Logger.warn(TAG, "[SaveSync] UPLOAD_CACHE gameId=$gameId | Session started on older save -- conflict for channel=$channelName | local=$localTime, server=$serverTime")
                return@withContext SaveSyncResult.Conflict(gameId, localTime, serverTime)
            }
        }

        try {
            val ext = cacheFile.extension
            val uploadFileName = if (ext.isNotEmpty()) "$channelName.$ext" else channelName

            val requestBody = cacheFile.asRequestBody("application/octet-stream".toMediaType())
            val filePart = MultipartBody.Part.createFormData("saveFile", uploadFileName, requestBody)

            val response = if (deviceId != null) {
                api.uploadSaveWithDevice(
                    romId = rommId,
                    emulator = resolvedEmulatorId,
                    deviceId = deviceId!!,
                    overwrite = overwrite,
                    slot = channelName,
                    autocleanup = true,
                    autocleanupLimit = AUTOCLEANUP_LIMIT,
                    saveFile = filePart
                )
            } else {
                api.uploadSave(
                    romId = rommId,
                    emulator = resolvedEmulatorId,
                    slot = channelName,
                    autocleanup = true,
                    autocleanupLimit = AUTOCLEANUP_LIMIT,
                    saveFile = filePart
                )
            }

            if (response.code() == 409) {
                Logger.debug(TAG, "[SaveSync] UPLOAD_CACHE gameId=$gameId | Server returned 409 (device out of sync for slot=$channelName)")
                return@withContext SaveSyncResult.Conflict(gameId, Instant.now(), Instant.now())
            }

            if (response.isSuccessful) {
                val serverSave = response.body()
                    ?: return@withContext SaveSyncResult.Error("Empty response from server")
                val serverTime = parseTimestamp(serverSave.updatedAt)
                Logger.info(TAG, "[SaveSync] UPLOAD_CACHE gameId=$gameId | Complete | serverSaveId=${serverSave.id}, channel=$channelName, serverTime=$serverTime")
                SaveSyncResult.Success(rommSaveId = serverSave.id, serverTimestamp = serverTime)
            } else {
                val errorBody = response.errorBody()?.string()
                Logger.error(TAG, "[SaveSync] UPLOAD_CACHE gameId=$gameId | HTTP failed | status=${response.code()}, body=$errorBody")
                SaveSyncResult.Error("Upload failed: ${response.code()}")
            }
        } catch (e: Exception) {
            Logger.error(TAG, "[SaveSync] UPLOAD_CACHE gameId=$gameId | Exception during upload", e)
            SaveSyncResult.Error(e.message ?: "Upload failed")
        }
    }

    suspend fun downloadSave(
        gameId: Long,
        emulatorId: String,
        channelName: String? = null,
        skipBackup: Boolean = false
    ): SaveSyncResult = withContext(Dispatchers.IO) {
        Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId emulator=$emulatorId channel=$channelName | Starting download")
        val api = this@SaveSyncApiClient.api
        if (api == null) {
            Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | No API connection")
            return@withContext SaveSyncResult.NotConfigured
        }

        val syncEntity = if (channelName != null) {
            saveSyncDao.getByGameEmulatorAndChannel(gameId, emulatorId, channelName)
        } else {
            saveSyncDao.getByGameAndEmulatorWithDefault(gameId, emulatorId, DEFAULT_SAVE_NAME)
        }
        if (syncEntity == null) {
            Logger.warn(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | No sync entity found in database")
            return@withContext SaveSyncResult.Error("No save tracking found")
        }

        val saveId = syncEntity.rommSaveId
        if (saveId == null) {
            Logger.warn(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | No server save ID in sync entity")
            return@withContext SaveSyncResult.Error("No server save ID")
        }

        val game = gameDao.getById(gameId)
        if (game == null) {
            Logger.warn(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Game not found in database")
            return@withContext SaveSyncResult.Error("Game not found")
        }

        val resolvedEmulatorId = if (emulatorId == "default" || emulatorId.isBlank()) {
            resolveEmulatorForGame(game) ?: run {
                Logger.warn(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Cannot resolve emulator from config")
                return@withContext SaveSyncResult.Error("Cannot determine emulator")
            }
        } else {
            emulatorId
        }
        Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Using emulator=$resolvedEmulatorId (original=$emulatorId)")

        val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(gameId, game.platformId, game.platformSlug)

        val serverSave = try {
            (if (deviceId != null) api.getSaveWithDevice(saveId, deviceId!!) else api.getSave(saveId)).body()
        } catch (e: Exception) {
            Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | getSave API call failed", e)
            return@withContext SaveSyncResult.Error("Failed to get save info: ${e.message}")
        }
        if (serverSave == null) {
            Logger.warn(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Save not found on server | saveId=$saveId")
            return@withContext SaveSyncResult.Error("Save not found on server")
        }

        val config = SavePathRegistry.getConfigIncludingUnsupported(resolvedEmulatorId)
        val isGciFormat = config?.usesGciFormat == true
        val isFolderBased = config?.usesFolderBasedSaves == true &&
            serverSave.fileName.endsWith(".zip", ignoreCase = true) && !isGciFormat
        val isSwitchEmulator = resolvedEmulatorId in SWITCH_EMULATOR_IDS
        Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Save info | fileName=${serverSave.fileName}, isFolderBased=$isFolderBased, isGciFormat=$isGciFormat, isSwitchEmulator=$isSwitchEmulator")

        val folderSyncEnabled = isFolderSaveSyncEnabled()
        if (isFolderBased && !folderSyncEnabled) {
            Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Folder save sync disabled, skipping")
            return@withContext SaveSyncResult.NotConfigured
        }

        val preDownloadTargetPath = if (isGciFormat) {
            null.also {
                Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | GCI format, will download to temp and detect bundle vs single")
            }
        } else if (isFolderBased) {
            if (isSwitchEmulator) {
                null.also {
                    Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Switch emulator, will discover active profile")
                }
            } else {
                val cached = syncEntity.localSavePath
                val discovered = if (cached == null) {
                    savePathResolver.discoverSavePath(
                        emulatorId = resolvedEmulatorId,
                        gameTitle = game.title,
                        platformSlug = game.platformSlug,
                        romPath = game.localPath,
                        cachedTitleId = game.titleId,
                        emulatorPackage = emulatorPackage,
                        gameId = gameId,
                        isFolderSaveSyncEnabled = folderSyncEnabled
                    )
                } else null
                val constructed = if (cached == null && discovered == null) {
                    savePathResolver.constructFolderSavePathWithOverride(resolvedEmulatorId, game.platformSlug, game.localPath, gameId, game.title, game.titleId, emulatorPackage)
                } else null
                (cached ?: discovered ?: constructed).also {
                    Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Folder save path | cached=${cached != null}, discovered=${discovered != null}, constructed=${constructed != null}, path=$it")
                }
            }
        } else {
            val discovered = syncEntity.localSavePath
                ?: savePathResolver.discoverSavePath(
                    emulatorId = resolvedEmulatorId,
                    gameTitle = game.title,
                    platformSlug = game.platformSlug,
                    romPath = game.localPath,
                    cachedTitleId = game.titleId,
                    emulatorPackage = emulatorPackage,
                    gameId = gameId,
                    isFolderSaveSyncEnabled = folderSyncEnabled
                )

            val retried = if (discovered == null && (game.titleId != null || titleDbRepository.getCachedCandidates(gameId).isNotEmpty())) {
                Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Clearing stale titleId cache and retrying")
                titleDbRepository.clearTitleIdCache(gameId)
                savePathResolver.discoverSavePath(
                    emulatorId = resolvedEmulatorId,
                    gameTitle = game.title,
                    platformSlug = game.platformSlug,
                    romPath = game.localPath,
                    cachedTitleId = null,
                    emulatorPackage = emulatorPackage,
                    gameId = gameId,
                    isFolderSaveSyncEnabled = folderSyncEnabled
                )
            } else discovered

            (retried ?: savePathResolver.constructSavePath(resolvedEmulatorId, game.title, game.platformSlug, game.localPath)).also {
                Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | File save path | cached=${syncEntity.localSavePath != null}, discovered=${retried != null}, path=$it")
            }
        }

        if (!isSwitchEmulator && !isFolderBased && !isGciFormat && preDownloadTargetPath == null) {
            Logger.warn(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Cannot determine save path for non-Switch file save")
            return@withContext SaveSyncResult.Error("Cannot determine save path")
        }

        var tempZipFile: File? = null

        try {
            val downloadPath = serverSave.downloadPath
            if (downloadPath == null) {
                Logger.warn(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | No download path in server save response")
                return@withContext SaveSyncResult.Error("No download path available")
            }

            val downloadStartTime = System.currentTimeMillis()
            Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | HTTP request starting | downloadPath=$downloadPath")

            SaveDebugLogger.logSyncDownloadStarted(
                gameId = gameId,
                gameName = game.title,
                channel = channelName,
                serverId = saveId
            )

            val response = try {
                withRetry(tag = "[SaveSync] DOWNLOAD gameId=$gameId") {
                    api.downloadRaw(downloadPath)
                }
            } catch (e: IOException) {
                Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | HTTP failed after retries", e)
                return@withContext SaveSyncResult.Error("Download failed: ${e.message}")
            }
            val contentLength = response.headers()["Content-Length"]?.toLongOrNull() ?: -1
            if (!response.isSuccessful) {
                Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | HTTP failed | status=${response.code()}, message=${response.message()}")
                return@withContext SaveSyncResult.Error("Download failed: ${response.code()}")
            }
            Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | HTTP success | status=${response.code()}, size=${contentLength}bytes")

            var targetPath: String

            if (isFolderBased) {
                if (!hasEnoughDiskSpace(context.cacheDir.absolutePath, contentLength)) {
                    Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Insufficient cache disk space for zip")
                    return@withContext SaveSyncResult.Error("Insufficient disk space")
                }
                tempZipFile = File(context.cacheDir, serverSave.fileName)
                val body = response.body()
                if (body == null) {
                    Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Response body is null")
                    return@withContext SaveSyncResult.Error("Empty response body")
                }
                body.byteStream().use { input ->
                    tempZipFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Saved temp zip | path=${tempZipFile.absolutePath}, size=${tempZipFile.length()}bytes")

                targetPath = if (isSwitchEmulator && config != null) {
                    val resolved = preDownloadTargetPath
                        ?: savePathResolver.resolveSwitchSaveTargetPath(tempZipFile, config, emulatorPackage)
                        ?: savePathResolver.constructFolderSavePath(resolvedEmulatorId, game.platformSlug, game.localPath, emulatorPackage)
                    if (resolved == null) {
                        Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Cannot determine Switch save path from ZIP or ROM")
                        return@withContext SaveSyncResult.Error("Cannot determine save path from ZIP or ROM")
                    }
                    Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Switch save target resolved | path=$resolved, method=${if (preDownloadTargetPath != null) "cached" else "from_zip_or_rom"}")
                    resolved
                } else {
                    preDownloadTargetPath ?: run {
                        Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Cannot determine folder save path")
                        return@withContext SaveSyncResult.Error("Cannot determine save path")
                    }
                }

                val hasLocalHardcore = saveCacheManager.get().hasHardcoreSave(gameId)
                val downloadedHasTrailer = saveArchiver.hasHardcoreTrailer(tempZipFile)
                Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Hardcore check | localHardcore=$hasLocalHardcore, downloadedTrailer=$downloadedHasTrailer")
                if (hasLocalHardcore && !downloadedHasTrailer) {
                    Logger.warn(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | NEEDS_HARDCORE_RESOLUTION | Server save missing trailer")
                    val tempPath = tempZipFile.absolutePath
                    tempZipFile = null
                    return@withContext SaveSyncResult.NeedsHardcoreResolution(
                        tempFilePath = tempPath,
                        gameId = gameId,
                        gameName = game.title,
                        emulatorId = resolvedEmulatorId,
                        targetPath = targetPath,
                        isFolderBased = true,
                        channelName = channelName ?: syncEntity.channelName
                    )
                }

                val existingTarget = File(targetPath)
                if (existingTarget.exists() && !skipBackup) {
                    try {
                        saveCacheManager.get().cacheCurrentSave(gameId, resolvedEmulatorId, targetPath)
                        Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Cached existing save before overwrite")
                    } catch (e: Exception) {
                        Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Backup failed, aborting download to prevent data loss", e)
                        return@withContext SaveSyncResult.Error("Failed to backup existing save before overwrite")
                    }
                }

                val extractedSize = tempZipFile.length() * 3
                if (!hasEnoughDiskSpace(targetPath, extractedSize)) {
                    Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Insufficient disk space for extracted save")
                    return@withContext SaveSyncResult.Error("Insufficient disk space")
                }

                val resolvedTitleId = game.titleId ?: gameDao.getTitleId(gameId)

                val saveContext = SaveContext(
                    config = config!!,
                    romPath = game.localPath,
                    titleId = resolvedTitleId,
                    emulatorPackage = emulatorPackage,
                    gameId = gameId,
                    gameTitle = game.title,
                    platformSlug = game.platformSlug,
                    emulatorId = resolvedEmulatorId,
                    localSavePath = targetPath
                )
                val handler = getHandler(config, game.platformSlug, resolvedEmulatorId)
                val result = handler.extractDownload(tempZipFile, saveContext)
                if (!result.success) {
                    Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Extraction failed | error=${result.error}")
                    return@withContext SaveSyncResult.Error(result.error ?: "Failed to extract save")
                }
                Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Extraction complete | target=${result.targetPath}")
            } else if (isGciFormat) {
                if (!hasEnoughDiskSpace(context.cacheDir.absolutePath, contentLength)) {
                    Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Insufficient cache disk space for GCI save")
                    return@withContext SaveSyncResult.Error("Insufficient disk space")
                }

                if (game.localPath == null) {
                    Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Cannot extract GCI without ROM path")
                    return@withContext SaveSyncResult.Error("Cannot determine save path without ROM")
                }

                val body = response.body()
                if (body == null) {
                    Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Response body is null for GCI save")
                    return@withContext SaveSyncResult.Error("Empty response body")
                }

                val tempGciFile = File(context.cacheDir, "temp_gci_${System.currentTimeMillis()}.tmp")
                try {
                    body.byteStream().use { input ->
                        tempGciFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Saved GCI temp file | path=${tempGciFile.absolutePath}, size=${tempGciFile.length()}bytes")

                    val saveContext = SaveContext(
                        config = config!!,
                        romPath = game.localPath,
                        titleId = game.titleId,
                        emulatorPackage = emulatorPackage,
                        gameId = gameId,
                        gameTitle = game.title,
                        platformSlug = game.platformSlug,
                        emulatorId = resolvedEmulatorId
                    )
                    val result = gciSaveHandler.extractDownload(tempGciFile, saveContext)
                    if (!result.success) {
                        Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | GCI extraction failed | error=${result.error}")
                        return@withContext SaveSyncResult.Error(result.error ?: "Failed to extract GCI save")
                    }
                    targetPath = result.targetPath!!
                    Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | GCI extraction complete | target=$targetPath")
                } finally {
                    tempGciFile.delete()
                }
            } else {
                targetPath = preDownloadTargetPath ?: run {
                    Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Cannot determine file save path")
                    return@withContext SaveSyncResult.Error("Cannot determine save path")
                }

                if (!hasEnoughDiskSpace(targetPath, contentLength)) {
                    Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Insufficient disk space for save file")
                    return@withContext SaveSyncResult.Error("Insufficient disk space")
                }

                val body = response.body()
                if (body == null) {
                    Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Response body is null for file save")
                    return@withContext SaveSyncResult.Error("Empty response body")
                }

                var tempSaveFile: File? = File(context.cacheDir, "temp_save_${System.currentTimeMillis()}.tmp")
                try {
                    body.byteStream().use { input ->
                        tempSaveFile!!.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Saved temp file | path=${tempSaveFile!!.absolutePath}, size=${tempSaveFile!!.length()}bytes")

                    val hasLocalHardcore = saveCacheManager.get().hasHardcoreSave(gameId)
                    val downloadedHasTrailer = saveArchiver.hasHardcoreTrailer(tempSaveFile!!)
                    Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Hardcore check | localHardcore=$hasLocalHardcore, downloadedTrailer=$downloadedHasTrailer")
                    if (hasLocalHardcore && !downloadedHasTrailer) {
                        Logger.warn(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | NEEDS_HARDCORE_RESOLUTION | Server save missing trailer")
                        val tempPath = tempSaveFile!!.absolutePath
                        tempSaveFile = null
                        return@withContext SaveSyncResult.NeedsHardcoreResolution(
                            tempFilePath = tempPath,
                            gameId = gameId,
                            gameName = game.title,
                            emulatorId = resolvedEmulatorId,
                            targetPath = targetPath,
                            isFolderBased = false,
                            channelName = channelName ?: syncEntity.channelName
                        )
                    }

                    val existingTarget = File(targetPath)
                    if (existingTarget.exists() && !skipBackup) {
                        try {
                            saveCacheManager.get().cacheCurrentSave(gameId, resolvedEmulatorId, targetPath)
                            Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Cached existing save before overwrite")
                        } catch (e: Exception) {
                            Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Backup failed, aborting download to prevent data loss", e)
                            tempSaveFile?.delete()
                            return@withContext SaveSyncResult.Error("Failed to backup existing save before overwrite")
                        }
                    }

                    val bytesWithoutTrailer = saveArchiver.readBytesWithoutTrailer(tempSaveFile!!)
                    val written = if (bytesWithoutTrailer != null) {
                        saveArchiver.writeBytesToPath(targetPath, bytesWithoutTrailer)
                    } else {
                        saveArchiver.copyFileToPath(tempSaveFile!!, targetPath)
                    }
                    if (!written) {
                        Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Failed to write file save | path=$targetPath")
                        return@withContext SaveSyncResult.Error("Failed to write save file")
                    }
                    Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | File save written | path=$targetPath")
                } finally {
                    tempSaveFile?.delete()
                }
            }

            saveSyncDao.upsert(
                syncEntity.copy(
                    localSavePath = targetPath,
                    localUpdatedAt = Instant.now(),
                    lastSyncedAt = Instant.now(),
                    syncStatus = SaveSyncEntity.STATUS_SYNCED
                )
            )

            if (isSwitchEmulator && game.titleId == null) {
                val extractedTitleId = File(targetPath).name
                if (switchSaveHandler.isValidTitleId(extractedTitleId)) {
                    gameDao.updateTitleId(gameId, extractedTitleId)
                    Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Cached titleId from save folder | titleId=$extractedTitleId")
                } else if (switchSaveHandler.isValidHexId(extractedTitleId)) {
                    Logger.warn(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Skipping invalid titleId=$extractedTitleId (doesn't start with 01)")
                }
            }

            val effectiveChannelName = channelName ?: syncEntity.channelName
            val romBaseName = game.localPath?.let { File(it).nameWithoutExtension }
            val isLatestSave = effectiveChannelName == null ||
                effectiveChannelName.equals(DEFAULT_SAVE_NAME, ignoreCase = true) ||
                romBaseName != null && effectiveChannelName.equals(romBaseName, ignoreCase = true)

            val cacheChannelName = if (isLatestSave) null else effectiveChannelName
            val cacheIsLocked = !isLatestSave

            try {
                val cacheResult = saveCacheManager.get().cacheCurrentSave(
                    gameId = gameId,
                    emulatorId = resolvedEmulatorId,
                    savePath = targetPath,
                    channelName = cacheChannelName,
                    isLocked = cacheIsLocked
                )
                if (cacheResult is SaveCacheManager.CacheResult.Created) {
                    gameDao.updateActiveSaveTimestamp(gameId, cacheResult.timestamp)
                    gameDao.updateActiveSaveApplied(gameId, false)
                }
            } catch (e: Exception) {
                Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Cache creation failed", e)
            }

            Logger.info(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Complete | path=$targetPath, channel=$effectiveChannelName")

            SaveDebugLogger.logSyncDownloadCompleted(
                gameId = gameId,
                gameName = game.title,
                channel = effectiveChannelName,
                sizeBytes = File(targetPath).let { if (it.isDirectory) it.walkTopDown().sumOf { f -> f.length() } else it.length() },
                durationMs = System.currentTimeMillis() - downloadStartTime
            )

            SaveSyncResult.Success()
        } catch (e: Exception) {
            Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Exception during download", e)

            SaveDebugLogger.logSyncDownloadFailed(
                gameId = gameId,
                gameName = game.title,
                channel = channelName,
                error = e.message ?: "Unknown exception"
            )

            SaveSyncResult.Error(e.message ?: "Download failed")
        } finally {
            tempZipFile?.delete()
        }
    }

    suspend fun downloadSaveById(
        serverSaveId: Long,
        targetPath: String,
        emulatorId: String,
        emulatorPackage: String? = null,
        gameId: Long? = null,
        romPath: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val api = this@SaveSyncApiClient.api ?: return@withContext false

        val serverSave = try {
            (if (deviceId != null) api.getSaveWithDevice(serverSaveId, deviceId!!) else api.getSave(serverSaveId)).body()
        } catch (e: Exception) {
            Logger.error(TAG, "downloadSaveById: getSave failed", e)
            return@withContext false
        } ?: return@withContext false

        val config = SavePathRegistry.getConfigIncludingUnsupported(emulatorId)
        val isGciFormat = config?.usesGciFormat == true
        val isFolderBased = config?.usesFolderBasedSaves == true &&
            serverSave.fileName.endsWith(".zip", ignoreCase = true) && !isGciFormat
        val isSwitchEmulator = emulatorId in SWITCH_EMULATOR_IDS

        var tempZipFile: File? = null

        try {
            val downloadPath = serverSave.downloadPath ?: return@withContext false

            val response = api.downloadRaw(downloadPath)
            if (!response.isSuccessful) {
                Logger.error(TAG, "downloadSaveById failed: ${response.code()}")
                return@withContext false
            }

            if (isGciFormat && romPath != null && gameId != null) {
                val tempGciFile = File(context.cacheDir, "temp_gci_${System.currentTimeMillis()}.tmp")
                try {
                    val body = response.body() ?: return@withContext false
                    body.byteStream().use { input ->
                        tempGciFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    val isZipBundle = tempGciFile.inputStream().use { input ->
                        val magic = ByteArray(2)
                        input.read(magic) == 2 && magic[0] == 0x50.toByte() && magic[1] == 0x4B.toByte()
                    }

                    if (isZipBundle) {
                        tempZipFile = tempGciFile
                        val extractedPaths = gciSaveHandler.extractBundle(tempGciFile, config, romPath, gameId)
                        if (extractedPaths.isEmpty()) {
                            Logger.error(TAG, "downloadSaveById: GCI bundle extraction failed")
                            return@withContext false
                        }
                        Logger.debug(TAG, "downloadSaveById: GCI bundle extracted | paths=${extractedPaths.size}")
                    } else {
                        val gciInfo = GameCubeHeaderParser.parseGciHeader(tempGciFile)
                        val romInfo = GameCubeHeaderParser.parseRomHeader(File(romPath))
                        if (gciInfo != null && romInfo != null) {
                            val gciFilename = GameCubeHeaderParser.buildGciFilename(
                                gciInfo.makerCode, gciInfo.gameId, gciInfo.internalFilename
                            )
                            val basePaths = SavePathRegistry.resolvePath(config, "ngc", null)
                            val baseDir = basePaths.firstOrNull { fal.exists(it) && fal.isDirectory(it) } ?: basePaths.firstOrNull()
                            if (baseDir != null) {
                                val resolvedPath = GameCubeHeaderParser.buildGciPath(baseDir, romInfo.region, gciFilename)
                                val parentDir = File(resolvedPath).parent
                                if (parentDir != null) fal.mkdirs(parentDir)
                                if (fal.copyFile(tempGciFile.absolutePath, resolvedPath)) {
                                    Logger.debug(TAG, "downloadSaveById: GCI single file written | path=$resolvedPath")
                                } else {
                                    Logger.error(TAG, "downloadSaveById: failed to copy GCI file | path=$resolvedPath")
                                }
                            }
                        }
                        tempGciFile.delete()
                    }
                } catch (e: Exception) {
                    tempGciFile.delete()
                    throw e
                }
                return@withContext true
            } else if (isFolderBased) {
                tempZipFile = File(context.cacheDir, serverSave.fileName)
                val body = response.body()
                if (body == null) {
                    Logger.error(TAG, "downloadSaveById: response body is null for folder save")
                    return@withContext false
                }
                body.byteStream().use { input ->
                    tempZipFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val resolvedTargetPath = if (isSwitchEmulator && config != null) {
                    savePathResolver.resolveSwitchSaveTargetPath(tempZipFile, config, emulatorPackage) ?: targetPath
                } else {
                    targetPath
                }

                val targetFolder = File(resolvedTargetPath)
                targetFolder.mkdirs()

                val isJksv = saveArchiver.isJksvFormat(tempZipFile)
                val unzipSuccess = if (isJksv) {
                    saveArchiver.unzipPreservingStructure(tempZipFile, targetFolder, SwitchSaveHandler.JKSV_EXCLUDE_FILES)
                } else {
                    saveArchiver.unzipSingleFolder(tempZipFile, targetFolder)
                }
                if (!unzipSuccess) {
                    return@withContext false
                }
            } else {
                val targetFile = File(targetPath)
                targetFile.parentFile?.mkdirs()

                val body = response.body()
                if (body == null) {
                    Logger.error(TAG, "downloadSaveById: response body is null for file save")
                    return@withContext false
                }
                body.byteStream().use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            true
        } catch (e: Exception) {
            Logger.error(TAG, "downloadSaveById exception", e)
            false
        } finally {
            tempZipFile?.delete()
        }
    }

    suspend fun downloadSaveAsChannel(
        gameId: Long,
        serverSaveId: Long,
        channelName: String,
        emulatorId: String?,
        skipDeviceId: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        val api = this@SaveSyncApiClient.api ?: return@withContext false

        val useDeviceId = if (skipDeviceId) null else deviceId
        val serverSave = try {
            val resp = if (useDeviceId != null) {
                api.getSaveWithDevice(serverSaveId, useDeviceId)
            } else {
                api.getSave(serverSaveId)
            }
            resp.body()
        } catch (e: Exception) {
            Logger.error(TAG, "downloadSaveAsChannel: getSave failed", e)
            return@withContext false
        } ?: return@withContext false

        val response = try {
            val dlPath = serverSave.downloadPath
            if (dlPath != null) {
                api.downloadRaw(dlPath)
            } else if (useDeviceId != null) {
                api.downloadSaveContentWithDevice(
                    serverSaveId, serverSave.fileName, useDeviceId
                )
            } else {
                api.downloadSaveContent(serverSaveId, serverSave.fileName)
            }
        } catch (e: Exception) {
            Logger.error(TAG, "downloadSaveAsChannel: download failed", e)
            return@withContext false
        }

        if (!response.isSuccessful) {
            Logger.error(TAG, "downloadSaveAsChannel: download failed with ${response.code()}")
            return@withContext false
        }

        val tempFile = File(context.cacheDir, "save_channel_${System.currentTimeMillis()}.tmp")
        try {
            response.body()?.byteStream()?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext false

            if (tempFile.length() == 0L) {
                Logger.error(TAG, "downloadSaveAsChannel: empty save data")
                return@withContext false
            }

            val serverTime = parseTimestamp(serverSave.updatedAt)
            val cacheResult = saveCacheManager.get().cacheServerDownload(
                gameId = gameId,
                emulatorId = emulatorId ?: "unknown",
                downloadedFile = tempFile,
                channelName = channelName,
                serverTimestamp = serverTime,
                isLocked = true,
                needsRemoteSync = false,
                rommSaveId = serverSaveId
            )

            Logger.debug(TAG, "downloadSaveAsChannel: result=$cacheResult, channel=$channelName")
            cacheResult.success
        } finally {
            tempFile.delete()
        }
    }

    suspend fun downloadAndCacheSave(
        serverSaveId: Long,
        gameId: Long,
        channelName: String?
    ): Boolean = withContext(Dispatchers.IO) {
        val api = this@SaveSyncApiClient.api ?: return@withContext false

        val serverSave = try {
            val resp = if (deviceId != null) {
                api.getSaveWithDevice(serverSaveId, deviceId!!)
            } else {
                api.getSave(serverSaveId)
            }
            resp.body()
        } catch (e: Exception) {
            Logger.error(TAG, "downloadAndCacheSave: getSave failed", e)
            return@withContext false
        } ?: return@withContext false

        val response = try {
            val dlPath = serverSave.downloadPath
            if (dlPath != null) {
                api.downloadRaw(dlPath)
            } else if (deviceId != null) {
                api.downloadSaveContentWithDevice(
                    serverSaveId, serverSave.fileName, deviceId!!
                )
            } else {
                api.downloadSaveContent(serverSaveId, serverSave.fileName)
            }
        } catch (e: Exception) {
            Logger.error(TAG, "downloadAndCacheSave: download failed", e)
            return@withContext false
        }

        if (!response.isSuccessful) {
            Logger.error(TAG, "downloadAndCacheSave: HTTP ${response.code()}")
            return@withContext false
        }

        val tempFile = File(context.cacheDir, "save_precache_${System.currentTimeMillis()}.tmp")
        try {
            response.body()?.byteStream()?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext false

            if (tempFile.length() == 0L) return@withContext false

            val game = gameDao.getById(gameId)
            val resolvedEmulatorId = game?.let {
                emulatorResolver.getEmulatorIdForGame(gameId, it.platformId, it.platformSlug)
            } ?: "unknown"

            val serverTime = parseTimestamp(serverSave.updatedAt)
            val result = saveCacheManager.get().cacheServerDownload(
                gameId = gameId,
                emulatorId = resolvedEmulatorId,
                downloadedFile = tempFile,
                channelName = channelName,
                serverTimestamp = serverTime,
                isLocked = channelName != null,
                needsRemoteSync = false,
                rommSaveId = serverSaveId
            )

            Logger.debug(TAG, "downloadAndCacheSave: result=$result, saveId=$serverSaveId")
            result.success
        } finally {
            tempFile.delete()
        }
    }

    suspend fun confirmDeviceSynced(saveId: Long) {
        val api = this.api ?: return
        val devId = deviceId ?: return
        try {
            val response = api.confirmSaveDownloaded(saveId, RomMDeviceIdRequest(devId))
            if (response.isSuccessful) {
                Logger.debug(TAG, "[SaveSync] confirmDeviceSynced | saveId=$saveId | Server acknowledged")
            } else {
                Logger.warn(TAG, "[SaveSync] confirmDeviceSynced | saveId=$saveId | HTTP ${response.code()}")
            }
        } catch (e: Exception) {
            Logger.warn(TAG, "[SaveSync] confirmDeviceSynced | saveId=$saveId | Failed", e)
        }
    }

    suspend fun flushPendingDeviceSync(gameId: Long) {
        val pendingSaveId = gameDao.getPendingDeviceSyncSaveId(gameId) ?: return
        Logger.debug(TAG, "[SaveSync] flushPendingDeviceSync | gameId=$gameId, pendingSaveId=$pendingSaveId")
        try {
            confirmDeviceSynced(pendingSaveId)
            gameDao.setPendingDeviceSyncSaveId(gameId, null)
            Logger.debug(TAG, "[SaveSync] flushPendingDeviceSync | gameId=$gameId | Flushed successfully")
        } catch (e: Exception) {
            Logger.warn(TAG, "[SaveSync] flushPendingDeviceSync | gameId=$gameId | Failed, will retry later", e)
        }
    }

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

    suspend fun discoverSavePath(
        emulatorId: String,
        gameTitle: String,
        platformSlug: String,
        romPath: String? = null,
        cachedTitleId: String? = null,
        coreName: String? = null,
        emulatorPackage: String? = null,
        gameId: Long? = null
    ): String? = savePathResolver.discoverSavePath(
        emulatorId = emulatorId,
        gameTitle = gameTitle,
        platformSlug = platformSlug,
        romPath = romPath,
        cachedTitleId = cachedTitleId,
        coreName = coreName,
        emulatorPackage = emulatorPackage,
        gameId = gameId,
        isFolderSaveSyncEnabled = isFolderSaveSyncEnabled()
    )

    suspend fun constructSavePath(
        emulatorId: String,
        gameTitle: String,
        platformSlug: String,
        romPath: String?
    ): String? = savePathResolver.constructSavePath(emulatorId, gameTitle, platformSlug, romPath)

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

    internal fun determineSyncStatus(localTime: Instant?, serverTime: Instant): String {
        if (localTime == null) return SaveSyncEntity.STATUS_SERVER_NEWER
        return when {
            serverTime.isAfter(localTime) -> SaveSyncEntity.STATUS_SERVER_NEWER
            localTime.isAfter(serverTime) -> SaveSyncEntity.STATUS_LOCAL_NEWER
            else -> SaveSyncEntity.STATUS_SYNCED
        }
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
        if (romBaseName == null) return false
        if (baseName.equals(romBaseName, ignoreCase = true)) return true
        if (baseName.startsWith(romBaseName, ignoreCase = true)) {
            val suffix = baseName.drop(romBaseName.length).trim()
            if (suffix.isEmpty()) return true
            if (ROMM_TIMESTAMP_TAG.matches(suffix)) return true
        }
        return false
    }

    private fun isTimestampSaveName(baseName: String): Boolean {
        return TIMESTAMP_ONLY_PATTERN.matches(baseName)
    }

    internal val sessionOnOlderSave = mutableMapOf<Long, Boolean>()

    fun setSessionOnOlderSave(gameId: Long, isOlder: Boolean) {
        sessionOnOlderSave[gameId] = isOlder
    }

    fun clearSessionOnOlderSave(gameId: Long) {
        sessionOnOlderSave.remove(gameId)
    }

    fun isSessionOnOlderSave(gameId: Long): Boolean =
        sessionOnOlderSave[gameId] ?: false

    companion object {
        private const val TAG = "SaveSyncApiClient"
        internal const val DEFAULT_SAVE_NAME = "argosy-latest"
        internal const val MIN_VALID_SAVE_SIZE_BYTES = 100L
        internal const val AUTOCLEANUP_LIMIT = 10
        internal val TIMESTAMP_ONLY_PATTERN = Regex("""^\d{4}-\d{2}-\d{2}[_-]\d{2}[_-]\d{2}[_-]\d{2}$""")
        internal val ROMM_TIMESTAMP_TAG = Regex("""^\[\d{4}-\d{2}-\d{2} \d{2}-\d{2}-\d{2}(-\d+)?\]$""")
        internal val SWITCH_EMULATOR_IDS = setOf(
            "yuzu", "ryujinx", "citron", "strato", "eden", "sudachi", "skyline"
        )
    }
}
