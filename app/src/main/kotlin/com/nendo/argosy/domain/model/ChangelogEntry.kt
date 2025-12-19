package com.nendo.argosy.domain.model

import com.nendo.argosy.ui.screens.settings.SettingsSection

data class ChangelogEntry(
    val version: String,
    val highlights: List<String>,
    val requiredActions: List<RequiredAction> = emptyList()
)

sealed class RequiredAction(
    val label: String,
    val section: SettingsSection,
    val actionKey: String? = null
) {
    data object ReloginRomM : RequiredAction("Re-login to RomM", SettingsSection.SERVER, "rommConfig")
    data object ResyncLibrary : RequiredAction("Resync Library", SettingsSection.SERVER, "syncLibrary")
    data object ClearCache : RequiredAction("Clear Cache", SettingsSection.STORAGE)
}
