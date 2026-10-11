package com.nendo.argosy.ui.dualscreen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nendo.argosy.domain.model.PresentationStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameShowcaseScrollTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun changingGameResetsScrollWhileRefreshingSameGamePreservesIt() {
        val stats = CompanionGameStats(
            developer = "Test studio",
            releaseYear = 2001,
            players = "1-4",
            genre = "Role-playing",
            communityRating = 85f,
            userRating = 4,
            userDifficulty = 3,
            playTimeMinutes = 120,
            timeToBeatMainSec = 3600,
            achievementCount = 20,
            earnedAchievementCount = 5
        )
        val selected = mutableStateOf(
            CompanionDetail(
                gameId = 1L,
                title = "Game A",
                subtitle = "Test platform",
                stats = stats
            )
        )
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxWidth().height(160.dp)) {
                    GameShowcase(
                        detail = selected.value,
                        style = PresentationStyle(hiddenStats = emptySet()),
                        bottomInset = 0.dp
                    )
                }
            }
        }

        val metadata = compose.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        )
        fun scrollRange() = metadata.fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]

        assertTrue("Game A should have scrollable metadata", scrollRange().maxValue() > 0f)
        metadata.performScrollToIndex(1)
        compose.waitForIdle()
        assertTrue(scrollRange().value() > 0f)

        compose.runOnIdle {
            selected.value = selected.value.copy(gameId = 2L, title = "Game B")
        }
        compose.waitForIdle()
        assertEquals(0f, scrollRange().value(), 0f)

        assertTrue("Game B should have scrollable metadata", scrollRange().maxValue() > 0f)
        metadata.performScrollToIndex(1)
        compose.waitForIdle()
        val beforeRefresh = scrollRange().value()
        assertTrue(beforeRefresh > 0f)
        compose.runOnIdle {
            selected.value = selected.value.copy(stats = stats.copy(userRating = 5))
        }
        compose.waitForIdle()
        assertEquals(beforeRefresh, scrollRange().value(), 0f)
    }
}
