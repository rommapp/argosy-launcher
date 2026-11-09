package com.nendo.argosy.data.repository

import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.ra.RAWebAchievementProgress
import com.nendo.argosy.data.remote.ra.RAWebAchievements
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
import com.nendo.argosy.data.remote.romm.RomMResult
import com.nendo.argosy.domain.model.SnapshotUnlock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RaMetadataSourceTest {

    private val end = Instant.parse("2026-10-01T12:00:00Z")
    private val credentials = RAWebCredentials("Player", "key-1234")

    private val webClient: RAWebClient = mockk()
    private val achievementService: RomMAchievementService = mockk(relaxed = true)
    private val raRepository: RetroAchievementsRepository = mockk()
    private val keyHealth = RAWebKeyHealth()

    private fun source(apiKey: String?): RaMetadataSource {
        val prefs = mockk<UserPreferencesRepository> {
            every { userPreferences } returns flowOf(UserPreferences(raUsername = "Player", raWebApiKey = apiKey))
        }
        return RaMetadataSource(webClient, achievementService, raRepository, prefs, keyHealth)
    }

    private fun webProgress(vararg achievements: RAWebAchievementProgress) =
        RAWebGameProgress(id = 55L, achievements = RAWebAchievements(achievements.associateBy { it.id.toString() }))

    private fun romMCurrentWith(vararg earned: RomMEarnedAchievement) {
        every { achievementService.progressionRefreshedAt() } returns end.plusSeconds(60)
        every { achievementService.hasProgressionFor(55L) } returns true
        every { achievementService.getEarnedAchievements(55L) } returns earned.toList()
    }

    @Test
    fun `progression refreshed before the window end is refreshed again`() {
        assertTrue(RaMetadataSource.needsRefresh(end.minusSeconds(1), end))
    }

    @Test
    fun `progression refreshed at or after the window end is trusted`() {
        assertFalse(RaMetadataSource.needsRefresh(end, end))
        assertFalse(RaMetadataSource.needsRefresh(end.plusSeconds(1), end))
    }

    @Test
    fun `progression never refreshed is refreshed`() {
        assertTrue(RaMetadataSource.needsRefresh(null, end))
    }

    @Test
    fun `warming a stale RomM cache refreshes it`() = runTest {
        every { achievementService.progressionRefreshedAt() } returns end.minusSeconds(60)
        coEvery { achievementService.refreshRAProgressionIfNeeded(true) } returns RomMResult.Success(Unit)

        source(null).warmRomMProgression(end)

        coVerify(exactly = 1) { achievementService.refreshRAProgressionIfNeeded(force = true) }
    }

    @Test
    fun `warming a current RomM cache asks RomM nothing`() = runTest {
        every { achievementService.progressionRefreshedAt() } returns end.plusSeconds(60)

        source(null).warmRomMProgression(end)

        coVerify(exactly = 0) { achievementService.refreshRAProgressionIfNeeded(any()) }
    }

    @Test
    fun `a RomM refresh that never answers releases the warm at its timeout`() = runTest {
        every { achievementService.progressionRefreshedAt() } returns null
        coEvery { achievementService.refreshRAProgressionIfNeeded(any()) } coAnswers { awaitCancellation() }

        source(null).warmRomMProgression(end)

        assertEquals(RaMetadataSource.ROMM_WARM_TIMEOUT_MS, currentTime)
    }

    @Test
    fun `with a key, unlocks come from the web and RomM is never asked`() = runTest {
        coEvery { webClient.gameProgress(credentials, 55L, end) } returns RAWebResult.Success(
            webProgress(RAWebAchievementProgress(9L, "00042", dateEarned = "2026-10-01 11:00:00"))
        )

        val evidence = source("key-1234").unlockEvidence(55L, end) as RaUnlockEvidence.Dated

        assertEquals(listOf(RomMEarnedAchievement("00042", date = "2026-10-01 11:00:00")), evidence.earned)
        coVerify(exactly = 0) { achievementService.refreshRAProgressionIfNeeded(any()) }
    }

    @Test
    fun `without a key, unlocks come from RomM and the web is never asked`() = runTest {
        romMCurrentWith(RomMEarnedAchievement("7", date = "2026-10-01 11:00:00"))

        val evidence = source(null).unlockEvidence(55L, end) as RaUnlockEvidence.Dated

        assertEquals(listOf("7"), evidence.earned.map { it.id })
        coVerify(exactly = 0) { webClient.gameProgress(any(), any(), any()) }
    }

    @Test
    fun `a rejected key falls back to RomM for that call`() = runTest {
        coEvery { webClient.gameProgress(any(), any(), any()) } returns RAWebResult.Rejected
        romMCurrentWith(RomMEarnedAchievement("7", date = "2026-10-01 11:00:00"))

        val evidence = source("key-1234").unlockEvidence(55L, end) as RaUnlockEvidence.Dated

        assertEquals(listOf("7"), evidence.earned.map { it.id })
    }

    @Test
    fun `a network failure on the web path falls back to RomM for that call`() = runTest {
        coEvery { webClient.gameProgress(any(), any(), any()) } returns RAWebResult.Failed("SocketTimeoutException")
        romMCurrentWith(RomMEarnedAchievement("7", date = "2026-10-01 11:00:00"))

        val evidence = source("key-1234").unlockEvidence(55L, end) as RaUnlockEvidence.Dated

        assertEquals(listOf("7"), evidence.earned.map { it.id })
    }

    @Test
    fun `stale RomM progression is refreshed before it is read`() = runTest {
        every { achievementService.progressionRefreshedAt() } returns end.minusSeconds(60)
        coEvery { achievementService.refreshRAProgressionIfNeeded(true) } returns RomMResult.Success(Unit)

        source(null).unlockEvidence(55L, end)

        coVerify(exactly = 1) { achievementService.refreshRAProgressionIfNeeded(true) }
    }

    @Test
    fun `when RomM cannot answer, the session's snapshot unlocks are used, dated at the window end`() = runTest {
        every { achievementService.progressionRefreshedAt() } returns null
        coEvery { achievementService.refreshRAProgressionIfNeeded(true) } returns RomMResult.Error("Not connected")
        val stored = listOf(SnapshotUnlock(9L, isHardcore = true))

        val evidence = source(null).unlockEvidence(55L, end, stored)

        assertEquals(RaUnlockEvidence.Snapshot(stored, end), evidence)
    }

    @Test
    fun `when RomM holds no progression for the game, the snapshot unlocks are used`() = runTest {
        every { achievementService.progressionRefreshedAt() } returns end.plusSeconds(60)
        every { achievementService.hasProgressionFor(55L) } returns false
        val stored = listOf(SnapshotUnlock(9L, isHardcore = false))

        val evidence = source(null).unlockEvidence(55L, end, stored)

        assertEquals(RaUnlockEvidence.Snapshot(stored, end), evidence)
    }

    @Test
    fun `RomM progression wins over a stored snapshot when it can answer`() = runTest {
        romMCurrentWith(RomMEarnedAchievement("7", date = "2026-10-01 11:00:00"))

        val evidence = source(null).unlockEvidence(55L, end, listOf(SnapshotUnlock(9L, isHardcore = true)))

        assertTrue(evidence is RaUnlockEvidence.Dated)
    }

    @Test
    fun `with no source able to answer and no snapshot, RomM's cache is what is left`() = runTest {
        every { achievementService.progressionRefreshedAt() } returns null
        coEvery { achievementService.refreshRAProgressionIfNeeded(true) } returns RomMResult.Error("Not connected")
        every { achievementService.getEarnedAchievements(55L) } returns emptyList()

        assertEquals(RaUnlockEvidence.Dated(emptyList()), source(null).unlockEvidence(55L, end, null))
    }

    @Test
    fun `web achievements earned in neither mode are left out`() {
        val earned = with(RaMetadataSource) {
            webProgress(
                RAWebAchievementProgress(1L, "001", dateEarned = "2026-10-01 11:00:00"),
                RAWebAchievementProgress(2L, "002", dateEarned = "2026-10-01 11:00:00", dateEarnedHardcore = "2026-10-01 11:00:00"),
                RAWebAchievementProgress(3L, "003")
            ).toEarnedAchievements()
        }
        assertEquals(listOf("001", "002"), earned.map { it.id })
        assertEquals("2026-10-01 11:00:00", earned.single { it.id == "002" }.dateHardcore)
    }

    @Test
    fun `with a key, the rich presence summary comes from the web`() = runTest {
        coEvery { webClient.userSummary(credentials) } returns RAWebResult.Success(
            RAWebUserSummary("Exploring", "2026-10-01 11:30:00", 55L)
        )

        val summary = source("key-1234").userSummary()

        assertEquals(RAWebUserSummary("Exploring", "2026-10-01 11:30:00", 55L), summary)
    }

    @Test
    fun `a failing web summary yields no summary`() = runTest {
        coEvery { webClient.userSummary(any()) } returns RAWebResult.Rejected

        assertEquals(null, source("key-1234").userSummary())
    }

    @Test
    fun `without a key there is no summary and the web is never asked`() = runTest {
        assertEquals(null, source(null).userSummary())

        coVerify(exactly = 0) { webClient.userSummary(any()) }
    }

    @Test
    fun `with a key, awards come from the web completion progress`() = runTest {
        coEvery { webClient.completionProgress(credentials) } returns RAWebResult.Success(
            listOf(RAWebCompletionEntry(gameId = 55L, highestAwardKind = "mastered", highestAwardDate = "2026-10-01T11:00:00+00:00"))
        )

        val awards = source("key-1234").awardProgression(end)

        assertEquals(listOf(RaGameAward(55L, "mastered", "2026-10-01T11:00:00+00:00")), awards)
    }

    @Test
    fun `a failing web completion progress falls back to RomM's progression`() = runTest {
        coEvery { webClient.completionProgress(any()) } returns RAWebResult.Failed("HTTP 500")
        every { achievementService.progressionRefreshedAt() } returns end.plusSeconds(60)
        every { achievementService.getProgression() } returns listOf(
            RomMRAGameProgression(romRaId = 56L, highestAwardKind = "beaten-hardcore"),
            RomMRAGameProgression(highestAwardKind = "mastered")
        )

        assertEquals(listOf(RaGameAward(56L, "beaten-hardcore", null)), source("key-1234").awardProgression(end))
    }

    @Test
    fun `a RomM mastery is dated by the last unlock once every achievement is hardcore`() {
        val award = with(RaMetadataSource) {
            RomMRAGameProgression(
                romRaId = 55L, maxPossible = 10, numAwarded = 10, numAwardedHardcore = 10,
                mostRecentAwardedDate = "2026-10-01 11:00:00", highestAwardKind = "mastered"
            ).toGameAward()
        }
        assertEquals(RaGameAward(55L, "mastered", "2026-10-01 11:00:00"), award)
    }

    @Test
    fun `a RomM mastery short of the full hardcore set stays undated`() {
        val award = with(RaMetadataSource) {
            RomMRAGameProgression(
                romRaId = 55L, maxPossible = 10, numAwarded = 10, numAwardedHardcore = 9,
                mostRecentAwardedDate = "2026-10-01 11:00:00", highestAwardKind = "mastered"
            ).toGameAward()
        }
        assertEquals(null, award?.awardedAt)
    }

    @Test
    fun `a RomM completion is dated once every achievement is earned in either mode`() {
        val award = with(RaMetadataSource) {
            RomMRAGameProgression(
                romRaId = 55L, maxPossible = 10, numAwarded = 10, numAwardedHardcore = 2,
                mostRecentAwardedDate = "2026-10-01 11:00:00", highestAwardKind = "completed"
            ).toGameAward()
        }
        assertEquals("2026-10-01 11:00:00", award?.awardedAt)
    }

    @Test
    fun `a RomM award with no achievements to count stays undated`() {
        val award = with(RaMetadataSource) {
            RomMRAGameProgression(
                romRaId = 55L, maxPossible = 0, numAwarded = 0, numAwardedHardcore = 0,
                mostRecentAwardedDate = "2026-10-01 11:00:00", highestAwardKind = "mastered"
            ).toGameAward()
        }
        assertEquals(null, award?.awardedAt)
    }

    @Test
    fun `a RomM beaten award stays undated`() {
        val award = with(RaMetadataSource) {
            RomMRAGameProgression(
                romRaId = 55L, maxPossible = 10, numAwarded = 10, numAwardedHardcore = 10,
                mostRecentAwardedDate = "2026-10-01 11:00:00", highestAwardKind = "beaten-hardcore"
            ).toGameAward()
        }
        assertEquals(null, award?.awardedAt)
    }

    @Test
    fun `snapshots are needed only when neither the key nor RomM covers the game`() = runTest {
        every { achievementService.hasProgressionFor(55L) } returns false
        assertTrue(source(null).needsUnlockSnapshots(55L))
        assertFalse(source("key-1234").needsUnlockSnapshots(55L))

        every { achievementService.hasProgressionFor(55L) } returns true
        assertFalse(source(null).needsUnlockSnapshots(55L))
    }

    @Test
    fun `a 401 or 403 on any web call marks the stored key rejected without clearing it`() = runTest {
        coEvery { webClient.gameProgress(any(), any(), any()) } returns RAWebResult.Rejected
        romMCurrentWith()

        source("key-1234").unlockEvidence(55L, end)

        assertEquals("key-1234", keyHealth.rejectedKey.value)
        assertTrue(RAWebKeyHealth.isRejected("key-1234", keyHealth.rejectedKey.value))
    }

    @Test
    fun `a later success with the same key lifts the rejection`() = runTest {
        coEvery { webClient.userSummary(any()) } returnsMany listOf(
            RAWebResult.Rejected,
            RAWebResult.Success(RAWebUserSummary("Exploring", "2026-10-01 11:30:00", 55L))
        )

        source("key-1234").userSummary()
        assertEquals("key-1234", keyHealth.rejectedKey.value)

        source("key-1234").userSummary()
        assertEquals(null, keyHealth.rejectedKey.value)
    }

    @Test
    fun `a network failure leaves the key's state alone`() = runTest {
        coEvery { webClient.completionProgress(any()) } returns RAWebResult.Failed("HTTP 500")
        every { achievementService.progressionRefreshedAt() } returns end.plusSeconds(60)

        source("key-1234").awardProgression(end)

        assertEquals(null, keyHealth.rejectedKey.value)
    }

    @Test
    fun `a rejection recorded for an old key does not mark a different stored key`() {
        assertFalse(RAWebKeyHealth.isRejected("new-key", "old-key"))
        assertFalse(RAWebKeyHealth.isRejected(null, "old-key"))
        assertFalse(RAWebKeyHealth.isRejected("", ""))
    }

    @Test
    fun `month unlocks come from the web key when it answers`() = runTest {
        val from = Instant.parse("2026-09-01T00:00:00Z")
        val unlock = RAWebEarnedAchievement(date = "2026-09-03 10:00:00", hardcoreMode = 1.0, achievementId = 9L, badgeName = "00042")
        coEvery { webClient.earnedBetween(credentials, from, end) } returns RAWebResult.Success(listOf(unlock))

        assertEquals(listOf(unlock), source("key-1234").webUnlocksBetween(from, end))
    }

    @Test
    fun `month unlocks hand back null without a key or when the web fails`() = runTest {
        val from = Instant.parse("2026-09-01T00:00:00Z")
        coEvery { webClient.earnedBetween(any(), any(), any()) } returns RAWebResult.Failed("HTTP 500")

        assertEquals(null, source(null).webUnlocksBetween(from, end))
        assertEquals(null, source("key-1234").webUnlocksBetween(from, end))
    }

    @Test
    fun `hardcore mode reads as a number, a boolean or a string`() {
        assertTrue(RAWebEarnedAchievement(hardcoreMode = 1.0).isHardcore)
        assertTrue(RAWebEarnedAchievement(hardcoreMode = true).isHardcore)
        assertTrue(RAWebEarnedAchievement(hardcoreMode = "1").isHardcore)
        assertFalse(RAWebEarnedAchievement(hardcoreMode = 0.0).isHardcore)
        assertFalse(RAWebEarnedAchievement(hardcoreMode = null).isHardcore)
    }

    @Test
    fun `a blank stored key counts as no key`() = runTest {
        romMCurrentWith()

        source("  ").unlockEvidence(55L, end)

        coVerify(exactly = 0) { webClient.gameProgress(any(), any(), any()) }
    }
}
