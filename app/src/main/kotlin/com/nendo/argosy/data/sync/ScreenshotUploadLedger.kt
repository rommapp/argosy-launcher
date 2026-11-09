package com.nendo.argosy.data.sync

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Source screenshots RomM has already answered for, keyed by account, rom, source path and
 * modification time. Entries expire after [RETENTION_MS]; past that the server's own
 * same-name dedupe keeps a repeat upload from adding a second copy.
 */
@Singleton
class ScreenshotUploadLedger @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun contains(ownerUserId: Long?, rommId: Long, sourcePath: String, sourceModifiedAt: Long): Boolean =
        prefs.contains(keyOf(ownerUserId, rommId, sourcePath, sourceModifiedAt))

    fun record(
        ownerUserId: Long?,
        rommId: Long,
        sourcePath: String,
        sourceModifiedAt: Long,
        now: Long = System.currentTimeMillis()
    ) {
        val cutoff = now - RETENTION_MS
        val editor = prefs.edit()
        prefs.all.forEach { (key, value) ->
            if ((value as? Long ?: 0L) < cutoff) editor.remove(key)
        }
        editor.putLong(keyOf(ownerUserId, rommId, sourcePath, sourceModifiedAt), now)
        editor.apply()
    }

    private fun keyOf(ownerUserId: Long?, rommId: Long, sourcePath: String, sourceModifiedAt: Long): String =
        "${ownerUserId ?: NO_OWNER}|$rommId|$sourceModifiedAt|$sourcePath"

    private companion object {
        const val PREFS_NAME = "screenshot_upload_ledger"
        const val NO_OWNER = "none"
        const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    }
}
