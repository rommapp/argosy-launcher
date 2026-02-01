package com.nendo.argosy.data.emulator

import com.nendo.argosy.util.AesXts
import com.nendo.argosy.util.Logger
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

data class TitleIdResult(
    val titleId: String,
    val fromBinary: Boolean
)

@Singleton
class TitleIdExtractor @Inject constructor(
    private val switchKeyManager: SwitchKeyManager
) {
    private val TAG = "TitleIdExtractor"

    fun extractTitleId(romFile: File, platformId: String, emulatorPackage: String? = null): String? {
        return extractTitleIdWithSource(romFile, platformId, emulatorPackage)?.titleId
    }

    fun extractTitleIdWithSource(romFile: File, platformId: String, emulatorPackage: String? = null): TitleIdResult? {
        Logger.debug(TAG, "[SaveSync] DETECT | Extracting title ID from ROM | file=${romFile.name}, platform=$platformId")
        val result = when (platformId) {
            "vita", "psvita" -> extractVitaTitleId(romFile)?.let { TitleIdResult(it, false) }
            "psp" -> extractPSPTitleId(romFile)?.let { TitleIdResult(it, false) }
            "switch" -> extractSwitchTitleIdWithSource(romFile, emulatorPackage)
            "3ds" -> extract3DSTitleId(romFile)?.let { TitleIdResult(it, true) }
            "wiiu" -> extractWiiUTitleId(romFile)?.let { TitleIdResult(it, false) }
            "wii" -> extractWiiTitleId(romFile)?.let { TitleIdResult(it, true) }
            else -> null
        }
        Logger.debug(TAG, "[SaveSync] DETECT | Title ID extraction result | file=${romFile.name}, platform=$platformId, titleId=${result?.titleId}, fromBinary=${result?.fromBinary}")
        return result
    }

    fun extractVitaTitleId(romFile: File): String? {
        val filename = romFile.nameWithoutExtension

        val bracketPattern = Regex("""\[([A-Z]{4}\d{5})\]""")
        bracketPattern.find(filename)?.let { return it.groupValues[1] }

        val prefixPattern = Regex("""^([A-Z]{4}\d{5})""")
        prefixPattern.find(filename)?.let { return it.groupValues[1] }

        if (romFile.extension.equals("zip", ignoreCase = true)) {
            extractTitleIdFromZip(romFile, Regex("""^([A-Z]{4}\d{5})/?"""))?.let { return it }
        }

        return null
    }

    fun extractPSPTitleId(romFile: File): String? {
        val filename = romFile.nameWithoutExtension

        val bracketPattern = Regex("""\[([A-Z]{4}\d{5})\]""")
        bracketPattern.find(filename)?.let { return it.groupValues[1] }

        val parenPattern = Regex("""\(([A-Z]{4}\d{5})\)""")
        parenPattern.find(filename)?.let { return it.groupValues[1] }

        val prefixPattern = Regex("""^([A-Z]{4}\d{5})""")
        prefixPattern.find(filename)?.let { return it.groupValues[1] }

        return null
    }

    fun extractSwitchTitleId(romFile: File, emulatorPackage: String? = null): String? {
        return extractSwitchTitleIdWithSource(romFile, emulatorPackage)?.titleId
    }

    fun extractSwitchTitleIdWithSource(romFile: File, emulatorPackage: String? = null): TitleIdResult? {
        val ext = romFile.extension.lowercase()

        // Try binary extraction first (high confidence, locked)
        when (ext) {
            "nsp" -> extractSwitchTitleIdFromNSP(romFile)?.let {
                Logger.debug(TAG, "[SaveSync] DETECT | Switch title ID from NSP binary | file=${romFile.name}, titleId=$it")
                return TitleIdResult(it, fromBinary = true)
            }
            "xci" -> {
                val prodKeysPath = emulatorPackage?.let { switchKeyManager.findProdKeysPath(it) }
                extractSwitchTitleIdFromXCI(romFile, prodKeysPath)?.let {
                    Logger.debug(TAG, "[SaveSync] DETECT | Switch title ID from XCI binary | file=${romFile.name}, titleId=$it")
                    return TitleIdResult(it, fromBinary = true)
                }
            }
        }

        // Fallback to filename patterns (lower confidence, not locked)
        val filename = romFile.nameWithoutExtension

        // Pattern: [0100F2C0115B6000] - 16 hex characters
        val bracketPattern = Regex("""\[([0-9A-Fa-f]{16})\]""")
        bracketPattern.find(filename)?.let { return TitleIdResult(it.groupValues[1].uppercase(), fromBinary = false) }

        // Some files use parentheses
        val parenPattern = Regex("""\(([0-9A-Fa-f]{16})\)""")
        parenPattern.find(filename)?.let { return TitleIdResult(it.groupValues[1].uppercase(), fromBinary = false) }

        // Title ID at end after dash/underscore
        val suffixPattern = Regex("""[-_]([0-9A-Fa-f]{16})$""")
        suffixPattern.find(filename)?.let { return TitleIdResult(it.groupValues[1].uppercase(), fromBinary = false) }

        return null
    }

    private fun extractSwitchTitleIdFromNSP(romFile: File): String? {
        return try {
            RandomAccessFile(romFile, "r").use { raf ->
                if (raf.length() < 0x100) return null

                // Verify PFS0 magic
                val magic = ByteArray(4)
                raf.readFully(magic)
                if (String(magic) != "PFS0") {
                    Logger.debug(TAG, "[SaveSync] DETECT | NSP missing PFS0 magic | file=${romFile.name}")
                    return null
                }

                // Read header info (little-endian)
                val fileCount = readLittleEndianInt(raf)
                val stringTableSize = readLittleEndianInt(raf)
                raf.skipBytes(4) // reserved

                // Skip file entries (24 bytes each)
                val stringTableOffset = 0x10 + (fileCount * 24)
                raf.seek(stringTableOffset.toLong())

                // Read string table and find NCA filename with title ID
                val readSize = minOf(stringTableSize, 0x1000)
                val stringTable = ByteArray(readSize)
                raf.readFully(stringTable)
                val tableStr = String(stringTable, Charsets.US_ASCII)

                // NCA filenames contain title ID: "0100f2c0115b6000.nca"
                val ncaPattern = Regex("([0-9a-fA-F]{16})\\.nca")
                ncaPattern.find(tableStr)?.groupValues?.get(1)?.uppercase()
                    ?.takeIf { it.startsWith("01") }
            }
        } catch (e: Exception) {
            Logger.warn(TAG, "[SaveSync] DETECT | Failed to parse NSP | file=${romFile.name}", e)
            null
        }
    }

    private fun extractSwitchTitleIdFromXCI(romFile: File, prodKeysPath: String?): String? {
        if (prodKeysPath == null) {
            Logger.debug(TAG, "[SaveSync] DETECT | No prod.keys for XCI decryption | file=${romFile.name}")
            return null
        }
        val headerKey = switchKeyManager.getHeaderKey(prodKeysPath) ?: return null

        return try {
            RandomAccessFile(romFile, "r").use { raf ->
                if (raf.length() < 0x200) return null

                // Read encrypted CardHeader at 0x100 (one sector)
                raf.seek(0x100)
                val encryptedHeader = ByteArray(0x100)
                raf.readFully(encryptedHeader)

                // Decrypt with AES-XTS (sector 0)
                val decryptedHeader = AesXts.decrypt(encryptedHeader, headerKey, 0, 0x200)

                // Verify "HEAD" magic at start of decrypted data
                if (String(decryptedHeader, 0, 4) != "HEAD") {
                    Logger.debug(TAG, "[SaveSync] DETECT | XCI decryption failed (no HEAD magic) | file=${romFile.name}")
                    return null
                }

                // PackageId (title ID) at offset 0x10 in decrypted header (8 bytes LE)
                val titleIdBytes = decryptedHeader.copyOfRange(0x10, 0x18)
                val titleId = titleIdBytes.reversed().joinToString("") { "%02X".format(it) }

                titleId.takeIf { it.startsWith("01") && it.length == 16 }
            }
        } catch (e: Exception) {
            Logger.warn(TAG, "[SaveSync] DETECT | Failed to parse XCI | file=${romFile.name}", e)
            null
        }
    }

    private fun readLittleEndianInt(raf: RandomAccessFile): Int {
        val bytes = ByteArray(4)
        raf.readFully(bytes)
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int
    }

    fun extract3DSTitleId(romFile: File): String? {
        val ext = romFile.extension.lowercase()

        // .3ds and .cci files both use NCSD format with Program ID at same offset
        if (ext == "3ds" || ext == "cci") {
            extract3DSTitleIdFromBinary(romFile)?.let { return it }
        }

        val filename = romFile.nameWithoutExtension

        // Full title ID pattern: [00040000001B5000] - 16 hex characters
        val fullPattern = Regex("""\[([0-9A-Fa-f]{16})\]""")
        fullPattern.find(filename)?.let {
            val fullId = it.groupValues[1].uppercase()
            Logger.debug(TAG, "[SaveSync] DETECT | 3DS title ID from filename (full) | file=${romFile.name}, titleId=$fullId")
            return fullId
        }

        // Short pattern: [001B5000] - 8 hex characters (title_id_low only)
        val shortPattern = Regex("""\[([0-9A-Fa-f]{8})\]""")
        shortPattern.find(filename)?.let {
            val shortId = it.groupValues[1].uppercase()
            Logger.debug(TAG, "[SaveSync] DETECT | 3DS title ID from filename (short) | file=${romFile.name}, titleId=$shortId")
            return shortId
        }

        // Parentheses variant
        val parenPattern = Regex("""\(([0-9A-Fa-f]{8,16})\)""")
        parenPattern.find(filename)?.let {
            val id = it.groupValues[1]
            val result = if (id.length == 16) id.uppercase() else id.uppercase()
            Logger.debug(TAG, "[SaveSync] DETECT | 3DS title ID from filename (paren) | file=${romFile.name}, titleId=$result")
            return result
        }

        return null
    }

    private fun extract3DSTitleIdFromBinary(romFile: File): String? {
        // .3ds files (NCSD format): NCCH partition at 0x4000, Program ID at offset 0x118
        // Absolute offset: 0x4000 + 0x118 = 0x4118
        val ncchOffset = 0x4000L
        val programIdOffset = 0x118L
        val absoluteOffset = ncchOffset + programIdOffset

        return try {
            RandomAccessFile(romFile, "r").use { raf ->
                if (raf.length() < absoluteOffset + 8) {
                    Logger.debug(TAG, "[SaveSync] DETECT | 3DS file too small for binary extraction | file=${romFile.name}, size=${raf.length()}")
                    return null
                }

                raf.seek(absoluteOffset)
                val bytes = ByteArray(8)
                raf.readFully(bytes)

                // Little-endian: reverse bytes to get proper hex string
                val titleId = bytes.reversed().joinToString("") { "%02X".format(it) }

                if (!isValid3DSTitleId(titleId)) {
                    Logger.debug(TAG, "[SaveSync] DETECT | 3DS binary title ID invalid | file=${romFile.name}, raw=$titleId")
                    return null
                }

                Logger.debug(TAG, "[SaveSync] DETECT | 3DS title ID from binary | file=${romFile.name}, titleId=$titleId")
                titleId
            }
        } catch (e: Exception) {
            Logger.warn(TAG, "[SaveSync] DETECT | Failed to read 3DS binary | file=${romFile.name}", e)
            null
        }
    }

    private fun isValid3DSTitleId(titleId: String): Boolean {
        if (titleId.length != 16) return false
        if (!titleId.all { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }) return false
        if (!titleId.uppercase().startsWith("0004")) return false
        return true
    }

    fun extractWiiUTitleId(romFile: File): String? {
        val filename = romFile.nameWithoutExtension

        // Pattern: [10118300] - 8 hex characters
        val bracketPattern = Regex("""\[([0-9A-Fa-f]{8})\]""")
        bracketPattern.find(filename)?.let { return it.groupValues[1].uppercase() }

        // Parentheses variant
        val parenPattern = Regex("""\(([0-9A-Fa-f]{8})\)""")
        parenPattern.find(filename)?.let { return it.groupValues[1].uppercase() }

        return null
    }

    fun extractWiiTitleId(romFile: File): String? {
        val gameInfo = GameCubeHeaderParser.parseRomHeader(romFile)
        if (gameInfo == null) {
            Logger.debug(TAG, "[SaveSync] DETECT | Failed to parse Wii ROM header | file=${romFile.name}")
            return null
        }
        val hexId = GameCubeHeaderParser.gameIdToHex(gameInfo.gameId)
        Logger.debug(TAG, "[SaveSync] DETECT | Wii game ID converted to hex | gameId=${gameInfo.gameId}, hexId=$hexId")
        return hexId
    }

    private fun extractTitleIdFromZip(zipFile: File, pattern: Regex): String? {
        return try {
            ZipFile(zipFile).use { zip ->
                zip.entries().asSequence()
                    .mapNotNull { entry ->
                        pattern.find(entry.name)?.groupValues?.get(1)
                    }
                    .firstOrNull()
            }
        } catch (e: Exception) {
            Logger.warn(TAG, "[SaveSync] DETECT | Failed to read zip for title ID | file=${zipFile.name}", e)
            null
        }
    }
}
