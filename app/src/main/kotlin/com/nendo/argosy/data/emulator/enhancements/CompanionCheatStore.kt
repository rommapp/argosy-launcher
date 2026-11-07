package com.nendo.argosy.data.emulator.enhancements

import com.nendo.argosy.data.local.dao.CheatDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.entity.CheatEntity
import com.nendo.argosy.data.model.VariantCategory
import com.nendo.argosy.util.Logger
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

data class ImportedCheat(
    val name: String,
    val code: String,
    val enabled: Boolean
)

data class CompanionText(
    val name: String,
    val text: String
)

/**
 * Cheat rows imported from the files a server ships in a game's mods or cheats folder, loose or
 * inside a pack. A cheat already present by name is not added again.
 */
@Singleton
class CompanionCheatStore @Inject constructor(
    private val cheatDao: CheatDao,
    private val gameFileDao: GameFileDao
) {
    suspend fun companionFiles(gameId: Long): List<File> =
        gameFileDao.getFilesForGame(gameId)
            .filter { VariantCategory.fromKey(it.category) in COMPANION_CATEGORIES }
            .mapNotNull { file -> file.localPath?.let(::File)?.takeIf { it.isFile } }

    fun readText(files: List<File>, extension: String): List<CompanionText> =
        files.flatMap { file ->
            when (file.extension.lowercase(Locale.ROOT)) {
                extension -> readLoose(file)
                ARCHIVE_EXTENSION -> readArchive(file, extension)
                else -> emptyList()
            }
        }

    fun readArchive(archive: File, extension: String): List<CompanionText> =
        try {
            ZipFile(archive).use { zip ->
                zip.entries().asSequence()
                    .filter { !it.isDirectory && it.size in 0..MAX_COMPANION_BYTES }
                    .filter { it.name.substringAfterLast('.', "").equals(extension, ignoreCase = true) }
                    .map { entry ->
                        val name = entry.name.replace('\\', '/').substringAfterLast('/')
                        CompanionText(name, zip.getInputStream(entry).use { it.readBytes() }.toString(Charsets.UTF_8))
                    }
                    .toList()
            }
        } catch (e: Exception) {
            Logger.warn(TAG, "Could not read ${archive.name}: ${e.message}")
            emptyList()
        }

    suspend fun add(gameId: Long, cheats: List<ImportedCheat>) {
        if (cheats.isEmpty()) return
        val known = cheatDao.getCheatsForGame(gameId).map { it.description }.toMutableSet()
        var nextIndex = (cheatDao.getMaxCheatIndex(gameId) ?: -1) + 1
        val fresh = cheats.filter { known.add(it.name) }.map { cheat ->
            CheatEntity(
                gameId = gameId,
                cheatIndex = nextIndex++,
                description = cheat.name,
                code = cheat.code,
                enabled = cheat.enabled,
                isUserCreated = true
            )
        }
        if (fresh.isNotEmpty()) {
            cheatDao.insertAll(fresh)
            Logger.info(TAG, "Imported ${fresh.size} companion cheats for game $gameId")
        }
    }

    private fun readLoose(file: File): List<CompanionText> {
        if (file.length() > MAX_COMPANION_BYTES) return emptyList()
        return try {
            listOf(CompanionText(file.name, file.readText(Charsets.UTF_8)))
        } catch (e: Exception) {
            Logger.warn(TAG, "Could not read ${file.name}: ${e.message}")
            emptyList()
        }
    }

    private companion object {
        const val TAG = "CompanionCheatStore"
        const val ARCHIVE_EXTENSION = "zip"
        const val MAX_COMPANION_BYTES = 4L * 1024 * 1024
        val COMPANION_CATEGORIES = setOf(VariantCategory.MOD, VariantCategory.CHEAT)
    }
}
