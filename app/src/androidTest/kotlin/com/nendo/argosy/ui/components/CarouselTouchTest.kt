package com.nendo.argosy.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nendo.argosy.ui.screens.home.HomeGameUi
import com.nendo.argosy.ui.screens.home.HomeMediaUi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CarouselTouchTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun enlargedGameAcceptsTouchWhileDockOwnsFocus() {
        assertEnlargedCardAcceptsTouch(
            CarouselItem.Game("game", HomeGameUi(
                id = 1, title = "Game", platformId = 1, platformSlug = "nes",
                platformDisplayName = "NES", platformShortName = "NES", coverPath = null, backgroundPath = null,
                developer = null, releaseYear = null, genre = null,
                isFavorite = false, isDownloaded = false
            ))
        )
    }

    @Test
    fun enlargedMediaAcceptsTouchWhileDockOwnsFocus() {
        assertEnlargedCardAcceptsTouch(
            CarouselItem.Media("media", HomeMediaUi("media", "Film", null, ""))
        )
    }

    private fun assertEnlargedCardAcceptsTouch(item: CarouselItem) {
        val scale = mutableStateOf(1f)
        var selected: Int? = null
        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(360.dp)) {
                    CarouselRail(
                        items = listOf(item),
                        focusedIndex = 0,
                        listState = rememberLazyListState(),
                        metrics = CarouselMetrics.hero(80.dp, 100.dp).copy(focusScale = scale.value),
                        showFocusVisuals = false,
                        verticalContentPadding = 120.dp,
                        availableWidth = 360.dp,
                        onItemTap = { selected = it }
                    )
                }
            }
        }
        val restingBounds = compose.onNode(hasClickAction()).fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { scale.value = 2f }
        compose.waitForIdle()
        compose.onRoot().performTouchInput {
            click(Offset(restingBounds.center.x, restingBounds.top - restingBounds.height / 3))
        }
        compose.runOnIdle { assertEquals(0, selected) }
    }
}
