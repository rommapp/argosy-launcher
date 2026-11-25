package com.nendo.argosy.data.music.stats

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.MusicGameContextDao
import com.nendo.argosy.data.local.dao.MusicStatsDao
import com.nendo.argosy.data.local.dao.RommMusicIndexDao
import com.nendo.argosy.data.local.entity.RommMusicTrackEntity
import com.nendo.argosy.data.music.identity.MusicAccountReader
import com.nendo.argosy.data.music.identity.MusicAccounts
import com.nendo.argosy.domain.model.MusicContextTagKind
import com.nendo.argosy.domain.model.MusicFlashbackYear
import com.nendo.argosy.domain.model.MusicMonthTotals
import com.nendo.argosy.domain.model.MusicPairingGroup
import com.nendo.argosy.domain.model.MusicRecommendations
import com.nendo.argosy.domain.model.MusicSourceKind
import com.nendo.argosy.domain.model.MusicStatAlbum
import com.nendo.argosy.domain.model.MusicStatArtist
import com.nendo.argosy.domain.model.MusicStatTrack
import com.nendo.argosy.domain.music.MusicKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val DEFAULT_PER_GROUP = 5
private const val DEFAULT_GROUP_LIMIT = 20
private const val SECONDS_TO_MS = 1000.0
private const val QUERY_CHUNK = 500

/**
 * Listening stats for the active account. Days are yyyymmdd ints in the device zone (inclusive
 * ranges), months yyyymm. Track lists group by recording key when one exists, else by track key,
 * and rank by audible time.
 */
