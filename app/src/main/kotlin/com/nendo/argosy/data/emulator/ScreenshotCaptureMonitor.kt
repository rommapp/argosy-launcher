package com.nendo.argosy.data.emulator

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.entity.PendingSyncQueueEntity
import com.nendo.argosy.data.local.entity.SyncPriority
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import com.nendo.argosy.data.sync.ScreenshotPayload
import com.nendo.argosy.data.sync.SyncPayloadCodec
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.PermissionHelper
import com.nendo.argosy.util.SafeCoroutineScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Watches MediaStore for system screenshots during a play session and queues them for RomM upload. */
@Singleton
class ScreenshotCaptureMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao,
    private val pendingSyncQueueDao: PendingSyncQueueDao,
    private val payloadCodec: SyncPayloadCodec,
    private val syncPreferencesRepository: SyncPreferencesRepository,
    private val connectionManager: RomMConnectionManager,
    private val permissionHelper: PermissionHelper
) {
    private val scope = SafeCoroutineScope(Dispatchers.IO, "ScreenshotCaptureMonitor")
    private val processMutex = Mutex()

    @Volatile private var observer: ContentObserver? = null
    @Volatile private var sessionGameId: Long = -1
    @Volatile private var sessionEmulatorPackage: String? = null
    @Volatile private var lastSeenMediaId: Long = -1
    @Volatile private var sessionStartEpochSec: Long = 0
    @Volatile private var usageAccessWarned: Boolean = false

    fun start(gameId: Long, emulatorPackage: String?, sessionStartTime: Long) {
        stop()
        if (gameId <= 0) return
        if (!hasMediaPermission()) return
        if (!connectionManager.getCapabilities().supportsScreenshotUpload) return
        sessionGameId = gameId
        sessionEmulatorPackage = emulatorPackage
        sessionStartEpochSec = sessionStartTime / 1000
        lastSeenMediaId = -1
        usageAccessWarned = false

        val contentObserver = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                scope.launch { processNewScreenshots() }
            }
        }
        context.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, contentObserver
        )
        observer = contentObserver

        scope.launch {
            val baseline = queryMaxMediaId()
            processMutex.withLock {
                if (observer === contentObserver) {
                    lastSeenMediaId = maxOf(lastSeenMediaId, baseline)
                }
            }
            Logger.debug(TAG, "Watching for screenshots (gameId=$sessionGameId, lastSeenId=$lastSeenMediaId)")
        }
    }

    fun stop() {
        observer?.let { context.contentResolver.unregisterContentObserver(it) }
        observer = null
        sessionGameId = -1
        sessionEmulatorPackage = null
    }

    private suspend fun processNewScreenshots() = processMutex.withLock {
        val gameId = sessionGameId
        if (gameId <= 0) return
        val prefs = syncPreferencesRepository.preferences.first()
        if (!prefs.uploadScreenshotsEnabled) return
        val ownerUserId = prefs.rommUserId
        if (!hasMediaPermission()) return
        if (!connectionManager.getCapabilities().supportsScreenshotUpload) return

        val game = gameDao.getById(gameId) ?: return
        val rommId = game.rommId ?: return

        val candidates = queryNewScreenshots()
        if (candidates.isEmpty()) return

        val foregroundPackage = when (val pkg = sessionEmulatorPackage) {
            null, EmulatorRegistry.BUILTIN_PACKAGE -> context.packageName
            else -> pkg
        }
        if (permissionHelper.hasUsageStatsPermission(context)) {
            if (!permissionHelper.isPackageInForeground(context, foregroundPackage, withinMs = FOREGROUND_WINDOW_MS)) {
                Logger.debug(TAG, "Screenshot ignored: $foregroundPackage not foregrounded")
                candidates.maxOfOrNull { it.mediaId }?.let { lastSeenMediaId = maxOf(lastSeenMediaId, it) }
                return
            }
        } else if (!usageAccessWarned) {
            usageAccessWarned = true
            Logger.warn(TAG, "Usage access not granted; attributing screenshots to the active session without foreground check")
        }

        for (candidate in candidates) {
            lastSeenMediaId = maxOf(lastSeenMediaId, candidate.mediaId)
            val copied = copyToPendingDir(candidate) ?: continue
            pendingSyncQueueDao.insert(
                PendingSyncQueueEntity(
                    gameId = gameId,
                    rommId = rommId,
                    syncType = SyncType.SCREENSHOT,
                    priority = SyncPriority.PROPERTY,
                    payloadJson = payloadCodec.encode(ScreenshotPayload(copied.absolutePath)),
                    ownerUserId = ownerUserId
                )
            )
            Logger.info(TAG, "Queued screenshot ${candidate.displayName} for gameId=$gameId rommId=$rommId")
        }
    }

    private data class ScreenshotCandidate(val mediaId: Long, val displayName: String)

    private fun queryNewScreenshots(): List<ScreenshotCandidate> {
        val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.RELATIVE_PATH
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.DATA
        }
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            pathColumn
        )
        val selection = buildString {
            append("${MediaStore.Images.Media._ID} > ? AND ${MediaStore.Images.Media.DATE_ADDED} >= ?")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                append(" AND ${MediaStore.Images.Media.IS_PENDING} = 0")
            }
        }
        val selectionArgs = arrayOf(lastSeenMediaId.toString(), sessionStartEpochSec.toString())

        val results = mutableListOf<ScreenshotCandidate>()
        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs,
                "${MediaStore.Images.Media._ID} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val pathIdx = cursor.getColumnIndexOrThrow(pathColumn)
                while (cursor.moveToNext()) {
                    val path = cursor.getString(pathIdx) ?: ""
                    val name = cursor.getString(nameIdx) ?: ""
                    if (!path.contains("screenshot", ignoreCase = true) &&
                        !name.contains("screenshot", ignoreCase = true)
                    ) continue
                    results.add(ScreenshotCandidate(cursor.getLong(idIdx), name))
                }
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to query MediaStore for screenshots", e)
        }
        return results
    }

    private fun queryMaxMediaId(): Long {
        return try {
            val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val args = Bundle().apply {
                    putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.Images.Media._ID))
                    putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                    putInt(ContentResolver.QUERY_ARG_LIMIT, 1)
                }
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Images.Media._ID), args, null
                )
            } else {
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Images.Media._ID),
                    null, null,
                    "${MediaStore.Images.Media._ID} DESC LIMIT 1"
                )
            }
            cursor?.use { if (it.moveToFirst()) it.getLong(0) else -1L } ?: -1L
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to query max media id", e)
            -1L
        }
    }

    private fun copyToPendingDir(candidate: ScreenshotCandidate): File? {
        return try {
            val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, candidate.mediaId)
            val pendingDir = File(context.filesDir, PENDING_DIR).also { it.mkdirs() }
            val safeName = candidate.displayName.ifBlank { "screenshot_${candidate.mediaId}.png" }
            val target = File(pendingDir, "${candidate.mediaId}_$safeName")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            if (target.length() <= 0) {
                target.delete()
                return null
            }
            target
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to copy screenshot ${candidate.displayName}", e)
            null
        }
    }

    private fun hasMediaPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            val allFilesAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                Environment.isExternalStorageManager()
            allFilesAccess ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    companion object {
        private const val TAG = "ScreenshotCaptureMonitor"
        private const val PENDING_DIR = "pending_screenshots"
        private const val FOREGROUND_WINDOW_MS = 15_000L
    }
}
