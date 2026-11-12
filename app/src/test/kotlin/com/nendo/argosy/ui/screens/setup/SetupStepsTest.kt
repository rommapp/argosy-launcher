package com.nendo.argosy.ui.screens.setup

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

    @Test
    fun `the plan carries optional pages only when offered`() {
        assertEquals(
            listOf(
                SetupPage.WELCOME, SetupPage.LIBRARY, SetupPage.PERMISSIONS, SetupPage.ACCESSIBILITY,
                SetupPage.FOLDERS, SetupPage.SAVE_SYNC, SetupPage.PLATFORMS, SetupPage.DONE
            ),
            fullPlan
        )
        val bare = setupPlan(SetupOffers(accessibility = false, platforms = false))
        assertFalse(SetupPage.ACCESSIBILITY in bare)
        assertFalse(SetupPage.PLATFORMS in bare)
        assertEquals(SetupPage.DONE, bare.last())
    }

    @Test
    fun `platforms are offered above ten platforms or when built-in emulators are missing`() {
        assertFalse(offersPlatforms(platformCount = 10, missingCoreCount = 0))
        assertTrue(offersPlatforms(platformCount = 11, missingCoreCount = 0))
        assertTrue(offersPlatforms(platformCount = 3, missingCoreCount = 1))
    }

    @Test
    fun `a fresh install opens on welcome`() {
        assertEquals(SetupPage.WELCOME, entryPage(missing(SetupItem.LIBRARY_SOURCE), SetupFacts()))
    }

    @Test
    fun `a library signed in or answered before opens on the library page`() {
        assertEquals(
            SetupPage.LIBRARY,
            entryPage(missing(SetupItem.LIBRARY_SOURCE), SetupFacts(librarySignedIn = true))
        )
        assertEquals(
            SetupPage.LIBRARY,
            entryPage(missing(SetupItem.LIBRARY_SOURCE), SetupFacts(saveSyncAnswered = true))
        )
    }

    @Test
    fun `each missing item opens the page that owns it`() {
        val facts = SetupFacts(librarySignedIn = true, librarySourceConfirmed = true)
        assertEquals(SetupPage.PERMISSIONS, entryPage(missing(SetupItem.ALL_FILES_ACCESS), facts))
        assertEquals(SetupPage.PERMISSIONS, entryPage(missing(SetupItem.USAGE_ACCESS), facts))
        assertEquals(SetupPage.PERMISSIONS, entryPage(missing(SetupItem.NOTIFICATIONS), facts))
        assertEquals(SetupPage.FOLDERS, entryPage(missing(SetupItem.ROM_FOLDER), facts))
        assertEquals(SetupPage.SAVE_SYNC, entryPage(missing(SetupItem.SAVE_SYNC), facts))
        assertEquals(SetupPage.DONE, entryPage(missing(SetupItem.DEFAULT_HOME), facts))
    }

    @Test
    fun `next follows the plan while nothing earlier is missing`() {
        assertEquals(SetupPage.ACCESSIBILITY, nextPage(SetupPage.PERMISSIONS, fullPlan, SetupState.Complete))
        assertEquals(SetupPage.PLATFORMS, nextPage(SetupPage.SAVE_SYNC, fullPlan, SetupState.Complete))
        assertEquals(SetupPage.DONE, nextPage(SetupPage.PLATFORMS, fullPlan, SetupState.Complete))
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
        assertEquals(SetupPage.SAVE_SYNC, previousPage(SetupPage.DONE, setupPlan(SetupOffers(platforms = false))))
        fullPlan.drop(1).forEach { page ->
            assertTrue("$page went back", fullPlan.indexOf(previousPage(page, fullPlan)) < fullPlan.indexOf(page))
        }
    }

    @Test
    fun `the rail counts every planned page and leaves welcome empty`() {
        val welcome = railFor(SetupPage.WELCOME, fullPlan)
        assertEquals(fullPlan.size, welcome.stepCount)
        assertTrue(fullPlan.indices.all { welcome.stateOf(it) == SetupSegmentState.UPCOMING })

        val library = railFor(SetupPage.LIBRARY, fullPlan)
        assertEquals(2, library.stepNumber)
        assertEquals(SetupSegmentState.DONE, library.stateOf(0))
        assertEquals(SetupSegmentState.CURRENT, library.stateOf(1))
    }
}
