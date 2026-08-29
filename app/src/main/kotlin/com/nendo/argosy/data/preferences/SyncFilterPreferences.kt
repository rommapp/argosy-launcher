package com.nendo.argosy.data.preferences

data class SyncFilterPreferences(
    val enabledRegions: List<String> = DEFAULT_REGIONS,
    val regionMode: RegionFilterMode = DEFAULT_REGION_MODE,
    val excludeBeta: Boolean = true,
    val excludePrototype: Boolean = true,
    val excludeDemo: Boolean = true,
    val excludeHack: Boolean = false,
    val excludeUnofficial: Boolean = false,
    val deleteOrphans: Boolean = true
) {
    /**
     * Whether [enabledRegions] is an ordered preference rather than a blacklist. Only an include
     * list carries priority, so consumers that rank by region must not read an exclude list.
     */
    val hasRegionPriority: Boolean
        get() = regionMode == RegionFilterMode.INCLUDE && enabledRegions.isNotEmpty()

    /** Enabled regions in priority order followed by disabled; canonical order when priority is off. */
    val pickerDisplayOrder: List<String>
        get() = if (regionMode != RegionFilterMode.INCLUDE) ALL_KNOWN_REGIONS
        else enabledRegions + ALL_KNOWN_REGIONS.filter { it !in enabledRegions }

    /** Rank of a rom's best region in the priority list; unranked and untagged roms sort last. */
    fun regionRank(romRegions: List<String>?): Int {
        if (romRegions.isNullOrEmpty()) return UNRANKED
        return romRegions.minOf { region ->
            enabledRegions.indexOfFirst { it.equals(region, ignoreCase = true) }
                .let { if (it == -1) UNRANKED else it }
        }
    }

    companion object {
        const val UNRANKED = Int.MAX_VALUE
        val ALL_KNOWN_REGIONS = listOf(
            "USA", "World", "Europe", "Japan", "Korea",
            "China", "Taiwan", "Australia", "Brazil",
            "France", "Germany", "Italy", "Spain"
        )
        /**
         * A blacklist of nothing, so a library syncs whole until the user says otherwise. The
         * include default whitelisted [ALL_KNOWN_REGIONS] and silently dropped every rom tagged
         * with a region outside that fixed list, which reads as a broken sync rather than a filter.
         */
        val DEFAULT_REGIONS = emptyList<String>()
        val DEFAULT_REGION_MODE = RegionFilterMode.EXCLUDE
    }
}

enum class RegionFilterMode {
    INCLUDE,
    EXCLUDE
}
