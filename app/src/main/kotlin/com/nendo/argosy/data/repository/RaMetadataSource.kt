package com.nendo.argosy.data.repository

import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.ra.RAWebClient
import com.nendo.argosy.data.remote.ra.RAWebCompletionEntry
import com.nendo.argosy.data.remote.ra.RAWebCredentials
import com.nendo.argosy.data.remote.ra.RAWebEarnedAchievement
import com.nendo.argosy.data.remote.ra.RAWebGameProgress
import com.nendo.argosy.data.remote.ra.RAWebResult
import com.nendo.argosy.data.remote.ra.RAWebUserSummary
import com.nendo.argosy.data.remote.romm.RomMAchievementService
import com.nendo.argosy.data.remote.romm.RomMEarnedAchievement
import com.nendo.argosy.data.remote.romm.RomMRAGameProgression
import com.nendo.argosy.domain.model.RecapAwardKind
import com.nendo.argosy.domain.model.SnapshotUnlock
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where a recap's unlocks come from. [Dated] carries per-achievement unlock dates keyed by badge id;
 * [Snapshot] carries a session's launch-to-end snapshot difference, every unlock dated [Snapshot.at].
 */
sealed interface RaUnlockEvidence {
    data class Dated(val earned: List<RomMEarnedAchievement>) : RaUnlockEvidence
    data class Snapshot(val unlocks: List<SnapshotUnlock>, val at: Instant) : RaUnlockEvidence
}

/**
 * A game's highest RetroAchievements award. [kind] is RA's award token; [awardedAt] is null when
 * the source cannot date the award.
 */
data class RaGameAward(
    val raGameId: Long,
    val kind: String?,
    val awardedAt: String?
)

/**
 * The one place that picks the source of RetroAchievements metadata for recaps. Unlocks try the
 * user's web API key first, then RomM's RA progression, then a session's stored unlock snapshots;
 * awards try the web API key, then RomM; rich presence comes from the web API key alone. A failing
 * source hands the call to the next one. Reads the RA username and web API key once per call.
 * Never calls `startsession` or `ping` and never uses the RA password.
 */
