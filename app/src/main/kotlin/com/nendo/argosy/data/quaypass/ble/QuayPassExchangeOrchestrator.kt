package com.nendo.argosy.data.quaypass.ble

import android.bluetooth.BluetoothDevice
import android.util.Log
import com.nendo.argosy.data.quaypass.QuayPassRasterPng
import com.nendo.argosy.data.local.dao.QuayPassDailyStatsDao
import com.nendo.argosy.data.local.dao.QuayPassEncounterDao
import com.nendo.argosy.data.local.entity.QuayPassDailyStatsEntity
import com.nendo.argosy.data.local.entity.QuayPassEncounterEntity
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuayPassExchangeOrchestrator @Inject constructor(
    private val keystore: QuayPassKeystore,
    private val credentialManager: QuayPassCredentialManager,
    private val nonceStore: QuayPassNonceStore,
    private val cooldownStore: QuayPassCooldownStore,
    private val encounterDao: QuayPassEncounterDao,
    private val dailyStatsDao: QuayPassDailyStatsDao,
    private val userPreferencesRepository: UserPreferencesRepository
) {

    private val _newEncounters = MutableSharedFlow<QuayPassEncounterEntity>(extraBufferCapacity = 16)
    val newEncounters: SharedFlow<QuayPassEncounterEntity> = _newEncounters.asSharedFlow()

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

    suspend fun handleClient(
        device: BluetoothDevice,
        gattClient: QuayPassGattClient,
        ourBytes: ByteArray
    ): Boolean {
        val theirBytes = gattClient.exchangeProfiles(device, ourBytes) ?: return false
        return processInbound(theirBytes)
    }

    suspend fun processInbound(bytes: ByteArray, now: Instant = Instant.now()): Boolean {
        val result = QuayPassWireFormat.decode(bytes, now)
        if (result is DecodeResult.Failure) {
            Log.v(TAG, "Decode failure: ${result.reason}")
            return false
        }
        return record((result as DecodeResult.Success).profile, now)
    }

    suspend fun record(profile: InboundProfile, now: Instant = Instant.now()): Boolean {
        val ourAccountId = userPreferencesRepository.userPreferences.first().socialUserId
        if (ourAccountId != null &&
            profile.credentialBundle.accountId.toString().equals(ourAccountId, ignoreCase = true)
        ) {
            Log.v(TAG, "Skipping self encounter (same account across two devices)")
            return false
        }

        val nowSecs = now.epochSecond

        if (!cooldownStore.claim(profile.credentialFingerprint, nowSecs)) {
            return false
        }
        if (!nonceStore.acceptOrReject(profile.credentialFingerprint, profile.nonce, nowSecs)) {
            return false
        }

        val entity = QuayPassEncounterEntity(
            credentialFingerprint = profile.credentialFingerprint,
            username = profile.username,
            displayName = profile.displayName,
            avatarColor = null,
            avatarBlobBase64 = QuayPassRasterPng.fromRasterBytes(
                profile.avatarBytes.takeIf { it.isNotEmpty() }
            ),
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
        encounterDao.upsert(entity)

        val dateKey = LocalDate.now(ZoneId.systemDefault()).toString()
        val updated = dailyStatsDao.incrementForDate(dateKey, tickets = 1)
        if (updated == 0) {
            dailyStatsDao.upsert(QuayPassDailyStatsEntity(date = dateKey, encounterCount = 1, ticketsEarned = 1))
        }

        _newEncounters.tryEmit(entity)
        return true
    }

    companion object {
        private const val TAG = "QuayPassOrchestrator"
    }
}
