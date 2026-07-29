package com.nendo.argosy.data.repository

import com.nendo.argosy.data.emulator.EmulatorRegistry
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

    /**
     * The save path a user set for [emulatorId], falling back to one they set for a sibling
     * emulator on the same platform.
     *
     * Emulator selection already degrades from a per-game pin to the platform default, so a game
     * pinned to one 3DS build resolves happily while asking for its save path by exact id finds
     * nothing and silently takes the packaged default.
     *
     * Only builds forked from the same base are treated as siblings. Those share a data directory,
     * so an override set on one describes the other. Unrelated emulators on a platform do not: they
     * each keep their own tree, and borrowing a path across them points a game at somewhere its
     * emulator never writes.
     *
     * Returns null when no sibling has one, leaving the packaged default in charge.
     */
    suspend fun resolveUserSavePath(emulatorId: String, platformSlug: String?): String? {
        emulatorSaveConfigDao.getByEmulator(emulatorId)
            ?.takeIf { it.isUserOverride }
            ?.savePathPattern
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        val slug = platformSlug?.takeIf { it.isNotBlank() } ?: return null
        val base = EmulatorRegistry.familyBaseIdFor(emulatorId)
        val siblings = EmulatorRegistry.getForPlatform(slug)
            .map { it.id }
            .filter { it != emulatorId && EmulatorRegistry.familyBaseIdFor(it) == base }
            .toSet()
        if (siblings.isEmpty()) return null

        return emulatorSaveConfigDao.getAll()
            .firstOrNull {
                it.emulatorId in siblings && it.isUserOverride && it.savePathPattern.isNotBlank()
            }
            ?.savePathPattern
    }

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

    suspend fun setSavesBesideRom(emulatorId: String, enabled: Boolean) {
        val existing = emulatorSaveConfigDao.getByEmulator(emulatorId)
        val base = existing ?: EmulatorSaveConfigEntity(
            emulatorId = emulatorId,
            savePathPattern = "",
            isAutoDetected = true
        )
        emulatorSaveConfigDao.upsert(
            base.copy(
                savesBesideRom = enabled,
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
