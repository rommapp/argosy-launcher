package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.data.preferences.RegionFilterMode
import com.nendo.argosy.data.preferences.SyncFilterPreferences
import com.nendo.argosy.util.Logger

object RomMSyncFilter {

    private const val TAG = "RomMSyncFilter"

    private val NO_INTRO_HACK_REGEX = Regex("\\[h[0-9a-z ]*\\]")
    private val HACK_BRACKET_REGEX = Regex("\\[.*\\bhack\\b.*\\]")
    private val HACK_PAREN_REGEX = Regex("\\(.*\\bhack\\b.*\\)")
    private val BAD_DUMP_REGEX = Regex("\\[[bopBOP][0-9]*\\]")

    fun shouldSyncRom(rom: RomMRom, filters: SyncFilterPreferences): Boolean {
        val extension = extractExtension(rom)
        if (!passesExtensionFilter(rom, extension)) {
            Logger.debug(TAG, "Filtered by extension: ${rom.name} (ext: $extension, platform: ${rom.platformSlug})")
            return false
        }
        if (!passesBadDumpFilter(rom)) {
            Logger.debug(TAG, "Filtered as bad dump: ${rom.name} (file: ${rom.fileName})")
            return false
        }
        if (!passesRegionFilter(rom, filters)) {
            Logger.debug(TAG, "Filtered by region: ${rom.name} (regions: ${rom.regions})")
            return false
        }
        if (!passesRevisionFilter(rom, filters)) {
            Logger.debug(TAG, "Filtered by revision: ${rom.name} (revision: ${rom.revision}, tags: ${rom.tags})")
            return false
        }
        return true
    }

    private fun passesExtensionFilter(rom: RomMRom, extension: String?): Boolean {
        if (extension == null) return true

        val platformDef = PlatformDefinitions.getBySlug(rom.platformSlug) ?: return true
        if (platformDef.extensions.isEmpty()) return true

        return extension in platformDef.extensions
    }

    private fun extractExtension(rom: RomMRom): String? {
        // Try files list first - only root-level files (skip subdirs like updates/, dlc/)
        rom.files
            ?.filter { !it.filePath.contains('/') }
            ?.firstOrNull()
            ?.fileName
            ?.let { fileName ->
                extractValidExtension(fileName)?.let { return it }
            }

        // Try full path
        rom.filePath?.let { path ->
            extractValidExtension(path)?.let { return it }
        }

        // Fall back to fs_name
        rom.fileName?.let { fileName ->
            extractValidExtension(fileName)?.let { return it }
        }

        return null
    }

    private fun extractValidExtension(name: String): String? {
        val ext = name.substringAfterLast('.', "").lowercase()
        // Valid extension: non-empty, short, no spaces, alphanumeric
        return if (ext.isNotEmpty() && ext.length <= 10 && !ext.contains(' ') && ext.all { it.isLetterOrDigit() }) {
            ext
        } else {
            null
        }
    }

    private fun passesBadDumpFilter(rom: RomMRom): Boolean {
        val name = rom.name.lowercase()
        val fileName = rom.fileName?.lowercase() ?: ""
        if (BAD_DUMP_REGEX.containsMatchIn(name) || BAD_DUMP_REGEX.containsMatchIn(fileName)) return false
        return true
    }

    private fun passesRegionFilter(rom: RomMRom, filters: SyncFilterPreferences): Boolean {
        if (filters.enabledRegions.isEmpty()) return true

        val romRegions = rom.regions
        if (romRegions.isNullOrEmpty()) return true

        val matchesEnabled = romRegions.any { region ->
            filters.enabledRegions.any { enabled ->
                region.equals(enabled, ignoreCase = true)
            }
        }

        return when (filters.regionMode) {
            RegionFilterMode.INCLUDE -> matchesEnabled
            RegionFilterMode.EXCLUDE -> !matchesEnabled
        }
    }

    private fun passesRevisionFilter(rom: RomMRom, filters: SyncFilterPreferences): Boolean {
        val revision = rom.revision?.lowercase() ?: ""
        val name = rom.name.lowercase()
        val tags = rom.tags?.map { it.lowercase() } ?: emptyList()

        if (filters.excludeBeta && (revision.contains("beta") || name.contains("(beta)"))) return false
        if (filters.excludePrototype && (revision.contains("proto") || name.contains("(proto)"))) return false
        if (filters.excludeDemo && (revision.contains("demo") || name.contains("(demo)") || name.contains("(sample)"))) {
            return false
        }
        if (filters.excludeHack && isHack(name, revision, tags)) return false

        return true
    }

    private fun isHack(name: String, revision: String, tags: List<String>): Boolean {
        if (revision.contains("hack")) return true
        if (tags.any { it.contains("hack") }) return true
        if (NO_INTRO_HACK_REGEX.containsMatchIn(name)) return true
        if (HACK_BRACKET_REGEX.containsMatchIn(name)) return true
        if (HACK_PAREN_REGEX.containsMatchIn(name)) return true
        return false
    }
}
