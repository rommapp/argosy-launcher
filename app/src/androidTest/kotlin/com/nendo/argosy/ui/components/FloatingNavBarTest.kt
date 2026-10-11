package com.nendo.argosy.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nendo.argosy.R
import com.nendo.argosy.ui.navigation.NavRing
import com.nendo.argosy.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FloatingNavBarTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun narrowNavigationKeepsDestinationsReachableAndRevealsCurrentRoute() {
        val currentRoute = mutableStateOf(Screen.Settings.route)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(320.dp)) {
                    FloatingNavBar(
                        visible = true,
                        destinations = NavRing.DEFAULT_TOKENS.mapNotNull(NavRing::page),
                        currentRoute = currentRoute.value,
                        onNavigate = { currentRoute.value = it },
                        onInteract = {}
                    )
                }
            }
        }

        compose.onNodeWithContentDescription(context.getString(R.string.ui_drawer_nav_settings))
            .assertIsDisplayed()
        compose.runOnIdle { currentRoute.value = Screen.Home.route }
        compose.onNodeWithContentDescription(context.getString(R.string.ui_drawer_nav_home))
            .assertIsDisplayed()

        compose.onNode(hasScrollToIndexAction())
            .performScrollToKey(Screen.Settings.route)
        compose.onNodeWithContentDescription(context.getString(R.string.ui_drawer_nav_settings))
            .assertIsDisplayed()
            .performClick()
        compose.runOnIdle {
            assertEquals(Screen.Settings.route, currentRoute.value)
        }
    }
}
