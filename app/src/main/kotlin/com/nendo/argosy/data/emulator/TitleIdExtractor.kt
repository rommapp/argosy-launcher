package com.nendo.argosy.data.emulator

import com.nendo.argosy.util.Logger
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TitleIdExtractor @Inject constructor() {

    private val TAG = "TitleIdExtractor"

    fun extractTitleId(romFile: File, platformId: String): String? {
        Logger.debug(TAG, "[SaveSync] DETECT | Extracting title ID from ROM | file=${romFile.name}, platform=$platformId")
        val result = when (platformId) {
            "vita", "psvita" -> extractVitaTitleId(romFile)
            "psp" -> extractPSPTitleId(romFile)
            "switch" -> extractSwitchTitleId(romFile)
            "3ds" -> extract3DSTitleId(romFile)
            "wiiu" -> extractWiiUTitleId(romFile)
            else -> null
        }
        Logger.debug(TAG, "[SaveSync] DETECT | Title ID extraction result | file=${romFile.name}, platform=$platformId, titleId=$result")
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

    fun extractSwitchTitleId(romFile: File): String? {
        val filename = romFile.nameWithoutExtension

        // Pattern: [0100F2C0115B6000] - 16 hex characters
        val bracketPattern = Regex("""\[([0-9A-Fa-f]{16})\]""")
        bracketPattern.find(filename)?.let { return it.groupValues[1].uppercase() }

        // Some files use parentheses
        val parenPattern = Regex("""\(([0-9A-Fa-f]{16})\)""")
        parenPattern.find(filename)?.let { return it.groupValues[1].uppercase() }

        // Title ID at end after dash/underscore
        val suffixPattern = Regex("""[-_]([0-9A-Fa-f]{16})$""")
        suffixPattern.find(filename)?.let { return it.groupValues[1].uppercase() }

        return null
    }

    fun extract3DSTitleId(romFile: File): String? {
        val filename = romFile.nameWithoutExtension

        // Full title ID pattern: [00040000001B5000] - 16 hex characters
        val fullPattern = Regex("""\[([0-9A-Fa-f]{16})\]""")
        fullPattern.find(filename)?.let {
            // Return just the lower 8 characters (title_id_low)
            return it.groupValues[1].takeLast(8).uppercase()
        }

        // Short pattern: [001B5000] - 8 hex characters (title_id_low only)
        val shortPattern = Regex("""\[([0-9A-Fa-f]{8})\]""")
        shortPattern.find(filename)?.let { return it.groupValues[1].uppercase() }

        // Parentheses variant
        val parenPattern = Regex("""\(([0-9A-Fa-f]{8,16})\)""")
        parenPattern.find(filename)?.let {
            val id = it.groupValues[1]
            return if (id.length == 16) id.takeLast(8).uppercase() else id.uppercase()
        }

        return null
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
