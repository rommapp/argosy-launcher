package com.nendo.argosy.ui.common.achievements

import com.nendo.argosy.core.game.AchievementUi
import com.nendo.argosy.data.repository.RAWebApiKeyCheck
import com.nendo.argosy.data.repository.RAWebApiKeyRepository
import com.nendo.argosy.data.repository.RaMetadataSource
import com.nendo.argosy.domain.model.AchievementComment
import com.nendo.argosy.domain.model.AchievementComments
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class AchievementCommentsControllerTest {

    private val achievement = AchievementUi(raId = 9L, title = "First", description = null, points = 5, type = null, badgeUrl = null)
    private val source: RaMetadataSource = mockk()
    private val keyRepository: RAWebApiKeyRepository = mockk()
    private val loaded = AchievementComments.Loaded(
        listOf(AchievementComment("A", "2026-10-01T00:00:00Z", "one"), AchievementComment("B", "2026-09-01T00:00:00Z", "two"))
    )

    @Test
    fun `opening loads the achievement's comments`() = runTest {
        coEvery { source.achievementComments(9L) } returns loaded
        val controller = AchievementCommentsController.Factory(source, keyRepository).create(this)

        controller.open(achievement)
        advanceUntilIdle()

        assertEquals(loaded, controller.state.value?.content)
    }

    @Test
    fun `scrolling stays inside the loaded comments`() = runTest {
        coEvery { source.achievementComments(9L) } returns loaded
        val controller = AchievementCommentsController.Factory(source, keyRepository).create(this)
        controller.open(achievement)
        advanceUntilIdle()

        controller.scroll(-1)
        assertEquals(0, controller.state.value?.scrollIndex)
        controller.scroll(5)
        assertEquals(1, controller.state.value?.scrollIndex)
    }

    @Test
    fun `a saved key closes the entry and loads the comments`() = runTest {
        coEvery { source.achievementComments(9L) } returnsMany listOf(AchievementComments.NeedsKey, loaded)
        coEvery { keyRepository.validateAndSave("abc") } returns RAWebApiKeyCheck.SAVED
        val controller = AchievementCommentsController.Factory(source, keyRepository).create(this)
        controller.open(achievement)
        advanceUntilIdle()

        controller.openKeyEntry()
        controller.setKeyDraft("abc")
        controller.submitKey()
        advanceUntilIdle()

        val state = controller.state.value!!
        assertFalse(state.keyEntryOpen)
        assertEquals("", state.keyDraft)
        assertNull(state.keyStatus)
        assertEquals(loaded, state.content)
    }

    @Test
    fun `a refused key keeps the entry open with the reason`() = runTest {
        coEvery { source.achievementComments(9L) } returns AchievementComments.NeedsKey
        coEvery { keyRepository.validateAndSave("bad") } returns RAWebApiKeyCheck.REJECTED
        val controller = AchievementCommentsController.Factory(source, keyRepository).create(this)
        controller.open(achievement)
        advanceUntilIdle()

        controller.openKeyEntry()
        controller.setKeyDraft("bad")
        controller.submitKey()
        advanceUntilIdle()

        val state = controller.state.value!!
        assertEquals(CommentsKeyStatus.REJECTED, state.keyStatus)
        assertEquals("bad", state.keyDraft)
        coVerify(exactly = 1) { source.achievementComments(9L) }
    }

    @Test
    fun `a blank draft is never sent`() = runTest {
        coEvery { source.achievementComments(9L) } returns AchievementComments.NeedsKey
        val controller = AchievementCommentsController.Factory(source, keyRepository).create(this)
        controller.open(achievement)
        advanceUntilIdle()

        controller.openKeyEntry()
        controller.setKeyDraft("  ")
        controller.submitKey()
        advanceUntilIdle()

        coVerify(exactly = 0) { keyRepository.validateAndSave(any()) }
    }
}
