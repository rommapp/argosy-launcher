package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StateSerialResolver @Inject constructor(
    private val gameDao: GameDao,
    private val gameDiscDao: GameDiscDao,
    private val titleIdExtractor: TitleIdExtractor
) {
    private val reads = ConcurrentHashMap<FileIdentity, CachedRead>()

    suspend fun serialsFor(gameId: Long, emulatorId: String): Set<String> {
        if (StatePathRegistry.serialPatternFor(emulatorId) == null) return emptySet()
        return withContext(Dispatchers.IO) { trustedSerials(gameId) }
    }

    private suspend fun trustedSerials(gameId: Long): Set<String> {
        val game = gameDao.getById(gameId) ?: return emptySet()
        val discPaths = gameDiscDao.getDiscsForGame(gameId).mapNotNull { it.localPath }
        val serials = if (discPaths.isNotEmpty()) discSerials(game, discPaths) else romSerial(game)
        if (serials.isEmpty()) {
            Logger.info(TAG, "No disc serial read for gameId=$gameId; its states are left untouched")
        }
        return serials
    }

    private fun discSerials(game: GameEntity, discPaths: List<String>): Set<String> =
        discPaths.mapNotNull { binarySerial(File(it), game.platformSlug) }.toSet()

    private suspend fun romSerial(game: GameEntity): Set<String> {
        val romPath = game.localPath ?: return emptySet()
        val result = binaryRead(File(romPath), game.platformSlug) ?: return emptySet()
        if (!game.titleIdLocked || !result.titleId.equals(game.titleId, ignoreCase = true)) {
            val saveId = game.saveId?.takeIf { it.isNotBlank() } ?: result.saveId
            gameDao.setTitleAndSaveIdWithLock(game.id, result.titleId, saveId, locked = true)
        }
        return setOf(result.titleId)
    }

    private fun binarySerial(file: File, platformSlug: String): String? =
        binaryRead(file, platformSlug)?.titleId

    private fun binaryRead(file: File, platformSlug: String): TitleIdResult? {
        if (!file.isFile) return null
        val identity = FileIdentity(file.absolutePath, platformSlug, file.length(), file.lastModified())
        reads[identity]?.let { return it.result }
        val result = try {
            titleIdExtractor.extractTitleIdWithSource(file, platformSlug)
        } catch (e: Exception) {
            Logger.warn(TAG, "Sigil could not read ${file.name}", e)
            return null
        }?.takeIf { it.fromBinary && it.titleId.isNotBlank() }
        reads.keys.removeAll { it.path == identity.path && it.platformSlug == platformSlug }
        reads[identity] = CachedRead(result)
        return result
    }

    private data class FileIdentity(val path: String, val platformSlug: String, val length: Long, val modified: Long)

    private class CachedRead(val result: TitleIdResult?)

    private companion object {
        const val TAG = "StateSerialResolver"
    }
}