@Singleton
class MusicStatsRepository @Inject constructor(
    private val statsDao: MusicStatsDao,
    private val contextDao: MusicGameContextDao,
    private val gameDao: GameDao,
    private val rommIndexDao: RommMusicIndexDao,
    private val accountReader: MusicAccountReader
) {

    suspend fun topTracks(fromDay: Int, toDay: Int, limit: Int): List<MusicStatTrack> = owned { accounts ->
        statsDao.topTracks(accounts.ownerKey, fromDay, toDay, limit).map(MusicStatsAggregation::toStat)
    }

    suspend fun topAlbums(fromDay: Int, toDay: Int, limit: Int): List<MusicStatAlbum> = owned { accounts ->
        statsDao.topAlbums(accounts.ownerKey, fromDay, toDay, limit).map { row ->
            MusicStatAlbum(
                albumKey = row.albumKey,
                source = row.source,
                romId = row.romId,
                title = row.albumTitle,
                artistCredit = row.artistCredit,
                trackGameTitle = row.trackGameTitle,
                listenedMs = row.totalMs,
                playCount = row.playCount,
                lastListenedAt = row.lastStartedAt
            )
        }
    }

    suspend fun topArtists(fromDay: Int, toDay: Int, limit: Int): List<MusicStatArtist> = owned { accounts ->
        statsDao.topArtists(accounts.ownerKey, fromDay, toDay, limit).map { row ->
            MusicStatArtist(
                artistKey = row.artistKey,
                name = row.name,
                listenedMs = row.totalMs,
                playCount = row.playCount,
                lastListenedAt = row.lastStartedAt
            )
        }
    }

    suspend fun monthTotals(yearMonth: Int): MusicMonthTotals = owned { accounts ->
        val row = statsDao.totals(accounts.ownerKey, yearMonth * 100 + 1, yearMonth * 100 + 31)
        MusicMonthTotals(yearMonth, row.totalMs, row.playCount, row.trackCount, row.activeDays)
    }

    /**
     * Every year with listens on [monthDay] (mmdd), newest year first, each with its top tracks.
     */
    suspend fun flashbackOnThisDay(monthDay: Int, perYear: Int = DEFAULT_PER_GROUP): List<MusicFlashbackYear> =
        owned { accounts ->
            MusicStatsAggregation.flashbackYears(statsDao.tracksOnDay(accounts.ownerKey, monthDay), perYear)
        }

    suspend fun flashbackMonth(month: Int, perYear: Int = DEFAULT_PER_GROUP): List<MusicFlashbackYear> =
        owned { accounts ->
            MusicStatsAggregation.flashbackYears(statsDao.tracksInMonth(accounts.ownerKey, month), perYear)
        }

    suspend fun pairingsByGame(
        perGroup: Int = DEFAULT_PER_GROUP,
        limit: Int = DEFAULT_GROUP_LIMIT
    ): List<MusicPairingGroup> = owned { accounts ->
        MusicStatsAggregation.pairingGroups(statsDao.pairingsByGame(accounts.ownerKey), perGroup, limit)
    }

    suspend fun pairingsByPlatform(
        perGroup: Int = DEFAULT_PER_GROUP,
        limit: Int = DEFAULT_GROUP_LIMIT
    ): List<MusicPairingGroup> = owned { accounts ->
        MusicStatsAggregation.pairingGroups(statsDao.pairingsByPlatform(accounts.ownerKey), perGroup, limit)
    }

    suspend fun pairingsByTag(
        kind: MusicContextTagKind,
        perGroup: Int = DEFAULT_PER_GROUP,
        limit: Int = DEFAULT_GROUP_LIMIT
    ): List<MusicPairingGroup> = owned { accounts ->
        MusicStatsAggregation.pairingGroups(statsDao.pairingsByTag(accounts.ownerKey, kind.token), perGroup, limit)
    }

    /**
     * The local game id behind each of [gameKeys], taken from its most recent session context.
     * Keys with no context row are absent.
     */
    suspend fun gameIdsFor(gameKeys: Collection<String>): Map<String, Long> = owned { accounts ->
        if (gameKeys.isEmpty()) return@owned emptyMap()
        gameKeys.distinct().chunked(QUERY_CHUNK)
            .flatMap { contextDao.latestGameIds(accounts.ownerKey, it) }
            .associate { it.gameKey to it.gameId }
    }

    /**
     * Tracks to play with [gameId], scored by the listen history's game contexts, followed by
     * unheard tracks of the game's own RomM soundtrack. Null when the game row is gone.
     */
    suspend fun recommendationsForGame(gameId: Long, limit: Int): MusicRecommendations? = owned { accounts ->
        val game = gameDao.getById(gameId) ?: return@owned null
        val seedAlbumKey = game.rommId?.let(MusicKeys::rommRomAlbumKey)
        val target = MusicRecommendationScoring.Target(
            gameId = game.id,
            gameKey = MusicGameContexts.gameKey(game, accounts.rommServerKey),
            platformSlug = game.platformSlug,
            tags = MusicGameContexts.tags(game).mapTo(HashSet()) { it.kind to it.tagKey },
            seedAlbumKey = seedAlbumKey
        )
        val rows = statsDao.contextListens(accounts.ownerKey, MusicListenRules.MIN_COUNTED_DURATION_MS, seedAlbumKey)
        val tagsByContext = contextDao.tags(accounts.ownerKey)
            .mapNotNull { tag -> MusicContextTagKind.fromToken(tag.kind)?.let { tag.contextId to (it to tag.tagKey) } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, tags) -> tags.toSet() }
        val seeds = game.rommId?.let { romId -> soundtrackSeeds(accounts, romId, seedAlbumKey) }.orEmpty()
        MusicRecommendations(
            gameId = game.id,
            seedAlbumKey = seedAlbumKey,
            tracks = MusicRecommendationScoring.rank(target, rows, tagsByContext, seeds, System.currentTimeMillis(), limit)
        )
    }

    private suspend fun soundtrackSeeds(accounts: MusicAccounts, romId: Long, albumKey: String?): List<MusicStatTrack> =
        rommIndexDao.tracksForRom(accounts.ownerKey, romId)
            .filter { (it.durationSeconds ?: 0.0) * SECONDS_TO_MS >= MusicListenRules.MIN_COUNTED_DURATION_MS }
            .sortedWith(
                compareBy<RommMusicTrackEntity>(
                    { it.disc ?: Int.MAX_VALUE },
                    { it.track ?: Int.MAX_VALUE },
                    { it.title.orEmpty().lowercase() }
                )
            )
            .map { track ->
                val trackKey = MusicKeys.rommTrackKey(track.romFileId)
                MusicStatTrack(
                    groupKey = trackKey,
                    trackKey = trackKey,
                    recordingKey = null,
                    source = MusicSourceKind.ROMM.token,
                    sourceAccountKey = accounts.rommAccountKey,
                    sourceTrackId = track.romFileId.toString(),
                    albumKey = albumKey,
                    romId = track.romId,
                    romFileId = track.romFileId,
                    title = track.title?.takeIf { it.isNotBlank() } ?: track.gameName.orEmpty(),
                    artistCredit = track.artist?.takeIf { it.isNotBlank() },
                    albumTitle = track.album?.takeIf { it.isNotBlank() } ?: track.gameName,
                    trackGameTitle = track.gameName,
                    durationMs = ((track.durationSeconds ?: 0.0) * SECONDS_TO_MS).toLong(),
                    listenedMs = 0,
                    playCount = 0,
                    lastListenedAt = 0
                )
            }

    private suspend fun <T> owned(block: suspend (MusicAccounts) -> T): T = withContext(Dispatchers.IO) {
        block(accountReader.current())
    }
}
