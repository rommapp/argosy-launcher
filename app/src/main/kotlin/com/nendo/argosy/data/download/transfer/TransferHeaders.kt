package com.nendo.argosy.data.download.transfer

import com.nendo.argosy.data.download.DownloadFailureReason
import okhttp3.Headers

internal class InvalidTransferHeaders(
    message: String,
    val reason: DownloadFailureReason = DownloadFailureReason.InvalidResponse
) : Exception(message)

internal class ChangedRepresentation(message: String) : Exception(message)

internal data class TransferHeaders(
    val validator: String?,
    val total: Long?,
    val segment: Long?
) {
    companion object {
        fun parse(
            headers: Headers,
            partial: Boolean,
            offset: Long,
            checkpoint: TransferCheckpoint,
            framedStream: Boolean = false,
            resuming: Boolean = partial
        ): TransferHeaders {
            fun single(name: String): String? {
                val values = headers.values(name)
                if (values.size > 1) throw InvalidTransferHeaders("Duplicate $name")
                return values.singleOrNull()
            }
            val encoding = single("Content-Encoding")
            val mime = single("Content-Type")?.substringBefore(';')?.trim()?.lowercase()
            if (mime == "text/html") {
                throw InvalidTransferHeaders("Invalid download content type", DownloadFailureReason.InvalidContentType(requireNotNull(mime)))
            }
            if (encoding != null && !encoding.equals("identity", true)) {
                throw InvalidTransferHeaders("Encoded response")
            }
            val etag = single("ETag")
            val modified = single("Last-Modified")
            val date = httpDateMillis(single("Date"))
            val modifiedDate = httpDateMillis(modified)
            val validator = when {
                strongEtag(etag) -> etag
                modifiedDate != null && date != null &&
                    date >= Long.MIN_VALUE + 60_000 && modifiedDate <= date - 60_000 -> modified
                else -> null
            }
            val length = single("Content-Length")?.let {
                if (!it.matches(Regex("[0-9]+"))) throw InvalidTransferHeaders("Invalid Content-Length")
                it.toLongOrNull() ?: throw InvalidTransferHeaders("Content-Length overflow")
            }
            val transferEncoding = single("Transfer-Encoding")
            if (transferEncoding != null && !transferEncoding.equals("chunked", true)) {
                throw InvalidTransferHeaders("Unsupported transfer encoding")
            }
            if (transferEncoding != null && length != null) throw InvalidTransferHeaders("Ambiguous framing")
            val range = single("Content-Range")
            if (!partial) {
                if (range != null) throw InvalidTransferHeaders("Unexpected Content-Range")
                if (length == 0L) throw InvalidTransferHeaders("Empty response", DownloadFailureReason.FileTooSmall)
                if (length == null && transferEncoding == null && !framedStream) {
                    throw InvalidTransferHeaders("Unbounded response")
                }
                return TransferHeaders(validator, length, length)
            }
            val match = range?.let { Regex("bytes ([0-9]+)-([0-9]+)/([0-9]+)").matchEntire(it) }
                ?: throw InvalidTransferHeaders("Invalid Content-Range")
            val (start, end, total) = match.groupValues.drop(1).map {
                it.toLongOrNull() ?: throw InvalidTransferHeaders("Content-Range overflow")
            }
            if (start != offset || end < start || end >= total) throw InvalidTransferHeaders("Inconsistent Content-Range")
            val segment = end - start + 1
            if (length != null && length != segment) throw InvalidTransferHeaders("Inconsistent Content-Length")
            if (resuming && (validator == null || validator != checkpoint.validator)) throw ChangedRepresentation("Changed validator")
            if (resuming && checkpoint.totalBytes != null && checkpoint.totalBytes != total) {
                throw ChangedRepresentation("Changed representation length")
            }
            return TransferHeaders(validator, total, segment)
        }

        fun strongEtag(value: String?): Boolean = value != null &&
            value.length >= 2 && value.first() == '"' && value.last() == '"' &&
            value.substring(1, value.lastIndex).all { it == '\u0021' || it in '\u0023'..'\u007e' || it >= '\u0080' }

        fun resumable(validator: String?): Boolean = strongEtag(validator) || httpDateMillis(validator) != null
    }
}
