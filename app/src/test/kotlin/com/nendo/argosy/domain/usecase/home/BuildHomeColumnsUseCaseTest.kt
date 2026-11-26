package com.nendo.argosy.domain.usecase.home

import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.domain.model.ColumnsConfig
import com.nendo.argosy.domain.model.HomeColumnKind
import com.nendo.argosy.domain.model.HomeColumnLabel
import com.nendo.argosy.domain.model.HomeColumnSources
import com.nendo.argosy.domain.model.HomeColumnTheme
import com.nendo.argosy.domain.model.HomeFriendsPlayingGame
import com.nendo.argosy.domain.model.HomeMediaColumnKind
import com.nendo.argosy.domain.model.HomeMediaRow
import com.nendo.argosy.domain.model.HomePinnedCollection
import com.nendo.argosy.domain.model.HomePlatformGames
import com.nendo.argosy.domain.model.HomePlayedGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class BuildHomeColumnsUseCaseTest {

    private val build = BuildHomeColumnsUseCase()

    private fun game(
        id: Long,
        platformId: Long = 1,
        lastPlayed: Instant? = null,
        rating: Float? = null,
        genre: String? = null,
        genres: String? = null
    ) = GameEntity(
        id = id,
        platformId = platformId,
        title = "Game $id",
        sortTitle = "game $id",
        localPath = null,
        rommId = null,
        igdbId = null,
        source = GameSource.LOCAL_ONLY,
        lastPlayed = lastPlayed,
        rating = rating,
        genre = genre,
        genres = genres
    )

    private fun at(epochSecond: Long): Instant = Instant.ofEpochSecond(epochSecond)

    private fun themeOf(label: HomeColumnLabel): HomeColumnTheme? = (label as? HomeColumnLabel.Theme)?.theme

    @Test
    fun `columns run themes in order then platforms then media`() {
        val sources = HomeColumnSources(
            recent = listOf(game(1)),
            playItAgain = listOf(game(2)),
            almostThere = listOf(game(3)),
            friendsPlaying = listOf(HomeFriendsPlayingGame(game(4), playingNow = true, lastPlayedAt = null)),
            newlyAdded = listOf(game(5)),
            gatheringDust = listOf(game(6)),
            favorites = listOf(game(7)),
            pinnedCollections = listOf(HomePinnedCollection(collectionId = 9, name = "Couch", games = listOf(game(8)))),
            genrePlay = listOf(HomePlayedGame(game(9, genres = "RPG"), activeMs = 60_000)),
            platforms = listOf(HomePlatformGames(platformId = 2, name = "SNES", games = listOf(game(10, platformId = 2)))),
            media = listOf(HomeMediaRow(id = "next", mediaKind = HomeMediaColumnKind.NEXT_UP, itemIds = listOf("a")))
        )

        val columns = build(sources)

        assertEquals(
            listOf(
                HomeColumnTheme.CONTINUE,
                HomeColumnTheme.PLAY_IT_AGAIN,
                HomeColumnTheme.ALMOST_THERE,
                HomeColumnTheme.FRIENDS_PLAYING,
                HomeColumnTheme.JUST_ADDED,
                HomeColumnTheme.GATHERING_DUST,
                HomeColumnTheme.FAVORITES,
                HomeColumnTheme.PINNED_COLLECTION,
                HomeColumnTheme.MOST_PLAYED_GENRE
            ),
            columns.take(9).map { themeOf(it.label) }
        )
        assertEquals(
            List(9) { HomeColumnKind.THEME } + HomeColumnKind.PLATFORM + HomeColumnKind.MEDIA,
            columns.map { it.kind }
        )
        assertEquals("Couch", (columns[7].label as HomeColumnLabel.Theme).subject)
        assertEquals("RPG", (columns[8].label as HomeColumnLabel.Theme).subject)
        assertEquals(listOf("a"), columns.last().mediaItemIds)
    }

    @Test
    fun `empty themes platforms and media rows are dropped`() {
        val sources = HomeColumnSources(
            recent = listOf(game(1)),
            favorites = listOf(game(2)),
            pinnedCollections = listOf(HomePinnedCollection(collectionId = 3, name = "Empty", games = emptyList())),
            platforms = listOf(HomePlatformGames(platformId = 4, name = "Empty", games = emptyList())),
            media = listOf(HomeMediaRow(id = "cw", mediaKind = HomeMediaColumnKind.CONTINUE_WATCHING, itemIds = emptyList()))
        )

        val columns = build(sources)

        assertEquals(
            listOf(HomeColumnTheme.CONTINUE, HomeColumnTheme.FAVORITES),
            columns.map { themeOf(it.label) }
        )
    }

    @Test
    fun `nothing at all builds no columns`() {
        assertTrue(build(HomeColumnSources()).isEmpty())
    }

    @Test
    fun `every column holds at most ten`() {
        val many = (1L..15L).map { game(it, platformId = 2) }
        val sources = HomeColumnSources(
            recent = many,
            platforms = listOf(HomePlatformGames(platformId = 2, name = "SNES", games = many)),
            media = listOf(HomeMediaRow(id = "next", mediaKind = HomeMediaColumnKind.NEXT_UP, itemIds = (1..15).map { "m$it" }))
        )

        val columns = build(sources)

        assertEquals(listOf(10, 10, 0), columns.map { it.games.size })
        assertEquals(10, columns.last().mediaItemIds.size)
    }

    @Test
    fun `show every game uncaps only the columns fetched in full`() {
        val many = (1L..15L).map { game(it, platformId = 2) }
        val sources = HomeColumnSources(
            recent = many,
            pinnedCollections = listOf(HomePinnedCollection(collectionId = 3, name = "Couch", games = many)),
            platforms = listOf(HomePlatformGames(platformId = 2, name = "SNES", games = many)),
            media = listOf(HomeMediaRow(id = "next", mediaKind = HomeMediaColumnKind.NEXT_UP, itemIds = (1..15).map { "m$it" }))
        )
        val full = setOf(
            BuildHomeColumnsUseCase.collectionColumnId(3),
            BuildHomeColumnsUseCase.platformColumnId(2),
            "media:next"
        )

        val columns = build(sources, ColumnsConfig(showAllGames = true), full)

        assertEquals(listOf(10, 15, 15, 0), columns.map { it.games.size })
        assertEquals(15, columns.last().mediaItemIds.size)
    }

    @Test
    fun `a column fetched in full stays at ten while every game is not shown`() {
        val many = (1L..15L).map { game(it) }
        val full = setOf(BuildHomeColumnsUseCase.themeColumnId(HomeColumnTheme.CONTINUE))

        val column = build(HomeColumnSources(recent = many), ColumnsConfig(showAllGames = false), full).single()

        assertEquals(10, column.games.size)
    }

    @Test
    fun `column ids read back the collection and platform they name`() {
        assertEquals(3L, BuildHomeColumnsUseCase.collectionIdOf(BuildHomeColumnsUseCase.collectionColumnId(3)))
        assertEquals(2L, BuildHomeColumnsUseCase.platformIdOf(BuildHomeColumnsUseCase.platformColumnId(2)))
        assertEquals(null, BuildHomeColumnsUseCase.platformIdOf(BuildHomeColumnsUseCase.collectionColumnId(2)))
    }

    @Test
    fun `hidden themes are dropped and the rest keep their order`() {
        val sources = HomeColumnSources(
            recent = listOf(game(1)),
            gatheringDust = listOf(game(2)),
            favorites = listOf(game(3)),
            pinnedCollections = listOf(
                HomePinnedCollection(collectionId = 4, name = "Couch", games = listOf(game(4))),
                HomePinnedCollection(collectionId = 5, name = "Party", games = listOf(game(5)))
            )
        )
        val config = ColumnsConfig(hiddenThemes = setOf(HomeColumnTheme.GATHERING_DUST, HomeColumnTheme.PINNED_COLLECTION))

        val columns = build(sources, config)

        assertEquals(listOf(HomeColumnTheme.CONTINUE, HomeColumnTheme.FAVORITES), columns.map { themeOf(it.label) })
    }

    @Test
    fun `a hidden theme's lead no longer pushes a platform off its own lead`() {
        val newest = game(1, platformId = 2, lastPlayed = at(300))
        val older = game(2, platformId = 2, lastPlayed = at(200))
        val sources = HomeColumnSources(
            recent = listOf(newest),
            platforms = listOf(HomePlatformGames(platformId = 2, name = "SNES", games = listOf(newest, older)))
        )

        val platform = build(sources, ColumnsConfig(hiddenThemes = setOf(HomeColumnTheme.CONTINUE))).single()

        assertEquals(listOf(1L, 2L), platform.games.map { it.id })
    }

    @Test
    fun `platform columns off drops every platform and keeps themes and media`() {
        val sources = HomeColumnSources(
            recent = listOf(game(1)),
            platforms = listOf(HomePlatformGames(platformId = 2, name = "SNES", games = listOf(game(2, platformId = 2)))),
            media = listOf(HomeMediaRow(id = "next", mediaKind = HomeMediaColumnKind.NEXT_UP, itemIds = listOf("a")))
        )

        val columns = build(sources, ColumnsConfig(showPlatformColumns = false))

        assertEquals(listOf(HomeColumnKind.THEME, HomeColumnKind.MEDIA), columns.map { it.kind })
    }

    @Test
    fun `the column game limit is ten unless every game shows and the column is fetched in full`() {
        assertEquals(HOME_COLUMN_MAX_GAMES, homeColumnGameLimit(showAllGames = false, fetchedInFull = true))
        assertEquals(HOME_COLUMN_MAX_GAMES, homeColumnGameLimit(showAllGames = true, fetchedInFull = false))
        assertEquals(Int.MAX_VALUE, homeColumnGameLimit(showAllGames = true, fetchedInFull = true))
    }

    @Test
    fun `a platform passes over a game that leads an earlier column`() {
        val newest = game(1, platformId = 2, lastPlayed = at(300))
        val older = game(2, platformId = 2, lastPlayed = at(200))
        val sources = HomeColumnSources(
            recent = listOf(newest),
            platforms = listOf(HomePlatformGames(platformId = 2, name = "SNES", games = listOf(newest, older)))
        )

        val platform = build(sources).last()

        assertEquals(listOf(2L, 1L), platform.games.map { it.id })
    }

    @Test
    fun `a platform keeps a lead that sits behind another column's lead`() {
        val other = game(9, platformId = 3, lastPlayed = at(500))
        val newest = game(1, platformId = 2, lastPlayed = at(300))
        val older = game(2, platformId = 2, lastPlayed = at(200))
        val sources = HomeColumnSources(
            recent = listOf(other, newest),
            platforms = listOf(HomePlatformGames(platformId = 2, name = "SNES", games = listOf(older, newest)))
        )

        val platform = build(sources).last()

        assertEquals(listOf(1L, 2L), platform.games.map { it.id })
    }

    @Test
    fun `a later platform passes over an earlier platform's lead`() {
        val shared = game(1, platformId = 2, lastPlayed = at(300))
        val second = game(2, platformId = 2, lastPlayed = at(200))
        val sources = HomeColumnSources(
            platforms = listOf(
                HomePlatformGames(platformId = 2, name = "SNES", games = listOf(shared, second)),
                HomePlatformGames(platformId = 3, name = "SNES Hacks", games = listOf(shared, second))
            )
        )

        val columns = build(sources)

        assertEquals(1L, columns[0].games.first().id)
        assertEquals(2L, columns[1].games.first().id)
    }

    @Test
    fun `a platform whose only game already leads keeps it`() {
        val only = game(1, platformId = 2, lastPlayed = at(300))
        val sources = HomeColumnSources(
            recent = listOf(only),
            platforms = listOf(HomePlatformGames(platformId = 2, name = "SNES", games = listOf(only)))
        )

        assertEquals(listOf(1L), build(sources).last().games.map { it.id })
    }

    @Test
    fun `an unplayed platform leads with its best rated game`() {
        val low = game(1, platformId = 2, rating = 60f)
        val high = game(2, platformId = 2, rating = 90f)
        val unrated = game(3, platformId = 2)
        val sources = HomeColumnSources(
            platforms = listOf(HomePlatformGames(platformId = 2, name = "SNES", games = listOf(unrated, low, high)))
        )

        assertEquals(listOf(2L, 1L, 3L), build(sources).single().games.map { it.id })
    }

    @Test
    fun `a played game leads over a better rated unplayed one`() {
        val rated = game(1, platformId = 2, rating = 99f)
        val played = game(2, platformId = 2, lastPlayed = at(100), rating = 10f)
        val sources = HomeColumnSources(
            platforms = listOf(HomePlatformGames(platformId = 2, name = "SNES", games = listOf(rated, played)))
        )

        assertEquals(2L, build(sources).single().games.first().id)
    }

    @Test
    fun `friends playing now come before friends who played earlier`() {
        val earlier = HomeFriendsPlayingGame(game(1), playingNow = false, lastPlayedAt = at(900))
        val live = HomeFriendsPlayingGame(game(2), playingNow = true, lastPlayedAt = at(100))
        val oldest = HomeFriendsPlayingGame(game(3), playingNow = false, lastPlayedAt = at(50))

        val column = build(HomeColumnSources(friendsPlaying = listOf(oldest, earlier, live))).single()

        assertEquals(listOf(2L, 1L, 3L), column.games.map { it.id })
    }

    @Test
    fun `listings of one game appear once in friends playing`() {
        val japanese = HomeFriendsPlayingGame(game(1).copy(igdbId = 77), playingNow = true, lastPlayedAt = at(500))
        val english = HomeFriendsPlayingGame(game(2).copy(igdbId = 77, localPath = "/roms/a.gba"), playingNow = true, lastPlayedAt = at(500))
        val revision = HomeFriendsPlayingGame(game(3).copy(igdbId = 77), playingNow = true, lastPlayedAt = at(500))
        val other = HomeFriendsPlayingGame(game(4).copy(igdbId = 88), playingNow = false, lastPlayedAt = at(100))

        val column = build(HomeColumnSources(friendsPlaying = listOf(japanese, english, revision, other))).single()

        assertEquals(listOf(2L, 4L), column.games.map { it.id })
    }

    @Test
    fun `the listing kept is the visible downloaded one the user played last`() {
        val hidden = HomeFriendsPlayingGame(
            game(1).copy(igdbId = 77, localPath = "/roms/a", isGroupVisible = false),
            playingNow = true,
            lastPlayedAt = null
        )
        val older = HomeFriendsPlayingGame(game(2, lastPlayed = at(10)).copy(igdbId = 77, localPath = "/roms/b"), playingNow = true, lastPlayedAt = null)
        val newer = HomeFriendsPlayingGame(game(3, lastPlayed = at(20)).copy(igdbId = 77, localPath = "/roms/c"), playingNow = true, lastPlayedAt = null)

        val column = build(HomeColumnSources(friendsPlaying = listOf(hidden, older, newer))).single()

        assertEquals(listOf(3L), column.games.map { it.id })
    }

    @Test
    fun `games with no igdb id are never merged`() {
        val first = HomeFriendsPlayingGame(game(1), playingNow = true, lastPlayedAt = null)
        val second = HomeFriendsPlayingGame(game(2), playingNow = true, lastPlayedAt = null)

        val column = build(HomeColumnSources(friendsPlaying = listOf(first, second))).single()

        assertEquals(setOf(1L, 2L), column.games.map { it.id }.toSet())
    }

    @Test
    fun `the most played genre sums play across a comma list`() {
        val sources = HomeColumnSources(
            genrePlay = listOf(
                HomePlayedGame(game(1, genres = "Platform,Adventure"), activeMs = 40),
                HomePlayedGame(game(2, genre = "Adventure, Puzzle"), activeMs = 30),
                HomePlayedGame(game(3, genres = "Platform"), activeMs = 50)
            )
        )

        val column = build(sources).single()

        assertEquals("Platform", (column.label as HomeColumnLabel.Theme).subject)
        assertEquals(listOf(3L, 1L), column.games.map { it.id })
    }

    @Test
    fun `games without a genre build no genre column`() {
        val sources = HomeColumnSources(genrePlay = listOf(HomePlayedGame(game(1), activeMs = 100)))

        assertTrue(build(sources).isEmpty())
    }

    @Test
    fun `genre tokens prefer the genres list over the single genre`() {
        assertEquals(setOf("Shooter", "Arcade"), BuildHomeColumnsUseCase.genreTokens(game(1, genre = "Shooter", genres = "Shooter,Arcade")))
        assertEquals(setOf("Action", "Indie"), BuildHomeColumnsUseCase.genreTokens(game(2, genre = "Action, Indie")))
        assertTrue(BuildHomeColumnsUseCase.genreTokens(game(3)).isEmpty())
    }

    @Test
    fun `play it again spans three days either side of the date a year back`() {
        val window = BuildHomeColumnsUseCase.playItAgainWindow(LocalDate.of(2026, 11, 19), ZoneOffset.UTC)

        assertEquals(Instant.parse("2025-11-16T00:00:00Z"), window.start)
        assertEquals(Instant.parse("2025-11-23T00:00:00Z"), window.end)
    }

    @Test
    fun `dust and genre windows reach ninety days back`() {
        val now = Instant.parse("2026-11-19T12:00:00Z")

        assertEquals(Instant.parse("2026-08-21T12:00:00Z"), BuildHomeColumnsUseCase.gatheringDustCutoff(now))
        assertEquals(Instant.parse("2026-08-21T12:00:00Z"), BuildHomeColumnsUseCase.mostPlayedGenreWindow(now).start)
    }
}
