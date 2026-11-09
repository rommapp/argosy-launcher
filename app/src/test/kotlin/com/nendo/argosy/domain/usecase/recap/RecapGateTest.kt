package com.nendo.argosy.domain.usecase.recap

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlaySessionDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.social.uploader.PlaySessionPullScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class RecapGateTest {

    private val now = Instant.parse("2026-10-13T12:00:00Z")
    private val gameDao: GameDao = mockk()
    private val playSessionDao: PlaySessionDao = mockk()
    private val preferencesRepository: UserPreferencesRepository = mockk()
    private val playSessionPullScheduler: PlaySessionPullScheduler = mockk {
        coEvery { pullForRecap() } returns true
    }

    private fun gate(enabled: Boolean, baseline: Instant? = now.minus(Duration.ofDays(365))): RecapGate {
        every { preferencesRepository.userPreferences } returns flowOf(
            UserPreferences(rommUserId = 7L, preLaunchRecapsEnabled = enabled, preLaunchRecapBaseline = baseline)
        )
        coEvery { preferencesRepository.setPreLaunchRecapBaseline(any()) } returns Unit
        val game = mockk<GameEntity> {
            every { effectiveRaId } returns 50L
            every { raId } returns 50L
        }
        coEvery { gameDao.getById(1L) } returns game
        return RecapGate(preferencesRepository, playSessionDao, gameDao, playSessionPullScheduler)
    }

    private fun lastSessionEndedAgo(gap: Duration) {
        coEvery { playSessionDao.getLatestForGameAndOwner(1L, 7L) } returns
            session(gameId = 1L, start = now.minus(gap).minusSeconds(3600), activeMinutes = 60, end = now.minus(gap))
    }

    @Test
    fun `a session over a week old for the connected owner is due`() = runTest {
        lastSessionEndedAgo(Duration.ofDays(10))
        assertEquals(10L, gate(enabled = true).preLaunchRecap(1L, now)!!.lastSession.daysSince)
    }

    @Test
    fun `a recent session is not due`() = runTest {
        lastSessionEndedAgo(Duration.ofHours(3))
        assertNull(gate(enabled = true).preLaunchRecap(1L, now))
    }

    @Test
    fun `the first check after the update records today and holds back an old session`() = runTest {
        lastSessionEndedAgo(Duration.ofDays(400))

        assertNull(gate(enabled = true, baseline = null).preLaunchRecap(1L, now))
        coVerify(exactly = 1) { preferencesRepository.setPreLaunchRecapBaseline(now) }
    }

    @Test
    fun `a session before the baseline is due a week after the baseline, not before`() = runTest {
        lastSessionEndedAgo(Duration.ofDays(400))

        assertNull(gate(enabled = true, baseline = now.minus(Duration.ofDays(3))).preLaunchRecap(1L, now))
        val due = gate(enabled = true, baseline = now.minus(Duration.ofDays(8))).preLaunchRecap(1L, now)
        assertEquals(400L, due!!.lastSession.daysSince)
    }

    @Test
    fun `the setting off answers before any lookup`() = runTest {
        lastSessionEndedAgo(Duration.ofDays(10))
        assertNull(gate(enabled = false).preLaunchRecap(1L, now))
        coVerify(exactly = 0) { gameDao.getById(any()) }
        coVerify(exactly = 0) { playSessionDao.getLatestForGameAndOwner(any(), any()) }
    }

    private fun monthlyGate(
        enabled: Boolean = true,
        lastShown: YearMonth? = YearMonth.of(2026, 1),
        privateIds: List<Long> = emptyList()
    ): RecapGate {
        every { preferencesRepository.userPreferences } returns flowOf(
            UserPreferences(rommUserId = 7L, monthlyRecapEnabled = enabled, lastMonthlyRecapShown = lastShown)
        )
        coEvery { preferencesRepository.setLastMonthlyRecapShown(any()) } returns Unit
        coEvery { preferencesRepository.privateIgdbIds() } returns privateIds
        return RecapGate(preferencesRepository, playSessionDao, gameDao, playSessionPullScheduler)
    }

    @Test
    fun `a device with no local sessions is due once the awaited pull stores another device's rows`() = runTest {
        var stored = false
        coEvery { playSessionPullScheduler.pullForRecap() } coAnswers {
            stored = true
            true
        }
        coEvery { playSessionDao.hasSessionsForOwnerInRange(7L, any(), any(), any()) } answers { stored }

        assertEquals(YearMonth.of(2026, 9), monthlyGate().monthlyDigestDue(now, ZoneOffset.UTC))
        coVerifyOrder {
            playSessionPullScheduler.pullForRecap()
            playSessionDao.hasSessionsForOwnerInRange(7L, any(), any(), any())
        }
    }

    @Test
    fun `a pull that fails still decides from the stored sessions`() = runTest {
        coEvery { playSessionPullScheduler.pullForRecap() } returns false
        coEvery { playSessionDao.hasSessionsForOwnerInRange(7L, any(), any(), any()) } returns true

        assertEquals(YearMonth.of(2026, 9), monthlyGate().monthlyDigestDue(now, ZoneOffset.UTC))
    }

    @Test
    fun `the first check, a month already shown and the setting off never pull`() = runTest {
        assertNull(monthlyGate(lastShown = null).monthlyDigestDue(now, ZoneOffset.UTC))
        assertNull(monthlyGate(lastShown = YearMonth.of(2026, 9)).monthlyDigestDue(now, ZoneOffset.UTC))
        assertNull(monthlyGate(enabled = false).monthlyDigestDue(now, ZoneOffset.UTC))

        coVerify(exactly = 0) { playSessionPullScheduler.pullForRecap() }
    }

    @Test
    fun `the first check after the update marks last month shown so the first digest is next month's`() = runTest {
        coEvery { playSessionDao.hasSessionsForOwnerInRange(7L, any(), any(), any()) } returns true

        assertNull(monthlyGate(lastShown = null).monthlyDigestDue(now, ZoneOffset.UTC))
        coVerify(exactly = 1) { preferencesRepository.setLastMonthlyRecapShown(YearMonth.of(2026, 9)) }
        coVerify(exactly = 0) { playSessionDao.hasSessionsForOwnerInRange(any(), any(), any(), any()) }
    }

    @Test
    fun `last month is due when it had sessions and was never shown`() = runTest {
        coEvery { playSessionDao.hasSessionsForOwnerInRange(7L, any(), any(), any()) } returns true
        assertEquals(YearMonth.of(2026, 9), monthlyGate().monthlyDigestDue(now, ZoneOffset.UTC))
    }

    @Test
    fun `a month already shown is not due again and the sessions are never queried`() = runTest {
        assertNull(monthlyGate(lastShown = YearMonth.of(2026, 9)).monthlyDigestDue(now, ZoneOffset.UTC))
        coVerify(exactly = 0) { playSessionDao.hasSessionsForOwnerInRange(any(), any(), any(), any()) }
    }

    @Test
    fun `a month with no sessions is skipped`() = runTest {
        coEvery { playSessionDao.hasSessionsForOwnerInRange(7L, any(), any(), any()) } returns false
        assertNull(monthlyGate().monthlyDigestDue(now, ZoneOffset.UTC))
    }

    @Test
    fun `games marked private are left out of the sessions that make a month due`() = runTest {
        coEvery { playSessionDao.hasSessionsForOwnerInRange(7L, any(), any(), listOf(42L)) } returns false

        assertNull(monthlyGate(privateIds = listOf(42L)).monthlyDigestDue(now, ZoneOffset.UTC))
        coVerify(exactly = 1) { playSessionDao.hasSessionsForOwnerInRange(7L, any(), any(), listOf(42L)) }
    }

    @Test
    fun `the setting off answers before the sessions are queried`() = runTest {
        assertNull(monthlyGate(enabled = false).monthlyDigestDue(now, ZoneOffset.UTC))
        coVerify(exactly = 0) { playSessionDao.hasSessionsForOwnerInRange(any(), any(), any(), any()) }
    }

    @Test
    fun `marking a month shown stores it`() = runTest {
        monthlyGate().markDigestShown(YearMonth.of(2026, 9))
        coVerify(exactly = 1) { preferencesRepository.setLastMonthlyRecapShown(YearMonth.of(2026, 9)) }
    }

    @Test
    fun `the sessions query covers the whole month from its first local midnight to the next month's`() = runTest {
        val tokyo = ZoneId.of("Asia/Tokyo")
        coEvery { playSessionDao.hasSessionsForOwnerInRange(7L, any(), any(), any()) } returns true

        monthlyGate().monthlyDigestDue(Instant.parse("2026-10-01T00:30:00+09:00"), tokyo)

        coVerify(exactly = 1) {
            playSessionDao.hasSessionsForOwnerInRange(
                7L,
                Instant.parse("2026-08-31T15:00:00Z"),
                Instant.parse("2026-09-30T15:00:00Z"),
                any()
            )
        }
    }

    @Test
    fun `the device zone decides which month is last month at the boundary`() = runTest {
        coEvery { playSessionDao.hasSessionsForOwnerInRange(7L, any(), any(), any()) } returns true
        val instant = Instant.parse("2026-10-31T23:30:00Z")

        assertEquals(YearMonth.of(2026, 9), monthlyGate().monthlyDigestDue(instant, ZoneOffset.UTC))
        assertEquals(YearMonth.of(2026, 10), monthlyGate().monthlyDigestDue(instant, ZoneId.of("Europe/Berlin")))
    }

    @Test
    fun `the month window holds its first instant and excludes the next month's first instant`() {
        val window = RecapEligibility.monthWindow(YearMonth.of(2026, 2), ZoneOffset.UTC)
        assertTrue(Instant.parse("2026-02-01T00:00:00Z") in window)
        assertTrue(Instant.parse("2026-02-28T23:59:59.999Z") in window)
        assertFalse(Instant.parse("2026-03-01T00:00:00Z") in window)
        assertFalse(Instant.parse("2026-01-31T23:59:59.999Z") in window)
    }
}
