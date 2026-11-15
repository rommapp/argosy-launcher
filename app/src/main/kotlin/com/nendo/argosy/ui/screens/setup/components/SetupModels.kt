package com.nendo.argosy.ui.screens.setup.components

import androidx.annotation.StringRes

enum class SetupDirection { FORWARD, BACK }

enum class SetupSegmentState { DONE, CURRENT, UPCOMING }

data class SetupSegment(val subTicks: Int = 1)

/**
 * One rail segment per page in this run's plan. Segment and sub-tick states derive from
 * [currentIndex] and [currentSubTick]: everything before the cursor is done, the cursor is current,
 * everything after is upcoming.
 */
data class SetupRailState(
    val segments: List<SetupSegment>,
    val currentIndex: Int,
    val currentSubTick: Int = 0,
) {
    val stepNumber: Int get() = currentIndex.coerceIn(0, (segments.size - 1).coerceAtLeast(0)) + 1
    val stepCount: Int get() = segments.size

    fun stateOf(index: Int, subTick: Int = 0): SetupSegmentState = when {
        index < currentIndex -> SetupSegmentState.DONE
        index > currentIndex -> SetupSegmentState.UPCOMING
        subTick < currentSubTick -> SetupSegmentState.DONE
        subTick > currentSubTick -> SetupSegmentState.UPCOMING
        else -> SetupSegmentState.CURRENT
    }
}

data class SetupStepLead(
    @StringRes val title: Int,
    @StringRes val purpose: Int? = null,
)

/**
 * An action-zone button. [hiddenWhenDocked] marks an action the controller keyboard's submit key
 * duplicates; it hides while the sheet is docked. [labelCount] is the format argument of a label
 * that carries a count, [labelArg] that of a label that carries a name.
 */
data class SetupAction(
    @StringRes val label: Int,
    val primary: Boolean = false,
    val focused: Boolean = false,
    val hiddenWhenDocked: Boolean = false,
    val labelCount: Int? = null,
    val labelArg: String? = null,
)

enum class SetupLayout { WIDE, STANDARD, TALL }
