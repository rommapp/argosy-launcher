package com.nendo.argosy.data.repository

import com.nendo.argosy.data.storage.FileAccessLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val DOWNLOAD_COMPLETE_MARKER = ".download_complete"
private const val DOWNLOAD_IN_PROGRESS_MARKER = ".download_in_progress"

/**
 * Single source of truth for "is this game's payload present on disk." Routes every
 * filesystem probe through [FileAccessLayer] and forces the IO onto [Dispatchers.IO]
 * so callers can invoke from arbitrary contexts (ViewModels, observers) without
 * blocking the main thread. Suspend-only by design - the four call sites all need
 * a one-shot answer rather than a hot stream.
 */
@Singleton
class DownloadFileStatusRepository @Inject constructor(
    private val fileAccessLayer: FileAccessLayer
) {

    suspend fun pathExists(localPath: String?): Boolean {
        if (localPath.isNullOrBlank()) return false
        return withContext(Dispatchers.IO) { fileAccessLayer.exists(localPath) }
    }

    suspend fun isDownloadComplete(localPath: String?): Boolean {
        if (localPath.isNullOrBlank()) return false
        return withContext(Dispatchers.IO) {
            fileAccessLayer.exists(joinMarker(localPath, DOWNLOAD_COMPLETE_MARKER))
        }
    }

    suspend fun isDownloadInProgress(localPath: String?): Boolean {
        if (localPath.isNullOrBlank()) return false
        return withContext(Dispatchers.IO) {
            fileAccessLayer.exists(joinMarker(localPath, DOWNLOAD_IN_PROGRESS_MARKER))
        }
    }

    private fun joinMarker(localPath: String, marker: String): String =
        "${localPath.trimEnd('/')}/$marker"
}
