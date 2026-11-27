package com.nendo.argosy.ui.common

import android.content.Context
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.FeatureTileKind
import com.nendo.argosy.domain.model.HomeTileTargetRef
import com.nendo.argosy.domain.model.RaFeaturedMode
import com.nendo.argosy.domain.model.RaLockedAchievement
import com.nendo.argosy.domain.model.RaTileContent
import com.nendo.argosy.domain.model.RaUnlock
import com.nendo.argosy.ui.components.RaBrowseEntry
import com.nendo.argosy.ui.components.RaTileLabels
import com.nendo.argosy.ui.screens.home.HomeGameUi
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val LATEST_GAME_ID = 11L
private const val TRACKED_GAME_ID = 22L
private const val NOW = 1_700_000_000_000L

class FeatureTileContentBuilderTest {

    private val context = mockk<Context>(relaxed = true)

    private val labels = RaTileLabels(
        signedOut = R.string.home_grid_tile_feature_ra_signed_out,
        empty = R.string.home_grid_tile_feature_ra_no_unlocks,
        latestHeading = R.string.home_grid_tile_ra_latest_heading,
        nextHeading = R.string.home_grid_tile_ra_next_heading,
        mastered = R.string.home_grid_tile_ra_mastered,
        locked = R.string.home_grid_tile_ra_locked,
        points = R.plurals.home_grid_tile_ra_points,
        unlocks = R.plurals.home_grid_tile_ra_unlocks,
        unlockMeta = R.string.home_grid_tile_ra_unlock_meta,
        accountTally = R.string.home_grid_tile_ra_account_tally,
        progress = R.string.home_grid_tile_ra_progress,
        gameProgress = R.string.home_grid_tile_ra_game_progress
    )

    private val strings = FeatureTileStrings(
        randomLabel = R.string.home_grid_tile_feature_random_label,
        randomEmpty = R.string.home_grid_tile_feature_random_empty,
        continueLabel = R.string.home_grid_tile_feature_continue_label,
        continueEmpty = R.string.home_grid_tile_feature_continue_empty,
        raLabel = R.string.home_grid_tile_feature_ra_label,
        libraryLinkLabel = R.string.home_grid_tile_feature_library_link_label,
        libraryLinkAll = R.string.home_grid_tile_feature_library_link_all,
        libraryLinkCount = R.string.home_grid_tile_feature_library_link_count,
        libraryLinkMore = R.string.home_grid_tile_feature_library_link_more,
        ra = labels
    )

    private val tileGames = mapOf(
        LATEST_GAME_ID to game(LATEST_GAME_ID, "Sonic"),
        TRACKED_GAME_ID to game(TRACKED_GAME_ID, "Metroid")
    )

    @Before
    fun setUp() {
        every { context.getString(any<Int>()) } answers { "res:${firstArg<Int>()}" }
    }

    @Test
    fun `the account tile browses its recent unlocks over the latest unlock's game`() {
        val unlocks = listOf(unlock(1L, LATEST_GAME_ID), unlock(2L, LATEST_GAME_ID))
        val content = raContentFor(
            RaTileContent.Account(
                username = "nendo",
                featuredMode = RaFeaturedMode.HARDCORE,
                points = 120,
                unlocks = 7,
                latestUnlock = unlocks.first(),
                recentUnlocks = unlocks,
                latestGameEarned = 3,
                latestGameTotal = 12
            )
        )

        val ra = requireNotNull(content.ra)
        assertEquals("nendo", content.label)
        assertEquals(listOf(1L, 2L), ra.entries.map { it.raId })
        assertTrue(ra.entries.all { it is RaBrowseEntry.Unlocked })
        assertEquals(LATEST_GAME_ID, ra.groundGame?.id)
        assertEquals(labels, ra.labels)
    }

    @Test
    fun `a tracked tile browses its latest unlock then what is still locked`() {
        val content = raContentFor(
            RaTileContent.TrackedGame(
                username = "nendo",
                featuredMode = RaFeaturedMode.SOFTCORE,
                points = 40,
                unlocks = 1,
                latestUnlock = unlock(1L, TRACKED_GAME_ID),
                gameId = TRACKED_GAME_ID,
                gameTitle = "Metroid",
                gameCoverPath = "/covers/22.png",
                total = 4,
                nextLocked = listOf(locked(5L), locked(6L)),
                mastered = false
            )
        )

        val ra = requireNotNull(content.ra)
        assertEquals(listOf(1L, 5L, 6L), ra.entries.map { it.raId })
        assertTrue(ra.entries.first() is RaBrowseEntry.Unlocked)
        assertTrue(ra.entries.drop(1).all { it is RaBrowseEntry.Locked })
        assertEquals(TRACKED_GAME_ID, ra.groundGame?.id)
        assertEquals(labels, ra.labels)
    }

