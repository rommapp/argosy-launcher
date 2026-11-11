package com.nendo.argosy.data.download.transfer

import com.nendo.argosy.data.download.DownloadFailureReason
import com.nendo.argosy.data.download.ZipExtractor
import com.nendo.argosy.util.Logger
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.ProtocolException
import java.security.cert.CertificateException
import java.util.zip.ZipFile
import java.util.zip.ZipException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import okhttp3.Protocol
import retrofit2.Call

class ResumableTransfer {
    suspend fun attempt(
        partFile: File,
        checkpoint: TransferCheckpoint,
        newCall: (range: String?, ifRange: String?) -> Call<ResponseBody>,
        persist: suspend (TransferCheckpoint) -> Unit,
        onProgress: suspend (bytes: Long, total: Long?) -> Unit,
        beforeRead: suspend () -> Unit = {},
        afterWrite: suspend () -> Unit = {},
        requireZip: Boolean = false,
        initialRange: Boolean = false
    ): TransferResult = withContext(Dispatchers.IO) {
        coroutineScope {
            val offset = try {
                partFile.length()
            } catch (e: SecurityException) {
                return@coroutineScope TransferResult.Permanent(DownloadFailureReason.StorageAccessFailed, e.message ?: "Storage access denied")
            }
            val resume = offset > 0 && TransferHeaders.resumable(checkpoint.validator)
            val requestOffset = if (resume) offset else 0L
            val rangeRequested = resume || initialRange
            Logger.info("ResumableTransfer", "Request | rangePresent=$rangeRequested requestOffset=$requestOffset")
            val call = try {
                newCall(if (rangeRequested) "bytes=$requestOffset-" else null, if (resume) checkpoint.validator else null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                currentCoroutineContext().ensureActive()
                return@coroutineScope networkFailure(e)
            }
            val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    call.cancel()
                }
            }
            try {
                val response = try {
                    call.execute()
                } catch (e: ProtocolException) {
                    currentCoroutineContext().ensureActive()
                    return@coroutineScope TransferResult.Permanent(DownloadFailureReason.InvalidResponse, e.message ?: "Invalid HTTP response")
                }
                try {
                    currentCoroutineContext().ensureActive()
                    val code = response.code()
                    Logger.info("ResumableTransfer", "Response | code=$code")
                    when {
                        code == 416 -> if (resume) {
                            completedPrefix(partFile, requestOffset, checkpoint, response.headers()["Content-Range"], persist, requireZip)
                        } else TransferResult.RestartRequired
                        code == 408 || code == 429 || code in setOf(500, 502, 503, 504, 524) ->
                            TransferResult.Retryable("HTTP $code", response.headers()["Retry-After"])
                        code != 200 && code != 206 -> TransferResult.Permanent(DownloadFailureReason.HttpError(code), "HTTP $code")
                        code == 206 && !rangeRequested -> TransferResult.Permanent(DownloadFailureReason.InvalidResponse, "Unsolicited partial response")
                        else -> {
                            val framedStream = response.raw().protocol in setOf(Protocol.HTTP_2, Protocol.H2_PRIOR_KNOWLEDGE)
                            val append = code == 206 && resume
                            val headers = TransferHeaders.parse(
                                response.headers(), code == 206, requestOffset, checkpoint, framedStream, resume
                            )
                            val complete = code == 200 || headers.segment == headers.total?.minus(requestOffset)
                            val validatorCategory = when {
                                TransferHeaders.strongEtag(headers.validator) -> "etag"
                                headers.validator != null -> "last-modified"
                                else -> "none"
                            }
                            Logger.info("ResumableTransfer", "Response | validatorCategory=$validatorCategory")
                            if (code == 206 && !resume && !complete && !TransferHeaders.resumable(headers.validator)) {
                                return@coroutineScope TransferResult.Permanent(DownloadFailureReason.InvalidResponse, "Partial response without resume validator")
                            }
                            val checksums = TransferChecksums.parse(
                                response.headers(), complete, code == 206,
                                checkpoint.representationChecksum.takeIf { append },
                                fullBody = !append && complete
                            )
                            val body = response.body() ?: return@coroutineScope TransferResult.Permanent(DownloadFailureReason.InvalidResponse, "Missing response body")
                            if (!append) Logger.info("ResumableTransfer", "Reset | resetReason=fresh-response")
                            writeBody(partFile, body, headers, checksums, append, requestOffset, persist, onProgress, beforeRead, afterWrite, requireZip)
                        }
                    }
                } finally {
                    response.body()?.close()
                    response.errorBody()?.close()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ChangedRepresentation) {
                Logger.info("ResumableTransfer", "Reset | resetReason=${e.message}")
                TransferResult.RestartRequired
            } catch (e: InvalidTransferHeaders) {
                TransferResult.Permanent(e.reason, e.message ?: "Invalid response headers")
            } catch (e: DiskFailure) {
                TransferResult.Permanent(DownloadFailureReason.StorageAccessFailed, e.cause?.message ?: "Disk failure")
            } catch (e: SecurityException) {
                TransferResult.Permanent(DownloadFailureReason.StorageAccessFailed, e.message ?: "Storage access denied")
            } catch (e: IOException) {
                currentCoroutineContext().ensureActive()
                networkFailure(e)
            } finally {
                cancellation.cancel()
            }
        }
    }

