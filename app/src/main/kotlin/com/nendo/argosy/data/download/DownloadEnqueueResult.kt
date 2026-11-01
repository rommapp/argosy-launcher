package com.nendo.argosy.data.download

sealed interface DownloadEnqueueResult {
    data class Accepted(val downloadId: Long) : DownloadEnqueueResult
    data class Conflict(val downloadId: Long) : DownloadEnqueueResult
}
