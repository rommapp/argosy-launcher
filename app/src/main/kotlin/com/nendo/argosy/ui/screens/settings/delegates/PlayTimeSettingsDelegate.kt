package com.nendo.argosy.ui.screens.settings.delegates

import com.nendo.argosy.R
import com.nendo.argosy.data.model.PlayTimeSnapshot
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.repository.PlayStatsRepository
import com.nendo.argosy.data.social.uploader.PlaySessionBackfill
import com.nendo.argosy.data.social.uploader.PlaySessionPull
import com.nendo.argosy.ui.common.DisplayText
import com.nendo.argosy.ui.screens.settings.PlayTimeEntryUi
import com.nendo.argosy.ui.screens.settings.PlayTimeFigure
import com.nendo.argosy.ui.screens.settings.PlayTimeGamesSortMode
import com.nendo.argosy.ui.screens.settings.PlayTimeScrub
import com.nendo.argosy.ui.screens.settings.PlayTimeSessionUi
import com.nendo.argosy.ui.screens.settings.PlayTimeState
import com.nendo.argosy.ui.screens.settings.PlayTimeSummaryUi
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

class PlayTimeSettingsDelegate @Inject constructor(
    private val playStatsRepository: PlayStatsRepository,
    private val platformRepository: PlatformRepository,
    private val gameRepository: GameRepository,
    private val backfill: PlaySessionBackfill,
    private val pull: PlaySessionPull
) {
    private val _state = MutableStateFlow(PlayTimeState())
    val state: StateFlow<PlayTimeState> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun load(scope: CoroutineScope) {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = !it.hasLoaded, engagedFigure = null) }
        loadJob = scope.launch {
            val snapshot = playStatsRepository.loadSnapshot(
                ComponentDefaults.PlayTimeChart.calendarWeeks * DAYS_IN_WEEK
            )
            val platformNames = platformRepository.getAllPlatforms().associate { it.slug to it.name }
            val coverPaths = gameRepository.getByIds(snapshot.games.map { it.gameId })
                .mapNotNull { game -> game.coverPath?.let { game.id to it } }
                .toMap()
            _state.update { it.apply(snapshot, platformNames, coverPaths) }
        }
    }

    fun setGamesSortMode(mode: PlayTimeGamesSortMode) {
        _state.update { it.copy(gamesSortMode = mode) }
    }

    fun setScrub(scrub: PlayTimeScrub, index: Int) {
        _state.update { it.copy(scrubs = it.scrubs + (scrub to index)) }
    }

    fun setEngagedFigure(figure: PlayTimeFigure?) {
        _state.update { it.copy(engagedFigure = figure) }
    }

    fun scrub(scrub: PlayTimeScrub, direction: Int, size: Int, initial: Int?, step: Int = 1) {
        if (size <= 0) return
        _state.update { state ->
            val current = state.scrubs[scrub] ?: initial ?: 0
            state.copy(scrubs = state.scrubs + (scrub to (current + direction * step).mod(size)))
        }
    }

    fun uploadNow(scope: CoroutineScope) {
        if (_state.value.isUploading) return
        _state.update { it.copy(isUploading = true, uploadNotice = null) }
        scope.launch {
            val summary = backfill.run()
            val notice = summary.stoppedBy?.let { reason ->
                DisplayText.Res(R.string.settings_play_time_romm_upload_stopped, listOf(reason))
            } ?: DisplayText.Res(
                R.string.settings_play_time_romm_upload_result,
                listOf(summary.sent, summary.duplicates, summary.failed)
            )
            _state.update { it.copy(isUploading = false, uploadNotice = notice) }
            load(scope)
        }
    }

    fun refreshFromRomm(scope: CoroutineScope) {
        if (_state.value.isPulling) return
        _state.update { it.copy(isPulling = true, pullNotice = null) }
        scope.launch {
            val summary = pull.run()
            val notice = summary.stoppedBy?.let { reason ->
                DisplayText.Res(R.string.settings_play_time_romm_refresh_stopped, listOf(reason))
            } ?: DisplayText.Plural(
                R.plurals.settings_play_time_romm_refresh_result,
                summary.added,
                listOf(summary.added)
            )
            _state.update { it.copy(isPulling = false, pullNotice = notice) }
            load(scope)
        }
    }

    private fun PlayTimeState.apply(
        snapshot: PlayTimeSnapshot,
        platformNames: Map<String, String>,
        coverPaths: Map<Long, String>
    ): PlayTimeState {
        val deviceNames = snapshot.devices.associate { it.deviceId to "${it.deviceManufacturer} ${it.deviceModel}".trim() }
        fun deviceName(deviceId: String, manufacturer: String, model: String): String =
            deviceNames[deviceId] ?: "$manufacturer $model".trim()
        return copy(
            isLoading = false,
            hasLoaded = true,
            days = snapshot.days,
            currentStreak = snapshot.streaks.current,
            longestStreak = snapshot.streaks.longest,
            weekHourMs = snapshot.weekHourMs,
            summary = snapshot.summary?.let { summary ->
                PlayTimeSummaryUi(
                    totalActiveMs = summary.totalActiveMs,
                    platformCount = summary.platformCount,
                    platformName = platformNames[summary.topPlatformSlug] ?: summary.topPlatformSlug,
                    weekday = summary.weekday,
                    hour = summary.hour,
                    deviceName = deviceNames[summary.topDeviceId] ?: summary.topDeviceId,
                    isThisDevice = summary.topDeviceId == snapshot.localDeviceId
                )
            },
            sessions = snapshot.rangeSessions.map { session ->
                PlayTimeSessionUi(
                    id = session.id,
                    gameId = session.gameId,
                    gameTitle = session.gameTitle,
                    platformSlug = session.platformSlug,
                    platformName = platformNames[session.platformSlug] ?: session.platformSlug,
                    startTime = session.startTime,
                    activeMs = session.activePlayMs,
                    deviceName = deviceName(session.deviceId, session.deviceManufacturer, session.deviceModel),
                    isThisDevice = session.deviceId == snapshot.localDeviceId
                )
            },
            coverPaths = coverPaths,
            platforms = snapshot.platforms.map { total ->
                PlayTimeEntryUi(
                    key = total.platformSlug,
                    name = platformNames[total.platformSlug] ?: total.platformSlug,
                    platformSlug = total.platformSlug,
                    activeMs = total.activeMs,
                    sessionCount = total.sessionCount,
                    lastPlayed = total.lastPlayed
                )
            },
            devices = snapshot.devices.map { total ->
                PlayTimeEntryUi(
                    key = total.deviceId,
                    name = deviceNames.getValue(total.deviceId),
                    activeMs = total.activeMs,
                    sessionCount = total.sessionCount,
                    lastPlayed = total.lastPlayed,
                    isThisDevice = total.deviceId == snapshot.localDeviceId
                )
            },
            games = snapshot.games.map { total ->
                PlayTimeEntryUi(
                    key = total.gameId.toString(),
                    name = total.gameTitle,
                    platformName = platformNames[total.platformSlug] ?: total.platformSlug,
                    platformSlug = total.platformSlug,
                    coverPath = coverPaths[total.gameId],
                    activeMs = total.activeMs,
                    sessionCount = total.sessionCount,
                    lastPlayed = total.lastPlayed
                )
            },
            sessionCount = snapshot.sessionCount,
            sessionsOnRomm = snapshot.syncCounts.onRomm,
            sessionsPending = snapshot.syncCounts.pending,
            sessionsUnlinked = snapshot.syncCounts.unlinked,
            sessionsFromOtherDevices = snapshot.syncCounts.fromOtherDevices,
            lastUploadAt = snapshot.lastUploadAt,
            lastPullAt = snapshot.lastPullAt
        )
    }

    private companion object {
        const val DAYS_IN_WEEK = 7
    }
}
