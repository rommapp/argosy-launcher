package com.nendo.argosy.ui.screens.setup

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.SelectSwapMode
import com.nendo.argosy.domain.model.SetupFacts
import com.nendo.argosy.domain.model.SetupItem
import com.nendo.argosy.domain.model.SetupState
import com.nendo.argosy.ui.screens.setup.components.SetupRailState
import com.nendo.argosy.ui.screens.setup.components.SetupSegment

/**
 * One setup page. [seenToken] is the stored token of an optional page, written when the person
 * moves past it so a resumed run starts at the first optional page not yet seen; required pages
 * carry none.
 */
enum class SetupPage(val seenToken: String? = null) {
    WELCOME,
    LIBRARY,
    PERMISSIONS,
    ACCESSIBILITY("accessibility"),
    FOLDERS,
    SAVE_SYNC,
    PLATFORMS("platforms"),
    DEFAULT_HOME("default_home"),
    SCREENS("screens"),
    CONTROLLER("controller"),
    BUTTON_CHECK("button_check"),
    THEME("theme"),
    HOME_LAYOUT("home_layout"),
    LIBRARY_LOOK("library_look"),
    DONE;

    val optional: Boolean get() = seenToken != null
}

val CUSTOMIZATION_PAGES: List<SetupPage> = listOf(SetupPage.THEME, SetupPage.HOME_LAYOUT, SetupPage.LIBRARY_LOOK)

private val CONTROLLER_PAGES: List<SetupPage> = listOf(SetupPage.CONTROLLER, SetupPage.BUTTON_CHECK)

/**
 * The optional pages one run offers. A page added to setup gets its flag here and its place in
 * [setupPlan]. [dualScreen] adds the default-home page unless Argosy already holds the home role
 * ([homeHeld]) and, unless the person declined home, the Screens page; [buttonCheck] holds while a
 * gamepad is connected.
 */
data class SetupOffers(
    val accessibility: Boolean = true,
    val platforms: Boolean = false,
    val dualScreen: Boolean = false,
    val homeDeclined: Boolean = false,
    val homeHeld: Boolean = false,
    val buttonCheck: Boolean = false
)

fun SetupOffers.withDevice(facts: SetupFacts): SetupOffers = copy(
    dualScreen = facts.dualScreenDevice,
    homeDeclined = homeDeclined || facts.defaultHomeDeclined,
    homeHeld = facts.defaultHome
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
    if (offers.dualScreen) {
        if (!offers.homeHeld) add(SetupPage.DEFAULT_HOME)
        if (!offers.homeDeclined) add(SetupPage.SCREENS)
    }
    add(SetupPage.CONTROLLER)
    if (offers.buttonCheck) add(SetupPage.BUTTON_CHECK)
    addAll(CUSTOMIZATION_PAGES)
    add(SetupPage.DONE)
}

fun pageOwning(item: SetupItem): SetupPage = when (item) {
    SetupItem.LIBRARY_SOURCE -> SetupPage.LIBRARY
    SetupItem.ALL_FILES_ACCESS,
    SetupItem.USAGE_ACCESS,
    SetupItem.NOTIFICATIONS -> SetupPage.PERMISSIONS
    SetupItem.ROM_FOLDER -> SetupPage.FOLDERS
    SetupItem.SAVE_SYNC -> SetupPage.SAVE_SYNC
    SetupItem.DEFAULT_HOME -> SetupPage.DEFAULT_HOME
}

/**
 * Where setup opens: the page owning the first unsatisfied item, Welcome on an install that has
 * answered nothing yet, and with every required item met the first optional page of [plan] not
 * yet seen, else Done.
 */
fun entryPage(state: SetupState, facts: SetupFacts, plan: List<SetupPage>): SetupPage = when (state) {
    SetupState.Complete -> plan.firstOrNull { it.optional && it.seenToken !in facts.pagesSeen } ?: SetupPage.DONE
    is SetupState.Incomplete -> when {
        state.firstMissing == SetupItem.LIBRARY_SOURCE && !facts.librarySignedIn && !facts.anyConfirmed ->
            SetupPage.WELCOME
        else -> pageOwning(state.firstMissing)
    }
}

/**
 * The seen tokens Skip customization writes: every customization page, so a resumed run never
 * returns to one.
 */
val CUSTOMIZATION_SEEN_TOKENS: Set<String> = CUSTOMIZATION_PAGES.mapNotNull { it.seenToken }.toSet()

/**
 * The page Next leads to. A required item on an earlier page that has become unsatisfied since
 * wins over the following page, so Next never carries the person past it. Pages compare in
 * declaration order, the order every plan keeps, so a page the plan dropped while it was open
 * still leads to the page after it.
 */
fun nextPage(current: SetupPage, plan: List<SetupPage>, state: SetupState): SetupPage {
    earlierBlocker(current, plan, state)?.let { return it }
    return plan.firstOrNull { it > current } ?: current
}

fun previousPage(current: SetupPage, plan: List<SetupPage>): SetupPage =
    plan.lastOrNull { it < current } ?: current

/**
 * The page a setup composed again mid-run opens on: the [carried] page it stood on, unless that
 * page left the plan or a required item on an earlier page went missing, which win as they do for
 * [entryPage] and [nextPage].
 */
fun resumePage(entry: SetupPage, carried: SetupPage?, plan: List<SetupPage>, state: SetupState): SetupPage {
    if (carried == null || carried !in plan) return entry
    return earlierBlocker(carried, plan, state) ?: carried
}

private fun earlierBlocker(current: SetupPage, plan: List<SetupPage>, state: SetupState): SetupPage? {
    val blocker = (state as? SetupState.Incomplete)?.let { pageOwning(it.firstMissing) } ?: return null
    return blocker.takeIf { it in plan && it < current }
}

/**
 * Whether the Select role swap is live on [state]'s page. Setup holds it off until the Screens
 * page and lets it through from there on.
 */
fun setupSwapAllowed(state: SetupUiState): Boolean {
    val screensIndex = state.plan.indexOf(SetupPage.SCREENS)
    return screensIndex >= 0 && state.plan.indexOf(state.page) >= screensIndex
}

@StringRes
fun setupSwapFooterLabel(mode: SelectSwapMode): Int = when (mode) {
    SelectSwapMode.HOLD -> R.string.setup_screens_footer_hold_swap
    SelectSwapMode.TAP -> R.string.setup_screens_footer_swap
}

private fun SetupPage.segmentGroup(): List<SetupPage>? = when (this) {
    in CUSTOMIZATION_PAGES -> CUSTOMIZATION_PAGES
    in CONTROLLER_PAGES -> CONTROLLER_PAGES
    else -> null
}

/**
 * The rail for [page]: one segment per page, except that the controller pages and the three
 * customization pages each share one segment, one sub-tick per page.
 */
fun railFor(page: SetupPage, plan: List<SetupPage>): SetupRailState {
    val groups = mutableListOf<List<SetupPage>>()
    plan.forEach { planned ->
        val group = planned.segmentGroup()?.filter { it in plan } ?: listOf(planned)
        if (groups.lastOrNull() != group) groups.add(group)
    }
    val index = groups.indexOfFirst { page in it }
    return SetupRailState(
        segments = groups.map { SetupSegment(subTicks = it.size) },
        currentIndex = if (page == SetupPage.WELCOME) -1 else index,
        currentSubTick = groups.getOrNull(index)?.indexOf(page)?.coerceAtLeast(0) ?: 0
    )
}
