package com.nendo.argosy.domain.model

object Changelog {
    private val entries = listOf(
        ChangelogEntry(
            version = "1.3.0",
            highlights = listOf(
                "Steam games now install to your ROMs folder, not GameNative's private storage",
                "GameNative 0.9.1+ requires you to point its Custom Games Folders at the new path",
                "Pick the Steam install path in Storage settings, then add that folder in GameNative",
                "Existing GameNative installs continue to work; new downloads use the new path"
            ),
            requiredActions = listOf(RequiredAction.SetSteamInstallPath),
            requiresActiveSteamAccount = true
        ),
        ChangelogEntry(
            version = "0.11.0",
            highlights = listOf(
                "BIOS management: download and auto-distribute to emulators",
                "3DO platform support for RetroArch (Opera core)",
                "Fixed disc-based game launching (broken m3u handling)",
                "RetroArch BIOS files auto-renamed to expected filenames"
            ),
            requiredActions = listOf(RequiredAction.ReloginRomM, RequiredAction.ResyncLibrary)
        ),
        ChangelogEntry(
            version = "0.10.0",
            highlights = listOf(
                "Folder-based ROM downloads with updates and DLC support",
                "Quick settings panel (R3) for haptics, sounds, and theme",
                "Improved 3DS filetype handling (optional force .3ds/.cci)",
                "Consolidated save/state path editing in storage settings"
            ),
            requiredActions = listOf(RequiredAction.ResyncLibrary)
        ),
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
