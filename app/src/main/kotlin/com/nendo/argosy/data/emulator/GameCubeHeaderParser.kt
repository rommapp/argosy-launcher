package com.nendo.argosy.data.emulator

import java.io.File
import java.io.RandomAccessFile

data class GameCubeGameInfo(
    val gameId: String,
    val makerCode: String,
    val region: String,
    val gameName: String?
)

data class GciSaveInfo(
    val gameId: String,
    val makerCode: String,
    val internalFilename: String,
    val region: String
)

object GameCubeHeaderParser {

    private const val RVZ_MAGIC = "RVZ"
    private const val RVZ_GAME_ID_OFFSET = 0x58L
    private const val RVZ_GAME_NAME_OFFSET = 0x78L
    private const val ISO_GAME_ID_OFFSET = 0x00L
    private const val ISO_GAME_NAME_OFFSET = 0x20L

    private const val GCI_GAME_ID_OFFSET = 0x00L
    private const val GCI_MAKER_CODE_OFFSET = 0x04L
    private const val GCI_INTERNAL_FILENAME_OFFSET = 0x08L
    private const val GCI_INTERNAL_FILENAME_LENGTH = 32

    fun parseRomHeader(file: File): GameCubeGameInfo? {
        if (!file.exists()) return null

        return try {
            RandomAccessFile(file, "r").use { raf ->
                // Check if RVZ format
                val magic = ByteArray(3)
                raf.read(magic)
                val isRvz = String(magic) == RVZ_MAGIC

                val gameIdOffset = if (isRvz) RVZ_GAME_ID_OFFSET else ISO_GAME_ID_OFFSET
                val gameNameOffset = if (isRvz) RVZ_GAME_NAME_OFFSET else ISO_GAME_NAME_OFFSET

                // Read game ID (4 bytes) and maker code (2 bytes)
                raf.seek(gameIdOffset)
                val gameIdBytes = ByteArray(4)
                val makerBytes = ByteArray(2)
                raf.read(gameIdBytes)
                raf.read(makerBytes)

                val gameId = String(gameIdBytes).trim()
                val makerCode = String(makerBytes).trim()

                // Read game name (up to 64 bytes, null-terminated)
                raf.seek(gameNameOffset)
                val nameBytes = ByteArray(64)
                raf.read(nameBytes)
                val gameName = String(nameBytes).substringBefore('\u0000').trim()

                // Determine region from last character of game ID
                val region = when (gameId.lastOrNull()) {
                    'E' -> "USA"
                    'P' -> "EUR"
                    'J' -> "JAP"
                    'K' -> "KOR"
                    else -> "USA" // Default to USA
                }

                GameCubeGameInfo(
                    gameId = gameId,
                    makerCode = makerCode,
                    region = region,
                    gameName = gameName.takeIf { it.isNotEmpty() }
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    fun parseGciHeader(file: File): GciSaveInfo? {
        if (!file.exists() || file.length() < 0x40) return null

        return try {
            RandomAccessFile(file, "r").use { raf ->
                // Read game ID (4 bytes)
                raf.seek(GCI_GAME_ID_OFFSET)
                val gameIdBytes = ByteArray(4)
                raf.read(gameIdBytes)
                val gameId = String(gameIdBytes).trim()

                // Read maker code (2 bytes)
                raf.seek(GCI_MAKER_CODE_OFFSET)
                val makerBytes = ByteArray(2)
                raf.read(makerBytes)
                val makerCode = String(makerBytes).trim()

                // Read internal filename (32 bytes, null-terminated)
                raf.seek(GCI_INTERNAL_FILENAME_OFFSET)
                val filenameBytes = ByteArray(GCI_INTERNAL_FILENAME_LENGTH)
                raf.read(filenameBytes)
                val internalFilename = String(filenameBytes).substringBefore('\u0000').trim()

                // Determine region from game ID
                val region = when (gameId.lastOrNull()) {
                    'E' -> "USA"
                    'P' -> "EUR"
                    'J' -> "JAP"
                    'K' -> "KOR"
                    else -> "USA"
                }

                GciSaveInfo(
                    gameId = gameId,
                    makerCode = makerCode,
                    internalFilename = internalFilename,
                    region = region
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    fun buildGciFilename(gameId: String, makerCode: String, internalFilename: String): String {
        return "${gameId}${makerCode}_${internalFilename}.gci"
    }

    fun buildGciPath(baseDir: String, region: String, gciFilename: String): String {
        return "$baseDir/$region/$gciFilename"
    }

    fun gameIdToHex(gameId: String): String {
        return gameId.toByteArray(Charsets.US_ASCII)
            .joinToString("") { "%02X".format(it) }
    }

    fun findGciForGame(saveDir: File, gameId: String): List<File> {
        val regions = listOf("USA", "EUR", "JAP", "KOR")
        val results = mutableListOf<File>()

        fun searchDirectory(dir: File) {
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.extension.equals("gci", ignoreCase = true) && !file.name.contains(".deleted")) {
                    if (file.name.contains(gameId, ignoreCase = true)) {
                        results.add(file)
                    } else {
                        val gciInfo = parseGciHeader(file)
                        if (gciInfo?.gameId.equals(gameId, ignoreCase = true)) {
                            results.add(file)
                        }
                    }
                }
            }
        }

        // Search the provided directory directly (for user overrides that point to card folder)
        searchDirectory(saveDir)
        if (results.isNotEmpty()) return results

        // Search standard Dolphin structure: {saveDir}/{region}/[Card A|B/]
        for (region in regions) {
            val regionDir = File(saveDir, region)
            if (!regionDir.exists()) continue

            val dirsToSearch = mutableListOf(regionDir)
            regionDir.listFiles()?.filter { it.isDirectory && it.name.startsWith("Card", ignoreCase = true) }
                ?.let { dirsToSearch.addAll(it) }

            for (dir in dirsToSearch) {
                searchDirectory(dir)
            }
        }

        return results
    }
}
