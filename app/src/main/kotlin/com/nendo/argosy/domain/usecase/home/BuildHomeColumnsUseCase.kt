package com.nendo.argosy.domain.usecase.home

import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.domain.model.ColumnsConfig
import com.nendo.argosy.domain.model.HomeColumn
import com.nendo.argosy.domain.model.HomeColumnLabel
import com.nendo.argosy.domain.model.HomeColumnWindow
import com.nendo.argosy.domain.model.HomeColumnSources
import com.nendo.argosy.domain.model.HomeColumnTheme
import com.nendo.argosy.domain.model.HomeFriendsPlayingGame
import com.nendo.argosy.domain.model.HomePlatformGames
import com.nendo.argosy.domain.model.HomePlayedGame
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

const val HOME_COLUMN_MAX_GAMES = 10
const val PLAY_IT_AGAIN_DAYS_AROUND = 3L
const val PLAY_IT_AGAIN_YEARS_BACK = 1L
const val GATHERING_DUST_DAYS = 90L
const val ALMOST_THERE_MAX_REMAINING_SEC = 3L * 60 * 60
const val MOST_PLAYED_GENRE_WINDOW_DAYS = 90L

/**
 * How many games one Columns column holds, and so how many each of its queries asks for.
 */
fun homeColumnGameLimit(showAllGames: Boolean): Int = if (showAllGames) Int.MAX_VALUE else HOME_COLUMN_MAX_GAMES

/**
 * The ordered Columns loop: themes, then platforms, then media. Empty columns, themes in
 * [ColumnsConfig.hiddenThemes] and, with [ColumnsConfig.showPlatformColumns] off, platforms are
 * dropped. Each column holds at most [homeColumnGameLimit] games. A platform column leads with its
 * most recently played game, else its best rated, passing over any game that already leads an
 * earlier column.
 */
class BuildHomeColumnsUseCase @Inject constructor() {

    operator fun invoke(sources: HomeColumnSources, config: ColumnsConfig = ColumnsConfig()): List<HomeColumn> {
        val limit = homeColumnGameLimit(config.showAllGames)
        val themes = themeColumns(sources, limit).filter { column ->
            (column.label as? HomeColumnLabel.Theme)?.let { config.showsTheme(it.theme) } ?: true
        }
        val leads = themes.mapTo(mutableSetOf()) { it.games.first().id }
        val platforms = if (!config.showPlatformColumns) {
            emptyList()
        } else {
            sources.platforms.mapNotNull { platform ->
                platformColumn(platform, leads, limit)?.also { leads += it.games.first().id }
            }
        }
        val media = sources.media.mapNotNull { row ->
            row.itemIds.distinct().take(limit).takeIf { it.isNotEmpty() }?.let { ids ->
                HomeColumn(
                    id = "$ID_MEDIA${row.id}",
                    label = HomeColumnLabel.Media(row.mediaKind),
                    mediaItemIds = ids
                )
            }
        }
        return themes + platforms + media
    }

    private fun themeColumns(sources: HomeColumnSources, limit: Int): List<HomeColumn> {
        val genre = mostPlayedGenre(sources.genrePlay)
        return listOfNotNull(
            theme(HomeColumnTheme.CONTINUE, sources.recent, limit),
            theme(HomeColumnTheme.PLAY_IT_AGAIN, sources.playItAgain, limit),
            theme(HomeColumnTheme.ALMOST_THERE, sources.almostThere, limit),
            theme(HomeColumnTheme.FRIENDS_PLAYING, friendsOrder(sources.friendsPlaying), limit),
            theme(HomeColumnTheme.JUST_ADDED, sources.newlyAdded, limit),
            theme(HomeColumnTheme.GATHERING_DUST, sources.gatheringDust, limit),
            theme(HomeColumnTheme.FAVORITES, sources.favorites, limit)
        ) + sources.pinnedCollections.mapNotNull { collection ->
            theme(
                HomeColumnTheme.PINNED_COLLECTION,
                collection.games,
                limit,
                subject = collection.name,
                id = "$ID_COLLECTION${collection.collectionId}"
            )
        } + listOfNotNull(
            genre?.let { (name, games) ->
                theme(HomeColumnTheme.MOST_PLAYED_GENRE, games, limit, subject = name)
            }
        )
    }

