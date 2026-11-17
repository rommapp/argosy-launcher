package com.nendo.argosy.ui.screens.settings.libretro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.nendo.argosy.core.emulator.LibretroSettingDef
import com.nendo.argosy.data.platform.PlatformWeightRegistry
import com.nendo.argosy.ui.components.ListSection
import com.nendo.argosy.ui.components.SectionFocusedScroll
import com.nendo.argosy.ui.screens.settings.components.LocalSettingsTouchFocus
import com.nendo.argosy.ui.screens.settings.components.SettingsRowTapArea
import com.nendo.argosy.ui.screens.settings.components.settingsRowItems
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens

/**
 * Which libretro settings a screen renders. [VIDEO] is every group except saving; [SAVES] is
 * the saving group alone.
 */
enum class LibretroSettingsScope { VIDEO, SAVES }

private const val SAVING_SECTION = "saving"

data class LibretroVisibilityState(
    val platformSlug: String?,
    val canEnableBFI: Boolean,
    val scope: LibretroSettingsScope = LibretroSettingsScope.VIDEO
)

internal fun isLibretroSettingShown(item: LibretroSettingDef, state: LibretroVisibilityState): Boolean {
    val inScope = (item.section == SAVING_SECTION) == (state.scope == LibretroSettingsScope.SAVES)
    return inScope && PlatformWeightRegistry.isSettingVisible(item, state.platformSlug, state.canEnableBFI)
}

private val libretroSettingsLayout = SettingsLayout<LibretroSettingDef, LibretroVisibilityState>(
    allItems = LibretroSettingDef.ALL,
    isFocusable = { true },
    visibleWhen = { item, state -> isLibretroSettingShown(item, state) },
    sectionOf = { it.section }
)

