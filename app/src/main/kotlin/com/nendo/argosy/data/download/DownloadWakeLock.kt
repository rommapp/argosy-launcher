package com.nendo.argosy.data.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class DownloadWakeLock(
    private val scope: CoroutineScope,
    private val renewMs: Long,
    private val acquire: () -> Unit,
    private val release: () -> Unit
) {
    private var renewalJob: Job? = null

    fun update(states: List<DownloadState>, mediaBusy: Boolean) {
        val needsWakeLock = mediaBusy || states.any {
            it == DownloadState.DOWNLOADING || it == DownloadState.EXTRACTING ||
                it == DownloadState.MOVING
        }
        if (!needsWakeLock) {
            stop()
            return
        }
        if (renewalJob != null || !scope.isActive) return
        renewalJob = scope.launch(start = CoroutineStart.LAZY) {
            try {
                while (true) {
                    acquire()
                    delay(renewMs)
                    release()
                }
            } finally {
                if (renewalJob == currentCoroutineContext()[Job]) {
                    renewalJob = null
                    release()
                }
            }
        }
        renewalJob?.start()
    }

    fun stop() {
        val job = renewalJob
        renewalJob = null
        job?.cancel()
        release()
    }
}
