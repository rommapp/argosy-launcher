package com.nendo.argosy.data.emulator.ini

import com.nendo.argosy.util.Logger
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Robust, non-destructive INI configuration manager.
 *
 * Designed to safely read, query, and modify section-based INI configuration files
 * without destroying user comments, trailing whitespace, or formatting, and preventing
 * section bleed or duplicate section injection.
 */
@Singleton
class IniFileManager @Inject constructor() {

    /**
     * Reads a single setting value from an INI formatted string.
     * Returns null if the section or key does not exist.
     */
    fun get(content: String, section: String, key: String): String? {
        val lines = content.lines()
        var inTargetSection = false

        for (line in lines) {
            val sectionMatch = SECTION_HEADER_REGEX.matchEntire(line)
            if (sectionMatch != null) {
                val currentSection = sectionMatch.groupValues[1].trim()
                inTargetSection = currentSection.equals(section.trim(), ignoreCase = true)
                continue
            }

            if (inTargetSection) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith(";") || trimmed.startsWith("#")) {
                    continue
                }
                val eqIndex = line.indexOf('=')
                if (eqIndex != -1) {
                    val currentKey = line.substring(0, eqIndex).trim()
                    if (currentKey.equals(key.trim(), ignoreCase = true)) {
                        val rawValue = line.substring(eqIndex + 1)
                        return extractValue(rawValue)
                    }
                }
            }
        }
        return null
    }

    /**
     * Reads a single setting value from an INI file.
     * Returns null if the file does not exist or the setting is not found.
     */
    fun get(file: File, section: String, key: String): String? {
        if (!file.exists() || !file.isFile) return null
        return try {
            val text = file.readText(Charsets.UTF_8)
            get(text, section, key)
        } catch (e: Exception) {
            Logger.warn(TAG, "Failed to read INI file ${file.absolutePath}: ${e.message}")
            null
        }
    }

    /**
     * Retrieves all key-value pairs for a given section from an INI formatted string.
     * Returns null if the section does not exist.
     */
    fun getSection(content: String, section: String): Map<String, String>? {
        val lines = content.lines()
        var inTargetSection = false
        var sectionFound = false
        val map = linkedMapOf<String, String>()

        for (line in lines) {
            val sectionMatch = SECTION_HEADER_REGEX.matchEntire(line)
            if (sectionMatch != null) {
                val currentSection = sectionMatch.groupValues[1].trim()
                if (currentSection.equals(section.trim(), ignoreCase = true)) {
                    inTargetSection = true
                    sectionFound = true
                } else if (inTargetSection) {
                    // Encountered a new section header, stop scanning
                    break
                }
                continue
            }

            if (inTargetSection) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith(";") || trimmed.startsWith("#")) {
                    continue
                }
                val eqIndex = line.indexOf('=')
                if (eqIndex != -1) {
                    val k = line.substring(0, eqIndex).trim()
                    val v = extractValue(line.substring(eqIndex + 1))
                    map[k] = v
                }
            }
        }
        return if (sectionFound) map else null
    }

    /**
     * Retrieves all key-value pairs for a given section from an INI file.
     * Returns null if the file or section does not exist.
     */
    fun getSection(file: File, section: String): Map<String, String>? {
        if (!file.exists() || !file.isFile) return null
        return try {
            val text = file.readText(Charsets.UTF_8)
            getSection(text, section)
        } catch (e: Exception) {
            Logger.warn(TAG, "Failed to read INI file section ${file.absolutePath}: ${e.message}")
            null
        }
    }

    /**
     * Updates or inserts a key-value setting in [content] under [section].
     *
     * Non-destructive:
     * - Preserves section comments, trailing comments, and indentation.
     * - Preserves existing inline comments on the modified line.
     * - Deduplicates existing duplicate keys in the same section.
     * - Appends cleanly to the end of the section without disturbing subsequent sections.
     * - Preserves CRLF or LF line endings.
     */
    fun updateSetting(
        content: String,
        section: String,
        key: String,
        value: String
    ): String {
        val targetSection = section.trim()
        val targetKey = key.trim()
        val lineSeparator = if (content.contains("\r\n")) "\r\n" else "\n"
        val hasTrailingNewline = content.endsWith("\n") || content.endsWith("\r\n")

        val lines = if (content.isEmpty()) mutableListOf() else content.lines().toMutableList()
        var targetSectionStart = -1
        var targetSectionEnd = -1
        val matchingKeyIndices = mutableListOf<Int>()
        var inTargetSection = false

        for (i in lines.indices) {
            val line = lines[i]
            val sectionMatch = SECTION_HEADER_REGEX.matchEntire(line)
            if (sectionMatch != null) {
                val currentSection = sectionMatch.groupValues[1].trim()
                if (inTargetSection) {
                    targetSectionEnd = i
                    inTargetSection = false
                }
                if (currentSection.equals(targetSection, ignoreCase = true)) {
                    inTargetSection = true
                    targetSectionStart = i
                }
                continue
            }

            if (inTargetSection) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith(";") || trimmed.startsWith("#")) {
                    continue
                }
                val eqIndex = line.indexOf('=')
                if (eqIndex != -1) {
                    val k = line.substring(0, eqIndex).trim()
                    if (k.equals(targetKey, ignoreCase = true)) {
                        matchingKeyIndices.add(i)
                    }
                }
            }
        }

        if (inTargetSection && targetSectionEnd == -1) {
            targetSectionEnd = lines.size
        }

        when {
            matchingKeyIndices.isNotEmpty() -> {
                // Update the first occurrence while preserving indentation and inline comments
                val firstIdx = matchingKeyIndices.first()
                lines[firstIdx] = formatUpdatedLine(lines[firstIdx], targetKey, value)

                // Remove duplicate keys in reverse order
                for (dupIdx in matchingKeyIndices.drop(1).asReversed()) {
                    lines.removeAt(dupIdx)
                }
            }
            targetSectionStart != -1 -> {
                // Insert at the end of the existing section (before trailing blank lines or next section)
                val insertIdx = findSectionInsertionIndex(lines, targetSectionStart, targetSectionEnd)
                lines.add(insertIdx, "$targetKey = $value")
            }
            else -> {
                // Section does not exist: append new section at end of file
                while (lines.isNotEmpty() && lines.last().isBlank()) {
                    lines.removeAt(lines.lastIndex)
                }
                if (lines.isNotEmpty()) {
                    lines.add("")
                }
                lines.add("[$targetSection]")
                lines.add("$targetKey = $value")
            }
        }

        val joined = lines.joinToString(lineSeparator)
        return if (hasTrailingNewline || lines.isNotEmpty()) {
            if (joined.endsWith(lineSeparator)) joined else joined + lineSeparator
        } else {
            joined
        }
    }

    /**
     * Updates multiple settings sequentially within [section].
     */
    fun updateSettings(
        content: String,
        section: String,
        entries: Map<String, String>
    ): String {
        var result = content
        entries.forEach { (k, v) ->
            result = updateSetting(result, section, k, v)
        }
        return result
    }

    /**
     * Updates or inserts a key-value setting in [file] under [section].
     * Writes changes atomically using a temporary file.
     */
    fun writeSetting(
        file: File,
        section: String,
        key: String,
        value: String
    ): Boolean {
        val existingContent = if (file.exists() && file.isFile) {
            try {
                file.readText(Charsets.UTF_8)
            } catch (e: Exception) {
                Logger.warn(TAG, "Failed to read ${file.absolutePath}: ${e.message}")
                ""
            }
        } else ""

        val updated = updateSetting(existingContent, section, key, value)
        return writeAtomically(file, updated)
    }

    /**
     * Updates multiple settings in [file] under [section].
     * Writes changes atomically.
     */
    fun writeSettings(
        file: File,
        section: String,
        entries: Map<String, String>
    ): Boolean {
        val existingContent = if (file.exists() && file.isFile) {
            try {
                file.readText(Charsets.UTF_8)
            } catch (e: Exception) {
                Logger.warn(TAG, "Failed to read ${file.absolutePath}: ${e.message}")
                ""
            }
        } else ""

        val updated = updateSettings(existingContent, section, entries)
        return writeAtomically(file, updated)
    }

    /**
     * Atomically writes [content] to [targetFile] using a temporary file and rename.
     * Uses explicit UTF-8 encoding.
     */
    fun writeAtomically(targetFile: File, content: String): Boolean {
        return try {
            val parent = targetFile.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            val tempFile = File(parent ?: File("."), "${targetFile.name}.${UUID.randomUUID()}.tmp")
            try {
                tempFile.writeText(content, Charsets.UTF_8)
                try {
                    Files.move(
                        tempFile.toPath(),
                        targetFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                    )
                } catch (e: Exception) {
                    try {
                        Files.move(
                            tempFile.toPath(),
                            targetFile.toPath(),
                            StandardCopyOption.REPLACE_EXISTING
                        )
                    } catch (e2: Exception) {
                        tempFile.copyTo(targetFile, overwrite = true)
                    }
                }
                true
            } finally {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
        } catch (e: Exception) {
            Logger.warn(TAG, "Failed to atomically write INI file ${targetFile.absolutePath}: ${e.message}")
            false
        }
    }

    private fun extractValue(rawValue: String): String {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) return ""

        // If quoted value, strip quotes and ignore comments outside quotes
        if (trimmed.startsWith("\"")) {
            val endQuote = trimmed.indexOf('"', startIndex = 1)
            if (endQuote != -1) {
                return trimmed.substring(1, endQuote)
            }
        }

        // Look for inline comment delimiter ; or # preceded by whitespace or at start
        var inQuote = false
        var commentIndex = -1
        for (i in trimmed.indices) {
            val c = trimmed[i]
            if (c == '"') {
                inQuote = !inQuote
            } else if (!inQuote && (c == ';' || c == '#')) {
                commentIndex = i
                break
            }
        }

        val actualVal = if (commentIndex != -1) {
            trimmed.substring(0, commentIndex).trim()
        } else {
            trimmed
        }

        return if (actualVal.startsWith("\"") && actualVal.endsWith("\"") && actualVal.length >= 2) {
            actualVal.substring(1, actualVal.length - 1)
        } else {
            actualVal
        }
    }

    private fun formatUpdatedLine(originalLine: String, key: String, newValue: String): String {
        val eqIndex = originalLine.indexOf('=')
        if (eqIndex == -1) {
            return "$key = $newValue"
        }

        val leadingIndent = originalLine.takeWhile { it.isWhitespace() }
        val rawValue = originalLine.substring(eqIndex + 1)

        // Find inline comment if present
        var inQuote = false
        var commentIndex = -1
        for (i in rawValue.indices) {
            val c = rawValue[i]
            if (c == '"') {
                inQuote = !inQuote
            } else if (!inQuote && (c == ';' || c == '#')) {
                commentIndex = i
                break
            }
        }

        val inlineComment = if (commentIndex != -1) {
            val commentSub = rawValue.substring(commentIndex)
            val precedingSpaces = rawValue.substring(0, commentIndex).takeLastWhile { it.isWhitespace() }
            if (precedingSpaces.isNotEmpty()) "$precedingSpaces$commentSub" else " $commentSub"
        } else {
            ""
        }

        return "${leadingIndent}$key = $newValue$inlineComment"
    }

    private fun findSectionInsertionIndex(
        lines: List<String>,
        sectionStart: Int,
        sectionEnd: Int
    ): Int {
        var idx = sectionEnd
        while (idx > sectionStart + 1 && lines[idx - 1].isBlank()) {
            idx--
        }
        return idx
    }

    companion object {
        private const val TAG = "IniFileManager"

        /**
         * Matches section headers like:
         * `[EmuCore/GS]`
         * `[EmuCore/GS] ; Graphics settings`
         * `[EmuCore/GS] # comment`
         * `  [EmuCore/GS]  `
         */
        val SECTION_HEADER_REGEX = Regex("""^\s*\[([^\]]+)\](?:\s*[;#].*)?$""")
    }
}
