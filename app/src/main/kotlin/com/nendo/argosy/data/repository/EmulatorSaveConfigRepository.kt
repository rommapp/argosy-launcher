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
     * Only builds forked from the same base are treated as siblings, matched on an id prefix.
     * Note what that does and does not mean: it groups `ppsspp` with `ppsspp_gold`, and `citra`
     * with `citra_mmj`, which are separate packages with separate data directories. The
     * justification is not a shared directory, it is that a user who pointed one build of an
     * emulator somewhere meant that place for that emulator, and forks are close enough that the
     * guess beats the packaged default. Unrelated emulators are not: borrowing across them points
     * a game at somewhere its emulator never writes.
     *
     * Returns null when no sibling has one, leaving the packaged default in charge.
     *
     * The fallback runs only for a genuine emulator id. A platform-qualified save-config id such
     * as `dolphin_wii` names a layout rather than an installed app, and feeding one to
     * sibling-family logic resolves it to the base emulator, which is how a GameCube override
     * became the Wii save base (#380). Siblings are other builds of the same emulator, never other
     * platforms of the same build.
     */
    suspend fun resolveUserSavePath(emulatorId: String, platformSlug: String?): String? {
        emulatorSaveConfigDao.getByEmulator(emulatorId)
            ?.takeIf { it.isUserOverride }
            ?.savePathPattern
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        if (EmulatorRegistry.getById(emulatorId) == null) return null

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

    /**
     * The packaged folder Argosy settled on for [emulatorId] when no user choice exists. Written
     * once by [setEvaluatedSavePath] and cleared only by [resetSavePath], so the answer holds
     * across sessions instead of following whichever folder happens to be readable today.
     */
    suspend fun resolveEvaluatedSavePath(emulatorId: String): String? =
        emulatorSaveConfigDao.getByEmulator(emulatorId)
            ?.takeIf { it.isAutoDetected && !it.isUserOverride }
            ?.savePathPattern
            ?.takeIf { it.isNotBlank() }

    /**
     * The base folder every save-path consumer should use: the user's choice first, then the
     * evaluated default. Null means the packaged candidates still decide.
     */
    suspend fun resolveEffectiveSavePath(emulatorId: String, platformSlug: String?): String? =
        resolveUserSavePath(emulatorId, platformSlug) ?: resolveEvaluatedSavePath(emulatorId)

    suspend fun setEvaluatedSavePath(emulatorId: String, path: String) {
        val existing = emulatorSaveConfigDao.getByEmulator(emulatorId)
        if (existing?.isUserOverride == true) return
        val base = existing ?: EmulatorSaveConfigEntity(
            emulatorId = emulatorId,
            savePathPattern = path,
            isAutoDetected = true
        )
        emulatorSaveConfigDao.upsert(
            base.copy(
                savePathPattern = path,
                isAutoDetected = true,
                isUserOverride = false,
                lastVerifiedAt = Instant.now()
            )
        )
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
        val hasSavePath = existing.savePathPattern.isNotEmpty()
        if (hasSavePath) {
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
            existing.savePathPattern.isNotEmpty() ||
            existing.isUserStateOverride ||
            existing.statePathPattern != null
        if (hasOtherState) {
            emulatorSaveConfigDao.upsert(existing.copy(selectedMemcardPath = null))
        } else {
            emulatorSaveConfigDao.delete(emulatorId)
        }
    }
}
