package com.nendo.argosy.data.emulator

import android.util.Log
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.entity.GameDiscEntity
import com.nendo.argosy.data.local.entity.GameEntity
import java.io.File
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "M3uManager"

sealed class M3uResult {
    data class Valid(val m3uFile: File) : M3uResult()
    data class Generated(val m3uFile: File) : M3uResult()
    data class NotApplicable(val reason: String) : M3uResult()
    data class Error(val message: String) : M3uResult()
}

@Singleton
class M3uManager @Inject constructor(
    private val gameDao: GameDao,
    private val gameDiscDao: GameDiscDao
) {
    companion object {
        private val SUPPORTED_PLATFORMS = setOf("psx", "saturn", "dreamcast", "dc")

        fun supportsM3u(platformSlug: String): Boolean = platformSlug in SUPPORTED_PLATFORMS

        fun parseFirstDisc(m3uFile: File): File? {
            return parseAllDiscs(m3uFile).firstOrNull()
        }

        fun parseAllDiscs(m3uFile: File): List<File> {
            if (!m3uFile.exists() || m3uFile.extension.lowercase() != "m3u") return emptyList()
            val parentDir = m3uFile.parentFile ?: return emptyList()

            return parseM3uLines(m3uFile).mapNotNull { line ->
                val discFile = File(parentDir, line)
                if (discFile.exists()) discFile else null
            }
        }

        fun isM3uComplete(m3uFile: File): Boolean {
            if (!m3uFile.exists() || m3uFile.extension.lowercase() != "m3u") return false
            val parentDir = m3uFile.parentFile ?: return false

            val lines = parseM3uLines(m3uFile)
            if (lines.isEmpty()) return false

            return lines.all { line -> File(parentDir, line).exists() }
        }

        private fun parseM3uLines(m3uFile: File): List<String> {
            return try {
                m3uFile.readLines()
                    .filter { it.isNotBlank() && !it.startsWith("#") }
                    .map { line ->
                        val commentIndex = line.indexOf('#')
                        if (commentIndex > 0) line.substring(0, commentIndex).trim()
                        else line.trim()
                    }
                    .filter { it.isNotEmpty() }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse m3u: ${e.message}")
                emptyList()
            }
        }

        fun isCueComplete(cueFile: File): Boolean {
            if (!cueFile.exists() || cueFile.extension.lowercase() != "cue") return false
            val parentDir = cueFile.parentFile ?: return false

            val referencedFiles = parseCueFiles(cueFile)
            if (referencedFiles.isEmpty()) return false

            return referencedFiles.all { fileName -> File(parentDir, fileName).exists() }
        }

        private fun parseCueFiles(cueFile: File): List<String> {
            return try {
                cueFile.readLines()
                    .filter { it.trim().uppercase().startsWith("FILE ") }
                    .mapNotNull { line ->
                        Regex("FILE\\s+\"([^\"]+)\"", RegexOption.IGNORE_CASE)
                            .find(line)?.groupValues?.getOrNull(1)
                    }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse cue: ${e.message}")
                emptyList()
            }
        }
    }

    suspend fun ensureM3u(game: GameEntity): M3uResult {
        if (!game.isMultiDisc) {
            return M3uResult.NotApplicable("Not a multi-disc game")
        }

        if (!supportsM3u(game.platformSlug)) {
            return M3uResult.NotApplicable("Platform ${game.platformSlug} does not support m3u")
        }

        val discs = gameDiscDao.getDiscsForGame(game.id)
        if (discs.isEmpty()) {
            return M3uResult.Error("No discs found for game")
        }

        val discsWithPaths = discs.filter { it.localPath != null }
        if (discsWithPaths.size != discs.size) {
            return M3uResult.NotApplicable("Not all discs downloaded yet")
        }

        val existingM3u = game.m3uPath?.let { File(it) }
        if (existingM3u != null && validateM3u(existingM3u, discsWithPaths)) {
            Log.d(TAG, "Existing m3u is valid: ${existingM3u.absolutePath}")
            return M3uResult.Valid(existingM3u)
        }

        return generateM3u(game, discsWithPaths)
    }

    suspend fun generateM3uIfComplete(gameId: Long): M3uResult {
        val game = gameDao.getById(gameId)
            ?: return M3uResult.Error("Game not found")

        if (!game.isMultiDisc || !supportsM3u(game.platformSlug)) {
            return M3uResult.NotApplicable("Not applicable for this game")
        }

        val totalDiscs = gameDiscDao.getTotalDiscCount(gameId)
        val downloadedDiscs = gameDiscDao.getDownloadedDiscCount(gameId)

        if (downloadedDiscs < totalDiscs) {
            return M3uResult.NotApplicable("Only $downloadedDiscs of $totalDiscs discs downloaded")
        }

        val discs = gameDiscDao.getDiscsForGame(gameId)
        return generateM3u(game, discs)
    }

    private suspend fun generateM3u(game: GameEntity, discs: List<GameDiscEntity>): M3uResult {
        val firstDiscPath = discs.firstOrNull { it.localPath != null }?.localPath
            ?: return M3uResult.Error("No disc paths available")

        val parentDir = File(firstDiscPath).parentFile
            ?: return M3uResult.Error("Could not determine parent directory")

        val m3uFileName = sanitizeFileName(game.title) + ".m3u"
        val m3uFile = File(parentDir, m3uFileName)

        val content = discs
            .filter { it.localPath != null }
            .sortedBy { it.discNumber }
            .joinToString("\n") { File(it.localPath!!).name }

        return try {
            m3uFile.writeText(content, Charset.forName("US-ASCII"))
            gameDao.updateM3uPath(game.id, m3uFile.absolutePath)
            Log.d(TAG, "Generated m3u: ${m3uFile.absolutePath}")
            M3uResult.Generated(m3uFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate m3u", e)
            M3uResult.Error("Failed to write m3u: ${e.message}")
        }
    }

    private fun validateM3u(m3uFile: File, discs: List<GameDiscEntity>): Boolean {
        if (!m3uFile.exists()) {
            Log.d(TAG, "M3u file does not exist: ${m3uFile.absolutePath}")
            return false
        }

        val m3uDir = m3uFile.parentFile ?: return false
        val lines = parseM3uLines(m3uFile)

        if (lines.size != discs.size) {
            Log.d(TAG, "M3u line count (${lines.size}) doesn't match disc count (${discs.size})")
            return false
        }

        for (line in lines) {
            val referencedFile = File(m3uDir, line)
            if (!referencedFile.exists()) {
                Log.d(TAG, "Referenced file does not exist: ${referencedFile.absolutePath}")
                return false
            }
        }

        return true
    }

    private fun sanitizeFileName(name: String): String {
        return name
            .replace(Regex("[<>:\"/\\\\|?*]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
