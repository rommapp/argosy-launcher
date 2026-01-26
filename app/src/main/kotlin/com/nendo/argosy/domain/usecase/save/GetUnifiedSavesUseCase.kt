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

        // Find the most recent cache for each channel (only these should match server saves)
        val mostRecentByChannel = localCaches
            .filter { it.note != null }
            .groupBy { it.note }
            .mapValues { (_, caches) -> caches.maxByOrNull { it.cachedAt }?.id }

        // Find the most recent non-channel cache (for "Latest" matching)
        val mostRecentNonChannel = localCaches
            .filter { it.note == null }
            .maxByOrNull { it.cachedAt }?.id

        for (cache in localCaches) {
            val channelName = cache.note
            val isMostRecentForChannel = if (channelName != null) {
                mostRecentByChannel[channelName] == cache.id
            } else {
                mostRecentNonChannel == cache.id
            }

            val matchingServer = if (isMostRecentForChannel) {
                serverSaves.find { serverSave -> matchesLocalCache(serverSave, channelName, romBaseName) }
            } else {
                null
            }

            if (matchingServer != null) {
                usedServerIds.add(matchingServer.id)
                val serverChannelName = parseServerChannelName(matchingServer.fileName, romBaseName)
                val isLatest = isLatestFileName(matchingServer.fileName, romBaseName)
                val mergedChannelName = channelName ?: serverChannelName
                // If this is a named channel (not latest), it should be locked regardless of local cache state
                // This ensures session saves (unlocked) don't demote synced channel slots
                val isLocked = mergedChannelName != null || cache.isLocked
                result.add(
                    UnifiedSaveEntry(
                        localCacheId = cache.id,
                        serverSaveId = matchingServer.id,
                        timestamp = cache.cachedAt,
                        size = cache.saveSize,
                        channelName = mergedChannelName,
                        source = UnifiedSaveEntry.Source.BOTH,
                        serverFileName = matchingServer.fileName,
                        isLatest = isLatest,
                        isLocked = isLocked,
                        isHardcore = cache.isHardcore,
                        cheatsUsed = cache.cheatsUsed
                    )
                )
            } else {
                // Only lock if this is the most recent for its channel AND has a channel name
                // Older entries for the same channel should appear in timeline, not slots
                val shouldBeLocked = isMostRecentForChannel && channelName != null
                result.add(
                    UnifiedSaveEntry(
                        localCacheId = cache.id,
                        timestamp = cache.cachedAt,
                        size = cache.saveSize,
                        channelName = channelName,
                        source = UnifiedSaveEntry.Source.LOCAL,
                        isLocked = shouldBeLocked,
                        isHardcore = cache.isHardcore,
                        cheatsUsed = cache.cheatsUsed
                    )
                )
            }
        }

        for (serverSave in serverSaves) {
            if (serverSave.id in usedServerIds) continue

            val timestamp = parseServerTimestamp(serverSave.updatedAt) ?: Instant.now()
            val serverChannelName = parseServerChannelName(serverSave.fileName, romBaseName)
            val isLatest = isLatestFileName(serverSave.fileName, romBaseName)
            val isLocked = serverChannelName != null

            result.add(
                UnifiedSaveEntry(
                    serverSaveId = serverSave.id,
                    timestamp = timestamp,
                    size = serverSave.fileSizeBytes,
                    channelName = serverChannelName,
                    source = UnifiedSaveEntry.Source.SERVER,
                    serverFileName = serverSave.fileName,
                    isLatest = isLatest,
                    isLocked = isLocked
                )
            )
        }

        return result
    }

    private fun matchesLocalCache(
        serverSave: RomMSave,
        channelName: String?,
        romBaseName: String?
    ): Boolean {
        val serverBaseName = File(serverSave.fileName).nameWithoutExtension
        return if (channelName != null) {
            channelName.equals(serverBaseName, ignoreCase = true)
        } else {
            isLatestName(serverBaseName, romBaseName)
        }
    }

    private fun isTimestampSaveName(baseName: String): Boolean {
        return TIMESTAMP_ONLY_PATTERN.matches(baseName)
    }

    private fun parseServerChannelName(fileName: String, romBaseName: String?): String? {
        val baseName = File(fileName).nameWithoutExtension
        if (isTimestampSaveName(baseName)) return null
        if (isLatestName(baseName, romBaseName)) return null
        return baseName
    }

    private fun isLatestName(baseName: String, romBaseName: String?): Boolean {
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

    private fun isLatestFileName(fileName: String, romBaseName: String?): Boolean {
        val baseName = File(fileName).nameWithoutExtension
        return isLatestName(baseName, romBaseName)
    }

    private fun sortEntries(entries: List<UnifiedSaveEntry>): List<UnifiedSaveEntry> {
        val latest = entries.filter { it.isLatest }
        val channels = entries.filter { it.isChannel && !it.isLatest }
        val dated = entries.filter { !it.isChannel && !it.isLatest }

        val sortedDated = dated.sortedByDescending { it.timestamp }
        val sortedChannels = channels.sortedBy { it.channelName?.lowercase() }

        return latest + sortedDated + sortedChannels
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
        private const val DEFAULT_SAVE_NAME = "argosy-latest"
        private val TIMESTAMP_ONLY_PATTERN = Regex("""^\d{4}-\d{2}-\d{2}[_-]\d{2}[_-]\d{2}[_-]\d{2}$""")
        private val ROMM_TIMESTAMP_TAG = Regex("""^\[\d{4}-\d{2}-\d{2} \d{2}-\d{2}-\d{2}(-\d+)?\]$""")
    }
}
