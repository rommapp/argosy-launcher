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
import com.nendo.argosy.core.emulator.LibretroSettingDef
import com.nendo.argosy.data.platform.PlatformWeightRegistry
import com.nendo.argosy.ui.components.ListSection
import com.nendo.argosy.ui.components.SectionFocusedScroll
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens

data class LibretroVisibilityState(
    val platformSlug: String?,
    val canEnableBFI: Boolean,
    val showSavingSection: Boolean = true
)

private val libretroSettingsLayout = SettingsLayout<LibretroSettingDef, LibretroVisibilityState>(
    allItems = LibretroSettingDef.ALL,
    isFocusable = { true },
    visibleWhen = { item, state ->
        if (item.section == "saving" && !state.showSavingSection) return@SettingsLayout false
        PlatformWeightRegistry.isSettingVisible(item, state.platformSlug, state.canEnableBFI)
    },
    sectionOf = { it.section }
)

@Composable
fun LibretroSettingsSection(
    accessor: LibretroSettingsAccessor,
    focusedIndex: Int,
    platformSlug: String? = null,
    canEnableBFI: Boolean = false,
    showSavingSection: Boolean = true,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = null,
    trailingItems: (androidx.compose.foundation.lazy.LazyListScope.() -> Unit)? = null
) {
    val isPerPlatform = platformSlug != null

    val visibilityState = remember(platformSlug, canEnableBFI, showSavingSection) {
        LibretroVisibilityState(platformSlug, canEnableBFI, showSavingSection)
    }

    val visibleSettings = remember(visibilityState) {
        libretroSettingsLayout.focusableItems(visibilityState)
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
        buildFlatItemSections(flatItems, visibleSettings)
    }

    val trailingContentOffset = if (trailingContent != null) 1 else 0

    SectionFocusedScroll(
        listState = listState,
        focusedIndex = focusedIndex,
        focusToListIndex = { focusIdx ->
            val setting = visibleSettings.getOrNull(focusIdx)
            if (setting != null) {
                flatItems.indexOfFirst { it is LibretroSettingsListItem.Setting && it.def == setting }
            } else {
                val trailingIdx = focusIdx - visibleSettings.size
                flatItems.size + trailingContentOffset + trailingIdx
            }
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

        if (trailingContent != null) {
            item(key = "trailing_content") {
                Spacer(modifier = Modifier.height(Dimens.spacingMd))
                trailingContent()
            }
        }

        trailingItems?.invoke(this)
    }
}

private sealed class LibretroSettingsListItem(val key: String) {
    data class Header(val section: String, val isFirst: Boolean) : LibretroSettingsListItem("header_$section")
    data class Setting(val def: LibretroSettingDef) : LibretroSettingsListItem(def.key)
}

private fun buildFlatItemSections(
    flatItems: List<LibretroSettingsListItem>,
    visibleSettings: List<LibretroSettingDef>
): List<ListSection> {
    val sectionNames = LibretroSettingDef.SECTION_ORDER.filter { section ->
        visibleSettings.any { it.section == section }
    }

    return sectionNames.mapNotNull { sectionName ->
        val sectionSettings = visibleSettings.filter { it.section == sectionName }
        if (sectionSettings.isEmpty()) return@mapNotNull null

        val firstFlatIdx = flatItems.indexOfFirst {
            it is LibretroSettingsListItem.Header && it.section == sectionName
        }
        val lastFlatIdx = flatItems.indexOfLast {
            it is LibretroSettingsListItem.Setting && it.def.section == sectionName
        }
        if (firstFlatIdx < 0 || lastFlatIdx < 0) return@mapNotNull null

        ListSection(
            listStartIndex = firstFlatIdx,
            listEndIndex = lastFlatIdx,
            focusStartIndex = visibleSettings.indexOf(sectionSettings.first()),
            focusEndIndex = visibleSettings.indexOf(sectionSettings.last())
        )
    }
}

fun libretroSettingsMaxFocusIndex(platformSlug: String?, canEnableBFI: Boolean, showSavingSection: Boolean = true): Int =
    libretroSettingsLayout.maxFocusIndex(LibretroVisibilityState(platformSlug, canEnableBFI, showSavingSection))

fun libretroSettingsItemAtFocusIndex(
    index: Int,
    platformSlug: String?,
    canEnableBFI: Boolean
): LibretroSettingDef? =
    libretroSettingsLayout.itemAtFocusIndex(index, LibretroVisibilityState(platformSlug, canEnableBFI))
