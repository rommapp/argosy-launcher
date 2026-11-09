package com.nendo.argosy.ui.dualscreen.media

import com.nendo.argosy.DualScreenManager
import com.nendo.argosy.data.local.entity.MediaItemEntity
import com.nendo.argosy.data.repository.MediaRepository
import com.nendo.argosy.ui.screens.media.MediaSeasonUi
import com.nendo.argosy.ui.screens.media.delegates.MediaSeriesDelegate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CompanionMediaPresenterTest {

    private val repository: MediaRepository = mockk(relaxed = true)
    private val seriesDelegate: MediaSeriesDelegate = mockk(relaxed = true)
    private val manager: DualScreenManager = mockk(relaxed = true) {
        every { mediaRepository } returns repository
        every { mediaSeriesDelegate } returns seriesDelegate
        every { mediaAvailabilityVerifier } returns null
        every { gradientExtractionDelegate.mediaGradients } returns kotlinx.coroutines.flow.MutableStateFlow(emptyMap())
    }
    private val playing = MediaItemEntity(
        ownerUserId = "user",
        itemId = EPISODE,
        itemType = "Episode",
        name = "Pilot",
        sortName = "pilot",
        parentId = SEASON_ONE,
        seriesId = SERIES
    )
    private val seasonTwoNeverReturns = CompletableDeferred<String?>()

    @Test
    fun `a season fetch cancelled by switching away clears the spinner and runs again on return`() =
        runTest(UnconfinedTestDispatcher()) {
            every { repository.observeItem(EPISODE) } returns flowOf(playing)
            coEvery { repository.getUserData(any()) } returns null
            every { seriesDelegate.seasonsFlow(SERIES) } returns flowOf(
                listOf(season(SEASON_ONE, 1), season(SEASON_TWO, 2))
            )
            every { seriesDelegate.episodesFlow(any(), any()) } returns flowOf(emptyList())
            coEvery { seriesDelegate.refreshEpisodes(SERIES, SEASON_ONE) } returns null
            var seasonTwoCalls = 0
            coEvery { seriesDelegate.refreshEpisodes(SERIES, SEASON_TWO) } coAnswers {
                seasonTwoCalls++
                if (seasonTwoCalls == 1) seasonTwoNeverReturns.await() else null
            }
            val presenter = CompanionMediaPresenter(manager)
            backgroundScope.launch { presenter.follow(EPISODE) }

            presenter.selectSeason(1)
            presenter.selectSeason(0)

            assertFalse(presenter.state.value.isFetchingEpisodes)

            presenter.selectSeason(1)

            coVerify(exactly = 2) { seriesDelegate.refreshEpisodes(SERIES, SEASON_TWO) }
        }

    private fun season(id: String, number: Int) = MediaSeasonUi(
        itemId = id,
        name = "Season $number",
        seasonNumber = number,
        episodeCount = 1
    )

    private companion object {
        const val SERIES = "series"
        const val SEASON_ONE = "season-1"
        const val SEASON_TWO = "season-2"
        const val EPISODE = "episode"
    }
}
