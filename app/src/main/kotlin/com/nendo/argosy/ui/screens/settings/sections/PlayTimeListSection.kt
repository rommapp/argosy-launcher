package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.PlayBarRow
import com.nendo.argosy.ui.screens.settings.PlayTimeEntryUi
import com.nendo.argosy.ui.screens.settings.PlayTimeGamesSortMode
import com.nendo.argosy.ui.screens.settings.PlayTimeListKind
import com.nendo.argosy.ui.screens.settings.SettingsSection
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.SectionHeader
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.util.formatPlayTime
import com.nendo.argosy.util.formatRelativeTime

private const val MS_PER_MINUTE = 60_000L

internal sealed class PlayTimeListItem(
    val key: String,
    val section: String?,
    val isFocusable: Boolean
) {
    data object TotalsHeader : PlayTimeListItem("totalsHeader", null, false)
    data object ListSpacer : PlayTimeListItem("listSpacer", "entries", false)
    data object ListHeader : PlayTimeListItem("listHeader", "entries", false)
    data object EmptyState : PlayTimeListItem("emptyState", null, false)
    class Row(val entry: PlayTimeEntryUi) : PlayTimeListItem("entry_${entry.key}", "entries", true)

    companion object {
        fun buildItems(entries: List<PlayTimeEntryUi>): List<PlayTimeListItem> = buildList {
            add(TotalsHeader)
            add(ListSpacer)
            add(ListHeader)
            if (entries.isEmpty()) {
                add(EmptyState)
            } else {
                entries.forEach { add(Row(it)) }
            }
        }
    }
}

internal fun playTimeListKindOf(section: SettingsSection): PlayTimeListKind? = when (section) {
    SettingsSection.PLAY_TIME_PLATFORMS -> PlayTimeListKind.PLATFORMS
    SettingsSection.PLAY_TIME_DEVICES -> PlayTimeListKind.DEVICES
    SettingsSection.PLAY_TIME_GAMES -> PlayTimeListKind.GAMES
    else -> null
}

internal fun playTimeListEntries(state: SettingsUiState, kind: PlayTimeListKind): List<PlayTimeEntryUi> =
    when (kind) {
        PlayTimeListKind.PLATFORMS -> state.playTime.platforms
        PlayTimeListKind.DEVICES -> state.playTime.devices
        PlayTimeListKind.GAMES -> when (state.playTime.gamesSortMode) {
            PlayTimeGamesSortMode.HOURS -> state.playTime.games
            PlayTimeGamesSortMode.SESSIONS -> state.playTime.games.sortedByDescending { it.sessionCount }
            PlayTimeGamesSortMode.RECENT -> state.playTime.games.sortedByDescending { it.lastPlayed }
        }
    }

internal fun createPlayTimeListLayout(items: List<PlayTimeListItem>) =
    SettingsLayout<PlayTimeListItem, Unit>(
        allItems = items,
        isFocusable = { it.isFocusable },
        visibleWhen = { _, _ -> true },
        sectionOf = { it.section }
    )

internal data class PlayTimeListLayoutInfo(
    val layout: SettingsLayout<PlayTimeListItem, Unit>
)

internal fun createPlayTimeListLayoutInfo(state: SettingsUiState, kind: PlayTimeListKind): PlayTimeListLayoutInfo =
    PlayTimeListLayoutInfo(createPlayTimeListLayout(PlayTimeListItem.buildItems(playTimeListEntries(state, kind))))

internal fun playTimeListMaxFocusIndex(info: PlayTimeListLayoutInfo): Int =
    info.layout.maxFocusIndex(Unit)

internal fun playTimeListSections(info: PlayTimeListLayoutInfo) = info.layout.buildSections(Unit)

