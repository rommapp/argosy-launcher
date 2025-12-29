package com.nendo.argosy.domain.model

object Changelog {
    private val entries = listOf(
        ChangelogEntry(
            version = "0.9.25",
            highlights = listOf(
                "Internal database migration for platform IDs",
                "Improved type safety and performance",
                "Fixed input debouncing when navigating settings sections",
                "Fixed emulator download links for Switch emulators",
                "Images now auto-repair when cached files are missing",
                "Screenshots load faster in game detail view"
            ),
            requiredActions = listOf(RequiredAction.ResyncLibrary)
        ),
        ChangelogEntry(
            version = "0.9.16",
            highlights = listOf(
                "Fixed crash for users with platform versions"
            ),
            requiredActions = listOf(RequiredAction.ResyncLibrary)
        ),
        ChangelogEntry(
            version = "0.9.15",
            highlights = listOf(
                "Bidirectional favorites sync with RomM",
                "Weekly game recommendations on home screen",
                "Library text search",
                "Improved save state conflict resolution",
                "Changelog modal for update notifications"
            ),
            requiredActions = listOf(RequiredAction.ReloginRomM)
        )
    )

    fun getEntry(version: String): ChangelogEntry? {
        val baseVersion = version.substringBefore("-")
        return entries.find { it.version == baseVersion || it.version == version }
    }

    fun hasEntry(version: String): Boolean = getEntry(version) != null
}
