package com.nendo.argosy.data.repository

import android.content.Context
import com.nendo.argosy.data.emulator.ArchiveRomNaming
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.GameCubeHeaderParser
import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMDeviceIdRequest
import com.nendo.argosy.data.remote.romm.originDeviceName
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.sync.SavePathResolver
import com.nendo.argosy.data.sync.SaveUnitResolver
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.sync.platform.ExtractResult
import com.nendo.argosy.data.sync.platform.FolderSaveHandler
import com.nendo.argosy.data.sync.platform.GciSaveHandler
import com.nendo.argosy.data.sync.platform.PlatformSaveHandlerRegistry
import com.nendo.argosy.data.sync.platform.SaveContext
import com.nendo.argosy.data.sync.platform.SigilRestore
import com.nendo.argosy.data.sync.platform.SigilSaveHandler
import com.nendo.argosy.data.sync.platform.SwitchSaveHandler
import com.nendo.argosy.data.sync.platform.UnitSaveHandler
import com.nendo.argosy.data.titledb.TitleDbRepository
import com.nendo.argosy.util.FileNames
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.SaveDebugLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
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
    private val saveCacheDao: SaveCacheDao,
    private val emulatorResolver: EmulatorResolver,
    private val gameDao: GameDao,
    private val activeSaveRepository: ActiveSaveRepository,
    private val titleDbRepository: TitleDbRepository,
    private val titleIdExtractor: com.nendo.argosy.data.emulator.TitleIdExtractor,
    private val saveArchiver: SaveArchiver,
    private val savePathResolver: SavePathResolver,
    private val syncPreferencesRepository: SyncPreferencesRepository,
    private val saveCacheManager: dagger.Lazy<SaveCacheManager>,
    private val fal: FileAccessLayer,
    private val switchSaveHandler: SwitchSaveHandler,
    private val gciSaveHandler: GciSaveHandler,
    private val apiClient: dagger.Lazy<SaveSyncApiClient>,
    private val saveUploader: dagger.Lazy<SaveUploader>,
    private val emulatorSaveConfigRepository: EmulatorSaveConfigRepository,
    private val unitSaveHandler: UnitSaveHandler,
    private val saveUnitResolver: SaveUnitResolver,
    private val sigilSaveHandler: SigilSaveHandler
) {

    private suspend fun unitPrimaryTarget(
        anchorPath: String,
        game: GameEntity,
        emulatorId: String,
        coreName: String?
    ): String? {
        if (emulatorId !in PlatformSaveHandlerRegistry.UNIT_EMULATOR_IDS) return null
        val layout = saveUnitResolver.layoutFor(game, emulatorId, coreName)?.takeIf { it.isNotBlank() } ?: return null
        val contentName = game.localPath
            ?.let { ArchiveRomNaming.syncContentName(File(it), game.platformSlug, emulatorId) }
            ?: return null
        return saveUnitResolver.expectedPrimaryPath(anchorPath, layout, game.platformSlug, contentName, game)
            ?.takeIf { it.isNotBlank() }
    }

    private suspend fun unitBundleResult(
        tempFile: File,
        targetPath: String,
        config: SavePathConfig?,
        game: GameEntity,
        emulatorId: String,
        emulatorPackage: String?,
        coreName: String?
    ): ExtractResult? {
        if (config == null || coreName == null) return null
        if (emulatorId !in PlatformSaveHandlerRegistry.UNIT_EMULATOR_IDS) return null
        val saveContext = SaveContext(
            config = config,
            romPath = game.localPath,
            saveId = game.saveId ?: game.titleId,
            emulatorPackage = emulatorPackage,
            gameId = game.id,
            gameTitle = game.title,
            platformSlug = game.platformSlug,
            emulatorId = emulatorId,
            localSavePath = targetPath,
            coreName = coreName,
            basePathOverride = overrideBaseFor(config, game.platformSlug)
        )
        return unitSaveHandler.extractBundle(tempFile, saveContext)
    }

    /**
     * The folder the user pointed this emulator at, keyed by the platform-aware config id so a
     * multi-platform emulator does not read the other platform's override. Handed to handlers that
     * build their own destination; without it a restore lands in the packaged default while
     * discovery keeps reading the override.
     */
    private suspend fun overrideBaseFor(config: SavePathConfig?, platformSlug: String): String? =
        config?.emulatorId?.let {
            emulatorSaveConfigRepository.resolveEffectiveSavePath(it, platformSlug)
        }?.takeIf { it.isNotBlank() }

    private val downloadMutexes = java.util.concurrent.ConcurrentHashMap<Long, kotlinx.coroutines.sync.Mutex>()

    suspend fun downloadSave(
        gameId: Long,
        emulatorId: String,
        channelName: String? = null,
        skipBackup: Boolean = false,
        knownServerSaveId: Long? = null,
        fromSnapshot: Boolean = false
    ): SaveSyncResult = downloadMutexes.computeIfAbsent(gameId) { kotlinx.coroutines.sync.Mutex() }.withLock {
        downloadSaveLocked(gameId, emulatorId, channelName, skipBackup, knownServerSaveId, fromSnapshot)
    }

    private suspend fun downloadSaveLocked(
        gameId: Long,
        emulatorId: String,
        channelName: String?,
        skipBackup: Boolean,
        knownServerSaveId: Long?,
        fromSnapshot: Boolean
    ): SaveSyncResult = withContext(Dispatchers.IO) {
        Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId emulator=$emulatorId channel=$channelName | Starting download")
        val secureSaves = syncPreferencesRepository.isSecureSaves()
        val ownerUserId = syncPreferencesRepository.getRommUserId()
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

        val syncEntity = saveSyncDao.getByGameEmulatorAndChannel(
            gameId, resolvedEmulatorId, SaveSyncApiClient.syncKeyOf(channelName), ownerUserId
        ) ?: knownServerSaveId?.let { serverId ->
            Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | No sync entity in DB; synthesizing from knownServerSaveId=$serverId")
            SaveSyncEntity(
                gameId = gameId,
                rommId = game.rommId ?: 0,
                emulatorId = resolvedEmulatorId,
                channelName = SaveSyncApiClient.syncKeyOf(channelName),
                rommSaveId = serverId,
                syncStatus = SaveSyncEntity.STATUS_SERVER_NEWER,
                ownerUserId = ownerUserId
            )
        }
        if (syncEntity == null) {
            Logger.warn(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | No sync entity found in database")
            return@withContext SaveSyncResult.Error("No save tracking found")
        }

        val saveId = knownServerSaveId ?: syncEntity.rommSaveId
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
        val preferredCore = client.resolveCoreForGame(game, resolvedEmulatorId)

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
        val sigilRouted = client.isSigilRoutedFor(gameId, config, game.platformSlug, resolvedEmulatorId)
        val sigilRoot = if (sigilRouted) sigilSaveHandler.route(gameId, resolvedEmulatorId)?.root else null
        if (sigilRouted && sigilRoot == null) {
            Logger.warn(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | No save root for $resolvedEmulatorId")
            return@withContext SaveSyncResult.NoSaveFound
        }
        val isGciFormat = sigilRoot == null && config?.usesGciFormat == true
        val isFolderBased = sigilRoot != null || (config?.usesFolderBasedSaves == true &&
            serverSave.fileName.endsWith(".zip", ignoreCase = true) && !isGciFormat)
        val isSwitchEmulator = sigilRoot == null && resolvedEmulatorId in SaveSyncApiClient.SWITCH_EMULATOR_IDS
        Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Save info | fileName=${serverSave.fileName}, isFolderBased=$isFolderBased, isGciFormat=$isGciFormat, isSwitchEmulator=$isSwitchEmulator")
        if (isNonGciSaveForGciGame(gameId, resolvedEmulatorId, serverSave.fileName)) {
            return@withContext SaveSyncResult.NoSaveFound
        }

        var strandedCardToMigrate: String? = null
        val preDownloadTargetPath = if (sigilRoot != null) {
            sigilRoot
        } else if (isGciFormat) {
            null.also {
                Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | GCI format, will download to temp and detect bundle vs single")
            }
        } else if (isFolderBased) {
            if (isSwitchEmulator) {
                null.also {
                    Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Switch emulator, will discover active profile")
                }
            } else {
                val folderHandler = client.getHandler(config, game.platformSlug, resolvedEmulatorId)
                val folderSaveId = game.saveId ?: game.titleId
                val cached = syncEntity.localSavePath
                    ?.takeIf { fal.exists(it) }
                    ?.takeIf { folderSaveId == null || folderHandler.isCanonicalFolderPath(it, folderSaveId) }
                if (syncEntity.localSavePath != null && cached == null) {
                    Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Cached folder path stale or non-canonical, re-discovering | stalePath=${syncEntity.localSavePath}")
                }
                val discovered = if (cached == null) {
                    savePathResolver.discoverSavePath(
                        emulatorId = resolvedEmulatorId,
                        gameTitle = game.title,
                        platformSlug = game.platformSlug,
                        romPath = game.localPath,
                        cachedSaveId = game.saveId ?: game.titleId,
                        coreName = preferredCore,
                        emulatorPackage = emulatorPackage,
                        gameId = gameId
                    )
                } else null
                val constructed = if (cached == null && discovered == null) {
                    savePathResolver.constructFolderSavePathWithOverride(resolvedEmulatorId, game.platformSlug, game.localPath, gameId, game.title, game.saveId ?: game.titleId, emulatorPackage)
                } else null
                (cached ?: discovered ?: constructed).also {
                    Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Folder save path | cached=${cached != null}, discovered=${discovered != null}, constructed=${constructed != null}, path=$it")
                }
            }
        } else {
            val strandedCard = syncEntity.localSavePath?.takeIf {
                game.platformSlug == "psx" &&
                    resolvedEmulatorId != DUCKSTATION_EMULATOR_ID &&
                    it.endsWith(DUCKSTATION_CARD_SUFFIX) &&
                    fal.exists(it)
            }
            strandedCardToMigrate = strandedCard
            val cachedFilePath = syncEntity.localSavePath
                ?.takeIf { strandedCard == null }
                ?.takeIf { fal.exists(it) }
            if (strandedCard != null) {
                Logger.info(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | DuckStation-named card cached for a libretro save, re-resolving | stalePath=$strandedCard")
            } else if (syncEntity.localSavePath != null && cachedFilePath == null) {
                Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Cached file path no longer exists on disk, re-discovering | stalePath=${syncEntity.localSavePath}")
            }
            val discovered = cachedFilePath
                ?: savePathResolver.discoverSavePath(
                    emulatorId = resolvedEmulatorId,
                    gameTitle = game.title,
                    platformSlug = game.platformSlug,
                    romPath = game.localPath,
                    cachedSaveId = game.saveId ?: game.titleId,
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
                    cachedSaveId = null,
                    coreName = preferredCore,
                    emulatorPackage = emulatorPackage,
                    gameId = gameId
                )
            } else discovered

            (retried ?: savePathResolver.constructSavePath(resolvedEmulatorId, game.title, game.platformSlug, game.localPath, preferredCore, game.saveId ?: game.titleId, gameId, folderShaped = false, emulatorPackage = emulatorPackage)).also {
                Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | File save path | cached=${syncEntity.localSavePath != null}, discovered=${retried != null}, path=$it")
            }
        }

        fal.prepareSaveAccess(preDownloadTargetPath, strandedCardToMigrate)

        if (strandedCardToMigrate != null && preDownloadTargetPath != null && !fal.exists(preDownloadTargetPath)) {
            val carried = runCatching {
                fal.copyFile(strandedCardToMigrate, preDownloadTargetPath) && fal.commitSaveAccess(preDownloadTargetPath)
            }.getOrDefault(false)
            Logger.info(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Carried stranded card to the libretro name | from=$strandedCardToMigrate, to=$preDownloadTargetPath, ok=$carried")
        }

        if (!isSwitchEmulator && !isFolderBased && !isGciFormat && preDownloadTargetPath == null) {
            Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Cannot determine save path for non-Switch file save | emulator=$resolvedEmulatorId, package=$emulatorPackage, core=$preferredCore, romPath=${game.localPath}, platformSlug=${game.platformSlug}")
            return@withContext SaveSyncResult.NoSaveFound
        }

        if (preDownloadTargetPath != null && serverSave.contentHash != null) {
            val cachedMatch = saveCacheManager.get().findCachedByHash(gameId, serverSave.contentHash)
                ?.takeIf { SaveSyncApiClient.syncKeyOf(it.channelName) == SaveSyncApiClient.syncKeyOf(channelName) }
                ?.takeIf { it.isHardcore || !saveCacheManager.get().hasHardcoreSave(gameId) }
            if (cachedMatch != null) {
                Logger.info(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Cache hit (hash=${serverSave.contentHash}), restoring from cacheId=${cachedMatch.id} instead of fetching content")
                if (!skipBackup && !saveCacheManager.get().protectBeforeOverwrite(gameId, resolvedEmulatorId, preDownloadTargetPath)) {
                    Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Backup failed before cache-hit restore")
                    return@withContext SaveSyncResult.Error("Failed to backup existing save before restore")
                }
                val restored = saveCacheManager.get().restoreSave(cachedMatch.id, preDownloadTargetPath, resolvedEmulatorId)
                if (!restored) {
                    Logger.warn(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | restoreSave failed, falling through to network download")
                } else {
                    val serverTimestamp = SaveSyncApiClient.parseTimestamp(serverSave.updatedAt)
                    val currentDeviceSync = serverSave.deviceSyncs?.firstOrNull { it.isCurrent }
                    saveSyncDao.upsert(
                        syncEntity.copy(
                            rommSaveId = serverSave.id,
                            localSavePath = preDownloadTargetPath,
                            localUpdatedAt = if (secureSaves) serverTimestamp else localWriteAnchor(preDownloadTargetPath, serverTimestamp),
                            serverUpdatedAt = serverTimestamp,
                            lastSyncedAt = Instant.now(),
                            syncStatus = SaveSyncEntity.STATUS_SYNCED,
                            lastUploadedHash = serverSave.contentHash?.takeIf { client.getCapabilities().trustsServerHash },
                            localContentHash = saveCacheManager.get().calculateLocalSaveHash(preDownloadTargetPath, gameId, resolvedEmulatorId),
                            lastSyncDeviceId = serverSave.originDeviceId ?: currentDeviceSync?.deviceId ?: deviceId ?: syncEntity.lastSyncDeviceId,
                            lastSyncDeviceName = serverSave.originDeviceName() ?: currentDeviceSync?.deviceName ?: syncEntity.lastSyncDeviceName
                        )
                    )
                    if (!fromSnapshot && serverTimestamp != null && cachedMatch.cachedAt != serverTimestamp) {
                        saveCacheDao.updateCachedAt(cachedMatch.id, serverTimestamp)
                    }
                    activeSaveRepository.activateCache(gameId, cachedMatch.id)
                    activeSaveRepository.setActiveSaveApplied(gameId, false)
                    confirmOrQueueDeviceSync(gameId, serverSave.id)
                    Logger.info(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Complete (cache-hit) | path=$preDownloadTargetPath")
                    return@withContext SaveSyncResult.Success(rommSaveId = serverSave.id, serverTimestamp = serverTimestamp)
                }
            }
        }

        var tempZipFile: File? = null

        try {
            val downloadPath = serverSave.downloadPath
            val caps = client.getCapabilities()
            val useDeviceEndpoint = caps.supportsDeviceSyncMode && deviceId != null
            if (!useDeviceEndpoint && downloadPath == null) {
                Logger.warn(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | No download path in server save response")
                return@withContext SaveSyncResult.Error("No download path available")
            }

            val serverFingerprint = (downloadPath?.substringAfter("?timestamp=", "") ?: "")
                .ifEmpty { serverSave.updatedAt ?: "" }
            if (serverFingerprint.isNotEmpty()) {
                val cachedCorrupt = saveSyncDao.getCorruptZipTimestamp(gameId, resolvedEmulatorId, channelName, ownerUserId)
                if (cachedCorrupt == serverFingerprint) {
                    Logger.warn(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Skipping - server zip known corrupt at this timestamp; re-upload from device to recover | timestamp=$serverFingerprint")
                    return@withContext SaveSyncResult.Error("Server zip is corrupt; re-upload to recover")
                } else if (cachedCorrupt != null) {
                    Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Server timestamp changed since corrupt-zip mark, retrying | wasCorruptAt=$cachedCorrupt, now=$serverFingerprint")
                    saveSyncDao.clearCorruptZip(gameId, resolvedEmulatorId, channelName, ownerUserId)
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
                tempZipFile = File(context.cacheDir, "dl_${gameId}_${System.nanoTime()}_${FileNames.sanitize(serverSave.fileName)}")
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
                        ?: savePathResolver.constructFolderSavePathWithOverride(resolvedEmulatorId, game.platformSlug, game.localPath, gameId, game.title, game.saveId ?: game.titleId, emulatorPackage)
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

                val hasLocalHardcore = !fromSnapshot && saveCacheManager.get().hasHardcoreSave(gameId)
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

                if (!skipBackup && !saveCacheManager.get().protectBeforeOverwrite(gameId, resolvedEmulatorId, targetPath)) {
                    Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Backup failed, aborting download to prevent data loss")
                    return@withContext SaveSyncResult.Error("Failed to backup existing save before overwrite")
                }

                val extractedSize = tempZipFile.length() * 3
                if (!client.hasEnoughDiskSpace(targetPath, extractedSize)) {
                    Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Insufficient disk space for extracted save")
                    return@withContext SaveSyncResult.Error("Insufficient disk space")
                }

                val resolvedSaveId = game.saveId ?: gameDao.getSaveId(gameId)
                    ?: game.titleId ?: gameDao.getTitleId(gameId)
                    ?: extractSaveIdNow(gameId, game.localPath, game.platformSlug, emulatorPackage)

                val saveContext = SaveContext(
                    config = config!!,
                    romPath = game.localPath,
                    saveId = resolvedSaveId,
                    emulatorPackage = emulatorPackage,
                    gameId = gameId,
                    gameTitle = game.title,
                    platformSlug = game.platformSlug,
                    emulatorId = resolvedEmulatorId,
                    localSavePath = targetPath,
                    coreName = preferredCore,
                    basePathOverride = overrideBaseFor(config, game.platformSlug)
                )
                val handler = if (sigilRoot != null) sigilSaveHandler else client.getHandler(config, game.platformSlug, resolvedEmulatorId)
                fal.prepareSaveAccess(targetPath)
                val result = handler.extractDownload(tempZipFile, saveContext)
                if (!result.success) {
                    Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Extraction failed | error=${result.error}")
                    if (result.corruptZip && serverFingerprint.isNotEmpty()) {
                        saveSyncDao.markCorruptZip(
                            gameId = gameId,
                            emulatorId = resolvedEmulatorId,
                            channelName = channelName,
                            ownerUserId = ownerUserId,
                            serverTimestamp = serverFingerprint,
                            error = result.error ?: "corrupt zip"
                        )
                        Logger.warn(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Server zip corrupt - auto-recovering by uploading local save | timestamp=$serverFingerprint")
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
                            Logger.info(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Auto-recovery succeeded - corrupt server zip overwritten with local copy")
                            saveSyncDao.clearCorruptZip(gameId, resolvedEmulatorId, channelName, ownerUserId)
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

                val tempGciFile = File(context.cacheDir, "temp_gci_${gameId}_${System.nanoTime()}.tmp")
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
                        saveId = game.saveId ?: game.titleId,
                        emulatorPackage = emulatorPackage,
                        gameId = gameId,
                        gameTitle = game.title,
                        platformSlug = game.platformSlug,
                        emulatorId = resolvedEmulatorId,
                        coreName = preferredCore,
                        basePathOverride = savePathResolver.gciBaseOverride(config, gameId, game.platformSlug)
                    )
                    val existingMember = syncEntity.localSavePath
                        ?.takeIf { it.endsWith(".gci", ignoreCase = true) && fal.exists(it) }
                        ?: savePathResolver.discoverSavePath(
                            emulatorId = resolvedEmulatorId,
                            gameTitle = game.title,
                            platformSlug = game.platformSlug,
                            romPath = game.localPath,
                            cachedSaveId = game.saveId ?: game.titleId,
                            coreName = preferredCore,
                            emulatorPackage = emulatorPackage,
                            gameId = gameId
                        )
                    val hasLocalHardcore = !fromSnapshot && saveCacheManager.get().hasHardcoreSave(gameId)
                    val resolutionTarget = if (hasLocalHardcore && !saveArchiver.hasHardcoreTrailer(tempGciFile)) {
                        existingMember ?: savePathResolver.constructSavePath(
                            resolvedEmulatorId, game.title, game.platformSlug, game.localPath, preferredCore,
                            game.saveId ?: game.titleId, gameId
                        )
                    } else {
                        null
                    }
                    if (resolutionTarget != null) {
                        Logger.warn(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | NEEDS_HARDCORE_RESOLUTION | GCI server save missing trailer")
                        val heldFile = File(context.cacheDir, "hardcore_gci_${gameId}_${System.nanoTime()}.tmp")
                        tempGciFile.copyTo(heldFile, overwrite = true)
                        return@withContext SaveSyncResult.NeedsHardcoreResolution(
                            tempFilePath = heldFile.absolutePath,
                            gameId = gameId,
                            gameName = game.title,
                            emulatorId = resolvedEmulatorId,
                            targetPath = resolutionTarget,
                            isFolderBased = false,
                            channelName = channelName ?: syncEntity.channelName
                        )
                    }
                    if (!skipBackup && existingMember != null &&
                        !saveCacheManager.get().protectBeforeOverwrite(gameId, resolvedEmulatorId, existingMember)
                    ) {
                        Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Backup failed, aborting GCI download to prevent data loss")
                        return@withContext SaveSyncResult.Error("Failed to backup existing save before overwrite")
                    }
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

                var tempSaveFile: File? = File(context.cacheDir, "temp_save_${gameId}_${System.nanoTime()}.tmp")
                try {
                    body.byteStream().use { input ->
                        tempSaveFile!!.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Saved temp file | path=${tempSaveFile!!.absolutePath}, size=${tempSaveFile!!.length()}bytes")

                    val hasLocalHardcore = !fromSnapshot && saveCacheManager.get().hasHardcoreSave(gameId)
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

                    if (!skipBackup && !saveCacheManager.get().protectBeforeOverwrite(gameId, resolvedEmulatorId, targetPath)) {
                        Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Backup failed, aborting download to prevent data loss")
                        return@withContext SaveSyncResult.Error("Failed to backup existing save before overwrite")
                    }

                    val sigilRestore = sigilSaveHandler.restore(gameId, tempSaveFile!!, resolvedEmulatorId)
                    val sigilPlaced = sigilRestore is SigilRestore.Restored || sigilRestore is SigilRestore.Refused
                    val bundleResult = if (sigilPlaced) {
                        null
                    } else {
                        unitBundleResult(tempSaveFile!!, targetPath, config, game, resolvedEmulatorId, emulatorPackage, preferredCore)
                    }
                    if (sigilRestore is SigilRestore.Refused) {
                        Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Sigil refused the restore | reason=${sigilRestore.reason}")
                        return@withContext SaveSyncResult.Error("Failed to place save: ${sigilRestore.reason}")
                    } else if (sigilRestore is SigilRestore.Restored) {
                        targetPath = unitPrimaryTarget(targetPath, game, resolvedEmulatorId, preferredCore) ?: targetPath
                        Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Restored through Sigil | primary=$targetPath")
                    } else if (bundleResult != null) {
                        if (!bundleResult.success) {
                            Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Bundle placement failed | error=${bundleResult.error}")
                            return@withContext SaveSyncResult.Error(bundleResult.error ?: "Failed to place save bundle")
                        }
                        targetPath = bundleResult.targetPath ?: targetPath
                        Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Bundle placed | primary=$targetPath")
                    } else {
                        targetPath = unitPrimaryTarget(targetPath, game, resolvedEmulatorId, preferredCore) ?: targetPath
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
                    }
                } finally {
                    tempSaveFile?.delete()
                }
            }

            val serverTimestamp = SaveSyncApiClient.parseTimestamp(serverSave.updatedAt)
            fal.getTransformedFile(targetPath).setLastModified(serverTimestamp.toEpochMilli())
            if (!fal.commitSaveAccess(targetPath)) {
                Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Wrote the mirrored copy but could not write it out | target=$targetPath")
                return@withContext SaveSyncResult.Error("Failed to write save")
            }

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
            val isLatestSave = SaveSyncApiClient.isLatestBucket(effectiveChannelName, romBaseName)

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
                    if (!fromSnapshot && serverTimestamp != null && cacheResult.cacheId > 0L) {
                        saveCacheDao.updateCachedAt(cacheResult.cacheId, serverTimestamp)
                    }
                    if (cacheResult.cacheId > 0L) {
                        activeSaveRepository.activateCache(gameId, cacheResult.cacheId)
                    }
                    activeSaveRepository.setActiveSaveApplied(gameId, false)
                }
                val placedCacheId = when (cacheResult) {
                    is SaveCacheManager.CacheResult.Created -> cacheResult.cacheId
                    is SaveCacheManager.CacheResult.Duplicate -> cacheResult.cacheId
                    SaveCacheManager.CacheResult.Failed -> 0L
                }
                if (placedCacheId > 0L) saveCacheDao.updateRommSaveId(placedCacheId, serverSave.id)
            } catch (e: Exception) {
                Logger.error(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Cache creation failed", e)
            }

            val completedUploaderSync = serverSave.deviceSyncs
                ?.filter { !it.isCurrent }
                ?.maxByOrNull { it.lastSyncedAt ?: "" }
            saveSyncDao.upsert(
                syncEntity.copy(
                    rommSaveId = serverSave.id,
                    localSavePath = targetPath,
                    localUpdatedAt = if (secureSaves) serverTimestamp else localWriteAnchor(targetPath, serverTimestamp),
                    serverUpdatedAt = serverTimestamp,
                    lastSyncedAt = Instant.now(),
                    syncStatus = SaveSyncEntity.STATUS_SYNCED,
                    lastUploadedHash = serverSave.contentHash?.takeIf { client.getCapabilities().trustsServerHash },
                    localContentHash = saveCacheManager.get().calculateLocalSaveHash(targetPath, gameId, resolvedEmulatorId),
                    lastSyncDeviceId = serverSave.originDeviceId ?: completedUploaderSync?.deviceId ?: syncEntity.lastSyncDeviceId,
                    lastSyncDeviceName = serverSave.originDeviceName() ?: completedUploaderSync?.deviceName ?: syncEntity.lastSyncDeviceName
                )
            )
            confirmOrQueueDeviceSync(gameId, serverSave.id)

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
        if (isNonGciSaveForGciGame(gameId, emulatorId, serverSave.fileName)) return@withContext false

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
                activate = true,
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

    private suspend fun isNonGciSaveForGciGame(gameId: Long, emulatorId: String?, fileName: String): Boolean {
        val game = gameDao.getById(gameId) ?: return false
        val resolvedEmulatorId = emulatorId
            ?: emulatorResolver.getEmulatorIdForGame(gameId, game.platformId, game.platformSlug)
            ?: return false
        val config = SavePathRegistry.getConfigForPlatform(resolvedEmulatorId, game.platformSlug)
        if (config?.usesGciFormat != true) return false
        if (apiClient.get().isSigilRoutedFor(gameId, config, game.platformSlug, resolvedEmulatorId)) return false
        if (GciSaveHandler.isGciServerFileName(fileName)) return false
        Logger.info(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | Skipped: server save is not a GameCube memory card file | fileName=$fileName")
        return true
    }

    suspend fun downloadAndCacheSave(
        serverSaveId: Long,
        gameId: Long,
        channelName: String?,
        activate: Boolean
    ): Boolean = downloadToCache(serverSaveId, gameId, channelName, activate) != null

    /**
     * Downloads server save [serverSaveId] into the save cache and returns the cache row's id,
     * or null when the save could not be fetched or cached. Nothing on disk is touched.
     */
    suspend fun downloadToCache(
        serverSaveId: Long,
        gameId: Long,
        channelName: String?,
        activate: Boolean
    ): Long? = downloadMutexes.computeIfAbsent(gameId) { kotlinx.coroutines.sync.Mutex() }.withLock {
        val ownerUserId = syncPreferencesRepository.getRommUserId()
        val held = saveCacheDao.getByGameAndOwner(gameId, ownerUserId).firstOrNull { it.rommSaveId == serverSaveId }
        if (held != null) {
            if (activate) activeSaveRepository.activateCache(gameId, held.id)
            return@withLock held.id
        }
        fetchIntoCache(serverSaveId, gameId, channelName, activate)
    }

    private suspend fun fetchIntoCache(
        serverSaveId: Long,
        gameId: Long,
        channelName: String?,
        activate: Boolean
    ): Long? = withContext(Dispatchers.IO) {
        val client = apiClient.get()
        val api = client.getApi() ?: return@withContext null
        val deviceId = client.getDeviceId()

        val serverSave = try {
            val resp = if (deviceId != null) {
                api.getSaveWithDevice(serverSaveId, deviceId)
            } else {
                api.getSave(serverSaveId)
            }
            resp.body()
        } catch (e: Exception) {
            Logger.error(TAG, "downloadToCache: getSave failed", e)
            return@withContext null
        } ?: return@withContext null
        if (isNonGciSaveForGciGame(gameId, null, serverSave.fileName)) return@withContext null

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
            Logger.error(TAG, "downloadToCache: download failed", e)
            return@withContext null
        }

        if (!response.isSuccessful) {
            Logger.error(TAG, "downloadToCache: HTTP ${response.code()}")
            return@withContext null
        }

        val tempFile = File(context.cacheDir, "save_precache_${serverSaveId}_${System.nanoTime()}.tmp")
        try {
            response.body()?.byteStream()?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null

            if (tempFile.length() == 0L) return@withContext null

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
                activate = activate,
                serverTimestamp = serverTime,
                isLocked = SaveSyncApiClient.syncKeyOf(channelName) != SaveSyncApiClient.AUTOSAVE_SLOT_NAME,
                needsRemoteSync = false,
                rommSaveId = serverSaveId
            )

            Logger.debug(TAG, "downloadToCache: result=$result, saveId=$serverSaveId")
            when (result) {
                is SaveCacheManager.CacheResult.Created -> result.cacheId
                is SaveCacheManager.CacheResult.Duplicate -> result.cacheId
                SaveCacheManager.CacheResult.Failed -> null
            }
        } finally {
            tempFile.delete()
        }
    }

    suspend fun confirmDeviceSynced(saveId: Long): Boolean {
        val client = apiClient.get()
        val api = client.getApi() ?: return false
        val devId = client.getDeviceId() ?: return false
        return try {
            val response = api.confirmSaveDownloaded(saveId, RomMDeviceIdRequest(devId))
            if (response.isSuccessful) {
                Logger.debug(TAG, "[SaveSync] confirmDeviceSynced | saveId=$saveId | Server acknowledged")
            } else {
                Logger.warn(TAG, "[SaveSync] confirmDeviceSynced | saveId=$saveId | HTTP ${response.code()}")
            }
            response.isSuccessful
        } catch (e: Exception) {
            Logger.warn(TAG, "[SaveSync] confirmDeviceSynced | saveId=$saveId | Failed", e)
            false
        }
    }

    /**
     * Records on the server that this device holds [saveId]. RomM refuses a later upload into the
     * same slot until that record exists, so a confirm that fails stays pending and
     * [flushPendingDeviceSync] retries it before the next sync.
     */
    suspend fun confirmOrQueueDeviceSync(gameId: Long, saveId: Long) {
        activeSaveRepository.setPendingDeviceSyncSaveId(gameId, saveId)
        if (confirmDeviceSynced(saveId)) {
            activeSaveRepository.setPendingDeviceSyncSaveId(gameId, null)
        } else {
            Logger.warn(TAG, "[SaveSync] confirmOrQueueDeviceSync | gameId=$gameId, saveId=$saveId | Queued for retry")
        }
    }

    suspend fun flushPendingDeviceSync(gameId: Long) {
        val pendingSaveId = activeSaveRepository.getPendingDeviceSyncSaveId(gameId) ?: return
        Logger.debug(TAG, "[SaveSync] flushPendingDeviceSync | gameId=$gameId, pendingSaveId=$pendingSaveId")
        if (confirmDeviceSynced(pendingSaveId)) {
            activeSaveRepository.setPendingDeviceSyncSaveId(gameId, null)
            Logger.debug(TAG, "[SaveSync] flushPendingDeviceSync | gameId=$gameId | Flushed successfully")
        } else {
            Logger.warn(TAG, "[SaveSync] flushPendingDeviceSync | gameId=$gameId | Failed, will retry later")
        }
    }

    private fun localWriteAnchor(path: String, fallback: Instant?): Instant? {
        val file = File(path)
        val newest = if (file.isDirectory) savePathResolver.findNewestFileTime(path) else file.lastModified()
        return if (newest > 0L) Instant.ofEpochMilli(newest) else fallback
    }

    private suspend fun extractSaveIdNow(
        gameId: Long,
        romPath: String?,
        platformSlug: String,
        emulatorPackage: String?
    ): String? {
        if (romPath.isNullOrBlank()) return null
        val romFile = File(romPath)
        if (!romFile.exists()) return null
        val result = titleIdExtractor.extractTitleIdWithSource(romFile, platformSlug, emulatorPackage) ?: run {
            Logger.debug(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | extractSaveIdNow: sigil returned null | rom=${romFile.name}, platform=$platformSlug")
            return null
        }
        Logger.info(TAG, "[SaveSync] DOWNLOAD gameId=$gameId | extractSaveIdNow: cached titleId=${result.titleId} saveId=${result.saveId} (fromBinary=${result.fromBinary})")
        gameDao.setTitleAndSaveIdWithLock(gameId, result.titleId, result.saveId, result.fromBinary)
        return result.saveId
    }

    companion object {
        private const val TAG = "SaveDownloader"
        private const val DUCKSTATION_EMULATOR_ID = "duckstation"
        private const val DUCKSTATION_CARD_SUFFIX = "_1.mcd"
    }
}
