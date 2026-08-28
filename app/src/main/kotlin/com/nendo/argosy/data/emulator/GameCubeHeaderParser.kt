package com.nendo.argosy.data.emulator

import com.nendo.argosy.util.FileNames
import com.nendo.argosy.util.Logger
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

    private const val TAG = "GameCubeHeaderParser"

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

    /**
     * A GCI id is four printable ASCII characters and a maker code is two. Reading fixed offsets
     * out of a file that is not a GCI yields whatever bytes happen to sit there, which is how an
     * srm payload once got written into a GameCube card directory under a garbage name.
     */
    private fun looksLikeGciField(value: String, length: Int): Boolean =
        value.length == length && value.all { it.code in 0x21..0x7E }

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

                if (!looksLikeGciField(gameId, 4) || !looksLikeGciField(makerCode, 2)) {
                    Logger.warn(
                        TAG,
                        "Not a GCI: header bytes are not a game id and maker code | file=${file.name}"
                    )
                    return@use null
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

    /**
     * The internal filename is 32 raw bytes off the GCI header, so it is whatever the file
     * carries rather than a name a volume will accept. Folding it keeps a separator in those
     * bytes from steering the built path out of the card directory, and it is also the only
     * form the storage layer would have accepted anyway.
     */
    fun buildGciFilename(makerCode: String, gameId: String, internalFilename: String): String {
        return "$makerCode-$gameId-${FileNames.sanitize(internalFilename)}.gci"
    }

    fun buildGciPath(baseDir: String, region: String, gciFilename: String): String {
        return "$baseDir/$region/Card A/$gciFilename"
    }

    private val VALID_REGIONS = listOf("USA", "EUR", "JAP", "KOR")

    fun isValidGciPath(path: String): Boolean {
        if (!path.endsWith(".gci", ignoreCase = true)) return false
        return VALID_REGIONS.any { region -> path.contains("/$region/Card A/") }
    }

}
