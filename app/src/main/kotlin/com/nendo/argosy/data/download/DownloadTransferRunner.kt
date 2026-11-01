package com.nendo.argosy.data.download

import com.nendo.argosy.data.download.transfer.DownloadRetryPolicy
import com.nendo.argosy.data.download.transfer.ResumableTransfer
import com.nendo.argosy.data.download.transfer.TransferCheckpoint
import com.nendo.argosy.data.download.transfer.TransferResult
import com.nendo.argosy.util.Logger
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import retrofit2.Call

internal typealias DownloadAttempt = suspend (
    File, TransferCheckpoint, (String?, String?) -> Call<ResponseBody>,
    suspend (TransferCheckpoint) -> Unit, suspend (Long, Long?) -> Unit,
    suspend () -> Unit, suspend () -> Unit, Boolean, Boolean
) -> TransferResult

internal class DownloadTransferRunner(
    private val connectivity: DownloadConnectivity,
    private val retryPolicy: DownloadRetryPolicy = DownloadRetryPolicy(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val transfer: DownloadAttempt = { file, checkpoint, call, persist, progress, before, after, requireZip, initialRange ->
        ResumableTransfer().attempt(file, checkpoint, call, persist, progress, before, after, requireZip, initialRange)
    }
) {
    suspend fun resolveRequestKey(
        initialAttempt: Int,
        initialRetryAt: Long?,
        resolve: () -> String,
        persistRetry: suspend (Int, Long?) -> Unit,
        onState: suspend (DownloadState) -> Unit
    ): String {
        var attempt = initialAttempt
        var retryAt = initialRetryAt
        while (true) {
            awaitReady(retryAt, onState)
            if (retryAt != null) {
                retryAt = null
                persistRetry(attempt, null)
            }
            try {
                return resolve()
            } catch (e: java.io.IOException) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val now = nowMillis()
                val wait = retryPolicy.delayMillis(attempt, nowMillis = now)
                retryAt = if (wait > Long.MAX_VALUE - now) Long.MAX_VALUE else now + wait
                attempt = (attempt + 1).coerceAtMost(30)
                persistRetry(attempt, retryAt)
            }
        }
    }

    private suspend fun awaitReady(retryAt: Long?, onState: suspend (DownloadState) -> Unit) {
        retryAt?.let {
            onState(DownloadState.RETRYING)
            delay((it - nowMillis()).coerceAtLeast(0))
        }
        connectivity.availability().onEach {
            if (!it) onState(DownloadState.WAITING_FOR_NETWORK)
        }.first { it }
        onState(DownloadState.DOWNLOADING)
    }

    suspend fun run(
        partFile: File,
        initialCheckpoint: TransferCheckpoint,
        initialAttempt: Int,
        initialRetryAt: Long?,
        newCall: (String?, String?) -> Call<ResponseBody>,
        persist: suspend (TransferCheckpoint) -> Unit,
        persistRetry: suspend (Int, Long?) -> Unit,
        onState: suspend (DownloadState) -> Unit,
        onProgress: suspend (Long, Long?) -> Unit,
        beforeRead: suspend () -> Unit,
        afterWrite: suspend () -> Unit,
        requireZip: Boolean = false,
        initialRange: Boolean = false
    ): TransferResult {
        var checkpoint = initialCheckpoint
        var attempt = initialAttempt
        var retryAt = initialRetryAt
        var restarted = false
        var progressBaseline = partFile.length()
        while (true) {
            awaitReady(retryAt, onState)
            if (retryAt != null) {
                retryAt = null
                persistRetry(attempt, null)
            }
            val result = monitorAttempt(newCall) { monitoredCall ->
                transfer(
                    partFile, checkpoint, monitoredCall,
                    {
                        persist(it)
                        checkpoint = it
                    },
                    { bytes, total ->
                        if (bytes < progressBaseline) progressBaseline = bytes
                        if (bytes - progressBaseline >= MEANINGFUL_PROGRESS_BYTES) {
                            if (attempt != 0 || retryAt != null) persistRetry(0, null)
                            attempt = 0
                            retryAt = null
                            progressBaseline = bytes
                        }
                        onProgress(bytes, total)
                    },
                    beforeRead,
                    afterWrite,
                    requireZip,
                    initialRange
                )
            }
            when (result) {
                is TransferResult.Verified -> {
                    persistRetry(0, null)
                    return result
                }
                is TransferResult.Permanent -> return result
                is TransferResult.Retryable -> {
                    val now = nowMillis()
                    val wait = retryPolicy.delayMillis(attempt, result.retryAfter, now)
                    retryAt = if (wait > Long.MAX_VALUE - now) Long.MAX_VALUE else now + wait
                    attempt = (attempt + 1).coerceAtMost(30)
                    persistRetry(attempt, retryAt)
                }
                TransferResult.Continue -> retryAt = null
                TransferResult.RestartRequired -> {
                    Logger.info("DownloadTransferRunner", "Reset | resetReason=range-not-satisfiable repeated=$restarted")
                    if (restarted) return TransferResult.Permanent("Repeated invalid range response")
                    restarted = true
                    checkpoint = TransferCheckpoint()
                    persist(checkpoint)
                    java.io.RandomAccessFile(partFile, "rw").use {
                        it.setLength(0)
                        it.fd.sync()
                    }
                    progressBaseline = 0
                    retryAt = null
                }
            }
        }
    }

    private suspend fun monitorAttempt(
        newCall: (String?, String?) -> Call<ResponseBody>,
        attempt: suspend ((String?, String?) -> Call<ResponseBody>) -> TransferResult
    ): TransferResult = coroutineScope {
        val networkLost = AtomicBoolean(false)
        val activeCall = AtomicReference<Call<ResponseBody>?>(null)
        val monitor = launch(start = CoroutineStart.UNDISPATCHED) {
            if (connectivity.availability().firstOrNull { !it } == false) {
                networkLost.set(true)
                activeCall.get()?.cancel()
            }
        }
        try {
            attempt { range, validator ->
                newCall(range, validator).also {
                    activeCall.set(it)
                    if (networkLost.get()) it.cancel()
                }
            }
        } finally {
            monitor.cancel()
        }
    }

    private companion object {
        const val MEANINGFUL_PROGRESS_BYTES = 1024 * 1024L
    }
}
