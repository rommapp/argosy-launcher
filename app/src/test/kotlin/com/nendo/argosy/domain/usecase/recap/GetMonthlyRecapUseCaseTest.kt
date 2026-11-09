package com.nendo.argosy.domain.usecase.recap

import com.nendo.argosy.data.local.dao.AchievementDao
import com.nendo.argosy.data.local.dao.GameArtDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlaySessionDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.ra.RAWebEarnedAchievement
import com.nendo.argosy.data.remote.romm.RomMEarnedAchievement
import com.nendo.argosy.data.remote.romm.RomMRAGameProgression
import com.nendo.argosy.data.repository.RaGameAward
import com.nendo.argosy.data.repository.RaMetadataSource
import com.nendo.argosy.data.social.uploader.PlaySessionPullScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

class GetMonthlyRecapUseCaseTest {

    private val playSessionDao: PlaySessionDao = mockk()
    private val metadataSource: RaMetadataSource = mockk()
    private val preferencesRepository: UserPreferencesRepository = mockk()
    private val gameDao: GameDao = mockk()
    private val gameArtDao: GameArtDao = mockk()
    private val achievementDao: AchievementDao = mockk()
    private val playSessionPullScheduler: PlaySessionPullScheduler = mockk {
        coEvery { pullForRecap() } returns true
    }
    private val useCase = GetMonthlyRecapUseCase(
        playSessionDao,
        metadataSource,
        preferencesRepository,
        gameDao,
        gameArtDao,
        achievementDao,
        playSessionPullScheduler
    )

    private fun stubEmptyMonth() {
        val preferences = mockk<UserPreferences>(relaxed = true)
        every { preferences.rommUserId } returns 7L
        every { preferencesRepository.userPreferences } returns flowOf(preferences)
        coEvery { preferencesRepository.privateIgdbIds() } returns emptyList()
        coEvery { playSessionDao.getSessionsForOwnerInRange(any(), any(), any(), any()) } returns emptyList()
        coEvery { playSessionDao.getFirstPlayedPerGame(any()) } returns emptyList()
        coEvery { playSessionDao.getActiveMsPerDay(any(), any(), any(), any(), any()) } returns emptyList()
        coEvery { metadataSource.webUnlocksBetween(any(), any()) } returns null
        coEvery { metadataSource.awardProgression(any()) } returns emptyList()
        coEvery { metadataSource.romMProgression(any()) } returns emptyList()
        coEvery { metadataSource.warmRomMProgression(any()) } returns Unit
    }

    @Test
    fun `the digest waits for the pull and the RA warm to finish before reading local rows`() = runTest {
        stubEmptyMonth()

        useCase(YearMonth.of(2025, 12), ZoneOffset.UTC)

        coVerifyOrder {
            playSessionPullScheduler.pullForRecap()
            playSessionDao.getSessionsForOwnerInRange(7L, any(), any(), any())
        }
        coVerifyOrder {
            metadataSource.warmRomMProgression(Instant.parse("2026-01-01T00:00:00Z"))
            playSessionDao.getSessionsForOwnerInRange(7L, any(), any(), any())
            metadataSource.romMProgression(any())
        }
    }

    @Test
    fun `a pull that outlasts four seconds still lands its sessions in the digest`() = runTest {
        stubEmptyMonth()
        coEvery { gameArtDao.getForGames(any()) } returns emptyList()
        var stored = false
        coEvery { playSessionPullScheduler.pullForRecap() } coAnswers {
            delay(12_000)
            stored = true
            true
        }
        coEvery { playSessionDao.getSessionsForOwnerInRange(7L, any(), any(), any()) } answers {
            if (stored) listOf(session(gameId = 1L, start = Instant.parse("2025-12-10T10:00:00Z"), activeMinutes = 90))
            else emptyList()
        }

        val digest = useCase(YearMonth.of(2025, 12), ZoneOffset.UTC)

        assertEquals(1, digest.sessionCount)
        assertTrue(currentTime >= 12_000)
    }

    @Test
    fun `a pull that times out or fails still builds the digest from local rows`() = runTest {
        stubEmptyMonth()
        coEvery { playSessionPullScheduler.pullForRecap() } returns false

        val digest = useCase(YearMonth.of(2025, 12), ZoneOffset.UTC)

        assertTrue(digest.isEmpty)
        coVerify(exactly = 1) { playSessionDao.getSessionsForOwnerInRange(7L, any(), any(), any()) }
    }

