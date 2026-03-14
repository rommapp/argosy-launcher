package com.nendo.argosy.domain.usecase.achievement

import com.nendo.argosy.data.download.ZipExtractor
import com.nendo.argosy.data.emulator.M3uManager
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.remote.ra.RAConsoleIds
import com.nendo.argosy.data.repository.RetroAchievementsRepository
import com.nendo.argosy.util.Logger
import com.swordfish.libretrodroid.LibretroDroid
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject

private const val TAG = "VerifyRAGameIdUseCase"

class VerifyRAGameIdUseCase @Inject constructor(
    private val gameDao: GameDao,
    private val raRepository: RetroAchievementsRepository
) {
    suspend operator fun invoke(gameId: Long, forceRehash: Boolean = false): Long? {
        val game = gameDao.getById(gameId) ?: return null

        if (game.raIdVerified && !forceRehash) {
            return game.effectiveRaId
        }

        val localPath = game.localPath
        if (localPath == null) {
            gameDao.updateVerifiedRaId(gameId, game.raId)
            return game.raId
        }

        val consoleId = RAConsoleIds.getConsoleId(game.platformSlug)
        if (consoleId == null) {
            gameDao.updateVerifiedRaId(gameId, game.raId)
            return game.raId
        }

        val hashPath = resolveHashPath(localPath, game.platformSlug)
        if (hashPath == null) {
            gameDao.updateVerifiedRaId(gameId, game.raId)
            return game.raId
        }

        val hash = if (!forceRehash && game.romHash != null) {
            game.romHash
        } else {
            computeHash(hashPath, consoleId)?.also { gameDao.updateRomHash(gameId, it) }
        }

        if (hash == null) {
            Logger.warn(TAG, "Failed to compute hash for game $gameId ($localPath)")
            gameDao.updateVerifiedRaId(gameId, game.raId)
            return game.raId
        }

        val resolvedId = try {
            raRepository.resolveGameId(hash)
        } catch (e: Exception) {
            Logger.warn(TAG, "Network error resolving game ID for hash $hash: ${e.message}")
            return game.effectiveRaId
        }

        gameDao.updateVerifiedRaId(gameId, resolvedId)

        if (resolvedId != null && resolvedId != game.raId) {
            Logger.info(TAG, "Verified RA ID differs from RomM: verified=$resolvedId, romm=${game.raId} (game $gameId)")
        }

        return resolvedId
    }

    private fun resolveHashPath(localPath: String, platformSlug: String): String? {
        if (localPath.endsWith(".m3u", ignoreCase = true)) {
            val firstDisc = M3uManager.parseFirstDisc(File(localPath))
            if (firstDisc == null) {
                Logger.warn(TAG, "Could not parse first disc from m3u: $localPath")
                return null
            }
            return firstDisc.absolutePath
        }

        val file = File(localPath)
        if (ZipExtractor.isArchiveFile(file) && !ZipExtractor.usesZipAsRomFormat(platformSlug)) {
            return extractRomFromZipForHash(file)
        }

        return localPath
    }

    private fun extractRomFromZipForHash(zipFile: File): String? {
        return try {
            ZipFile(zipFile).use { zip ->
                val entry = zip.entries().asSequence()
                    .filter { !it.isDirectory }
                    .maxByOrNull { it.size }
                    ?: return null

                val tempFile = File.createTempFile("ra_hash_", "_${entry.name.substringAfterLast('/')}")
                tempFile.deleteOnExit()
                zip.getInputStream(entry).use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
                Logger.info(TAG, "Extracted ${entry.name} from archive for hashing")
                tempFile.absolutePath
            }
        } catch (e: Exception) {
            Logger.warn(TAG, "Failed to extract ROM from archive for hashing: ${e.message}")
            null
        }
    }

    private fun computeHash(path: String, consoleId: Int): String? {
        return try {
            LibretroDroid.computeRomHash(path, consoleId)
        } catch (e: Exception) {
            Logger.warn(TAG, "Hash computation failed for $path: ${e.message}")
            null
        }
    }
}
