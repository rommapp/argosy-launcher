package com.nendo.argosy.ui.screens.stream

import androidx.annotation.StringRes

/**
 * The stream choice a game offers beside Download. [isLive] is true while this game's stream is
 * already running, when the row resumes it.
 */
data class StreamOffer(val containerLabel: String, val isLive: Boolean)

data class StreamOptionUi(
    val id: Long?,
    val title: String,
    val timestampMillis: Long?,
    val owner: String?
)

enum class StreamLaunchRow { STATE, SAVE, START, CANCEL, JOIN, BACK, IMPORT_CARD, FRESH_CARD, DISMISS }

sealed interface StreamLaunchStep {
    data object Loading : StreamLaunchStep

    data object Pick : StreamLaunchStep

    data object Starting : StreamLaunchStep

    data class Busy(
        val romName: String?,
        val sinceMillis: Long?,
        val draining: Boolean,
        val canJoin: Boolean
    ) : StreamLaunchStep

    data class CardDecision(
        val unreadable: Boolean,
        val fileCount: Int,
        val gameCodes: List<String>,
        val reason: String?
    ) : StreamLaunchStep

    data class Notice(@StringRes val message: Int, val detail: String?) : StreamLaunchStep
}

data class StreamLaunchUiState(
    val gameId: Long,
    val title: String,
    val containerLabel: String,
    val step: StreamLaunchStep = StreamLaunchStep.Loading,
    val states: List<StreamOptionUi> = emptyList(),
    val stateIndex: Int = 0,
    val saves: List<StreamOptionUi> = emptyList(),
    val saveIndex: Int = 0,
    val showsStatePicker: Boolean = false,
    val showsSavePicker: Boolean = false,
    val focusIndex: Int = 0
) {
    val rows: List<StreamLaunchRow>
        get() = when (val current = step) {
            StreamLaunchStep.Loading -> listOf(StreamLaunchRow.CANCEL)
            StreamLaunchStep.Starting -> emptyList()
            StreamLaunchStep.Pick -> buildList {
                if (showsStatePicker) add(StreamLaunchRow.STATE)
                if (showsSavePicker) add(StreamLaunchRow.SAVE)
                add(StreamLaunchRow.START)
                add(StreamLaunchRow.CANCEL)
            }
            is StreamLaunchStep.Busy -> buildList {
                if (current.canJoin) add(StreamLaunchRow.JOIN)
                add(StreamLaunchRow.BACK)
            }
            is StreamLaunchStep.CardDecision -> buildList {
                if (!current.unreadable) add(StreamLaunchRow.IMPORT_CARD)
                add(StreamLaunchRow.FRESH_CARD)
                add(StreamLaunchRow.BACK)
            }
            is StreamLaunchStep.Notice -> listOf(StreamLaunchRow.DISMISS)
        }

    val focusedRow: StreamLaunchRow? get() = rows.getOrNull(focusIndex)
}
