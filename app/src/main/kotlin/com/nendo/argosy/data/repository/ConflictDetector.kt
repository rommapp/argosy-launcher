package com.nendo.argosy.data.repository

import com.nendo.argosy.data.remote.romm.RomMSave
import com.nendo.argosy.util.Logger
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConflictDetector @Inject constructor() {

    private val sessionOnOlderSave = mutableMapOf<Long, Boolean>()

    fun setSessionOnOlderSave(gameId: Long, isOlder: Boolean) {
        sessionOnOlderSave[gameId] = isOlder
    }

    fun clearSessionOnOlderSave(gameId: Long) {
        sessionOnOlderSave.remove(gameId)
    }

    fun isSessionOnOlderSave(gameId: Long): Boolean =
        sessionOnOlderSave[gameId] ?: false

    fun determineSyncStatus(
        localTime: Instant?,
        serverTime: Instant
    ): String {
        if (localTime == null) return com.nendo.argosy.data.local.entity.SaveSyncEntity.STATUS_SERVER_NEWER
        return when {
            serverTime.isAfter(localTime) -> com.nendo.argosy.data.local.entity.SaveSyncEntity.STATUS_SERVER_NEWER
            localTime.isAfter(serverTime) -> com.nendo.argosy.data.local.entity.SaveSyncEntity.STATUS_LOCAL_NEWER
            else -> com.nendo.argosy.data.local.entity.SaveSyncEntity.STATUS_SYNCED
        }
    }

    fun extractUploaderDeviceName(save: RomMSave?, currentDeviceId: String?): String? {
        val syncs = save?.deviceSyncs ?: return null
        return syncs
            .filter { it.deviceId != currentDeviceId }
            .maxByOrNull { it.lastSyncedAt ?: "" }
            ?.deviceName
    }

    fun pickLatestServerSave(
        serverSaves: List<RomMSave>,
        channelName: String?,
        romBaseName: String?,
        isGciBundle: Boolean
    ): RomMSave? {
        return if (channelName != null) {
            serverSaves
                .filter { it.slot != null && SaveSyncApiClient.equalsNormalized(it.slot, channelName) }
                .maxByOrNull { SaveSyncApiClient.parseTimestamp(it.updatedAt) }
                ?: serverSaves.find {
                    SaveSyncApiClient.equalsNormalized(File(it.fileName).nameWithoutExtension, channelName)
                }
        } else {
            val candidates = serverSaves.filter { SaveSyncApiClient.isLatestSaveFileName(it.fileName, romBaseName) }
            val pickedByName = if (isGciBundle && candidates.size > 1) {
                candidates.find { it.fileName.endsWith(".zip", ignoreCase = true) }
                    ?: candidates.firstOrNull()
            } else {
                candidates.firstOrNull()
            }
            pickedByName ?: serverSaves.singleOrNull()?.also { lone ->
                Logger.warn(TAG, "[SaveSync] No filename match for romBaseName='$romBaseName'; accepting lone server save fileName='${lone.fileName}'")
            }
        }
    }

    fun pickExistingServerSave(
        serverSaves: List<RomMSave>,
        channelName: String?,
        romBaseName: String?,
        isGciBundle: Boolean
    ): RomMSave? {
        val candidates = serverSaves.filter { serverSave ->
            val baseName = File(serverSave.fileName).nameWithoutExtension
            if (channelName != null) {
                SaveSyncApiClient.equalsNormalized(baseName, channelName)
            } else {
                baseName.equals(SaveSyncApiClient.DEFAULT_SAVE_NAME, ignoreCase = true) ||
                    romBaseName != null && SaveSyncApiClient.equalsNormalized(baseName, romBaseName)
            }
        }
        return if (isGciBundle && candidates.size > 1) {
            candidates.find { it.fileName.endsWith(".zip", ignoreCase = true) }
                ?: candidates.firstOrNull()
        } else {
            candidates.firstOrNull()
        }
    }

    data class UploadConflictDecision(
        val isConflict: Boolean,
        val localTimestamp: Instant,
        val serverTimestamp: Instant,
        val serverDeviceName: String?
    )

    fun detectUploadConflict(
        gameId: Long,
        channelName: String?,
        forceOverwrite: Boolean,
        currentDeviceId: String?,
        latestServerSave: RomMSave?,
        localModified: Instant,
        preSyncTimeIfSession: Instant?
    ): UploadConflictDecision? {
        if (forceOverwrite) {
            Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Skipping conflict check (force overwrite)")
            return null
        }

        if (channelName != null && currentDeviceId != null && isSessionOnOlderSave(gameId)) {
            Logger.warn(TAG, "[SaveSync] UPLOAD gameId=$gameId | Session started on older save -- conflict for channel=$channelName")
            val serverTime = latestServerSave?.let { SaveSyncApiClient.parseTimestamp(it.updatedAt) } ?: Instant.now()
            val preSync = preSyncTimeIfSession ?: localModified
            return UploadConflictDecision(
                isConflict = true,
                localTimestamp = preSync,
                serverTimestamp = serverTime,
                serverDeviceName = extractUploaderDeviceName(latestServerSave, currentDeviceId)
            )
        }

        if (channelName == null && latestServerSave != null) {
            val serverTime = SaveSyncApiClient.parseTimestamp(latestServerSave.updatedAt)
            val deltaMs = serverTime.toEpochMilli() - localModified.toEpochMilli()
            val deltaStr = if (deltaMs >= 0) "+${deltaMs / 1000}s" else "${deltaMs / 1000}s"
            Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Conflict check | local=$localModified, server=$serverTime, delta=$deltaStr")
            if (serverTime.isAfter(localModified)) {
                Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Decision=CONFLICT | Server is newer, blocking upload")
                return UploadConflictDecision(
                    isConflict = true,
                    localTimestamp = localModified,
                    serverTimestamp = serverTime,
                    serverDeviceName = extractUploaderDeviceName(latestServerSave, currentDeviceId)
                )
            }
            Logger.debug(TAG, "[SaveSync] UPLOAD gameId=$gameId | Decision=PROCEED | Local is newer or equal")
        }
        return null
    }

    companion object {
        private const val TAG = "ConflictDetector"
    }
}
