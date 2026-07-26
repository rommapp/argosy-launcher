package com.nendo.argosy.data.quaypass

import com.nendo.argosy.data.local.dao.QuayPassPendingReportDao
import com.nendo.argosy.data.quaypass.ble.QuayPassExchangeOrchestrator
import com.nendo.argosy.data.social.ArgosSocialService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drains the durable per-meeting report queue to the server. Every distinct
 * meeting is sent in full (the server dedups per account); the collapsed display
 * ledger is never the report source, so an offline run of several meetings with
 * the same peer is transmitted meeting by meeting. Drains on each new encounter
 * and on reconnect; a send failure stops the drain and it resumes next connect.
 * Reports whose signed meeting time is older than the server's 30-day accept
 * window are dropped rather than sent.
 */
@Singleton
class QuayPassEncounterReporter @Inject constructor(
    private val socialService: ArgosSocialService,
    private val orchestrator: QuayPassExchangeOrchestrator,
    private val pendingReportDao: QuayPassPendingReportDao
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val drainMutex = Mutex()

    init {
        scope.launch {
            orchestrator.newEncounters.collect { drainQueue() }
        }
        scope.launch {
            socialService.connectionState.collect { state ->
                if (state is ArgosSocialService.ConnectionState.Connected) drainQueue()
            }
        }
    }

    private suspend fun drainQueue() = drainMutex.withLock {
        if (!socialService.isConnected()) return@withLock
        val cutoff = Instant.now().minus(MAX_REPORT_AGE).epochSecond
        pendingReportDao.deleteOlderThan(cutoff)
        for (report in pendingReportDao.all()) {
            if (report.tsSecs < cutoff) {
                pendingReportDao.delete(report.id)
                continue
            }
            val sent = socialService.reportQuayPassEncounter(
                peerAccountId = report.peerAccountId,
                credentialBase64 = report.credentialBase64,
                attestationBase64 = report.attestationBase64,
                nonceBase64 = report.nonceBase64,
                cardMessage = report.cardMessage,
                cardIgdbId = report.cardIgdbId,
                cardAvatarPngBase64 = report.cardAvatarPngBase64
            )
            if (!sent) return@withLock
            pendingReportDao.delete(report.id)
            delay(FLUSH_SPACING_MS)
        }
    }

    companion object {
        private val MAX_REPORT_AGE: Duration = Duration.ofDays(30)
        private const val FLUSH_SPACING_MS = 300L
    }
}
