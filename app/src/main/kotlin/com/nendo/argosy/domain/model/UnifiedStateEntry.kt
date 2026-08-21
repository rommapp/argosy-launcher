package com.nendo.argosy.domain.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class UnifiedStateEntry(
    val localCacheId: Long? = null,
    val serverStateId: Long? = null,
    val slotNumber: Int,
    val timestamp: Instant,
    val size: Long,
    val channelName: String? = null,
    val coreId: String? = null,
    val coreVersion: String? = null,
    val screenshotPath: String? = null,
    val source: Source,
    val isActive: Boolean = false,
    val isLocked: Boolean = false,
    val versionStatus: VersionStatus = VersionStatus.UNKNOWN,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY
) {
    enum class Source { LOCAL, SERVER, BOTH }
    enum class VersionStatus { COMPATIBLE, MISMATCH, UNKNOWN }
    enum class SyncStatus { LOCAL_ONLY, SYNCED, PENDING_UPLOAD, SERVER_ONLY }

    val isAutoSlot: Boolean get() = slotNumber == -1
    val isEmpty: Boolean get() = localCacheId == null && serverStateId == null
    val canRestore: Boolean get() = localCacheId != null
    val canBindToChannel: Boolean get() = localCacheId != null && channelName == null

    val displayName: String
        get() = when {
            isAutoSlot -> "Auto"
            channelName != null -> "$channelName (Slot $slotNumber)"
            else -> "Slot $slotNumber"
        }

    val slotLabel: String
        get() = when {
            isAutoSlot -> "Auto"
            else -> slotNumber.toString()
        }

    val timestampFormatted: String
        get() = TIMESTAMP_FORMATTER.format(timestamp)

    val sizeFormatted: String
        get() = when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> String.format("%.1f MB", size / (1024.0 * 1024.0))
        }

    val coreLabel: String?
        get() = when {
            coreId != null && coreVersion != null -> "$coreId v$coreVersion"
            coreId != null -> coreId
            else -> null
        }

    companion object {
        private val TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("MMM d, HH:mm")
            .withZone(ZoneId.systemDefault())

        fun empty(slotNumber: Int): UnifiedStateEntry = UnifiedStateEntry(
            slotNumber = slotNumber,
            timestamp = Instant.EPOCH,
            size = 0,
            source = Source.LOCAL
        )
    }
}
