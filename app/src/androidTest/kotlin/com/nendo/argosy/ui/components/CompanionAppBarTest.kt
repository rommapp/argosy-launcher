package com.nendo.argosy.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.UiScaleConfig
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompanionAppBarTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun pinnedAppRemainsVisibleAndTouchableWhenNavigationIsNarrow() {
        val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        var launchedPackage: String? = null
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUiScale provides UiScaleConfig(scale = 1.5f)) {
                    Box(Modifier.width(320.dp)) {
                        CompanionAppBar(
                            apps = listOf(packageName),
                            onAppClick = { launchedPackage = it },
                            maximumWidth = 104.dp,
                            modifier = Modifier.padding(horizontal = Dimens.spacingMd)
                        )
                    }
                }
            }
        }

        compose.onNodeWithContentDescription(packageName)
            .assertIsDisplayed()
            .performTouchInput { click() }
        compose.runOnIdle { assertEquals(packageName, launchedPackage) }
    }
}
