package com.nendo.argosy.domain.usecase.recap

import com.nendo.argosy.data.local.dao.AchievementDao
import com.nendo.argosy.data.local.dao.GameArtDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlaySessionDao
import com.nendo.argosy.data.local.dao.resolvedFor
import com.nendo.argosy.data.local.entity.AchievementEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.MIN_DISPLAY_MS
import com.nendo.argosy.data.repository.RaMetadataSource
import com.nendo.argosy.data.social.uploader.PlaySessionPullScheduler
import com.nendo.argosy.domain.model.MonthlyDigest
import com.nendo.argosy.domain.model.RecapMonthUnlock
import com.nendo.argosy.domain.model.SessionUnlock
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * The monthly recap for [YearMonth] in the device's zone. Unlocks come from the RetroAchievements
 * web API key when it answers, else from RomM's progression resolved against the local
 * achievement rows. Games marked Private stay out of play time, unlocks and awards alike. Before
 * reading, waits for the pull of the other devices' play sessions to finish, bounded by
 * [PlaySessionPullScheduler.RECAP_PULL_TIMEOUT_MS], while RomM's RA progression refreshes, bounded
 * by [RaMetadataSource.ROMM_WARM_TIMEOUT_MS]; the digest is built from whatever is stored once both
 * end. Reads the RomM owner once per call.
 */
class GetMonthlyRecapUseCase @Inject constructor(
    private val playSessionDao: PlaySessionDao,
    private val metadataSource: RaMetadataSource,
    private val preferencesRepository: UserPreferencesRepository,
    private val gameDao: GameDao,
    private val gameArtDao: GameArtDao,
    private val achievementDao: AchievementDao,
    private val playSessionPullScheduler: PlaySessionPullScheduler
) {
    suspend operator fun invoke(month: YearMonth, zone: ZoneId = ZoneId.systemDefault()): MonthlyDigest {
        val window = RecapEligibility.monthWindow(month, zone)
        coroutineScope {
            launch { metadataSource.warmRomMProgression(window.end) }
            playSessionPullScheduler.pullForRecap()
        }
        val owner = preferencesRepository.userPreferences.first().rommUserId
        val previousStart = RecapEligibility.monthWindow(month.minusMonths(1), zone).start
        val hidden = preferencesRepository.privateIgdbIds()
        val sessions = playSessionDao.getSessionsForOwnerInRange(owner, previousStart, window.end, hidden)
        val firstPlayed = playSessionDao.getFirstPlayedPerGame(owner).associate { it.gameId to it.firstPlayed }
        val hiddenRaIds = privateRaIds(hidden)
        val webUnlocks = metadataSource.webUnlocksBetween(window.start, window.end)
            ?.filterNot { it.gameId in hiddenRaIds }
        val progression = if (webUnlocks == null) metadataSource.romMProgression(window.end) else emptyList()
        val recap = MonthlyRecapCalculator.compute(
            month = month,
            zone = zone,
            sessions = sessions,
            firstPlayed = firstPlayed,
            progression = progression.filterNot { it.romRaId in hiddenRaIds },
            topGamesLimit = Int.MAX_VALUE,
            awards = metadataSource.awardProgression(window.end).filterNot { it.raGameId in hiddenRaIds }
        )
        val unlocks = webUnlocks?.let { MonthlyDigestAssembler.webUnlocks(it, window) }
            ?: resolveRomMUnlocks(recap.unlocks, owner)
        val awardTitles = MonthlyDigestAssembler.webGameTitles(webUnlocks.orEmpty()) +
            recap.awards.map { it.raGameId }.distinct()
                .mapNotNull { raGameId -> gameDao.getByRaId(raGameId)?.let { raGameId to it.title } }
        val covers = gameArtDao.resolvedFor(recap.topGames.take(COVERED_GAMES).map { it.gameId })
            .mapValues { (_, art) -> art.coverPath }
        val days = MonthlyDigestAssembler.monthDays(
            month,
            playSessionDao.getActiveMsPerDay(owner, window.start, window.end, MIN_DISPLAY_MS, hidden)
        )
        return MonthlyDigestAssembler.assemble(recap, days, unlocks, covers, awardTitles)
    }

    private suspend fun privateRaIds(privateIgdbIds: List<Long>): Set<Long> =
        privateIgdbIds.flatMap { igdbId -> gameDao.getAllByIgdbId(igdbId) }
            .mapNotNullTo(mutableSetOf()) { it.effectiveRaId }

    private suspend fun resolveRomMUnlocks(unlocks: List<RecapMonthUnlock>, owner: Long?): List<SessionUnlock> =
        unlocks.groupBy { it.raGameId }
            .flatMap { (raGameId, rows) ->
                val game = gameDao.getByRaId(raGameId)
                val definitions = game
                    ?.let { achievementDao.getByGameId(it.id, owner ?: AchievementEntity.NO_OWNER) }
                    .orEmpty()
                GetSessionUnlocksUseCase.resolve(rows.map { it.unlock }, definitions)
                    .map { it.copy(gameTitle = game?.title) }
            }
            .let { MonthlyDigestAssembler.onePerBadge(it) }

    private companion object {
        const val COVERED_GAMES = 1
    }
}
