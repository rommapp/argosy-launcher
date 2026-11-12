package com.nendo.argosy.ui.screens.setup

import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.SelectSwapMode
import com.nendo.argosy.domain.model.SetupFacts
import com.nendo.argosy.domain.model.SetupItem
import com.nendo.argosy.domain.model.SetupState
import com.nendo.argosy.ui.screens.setup.components.SetupSegmentState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupStepsTest {

    private fun missing(item: SetupItem) = SetupState.Incomplete(item)

    private val fullPlan = setupPlan(SetupOffers(accessibility = true, platforms = true))

    private val customization = listOf(SetupPage.THEME, SetupPage.HOME_LAYOUT, SetupPage.LIBRARY_LOOK)

    @Test
    fun `the plan carries optional pages only when offered`() {
        assertEquals(
            listOf(
                SetupPage.WELCOME, SetupPage.LIBRARY, SetupPage.PERMISSIONS, SetupPage.ACCESSIBILITY,
                SetupPage.FOLDERS, SetupPage.SAVE_SYNC, SetupPage.PLATFORMS, SetupPage.CONTROLLER
            ) + customization + SetupPage.DONE,
            fullPlan
        )
        val bare = setupPlan(SetupOffers(accessibility = false, platforms = false))
        assertFalse(SetupPage.ACCESSIBILITY in bare)
        assertFalse(SetupPage.PLATFORMS in bare)
        assertEquals(SetupPage.DONE, bare.last())
    }

    @Test
    fun `a single-screen device never sees the home or screens pages`() {
        val single = setupPlan(SetupOffers(dualScreen = false, buttonCheck = true))
        assertFalse(SetupPage.DEFAULT_HOME in single)
        assertFalse(SetupPage.SCREENS in single)
    }

    @Test
    fun `a dual-screen device asks for home right before screens`() {
        val dual = setupPlan(SetupOffers(dualScreen = true, buttonCheck = true))
        assertEquals(dual.indexOf(SetupPage.DEFAULT_HOME) + 1, dual.indexOf(SetupPage.SCREENS))
        assertEquals(dual.indexOf(SetupPage.SCREENS) + 1, dual.indexOf(SetupPage.CONTROLLER))
    }

    @Test
    fun `declining home skips the screens page and keeps the home page`() {
        val declined = setupPlan(SetupOffers(dualScreen = true, homeDeclined = true))
        assertTrue(SetupPage.DEFAULT_HOME in declined)
        assertFalse(SetupPage.SCREENS in declined)
        assertEquals(declined.indexOf(SetupPage.DEFAULT_HOME) + 1, declined.indexOf(SetupPage.CONTROLLER))
    }

    @Test
    fun `the button check is planned only while a gamepad is connected`() {
        val withPad = setupPlan(SetupOffers(buttonCheck = true))
        assertEquals(withPad.indexOf(SetupPage.CONTROLLER) + 1, withPad.indexOf(SetupPage.BUTTON_CHECK))
        val noPad = setupPlan(SetupOffers(buttonCheck = false))
        assertTrue(SetupPage.CONTROLLER in noPad)
        assertFalse(SetupPage.BUTTON_CHECK in noPad)
    }

    @Test
    fun `a dual-screen device that already has Argosy as home skips the home page and keeps screens`() {
        val held = setupPlan(SetupOffers(dualScreen = true, homeHeld = true))
        assertFalse(SetupPage.DEFAULT_HOME in held)
        assertTrue(SetupPage.SCREENS in held)
        assertEquals(held.indexOf(SetupPage.SAVE_SYNC) + 1, held.indexOf(SetupPage.SCREENS))
        assertTrue(SetupPage.DEFAULT_HOME in setupPlan(SetupOffers(dualScreen = true, homeHeld = false)))
    }

    @Test
    fun `the home role held is read from the device facts`() {
        assertTrue(SetupOffers().withDevice(SetupFacts(dualScreenDevice = true, defaultHome = true)).homeHeld)
        assertFalse(SetupOffers(homeHeld = true).withDevice(SetupFacts(dualScreenDevice = true)).homeHeld)
    }

    @Test
    fun `a page the plan dropped while open still leads forward and back`() {
        val held = setupPlan(SetupOffers(dualScreen = true, homeHeld = true))
        assertEquals(SetupPage.SCREENS, nextPage(SetupPage.DEFAULT_HOME, held, SetupState.Complete))
        assertEquals(SetupPage.SAVE_SYNC, previousPage(SetupPage.DEFAULT_HOME, held))
    }

    @Test
    fun `a setup composed again opens on the page it stood on`() {
        val dual = setupPlan(SetupOffers(dualScreen = true, homeHeld = true))
        assertEquals(SetupPage.SCREENS, resumePage(SetupPage.CONTROLLER, SetupPage.SCREENS, dual, SetupState.Complete))
        assertEquals(SetupPage.SCREENS, resumePage(SetupPage.SAVE_SYNC, SetupPage.SCREENS, dual, SetupState.Complete))
        assertEquals(SetupPage.CONTROLLER, resumePage(SetupPage.CONTROLLER, null, dual, SetupState.Complete))
    }

    @Test
    fun `a carried page outside the plan falls back to the entry page`() {
        val plan = setupPlan(SetupOffers(platforms = false))
        assertEquals(SetupPage.SAVE_SYNC, resumePage(SetupPage.SAVE_SYNC, SetupPage.PLATFORMS, plan, SetupState.Complete))
    }

    @Test
    fun `an earlier missing item wins over the carried page, a later one does not`() {
        assertEquals(
            SetupPage.PERMISSIONS,
            resumePage(SetupPage.PERMISSIONS, SetupPage.FOLDERS, fullPlan, missing(SetupItem.USAGE_ACCESS))
        )
        assertEquals(
            SetupPage.PERMISSIONS,
            resumePage(SetupPage.SAVE_SYNC, SetupPage.PERMISSIONS, fullPlan, missing(SetupItem.SAVE_SYNC))
        )
        assertEquals(
            SetupPage.FOLDERS,
            resumePage(SetupPage.FOLDERS, SetupPage.FOLDERS, fullPlan, missing(SetupItem.ROM_FOLDER))
        )
    }

    @Test
    fun `a stored decline sticks even before the offers saw it`() {
        val offers = SetupOffers(homeDeclined = true).withDevice(SetupFacts(dualScreenDevice = true))
        assertTrue(offers.dualScreen)
        assertTrue(offers.homeDeclined)
        assertTrue(SetupOffers().withDevice(SetupFacts(defaultHomeDeclined = true)).homeDeclined)
    }

    private val requiredMet = SetupFacts(
        librarySignedIn = true,
        librarySourceConfirmed = true,
        allFilesAccess = true,
        usageAccess = true,
        notifications = true,
        romFolderPresent = true,
        romFolderConfirmed = true,
        saveSyncAnswered = true
    )

    @Test
    fun `required items met resume at the first optional page not yet seen`() {
        val plan = setupPlan(SetupOffers(accessibility = false, buttonCheck = true))
        assertEquals(SetupPage.CONTROLLER, entryPage(SetupState.Complete, requiredMet, plan))
        val controllerSeen = requiredMet.copy(pagesSeen = setOf("controller"))
        assertEquals(SetupPage.BUTTON_CHECK, entryPage(SetupState.Complete, controllerSeen, plan))
        val allSeen = requiredMet.copy(pagesSeen = plan.mapNotNull { it.seenToken }.toSet())
        assertEquals(SetupPage.DONE, entryPage(SetupState.Complete, allSeen, plan))
    }

    @Test
    fun `skipping customization marks all three pages seen`() {
        assertEquals(customization.mapNotNull { it.seenToken }.toSet(), CUSTOMIZATION_SEEN_TOKENS)
        val plan = setupPlan(SetupOffers(accessibility = false))
        val skipped = requiredMet.copy(pagesSeen = setOf("controller") + CUSTOMIZATION_SEEN_TOKENS)
        assertEquals(SetupPage.DONE, entryPage(SetupState.Complete, skipped, plan))
    }

    @Test
    fun `required pages carry no seen token and optional pages carry distinct ones`() {
        val required = listOf(
            SetupPage.WELCOME, SetupPage.LIBRARY, SetupPage.PERMISSIONS, SetupPage.FOLDERS,
            SetupPage.SAVE_SYNC, SetupPage.DONE
        )
        required.forEach { assertFalse("$it is optional", it.optional) }
        assertTrue(SetupPage.DEFAULT_HOME.optional)
        val tokens = SetupPage.entries.mapNotNull { it.seenToken }
        assertEquals(tokens.size, tokens.toSet().size)
    }

    @Test
    fun `platforms are offered above ten platforms or when built-in emulators are missing`() {
        assertFalse(offersPlatforms(platformCount = 10, missingCoreCount = 0))
        assertTrue(offersPlatforms(platformCount = 11, missingCoreCount = 0))
        assertTrue(offersPlatforms(platformCount = 3, missingCoreCount = 1))
    }

    @Test
    fun `a fresh install opens on welcome`() {
        assertEquals(SetupPage.WELCOME, entryPage(missing(SetupItem.LIBRARY_SOURCE), SetupFacts(), fullPlan))
    }

    @Test
    fun `a library signed in or answered before opens on the library page`() {
        assertEquals(
            SetupPage.LIBRARY,
            entryPage(missing(SetupItem.LIBRARY_SOURCE), SetupFacts(librarySignedIn = true), fullPlan)
        )
        assertEquals(
            SetupPage.LIBRARY,
            entryPage(missing(SetupItem.LIBRARY_SOURCE), SetupFacts(saveSyncAnswered = true), fullPlan)
        )
    }

    @Test
    fun `each missing item opens the page that owns it`() {
        val facts = SetupFacts(librarySignedIn = true, librarySourceConfirmed = true)
        assertEquals(SetupPage.PERMISSIONS, entryPage(missing(SetupItem.ALL_FILES_ACCESS), facts, fullPlan))
        assertEquals(SetupPage.PERMISSIONS, entryPage(missing(SetupItem.USAGE_ACCESS), facts, fullPlan))
        assertEquals(SetupPage.PERMISSIONS, entryPage(missing(SetupItem.NOTIFICATIONS), facts, fullPlan))
        assertEquals(SetupPage.FOLDERS, entryPage(missing(SetupItem.ROM_FOLDER), facts, fullPlan))
        assertEquals(SetupPage.SAVE_SYNC, entryPage(missing(SetupItem.SAVE_SYNC), facts, fullPlan))
        assertEquals(SetupPage.DEFAULT_HOME, entryPage(missing(SetupItem.DEFAULT_HOME), facts, fullPlan))
    }

    @Test
    fun `next follows the plan while nothing earlier is missing`() {
        assertEquals(SetupPage.ACCESSIBILITY, nextPage(SetupPage.PERMISSIONS, fullPlan, SetupState.Complete))
        assertEquals(SetupPage.PLATFORMS, nextPage(SetupPage.SAVE_SYNC, fullPlan, SetupState.Complete))
        assertEquals(SetupPage.CONTROLLER, nextPage(SetupPage.PLATFORMS, fullPlan, SetupState.Complete))
        assertEquals(SetupPage.DONE, nextPage(SetupPage.LIBRARY_LOOK, fullPlan, SetupState.Complete))
        assertEquals(SetupPage.DONE, nextPage(SetupPage.DONE, fullPlan, SetupState.Complete))
    }

    @Test
    fun `next returns to an earlier page whose required item went missing`() {
        assertEquals(
            SetupPage.PERMISSIONS,
            nextPage(SetupPage.FOLDERS, fullPlan, missing(SetupItem.USAGE_ACCESS))
        )
    }

    @Test
    fun `next ignores a missing item owned by the current or a later page`() {
        assertEquals(
            SetupPage.FOLDERS,
            nextPage(SetupPage.ACCESSIBILITY, fullPlan, missing(SetupItem.ROM_FOLDER))
        )
        assertEquals(
            SetupPage.SAVE_SYNC,
            nextPage(SetupPage.FOLDERS, fullPlan, missing(SetupItem.ROM_FOLDER))
        )
    }

    @Test
    fun `back steps to the previous planned page and stays on welcome`() {
        assertEquals(SetupPage.WELCOME, previousPage(SetupPage.WELCOME, fullPlan))
        assertEquals(SetupPage.SAVE_SYNC, previousPage(SetupPage.PLATFORMS, fullPlan))
        assertEquals(SetupPage.SAVE_SYNC, previousPage(SetupPage.CONTROLLER, setupPlan(SetupOffers(platforms = false))))
        assertEquals(SetupPage.LIBRARY_LOOK, previousPage(SetupPage.DONE, fullPlan))
        fullPlan.drop(1).forEach { page ->
            assertTrue("$page went back", fullPlan.indexOf(previousPage(page, fullPlan)) < fullPlan.indexOf(page))
        }
    }

    @Test
    fun `the rail counts every planned page and leaves welcome empty`() {
        val welcome = railFor(SetupPage.WELCOME, fullPlan)
        assertEquals(fullPlan.size - customization.size + 1, welcome.stepCount)
        assertTrue((0 until welcome.stepCount).all { welcome.stateOf(it) == SetupSegmentState.UPCOMING })

        val library = railFor(SetupPage.LIBRARY, fullPlan)
        assertEquals(2, library.stepNumber)
        assertEquals(SetupSegmentState.DONE, library.stateOf(0))
        assertEquals(SetupSegmentState.CURRENT, library.stateOf(1))
    }

    @Test
    fun `the customization pages share one rail segment with a tick each`() {
        val theme = railFor(SetupPage.THEME, fullPlan)
        val home = railFor(SetupPage.HOME_LAYOUT, fullPlan)
        val library = railFor(SetupPage.LIBRARY_LOOK, fullPlan)
        assertEquals(theme.currentIndex, home.currentIndex)
        assertEquals(home.currentIndex, library.currentIndex)
        assertEquals(3, theme.segments[theme.currentIndex].subTicks)
        assertEquals(listOf(0, 1, 2), listOf(theme, home, library).map { it.currentSubTick })
    }

    @Test
    fun `the select footer hint names the hold only when holding swaps`() {
        assertEquals(R.string.setup_screens_footer_hold_swap, setupSwapFooterLabel(SelectSwapMode.HOLD))
        assertEquals(R.string.setup_screens_footer_swap, setupSwapFooterLabel(SelectSwapMode.TAP))
    }

    @Test
    fun `the controller and button check share one rail segment`() {
        val plan = setupPlan(SetupOffers(buttonCheck = true))
        val controller = railFor(SetupPage.CONTROLLER, plan)
        val check = railFor(SetupPage.BUTTON_CHECK, plan)
        assertEquals(controller.currentIndex, check.currentIndex)
        assertEquals(2, check.segments[check.currentIndex].subTicks)
        assertEquals(1, check.currentSubTick)
    }
}
