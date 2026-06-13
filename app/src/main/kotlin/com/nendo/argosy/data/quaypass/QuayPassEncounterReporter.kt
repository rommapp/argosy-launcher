package com.nendo.argosy.data.quaypass

import com.nendo.argosy.data.local.dao.QuayPassEncounterDao
import com.nendo.argosy.data.local.entity.QuayPassEncounterEntity
import com.nendo.argosy.data.quaypass.ble.QuayPassExchangeOrchestrator
import com.nendo.argosy.data.social.ArgosSocialService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
        encounterDao.unreported().forEach { report(it) }
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
}