    private suspend fun writeBody(
        file: File,
        body: ResponseBody,
        headers: TransferHeaders,
        checksums: TransferChecksums,
        append: Boolean,
        offset: Long,
        persist: suspend (TransferCheckpoint) -> Unit,
        onProgress: suspend (Long, Long?) -> Unit,
        beforeRead: suspend () -> Unit,
        afterWrite: suspend () -> Unit,
        requireZip: Boolean
    ): TransferResult {
        val metadata = TransferCheckpoint(
            headers.validator, headers.total, representationChecksum = checksums.representationChecksum
        )
        if (!append) disk { persist(TransferCheckpoint()) }
        val output = disk { RandomAccessFile(file, "rw") }
        var received = 0L
        val initial = if (append) offset else 0L
        var checksumMismatch = false
        val guardedSegment = checksums.hasUnprotectedSegmentChecksums
        var segmentVerified = false
        try {
            if (!append) {
                disk { output.setLength(0); output.fd.sync() }
            }
            if (append && checksums.needsPrefix) {
                val prefix = ByteArray(64 * 1024)
                var remaining = initial
                while (remaining > 0) {
                    currentCoroutineContext().ensureActive()
                    val count = disk { output.read(prefix, 0, minOf(prefix.size.toLong(), remaining).toInt()) }
                    if (count < 0) throw DiskFailure(IOException("Truncated local prefix"))
                    checksums.updatePrefix(prefix, count)
                    remaining -= count
                }
            }
            disk { output.seek(initial) }
            disk { persist(if (guardedSegment) TransferCheckpoint() else metadata) }
            val input = body.byteStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                beforeRead()
                val count = input.read(buffer)
                if (count == -1) break
                if (count == 0) continue
                if (received > Long.MAX_VALUE - initial - count ||
                    (headers.segment != null && count > headers.segment - received)) {
                    return TransferResult.Permanent(DownloadFailureReason.InvalidResponse, "Response exceeds declared length")
                }
                disk { output.write(buffer, 0, count) }
                checksums.update(buffer, count)
                received += count
                afterWrite()
                onProgress(initial + received, headers.total)
            }
            if (headers.segment == null || received == headers.segment) {
                checksumMismatch = !checksums.matches()
                segmentVerified = !checksumMismatch
                if (checksumMismatch && !guardedSegment) disk { output.setLength(initial) }
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                try {
                    if (guardedSegment && !segmentVerified) disk { output.setLength(initial) }
                    disk { output.fd.sync() }
                } finally {
                    disk { output.close() }
                }
                if (guardedSegment) disk { persist(metadata) }
            }
        }
        if (headers.segment != null && received != headers.segment) return TransferResult.Retryable("Truncated response")
        if (received == 0L) return TransferResult.Permanent(DownloadFailureReason.FileTooSmall, "Empty response")
        if (checksumMismatch) return TransferResult.Permanent(DownloadFailureReason.IntegrityCheckFailed, "Checksum mismatch")
        val bytes = initial + received
        if (headers.total != null && bytes < headers.total) return TransferResult.Continue
        if (requireZip && disk { ZipExtractor.detectRomFormatByMagic(file) } == null) disk {
            try {
                ZipFile(file).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) entries.nextElement()
                }
            } catch (e: ZipException) {
                return TransferResult.Permanent(DownloadFailureReason.IntegrityCheckFailed, e.message ?: "Invalid ZIP archive")
            }
        }
        disk { persist(metadata.copy(validatedBytes = bytes)) }
        return TransferResult.Verified(bytes)
    }

    private suspend fun completedPrefix(
        file: File,
        length: Long,
        checkpoint: TransferCheckpoint,
        contentRange: String?,
        persist: suspend (TransferCheckpoint) -> Unit,
        requireZip: Boolean
    ): TransferResult {
        val total = contentRange?.let { UNSATISFIED_RANGE.matchEntire(it.trim()) }?.groupValues?.get(1)?.toLongOrNull()
        val sameRepresentation = total == length &&
            (checkpoint.totalBytes == null || checkpoint.totalBytes == total) &&
            checkpoint.representationChecksum == null
        if (!sameRepresentation) return TransferResult.RestartRequired
        if (requireZip && disk { ZipExtractor.detectRomFormatByMagic(file) } == null) disk {
            try {
                ZipFile(file).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) entries.nextElement()
                }
            } catch (_: ZipException) {
                return TransferResult.RestartRequired
            }
        }
        Logger.info("ResumableTransfer", "Response | completePrefix=$length")
        disk { persist(checkpoint.copy(totalBytes = length, validatedBytes = length)) }
        return TransferResult.Verified(length)
    }

    private fun networkFailure(error: IOException): TransferResult {
        val certificateFailure = error is SSLHandshakeException &&
            generateSequence<Throwable>(error) { it.cause }.take(16).any { it is CertificateException }
        return if (error is SSLPeerUnverifiedException || certificateFailure) {
            TransferResult.Permanent(DownloadFailureReason.TlsVerificationFailed, error.message ?: "TLS identity verification failed")
        } else TransferResult.Retryable(error.message ?: "Connection failure")
    }

    private inline fun <T> disk(block: () -> T): T = try {
        block()
    } catch (e: IOException) {
        throw DiskFailure(e)
    }

    private class DiskFailure(cause: IOException) : Exception(cause)

    private companion object {
        val UNSATISFIED_RANGE = Regex("bytes \\*/([0-9]+)")
    }
}
