package com.nendo.argosy.domain.model

import com.nendo.argosy.data.local.entity.GameEntity
import java.time.Instant

enum class HomeColumnKind { THEME, PLATFORM, MEDIA }

/**
 * The themed columns of the Columns home layout, in loop order. The names are stable tokens; the
 * display labels attach in ui/common.
 */
enum class HomeColumnTheme {
    CONTINUE,
    PLAY_IT_AGAIN,
    ALMOST_THERE,
    FRIENDS_PLAYING,
    JUST_ADDED,
    GATHERING_DUST,
    FAVORITES,
    PINNED_COLLECTION,
    MOST_PLAYED_GENRE
}

enum class HomeMediaColumnKind { CONTINUE_WATCHING, NEXT_UP }

/**
 * What a column's label is built from. [Theme] carries [subject] for the themes that name
 * something: the collection name for a pinned collection and the genre for the most played genre.
 */
sealed interface HomeColumnLabel {
    val kind: HomeColumnKind

    data class Theme(val theme: HomeColumnTheme, val subject: String? = null) : HomeColumnLabel {
        override val kind: HomeColumnKind get() = HomeColumnKind.THEME
    }

    data class Platform(val name: String) : HomeColumnLabel {
        override val kind: HomeColumnKind get() = HomeColumnKind.PLATFORM
    }

    data class Media(val mediaKind: HomeMediaColumnKind) : HomeColumnLabel {
        override val kind: HomeColumnKind get() = HomeColumnKind.MEDIA
    }
}

/**
 * One column of the loop. Game columns fill [games]; a media column fills [mediaItemIds] with the
 * ids of the media items it shows, in order.
 */
data class HomeColumn(
    val id: String,
    val label: HomeColumnLabel,
    val games: List<GameEntity> = emptyList(),
    val mediaItemIds: List<String> = emptyList()
) {
    val kind: HomeColumnKind get() = label.kind
}

data class HomePinnedCollection(
    val collectionId: Long,
    val name: String,
    val games: List<GameEntity>
)

data class HomePlatformGames(
    val platformId: Long,
    val name: String,
    val games: List<GameEntity>
)

data class HomeFriendsPlayingGame(
    val game: GameEntity,
    val playingNow: Boolean,
    val lastPlayedAt: Instant?
)

data class HomePlayedGame(
    val game: GameEntity,
    val activeMs: Long
)

data class HomeColumnWindow(val start: Instant, val end: Instant)

data class HomeMediaRow(
    val id: String,
    val mediaKind: HomeMediaColumnKind,
    val itemIds: List<String>
)

/**
 * Everything the Columns loop is built from. Game lists arrive in the order their source ranks
 * them; [genrePlay] is play per game inside the genre window.
 */
data class HomeColumnSources(
    val recent: List<GameEntity> = emptyList(),
    val playItAgain: List<GameEntity> = emptyList(),
    val almostThere: List<GameEntity> = emptyList(),
    val friendsPlaying: List<HomeFriendsPlayingGame> = emptyList(),
    val newlyAdded: List<GameEntity> = emptyList(),
    val gatheringDust: List<GameEntity> = emptyList(),
    val favorites: List<GameEntity> = emptyList(),
    val pinnedCollections: List<HomePinnedCollection> = emptyList(),
    val genrePlay: List<HomePlayedGame> = emptyList(),
    val platforms: List<HomePlatformGames> = emptyList(),
    val media: List<HomeMediaRow> = emptyList()
)
