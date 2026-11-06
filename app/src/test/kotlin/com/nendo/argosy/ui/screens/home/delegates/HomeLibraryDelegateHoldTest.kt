package com.nendo.argosy.ui.screens.home.delegates

import android.content.Context
import com.nendo.argosy.data.emulator.LaunchProgressTracker
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.RecentNewGamesLimit
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.LibraryImportTracker
import com.nendo.argosy.domain.model.LaunchStep
import com.nendo.argosy.ui.screens.common.GradientExtractionDelegate
import com.nendo.argosy.ui.screens.home.HomeRow
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalCoroutinesApi::class)
class HomeLibraryDelegateHoldTest {
    private val now = Instant.now()
    private val older = game(1L, lastPlayed = now.minus(2, ChronoUnit.DAYS))
    private val newer = game(2L, lastPlayed = now.minus(1, ChronoUnit.DAYS))
    private val downloaded = game(3L, lastPlayed = null)

    private val played = MutableStateFlow(listOf(older, newer))
    private var newlyAdded = emptyList<GameEntity>()
    private var prefs = UserPreferences()
    private val importing = MutableStateFlow(false)

    private val gameRepository = mockk<GameRepository>(relaxed = true).also {
        coEvery { it.awaitStorageReady(any()) } returns true
        every { it.observeRecentlyPlayed(any()) } returns played
        coEvery { it.getRecentlyPlayed(any()) } answers { played.value }
        coEvery { it.getNewlyAdded(any(), any(), any()) } answers { newlyAdded.take(thirdArg<Int>()) }
        coEvery { it.getArt(any<Collection<Long>>()) } returns emptyMap()
    }

    private fun game(id: Long, lastPlayed: Instant?) = GameEntity(
        id = id,
        platformId = 1L,
        platformSlug = "snes",
        title = "Game $id",
        sortTitle = "game $id",
        localPath = "/roms/game$id.sfc",
        rommId = null,
        igdbId = null,
        source = GameSource.ROMM_SYNCED,
        lastPlayed = lastPlayed,
        addedAt = if (lastPlayed != null) now.minus(3, ChronoUnit.DAYS) else now
    )

    private fun TestScope.delegate(tracker: LaunchProgressTracker) = HomeLibraryDelegate(
        context = mockk<Context>(relaxed = true),
        preferencesRepository = mockk<UserPreferencesRepository>(relaxed = true).also {
            every { it.userPreferences } answers { flowOf(prefs) }
        },
        gameRepository = gameRepository,
        platformRepository = mockk(relaxed = true),
        generateRecommendationsUseCase = mockk(relaxed = true),
        getPinnedCollectionsUseCase = mockk(relaxed = true),
        getGamesForPinnedCollectionUseCase = mockk(relaxed = true),
        gradientExtractionDelegate = mockk<GradientExtractionDelegate>(relaxed = true).also {
            every { it.getGradient(any()) } returns null
        },
        emulatorDetector = mockk(relaxed = true),
        notificationManager = mockk(relaxed = true),
        repairImageCacheUseCase = mockk(relaxed = true),
        downloadFileStatusRepository = mockk(relaxed = true),
        steamPathResolver = mockk(relaxed = true),
        collectionRepository = mockk(relaxed = true),
        appsRepository = mockk(relaxed = true),
        launchProgressTracker = tracker,
        libraryImportTracker = LibraryImportTracker(backgroundScope, listOf(importing))
    )

    private fun TestScope.observing(tracker: LaunchProgressTracker): HomeLibraryDelegate {
        val delegate = delegate(tracker)
        delegate.observeRecentlyPlayedChanges(backgroundScope) {}
        runCurrent()
        return delegate
    }

    private fun HomeLibraryDelegate.shownIds() = state.value.recentGames.map { it.id }

