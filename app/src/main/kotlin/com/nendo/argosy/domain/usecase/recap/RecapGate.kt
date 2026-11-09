package com.nendo.argosy.domain.usecase.recap

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlaySessionDao
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.social.uploader.PlaySessionPullScheduler
import com.nendo.argosy.domain.model.PreLaunchRecap
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * Decides whether a recap is due, from the stored session facts and the two recap settings. Reads
 * the settings once per call. A monthly digest still unshown waits for the pull of the other
 * devices' sessions, bounded by [PlaySessionPullScheduler.RECAP_PULL_TIMEOUT_MS], before the stored
 * sessions are checked. Never contacts RetroAchievements.
 */
class RecapGate @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val playSessionDao: PlaySessionDao,
    private val gameDao: GameDao,
    private val playSessionPullScheduler: PlaySessionPullScheduler
) {
    suspend fun preLaunchRecap(gameId: Long, now: Instant = Instant.now()): PreLaunchRecap? {
        val prefs = preferencesRepository.userPreferences.first()
        if (!prefs.preLaunchRecapsEnabled) return null
        val baseline = prefs.preLaunchRecapBaseline
            ?: now.also { preferencesRepository.setPreLaunchRecapBaseline(it) }
        val game = gameDao.getById(gameId) ?: return null
        return RecapEligibility.preLaunchRecapFor(
            gameRaId = game.effectiveRaId ?: game.raId,
            lastSession = playSessionDao.getLatestForGameAndOwner(gameId, prefs.rommUserId),
            now = now,
            enabled = prefs.preLaunchRecapsEnabled,
            notBefore = baseline
        )
    }

    suspend fun monthlyDigestDue(
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): YearMonth? {
        val prefs = preferencesRepository.userPreferences.first()
        if (prefs.lastMonthlyRecapShown == null) {
            preferencesRepository.setLastMonthlyRecapShown(RecapEligibility.digestMonthAt(now, zone))
            return null
        }
        val candidate = RecapEligibility.monthlyDigestDue(
            now = now,
            zone = zone,
            lastShownMonth = prefs.lastMonthlyRecapShown,
            hadSessionsLastMonth = true,
            enabled = prefs.monthlyRecapEnabled
        ) ?: return null
        playSessionPullScheduler.pullForRecap()
        val window = RecapEligibility.monthWindow(candidate, zone)
        val hadSessions = playSessionDao.hasSessionsForOwnerInRange(
            prefs.rommUserId,
            window.start,
            window.end,
            preferencesRepository.privateIgdbIds()
        )
        return candidate.takeIf { hadSessions }
    }

    suspend fun markDigestShown(month: YearMonth) {
        preferencesRepository.setLastMonthlyRecapShown(month)
    }
}
