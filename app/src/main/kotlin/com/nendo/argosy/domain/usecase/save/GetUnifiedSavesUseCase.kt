package com.nendo.argosy.domain.usecase.save

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.remote.romm.RomMSave
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.domain.model.UnifiedSaveEntry
import java.io.File
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class GetUnifiedSavesUseCase @Inject constructor(
    private val saveCacheManager: SaveCacheManager,
    private val saveSyncRepository: SaveSyncRepository,
    private val gameDao: GameDao
) {
    suspend operator fun invoke(gameId: Long): List<UnifiedSaveEntry> {
        val localCaches = saveCacheManager.getCachesForGameOnce(gameId)
        val game = gameDao.getById(gameId)
        val rommId = game?.rommId
        val romBaseName = game?.localPath?.let { File(it).nameWithoutExtension }

        val serverSaves = if (rommId != null) {
            saveSyncRepository.checkSavesForGame(gameId, rommId)
        } else {
            emptyList()
        }

        val entries = mergeEntries(localCaches, serverSaves, romBaseName)
        return sortEntries(entries)
    }

    private fun mergeEntries(
        localCaches: List<SaveCacheEntity>,
        serverSaves: List<RomMSave>,
        romBaseName: String?
    ): List<UnifiedSaveEntry> {
        val result = mutableListOf<UnifiedSaveEntry>()
        val usedServerIds = mutableSetOf<Long>()

        for (cache in localCaches) {
            val channelName = cache.note
            val matchingServer = serverSaves.find { serverSave ->
                matchesLocalCache(cache, serverSave, channelName)
            }

            if (matchingServer != null) {
                usedServerIds.add(matchingServer.id)
                val serverChannelName = parseServerChannelName(matchingServer.fileName, romBaseName)
                result.add(
                    UnifiedSaveEntry(
                        localCacheId = cache.id,
                        serverSaveId = matchingServer.id,
                        timestamp = cache.cachedAt,
                        size = cache.saveSize,
                        channelName = channelName ?: serverChannelName,
                        source = UnifiedSaveEntry.Source.BOTH,
                        serverFileName = matchingServer.fileName,
                        isLatest = isLatestFileName(matchingServer.fileName, romBaseName)
                    )
                )
            } else {
                result.add(
                    UnifiedSaveEntry(
                        localCacheId = cache.id,
                        timestamp = cache.cachedAt,
                        size = cache.saveSize,
                        channelName = channelName,
                        source = UnifiedSaveEntry.Source.LOCAL
                    )
                )
            }
        }

        for (serverSave in serverSaves) {
            if (serverSave.id in usedServerIds) continue

            val timestamp = parseServerTimestamp(serverSave.updatedAt) ?: Instant.now()
            val serverChannelName = parseServerChannelName(serverSave.fileName, romBaseName)
            val isLatest = isLatestFileName(serverSave.fileName, romBaseName)

            result.add(
                UnifiedSaveEntry(
                    serverSaveId = serverSave.id,
                    timestamp = timestamp,
                    size = serverSave.fileSizeBytes,
                    channelName = serverChannelName,
                    source = UnifiedSaveEntry.Source.SERVER,
                    serverFileName = serverSave.fileName,
                    isLatest = isLatest
                )
            )
        }

        return result
    }

    private fun matchesLocalCache(cache: SaveCacheEntity, serverSave: RomMSave, channelName: String?): Boolean {
        if (channelName != null) {
            val serverBaseName = File(serverSave.fileName).nameWithoutExtension
            return channelName.equals(serverBaseName, ignoreCase = true)
        }
        return isSameTimestamp(cache.cachedAt, serverSave)
    }

    private fun parseServerChannelName(fileName: String, romBaseName: String?): String? {
        val baseName = File(fileName).nameWithoutExtension

        if (romBaseName != null && baseName.equals(romBaseName, ignoreCase = true)) {
            return null
        }

        if (romBaseName != null && baseName.startsWith(romBaseName, ignoreCase = true)) {
            val suffix = baseName.substring(romBaseName.length)
            if (TIMESTAMP_PATTERN.matches(suffix)) {
                return null
            }
        }

        return baseName
    }

    private fun isLatestFileName(fileName: String, romBaseName: String?): Boolean {
        if (romBaseName == null) return false
        val baseName = File(fileName).nameWithoutExtension
        return baseName.equals(romBaseName, ignoreCase = true)
    }

    private fun sortEntries(entries: List<UnifiedSaveEntry>): List<UnifiedSaveEntry> {
        val latest = entries.filter { it.isLatest }
        val channels = entries.filter { it.isChannel && !it.isLatest }
        val dated = entries.filter { !it.isChannel && !it.isLatest }

        val sortedDated = dated.sortedByDescending { it.timestamp }
        val sortedChannels = channels.sortedBy { it.channelName?.lowercase() }

        return latest + sortedDated + sortedChannels
    }

    private fun isSameTimestamp(localTime: Instant, serverSave: RomMSave): Boolean {
        val serverTime = parseServerTimestamp(serverSave.updatedAt) ?: return false
        val diffSeconds = kotlin.math.abs(localTime.epochSecond - serverTime.epochSecond)
        return diffSeconds < TIMESTAMP_TOLERANCE_SECONDS
    }

    private fun parseServerTimestamp(timestamp: String): Instant? {
        return try {
            ZonedDateTime.parse(timestamp, DateTimeFormatter.ISO_DATE_TIME).toInstant()
        } catch (_: Exception) {
            try {
                Instant.parse(timestamp)
            } catch (_: Exception) {
                null
            }
        }
    }

    companion object {
        private val TIMESTAMP_PATTERN = Regex("""_\d{8}_\d{6}$""")
        private const val TIMESTAMP_TOLERANCE_SECONDS = 60L
    }
}
