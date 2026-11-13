package com.nendo.argosy.ui.screens.stream

import androidx.annotation.StringRes
import com.nendo.argosy.data.streaming.StreamPhase
import com.nendo.argosy.data.streaming.StreamSession
import com.nendo.argosy.ui.dualscreen.dashboard.DashboardQuickState
import com.nendo.argosy.ui.dualscreen.dashboard.SessionControls

enum class StreamControlRow { RESUME, SAVE_STATE, LOAD_STATE, NEXT_DISC, SAVE_AND_EXIT, LEAVE }

enum class StreamPlayerStage { LAUNCHING, PLAYING, ENDED }

data class StreamPlayerUiState(
    val title: String = "",
    val containerLabel: String = "",
    val stage: StreamPlayerStage = StreamPlayerStage.LAUNCHING,
    val preparing: Boolean = false,
    val launchFailed: Boolean = false,
    val pageRevealed: Boolean = false,
    val controlsOpen: Boolean = false,
    val controlsFocusIndex: Int = 0,
    val rows: List<StreamControlRow> = emptyList(),
    @StringRes val notice: Int? = null
) {
    val focusedRow: StreamControlRow? get() = rows.getOrNull(controlsFocusIndex)

    val capturesInput: Boolean get() = controlsOpen || stage != StreamPlayerStage.PLAYING

    companion object {
        fun rowsFor(session: StreamSession): List<StreamControlRow> = buildList {
            add(StreamControlRow.RESUME)
            if (session.supportsStates) {
                add(StreamControlRow.SAVE_STATE)
                add(StreamControlRow.LOAD_STATE)
            }
            if (session.canSwapDisc) add(StreamControlRow.NEXT_DISC)
            if (session.isHost) add(StreamControlRow.SAVE_AND_EXIT) else add(StreamControlRow.LEAVE)
        }

        fun stageOf(phase: StreamPhase, pageRevealed: Boolean): StreamPlayerStage = when (phase) {
            StreamPhase.LAUNCHING -> StreamPlayerStage.LAUNCHING
            StreamPhase.PLAYING -> if (pageRevealed) StreamPlayerStage.PLAYING else StreamPlayerStage.LAUNCHING
            StreamPhase.ENDED -> StreamPlayerStage.ENDED
        }

        fun sessionControlsFor(session: StreamSession): SessionControls = SessionControls(
            quickStates = session.lastStateSavedAtMillis
                ?.takeIf { session.supportsStates }
                ?.let { listOf(DashboardQuickState(session.stateSlot, it, null)) }
                .orEmpty(),
            cheatsAvailable = false,
            settingsAvailable = true,
            screenshotAvailable = false,
            discSwapAvailable = session.canSwapDisc,
            statesAvailable = session.supportsStates,
            achievementsAvailable = false
        )
    }
}

object StreamPageReveal {
    const val POLL_MS = 250L
    const val SETTLE_MS = 1_000L
    const val TIMEOUT_MS = 20_000L

    fun shouldReveal(loadStartedAtMillis: Long, videoSinceMillis: Long?, nowMillis: Long): Boolean {
        if (nowMillis - loadStartedAtMillis >= TIMEOUT_MS) return true
        return videoSinceMillis != null && nowMillis - videoSinceMillis >= SETTLE_MS
    }
}
