package com.nendo.argosy.domain.usecase.save

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.remote.romm.RomMSave
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncApiClient
import com.nendo.argosy.data.repository.SaveSyncApiClient.Companion.equalsNormalized
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.domain.model.UnifiedSaveEntry
import java.io.File
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private const val LATEST_SLOT_KEY = "__latest__"

class GetUnifiedSavesUseCase @Inject constructor(
    private val saveCacheManager: SaveCacheManager,
    private val saveSyncRepository: SaveSyncRepository,
    private val gameDao: GameDao
) {
    suspend operator fun invoke(gameId: Long): List<UnifiedSaveEntry> {
        saveCacheManager.dedupeIdenticalCaches(gameId)
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
        val sorted = sortEntries(entries)

        val both = entries.count { it.source == UnifiedSaveEntry.Source.BOTH }
        val localOnly = entries.count { it.source == UnifiedSaveEntry.Source.LOCAL }
        val serverOnly = entries.count { it.source == UnifiedSaveEntry.Source.SERVER }
        val droppedServerByClaimedSlot = (serverSaves.size - (both + serverOnly)).coerceAtLeast(0)
        com.nendo.argosy.util.SaveDebugLogger.logUnifiedBuilt(
            gameId = gameId,
            totalLocal = localCaches.size,
            totalServer = serverSaves.size,
            mergedBoth = both,
            localOnly = localOnly,
            serverOnly = serverOnly,
            unmatchedServerByClaimedSlot = droppedServerByClaimedSlot
        )
        return sorted
    }

    suspend fun localOnly(gameId: Long): List<UnifiedSaveEntry> {
        saveCacheManager.dedupeIdenticalCaches(gameId)
        val localCaches = saveCacheManager.getCachesForGameOnce(gameId)
        val romBaseName = gameDao.getById(gameId)?.localPath?.let {
            File(it).nameWithoutExtension
        }
        val entries = mergeEntries(localCaches, emptyList(), romBaseName)
        return sortEntries(entries)
    }

    private fun mergeEntries(
        localCaches: List<SaveCacheEntity>,
        serverSaves: List<RomMSave>,
        romBaseName: String?
    ): List<UnifiedSaveEntry> {
        val result = mutableListOf<UnifiedSaveEntry>()
        val usedServerIds = mutableSetOf<Long>()
        val claimedSlots = mutableSetOf<String>()
        val serverSaveById = serverSaves.associateBy { it.id }

        val slottedChannels = serverSaves
            .mapNotNull { it.slot?.takeIf { s -> s.isNotBlank() } }
            .toSet()

        // Find the most recent cache for each channel (only these should match server saves by name)
        val mostRecentByChannel = localCaches
            .filter { it.channelName != null }
            .groupBy { it.channelName }
            .mapValues { (_, caches) -> caches.maxByOrNull { it.cachedAt }?.id }

        // Find the most recent non-channel cache (for "Latest" matching)
        val mostRecentNonChannel = localCaches
            .filter { it.channelName == null }
            .maxByOrNull { it.cachedAt }?.id

        for (cache in localCaches) {
            val channelName = cache.channelName
            val isMostRecentForChannel = if (channelName != null) {
                mostRecentByChannel[channelName] == cache.id
            } else {
                mostRecentNonChannel == cache.id
            }

            // Direct match by rommSaveId takes priority over name-based matching
            val matchingServer = if (cache.rommSaveId != null) {
                serverSaveById[cache.rommSaveId]
            } else if (isMostRecentForChannel) {
                serverSaves.find { serverSave -> matchesLocalCache(serverSave, channelName, romBaseName) }
            } else {
                null
            }

            if (matchingServer != null) {
                usedServerIds.add(matchingServer.id)
                val isLatest = isLatestSlot(matchingServer.slot, matchingServer.fileName, romBaseName)
                val serverChannelName = if (isLatest) null
                    else matchingServer.slot ?: SaveSyncApiClient.parseServerChannelNameForSync(matchingServer.fileName, romBaseName)
                val mergedChannelName = channelName ?: serverChannelName
                val isLocked = mergedChannelName != null || cache.isLocked
                val deviceSyncCurrent = saveSyncRepository.getDeviceId()?.let { devId ->
                    matchingServer.deviceSyncs?.find { it.deviceId == devId }?.isCurrent
                }
                val slotKey = resolveSlotKey(matchingServer.slot, matchingServer.fileName, romBaseName)
                claimedSlots.add(slotKey)
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
                        cheatsUsed = cache.cheatsUsed,
                        isRollback = cache.isRollback,
                        isUserCreatedSlot = cache.isLocked,
                        isCurrent = deviceSyncCurrent ?: false
                    )
                )
            } else {
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
                        cheatsUsed = cache.cheatsUsed,
                        isRollback = cache.isRollback,
                        isUserCreatedSlot = cache.isLocked
                    )
                )
            }
        }

        val unmatchedServerSaves = serverSaves
            .filter { it.id !in usedServerIds }
            .sortedByDescending { parseServerTimestamp(it.updatedAt) ?: Instant.EPOCH }

        for (serverSave in unmatchedServerSaves) {
            val slotKey = resolveSlotKey(serverSave.slot, serverSave.fileName, romBaseName)
            if (slotKey in claimedSlots) continue

            val timestamp = parseServerTimestamp(serverSave.updatedAt) ?: Instant.now()
            val isLatest = isLatestSlot(serverSave.slot, serverSave.fileName, romBaseName)
            val derivedChannelName = serverSave.slot
                ?: SaveSyncApiClient.parseServerChannelNameForSync(serverSave.fileName, romBaseName)

            if (serverSave.slot == null && derivedChannelName != null && derivedChannelName in slottedChannels) {
                continue
            }

            val serverChannelName = if (isLatest) null else derivedChannelName
            val isLocked = serverChannelName != null
            val deviceSyncCurrent = saveSyncRepository.getDeviceId()?.let { devId ->
                serverSave.deviceSyncs?.find { it.deviceId == devId }?.isCurrent
            }

            claimedSlots.add(slotKey)
            result.add(
                UnifiedSaveEntry(
                    serverSaveId = serverSave.id,
                    timestamp = timestamp,
                    size = serverSave.fileSizeBytes,
                    channelName = serverChannelName,
                    source = UnifiedSaveEntry.Source.SERVER,
                    serverFileName = serverSave.fileName,
                    isLatest = isLatest,
                    isLocked = isLocked,
                    isUserCreatedSlot = serverSave.slot != null,
                    isCurrent = deviceSyncCurrent ?: false
                )
            )
        }

        return result
    }

    private fun resolveSlotKey(slot: String?, fileName: String, romBaseName: String?): String {
        if (slot != null) return slot.lowercase()
        val baseName = File(fileName).nameWithoutExtension.lowercase()
        if (romBaseName != null && SaveSyncApiClient.isLatestSaveFileName(fileName, romBaseName)) {
            return LATEST_SLOT_KEY
        }
        return baseName
    }

    private fun matchesLocalCache(
        serverSave: RomMSave,
        channelName: String?,
        romBaseName: String?
    ): Boolean {
        if (channelName != null) {
            if (serverSave.slot != null) return equalsNormalized(serverSave.slot, channelName)
            val serverBaseName = File(serverSave.fileName).nameWithoutExtension
            return equalsNormalized(channelName, serverBaseName)
        }
        if (serverSave.slot != null) return SaveSyncApiClient.isLatestSaveFileName(serverSave.slot, romBaseName)
        return SaveSyncApiClient.isLatestSaveFileName(serverSave.fileName, romBaseName)
    }

    private fun isLatestSlot(slot: String?, fileName: String, romBaseName: String?): Boolean {
        if (slot != null) return SaveSyncApiClient.isLatestSaveFileName(slot, romBaseName)
        return SaveSyncApiClient.isLatestSaveFileName(fileName, romBaseName)
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

}
