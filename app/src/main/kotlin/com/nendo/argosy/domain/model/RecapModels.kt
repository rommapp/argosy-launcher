package com.nendo.argosy.domain.model

import com.nendo.argosy.data.model.PlayDay
import java.time.Instant
import java.time.YearMonth

/**
 * One achievement earned inside a time window, keyed by its RetroAchievements badge id. A hardcore
 * unlock inside the window wins over a softcore one, and [unlockedAt] is the time of the one kept.
 */
data class WindowUnlock(
    val badgeId: String,
    val unlockedAt: Instant,
    val isHardcore: Boolean
)

/**
 * An achievement that appeared between the unlock snapshots taken at a session's launch and end,
 * keyed by its RetroAchievements achievement id. Undated: it belongs to the session as a whole.
 */
data class SnapshotUnlock(
    val achievementId: Long,
    val isHardcore: Boolean
)

/**
 * An unlock earned during one play session. [title], [points], [description] and [type] are null
 * when the local achievement list has no row for [badgeId]. [badgePath] is the cached badge file
 * when one exists, else the remote url. [type] is the RetroAchievements type token as RomM stores
 * it, never a label. [gameTitle] names the game the unlock belongs to; null where every unlock
 * shown together comes from one game, or no title is known.
 */
data class SessionUnlock(
    val badgeId: String,
    val title: String?,
    val points: Int?,
    val badgePath: String,
    val isHardcore: Boolean,
    val unlockedAt: Instant,
    val description: String?,
    val type: String?,
    val gameTitle: String?
)

/**
 * The facts of a game's last session a recap shows. [daysSince] counts whole days between the
 * session's end and now.
 */
data class RecapLastSession(
    val start: Instant,
    val end: Instant,
    val activePlayMs: Long,
    val richPresence: String?,
    val daysSince: Long
)

/**
 * A recap due before the next launch of [gameId]. Unlocks are read from
 * [unlockWindowStart, unlockWindowEnd], the last session's span. [ownerUserId] is the RomM user
 * whose achievement rows resolve the unlocks. [snapshotUnlocks] is the last session's launch-to-end
 * snapshot difference, read only when no dated source can answer.
 */
data class PreLaunchRecap(
    val gameId: Long,
    val raGameId: Long,
    val ownerUserId: Long?,
    val unlockWindowStart: Instant,
    val unlockWindowEnd: Instant,
    val lastSession: RecapLastSession,
    val snapshotUnlocks: List<SnapshotUnlock>? = null
)

/**
 * What the launch flow shows before the game starts. [lastSession] is null when no session of the
 * game is recorded.
 */
data class LaunchRecap(
    val gameTitle: String,
    val lastSession: RecapLastSession?,
    val unlocks: List<SessionUnlock>
)

enum class RecapAwardKind(val token: String) {
    BEATEN_SOFTCORE("beaten-softcore"),
    BEATEN_HARDCORE("beaten-hardcore"),
    COMPLETED("completed"),
    MASTERED("mastered");

    companion object {
        fun fromToken(token: String?): RecapAwardKind? = entries.firstOrNull { it.token == token }
    }
}

data class RecapGameTotal(
    val gameId: Long,
    val gameTitle: String,
    val activeMs: Long,
    val sessionCount: Int
)

data class RecapFavorite(
    val gameId: Long,
    val gameTitle: String,
    val activeMs: Long,
    val isFirstPlay: Boolean
)

data class RecapMonthUnlock(
    val raGameId: Long,
    val unlock: WindowUnlock
)

data class RecapAward(
    val raGameId: Long,
    val kind: RecapAwardKind,
    val awardedAt: Instant
)

/**
 * A calendar month in one zone: [start] is its first instant, [end] the first instant of the
 * month after.
 */
data class MonthWindow(
    val start: Instant,
    val end: Instant
) {
    operator fun contains(instant: Instant): Boolean = !instant.isBefore(start) && instant.isBefore(end)
}

/**
 * One month of play. [topGames] is ordered by active time, most first. [unlocks] and [awards] are
 * oldest first. [longestSessionMs] is 0 for a
 * month without sessions.
 */
data class MonthlyRecap(
    val month: YearMonth,
    val totalActiveMs: Long,
    val sessionCount: Int,
    val longestSessionMs: Long,
    val topGames: List<RecapGameTotal>,
    val newFavorites: List<RecapFavorite>,
    val unlocks: List<RecapMonthUnlock>,
    val awards: List<RecapAward>
)

/**
 * A game of the month's play. [isNewFavorite] marks a top-three game that was not in the previous
 * month's top three.
 */
data class DigestGame(
    val gameId: Long,
    val gameTitle: String,
    val coverPath: String?,
    val activeMs: Long,
    val isNewFavorite: Boolean
)

data class DigestAward(
    val raGameId: Long,
    val gameTitle: String,
    val kind: RecapAwardKind,
    val awardedAt: Instant
)

/**
 * The month's listening. [topAlbumArt] is the top album's cover when a music source still lists
 * the album.
 */
data class DigestMusic(
    val listenedMs: Long,
    val playCount: Int,
    val topTrack: MusicStatTrack?,
    val topAlbum: MusicStatAlbum?,
    val topAlbumArt: MusicArt?,
    val topArtist: MusicStatArtist?
)

/**
 * What the monthly recap card shows for [month]. [days] holds every date of the month in order,
 * zero where nothing was played. [topGames] is every game played, most active time first.
 * [unlocks] are oldest first, one per achievement. [music] is null for a month with no listening.
 * [isEmpty] is true for a month with no sessions, unlocks, awards or listening.
 */
data class MonthlyDigest(
    val month: YearMonth,
    val totalActiveMs: Long,
    val sessionCount: Int,
    val longestSessionMs: Long,
    val days: List<PlayDay>,
    val topGames: List<DigestGame>,
    val unlocks: List<SessionUnlock>,
    val awards: List<DigestAward>,
    val music: DigestMusic? = null
) {
    val isEmpty: Boolean get() = sessionCount == 0 && unlocks.isEmpty() && awards.isEmpty() && music == null

    val daysPlayed: Int get() = days.count { it.activeMs > 0L }

    /**
     * Index into [days] of the day with the most play, the earliest on a tie, or null when fewer
     * than two days have any.
     */
    val busiestDayIndex: Int?
        get() = days.indices
            .filter { days[it].activeMs > 0L }
            .takeIf { it.size >= MIN_DAYS_FOR_BUSIEST }
            ?.maxWithOrNull(compareBy<Int> { days[it].activeMs }.thenByDescending { it })

    private companion object {
        const val MIN_DAYS_FOR_BUSIEST = 2
    }
}
