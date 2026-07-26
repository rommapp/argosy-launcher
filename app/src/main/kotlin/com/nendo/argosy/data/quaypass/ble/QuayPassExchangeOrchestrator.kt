package com.nendo.argosy.data.quaypass.ble

import android.bluetooth.BluetoothDevice
import android.util.Log
import com.nendo.argosy.data.quaypass.QuayPassRasterPng
import com.nendo.argosy.data.local.dao.QuayPassDailyStatsDao
import com.nendo.argosy.data.local.dao.QuayPassEncounterDao
import com.nendo.argosy.data.local.dao.QuayPassPendingReportDao
import com.nendo.argosy.data.local.entity.QuayPassEncounterEntity
import com.nendo.argosy.data.local.entity.QuayPassPendingReportEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.quaypass.QuayPassCredentialManager
import com.nendo.argosy.data.quaypass.QuayPassKeystore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A verified meeting ready to record: the peer's profile plus the attestation it
 * signed, our per-meeting nonce, and the peer's signed time.
 */
data class RecordedMeeting(
    val profile: InboundProfile,
    val attestation: ByteArray,
    val nonce: ByteArray,
    val tsSecs: Long
)

@Singleton
class QuayPassExchangeOrchestrator @Inject constructor(
    private val keystore: QuayPassKeystore,
    private val credentialManager: QuayPassCredentialManager,
    private val encounterDao: QuayPassEncounterDao,
    private val dailyStatsDao: QuayPassDailyStatsDao,
    private val pendingReportDao: QuayPassPendingReportDao,
    private val userPreferencesRepository: UserPreferencesRepository
) {

    private val _newEncounters = MutableSharedFlow<QuayPassEncounterEntity>(extraBufferCapacity = 16)
    val newEncounters: SharedFlow<QuayPassEncounterEntity> = _newEncounters.asSharedFlow()

    private data class ServerPending(val peerProfile: InboundProfile, val ourNonce: ByteArray)
    private val serverPending = ConcurrentHashMap<String, ServerPending>()

    suspend fun buildOurWireBytes(profile: OutboundProfile): ByteArray? {
        val credential = credentialManager.getValidCredential() ?: return null
        return try {
            QuayPassWireFormat.encode(
                profile = profile,
                credentialBytesBase64 = credential.bytesBase64,
                signer = { keystore.sign(it) }
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to encode our wire bytes", t)
            null
        }
    }

    fun signAttestationFor(peerAccountId: UUID, peerChallenge: ByteArray, tsSecs: Long): ByteArray =
        keystore.signServerVerifiable(QuayPassAttestation.preimage(peerAccountId, peerChallenge, tsSecs))

    /**
     * Central role: send our profile with a fresh challenge, verify the peer's
     * attestation on the read, sign our own for the second write, and record.
     */
    suspend fun handleClient(
        device: BluetoothDevice,
        gattClient: QuayPassGattClient,
        ourEnvelope: ByteArray
    ): Boolean {
        val ourNonce = QuayPassExchangeFrames.newChallenge()
        val write1 = QuayPassExchangeFrames.profileWrite(ourEnvelope, ourNonce)
        var peer: InboundProfile? = null
        val response = gattClient.exchange(device, write1) { resp ->
            val b = (QuayPassWireFormat.decode(resp.envelope) as? DecodeResult.Success)?.profile
                ?: return@exchange null
            peer = b
            val tsA = Instant.now().epochSecond
            val attA = signAttestationFor(b.credentialBundle.accountId, resp.challenge, tsA)
            QuayPassExchangeFrames.attestationWrite(attA, tsA)
        } ?: return false
        val peerProfile = peer ?: return false
        return record(RecordedMeeting(peerProfile, response.attestation, ourNonce, response.tsSecs))
    }

    /**
     * Peripheral role, first write: verify the peer's profile, mint our
     * attestation of meeting them, and return the read response the central reads.
     * Null rejects the exchange (bad profile, self, or not signed in).
     */
    suspend fun onServerProfileWrite(
        deviceKey: String,
        ourEnvelope: ByteArray,
        frame: QuayPassExchangeFrames.ProfileWrite
    ): ByteArray? {
        val peer = (QuayPassWireFormat.decode(frame.envelope) as? DecodeResult.Success)?.profile
            ?: return null
        val ourUuid = ourAccountUuid() ?: return null
        if (peer.credentialBundle.accountId == ourUuid) return null
        val ourNonce = QuayPassExchangeFrames.newChallenge()
        val tsB = Instant.now().epochSecond
        val attB = signAttestationFor(peer.credentialBundle.accountId, frame.challenge, tsB)
        serverPending[deviceKey] = ServerPending(peer, ourNonce)
        return QuayPassExchangeFrames.readResponse(ourEnvelope, attB, tsB, ourNonce)
    }

    /**
     * Peripheral role, second write: the central's attestation of meeting us,
     * verified and recorded.
     */
    suspend fun onServerAttestationWrite(
        deviceKey: String,
        frame: QuayPassExchangeFrames.AttestationWrite
    ) {
        val pending = serverPending.remove(deviceKey) ?: return
        record(RecordedMeeting(pending.peerProfile, frame.attestation, pending.ourNonce, frame.tsSecs))
    }

    fun onServerDeviceGone(deviceKey: String) {
        serverPending.remove(deviceKey)
    }

    suspend fun record(meeting: RecordedMeeting, now: Instant = Instant.now()): Boolean {
        val profile = meeting.profile
        val ourUuid = ourAccountUuid() ?: return false
        if (profile.credentialBundle.accountId == ourUuid) {
            Log.v(TAG, "Skipping self encounter")
            return false
        }
        if (!QuayPassAttestation.verify(
                profile.credentialBundle, ourUuid, meeting.nonce, meeting.tsSecs, meeting.attestation
            )
        ) {
            Log.v(TAG, "Attestation verification failed")
            return false
        }

        val avatarPng = QuayPassRasterPng.fromRasterBytes(profile.avatarBytes.takeIf { it.isNotEmpty() })
        val entity = QuayPassEncounterEntity(
            credentialFingerprint = profile.credentialFingerprint,
            username = profile.username,
            displayName = profile.displayName,
            avatarColor = null,
            avatarBlobBase64 = avatarPng,
            greeting = profile.greeting,
            lastGameTitle = profile.lastGameTitle,
            lastGamePlatform = profile.lastGamePlatform,
            lastGamePlaytimeMinutes = profile.lastGamePlaytimeMinutes,
            lastGameIgdbId = profile.lastGameIgdbId,
            encounteredAt = now,
            seenByUser = false,
            accountId = profile.credentialBundle.accountId.toString(),
            reported = false
        )

        val cooldownCutoff = now.minusSeconds(QuayPassConfig.EXCHANGE_COOLDOWN_SECS)
        if (!encounterDao.claimEncounter(entity, cooldownCutoff)) {
            return false
        }

        pendingReportDao.enqueue(
            QuayPassPendingReportEntity(
                peerAccountId = profile.credentialBundle.accountId.toString(),
                credentialBase64 = profile.credentialBytesBase64,
                attestationBase64 = base64(meeting.attestation),
                nonceBase64 = base64(meeting.nonce),
                tsSecs = meeting.tsSecs,
                cardMessage = profile.greeting,
                cardIgdbId = profile.lastGameIgdbId,
                cardAvatarPngBase64 = avatarPng
            )
        )

        val dateKey = LocalDate.now(ZoneId.systemDefault()).toString()
        dailyStatsDao.creditDay(dateKey, tickets = 1)
        _newEncounters.tryEmit(entity)
        return true
    }

    private suspend fun ourAccountUuid(): UUID? =
        userPreferencesRepository.userPreferences.first().socialUserId
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    private fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    companion object {
        private const val TAG = "QuayPassOrchestrator"
    }
}
