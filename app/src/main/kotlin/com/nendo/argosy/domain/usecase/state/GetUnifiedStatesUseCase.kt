package com.nendo.argosy.domain.usecase.state

import com.nendo.argosy.data.emulator.CoreVersionExtractor
import com.nendo.argosy.data.emulator.StatePathRegistry
import com.nendo.argosy.data.emulator.VersionValidationResult
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.StateCacheEntity
import com.nendo.argosy.data.repository.StateCacheManager
import com.nendo.argosy.domain.model.UnifiedStateEntry
import java.io.File
import javax.inject.Inject

class GetUnifiedStatesUseCase @Inject constructor(
    private val stateCacheManager: StateCacheManager,
    private val gameDao: GameDao,
    private val coreVersionExtractor: CoreVersionExtractor
) {
    suspend operator fun invoke(
        gameId: Long,
        emulatorId: String? = null,
        channelName: String? = null,
        currentCoreId: String? = null,
        currentCoreVersion: String? = null
    ): List<UnifiedStateEntry> {
        val game = gameDao.getById(gameId) ?: return emptyList()

        val effectiveEmulatorId = emulatorId ?: resolveEmulatorId(game.platformSlug)
        if (effectiveEmulatorId == null) return emptyList()

        val localStates = if (channelName != null) {
            stateCacheManager.getStatesForChannel(gameId, channelName)
        } else {
            stateCacheManager.getDefaultChannelStates(gameId)
        }

        val config = StatePathRegistry.getConfig(effectiveEmulatorId)
        val maxSlots = config?.maxSlots ?: 10

        return buildSlotList(
            localStates = localStates,
            maxSlots = maxSlots,
            channelName = channelName,
            currentCoreId = currentCoreId,
            currentCoreVersion = currentCoreVersion
        )
    }

    private fun buildSlotList(
        localStates: List<StateCacheEntity>,
        maxSlots: Int,
        channelName: String?,
        currentCoreId: String?,
        currentCoreVersion: String?
    ): List<UnifiedStateEntry> {
        val statesBySlot = localStates.associateBy { it.slotNumber }
        val result = mutableListOf<UnifiedStateEntry>()

        val autoSlotState = statesBySlot[-1]
        if (autoSlotState != null) {
            result.add(
                createEntry(
                    cache = autoSlotState,
                    channelName = channelName,
                    currentCoreId = currentCoreId,
                    currentCoreVersion = currentCoreVersion
                )
            )
        }

        val slotsToShow = if (maxSlots < 0) {
            localStates.filter { it.slotNumber >= 0 }.map { it.slotNumber }.maxOrNull()?.plus(1) ?: 10
        } else {
            maxSlots
        }

        for (slot in 0 until slotsToShow) {
            val state = statesBySlot[slot]
            if (state != null) {
                result.add(
                    createEntry(
                        cache = state,
                        channelName = channelName,
                        currentCoreId = currentCoreId,
                        currentCoreVersion = currentCoreVersion
                    )
                )
            } else {
                result.add(UnifiedStateEntry.empty(slot))
            }
        }

        return result
    }

    private fun createEntry(
        cache: StateCacheEntity,
        channelName: String?,
        currentCoreId: String?,
        currentCoreVersion: String?
    ): UnifiedStateEntry {
        val versionStatus = determineVersionStatus(
            cache.coreId,
            cache.coreVersion,
            currentCoreId,
            currentCoreVersion
        )

        return UnifiedStateEntry(
            localCacheId = cache.id,
            slotNumber = cache.slotNumber,
            timestamp = cache.cachedAt,
            size = cache.stateSize,
            channelName = channelName,
            coreId = cache.coreId,
            coreVersion = cache.coreVersion,
            screenshotPath = stateCacheManager.getScreenshotPath(cache),
            source = UnifiedStateEntry.Source.LOCAL,
            isActive = false,
            isLocked = cache.isLocked,
            versionStatus = versionStatus
        )
    }

    private fun determineVersionStatus(
        savedCoreId: String?,
        savedVersion: String?,
        currentCoreId: String?,
        currentVersion: String?
    ): UnifiedStateEntry.VersionStatus {
        if (savedCoreId != null && currentCoreId != null && savedCoreId != currentCoreId) {
            return UnifiedStateEntry.VersionStatus.MISMATCH
        }

        return when (coreVersionExtractor.validateVersion(savedVersion, currentVersion)) {
            is VersionValidationResult.Compatible -> UnifiedStateEntry.VersionStatus.COMPATIBLE
            is VersionValidationResult.Mismatch -> UnifiedStateEntry.VersionStatus.MISMATCH
            is VersionValidationResult.Unknown -> UnifiedStateEntry.VersionStatus.UNKNOWN
        }
    }

    private fun resolveEmulatorId(platformSlug: String): String? {
        return null
    }
}