@Composable
fun PlayTimeListSection(uiState: SettingsUiState, viewModel: SettingsViewModel, kind: PlayTimeListKind) {
    val playTime = uiState.playTime
    val context = LocalContext.current
    val entries = remember(playTime.platforms, playTime.devices, playTime.games, playTime.gamesSortMode, kind) {
        playTimeListEntries(uiState, kind)
    }
    val allItems = remember(entries) { PlayTimeListItem.buildItems(entries) }
    val layout = remember(allItems) { createPlayTimeListLayout(allItems) }
    val visibleItems = remember(layout) { layout.visibleItems(Unit) }
    val sections = remember(layout, context) { layout.buildSections(Unit, context) }
    val totalMs = remember(entries) { entries.sumOf { it.activeMs } }
    val maxMs = remember(entries) { entries.maxOfOrNull { it.activeMs } ?: 0L }

    fun isFocused(item: PlayTimeListItem): Boolean =
        uiState.focusedIndex == layout.focusIndexOf(item, Unit)

    SectionPaneLayout(
        items = visibleItems,
        sections = sections,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { layout.focusToListIndex(it, Unit) },
        itemKey = { it.key },
        isNavItem = { it is PlayTimeListItem.ListSpacer },
        isHeader = { it is PlayTimeListItem.ListHeader },
        onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) { item ->
        when (item) {
            PlayTimeListItem.TotalsHeader -> ListTotalsHeader(kind = kind, count = entries.size, totalMs = totalMs)

            PlayTimeListItem.ListSpacer -> Spacer(modifier = Modifier.height(Dimens.spacingMd))

            PlayTimeListItem.ListHeader -> ListHeaderRow(
                kind = kind,
                sortMode = playTime.gamesSortMode,
                onToggleSort = { viewModel.togglePlayTimeGamesSortMode() }
            )

            PlayTimeListItem.EmptyState -> ListEmptyState(kind)

            is PlayTimeListItem.Row -> {
                val entry = item.entry
                PlayBarRow(
                    name = entry.name.ifBlank { stringResource(R.string.settings_play_time_list_unknown_game) },
                    valueLabel = formatPlayTime(context, (entry.activeMs / MS_PER_MINUTE).toInt()),
                    value = entry.activeMs,
                    maxValue = maxMs,
                    detail = when (kind) {
                        PlayTimeListKind.GAMES -> stringResource(
                            R.string.settings_play_time_list_game_detail,
                            entry.platformName,
                            formatRelativeTime(context, entry.lastPlayed)
                        )
                        else -> pluralStringResource(
                            R.plurals.settings_play_time_list_sessions,
                            entry.sessionCount,
                            entry.sessionCount
                        )
                    },
                    badge = if (entry.isThisDevice) stringResource(R.string.settings_play_time_list_this_device) else null,
                    isFocused = isFocused(item),
                    onClick = { viewModel.setFocusIndex(layout.focusIndexOf(item, Unit)) }
                )
            }
        }
    }
}

@Composable
private fun ListTotalsHeader(kind: PlayTimeListKind, count: Int, totalMs: Long) {
    val theme = LocalArgosyTheme.current
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusLg))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(Dimens.spacingMd)
    ) {
        Text(
            text = when (kind) {
                PlayTimeListKind.PLATFORMS ->
                    pluralStringResource(R.plurals.settings_play_time_platforms_totals_count, count, count)
                PlayTimeListKind.DEVICES ->
                    pluralStringResource(R.plurals.settings_play_time_devices_totals_count, count, count)
                PlayTimeListKind.GAMES ->
                    pluralStringResource(R.plurals.settings_play_time_games_totals_count, count, count)
            },
            style = MaterialTheme.typography.titleMedium,
            color = theme.textPrimary
        )
        Text(
            text = stringResource(
                R.string.settings_play_time_list_totals_played,
                formatPlayTime(context, (totalMs / MS_PER_MINUTE).toInt())
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textDim
        )
    }
}

@Composable
private fun ListHeaderRow(
    kind: PlayTimeListKind,
    sortMode: PlayTimeGamesSortMode,
    onToggleSort: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SectionHeader(
            when (kind) {
                PlayTimeListKind.PLATFORMS -> stringResource(R.string.settings_play_time_platforms_list_header)
                PlayTimeListKind.DEVICES -> stringResource(R.string.settings_play_time_devices_list_header)
                PlayTimeListKind.GAMES -> stringResource(R.string.settings_play_time_games_list_header)
            }
        )
        if (kind == PlayTimeListKind.GAMES) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(Dimens.radiusSm))
                    .clickableNoFocus(onClick = onToggleSort)
                    .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
            ) {
                Icon(
                    imageVector = Icons.Default.SwapVert,
                    contentDescription = stringResource(R.string.settings_play_time_games_sort_toggle_description),
                    tint = theme.textDim,
                    modifier = Modifier.size(Dimens.iconXs)
                )
                Text(
                    text = when (sortMode) {
                        PlayTimeGamesSortMode.HOURS -> stringResource(R.string.settings_play_time_games_sort_hours)
                        PlayTimeGamesSortMode.SESSIONS -> stringResource(R.string.settings_play_time_games_sort_sessions)
                        PlayTimeGamesSortMode.RECENT -> stringResource(R.string.settings_play_time_games_sort_recent)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textDim
                )
            }
        }
    }
}

@Composable
private fun ListEmptyState(kind: PlayTimeListKind) {
    val theme = LocalArgosyTheme.current
    Text(
        text = when (kind) {
            PlayTimeListKind.PLATFORMS -> stringResource(R.string.settings_play_time_platforms_list_empty)
            PlayTimeListKind.DEVICES -> stringResource(R.string.settings_play_time_devices_list_empty)
            PlayTimeListKind.GAMES -> stringResource(R.string.settings_play_time_games_list_empty)
        },
        style = MaterialTheme.typography.bodySmall,
        color = theme.textDim,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusLg))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(Dimens.spacingMd)
    )
}