@Singleton
class RaMetadataSource @Inject constructor(
    private val webClient: RAWebClient,
    private val achievementService: RomMAchievementService,
    private val raRepository: RetroAchievementsRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val keyHealth: RAWebKeyHealth
) {

    /**
     * The user's rich-presence summary from the web API key, or null when no key is set or the
     * call fails.
     */
    suspend fun userSummary(): RAWebUserSummary? {
        val credentials = webCredentials() ?: return null
        return when (val result = webClient.userSummary(credentials).recordedFor(credentials)) {
            is RAWebResult.Success -> result.value
            else -> {
                Logger.debug(TAG, "Web user summary unavailable (${result.describe()})")
                null
            }
        }
    }

    /**
     * The evidence for [raGameId]'s unlocks up to [through]. [snapshotUnlocks] is the session's
     * stored snapshot difference, used only when neither dated source can answer; without one the
     * call ends on whatever RomM holds.
     */
    suspend fun unlockEvidence(
        raGameId: Long,
        through: Instant,
        snapshotUnlocks: List<SnapshotUnlock>? = null
    ): RaUnlockEvidence {
        webCredentials()?.let { credentials ->
            when (val result = webClient.gameProgress(credentials, raGameId, through).recordedFor(credentials)) {
                is RAWebResult.Success -> return RaUnlockEvidence.Dated(result.value.toEarnedAchievements())
                else -> Logger.debug(TAG, "Web progress for $raGameId unavailable (${result.describe()}), asking RomM")
            }
        }
        refreshRomMIfStale(through)
        val romMCurrent = !needsRefresh(achievementService.progressionRefreshedAt(), through)
        if (romMCurrent && achievementService.hasProgressionFor(raGameId)) {
            return RaUnlockEvidence.Dated(achievementService.getEarnedAchievements(raGameId))
        }
        if (snapshotUnlocks != null) return RaUnlockEvidence.Snapshot(snapshotUnlocks, through)
        return RaUnlockEvidence.Dated(achievementService.getEarnedAchievements(raGameId))
    }

    /**
     * Per-game highest awards through [through]: from the web API key when set and answering, else
     * RomM's progression.
     */
    suspend fun awardProgression(through: Instant): List<RaGameAward> {
        webCredentials()?.let { credentials ->
            when (val result = webClient.completionProgress(credentials).recordedFor(credentials)) {
                is RAWebResult.Success -> return result.value.map { it.toGameAward() }
                else -> Logger.debug(TAG, "Web completion progress unavailable (${result.describe()}), asking RomM")
            }
        }
        return romMProgression(through).mapNotNull { it.toGameAward() }
    }

    /**
     * Every unlock earned in [from, to] from the web API key, or null when no key is set or the
     * call fails.
     */
    suspend fun webUnlocksBetween(from: Instant, to: Instant): List<RAWebEarnedAchievement>? {
        val credentials = webCredentials() ?: return null
        return when (val result = webClient.earnedBetween(credentials, from, to).recordedFor(credentials)) {
            is RAWebResult.Success -> result.value
            else -> {
                Logger.debug(TAG, "Web unlocks between $from and $to unavailable (${result.describe()}), asking RomM")
                null
            }
        }
    }

    /**
     * RomM's progression, refreshed first when the cache predates [through].
     */
    suspend fun romMProgression(through: Instant): List<RomMRAGameProgression> {
        refreshRomMIfStale(through)
        return achievementService.getProgression()
    }

    /**
     * Refreshes RomM's progression when the cache predates [through], waiting at most
     * [ROMM_WARM_TIMEOUT_MS]. A refresh cut off by the timeout leaves the cache as it was.
     */
    suspend fun warmRomMProgression(through: Instant) {
        withTimeoutOrNull(ROMM_WARM_TIMEOUT_MS) { refreshRomMIfStale(through) }
            ?: Logger.debug(TAG, "RomM progression warm through $through ran past ${ROMM_WARM_TIMEOUT_MS}ms")
    }

    /**
     * Fetches [raGameId]'s web progress, fresh through [through], into the client's cache. Does
     * nothing without a web API key.
     */
    suspend fun warmGameProgress(raGameId: Long, through: Instant) {
        val credentials = webCredentials() ?: return
        val result = webClient.gameProgress(credentials, raGameId, through).recordedFor(credentials)
        if (result !is RAWebResult.Success) Logger.debug(TAG, "Web progress warm for $raGameId skipped (${result.describe()})")
    }

    /**
     * True when neither the web API key nor RomM's cached progression can answer for [raGameId],
     * the only case a session takes unlock snapshots.
     */
    suspend fun needsUnlockSnapshots(raGameId: Long): Boolean =
        webCredentials() == null && !achievementService.hasProgressionFor(raGameId)

    suspend fun unlockSnapshot(raGameId: Long): RetroAchievementsRepository.GameUnlocks? =
        raRepository.fetchUnlocksSnapshot(raGameId)

    private suspend fun refreshRomMIfStale(through: Instant) {
        if (needsRefresh(achievementService.progressionRefreshedAt(), through)) {
            achievementService.refreshRAProgressionIfNeeded(force = true)
        }
    }

    private suspend fun webCredentials(): RAWebCredentials? {
        val prefs = preferencesRepository.userPreferences.first()
        val username = prefs.raUsername?.takeIf { it.isNotBlank() } ?: return null
        val apiKey = prefs.raWebApiKey?.takeIf { it.isNotBlank() } ?: return null
        return RAWebCredentials(username, apiKey)
    }

    private fun <T> RAWebResult<T>.recordedFor(credentials: RAWebCredentials): RAWebResult<T> {
        keyHealth.record(credentials.apiKey, this)
        return this
    }

    companion object {
        private const val TAG = "RaMetadataSource"
        const val ROMM_WARM_TIMEOUT_MS = 15_000L

        fun needsRefresh(refreshedAt: Instant?, through: Instant): Boolean =
            refreshedAt == null || refreshedAt.isBefore(through)

        /**
         * The earned achievements of a web progress answer, keyed by badge name as RomM keys them.
         * Achievements earned in neither mode are left out.
         */
        fun RAWebGameProgress.toEarnedAchievements(): List<RomMEarnedAchievement> =
            achievements.byId.values.mapNotNull { achievement ->
                val badge = achievement.badgeName?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                if (achievement.dateEarned == null && achievement.dateEarnedHardcore == null) return@mapNotNull null
                RomMEarnedAchievement(
                    id = badge,
                    date = achievement.dateEarned,
                    dateHardcore = achievement.dateEarnedHardcore
                )
            }

        fun RAWebCompletionEntry.toGameAward(): RaGameAward = RaGameAward(
            raGameId = gameId,
            kind = highestAwardKind,
            awardedAt = highestAwardDate
        )

        /**
         * RomM's row as an award, or null without an RA game id. A mastery or completion is dated by
         * the most recent unlock once every achievement is earned in that award's mode; any other
         * award is undated.
         */
        fun RomMRAGameProgression.toGameAward(): RaGameAward? {
            val raGameId = romRaId ?: return null
            val earned = when (RecapAwardKind.fromToken(highestAwardKind)) {
                RecapAwardKind.MASTERED -> numAwardedHardcore
                RecapAwardKind.COMPLETED -> numAwarded
                else -> null
            }
            val total = maxPossible ?: 0
            val complete = earned != null && total > 0 && earned >= total
            return RaGameAward(
                raGameId = raGameId,
                kind = highestAwardKind,
                awardedAt = mostRecentAwardedDate.takeIf { complete }
            )
        }

        private fun RAWebResult<*>.describe(): String = when (this) {
            is RAWebResult.Success -> "ok"
            is RAWebResult.Rejected -> "key rejected"
            is RAWebResult.Failed -> reason
        }
    }
}
