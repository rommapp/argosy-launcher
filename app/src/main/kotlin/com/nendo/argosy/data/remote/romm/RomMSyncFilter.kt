package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.data.preferences.RegionFilterMode
import com.nendo.argosy.data.preferences.SyncFilterPreferences

object RomMSyncFilter {

    private val NO_INTRO_HACK_REGEX = Regex("\\[h[0-9a-z ]*\\]")
    private val HACK_BRACKET_REGEX = Regex("\\[.*\\bhack\\b.*\\]")
    private val HACK_PAREN_REGEX = Regex("\\(.*\\bhack\\b.*\\)")
    private val BAD_DUMP_REGEX = Regex("\\[[boftpBOFTP][0-9]*\\]")

    fun shouldSyncRom(rom: RomMRom, filters: SyncFilterPreferences): Boolean {
        if (!passesExtensionFilter(rom)) return false
        if (!passesBadDumpFilter(rom)) return false
        if (!passesRegionFilter(rom, filters)) return false
        if (!passesRevisionFilter(rom, filters)) return false
        return true
    }

    private fun passesExtensionFilter(rom: RomMRom): Boolean {
        val fileName = rom.fileName ?: return true
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (extension.isEmpty()) return true

        val platformDef = PlatformDefinitions.getById(rom.platformSlug) ?: return true
        if (platformDef.extensions.isEmpty()) return true

        return extension in platformDef.extensions
    }

    private fun passesBadDumpFilter(rom: RomMRom): Boolean {
        val name = rom.name.lowercase()
        val fileName = rom.fileName?.lowercase() ?: ""
        if (BAD_DUMP_REGEX.containsMatchIn(name) || BAD_DUMP_REGEX.containsMatchIn(fileName)) return false
        return true
    }

    private fun passesRegionFilter(rom: RomMRom, filters: SyncFilterPreferences): Boolean {
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
