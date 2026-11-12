package com.nendo.argosy.ui.dualscreen

import com.nendo.argosy.domain.model.HomeLayoutSettings

@JvmInline
value class SlotOwner(val id: String) {
    companion object {
        /**
         * An owner unique to [holder], for a screen that more than one surface can host at once.
         */
        fun of(name: String, holder: Any): SlotOwner = SlotOwner("$name@${System.identityHashCode(holder)}")
    }
}

/**
 * What the presentation screen shows while a screen on the control surface is open.
 *
 * Every case is authored beside the screen that publishes it; there is no generic payload a host
 * could render by guessing. A screen with nothing worth showing publishes nothing and the host
 * falls back.
 */
sealed interface PresentationSlot {
    data object Fallback : PresentationSlot

    data class HomeLayoutPreview(val settings: HomeLayoutSettings) : PresentationSlot

    data class Setup(val state: com.nendo.argosy.ui.screens.setup.components.SetupCompanionState) : PresentationSlot

    data class PlayTime(
        val sectionLabel: String,
        val games: List<PlayTimeSlotGame>,
        val dateLabel: String? = null
    ) : PresentationSlot

    data class PlayTimeline(
        val dots: List<TimelineDot>,
        val selectedIndex: Int,
        val dayLabel: String,
        val dayTotal: String?,
        val games: List<PlayTimeSlotGame>
    ) : PresentationSlot

    data class PlayShare(
        val title: String,
        val totalLabel: String,
        val subtitle: String,
        val rows: List<PlayShareRow>
    ) : PresentationSlot

    data class Detail(val detail: CompanionDetail) : PresentationSlot

    data class Breakdown(
        val title: String,
        val subtitle: String?,
        val rows: List<BreakdownRow>
    ) : PresentationSlot

    data class PlatformShowcase(
        val name: String,
        val yearSpan: String?,
        val coverPaths: List<String>,
        val facts: List<CompanionFact>
    ) : PresentationSlot

    data class MediaPlayback(val itemId: String) : PresentationSlot

    data class InGame(
        val state: com.nendo.argosy.hardware.CompanionInGameState,
        val achievements: List<com.nendo.argosy.core.game.AchievementUi>
    ) : PresentationSlot

    data class SaveGame(
        val title: String,
        val subtitle: String,
        val totalLabel: String?,
        val coverPath: String?,
        val backgroundPath: String?,
        val days: List<com.nendo.argosy.data.model.PlayDay>,
        val saveDayIndices: Set<Int>,
        val weekHourMs: List<List<Long>>,
        val peakLabel: String?,
        val devices: List<SaveGameDevice>,
        val totalDeviceMs: Long,
        val deviceSegments: List<Pair<androidx.compose.ui.graphics.Color, Long>>
    ) : PresentationSlot
}

data class SaveGameDevice(
    val label: String,
    val kind: com.nendo.argosy.domain.model.DeviceKind,
    val activeMs: Long,
    val valueLabel: String,
    val shareLabel: String,
    val detail: String,
    val color: androidx.compose.ui.graphics.Color,
    val isThisDevice: Boolean
)

data class BreakdownRow(
    val label: String,
    val value: String,
    val fraction: Float,
    val color: androidx.compose.ui.graphics.Color
)

data class PlayTimeSlotGame(
    val gameId: Long,
    val title: String,
    val coverPath: String?,
    val detail: String,
    val subtitle: String? = null
)

data class PlayShareRow(
    val label: String,
    val valueLabel: String,
    val shareLabel: String,
    val fraction: Float,
    val color: androidx.compose.ui.graphics.Color,
    val iconModel: Any?,
    val showsCover: Boolean,
    val coverPath: String?,
    val topGames: List<PlayTimeSlotGame>
)

/**
 * The slot a presentation surface on [displayId] draws: [PresentationSlot.Fallback] in place of
 * the in-game panel unless the game is known to run on another display, and in place of the
 * media panel on the display the player occupies.
 */
fun PresentationSlot.onDisplay(
    displayId: Int?,
    gameDisplayId: Int?,
    playerDisplayId: Int? = null
): PresentationSlot = when {
    this is PresentationSlot.InGame && (gameDisplayId == null || displayId == gameDisplayId) ->
        PresentationSlot.Fallback
    this is PresentationSlot.MediaPlayback && playerDisplayId != null && displayId == playerDisplayId ->
        PresentationSlot.Fallback
    else -> this
}

data class TimelineDot(
    val hasActivity: Boolean,
    val color: androidx.compose.ui.graphics.Color,
    val label: String?
)
