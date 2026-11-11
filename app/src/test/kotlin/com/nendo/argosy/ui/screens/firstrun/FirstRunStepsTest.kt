package com.nendo.argosy.ui.screens.firstrun

import com.nendo.argosy.domain.model.SetupFacts
import com.nendo.argosy.domain.model.SetupItem
import com.nendo.argosy.domain.model.SetupState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstRunStepsTest {

    private fun missing(item: SetupItem) = SetupState.Incomplete(item)

    @Test
    fun `a fresh install opens on welcome`() {
        assertEquals(FirstRunStep.WELCOME, entryStepFor(missing(SetupItem.LIBRARY_SOURCE), SetupFacts()))
    }

    @Test
    fun `a signed-in library still to confirm opens on its signed-in page`() {
        assertEquals(
            FirstRunStep.ROMM_SUCCESS,
            entryStepFor(missing(SetupItem.LIBRARY_SOURCE), SetupFacts(librarySignedIn = true))
        )
    }

    @Test
    fun `a signed-out library after earlier answers opens on sign-in`() {
        assertEquals(
            FirstRunStep.ROMM_LOGIN,
            entryStepFor(missing(SetupItem.LIBRARY_SOURCE), SetupFacts(saveSyncAnswered = true))
        )
    }

    @Test
    fun `each missing item opens the step that owns it`() {
        val facts = SetupFacts(librarySignedIn = true, librarySourceConfirmed = true)
        assertEquals(FirstRunStep.PERMISSIONS, entryStepFor(missing(SetupItem.ALL_FILES_ACCESS), facts))
        assertEquals(FirstRunStep.PERMISSIONS, entryStepFor(missing(SetupItem.USAGE_ACCESS), facts))
        assertEquals(FirstRunStep.PERMISSIONS, entryStepFor(missing(SetupItem.NOTIFICATIONS), facts))
        assertEquals(FirstRunStep.ROM_PATH, entryStepFor(missing(SetupItem.ROM_FOLDER), facts))
        assertEquals(FirstRunStep.SAVE_SYNC, entryStepFor(missing(SetupItem.SAVE_SYNC), facts))
    }

    @Test
    fun `save sync sits between the image cache and the platform list`() {
        assertEquals(FirstRunStep.SAVE_SYNC, stepAfter(FirstRunStep.IMAGE_CACHE, platformCount = 3))
        assertEquals(FirstRunStep.CORE_PROMPT, stepAfter(FirstRunStep.SAVE_SYNC, platformCount = 3))
        assertEquals(FirstRunStep.PLATFORM_SELECT, stepAfter(FirstRunStep.SAVE_SYNC, platformCount = 11))
        assertEquals(FirstRunStep.IMAGE_CACHE, stepBefore(FirstRunStep.SAVE_SYNC, platformCount = 11))
        assertEquals(FirstRunStep.SAVE_SYNC, stepBefore(FirstRunStep.CORE_PROMPT, platformCount = 3))
        assertEquals(FirstRunStep.PLATFORM_SELECT, stepBefore(FirstRunStep.CORE_PROMPT, platformCount = 11))
    }

    @Test
    fun `every step but welcome goes back to an earlier step`() {
        FirstRunStep.entries.filter { it != FirstRunStep.WELCOME }.forEach { step ->
            listOf(3, 11).forEach { count ->
                val previous = stepBefore(step, count)
                assertTrue("$step went back to $previous", previous.ordinal < step.ordinal)
            }
        }
    }
}