    @Test
    fun `a romm fallback unlock carries its local game's title, and none when the game is not in the library`() = runTest {
        stubEmptyMonth()
        coEvery { metadataSource.romMProgression(any()) } returns listOf(
            RomMRAGameProgression(
                romRaId = 55L,
                earnedAchievements = listOf(RomMEarnedAchievement("00042", date = "2025-12-03 10:00:00"))
            ),
            RomMRAGameProgression(
                romRaId = 56L,
                earnedAchievements = listOf(RomMEarnedAchievement("00043", date = "2025-12-04 10:00:00"))
            )
        )
        val game = mockk<GameEntity> {
            every { id } returns 1L
            every { title } returns "Donkey Kong Country"
        }
        coEvery { gameDao.getByRaId(55L) } returns game
        coEvery { gameDao.getByRaId(56L) } returns null
        coEvery { achievementDao.getByGameId(1L, 7L) } returns emptyList()

        val digest = useCase(YearMonth.of(2025, 12), ZoneOffset.UTC)

        assertEquals(listOf("00042", "00043"), digest.unlocks.map { it.badgeId })
        assertEquals(listOf("Donkey Kong Country", null), digest.unlocks.map { it.gameTitle })
    }

    private fun stubPrivateAndPublicGames() {
        val secret = mockk<GameEntity> {
            every { id } returns 1L
            every { title } returns "Secret Game"
            every { effectiveRaId } returns 55L
        }
        val shown = mockk<GameEntity> {
            every { id } returns 2L
            every { title } returns "Shown Game"
        }
        coEvery { preferencesRepository.privateIgdbIds() } returns listOf(900L)
        coEvery { gameDao.getAllByIgdbId(900L) } returns listOf(secret)
        coEvery { gameDao.getByRaId(55L) } returns secret
        coEvery { gameDao.getByRaId(56L) } returns shown
        coEvery { achievementDao.getByGameId(any(), 7L) } returns emptyList()
        coEvery { metadataSource.awardProgression(any()) } returns listOf(
            RaGameAward(55L, "mastered", "2025-12-05 10:00:00"),
            RaGameAward(56L, "mastered", "2025-12-06 10:00:00")
        )
    }

    @Test
    fun `a private game's romm unlocks and awards stay out of the digest`() = runTest {
        stubEmptyMonth()
        stubPrivateAndPublicGames()
        coEvery { metadataSource.romMProgression(any()) } returns listOf(
            RomMRAGameProgression(
                romRaId = 55L,
                earnedAchievements = listOf(RomMEarnedAchievement("00042", date = "2025-12-03 10:00:00"))
            ),
            RomMRAGameProgression(
                romRaId = 56L,
                earnedAchievements = listOf(RomMEarnedAchievement("00043", date = "2025-12-04 10:00:00"))
            )
        )

        val digest = useCase(YearMonth.of(2025, 12), ZoneOffset.UTC)

        assertEquals(listOf("00043"), digest.unlocks.map { it.badgeId })
        assertEquals(listOf(56L), digest.awards.map { it.raGameId })
    }

    @Test
    fun `a private game's web unlocks and awards stay out of the digest`() = runTest {
        stubEmptyMonth()
        stubPrivateAndPublicGames()
        coEvery { metadataSource.webUnlocksBetween(any(), any()) } returns listOf(
            RAWebEarnedAchievement(
                date = "2025-12-03 10:00:00",
                badgeName = "00042",
                gameId = 55L,
                gameTitle = "Secret Game"
            ),
            RAWebEarnedAchievement(
                date = "2025-12-04 10:00:00",
                badgeName = "00043",
                gameId = 56L,
                gameTitle = "Shown Game"
            )
        )

        val digest = useCase(YearMonth.of(2025, 12), ZoneOffset.UTC)

        assertEquals(listOf("00043"), digest.unlocks.map { it.badgeId })
        assertEquals(listOf("Shown Game"), digest.awards.map { it.gameTitle })
        coVerify(exactly = 0) { metadataSource.romMProgression(any()) }
    }
}
