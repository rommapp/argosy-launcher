package com.nendo.argosy.data.repository

import android.content.Context
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.remote.romm.RomMDeleteSavesRequest
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.sync.SavePathResolver
import com.nendo.argosy.data.sync.platform.SaveContext
import com.nendo.argosy.data.sync.platform.SwitchSaveHandler
import com.nendo.argosy.data.titledb.TitleDbRepository
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.SaveDebugLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaveUploader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val saveSyncDao: SaveSyncDao,
    private val emulatorResolver: EmulatorResolver,
    private val gameDao: GameDao,
    private val titleDbRepository: TitleDbRepository,
    private val saveArchiver: SaveArchiver,
    private val savePathResolver: SavePathResolver,
    private val fal: FileAccessLayer,
    private val switchSaveHandler: SwitchSaveHandler,
    private val apiClient: dagger.Lazy<SaveSyncApiClient>,
    private val conflictDetector: ConflictDetector
) {

    suspend fun uploadSave(
        gameId: Long,
        emulatorId: String,
        channelName: String? = null,
        forceOverwrite: Boolean = false,
        isHardcore: Boolean = false
    ): SaveSyncResult = withContext(Dispatchers.IO) {
        Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId emulator=$emulatorId channel=$channelName | Starting upload")
        val client = apiClient.get()
        val api = client.getApi()
        if (api == null) {
            Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | No API connection")
            return@withContext SaveSyncResult.NotConfigured
        }
        val deviceId = client.getDeviceId()

        val syncEntity = if (channelName != null) {
            saveSyncDao.getByGameEmulatorAndChannel(gameId, emulatorId, channelName)
        } else {
            saveSyncDao.getByGameAndEmulatorWithDefault(gameId, emulatorId, SaveSyncApiClient.DEFAULT_SAVE_NAME)
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
            client.resolveEmulatorForGame(game) ?: run {
                Logger.warn(TAG, "[SaveSync] UPLOAD gameId=$gameId | Cannot resolve emulator from config")
                return@withContext SaveSyncResult.Error("Cannot determine emulator")
            }
        } else {
            emulatorId
        }
        Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Using emulator=$resolvedEmulatorId (original=$emulatorId)")

        val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(gameId, game.platformId, game.platformSlug)
        val preferredCore = client.resolveCoreForGame(game)

        val folderSyncEnabled = client.isFolderSaveSyncEnabled()
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
                coreName = preferredCore,
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
                coreName = preferredCore,
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

        val config = SavePathRegistry.getConfigForPlatform(resolvedEmulatorId, game.platformSlug)
        val isDirectory = fal.isDirectory(localPath)
        val isFolderBased = config?.usesFolderBasedSaves == true && isDirectory
        val isGciBundle = config?.usesGciFormat == true

        if (isFolderBased && !client.isFolderSaveSyncEnabled()) {
            Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Folder save sync disabled, skipping")
            return@withContext SaveSyncResult.NotConfigured
        }

        val localModified = if (isDirectory) {
            Instant.ofEpochMilli(savePathResolver.findNewestFileTime(localPath))
        } else {
            Instant.ofEpochMilli(fal.lastModified(localPath))
        }
        Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Local modified time | localModified=$localModified")

        val handler = client.getHandler(config, game.platformSlug, resolvedEmulatorId)
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
            localSavePath = localPath,
            coreName = preferredCore
        )

        val prepared = handler.prepareForUpload(localPath, saveContext)
        if (prepared == null) {
            Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Handler returned no prepared save")
            return@withContext SaveSyncResult.NoSaveFound
        }

        val fileToUpload = prepared.file
        Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Save prepared | file=${fileToUpload.absolutePath}, size=${fileToUpload.length()}bytes, isTemporary=${prepared.isTemporary}")

        if (fileToUpload.length() <= SaveSyncApiClient.MIN_VALID_SAVE_SIZE_BYTES) {
            Logger.warn(TAG, "[SaveSync] UPLOAD gameId=$gameId | Rejecting empty save | size=${fileToUpload.length()}bytes, minRequired=${SaveSyncApiClient.MIN_VALID_SAVE_SIZE_BYTES}")
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
            val latestSlotName = romBaseName ?: SaveSyncApiClient.DEFAULT_SAVE_NAME

            val uploadFileName = if (channelName != null) {
                val ext = fileToUpload.extension
                if (ext.isNotEmpty()) "$channelName.$ext" else channelName
            } else {
                val baseName = romBaseName ?: SaveSyncApiClient.DEFAULT_SAVE_NAME
                val ext = fileToUpload.extension
                if (ext.isNotEmpty()) "$baseName.$ext" else baseName
            }

            val serverSaves = client.checkSavesForGame(gameId, rommId)
            Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Server saves found | count=${serverSaves.size}, files=${serverSaves.map { it.fileName }}")

            val latestServerSave = conflictDetector.pickLatestServerSave(serverSaves, channelName, romBaseName, isGciBundle)
            val existingServerSave = conflictDetector.pickExistingServerSave(serverSaves, channelName, romBaseName, isGciBundle)

            val conflictDecision = conflictDetector.detectUploadConflict(
                gameId = gameId,
                channelName = channelName,
                forceOverwrite = forceOverwrite,
                currentDeviceId = deviceId,
                latestServerSave = latestServerSave,
                localModified = localModified,
                preSyncTimeIfSession = syncEntity?.lastSyncedAt
            )
            if (conflictDecision != null && conflictDecision.isConflict) {
                return@withContext SaveSyncResult.Conflict(
                    gameId,
                    conflictDecision.localTimestamp,
                    conflictDecision.serverTimestamp,
                    conflictDecision.serverDeviceName
                )
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

            val uploadStartTime = System.currentTimeMillis()
            Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | HTTP request | fileName=$uploadFileName, size=${uploadFile.length()}bytes, serverSavesCount=${serverSaves.size}")

            SaveDebugLogger.logSyncUploadStarted(
                gameId = gameId,
                gameName = game.title,
                channel = channelName,
                sizeBytes = uploadFile.length(),
                contentHash = contentHash
            )

            val requestBody = uploadFile.asRequestBody("application/octet-stream".toMediaType())
            val filePart = MultipartBody.Part.createFormData("saveFile", uploadFileName, requestBody)

            val uploadSlot = channelName ?: latestSlotName
            val response = if (deviceId != null) {
                api.uploadSaveWithDevice(rommId, resolvedEmulatorId, deviceId, overwrite = forceOverwrite, slot = uploadSlot, autocleanup = true, autocleanupLimit = SaveSyncApiClient.AUTOCLEANUP_LIMIT, saveFile = filePart)
            } else {
                api.uploadSave(rommId, resolvedEmulatorId, slot = uploadSlot, autocleanup = true, autocleanupLimit = SaveSyncApiClient.AUTOCLEANUP_LIMIT, filePart)
            }

            if (response.code() == 409) {
                val serverTime = latestServerSave?.let { SaveSyncApiClient.parseTimestamp(it.updatedAt) } ?: Instant.now()
                val conflictLocalTime = syncEntity?.lastSyncedAt ?: localModified
                Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Decision=CONFLICT | Server returned 409 (device out of sync)")
                return@withContext SaveSyncResult.Conflict(
                    gameId,
                    conflictLocalTime,
                    serverTime,
                    conflictDetector.extractUploaderDeviceName(latestServerSave, deviceId)
                )
            }

            if (response.isSuccessful) {
                val serverSave = response.body()
                    ?: return@withContext SaveSyncResult.Error("Empty response from server")
                Logger.info(TAG, "[SaveSync] UPLOAD gameId=$gameId | Complete | serverSaveId=${serverSave.id}, fileName=$uploadFileName")
                val serverTimestamp = SaveSyncApiClient.parseTimestamp(serverSave.updatedAt)
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
        val client = apiClient.get()
        val api = client.getApi()
            ?: return@withContext SaveSyncResult.NotConfigured
        val deviceId = client.getDeviceId()

        if (!cacheFile.exists() || cacheFile.length() <= SaveSyncApiClient.MIN_VALID_SAVE_SIZE_BYTES) {
            Logger.warn(TAG, "[SaveSync] UPLOAD_CACHE gameId=$gameId | Cache file missing or empty | exists=${cacheFile.exists()}, size=${cacheFile.length()}")
            return@withContext SaveSyncResult.Error("Cache file not valid")
        }

        val game = gameDao.getById(gameId)
            ?: return@withContext SaveSyncResult.Error("Game not found")

        val resolvedEmulatorId = if (emulatorId == "default" || emulatorId.isBlank()) {
            client.resolveEmulatorForGame(game) ?: return@withContext SaveSyncResult.Error("Cannot determine emulator")
        } else {
            emulatorId
        }

        if (!overwrite && deviceId != null) {
            if (conflictDetector.isSessionOnOlderSave(gameId)) {
                val serverSaves = client.checkSavesForGame(gameId, rommId)
                val latestForSlot = serverSaves
                    .filter { it.slot != null && SaveSyncApiClient.equalsNormalized(it.slot, channelName) }
                    .maxByOrNull { SaveSyncApiClient.parseTimestamp(it.updatedAt) }
                val serverTime = latestForSlot?.let { SaveSyncApiClient.parseTimestamp(it.updatedAt) } ?: Instant.now()
                val preSyncTime = saveSyncDao.getByGameEmulatorAndChannel(gameId, emulatorId, channelName)?.lastSyncedAt
                    ?: Instant.ofEpochMilli(cacheFile.lastModified())
                Logger.warn(TAG, "[SaveSync] UPLOAD_CACHE gameId=$gameId | Session started on older save -- conflict for channel=$channelName | preSyncTime=$preSyncTime, server=$serverTime")
                return@withContext SaveSyncResult.Conflict(
                    gameId,
                    preSyncTime,
                    serverTime,
                    conflictDetector.extractUploaderDeviceName(latestForSlot, deviceId)
                )
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
                    deviceId = deviceId,
                    overwrite = overwrite,
                    slot = channelName,
                    autocleanup = true,
                    autocleanupLimit = SaveSyncApiClient.AUTOCLEANUP_LIMIT,
                    saveFile = filePart
                )
            } else {
                api.uploadSave(
                    romId = rommId,
                    emulator = resolvedEmulatorId,
                    slot = channelName,
                    autocleanup = true,
                    autocleanupLimit = SaveSyncApiClient.AUTOCLEANUP_LIMIT,
                    saveFile = filePart
                )
            }

            if (response.code() == 409) {
                val conflictSaves = try { client.checkSavesForGame(gameId, rommId) } catch (_: Exception) { emptyList() }
                val conflictSlotSave = conflictSaves
                    .filter { it.slot != null && SaveSyncApiClient.equalsNormalized(it.slot, channelName) }
                    .maxByOrNull { SaveSyncApiClient.parseTimestamp(it.updatedAt) }
                val serverTime = conflictSlotSave?.let { SaveSyncApiClient.parseTimestamp(it.updatedAt) } ?: Instant.now()
                val conflictLocalTime = saveSyncDao.getByGameEmulatorAndChannel(gameId, emulatorId, channelName)?.lastSyncedAt
                    ?: Instant.ofEpochMilli(cacheFile.lastModified())
                Logger.debug(TAG, "[SaveSync] UPLOAD_CACHE gameId=$gameId | Server returned 409 (device out of sync for slot=$channelName) | preSyncTime=$conflictLocalTime, server=$serverTime")
                return@withContext SaveSyncResult.Conflict(
                    gameId,
                    conflictLocalTime,
                    serverTime,
                    conflictDetector.extractUploaderDeviceName(conflictSlotSave, deviceId)
                )
            }

            if (response.isSuccessful) {
                val serverSave = response.body()
                    ?: return@withContext SaveSyncResult.Error("Empty response from server")
                val serverTime = SaveSyncApiClient.parseTimestamp(serverSave.updatedAt)
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

    companion object {
        private const val TAG = "SaveUploader"
    }
}
