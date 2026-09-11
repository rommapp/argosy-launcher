package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.common.resolve
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.CategoryTile
import com.nendo.argosy.ui.components.InfoPreference
import com.nendo.argosy.ui.components.ListSection
import com.nendo.argosy.ui.components.playtime.MosaicGame
import com.nendo.argosy.ui.components.playtime.MosaicTile
import com.nendo.argosy.ui.components.playtime.foldMosaic
import com.nendo.argosy.ui.components.playtime.foldedGames
import com.nendo.argosy.ui.screens.settings.ConnectionStatus
import com.nendo.argosy.ui.screens.settings.PlayTimeFigure
import com.nendo.argosy.ui.screens.settings.PlayTimeScrub
import com.nendo.argosy.ui.screens.settings.PlayTimeState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.SectionHeader
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.util.formatClockDateTime
import com.nendo.argosy.util.formatPlayTime
import java.time.LocalDateTime

private const val MS_PER_MINUTE = 60_000L
private const val DAYS_IN_WEEK = 7
private const val HOURS_IN_DAY = 24

internal data class PlayTimeLayoutState(val isLoading: Boolean, val isEmpty: Boolean) {
    val hasData: Boolean get() = !isLoading && !isEmpty

    companion object {
        fun from(state: SettingsUiState) = PlayTimeLayoutState(
            isLoading = state.playTime.isLoading && !state.playTime.hasLoaded,
            isEmpty = state.playTime.isEmpty
        )
    }
}

internal sealed class PlayTimeItem(
    val key: String,
    val section: String?,
    val visibleWhen: (PlayTimeLayoutState) -> Boolean = { it.hasData }
) {
    val isFocusable: Boolean get() = when (this) {
        is Header, is SectionSpacer, LoadingState, EmptyState,
        PlatformBand, DeviceBand, FactsCard -> false
        else -> true
    }

    class Header(key: String, section: String, val titleRes: Int) : PlayTimeItem(key, section)
    class SectionSpacer(key: String, section: String) : PlayTimeItem(key, section)

    data object LoadingState : PlayTimeItem("loading", null, { it.isLoading })
    data object EmptyState : PlayTimeItem("empty", null, { it.isEmpty })

    data object CalendarCard : PlayTimeItem("calendarCard", "activity")

    data object WaveformCard : PlayTimeItem("waveformCard", "when")

    data object PlatformBand : PlayTimeItem("platformBand", "platforms")
    data object PlatformsTile : PlayTimeItem("platformsTile", "platforms")
    data object DeviceBand : PlayTimeItem("deviceBand", "where")
    data object DevicesTile : PlayTimeItem("devicesTile", "where")

    data object MosaicCard : PlayTimeItem("mosaicCard", "what")
    data object GamesTile : PlayTimeItem("gamesTile", "what")

    data object FactsCard : PlayTimeItem("factsCard", "facts")

    data object RommOnServer : PlayTimeItem("rommOnServer", "romm", { !it.isLoading })
    data object RommPending : PlayTimeItem("rommPending", "romm", { !it.isLoading })
    data object RommUnlinked : PlayTimeItem("rommUnlinked", "romm", { !it.isLoading })
    data object RommLastUpload : PlayTimeItem("rommLastUpload", "romm", { !it.isLoading })
    data object RommPulled : PlayTimeItem("rommPulled", "romm", { !it.isLoading })
    data object RommLastRefresh : PlayTimeItem("rommLastRefresh", "romm", { !it.isLoading })
    data object UploadNow : PlayTimeItem("uploadNow", "romm", { !it.isLoading })
    data object RefreshFromRomm : PlayTimeItem("refreshFromRomm", "romm", { !it.isLoading })

    companion object {
        private val WhenSpacer = SectionSpacer("whenSpacer", "when")
        private val WhenHeader = Header("whenHeader", "when", R.string.settings_play_time_section_when)
        private val PlatformsSpacer = SectionSpacer("platformsSpacer", "platforms")
        private val PlatformsHeader = Header("platformsHeader", "platforms", R.string.settings_play_time_section_platforms)
        private val WhereSpacer = SectionSpacer("whereSpacer", "where")
        private val WhereHeader = Header("whereHeader", "where", R.string.settings_play_time_section_where)
        private val WhatSpacer = SectionSpacer("whatSpacer", "what")
        private val WhatHeader = Header("whatHeader", "what", R.string.settings_play_time_section_what)
        private val FactsSpacer = SectionSpacer("factsSpacer", "facts")
        private val FactsHeader = Header("factsHeader", "facts", R.string.settings_play_time_section_facts)
        private val RommSpacer = SectionSpacer("rommSpacer", "romm")
        private val RommHeader = Header("rommHeader", "romm", R.string.settings_play_time_section_romm)

        val ALL: List<PlayTimeItem>
            get() = listOf(
                LoadingState, EmptyState,
                CalendarCard,
                WhenSpacer, WhenHeader, WaveformCard,
                PlatformsSpacer, PlatformsHeader, PlatformBand, PlatformsTile,
                WhereSpacer, WhereHeader, DeviceBand, DevicesTile,
                WhatSpacer, WhatHeader, MosaicCard, GamesTile,
                FactsSpacer, FactsHeader, FactsCard,
                RommSpacer, RommHeader, RommOnServer, RommPending, RommUnlinked, RommLastUpload, RommPulled,
                RommLastRefresh, UploadNow, RefreshFromRomm
            )
    }
}

