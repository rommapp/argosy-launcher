package com.nendo.argosy.ui.screens.stream

import androidx.annotation.StringRes
import com.nendo.argosy.data.streaming.StreamPhase
import com.nendo.argosy.data.streaming.StreamSession

enum class StreamControlRow { RESUME, SAVE_STATE, LOAD_STATE, NEXT_DISC, MUTE, SAVE_AND_EXIT, LEAVE }

enum class StreamPlayerStage { LAUNCHING, PLAYING, ENDED }

data class StreamPlayerUiState(
    val title: String = "",
    val containerLabel: String = "",
    val stage: StreamPlayerStage = StreamPlayerStage.LAUNCHING,
    val preparing: Boolean = false,
    val launchFailed: Boolean = false,
    val controlsOpen: Boolean = false,
    val controlsFocusIndex: Int = 0,
    val rows: List<StreamControlRow> = emptyList(),
    val muted: Boolean = false,
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
            if (session.isHost) {
                add(StreamControlRow.MUTE)
                add(StreamControlRow.SAVE_AND_EXIT)
            } else {
                add(StreamControlRow.LEAVE)
            }
        }

        fun stageOf(phase: StreamPhase): StreamPlayerStage = when (phase) {
            StreamPhase.LAUNCHING -> StreamPlayerStage.LAUNCHING
            StreamPhase.PLAYING -> StreamPlayerStage.PLAYING
            StreamPhase.ENDED -> StreamPlayerStage.ENDED
        }
    }
}