    private fun theme(
        theme: HomeColumnTheme,
        games: List<GameEntity>,
        limit: Int,
        subject: String? = null,
        id: String = "$ID_THEME${theme.name}"
    ): HomeColumn? {
        val capped = games.distinctBy { it.id }.take(limit)
        if (capped.isEmpty()) return null
        return HomeColumn(id = id, label = HomeColumnLabel.Theme(theme, subject), games = capped)
    }

    private fun platformColumn(platform: HomePlatformGames, leads: Set<Long>, limit: Int): HomeColumn? {
        val ranked = platform.games.distinctBy { it.id }.sortedWith(LEAD_ORDER)
        if (ranked.isEmpty()) return null
        val lead = ranked.firstOrNull { it.id !in leads } ?: ranked.first()
        val games = (listOf(lead) + ranked.filter { it.id != lead.id }).take(limit)
        return HomeColumn(
            id = "$ID_PLATFORM${platform.platformId}",
            label = HomeColumnLabel.Platform(platform.name),
            games = games
        )
    }

    private fun friendsOrder(entries: List<HomeFriendsPlayingGame>): List<GameEntity> =
        entries.sortedWith(
            compareByDescending<HomeFriendsPlayingGame> { it.playingNow }
                .thenByDescending { it.lastPlayedAt }
        ).map { it.game }

    private fun mostPlayedGenre(played: List<HomePlayedGame>): Pair<String, List<GameEntity>>? {
        val tokensByGame = played.associate { it.game.id to genreTokens(it.game) }
        val totals = mutableMapOf<String, Long>()
        played.forEach { entry ->
            tokensByGame[entry.game.id].orEmpty().forEach { token ->
                totals[token] = (totals[token] ?: 0L) + entry.activeMs
            }
        }
        val top = totals.entries
            .filter { it.value > 0 }
            .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .firstOrNull()?.key ?: return null
        val games = played
            .filter { top in tokensByGame[it.game.id].orEmpty() }
            .sortedByDescending { it.activeMs }
            .map { it.game }
        return top to games
    }

    companion object {
        private const val ID_THEME = "theme:"
        private const val ID_COLLECTION = "collection:"
        private const val ID_PLATFORM = "platform:"
        private const val ID_MEDIA = "media:"
        private const val GENRE_SEPARATOR = ","

        private val LEAD_ORDER: Comparator<GameEntity> =
            compareBy<GameEntity> { it.lastPlayed == null }
                .thenByDescending { it.lastPlayed }
                .thenBy { it.rating == null }
                .thenByDescending { it.rating }
                .thenBy { it.sortTitle }

        /**
         * A game's genres as single names. RomM writes a comma list to `genres` and its first genre
         * to `genre`; Steam and the store syncs write a comma list to `genre` and leave `genres`
         * empty.
         */
        fun genreTokens(game: GameEntity): Set<String> =
            (game.genres?.takeIf { it.isNotBlank() } ?: game.genre.orEmpty())
                .split(GENRE_SEPARATOR)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()

        /**
         * Sessions within [PLAY_IT_AGAIN_DAYS_AROUND] days of [today] one year back, whole days in
         * [zone], end exclusive.
         */
        fun playItAgainWindow(today: LocalDate, zone: ZoneId): HomeColumnWindow {
            val anchor = today.minusYears(PLAY_IT_AGAIN_YEARS_BACK)
            return HomeColumnWindow(
                start = anchor.minusDays(PLAY_IT_AGAIN_DAYS_AROUND).atStartOfDay(zone).toInstant(),
                end = anchor.plusDays(PLAY_IT_AGAIN_DAYS_AROUND + 1).atStartOfDay(zone).toInstant()
            )
        }

        fun gatheringDustCutoff(now: Instant): Instant = now.minus(Duration.ofDays(GATHERING_DUST_DAYS))

        fun mostPlayedGenreWindow(now: Instant): HomeColumnWindow =
            HomeColumnWindow(start = now.minus(Duration.ofDays(MOST_PLAYED_GENRE_WINDOW_DAYS)), end = now)
    }
}
