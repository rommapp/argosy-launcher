package com.nendo.argosy.data.quaypass

import com.nendo.argosy.data.local.dao.QuayPassEncounterDao
import com.nendo.argosy.data.local.entity.QuayPassEncounterEntity
import com.nendo.argosy.data.quaypass.ble.QuayPassExchangeOrchestrator
import com.nendo.argosy.data.social.ArgosSocialService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reports encounters to the server so the account earns tickets. Reports inline
 * when connected and flushes any still-unreported encounters on reconnect; the
 * server dedups per account-pair so resends are harmless.
 */
@Singleton
class QuayPassEncounterReporter @Inject constructor(
    private val socialService: ArgosSocialService,
    private val orchestrator: QuayPassExchangeOrchestrator,
    private val encounterDao: QuayPassEncounterDao
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            orchestrator.newEncounters.collect { report(it) }
        }
        scope.launch {
            socialService.connectionState.collect { state ->
                if (state is ArgosSocialService.ConnectionState.Connected) flushUnreported()
            }
        }
    }

    private suspend fun flushUnreported() {
        val cutoff = Instant.now().minus(MAX_REPORT_AGE)
        encounterDao.unreported().forEach { encounter ->
            if (encounter.encounteredAt.isBefore(cutoff)) {
                encounterDao.markReported(encounter.credentialFingerprint)
            } else {
                report(encounter)
                delay(FLUSH_SPACING_MS)
            }
        }
    }

    private fun report(encounter: QuayPassEncounterEntity) {
        val accountId = encounter.accountId ?: return
        val sent = socialService.reportQuayPassEncounter(
            peerAccountId = accountId,
            fingerprint = encounter.credentialFingerprint,
            encounteredAtEpoch = encounter.encounteredAt.epochSecond
        )
        if (sent) {
            scope.launch { encounterDao.markReported(encounter.credentialFingerprint) }
        }
    }

    companion object {
        private val MAX_REPORT_AGE: Duration = Duration.ofDays(30)
        private const val FLUSH_SPACING_MS = 300L
    }
}