private val playTimeLayout = SettingsLayout<PlayTimeItem, PlayTimeLayoutState>(
    allItems = PlayTimeItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section },
    sectionTitleRes = {
        when (it) {
            "activity" -> R.string.settings_play_time_section_activity
            "when" -> R.string.settings_play_time_section_when
            "platforms" -> R.string.settings_play_time_section_platforms
            "where" -> R.string.settings_play_time_section_where
            "what" -> R.string.settings_play_time_section_what
            "facts" -> R.string.settings_play_time_section_facts
            "romm" -> R.string.settings_play_time_section_romm
            else -> null
        }
    }
)

internal data class PlayTimeLayoutInfo(
    val layout: SettingsLayout<PlayTimeItem, PlayTimeLayoutState>,
    val state: PlayTimeLayoutState
)

internal fun createPlayTimeLayoutInfo(state: SettingsUiState): PlayTimeLayoutInfo =
    PlayTimeLayoutInfo(playTimeLayout, PlayTimeLayoutState.from(state))

internal fun playTimeItemAtFocusIndex(index: Int, info: PlayTimeLayoutInfo): PlayTimeItem? =
    info.layout.itemAtFocusIndex(index, info.state)

internal fun playTimeMaxFocusIndex(info: PlayTimeLayoutInfo): Int =
    info.layout.maxFocusIndex(info.state)

internal fun playTimeSections(info: PlayTimeLayoutInfo): List<ListSection> =
    info.layout.buildSections(info.state)

internal fun playTimeFigureOf(item: PlayTimeItem?): PlayTimeFigure? = when (item) {
    PlayTimeItem.CalendarCard -> PlayTimeFigure.CALENDAR
    PlayTimeItem.WaveformCard -> PlayTimeFigure.WAVEFORM
    PlayTimeItem.MosaicCard -> PlayTimeFigure.MOSAIC
    else -> null
}

internal fun playTimeHorizontalScrubOf(item: PlayTimeItem?): PlayTimeScrub? = when (item) {
    PlayTimeItem.CalendarCard -> PlayTimeScrub.CALENDAR
    PlayTimeItem.WaveformCard -> PlayTimeScrub.HOUR
    else -> null
}

internal fun playTimeVerticalScrubOf(figure: PlayTimeFigure): PlayTimeScrub = when (figure) {
    PlayTimeFigure.CALENDAR -> PlayTimeScrub.CALENDAR
    PlayTimeFigure.WAVEFORM -> PlayTimeScrub.WEEKDAY
    PlayTimeFigure.MOSAIC -> PlayTimeScrub.MOSAIC
}

internal fun playTimeScrubStep(scrub: PlayTimeScrub, horizontal: Boolean): Int =
    if (scrub == PlayTimeScrub.CALENDAR && horizontal) DAYS_IN_WEEK else 1

