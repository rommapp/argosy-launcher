package com.nendo.argosy.data.download.transfer

data class TransferCheckpoint(
    val validator: String? = null,
    val totalBytes: Long? = null,
    val validatedBytes: Long? = null,
    val representationChecksum: String? = null
)

sealed interface TransferResult {
    data class Verified(val bytes: Long) : TransferResult
    data class Retryable(val message: String, val retryAfter: String? = null) : TransferResult
    data class Permanent(val message: String) : TransferResult
    data object RestartRequired : TransferResult
    data object Continue : TransferResult
}
