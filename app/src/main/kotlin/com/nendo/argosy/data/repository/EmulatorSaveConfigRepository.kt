package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.EmulatorSaveConfigDao
import com.nendo.argosy.data.local.entity.EmulatorSaveConfigEntity
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmulatorSaveConfigRepository @Inject constructor(
    private val emulatorSaveConfigDao: EmulatorSaveConfigDao
) {
    suspend fun getByEmulator(emulatorId: String): EmulatorSaveConfigEntity? =
        emulatorSaveConfigDao.getByEmulator(emulatorId)

    suspend fun getAll(): List<EmulatorSaveConfigEntity> =
        emulatorSaveConfigDao.getAll()

    suspend fun setSavePath(emulatorId: String, path: String) {
        val existing = emulatorSaveConfigDao.getByEmulator(emulatorId)
        val base = existing ?: EmulatorSaveConfigEntity(
            emulatorId = emulatorId,
            savePathPattern = path,
            isAutoDetected = false
        )
        emulatorSaveConfigDao.upsert(
            base.copy(
                savePathPattern = path,
                isAutoDetected = false,
                isUserOverride = true,
                lastVerifiedAt = Instant.now()
            )
        )
    }

    suspend fun resetSavePath(emulatorId: String) {
        val existing = emulatorSaveConfigDao.getByEmulator(emulatorId) ?: return
        if (existing.isUserStateOverride && existing.statePathPattern != null) {
            emulatorSaveConfigDao.upsert(
                existing.copy(
                    savePathPattern = "",
                    isAutoDetected = true,
                    isUserOverride = false
                )
            )
        } else {
            emulatorSaveConfigDao.delete(emulatorId)
        }
    }

    suspend fun setStatePath(emulatorId: String, path: String) {
        val existing = emulatorSaveConfigDao.getByEmulator(emulatorId)
        val base = existing ?: EmulatorSaveConfigEntity(
            emulatorId = emulatorId,
            savePathPattern = "",
            isAutoDetected = true
        )
        emulatorSaveConfigDao.upsert(
            base.copy(
                statePathPattern = path,
                isUserStateOverride = true,
                lastVerifiedAt = Instant.now()
            )
        )
    }

    suspend fun resetStatePath(emulatorId: String) {
        val existing = emulatorSaveConfigDao.getByEmulator(emulatorId) ?: return
        val hasSaveOverride = existing.isUserOverride && existing.savePathPattern.isNotEmpty()
        if (hasSaveOverride) {
            emulatorSaveConfigDao.upsert(
                existing.copy(
                    statePathPattern = null,
                    isUserStateOverride = false
                )
            )
        } else {
            emulatorSaveConfigDao.delete(emulatorId)
        }
    }

    suspend fun setMemcardPath(emulatorId: String, cardPath: String) {
        val existing = emulatorSaveConfigDao.getByEmulator(emulatorId)
        val base = existing ?: EmulatorSaveConfigEntity(
            emulatorId = emulatorId,
            savePathPattern = "",
            isAutoDetected = true
        )
        emulatorSaveConfigDao.upsert(
            base.copy(
                selectedMemcardPath = cardPath,
                lastVerifiedAt = Instant.now()
            )
        )
    }

    suspend fun clearMemcardPath(emulatorId: String) {
        val existing = emulatorSaveConfigDao.getByEmulator(emulatorId) ?: return
        val hasOtherState = existing.isUserOverride ||
            existing.isUserStateOverride ||
            existing.statePathPattern != null
        if (hasOtherState) {
            emulatorSaveConfigDao.upsert(existing.copy(selectedMemcardPath = null))
        } else {
            emulatorSaveConfigDao.delete(emulatorId)
        }
    }
}
