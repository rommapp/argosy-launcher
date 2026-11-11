package com.nendo.argosy.data.download.transfer

import com.nendo.argosy.data.download.DownloadFailureReason

data class TransferCheckpoint(
    val validator: String? = null,
    val totalBytes: Long? = null,
    val validatedBytes: Long? = null,
    val representationChecksum: String? = null
)

sealed interface TransferResult {
    data class Verified(val bytes: Long) : TransferResult
    data class Retryable(val message: String, val retryAfter: String? = null) : TransferResult
    data class Permanent(val reason: DownloadFailureReason, val message: String) : TransferResult
    data object RestartRequired : TransferResult
    data object Continue : TransferResult
}
