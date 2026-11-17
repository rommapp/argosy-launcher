package com.nendo.argosy.ui.screens.settings.search

import com.nendo.argosy.ui.common.DisplayText
import com.nendo.argosy.ui.screens.settings.SettingsSection

sealed interface SettingsSearchTarget {
    val id: String

    data class Row(val section: SettingsSection, val key: String) : SettingsSearchTarget {
        override val id: String get() = "row:${section.name}:$key"
    }

    data class Platform(val platformId: Long, val rowKey: String? = null) : SettingsSearchTarget {
        override val id: String get() = if (rowKey == null) "platform:$platformId" else "platform:$platformId:$rowKey"
    }

    data class CoreOption(val coreId: String, val optionKey: String) : SettingsSearchTarget {
        override val id: String get() = "coreOption:$coreId:$optionKey"
    }
}

data class SettingsSearchEntry(
    val target: SettingsSearchTarget,
    val label: DisplayText,
    val keywords: DisplayText?,
    val path: List<DisplayText>
)

data class SettingsSearchResult(
    val entry: SettingsSearchEntry,
    val matchStart: Int,
    val matchLength: Int
)

data class SettingsSearchCoreOption(
    val coreId: String,
    val coreName: String,
    val optionKey: String,
    val optionName: String
)

data class SettingsSearchState(
    val query: String = "",
    val keyboardOpen: Boolean = false,
    val results: List<SettingsSearchResult> = emptyList(),
    val coreOptions: List<SettingsSearchCoreOption> = emptyList()
) {
    val isActive: Boolean get() = query.isNotBlank()
}