    @Test
    fun `a Continue refresh during a launch waits and shows once the launch is over`() = runTest {
        val tracker = LaunchProgressTracker(backgroundScope)
        val delegate = observing(tracker)
        assertEquals(listOf(2L, 1L), delegate.shownIds())

        val ticket = tracker.begin(1L, "Game 1")!!
        ticket.step(LaunchStep.PreparingUi)
        runCurrent()
        newlyAdded = listOf(downloaded)

        val result = delegate.refreshCurrentRow(HomeRow.Continue, focusedGameId = null)
        runCurrent()
        assertEquals(listOf(2L, 1L), result.gameIds)
        assertFalse(result.isEmpty)
        assertEquals(listOf(2L, 1L), delegate.shownIds())

        tracker.finish(ticket, launched = true)
        runCurrent()
        assertEquals(listOf(2L, 1L), delegate.shownIds())

        advanceTimeBy(8_001)
        runCurrent()
        assertEquals(listOf(3L, 2L, 1L), delegate.shownIds())
    }

    @Test
    fun `a recent-games load during a launch waits and shows once the launch is over`() = runTest {
        val tracker = LaunchProgressTracker(backgroundScope)
        val delegate = observing(tracker)

        val ticket = tracker.begin(1L, "Game 1")!!
        ticket.step(LaunchStep.PreparingUi)
        runCurrent()
        newlyAdded = listOf(downloaded)
        delegate.invalidateRecentGamesCache()

        delegate.loadRecentGames()
        runCurrent()
        assertEquals(listOf(2L, 1L), delegate.shownIds())

        tracker.finish(ticket, launched = false)
        runCurrent()
        assertEquals(listOf(3L, 2L, 1L), delegate.shownIds())
    }

    @Test
    fun `the play-start stamp re-sorts the row when the launch clears, not when the session ends`() = runTest {
        val tracker = LaunchProgressTracker(backgroundScope)
        val delegate = observing(tracker)

        val ticket = tracker.begin(1L, "Game 1")!!
        ticket.step(LaunchStep.PreparingUi)
        runCurrent()
        played.value = listOf(older.copy(lastPlayed = now), newer)
        runCurrent()
        assertEquals(listOf(2L, 1L), delegate.shownIds())

        tracker.finish(ticket, launched = true)
        tracker.hostStopped()
        runCurrent()
        assertEquals(listOf(1L, 2L), delegate.shownIds())
    }

    @Test
    fun `the play-start stamp of a launch with no step yet does not move the row`() = runTest {
        val tracker = LaunchProgressTracker(backgroundScope)
        val delegate = observing(tracker)

        val ticket = tracker.begin(1L, "Game 1")!!
        played.value = listOf(older.copy(lastPlayed = now), newer)
        runCurrent()
        assertEquals(listOf(2L, 1L), delegate.shownIds())

        tracker.finish(ticket, launched = false)
        runCurrent()
        assertEquals(listOf(1L, 2L), delegate.shownIds())
    }

    @Test
    fun `a Continue refresh during a launch with no step yet waits for the launch to clear`() = runTest {
        val tracker = LaunchProgressTracker(backgroundScope)
        val delegate = observing(tracker)

        val ticket = tracker.begin(1L, "Game 1")!!
        runCurrent()
        newlyAdded = listOf(downloaded)

        val result = delegate.refreshCurrentRow(HomeRow.Continue, focusedGameId = null)
        runCurrent()
        assertEquals(listOf(2L, 1L), result.gameIds)
        assertFalse(result.isEmpty)
        assertEquals(listOf(2L, 1L), delegate.shownIds())

        tracker.finish(ticket, launched = false)
        runCurrent()
        assertEquals(listOf(3L, 2L, 1L), delegate.shownIds())
    }

    @Test
    fun `a game screen that never opens releases the row when the launch clears`() = runTest {
        val tracker = LaunchProgressTracker(backgroundScope)
        val delegate = observing(tracker)

        val ticket = tracker.begin(1L, "Game 1")!!
        played.value = listOf(older.copy(lastPlayed = now), newer)
        tracker.finish(ticket, launched = true)
        runCurrent()
        assertEquals(listOf(2L, 1L), delegate.shownIds())

        tracker.openFailed(ticket)
        runCurrent()
        assertEquals(listOf(1L, 2L), delegate.shownIds())
    }

