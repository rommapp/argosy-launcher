package com.nendo.argosy.data.preferences

data class SyncFilterPreferences(
    val enabledRegions: Set<String> = DEFAULT_REGIONS,
    val regionMode: RegionFilterMode = RegionFilterMode.INCLUDE,
    val excludeBeta: Boolean = true,
    val excludePrototype: Boolean = true,
    val excludeDemo: Boolean = true,
    val deleteOrphans: Boolean = true
) {
    companion object {
        val DEFAULT_REGIONS = setOf("USA")
        val ALL_KNOWN_REGIONS = listOf(
            "USA", "World", "Europe", "Japan", "Korea",
            "China", "Taiwan", "Australia", "Brazil",
            "France", "Germany", "Italy", "Spain"
        )
    }
}

enum class RegionFilterMode {
    INCLUDE,
    EXCLUDE
}
