package com.nendo.argosy.util

import android.util.Log as AndroidLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object SaveDebugLogger {
    private const val TAG = "SaveDebugLogger"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val logChannel = Channel<SaveLogEntry>(Channel.BUFFERED)

    private var fileWriter: FileWriter? = null
    private var currentLogDate: LocalDate? = null
    private var logDirectory: String? = null
    private var versionName: String = "unknown"

    @Volatile
    private var enabled = false

    private val timestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    val isEnabled: Boolean get() = enabled

    init {
        scope.launch {
            logChannel.receiveAsFlow().collect { entry ->
                writeToFile(entry)
            }
        }
    }

    fun configure(versionName: String, logDirectory: String?, enabled: Boolean) {
        this.versionName = versionName
        this.logDirectory = logDirectory
        this.enabled = enabled && logDirectory != null

        if (!this.enabled) {
            closeCurrentFile()
        }

        if (this.enabled) {
            AndroidLog.i(TAG, "Save debug logging enabled, output: $logDirectory/saves-*.log")
        }
    }

    fun logCacheCreated(
        gameId: Long,
        gameName: String?,
        channel: String?,
        sizeBytes: Long,
        contentHash: String,
        isHardcore: Boolean,
        needsRemoteSync: Boolean,
        emulatorId: String
    ) {
        log(
            event = "CACHE_CREATED",
            gameId = gameId,
            gameName = gameName,
            channel = channel,
            details = buildString {
                append("size=${formatSize(sizeBytes)}")
                append(", hash=${contentHash.take(12)}...")
                append(", hardcore=$isHardcore")
                append(", needsSync=$needsRemoteSync")
                append(", emulator=$emulatorId")
            }
        )
    }

    fun logCacheDuplicate(
        gameId: Long,
        gameName: String?,
        channel: String?,
        contentHash: String
    ) {
        log(
            event = "CACHE_DUPLICATE",
            gameId = gameId,
            gameName = gameName,
            channel = channel,
            details = "hash=${contentHash.take(12)}... (skipped)"
        )
    }

    fun logCacheRestored(
        gameId: Long,
        gameName: String?,
        channel: String?,
        cacheId: Long,
        targetPath: String
    ) {
        log(
            event = "CACHE_RESTORED",
            gameId = gameId,
            gameName = gameName,
            channel = channel,
            details = "cacheId=$cacheId, target=${File(targetPath).name}"
        )
    }

    fun logCacheDeleted(
        gameId: Long,
        gameName: String?,
        channel: String?,
        cacheId: Long,
        reason: String
    ) {
        log(
            event = "CACHE_DELETED",
            gameId = gameId,
            gameName = gameName,
            channel = channel,
            details = "cacheId=$cacheId, reason=$reason"
        )
    }

    fun logCachePruned(
        gameId: Long,
        gameName: String?,
        prunedCount: Int,
        remainingCount: Int
    ) {
        log(
            event = "CACHE_PRUNED",
            gameId = gameId,
            gameName = gameName,
            channel = null,
            details = "deleted=$prunedCount, remaining=$remainingCount"
        )
    }

    fun logSyncUploadStarted(
        gameId: Long,
        gameName: String?,
        channel: String?,
        sizeBytes: Long,
        contentHash: String
    ) {
        log(
            event = "SYNC_UPLOAD_START",
            gameId = gameId,
            gameName = gameName,
            channel = channel,
            details = "size=${formatSize(sizeBytes)}, hash=${contentHash.take(12)}..."
        )
    }

    fun logSyncUploadCompleted(
        gameId: Long,
        gameName: String?,
        channel: String?,
        serverId: Long?,
        durationMs: Long
    ) {
        log(
            event = "SYNC_UPLOAD_OK",
            gameId = gameId,
            gameName = gameName,
            channel = channel,
            details = "serverId=$serverId, duration=${durationMs}ms"
        )
    }

    fun logSyncUploadFailed(
        gameId: Long,
        gameName: String?,
        channel: String?,
        error: String
    ) {
        log(
            event = "SYNC_UPLOAD_FAIL",
            gameId = gameId,
            gameName = gameName,
            channel = channel,
            details = "error=$error"
        )
    }

    fun logSyncDownloadStarted(
        gameId: Long,
        gameName: String?,
        channel: String?,
        serverId: Long
    ) {
        log(
            event = "SYNC_DOWNLOAD_START",
            gameId = gameId,
            gameName = gameName,
            channel = channel,
            details = "serverId=$serverId"
        )
    }

    fun logSyncDownloadCompleted(
        gameId: Long,
        gameName: String?,
        channel: String?,
        sizeBytes: Long,
        durationMs: Long
    ) {
        log(
            event = "SYNC_DOWNLOAD_OK",
            gameId = gameId,
            gameName = gameName,
            channel = channel,
            details = "size=${formatSize(sizeBytes)}, duration=${durationMs}ms"
        )
    }

    fun logSyncDownloadFailed(
        gameId: Long,
        gameName: String?,
        channel: String?,
        error: String
    ) {
        log(
            event = "SYNC_DOWNLOAD_FAIL",
            gameId = gameId,
            gameName = gameName,
            channel = channel,
            details = "error=$error"
        )
    }

    fun logSyncStatusChanged(
        gameId: Long,
        gameName: String?,
        channel: String?,
        oldStatus: String?,
        newStatus: String,
        reason: String? = null
    ) {
        log(
            event = "SYNC_STATUS",
            gameId = gameId,
            gameName = gameName,
            channel = channel,
            details = buildString {
                append("$oldStatus -> $newStatus")
                if (reason != null) append(" ($reason)")
            }
        )
    }

    fun logConflictDetected(
        gameId: Long,
        gameName: String?,
        channel: String?,
        localHash: String?,
        serverHash: String?,
        localTime: Instant?,
        serverTime: Instant?
    ) {
        log(
            event = "SYNC_CONFLICT",
            gameId = gameId,
            gameName = gameName,
            channel = channel,
            details = buildString {
                append("localHash=${localHash?.take(12) ?: "null"}...")
                append(", serverHash=${serverHash?.take(12) ?: "null"}...")
                append(", localTime=${localTime?.let { formatInstant(it) } ?: "null"}")
                append(", serverTime=${serverTime?.let { formatInstant(it) } ?: "null"}")
            }
        )
    }

    fun logConflictResolved(
        gameId: Long,
        gameName: String?,
        channel: String?,
        resolution: String
    ) {
        log(
            event = "SYNC_CONFLICT_RESOLVED",
            gameId = gameId,
            gameName = gameName,
            channel = channel,
            details = "resolution=$resolution"
        )
    }

    fun logSessionStart(
        gameId: Long,
        gameName: String?,
        channel: String?,
        isHardcore: Boolean,
        existingSaveHash: String?
    ) {
        log(
            event = "SESSION_START",
            gameId = gameId,
            gameName = gameName,
            channel = channel,
            details = buildString {
                append("hardcore=$isHardcore")
                append(", existingHash=${existingSaveHash?.take(12) ?: "none"}...")
            }
        )
    }

    fun logSessionEnd(
        gameId: Long,
        gameName: String?,
        channel: String?,
        durationSec: Long,
        saveChanged: Boolean,
        newHash: String?
    ) {
        log(
            event = "SESSION_END",
            gameId = gameId,
            gameName = gameName,
            channel = channel,
            details = buildString {
                append("duration=${durationSec}s")
                append(", saveChanged=$saveChanged")
                if (newHash != null) append(", newHash=${newHash.take(12)}...")
            }
        )
    }

    fun logChannelCreated(
        gameId: Long,
        gameName: String?,
        channel: String,
        fromCacheId: Long?
    ) {
        log(
            event = "CHANNEL_CREATED",
            gameId = gameId,
            gameName = gameName,
            channel = channel,
            details = if (fromCacheId != null) "fromCache=$fromCacheId" else "new"
        )
    }

    fun logChannelDeleted(
        gameId: Long,
        gameName: String?,
        channel: String
    ) {
        log(
            event = "CHANNEL_DELETED",
            gameId = gameId,
            gameName = gameName,
            channel = channel,
            details = null
        )
    }

    fun logChannelSwitched(
        gameId: Long,
        gameName: String?,
        fromChannel: String?,
        toChannel: String?
    ) {
        log(
            event = "CHANNEL_SWITCHED",
            gameId = gameId,
            gameName = gameName,
            channel = toChannel,
            details = "from=${fromChannel ?: "default"} to=${toChannel ?: "default"}"
        )
    }

    fun logError(
        operation: String,
        gameId: Long?,
        gameName: String?,
        channel: String?,
        error: Throwable
    ) {
        log(
            event = "ERROR",
            gameId = gameId,
            gameName = gameName,
            channel = channel,
            details = "$operation: ${error.message}"
        )
    }

    fun logCustom(
        event: String,
        gameId: Long?,
        gameName: String?,
        channel: String?,
        details: String?
    ) {
        log(event, gameId, gameName, channel, details)
    }

    private fun log(
        event: String,
        gameId: Long?,
        gameName: String?,
        channel: String?,
        details: String?
    ) {
        val message = buildString {
            append("[$event]")
            if (gameId != null) append(" gameId=$gameId")
            if (!gameName.isNullOrBlank()) append(" game=\"$gameName\"")
            if (!channel.isNullOrBlank()) append(" channel=\"$channel\"")
            if (!details.isNullOrBlank()) append(" | $details")
        }

        AndroidLog.d(TAG, message)

        if (enabled) {
            val entry = SaveLogEntry(
                timestamp = LocalDateTime.now(),
                event = event,
                gameId = gameId,
                gameName = gameName,
                channel = channel,
                details = details
            )
            scope.launch { logChannel.send(entry) }
        }
    }

    private fun writeToFile(entry: SaveLogEntry) {
        val dir = logDirectory ?: return

        val today = entry.timestamp.toLocalDate()
        if (today != currentLogDate) {
            rotateLogFile(dir, today)
        }

        val writer = fileWriter ?: return

        val line = buildString {
            append(entry.timestamp.format(timestampFormat))
            append(" [")
            append(entry.event.padEnd(20))
            append("]")
            if (entry.gameId != null) {
                append(" game=")
                append(entry.gameId)
            }
            if (!entry.gameName.isNullOrBlank()) {
                append(" \"")
                append(entry.gameName.take(30))
                append("\"")
            }
            if (!entry.channel.isNullOrBlank()) {
                append(" ch=\"")
                append(entry.channel)
                append("\"")
            }
            if (!entry.details.isNullOrBlank()) {
                append(" | ")
                append(entry.details)
            }
        }

        writer.appendLine(line)
        writer.flush()
    }

    private fun rotateLogFile(directory: String, date: LocalDate) {
        closeCurrentFile()

        val fileName = "saves-$versionName-${date.format(dateFormat)}.log"
        val logFile = File(directory, fileName)

        try {
            logFile.parentFile?.mkdirs()
            fileWriter = FileWriter(logFile, true)
            currentLogDate = date

            fileWriter?.appendLine("=== Argosy Save Debug Log - $versionName - ${date.format(dateFormat)} ===")
            fileWriter?.appendLine("Format: TIMESTAMP [EVENT] game=ID \"NAME\" ch=\"CHANNEL\" | DETAILS")
            fileWriter?.appendLine("========================================================================")
            fileWriter?.flush()
        } catch (e: Exception) {
            AndroidLog.e(TAG, "Failed to create save debug log file", e)
            fileWriter = null
        }
    }

    private fun closeCurrentFile() {
        try {
            fileWriter?.close()
        } catch (_: Exception) { }
        fileWriter = null
        currentLogDate = null
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${bytes / 1024}KB"
            else -> "${bytes / (1024 * 1024)}MB"
        }
    }

    private fun formatInstant(instant: Instant): String {
        return DateTimeFormatter.ofPattern("MM-dd HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(instant)
    }
}

private data class SaveLogEntry(
    val timestamp: LocalDateTime,
    val event: String,
    val gameId: Long?,
    val gameName: String?,
    val channel: String?,
    val details: String?
)