    @Test
    fun `a signed out tile still draws, with nothing to browse`() {
        val content = raContentFor(null)

        val ra = requireNotNull(content.ra)
        assertNull(ra.content)
        assertTrue(ra.entries.isEmpty())
        assertNull(ra.groundGame)
        assertEquals(labels, ra.labels)
        assertEquals("res:${R.string.home_grid_tile_feature_ra_label}", content.label)
    }

    private fun raContentFor(summary: RaTileContent?) = featureTileContentFor(
        target = HomeTileTargetRef.Feature(FeatureTileKind.RA_SUMMARY),
        tileGames = tileGames,
        continueGameId = null,
        raSummary = summary,
        context = context,
        strings = strings,
        now = NOW
    )

    private fun unlock(raId: Long, gameId: Long) = RaUnlock(
        raId = raId,
        title = "Achievement $raId",
        description = "Do the thing",
        points = 10,
        badgePath = "/cache/$raId.png",
        unlockedAt = NOW - raId,
        hardcore = true,
        gameId = gameId,
        gameTitle = "Game $gameId",
        gameCoverPath = "/covers/$gameId.png"
    )

    private fun locked(raId: Long) = RaLockedAchievement(
        raId = raId,
        title = "Locked $raId",
        description = "Still to do",
        points = 5,
        badgeLockPath = "/badges/${raId}_lock.png"
    )

    @Test
    fun `a library link is named by its platforms and draws one of their covers`() {
        val content = com.nendo.argosy.ui.common.featureTileContentFor(
            target = HomeTileTargetRef.Feature(FeatureTileKind.LIBRARY_LINK),
            tileGames = tileGames,
            continueGameId = null,
            raSummary = null,
            context = context,
            strings = strings,
            libraryLink = com.nendo.argosy.ui.screens.home.LibraryLinkTileUi(
                gameCount = 34,
                coverGameId = LATEST_GAME_ID,
                platformNames = listOf("Game Boy", "Super Nintendo")
            ),
            now = NOW
        )

        assertEquals("Game Boy • Super Nintendo", content.label)
        assertEquals(LATEST_GAME_ID, content.game?.id)
        assertEquals(LATEST_GAME_ID, content.game?.id)
        assertEquals("34", content.stats.single().value)
        assertTrue(content.isLibraryLink)
    }

    /**
     * A cell holds two lines. Past three names the later ones were not merely clipped but absent,
     * so the tile could not say it carried more filters than the one it had room to show.
     */
    @Test
    fun `a library link names three filters and counts the rest`() {
        val content = com.nendo.argosy.ui.common.featureTileContentFor(
            target = HomeTileTargetRef.Feature(FeatureTileKind.LIBRARY_LINK),
            tileGames = tileGames,
            continueGameId = null,
            raSummary = null,
            context = context,
            strings = strings,
            libraryLink = com.nendo.argosy.ui.screens.home.LibraryLinkTileUi(
                gameCount = 12,
                platformNames = listOf("NES", "SNES", "N64", "GBA", "GBC")
            ),
            now = NOW
        )

        assertTrue(content.label.startsWith("NES • SNES • N64 • "))
        assertFalse(content.label.contains("GBA"))
    }

    @Test
    fun `a library link narrowed only by genre is named by the genres`() {
        val content = com.nendo.argosy.ui.common.featureTileContentFor(
            target = HomeTileTargetRef.Feature(FeatureTileKind.LIBRARY_LINK),
            tileGames = tileGames,
            continueGameId = null,
            raSummary = null,
            context = context,
            strings = strings,
            libraryLink = com.nendo.argosy.ui.screens.home.LibraryLinkTileUi(
                gameCount = 4,
                genres = listOf("Platform")
            ),
            now = NOW
        )

        assertEquals("Platform", content.label)
    }

    @Test
    fun `a library link narrowing nothing says so instead of reading empty`() {
        val content = com.nendo.argosy.ui.common.featureTileContentFor(
            target = HomeTileTargetRef.Feature(FeatureTileKind.LIBRARY_LINK),
            tileGames = tileGames,
            continueGameId = null,
            raSummary = null,
            context = context,
            strings = strings,
            libraryLink = com.nendo.argosy.ui.screens.home.LibraryLinkTileUi(gameCount = 900),
            now = NOW
        )

        assertEquals("res:${R.string.home_grid_tile_feature_library_link_all}", content.label)
        assertNull(content.game)
    }

    private fun game(id: Long, title: String) = HomeGameUi(
        id = id,
        title = title,
        platformId = 1L,
        platformSlug = "snes",
        platformDisplayName = "Super Nintendo",
        platformShortName = "SNES",
        coverPath = "/covers/$id.png",
        backgroundPath = null,
        developer = null,
        releaseYear = null,
        genre = null,
        isFavorite = false,
        isDownloaded = true
    )
}
