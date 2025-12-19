package com.nendo.argosy.domain.model

object Changelog {
    private val entries = listOf(
        ChangelogEntry(
            version = "0.9.14",
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