    @Test
    fun `with its observer gone a Continue refresh during a launch reads the database at once`() = runTest {
        val tracker = LaunchProgressTracker(backgroundScope)
        val delegate = delegate(tracker)
        val observer = backgroundScope.launch { delegate.observeRecentlyPlayedChanges(this) {} }
        runCurrent()
        assertEquals(listOf(2L, 1L), delegate.shownIds())
        observer.cancel()
        runCurrent()

        tracker.begin(1L, "Game 1")!!
        newlyAdded = listOf(downloaded)

        val result = delegate.refreshCurrentRow(HomeRow.Continue, focusedGameId = null)

        assertEquals(listOf(3L, 2L, 1L), result.gameIds)
        assertEquals(listOf(3L, 2L, 1L), delegate.shownIds())
    }

    @Test
    fun `with no observer started a recent-games load during a launch reads the database at once`() = runTest {
        val tracker = LaunchProgressTracker(backgroundScope)
        val delegate = delegate(tracker)
        tracker.begin(1L, "Game 1")!!
        newlyAdded = listOf(downloaded)

        delegate.loadRecentGames()

        assertEquals(listOf(3L, 2L, 1L), delegate.shownIds())
    }

    @Test
    fun `a deferred Continue refresh of an empty row reports the row empty`() = runTest {
        played.value = emptyList()
        val tracker = LaunchProgressTracker(backgroundScope)
        val delegate = observing(tracker)

        tracker.begin(1L, "Game 1")!!
        runCurrent()
        newlyAdded = listOf(downloaded)

        val result = delegate.refreshCurrentRow(HomeRow.Continue, focusedGameId = null)

        assertEquals(emptyList<Long>(), result.gameIds)
        assertTrue(result.isEmpty)
        assertEquals(emptyList<Long>(), delegate.shownIds())
    }

    @Test
    fun `with no launch a Continue refresh reads the database at once`() = runTest {
        val delegate = observing(LaunchProgressTracker(backgroundScope))
        newlyAdded = listOf(downloaded)

        delegate.refreshCurrentRow(HomeRow.Continue, focusedGameId = null)

        assertEquals(listOf(3L, 2L, 1L), delegate.shownIds())
    }

    @Test
    fun `games written during a library import reach the row once the import ends`() = runTest {
        val delegate = observing(LaunchProgressTracker(backgroundScope))
        importing.value = true
        runCurrent()

        newlyAdded = newGames(2)
        played.value = listOf(older, newer.copy(title = "Game 2 updated"))
        runCurrent()
        newlyAdded = newGames(4)
        played.value = listOf(older, newer)
        runCurrent()
        assertEquals(listOf(2L, 1L), delegate.shownIds())

        val result = delegate.refreshCurrentRow(HomeRow.Continue, focusedGameId = null)
        runCurrent()
        assertEquals(listOf(2L, 1L), result.gameIds)
        assertEquals(listOf(2L, 1L), delegate.shownIds())

        importing.value = false
        runCurrent()
        assertEquals(listOf(100L, 101L, 102L, 103L, 2L, 1L), delegate.shownIds())
    }

    private fun newGames(count: Int) = (100L until 100L + count).map { id ->
        game(id, lastPlayed = null).copy(addedAt = now.minusSeconds(id))
    }

    @Test
    fun `the new-games limit caps how many newly added games join the row`() = runTest {
        prefs = UserPreferences(recentNewGamesLimit = 3)
        val delegate = observing(LaunchProgressTracker(backgroundScope))
        newlyAdded = newGames(8)

        delegate.refreshCurrentRow(HomeRow.Continue, focusedGameId = null)

        assertEquals(listOf(100L, 101L, 102L, 2L, 1L), delegate.shownIds())
    }

    @Test
    fun `a new-games limit of zero keeps newly added games out of the row`() = runTest {
        prefs = UserPreferences(recentNewGamesLimit = 0)
        val delegate = observing(LaunchProgressTracker(backgroundScope))
        newlyAdded = newGames(8)

        delegate.loadRecentGames()

        assertEquals(listOf(2L, 1L), delegate.shownIds())
    }

    @Test
    fun `the All new-games limit lets every newly added game join the row`() = runTest {
        prefs = UserPreferences(recentNewGamesLimit = RecentNewGamesLimit.ALL)
        val delegate = observing(LaunchProgressTracker(backgroundScope))
        newlyAdded = newGames(8)

        delegate.refreshCurrentRow(HomeRow.Continue, focusedGameId = null)

        assertEquals(newGames(8).map { it.id } + listOf(2L, 1L), delegate.shownIds())
    }
}
