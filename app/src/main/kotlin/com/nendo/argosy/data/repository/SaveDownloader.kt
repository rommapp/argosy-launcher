package com.nendo.argosy.data.repository

import android.content.Context
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.GameCubeHeaderParser
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.remote.romm.RomMDeviceIdRequest
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.sync.SavePathResolver
import com.nendo.argosy.data.sync.platform.GciSaveHandler
import com.nendo.argosy.data.sync.platform.SaveContext
import com.nendo.argosy.data.sync.platform.SwitchSaveHandler
import com.nendo.argosy.data.titledb.TitleDbRepository
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.SaveDebugLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaveDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val saveSyncDao: SaveSyncDao,
    private val emulatorResolver: EmulatorResolver,
    private val gameDao: GameDao,
    private val titleDbRepository: TitleDbRepository,
    private val saveArchiver: SaveArchiver,
    private val savePathResolver: SavePathResolver,
    private val saveCacheManager: dagger.Lazy<SaveCacheManager>,
    private val fal: FileAccessLayer,
    private val switchSaveHandler: SwitchSaveHandler,
    private val gciSaveHandler: GciSaveHandler,
    private val apiClient: dagger.Lazy<SaveSyncApiClient>,
    private val saveUploader: dagger.Lazy<SaveUploader>
) {

    suspend fun downloadSave(
        gameId: Long,
        emulatorId: String,
        channelName: String? = null,
        skipBackup: Boolean = false,
        knownServerSaveId: Long? = null
    ): SaveSyncResult = withContext(Dispatchers.IO) {
        Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId emulator=$emulatorId channel=$channelName | Starting download")
        val client = apiClient.get()
        val api = client.getApi()
        if (api == null) {
            Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | No API connection")
            return@withContext SaveSyncResult.NotConfigured
        }
        val deviceId = client.getDeviceId()

        val game = gameDao.getById(gameId)
        if (game == null) {
            Logger.warn(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Game not found in database")
            return@withContext SaveSyncResult.Error("Game not found")
        }
        if (game.localPath == null) {
            Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Game has no local ROM, skipping save sync")
            return@withContext SaveSyncResult.NoSaveFound
        }

        val resolvedEmulatorId = if (emulatorId.isBlank() || emulatorId == "default") {
            client.resolveEmulatorForGame(game) ?: run {
                Logger.warn(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Cannot resolve emulator from config")
                return@withContext SaveSyncResult.Error("Cannot determine emulator")
            }
        } else {
            emulatorId
        }
        if (resolvedEmulatorId != emulatorId) {
            Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Canonical emulator=$resolvedEmulatorId (original=$emulatorId)")
        }

        val syncEntity = (if (channelName != null) {
            saveSyncDao.getByGameEmulatorAndChannel(gameId, resolvedEmulatorId, channelName)
        } else {
            saveSyncDao.getByGameAndEmulatorWithDefault(gameId, resolvedEmulatorId, SaveSyncApiClient.DEFAULT_SAVE_NAME)
        }) ?: knownServerSaveId?.let { serverId ->
            Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | No sync entity in DB; synthesizing from knownServerSaveId=$serverId")
            SaveSyncEntity(
                gameId = gameId,
                rommId = game.rommId ?: 0,
                emulatorId = resolvedEmulatorId,
                channelName = channelName,
                rommSaveId = serverId,
                syncStatus = SaveSyncEntity.STATUS_SERVER_NEWER
            )
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
        if (game.rommId != null && syncEntity.rommId != game.rommId) {
            Logger.warn(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Stale save_sync row: row.rommId=${syncEntity.rommId} but game.rommId=${game.rommId} (id likely reassigned by rescan); deleting row")
            saveSyncDao.deleteById(syncEntity.id)
            return@withContext SaveSyncResult.NoSaveFound
        }

        val emulatorPackage = emulatorResolver.getEmulatorPackageForGame(gameId, game.platformId, game.platformSlug)
        val preferredCore = client.resolveCoreForGame(game)

        val saveInfoResponse = try {
            if (deviceId != null) api.getSaveWithDevice(saveId, deviceId) else api.getSave(saveId)
        } catch (e: Exception) {
            Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | getSave API call failed", e)
            return@withContext SaveSyncResult.Error("Failed to get save info: ${e.message}")
        }
        val serverSave = saveInfoResponse.body()
        if (serverSave == null) {
            if (saveInfoResponse.code() == 404) {
                Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Server save deleted (HTTP 404), dropping orphan tracking row | saveId=$saveId, syncEntityId=${syncEntity.id}")
                saveSyncDao.deleteById(syncEntity.id)
                return@withContext SaveSyncResult.NoSaveFound
            }
            Logger.warn(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | getSave returned no body | status=${saveInfoResponse.code()}, treating as transient")
            return@withContext SaveSyncResult.Error("Failed to fetch save info: HTTP ${saveInfoResponse.code()}")
        }

        val config = SavePathRegistry.getConfigForPlatform(resolvedEmulatorId, game.platformSlug)
        val isGciFormat = config?.usesGciFormat == true
        val isFolderBased = config?.usesFolderBasedSaves == true &&
            serverSave.fileName.endsWith(".zip", ignoreCase = true) && !isGciFormat
        val isSwitchEmulator = resolvedEmulatorId in SaveSyncApiClient.SWITCH_EMULATOR_IDS
        Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Save info | fileName=${serverSave.fileName}, isFolderBased=$isFolderBased, isGciFormat=$isGciFormat, isSwitchEmulator=$isSwitchEmulator")

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
                val cached = syncEntity.localSavePath?.takeIf { fal.exists(it) }
                if (syncEntity.localSavePath != null && cached == null) {
                    Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Cached folder path no longer exists on disk, re-discovering | stalePath=${syncEntity.localSavePath}")
                }
                val discovered = if (cached == null) {
                    savePathResolver.discoverSavePath(
                        emulatorId = resolvedEmulatorId,
                        gameTitle = game.title,
                        platformSlug = game.platformSlug,
                        romPath = game.localPath,
                        cachedTitleId = game.titleId,
                        coreName = preferredCore,
                        emulatorPackage = emulatorPackage,
                        gameId = gameId
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
            val cachedFilePath = syncEntity.localSavePath?.takeIf { fal.exists(it) }
            if (syncEntity.localSavePath != null && cachedFilePath == null) {
                Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Cached file path no longer exists on disk, re-discovering | stalePath=${syncEntity.localSavePath}")
            }
            val discovered = cachedFilePath
                ?: savePathResolver.discoverSavePath(
                    emulatorId = resolvedEmulatorId,
                    gameTitle = game.title,
                    platformSlug = game.platformSlug,
                    romPath = game.localPath,
                    cachedTitleId = game.titleId,
                    coreName = preferredCore,
                    emulatorPackage = emulatorPackage,
                    gameId = gameId
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
                    coreName = preferredCore,
                    emulatorPackage = emulatorPackage,
                    gameId = gameId
                )
            } else discovered

            (retried ?: savePathResolver.constructSavePath(resolvedEmulatorId, game.title, game.platformSlug, game.localPath, preferredCore, game.titleId)).also {
                Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | File save path | cached=${syncEntity.localSavePath != null}, discovered=${retried != null}, path=$it")
            }
        }

        if (!isSwitchEmulator && !isFolderBased && !isGciFormat && preDownloadTargetPath == null) {
            Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Cannot determine save path for non-Switch file save | emulator=$resolvedEmulatorId, package=$emulatorPackage, core=$preferredCore, romPath=${game.localPath}, platformSlug=${game.platformSlug}")
            return@withContext SaveSyncResult.NoSaveFound
        }

        if (preDownloadTargetPath != null && serverSave.contentHash != null) {
            val cachedMatch = saveCacheManager.get().findCachedByHash(gameId, serverSave.contentHash)
            if (cachedMatch != null) {
                Logger.info(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Cache hit (hash=${serverSave.contentHash}), restoring from cacheId=${cachedMatch.id} instead of fetching content")
                val existingTarget = File(preDownloadTargetPath)
                if (existingTarget.exists() && !skipBackup) {
                    try {
                        saveCacheManager.get().cacheCurrentSave(gameId, resolvedEmulatorId, preDownloadTargetPath)
                    } catch (e: Exception) {
                        Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Backup failed before cache-hit restore", e)
                        return@withContext SaveSyncResult.Error("Failed to backup existing save before restore")
                    }
                }
                val restored = saveCacheManager.get().restoreSave(cachedMatch.id, preDownloadTargetPath)
                if (!restored) {
                    Logger.warn(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | restoreSave failed, falling through to network download")
                } else {
                    val serverTimestamp = SaveSyncApiClient.parseTimestamp(serverSave.updatedAt)
                    val currentDeviceSync = serverSave.deviceSyncs?.firstOrNull { it.isCurrent }
                    saveSyncDao.upsert(
                        syncEntity.copy(
                            rommSaveId = serverSave.id,
                            localSavePath = preDownloadTargetPath,
                            localUpdatedAt = serverTimestamp,
                            serverUpdatedAt = serverTimestamp,
                            lastSyncedAt = Instant.now(),
                            syncStatus = SaveSyncEntity.STATUS_SYNCED,
                            lastUploadedHash = serverSave.contentHash,
                            lastSyncDeviceId = currentDeviceSync?.deviceId ?: deviceId ?: syncEntity.lastSyncDeviceId,
                            lastSyncDeviceName = currentDeviceSync?.deviceName ?: syncEntity.lastSyncDeviceName
                        )
                    )
                    Logger.info(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Complete (cache-hit) | path=$preDownloadTargetPath")
                    return@withContext SaveSyncResult.Success(rommSaveId = serverSave.id, serverTimestamp = serverTimestamp)
                }
            }
        }

        var tempZipFile: File? = null

        try {
            val downloadPath = serverSave.downloadPath
            val caps = client.getCapabilities()
            // Only RomM 4.9+ supports the optimistic=false fence; older servers use the raw asset URL.
            val useDeviceEndpoint = caps.supportsDeviceSyncMode && deviceId != null
            if (!useDeviceEndpoint && downloadPath == null) {
                Logger.warn(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | No download path in server save response")
                return@withContext SaveSyncResult.Error("No download path available")
            }

            // Skip if a previous attempt at this exact server-side timestamp produced
            // a corrupt zip; resume only when the server copy changes (re-upload).
            val serverFingerprint = (downloadPath?.substringAfter("?timestamp=", "") ?: "")
                .ifEmpty { serverSave.updatedAt ?: "" }
            if (serverFingerprint.isNotEmpty()) {
                val cachedCorrupt = saveSyncDao.getCorruptZipTimestamp(gameId, resolvedEmulatorId, channelName)
                if (cachedCorrupt == serverFingerprint) {
                    Logger.warn(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Skipping — server zip known corrupt at this timestamp; re-upload from device to recover | timestamp=$serverFingerprint")
                    return@withContext SaveSyncResult.Error("Server zip is corrupt; re-upload to recover")
                } else if (cachedCorrupt != null) {
                    Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Server timestamp changed since corrupt-zip mark, retrying | wasCorruptAt=$cachedCorrupt, now=$serverFingerprint")
                    saveSyncDao.clearCorruptZip(gameId, resolvedEmulatorId, channelName)
                }
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
                client.withRetry(tag = "[SaveSync] DOWNLOAD gameId=$gameId") {
                    if (useDeviceEndpoint) {
                        api.downloadSaveContentWithDevice(
                            saveId = saveId,
                            deviceId = deviceId!!,
                            optimistic = false
                        )
                    } else {
                        api.downloadRaw(downloadPath!!)
                    }
                }
            } catch (e: IOException) {
                Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | HTTP failed after retries", e)
                return@withContext SaveSyncResult.Error("Download failed: ${e.message}")
            }
            val contentLength = response.headers()["Content-Length"]?.toLongOrNull() ?: -1
            if (!response.isSuccessful) {
                // 404 content = orphan (metadata exists, file missing); drop row like the getSave 404 path.
                if (response.code() == 404) {
                    Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Content missing (HTTP 404), dropping orphan tracking row | saveId=$saveId, syncEntityId=${syncEntity.id}")
                    saveSyncDao.deleteById(syncEntity.id)
                    return@withContext SaveSyncResult.NoSaveFound
                }
                Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | HTTP failed | status=${response.code()}, message=${response.message()}")
                return@withContext SaveSyncResult.Error("Download failed: ${response.code()}")
            }
            Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | HTTP success | status=${response.code()}, size=${contentLength}bytes")

            var targetPath: String

            if (isFolderBased) {
                if (!client.hasEnoughDiskSpace(context.cacheDir.absolutePath, contentLength)) {
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
                        ?: savePathResolver.constructFolderSavePathWithOverride(resolvedEmulatorId, game.platformSlug, game.localPath, gameId, game.title, game.titleId, emulatorPackage)
                    if (resolved == null) {
                        Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Cannot determine Switch save path from ZIP or ROM | emulator=$resolvedEmulatorId, package=$emulatorPackage, romPath=${game.localPath}")
                        return@withContext SaveSyncResult.NoSaveFound
                    }
                    Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Switch save target resolved | path=$resolved, method=${if (preDownloadTargetPath != null) "cached" else "from_zip_or_rom"}")
                    resolved
                } else {
                    preDownloadTargetPath ?: run {
                        Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Cannot determine folder save path, skipping (likely missing titleId mapping)")
                        return@withContext SaveSyncResult.NoSaveFound
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
                if (!client.hasEnoughDiskSpace(targetPath, extractedSize)) {
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
                    localSavePath = targetPath,
                    coreName = preferredCore
                )
                val handler = client.getHandler(config, game.platformSlug, resolvedEmulatorId)
                val result = handler.extractDownload(tempZipFile, saveContext)
                if (!result.success) {
                    Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Extraction failed | error=${result.error}")
                    if (result.corruptZip && serverFingerprint.isNotEmpty()) {
                        saveSyncDao.markCorruptZip(
                            gameId = gameId,
                            emulatorId = resolvedEmulatorId,
                            channelName = channelName,
                            serverTimestamp = serverFingerprint,
                            error = result.error ?: "corrupt zip"
                        )
                        // Auto-recovery: server zip is corrupt, push the (good) local
                        // copy over it. Successful upload will produce a new server
                        // timestamp, so the next sync clears the corrupt mark and
                        // resumes normal flow. If the upload also fails, we fall
                        // through with the original error and the skip-cache prevents
                        // futile retries.
                        Logger.warn(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Server zip corrupt — auto-recovering by uploading local save | timestamp=$serverFingerprint")
                        val recoveryResult = try {
                            saveUploader.get().uploadSave(
                                gameId = gameId,
                                emulatorId = resolvedEmulatorId,
                                channelName = channelName,
                                forceOverwrite = true,
                                isHardcore = false
                            )
                        } catch (e: Exception) {
                            Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Auto-recovery upload threw", e)
                            SaveSyncResult.Error(e.message ?: "auto-recovery threw")
                        }
                        if (recoveryResult is SaveSyncResult.Success) {
                            Logger.info(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Auto-recovery succeeded — corrupt server zip overwritten with local copy")
                            saveSyncDao.clearCorruptZip(gameId, resolvedEmulatorId, channelName)
                            return@withContext recoveryResult
                        } else {
                            Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Auto-recovery upload failed | result=$recoveryResult")
                        }
                    }
                    return@withContext SaveSyncResult.Error(result.error ?: "Failed to extract save")
                }
                Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Extraction complete | target=${result.targetPath}")
            } else if (isGciFormat) {
                if (!client.hasEnoughDiskSpace(context.cacheDir.absolutePath, contentLength)) {
                    Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Insufficient cache disk space for GCI save")
                    return@withContext SaveSyncResult.Error("Insufficient disk space")
                }

                if (game.localPath == null) {
                    Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Cannot extract GCI without ROM path")
                    return@withContext SaveSyncResult.NoSaveFound
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
                        emulatorId = resolvedEmulatorId,
                        coreName = preferredCore
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
                    Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Cannot determine file save path | emulator=$resolvedEmulatorId, package=$emulatorPackage, core=$preferredCore")
                    return@withContext SaveSyncResult.NoSaveFound
                }

                if (!client.hasEnoughDiskSpace(targetPath, contentLength)) {
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

            val downloadedHash = saveCacheManager.get().calculateLocalSaveHash(targetPath)
            val serverTimestamp = SaveSyncApiClient.parseTimestamp(serverSave.updatedAt)
            File(targetPath).setLastModified(serverTimestamp.toEpochMilli())
            val uploaderDeviceSync = serverSave.deviceSyncs
                ?.filter { !it.isCurrent }
                ?.maxByOrNull { it.lastSyncedAt ?: "" }
            saveSyncDao.upsert(
                syncEntity.copy(
                    localSavePath = targetPath,
                    localUpdatedAt = serverTimestamp,
                    lastSyncedAt = Instant.now(),
                    serverUpdatedAt = serverTimestamp,
                    lastUploadedHash = serverSave.contentHash,
                    syncStatus = SaveSyncEntity.STATUS_SYNCED,
                    lastSyncDeviceId = uploaderDeviceSync?.deviceId ?: syncEntity.lastSyncDeviceId,
                    lastSyncDeviceName = uploaderDeviceSync?.deviceName ?: syncEntity.lastSyncDeviceName
                )
            )

            Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | wrote lastUploadedHash=${serverSave.contentHash} serverUpdatedAt=$serverTimestamp")
            confirmDeviceSynced(serverSave.id)

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
                effectiveChannelName.equals(SaveSyncApiClient.DEFAULT_SAVE_NAME, ignoreCase = true) ||
                romBaseName != null && effectiveChannelName.equals(romBaseName, ignoreCase = true)

            val cacheChannelName = if (isLatestSave) null else effectiveChannelName
            val cacheIsLocked = !isLatestSave

            try {
                val cacheResult = saveCacheManager.get().cacheCurrentSave(
                    gameId = gameId,
                    emulatorId = resolvedEmulatorId,
                    savePath = targetPath,
                    channelName = cacheChannelName,
                    isLocked = cacheIsLocked,
                    precomputedContentHash = serverSave.contentHash
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
        val client = apiClient.get()
        val api = client.getApi() ?: return@withContext false
        val deviceId = client.getDeviceId()

        val serverSave = try {
            (if (deviceId != null) api.getSaveWithDevice(serverSaveId, deviceId) else api.getSave(serverSaveId)).body()
        } catch (e: Exception) {
            Logger.error(TAG, "downloadSaveById: getSave failed", e)
            return@withContext false
        } ?: return@withContext false

        val platformSlug = gameId?.let { gameDao.getById(it)?.platformSlug }
        val config = if (platformSlug != null) {
            SavePathRegistry.getConfigForPlatform(emulatorId, platformSlug)
        } else {
            SavePathRegistry.getConfigIncludingUnsupported(emulatorId)
        }
        val isGciFormat = config?.usesGciFormat == true
        val isFolderBased = config?.usesFolderBasedSaves == true &&
            serverSave.fileName.endsWith(".zip", ignoreCase = true) && !isGciFormat
        val isSwitchEmulator = emulatorId in SaveSyncApiClient.SWITCH_EMULATOR_IDS

        var tempZipFile: File? = null

        try {
            val response = try {
                val dlPath = serverSave.downloadPath
                when {
                    dlPath != null -> api.downloadRaw(dlPath)
                    deviceId != null -> api.downloadSaveContentWithDevice(serverSaveId, deviceId)
                    else -> api.downloadSaveContent(serverSaveId)
                }
            } catch (e: Exception) {
                Logger.error(TAG, "downloadSaveById: content download failed", e)
                return@withContext false
            }
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
        val client = apiClient.get()
        val api = client.getApi() ?: return@withContext false

        val useDeviceId = if (skipDeviceId) null else client.getDeviceId()
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
                api.downloadSaveContentWithDevice(serverSaveId, useDeviceId)
            } else {
                api.downloadSaveContent(serverSaveId)
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

            val serverTime = SaveSyncApiClient.parseTimestamp(serverSave.updatedAt)
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
        val client = apiClient.get()
        val api = client.getApi() ?: return@withContext false
        val deviceId = client.getDeviceId()

        val serverSave = try {
            val resp = if (deviceId != null) {
                api.getSaveWithDevice(serverSaveId, deviceId)
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
                api.downloadSaveContentWithDevice(serverSaveId, deviceId)
            } else {
                api.downloadSaveContent(serverSaveId)
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

            val serverTime = SaveSyncApiClient.parseTimestamp(serverSave.updatedAt)
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
        val client = apiClient.get()
        val api = client.getApi() ?: return
        val devId = client.getDeviceId() ?: return
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

    companion object {
        private const val TAG = "SaveDownloader"
    }
}