@Composable
fun LibretroSettingsSection(
    accessor: LibretroSettingsAccessor,
    focusedIndex: Int,
    platformSlug: String? = null,
    canEnableBFI: Boolean = false,
    scope: LibretroSettingsScope = LibretroSettingsScope.VIDEO,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
    enablePicker: Boolean = true,
    pickerTokenFor: (LibretroSettingDef) -> Int = { 0 },
    trailingContent: @Composable (() -> Unit)? = null,
    trailingFocusCount: Int = 0,
    trailingItems: (androidx.compose.foundation.lazy.LazyListScope.() -> Unit)? = null,
    leadingHeader: String? = null,
    leadingFocusCount: Int = 0,
    leadingRow: @Composable (index: Int, isFocused: Boolean) -> Unit = { _, _ -> },
    inSession: Boolean = false
) {
    val isPerPlatform = platformSlug != null
    val leadingListCount = if (leadingFocusCount > 0) leadingFocusCount + 1 else 0

    val visibilityState = remember(platformSlug, canEnableBFI, scope) {
        LibretroVisibilityState(platformSlug, canEnableBFI, scope)
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

    val trailingContentOffset = if (trailingContent != null) 1 else 0

    val sections = remember(
        flatItems, visibleSettings, trailingContentOffset, trailingFocusCount, leadingFocusCount
    ) {
        val leadingSections = if (leadingFocusCount <= 0) {
            emptyList()
        } else {
            listOf(
                ListSection(
                    listStartIndex = 0,
                    listEndIndex = leadingListCount - 1,
                    focusStartIndex = 0,
                    focusEndIndex = leadingFocusCount - 1
                )
            )
        }
        val settingSections = buildFlatItemSections(flatItems, visibleSettings).map {
            it.copy(
                listStartIndex = it.listStartIndex + leadingListCount,
                listEndIndex = it.listEndIndex + leadingListCount,
                focusStartIndex = it.focusStartIndex + leadingFocusCount,
                focusEndIndex = it.focusEndIndex + leadingFocusCount
            )
        }
        val trailingSections = if (trailingFocusCount <= 0) {
            emptyList()
        } else {
            listOf(
                ListSection(
                    listStartIndex = leadingListCount + flatItems.size,
                    listEndIndex = leadingListCount + flatItems.size + trailingContentOffset + trailingFocusCount - 1,
                    focusStartIndex = leadingFocusCount + visibleSettings.size,
                    focusEndIndex = leadingFocusCount + visibleSettings.size + trailingFocusCount - 1
                )
            )
        }
        leadingSections + settingSections + trailingSections
    }

    SectionFocusedScroll(
        listState = listState,
        focusedIndex = focusedIndex,
        focusToListIndex = { focusIdx ->
            val settingIdx = focusIdx - leadingFocusCount
            val setting = visibleSettings.getOrNull(settingIdx)
            when {
                focusIdx < leadingFocusCount -> 1 + focusIdx
                setting != null -> leadingListCount +
                    flatItems.indexOfFirst { it is LibretroSettingsListItem.Setting && it.def == setting }
                else -> leadingListCount + flatItems.size + trailingContentOffset + settingIdx - visibleSettings.size
            }
        },
        sections = sections,
        isTouchMode = LocalSettingsTouchFocus.current.isTouchMode
    )

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        if (leadingFocusCount > 0) {
            item(key = "leading_header") {
                LibretroSectionHeader(text = leadingHeader.orEmpty(), isFirst = true)
            }
            items(leadingFocusCount, key = { "leading_row_$it" }) { index ->
                SettingsRowTapArea(index) {
                    leadingRow(index, focusedIndex == index)
                }
            }
        }

        settingsRowItems(
            flatItems,
            key = { it.key },
            focusIndexOf = { item ->
                when (item) {
                    is LibretroSettingsListItem.Header -> -1
                    is LibretroSettingsListItem.Setting -> leadingFocusCount + visibleSettings.indexOf(item.def)
                }
            }
        ) { item ->
            when (item) {
                is LibretroSettingsListItem.Header -> LibretroSectionHeader(
                    text = LibretroSettingDef.SECTIONS[item.section]
                        ?.let { stringResource(it) }
                        ?: item.section,
                    isFirst = item.isFirst && leadingFocusCount == 0
                )

                is LibretroSettingsListItem.Setting -> {
                    val settingFocusIndex = leadingFocusCount + visibleSettings.indexOf(item.def)
                    LibretroSettingItem(
                        setting = item.def,
                        accessor = accessor,
                        isFocused = focusedIndex == settingFocusIndex,
                        isPerPlatform = isPerPlatform,
                        enablePicker = enablePicker,
                        pickerRequestToken = pickerTokenFor(item.def),
                        inSession = inSession
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

@Composable
private fun LibretroSectionHeader(text: String, isFirst: Boolean) {
    if (!isFirst) {
        Spacer(modifier = Modifier.height(Dimens.spacingMd))
    }
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(
            start = Dimens.spacingSm,
            top = Dimens.spacingXs,
            bottom = Dimens.spacingXs
        )
    )
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

fun libretroSettingsMaxFocusIndex(
    platformSlug: String?,
    canEnableBFI: Boolean,
    scope: LibretroSettingsScope = LibretroSettingsScope.VIDEO
): Int =
    libretroSettingsLayout.maxFocusIndex(LibretroVisibilityState(platformSlug, canEnableBFI, scope))

/**
 * Resolves a focus index to a libretro setting. [scope] must match the value the matching
 * [LibretroSettingsSection] renders with, or indices resolve against the other screen's rows.
 */
fun libretroSettingsItemAtFocusIndex(
    index: Int,
    platformSlug: String?,
    canEnableBFI: Boolean,
    scope: LibretroSettingsScope = LibretroSettingsScope.VIDEO
): LibretroSettingDef? =
    libretroSettingsLayout.itemAtFocusIndex(
        index,
        LibretroVisibilityState(platformSlug, canEnableBFI, scope)
    )
