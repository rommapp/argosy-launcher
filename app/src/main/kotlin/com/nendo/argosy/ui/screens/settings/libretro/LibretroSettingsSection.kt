package com.nendo.argosy.ui.screens.settings.libretro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.nendo.argosy.data.platform.PlatformWeightRegistry
import com.nendo.argosy.ui.components.ListSection
import com.nendo.argosy.ui.components.SectionFocusedScroll
import com.nendo.argosy.ui.theme.Dimens

@Composable
fun LibretroSettingsSection(
    accessor: LibretroSettingsAccessor,
    focusedIndex: Int,
    platformSlug: String? = null,
    canEnableBFI: Boolean = false,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    val isPerPlatform = platformSlug != null

    val visibleSettings = remember(platformSlug, canEnableBFI) {
        LibretroSettingDef.ALL.filter { setting ->
            PlatformWeightRegistry.isSettingVisible(setting, platformSlug, canEnableBFI)
        }
    }

    val groupedBySection = remember(visibleSettings) {
        LibretroSettingDef.SECTION_ORDER.mapNotNull { section ->
            val items = visibleSettings.filter { it.section == section }
            if (items.isNotEmpty()) section to items else null
        }
    }

    val flatItems = remember(groupedBySection) {
        buildList {
            groupedBySection.forEachIndexed { index, (section, items) ->
                add(LibretroSettingsListItem.Header(section, isFirst = index == 0))
                items.forEach { add(LibretroSettingsListItem.Setting(it)) }
            }
        }
    }

    val sections = remember(flatItems, visibleSettings) {
        buildSections(flatItems, visibleSettings)
    }

    SectionFocusedScroll(
        listState = listState,
        focusedIndex = focusedIndex,
        focusToListIndex = { focusIdx ->
            val setting = visibleSettings.getOrNull(focusIdx) ?: return@SectionFocusedScroll focusIdx
            flatItems.indexOfFirst { it is LibretroSettingsListItem.Setting && it.def == setting }
        },
        sections = sections
    )

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        items(flatItems, key = { it.key }) { item ->
            when (item) {
                is LibretroSettingsListItem.Header -> {
                    if (!item.isFirst) {
                        Spacer(modifier = Modifier.height(Dimens.spacingMd))
                    }
                    Text(
                        text = LibretroSettingDef.SECTIONS[item.section] ?: item.section,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(
                            start = Dimens.spacingSm,
                            top = Dimens.spacingXs,
                            bottom = Dimens.spacingXs
                        )
                    )
                }

                is LibretroSettingsListItem.Setting -> {
                    val settingFocusIndex = visibleSettings.indexOf(item.def)
                    LibretroSettingItem(
                        setting = item.def,
                        accessor = accessor,
                        isFocused = focusedIndex == settingFocusIndex,
                        isPerPlatform = isPerPlatform
                    )
                }
            }
        }
    }
}

private sealed class LibretroSettingsListItem(val key: String) {
    data class Header(val section: String, val isFirst: Boolean) : LibretroSettingsListItem("header_$section")
    data class Setting(val def: LibretroSettingDef) : LibretroSettingsListItem(def.key)
}

private fun buildSections(
    flatItems: List<LibretroSettingsListItem>,
    visibleSettings: List<LibretroSettingDef>
): List<ListSection> {
    val sectionNames = LibretroSettingDef.SECTION_ORDER.filter { section ->
        visibleSettings.any { it.section == section }
    }

    return sectionNames.mapNotNull { sectionName ->
        val sectionItems = flatItems.filter { item ->
            when (item) {
                is LibretroSettingsListItem.Header -> item.section == sectionName
                is LibretroSettingsListItem.Setting -> item.def.section == sectionName
            }
        }
        val sectionSettings = visibleSettings.filter { it.section == sectionName }
        if (sectionItems.isEmpty() || sectionSettings.isEmpty()) return@mapNotNull null

        ListSection(
            listStartIndex = flatItems.indexOf(sectionItems.first()),
            listEndIndex = flatItems.indexOf(sectionItems.last()),
            focusStartIndex = visibleSettings.indexOf(sectionSettings.first()),
            focusEndIndex = visibleSettings.indexOf(sectionSettings.last())
        )
    }
}

fun libretroSettingsMaxFocusIndex(platformSlug: String?, canEnableBFI: Boolean): Int {
    val visibleCount = LibretroSettingDef.ALL.count { setting ->
        PlatformWeightRegistry.isSettingVisible(setting, platformSlug, canEnableBFI)
    }
    return (visibleCount - 1).coerceAtLeast(0)
}

fun libretroSettingsItemAtFocusIndex(
    index: Int,
    platformSlug: String?,
    canEnableBFI: Boolean
): LibretroSettingDef? {
    val visibleSettings = LibretroSettingDef.ALL.filter { setting ->
        PlatformWeightRegistry.isSettingVisible(setting, platformSlug, canEnableBFI)
    }
    return visibleSettings.getOrNull(index)
}
