package com.nendo.argosy.data.repository

import android.content.Context
import android.util.Log
import com.nendo.argosy.util.AppPaths
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "HardResetRecorder"
private const val KEEP_RECORDS = 10
private const val FILE_PREFIX = "reset-"
private const val FILE_SUFFIX = ".json"

@JsonClass(generateAdapter = true)
data class HardResetRecordEntry(
    val gameId: Long,
    val title: String,
    val platformSlug: String,
    val path: String,
    val origin: String,
    val action: String,
    val result: String?
)

@JsonClass(generateAdapter = true)
data class HardResetRecord(
    val startedAt: Long,
    val completedAt: Long?,
    val deleted: Int,
    val kept: Int,
    val failed: Int,
    val entries: List<HardResetRecordEntry>
)

/**
 * Support trail for a hard reset: one JSON file per reset under the app's files directory,
 * written with every path the reset will touch before the first delete and rewritten with
 * per-file results as they land. The file stays after the reset so a user who lost something
 * can show what was removed and what was kept. Only the last [KEEP_RECORDS] are retained.
 */
@Singleton
class HardResetRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
    moshi: Moshi
) {
    private val adapter = moshi.adapter(HardResetRecord::class.java).indent("  ")

    inner class Session internal constructor(
        val file: File,
        private val startedAt: Instant,
        entries: List<HardResetRecordEntry>
    ) {
        private val entries = entries.toMutableList()
        private var deleted = 0
        private var kept = 0
        private var failed = 0

        fun recordDeleted(gameId: Long) = setResult(gameId, RESULT_DELETED) { deleted++ }

        fun recordMissing(gameId: Long) = setResult(gameId, RESULT_MISSING) { deleted++ }

        fun recordFailed(gameId: Long, reason: String?) =
            setResult(gameId, RESULT_FAILED + (reason?.let { ": $it" } ?: "")) { failed++ }

        fun recordKept(gameId: Long) = setResult(gameId, RESULT_KEPT) { kept++ }

        fun complete() {
            write(completedAt = Instant.now())
        }

        internal fun begin() {
            write(completedAt = null)
        }

        private fun setResult(gameId: Long, result: String, tally: () -> Unit) {
            val index = entries.indexOfFirst { it.gameId == gameId && it.result == null }
            if (index < 0) return
            entries[index] = entries[index].copy(result = result)
            tally()
        }

        private fun write(completedAt: Instant?) {
            val record = HardResetRecord(
                startedAt = startedAt.toEpochMilli(),
                completedAt = completedAt?.toEpochMilli(),
                deleted = deleted,
                kept = kept,
                failed = failed,
                entries = entries.toList()
            )
            try {
                file.parentFile?.mkdirs()
                file.writeText(adapter.toJson(record))
            } catch (e: Exception) {
                Log.e(TAG, "write: could not save reset record ${file.name}: ${e.message}")
            }
        }
    }

    fun begin(entries: List<HardResetRecordEntry>): Session {
        val startedAt = Instant.now()
        val dir = AppPaths.resetRecordsDir(context.filesDir)
        val file = File(dir, FILE_PREFIX + FILE_STAMP.format(startedAt) + FILE_SUFFIX)
        val session = Session(file, startedAt, entries)
        session.begin()
        pruneOldRecords(dir, keep = file)
        return session
    }

    private fun pruneOldRecords(dir: File, keep: File) {
        val records = dir.listFiles { f ->
            f.isFile && f.name.startsWith(FILE_PREFIX) && f.name.endsWith(FILE_SUFFIX)
        }?.sortedByDescending { it.name } ?: return
        records.filter { it != keep }
            .drop(KEEP_RECORDS - 1)
            .forEach { stale ->
                if (!stale.delete()) Log.w(TAG, "pruneOldRecords: could not delete ${stale.name}")
            }
    }

    companion object {
        const val ACTION_DELETE = "delete"
        const val ACTION_KEEP = "keep"
        const val RESULT_DELETED = "deleted"
        const val RESULT_MISSING = "missing"
        const val RESULT_FAILED = "failed"
        const val RESULT_KEPT = "kept"

        private val FILE_STAMP: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)
    }
}
