package com.nendo.argosy.ui.quickmenu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class QuickMenuStateTest {

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 17)

    private fun at(daysAgo: Long, hour: Int = 12): Instant =
        today.minusDays(daysAgo).atTime(hour, 0).toInstant(zone)

    private fun game(
        id: Long,
        title: String = "Game $id",
        lastPlayed: Instant? = null,
        addedAt: Instant = Instant.EPOCH,
        platformName: String? = "SNES",
        isFavorite: Boolean = false
    ) = QuickMenuGameUi(
        id = id,
        title = title,
        platformId = 1,
        platformSlug = "snes",
        platformName = platformName,
        coverPath = null,
        backdropPath = null,
        backdropIsScreenshotOrArt = false,
        year = null,
        developer = null,
        genre = null,
        rating = null,
        description = null,
        isDownloaded = true,
        needsInstall = false,
        isFavorite = isFavorite,
        playTimeMinutes = 0,
        lastPlayed = lastPlayed,
        addedAt = addedAt
    )

    private fun recentState(vararg ids: Long) = QuickMenuUiState(
        recent = RecentState(
            rows = ids.map { RecentRowUi(game(it), RecentGroup.TODAY, isRunning = false) },
            isLoaded = true
        )
    )

    @Test
    fun `sections wrap in both directions`() {
        val first = QuickMenuUiState(selectedOrb = QuickMenuOrb.SEARCH)
        assertEquals(QuickMenuOrb.FAVORITES, first.movingSection(-1).selectedOrb)
        val last = QuickMenuUiState(selectedOrb = QuickMenuOrb.FAVORITES)
        assertEquals(QuickMenuOrb.SEARCH, last.movingSection(1).selectedOrb)
        assertEquals(QuickMenuOrb.MOST_PLAYED, first.movingSection(2).selectedOrb)
    }

    @Test
    fun `each section keeps its own focus index across switches`() {
        val state = recentState(1, 2, 3)
            .withFocusIndex(QuickMenuOrb.RECENT, 2)
            .withFocusIndex(QuickMenuOrb.FAVORITES, 1)
        val away = state.movingSection(1)
        assertEquals(QuickMenuOrb.FAVORITES, away.selectedOrb)
        assertEquals(1, away.focusIndex)
        val back = away.movingSection(-1)
        assertEquals(QuickMenuOrb.RECENT, back.selectedOrb)
        assertEquals(2, back.focusIndex)
        assertEquals(3L, back.focusedGame()?.id)
    }

    @Test
    fun `first open of a session lands on recent`() {
        val opened = QuickMenuUiState(selectedOrb = QuickMenuOrb.FAVORITES, picker = QuickMenuPicker.SORT).opened(firstOpen = true)
        assertTrue(opened.isVisible)
        assertNull(opened.picker)
        assertEquals(QuickMenuOrb.RECENT, opened.selectedOrb)
    }

    @Test
    fun `bumpers onto search focus the field and open nothing`() {
        val state = QuickMenuUiState(selectedOrb = QuickMenuOrb.FAVORITES).withFocusIndex(QuickMenuOrb.SEARCH, 3)
        val onSearch = state.movingSection(1)
        assertEquals(QuickMenuOrb.SEARCH, onSearch.selectedOrb)
        assertEquals(SEARCH_FIELD_INDEX, onSearch.focusIndex)
    }

    @Test
    fun `reopen keeps the last section`() {
        val opened = QuickMenuUiState(selectedOrb = QuickMenuOrb.FAVORITES).opened(firstOpen = false)
        assertEquals(QuickMenuOrb.FAVORITES, opened.selectedOrb)
    }

    @Test
    fun `reopen left on search lands on recent`() {
        val opened = QuickMenuUiState(selectedOrb = QuickMenuOrb.SEARCH).opened(firstOpen = false)
        assertEquals(QuickMenuOrb.RECENT, opened.selectedOrb)
    }

    @Test
    fun `reload keeps focus on the same game after it moves`() {
        val before = recentState(1, 2, 3).withFocusIndex(QuickMenuOrb.RECENT, 1)
        val after = before.replacingSection(QuickMenuOrb.RECENT) {
            it.copy(recent = recentState(9, 4, 2, 1).recent)
        }
        assertEquals(2, after.focusIndexOf(QuickMenuOrb.RECENT))
        assertEquals(2L, after.focusedGame(QuickMenuOrb.RECENT)?.id)
    }

    @Test
    fun `reload clamps focus when the focused game is gone`() {
        val before = recentState(1, 2, 3).withFocusIndex(QuickMenuOrb.RECENT, 2)
        val after = before.replacingSection(QuickMenuOrb.RECENT) {
            it.copy(recent = recentState(7, 8).recent)
        }
        assertEquals(1, after.focusIndexOf(QuickMenuOrb.RECENT))
    }

    @Test
    fun `reload into an empty section resets focus`() {
        assertEquals(0, reconcileFocus(previousId = 5L, previousIndex = 3, ids = emptyList()))
    }

    private fun rolled(vararg ids: Long): RandomState =
        ids.fold(RandomState(platforms = listOf(RandomPlatform(1, "SNES"), RandomPlatform(4, "GBA")))) { state, id ->
            state.withRoll(game(id))
        }

    @Test
    fun `random history keeps the last five rolls and counts every roll`() {
        val state = (1L..7L).fold(RandomState()) { random, id -> random.withRoll(game(id)) }
        assertEquals(listOf(3L, 4L, 5L, 6L, 7L), state.rolls.map { it.id })
        assertEquals(7, state.rollNumber)
        assertEquals(3, state.picking(0).rollNumber)
    }

    @Test
    fun `an empty roll keeps the history and disables play and roll again`() {
        val state = rolled(1).focusing(RandomStop.ROLL).withRoll(null)
        assertEquals(1L, state.current?.id)
        assertTrue(state.isPoolEmpty)
        assertFalse(state.isEnabled(RandomStop.PLAY))
        assertFalse(state.isEnabled(RandomStop.ROLL))
        assertEquals(RandomStop.DETAILS, state.focus)
        assertEquals(RandomStop.PLATFORM, RandomState().withRoll(null).focus)
    }

    @Test
    fun `random filters follow platform and installed only`() {
        val state = rolled(1)
        assertFalse(state.filters.downloadedOnly)
        assertTrue(state.filters.platformIds.isEmpty())
        val gba = state.cyclingPlatform(2).withInstalledOnly(true)
        assertEquals(setOf(4L), gba.filters.platformIds)
        assertTrue(gba.filters.downloadedOnly)
        assertEquals("GBA", gba.platformName)
    }

    @Test
    fun `platform cycling wraps through all platforms`() {
        val state = rolled(1)
        assertEquals(0, state.platformIndex)
        assertEquals(4L, state.cyclingPlatform(-1).platformId)
        assertNull(state.cyclingPlatform(3).platformId)
        val dropped = state.cyclingPlatform(2).withPlatforms(listOf(RandomPlatform(1, "SNES")))
        assertNull(dropped.platformId)
    }

    @Test
    fun `a pool change keeps a pick that still fits and drops one that does not`() {
        val state = rolled(1)
        assertTrue(state.pickFitsPool)
        assertTrue(state.cyclingPlatform(1).pickFitsPool)
        assertFalse(state.cyclingPlatform(2).pickFitsPool)
        val notInstalled = RandomState().withRoll(game(2).copy(isDownloaded = false))
        assertFalse(notInstalled.withInstalledOnly(true).pickFitsPool)
    }

    @Test
    fun `random buttons move only sideways and down reaches the current pick`() {
        val state = rolled(1, 2, 3).entered()
        assertEquals(RandomStop.PLAY, state.focus)
        assertNull(state.stepped(FocusStep.UP, portrait = false))
        assertNull(state.stepped(FocusStep.LEFT, portrait = false))
        val roll = state.stepped(FocusStep.RIGHT, portrait = false)!!
        assertEquals(RandomStop.ROLL, roll.focus)
        val favorite = roll.stepped(FocusStep.RIGHT, portrait = false)!!.stepped(FocusStep.RIGHT, portrait = false)!!
        assertEquals(RandomStop.FAVORITE, favorite.focus)
        assertNull(favorite.stepped(FocusStep.RIGHT, portrait = false))
        val history = roll.stepped(FocusStep.DOWN, portrait = false)!!
        assertEquals(RandomStop.HISTORY, history.focus)
        assertEquals(history.rollIndex, history.historyFocus)
        assertEquals(RandomStop.ROLL, history.stepped(FocusStep.UP, portrait = false)?.focus)
    }

    @Test
    fun `random history clamps and leads to the pool controls`() {
        val history = rolled(1, 2, 3).entered().focusing(RandomStop.HISTORY)
        assertNull(history.stepped(FocusStep.RIGHT, portrait = false))
        assertEquals(1, history.stepped(FocusStep.LEFT, portrait = false)?.historyFocus)
        val platform = history.stepped(FocusStep.DOWN, portrait = false)!!
        assertEquals(RandomStop.PLATFORM, platform.focus)
        val installed = platform.stepped(FocusStep.DOWN, portrait = false)!!
        assertEquals(RandomStop.INSTALLED, installed.focus)
        assertNull(installed.stepped(FocusStep.DOWN, portrait = false))
        assertEquals(RandomStop.HISTORY, platform.stepped(FocusStep.UP, portrait = false)?.focus)
    }

    @Test
    fun `picking a history tile makes it the pick and focuses play`() {
        val picked = rolled(1, 2, 3).focusingHistory(0).picking(0)
        assertEquals(1L, picked.current?.id)
        assertEquals(RandomStop.PLAY, picked.focus)
        assertEquals(1L, rolled(1, 2, 3).focusingHistory(0).focusedGame?.id)
    }

    @Test
    fun `portrait random puts play above the other buttons`() {
        val play = rolled(1).entered()
        assertNull(play.stepped(FocusStep.LEFT, portrait = true))
        val roll = play.stepped(FocusStep.DOWN, portrait = true)!!
        assertEquals(RandomStop.ROLL, roll.focus)
        val details = roll.stepped(FocusStep.RIGHT, portrait = true)!!
        assertEquals(RandomStop.DETAILS, details.focus)
        assertEquals(RandomStop.PLAY, details.stepped(FocusStep.UP, portrait = true)?.focus)
        assertEquals(RandomStop.HISTORY, details.stepped(FocusStep.DOWN, portrait = true)?.focus)
        assertNull(roll.stepped(FocusStep.LEFT, portrait = true))
    }

    @Test
    fun `installed only starts on when offline or metered`() {
        assertFalse(defaultInstalledOnly(isOnline = true, isMetered = false))
        assertTrue(defaultInstalledOnly(isOnline = false, isMetered = false))
        assertTrue(defaultInstalledOnly(isOnline = true, isMetered = true))
    }

    @Test
    fun `unfavoriting a favorite keeps its tile in place and restores on the second press`() {
        val favorites = FavoritesState(games = listOf(game(1), game(2), game(3)), isLoaded = true)
        val removed = favorites.markingFavorite(2L, isFavorite = false)
        assertEquals(listOf(1L, 2L, 3L), removed.games.map { it.id })
        assertEquals(setOf(2L), removed.removedIds)
        val restored = removed.markingFavorite(2L, isFavorite = true)
        assertEquals(listOf(1L, 2L, 3L), restored.games.map { it.id })
        assertTrue(restored.removedIds.isEmpty())
    }

    @Test
    fun `favorites count leaves out removed tiles`() {
        val state = QuickMenuUiState(
            favorites = FavoritesState(games = listOf(game(1), game(2)), isLoaded = true)
                .markingFavorite(2L, isFavorite = false)
                .markingFavorite(9L, isFavorite = false)
        )
        assertEquals(1, state.favorites.count)
    }

    @Test
    fun `up from the top of favorites reaches the sort control and down returns to the column`() {
        val games = (1L..12L).map { game(it) }
        val state = QuickMenuUiState(
            selectedOrb = QuickMenuOrb.FAVORITES,
            formFactor = QuickMenuFormFactor.DUAL_SCREEN,
            favorites = FavoritesState(games = games, isLoaded = true)
        ).withFocusIndex(QuickMenuOrb.FAVORITES, 3)
        val header = state.stepped(FocusStep.UP)!!
        assertTrue(header.isHeaderFocused)
        assertNull(header.actionGame())
        assertNull(header.stepped(FocusStep.UP))
        val back = header.stepped(FocusStep.DOWN)!!
        assertFalse(back.isHeaderFocused)
        assertEquals(3, back.focusIndex)
    }

    @Test
    fun `up from the top of recent and top new stays put`() {
        assertNull(recentState(1, 2).copy(selectedOrb = QuickMenuOrb.RECENT).stepped(FocusStep.UP))
        assertNull(recentState(1, 2).copy(selectedOrb = QuickMenuOrb.RECENT).stepped(FocusStep.LEFT))
        val topNew = QuickMenuUiState(
            selectedOrb = QuickMenuOrb.TOP_UNPLAYED,
            topNew = TopNewState(tiles = listOf(TopNewTileUi(game(1), null), TopNewTileUi(game(2), null)), isLoaded = true)
        )
        assertNull(topNew.stepped(FocusStep.UP))
        assertEquals(1, topNew.stepped(FocusStep.RIGHT)?.focusIndex)
    }

    @Test
    fun `search tab steps from the field through the recent searches`() {
        val state = QuickMenuUiState(
            selectedOrb = QuickMenuOrb.SEARCH,
            search = SearchTabState(
                recentSearches = listOf(RecentSearchUi("zelda", 14, emptyList()), RecentSearchUi("rpg", 3, emptyList())),
                isLoaded = true
            )
        )
        assertNull(state.stepped(FocusStep.UP))
        val first = state.stepped(FocusStep.DOWN)!!
        assertEquals("zelda", first.focusedSearch?.query)
        val second = first.stepped(FocusStep.DOWN)!!
        assertNull(second.stepped(FocusStep.DOWN))
        assertNull(second.focusedGame())
        assertEquals(SEARCH_FIELD_INDEX, first.stepped(FocusStep.UP)?.focusIndex)
    }

    @Test
    fun `search tab lists recently added games when nothing was searched`() {
        val state = QuickMenuUiState(
            selectedOrb = QuickMenuOrb.SEARCH,
            search = SearchTabState(recentlyAdded = listOf(game(5), game(6)), isLoaded = true)
        ).withFocusIndex(QuickMenuOrb.SEARCH, 2)
        assertTrue(state.search.showsRecentlyAdded)
        assertEquals(6L, state.focusedGame()?.id)
        assertNull(state.focusedSearch)
    }

    @Test
    fun `a game with no play time and no last played reads as never played`() {
        assertTrue(game(1).isNeverPlayed)
        assertFalse(game(1, lastPlayed = at(1)).isNeverPlayed)
        assertFalse(game(1).copy(playTimeMinutes = 5).isNeverPlayed)
    }

    @Test
    fun `favorites sort cycles and focus follows the game`() {
        val games = listOf(
            game(1, title = "Beta", addedAt = Instant.ofEpochSecond(30)),
            game(2, title = "Alpha", addedAt = Instant.ofEpochSecond(10)),
            game(3, title = "Gamma", addedAt = Instant.ofEpochSecond(20))
        )
        val state = QuickMenuUiState(
            selectedOrb = QuickMenuOrb.FAVORITES,
            favorites = FavoritesState(games = sortFavorites(games, FavoritesSort.RECENTLY_ADDED), isLoaded = true)
        ).withFocusIndex(QuickMenuOrb.FAVORITES, 0)
        assertEquals(1L, state.focusedGame()?.id)
        val sorted = state.replacingSection(QuickMenuOrb.FAVORITES) { it.copy(favorites = it.favorites.cyclingSort()) }
        assertEquals(FavoritesSort.TITLE, sorted.favorites.sort)
        assertEquals(listOf(2L, 1L, 3L), sorted.favorites.games.map { it.id })
        assertEquals(1L, sorted.focusedGame()?.id)
    }

    @Test
    fun `most played ranks by time with every arrangement of a pair`() {
        val a = PlayTally(1L, 120, at(3), "A")
        val b = PlayTally(2L, 60, at(1), "B")
        assertEquals(listOf(1L, 2L), rankPlayTallies(listOf(a, b), 20).map { it.tally.gameId })
        assertEquals(listOf(1L, 2L), rankPlayTallies(listOf(b, a), 20).map { it.tally.gameId })
        val bAhead = b.copy(minutes = 200)
        assertEquals(listOf(2L, 1L), rankPlayTallies(listOf(a, bAhead), 20).map { it.tally.gameId })
        val tied = b.copy(minutes = 120)
        assertEquals(listOf(2L, 1L), rankPlayTallies(listOf(a, tied), 20).map { it.tally.gameId })
    }

    @Test
    fun `most played ties on time and date fall back to title`() {
        val same = at(2)
        val ranked = rankPlayTallies(
            listOf(PlayTally(1L, 30, same, "zelda"), PlayTally(2L, 30, same, "Animal")),
            20
        )
        assertEquals(listOf(2L, 1L), ranked.map { it.tally.gameId })
    }

    @Test
    fun `most played leaves out unplayed games and scales bars to the leader`() {
        val ranked = rankPlayTallies(
            listOf(PlayTally(1L, 0, null, "A"), PlayTally(2L, 50, null, "B"), PlayTally(3L, 100, null, "C")),
            20
        )
        assertEquals(listOf(3L, 2L), ranked.map { it.tally.gameId })
        assertEquals(listOf(1, 2), ranked.map { it.rank })
        assertEquals(1f, ranked[0].fraction, 0.0001f)
        assertEquals(0.5f, ranked[1].fraction, 0.0001f)
        assertEquals(1, rankPlayTallies(ranked.map { it.tally }, 1).size)
    }

    @Test
    fun `recent puts running games first in resume order`() {
        val games = listOf(
            game(1, lastPlayed = at(0, 20)),
            game(2, lastPlayed = at(5)),
            game(3, lastPlayed = at(1)),
            game(4, lastPlayed = at(0, 9))
        )
        val rows = orderRecent(games, runningOrder = mapOf(2L to 1, 3L to 0), today = today, zone = zone)
        assertEquals(listOf(3L, 2L, 1L, 4L), rows.map { it.game.id })
        assertEquals(listOf(true, true, false, false), rows.map { it.isRunning })
        assertTrue(rows.take(2).all { it.group == RecentGroup.TODAY })
    }

    @Test
    fun `recent without running games is newest first`() {
        val rows = orderRecent(
            listOf(game(1, lastPlayed = at(3)), game(2, lastPlayed = at(0)), game(3, lastPlayed = null)),
            runningOrder = emptyMap(),
            today = today,
            zone = zone
        )
        assertEquals(listOf(2L, 1L, 3L), rows.map { it.game.id })
    }

    @Test
    fun `recent groups by day`() {
        assertEquals(RecentGroup.TODAY, recentGroupOf(at(0), today, zone))
        assertEquals(RecentGroup.YESTERDAY, recentGroupOf(at(1), today, zone))
        assertEquals(RecentGroup.THIS_WEEK, recentGroupOf(at(6), today, zone))
        assertEquals(RecentGroup.EARLIER, recentGroupOf(at(7), today, zone))
        assertEquals(RecentGroup.EARLIER, recentGroupOf(null, today, zone))
    }

    @Test
    fun `grid steps leave upward from the first row and wrap sideways`() {
        assertNull(stepFocus(index = 2, size = 10, columns = 4, direction = FocusStep.UP))
        assertEquals(1, stepFocus(index = 5, size = 10, columns = 4, direction = FocusStep.UP))
        assertEquals(9, stepFocus(index = 6, size = 10, columns = 4, direction = FocusStep.DOWN))
        assertEquals(9, stepFocus(index = 9, size = 10, columns = 4, direction = FocusStep.DOWN))
        assertEquals(9, stepFocus(index = 0, size = 10, columns = 4, direction = FocusStep.LEFT))
        assertEquals(0, stepFocus(index = 9, size = 10, columns = 4, direction = FocusStep.RIGHT))
    }

    @Test
    fun `list steps stop at the bottom and leave from the top`() {
        assertNull(stepFocus(index = 0, size = 3, columns = 1, direction = FocusStep.UP))
        assertEquals(2, stepFocus(index = 2, size = 3, columns = 1, direction = FocusStep.DOWN))
        assertEquals(1, stepFocus(index = 0, size = 3, columns = 1, direction = FocusStep.DOWN))
    }

    @Test
    fun `not interested toggles in place`() {
        val state = TopNewState(tiles = listOf(TopNewTileUi(game(1), null)), isLoaded = true)
        assertEquals(setOf(1L), state.togglingDismissal(1L).dismissedIds)
        assertTrue(state.togglingDismissal(1L).togglingDismissal(1L).dismissedIds.isEmpty())
        assertEquals(1, state.togglingDismissal(1L).tiles.size)
    }

    @Test
    fun `weekly strip sums days into weeks and finds the busiest`() {
        val days = (0 until PLAY_STRIP_WEEKS * 7).map { offset ->
            com.nendo.argosy.data.model.PlayDay(
                date = today.minusDays((PLAY_STRIP_WEEKS * 7 - 1 - offset).toLong()),
                activeMs = if (offset / 7 == 4) 600_000L else 0L
            )
        }
        val weeks = weeklyMinutes(days)
        assertEquals(PLAY_STRIP_WEEKS, weeks.size)
        assertEquals(70L, weeks[4])
        assertEquals(4, busiestWeekOf(weeks))
        assertNull(busiestWeekOf(List(PLAY_STRIP_WEEKS) { 0L }))
    }
}