private fun mosaicGames(state: PlayTimeState): List<MosaicGame> =
    state.games.mapNotNull { entry ->
        entry.key.toLongOrNull()?.let { gameId ->
            MosaicGame(
                gameId = gameId,
                title = entry.name,
                activeMs = entry.activeMs,
                lastPlayed = entry.lastPlayed,
                coverPath = state.coverPaths[gameId]
            )
        }
    }

internal fun playTimeMosaicTiles(state: PlayTimeState): List<MosaicTile> =
    if (state.mosaicFolded) {
        foldedGames(
            games = mosaicGames(state),
            maxTiles = ComponentDefaults.PlayTimeChart.mosaicMaxTiles,
            minShare = ComponentDefaults.PlayTimeChart.mosaicMinShareRatio
        )
    } else {
        foldMosaic(
            games = mosaicGames(state),
            maxTiles = ComponentDefaults.PlayTimeChart.mosaicMaxTiles,
            minShare = ComponentDefaults.PlayTimeChart.mosaicMinShareRatio
        )
    }

internal fun playTimeMosaicScrub(state: PlayTimeState): PlayTimeScrub =
    if (state.mosaicFolded) PlayTimeScrub.MOSAIC_FOLDED else PlayTimeScrub.MOSAIC

internal fun playTimeScrubSize(scrub: PlayTimeScrub, state: PlayTimeState): Int = when (scrub) {
    PlayTimeScrub.CALENDAR -> state.days.size
    PlayTimeScrub.WEEKDAY -> DAYS_IN_WEEK
    PlayTimeScrub.HOUR -> HOURS_IN_DAY
    PlayTimeScrub.MOSAIC, PlayTimeScrub.MOSAIC_FOLDED -> playTimeMosaicTiles(state).size
}

internal fun playTimeScrubInitial(scrub: PlayTimeScrub, state: PlayTimeState): Int? = when (scrub) {
    PlayTimeScrub.CALENDAR -> state.days.indexOfLast { it.activeMs > 0L }
        .takeIf { it >= 0 } ?: state.days.lastIndex.takeIf { it >= 0 }
    PlayTimeScrub.WEEKDAY -> LocalDateTime.now().dayOfWeek.value - 1
    PlayTimeScrub.HOUR -> LocalDateTime.now().hour
    PlayTimeScrub.MOSAIC, PlayTimeScrub.MOSAIC_FOLDED ->
        0.takeIf { playTimeMosaicTiles(state).isNotEmpty() }
}

internal fun playTimeScrubIndex(scrub: PlayTimeScrub, state: PlayTimeState): Int? {
    val size = playTimeScrubSize(scrub, state)
    return state.scrubs[scrub]?.takeIf { it in 0 until size } ?: playTimeScrubInitial(scrub, state)
}

@Composable
internal fun playTimeLabel(ms: Long): String =
    formatPlayTime(LocalContext.current, (ms / MS_PER_MINUTE).toInt())

