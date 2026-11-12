package com.nendo.argosy.ui.screens.setup

import com.nendo.argosy.domain.model.SetupFacts
import com.nendo.argosy.domain.model.SetupItem
import com.nendo.argosy.domain.model.SetupState
import com.nendo.argosy.ui.screens.setup.components.SetupRailState
import com.nendo.argosy.ui.screens.setup.components.SetupSegment

enum class SetupPage {
    WELCOME,
    LIBRARY,
    PERMISSIONS,
    ACCESSIBILITY,
    FOLDERS,
    SAVE_SYNC,
    PLATFORMS,
    DONE
}

/**
 * The optional pages one run offers. A page added to setup gets its flag here and its place in
 * [setupPlan].
 */
data class SetupOffers(
    val accessibility: Boolean = true,
    val platforms: Boolean = false
)

internal const val PLATFORM_SELECT_THRESHOLD = 10

internal fun offersPlatforms(platformCount: Int, missingCoreCount: Int): Boolean =
    platformCount > PLATFORM_SELECT_THRESHOLD || missingCoreCount > 0

/**
 * The ordered pages of one run. Required pages are always present; [offers] decides the optional
 * ones. The rail, the step count and Back and Next all read this list, so a page enters setup
 * here and nowhere else.
 */
fun setupPlan(offers: SetupOffers): List<SetupPage> = buildList {
    add(SetupPage.WELCOME)
    add(SetupPage.LIBRARY)
    add(SetupPage.PERMISSIONS)
    if (offers.accessibility) add(SetupPage.ACCESSIBILITY)
    add(SetupPage.FOLDERS)
    add(SetupPage.SAVE_SYNC)
    if (offers.platforms) add(SetupPage.PLATFORMS)
    add(SetupPage.DONE)
}

fun pageOwning(item: SetupItem): SetupPage = when (item) {
    SetupItem.LIBRARY_SOURCE -> SetupPage.LIBRARY
    SetupItem.ALL_FILES_ACCESS,
    SetupItem.USAGE_ACCESS,
    SetupItem.NOTIFICATIONS -> SetupPage.PERMISSIONS
    SetupItem.ROM_FOLDER -> SetupPage.FOLDERS
    SetupItem.SAVE_SYNC -> SetupPage.SAVE_SYNC
    SetupItem.DEFAULT_HOME -> SetupPage.DONE
}

/**
 * Where setup opens: the page owning the first unsatisfied item, or Welcome on an install that
 * has answered nothing yet.
 */
fun entryPage(state: SetupState, facts: SetupFacts): SetupPage = when (state) {
    SetupState.Complete -> SetupPage.WELCOME
    is SetupState.Incomplete -> when {
        state.firstMissing == SetupItem.LIBRARY_SOURCE && !facts.librarySignedIn && !facts.anyConfirmed ->
            SetupPage.WELCOME
        else -> pageOwning(state.firstMissing)
    }
}

/**
 * The page Next leads to. A required item on an earlier page that has become unsatisfied since
 * wins over the following page, so Next never carries the person past it.
 */
fun nextPage(current: SetupPage, plan: List<SetupPage>, state: SetupState): SetupPage {
    val index = plan.indexOf(current)
    val blocker = (state as? SetupState.Incomplete)?.let { pageOwning(it.firstMissing) }
    if (blocker != null && plan.indexOf(blocker) in 0 until index) return blocker
    return plan.getOrNull(index + 1) ?: current
}

fun previousPage(current: SetupPage, plan: List<SetupPage>): SetupPage =
    plan.getOrNull(plan.indexOf(current) - 1) ?: current

fun railFor(page: SetupPage, plan: List<SetupPage>): SetupRailState = SetupRailState(
    segments = plan.map { SetupSegment() },
    currentIndex = if (page == SetupPage.WELCOME) -1 else plan.indexOf(page)
)