@Composable
fun PlayTimeSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val playTime = uiState.playTime
    val context = LocalContext.current
    val layoutState = remember(playTime.isLoading, playTime.hasLoaded, playTime.isEmpty) {
        PlayTimeLayoutState.from(uiState)
    }
    val visibleItems = remember(layoutState) { playTimeLayout.visibleItems(layoutState) }
    val sections = remember(layoutState, context) { playTimeLayout.buildSections(layoutState, context) }
    val isOnline = uiState.server.connectionStatus == ConnectionStatus.ONLINE

    fun isFocused(item: PlayTimeItem): Boolean =
        uiState.focusedIndex == playTimeLayout.focusIndexOf(item, layoutState)

    fun isEngaged(item: PlayTimeItem): Boolean =
        playTime.engagedFigure != null && playTime.engagedFigure == playTimeFigureOf(item)

    fun focusOn(item: PlayTimeItem) {
        if (!isEngaged(item)) viewModel.setPlayTimeEngagedFigure(null)
        viewModel.setFocusIndex(playTimeLayout.focusIndexOf(item, layoutState))
    }

    fun openFrom(item: PlayTimeItem, enter: () -> Unit) {
        focusOn(item)
        enter()
    }

    SectionPaneLayout(
        items = visibleItems,
        sections = sections,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { playTimeLayout.focusToListIndex(it, layoutState) },
        itemKey = { it.key },
        isNavItem = { it is PlayTimeItem.SectionSpacer },
        isHeader = { it is PlayTimeItem.Header },
        onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        headerLock = true
    ) { item ->
        when (item) {
            is PlayTimeItem.Header -> SectionHeader(stringResource(item.titleRes))
            is PlayTimeItem.SectionSpacer -> Spacer(modifier = Modifier.height(Dimens.spacingMd))

            PlayTimeItem.LoadingState -> PlayTimeMessageCard(
                title = stringResource(R.string.settings_play_time_loading_title),
                message = stringResource(R.string.settings_play_time_loading_message),
                isWorking = true
            )

            PlayTimeItem.EmptyState -> PlayTimeMessageCard(
                title = stringResource(R.string.settings_play_time_empty_title),
                message = stringResource(R.string.settings_play_time_empty_message),
                isWorking = false
            )

            PlayTimeItem.CalendarCard -> PlayTimeCalendarCard(
                state = playTime,
                isFocused = isFocused(item),
                isEngaged = isEngaged(item),
                onFocus = { focusOn(item) },
                onCellTap = { index ->
                    focusOn(item)
                    viewModel.setPlayTimeEngagedFigure(PlayTimeFigure.CALENDAR)
                    viewModel.setPlayTimeScrub(PlayTimeScrub.CALENDAR, index)
                }
            )

            PlayTimeItem.WaveformCard -> PlayTimeWaveformCard(
                state = playTime,
                isFocused = isFocused(item),
                isEngaged = isEngaged(item),
                onFocus = { focusOn(item) },
                onCellTap = { weekday, hour ->
                    focusOn(item)
                    viewModel.setPlayTimeEngagedFigure(PlayTimeFigure.WAVEFORM)
                    viewModel.setPlayTimeScrub(PlayTimeScrub.WEEKDAY, weekday)
                    viewModel.setPlayTimeScrub(PlayTimeScrub.HOUR, hour)
                }
            )

            PlayTimeItem.PlatformBand -> PlayTimePlatformBand(state = playTime)

            PlayTimeItem.PlatformsTile -> CategoryTile(
                title = stringResource(R.string.settings_play_time_platforms_tile_title),
                icon = Icons.Default.Gamepad,
                primaryStat = playTimeLabel(playTime.platforms.sumOf { it.activeMs }),
                secondaryStat = pluralStringResource(
                    R.plurals.settings_play_time_platforms_tile_count,
                    playTime.platforms.size,
                    playTime.platforms.size
                ),
                isFocused = isFocused(item),
                onClick = { openFrom(item) { viewModel.navigateToPlayTimePlatforms() } }
            )

            PlayTimeItem.DeviceBand -> PlayTimeDeviceBand(state = playTime)

            PlayTimeItem.DevicesTile -> CategoryTile(
                title = stringResource(R.string.settings_play_time_devices_tile_title),
                icon = Icons.Default.Devices,
                primaryStat = playTimeLabel(playTime.devices.sumOf { it.activeMs }),
                secondaryStat = pluralStringResource(
                    R.plurals.settings_play_time_devices_tile_count,
                    playTime.devices.size,
                    playTime.devices.size
                ),
                isFocused = isFocused(item),
                onClick = { openFrom(item) { viewModel.navigateToPlayTimeDevices() } }
            )

            PlayTimeItem.MosaicCard -> PlayTimeMosaicCard(
                state = playTime,
                isFocused = isFocused(item),
                isEngaged = isEngaged(item),
                onFocus = { focusOn(item) },
                onTileTap = { index ->
                    focusOn(item)
                    viewModel.setPlayTimeEngagedFigure(PlayTimeFigure.MOSAIC)
                    viewModel.setPlayTimeScrub(playTimeMosaicScrub(playTime), index)
                }
            )

            PlayTimeItem.GamesTile -> CategoryTile(
                title = stringResource(R.string.settings_play_time_games_tile_title),
                icon = Icons.Default.SportsEsports,
                primaryStat = playTimeLabel(playTime.games.sumOf { it.activeMs }),
                secondaryStat = pluralStringResource(
                    R.plurals.settings_play_time_games_tile_count,
                    playTime.games.size,
                    playTime.games.size
                ),
                isFocused = isFocused(item),
                onClick = { openFrom(item) { viewModel.navigateToPlayTimeGames() } }
            )

            PlayTimeItem.FactsCard -> PlayTimeFactsCard(state = playTime)

            PlayTimeItem.RommOnServer -> InfoPreference(
                title = stringResource(R.string.settings_play_time_romm_on_server_title),
                value = playTime.sessionsOnRomm.toString(),
                isFocused = isFocused(item)
            )

            PlayTimeItem.RommPending -> InfoPreference(
                title = stringResource(R.string.settings_play_time_romm_pending_title),
                value = playTime.sessionsPending.toString(),
                subtitle = stringResource(R.string.settings_play_time_romm_pending_subtitle),
                isFocused = isFocused(item)
            )

            PlayTimeItem.RommUnlinked -> InfoPreference(
                title = stringResource(R.string.settings_play_time_romm_unlinked_title),
                value = playTime.sessionsUnlinked.toString(),
                subtitle = stringResource(R.string.settings_play_time_romm_unlinked_subtitle),
                isFocused = isFocused(item)
            )

            PlayTimeItem.RommLastUpload -> InfoPreference(
                title = stringResource(R.string.settings_play_time_romm_last_upload_title),
                value = playTime.lastUploadAt?.let { formatClockDateTime(context, it.toEpochMilli()) }
                    ?: stringResource(R.string.settings_play_time_romm_last_upload_never),
                isFocused = isFocused(item)
            )

            PlayTimeItem.RommPulled -> InfoPreference(
                title = stringResource(R.string.settings_play_time_romm_pulled_title),
                value = playTime.sessionsFromOtherDevices.toString(),
                isFocused = isFocused(item)
            )

            PlayTimeItem.RommLastRefresh -> InfoPreference(
                title = stringResource(R.string.settings_play_time_romm_last_refresh_title),
                value = playTime.lastPullAt?.let { formatClockDateTime(context, it.toEpochMilli()) }
                    ?: stringResource(R.string.settings_play_time_romm_last_refresh_never),
                isFocused = isFocused(item)
            )

            PlayTimeItem.UploadNow -> ActionPreference(
                icon = Icons.Default.CloudUpload,
                title = stringResource(R.string.settings_play_time_romm_upload_title),
                subtitle = when {
                    playTime.isUploading -> stringResource(R.string.settings_play_time_romm_upload_busy)
                    !isOnline -> stringResource(R.string.settings_play_time_romm_upload_offline)
                    playTime.uploadNotice != null -> playTime.uploadNotice.resolve()
                    else -> stringResource(R.string.settings_play_time_romm_upload_subtitle)
                },
                isFocused = isFocused(item),
                isEnabled = isOnline && !playTime.isUploading,
                spinIcon = playTime.isUploading,
                onClick = { viewModel.uploadPlaySessionsNow() }
            )

            PlayTimeItem.RefreshFromRomm -> ActionPreference(
                icon = Icons.Default.Refresh,
                title = stringResource(R.string.settings_play_time_romm_refresh_title),
                subtitle = when {
                    playTime.isPulling -> stringResource(R.string.settings_play_time_romm_refresh_busy)
                    !isOnline -> stringResource(R.string.settings_play_time_romm_refresh_offline)
                    playTime.pullNotice != null -> playTime.pullNotice.resolve()
                    else -> stringResource(R.string.settings_play_time_romm_refresh_subtitle)
                },
                isFocused = isFocused(item),
                isEnabled = isOnline && !playTime.isPulling,
                spinIcon = playTime.isPulling,
                onClick = { viewModel.refreshPlaySessionsFromRomm() }
            )
        }
    }
}
