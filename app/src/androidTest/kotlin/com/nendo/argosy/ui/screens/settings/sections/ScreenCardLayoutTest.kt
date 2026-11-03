package com.nendo.argosy.ui.screens.settings.sections

import android.content.Context
import android.os.ParcelFileDescriptor
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nendo.argosy.domain.model.ScreenRole
import com.nendo.argosy.ui.screens.settings.ScreenAssignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenCardLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val phone = ScreenAssignment(
        key = "phone", displayId = 0, number = 1,
        widthPx = 2400, heightPx = 1200, builtIn = true, role = ScreenRole.PRIMARY
    )

    @Test
    fun focusedCardRemainsVisiblyDifferentInBothThemesAndOffRole() {
        val focused = mutableStateOf(false)
        val dark = mutableStateOf(true)
        val role = mutableStateOf(ScreenRole.PRIMARY)
        compose.setContent {
            MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                Box(Modifier.testTag("card")) {
                    ScreenCard(phone.copy(role = role.value), 0.1.dp, focused.value) {}
                }
            }
        }
        for (isDark in listOf(true, false)) {
            for (screenRole in listOf(ScreenRole.PRIMARY, ScreenRole.OFF)) {
                compose.runOnIdle {
                    dark.value = isDark
                    role.value = screenRole
                    focused.value = false
                }
                val before = compose.onNodeWithTag("card").captureToImage().toPixelMap()
                compose.runOnIdle { focused.value = true }
                compose.mainClock.advanceTimeBy(1_000)
                compose.waitForIdle()
                val after = compose.onNodeWithTag("card").captureToImage().toPixelMap()
                val x = before.width * 4 / 5
                val y = before.height / 4
                assertTrue(
                    "Focused card fill must remain visible over its background: dark=$isDark role=$screenRole",
                    before[x, y] != after[x, y]
                )
            }
        }
    }

    @Test
    fun originalCardKeepsItsScreenNumberAndRole() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val screen = mutableStateOf(phone)
        compose.setContent {
            MaterialTheme {
                ScreenCard(screen.value, 0.1.dp, false) {}
            }
        }
        compose.onNodeWithText("1").assertIsDisplayed()
        compose.onNodeWithText(context.getString(screenRoleTitleRes(ScreenRole.PRIMARY))).assertIsDisplayed()
        compose.runOnIdle {
            screen.value = phone.copy(key = "tv", displayId = 4, number = 2, builtIn = false, role = ScreenRole.APP_TARGET)
        }
        compose.onNodeWithText("2").assertIsDisplayed()
        compose.onNodeWithText(context.getString(screenRoleTitleRes(ScreenRole.APP_TARGET))).assertIsDisplayed()
    }

    @Test
    fun setupHostIdentityWorksWithoutOverlayPermissionAndHidesUnknownHosts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        withOverlayPermissionDenied(context) {
            val number = mutableStateOf<Int?>(1)
            compose.setContent {
                MaterialTheme {
                    ScreenSetupIdentity(number.value)
                }
            }
            compose.onNodeWithText("1").assertIsDisplayed()
            compose.runOnIdle { number.value = null }
            compose.onNodeWithText("1").assertDoesNotExist()
        }
    }

    private fun withOverlayPermissionDenied(context: Context, testBody: () -> Unit) {
        val wasAllowed = Settings.canDrawOverlays(context)
        val previousMode = overlayPermissionMode(context)
            ?: if (wasAllowed) "allow" else "default"
        try {
            setOverlayPermissionMode(context, "deny")
            assertFalse(
                "The test setup must deny the debug app's overlay permission",
                Settings.canDrawOverlays(context)
            )
            testBody()
        } finally {
            setOverlayPermissionMode(context, previousMode)
            assertEquals(wasAllowed, Settings.canDrawOverlays(context))
        }
    }

    private fun overlayPermissionMode(context: Context): String? {
        val output = runShellCommand("appops get ${context.packageName} SYSTEM_ALERT_WINDOW")
        return output.lineSequence()
            .map(String::trim)
            .firstOrNull { it.startsWith("SYSTEM_ALERT_WINDOW:") }
            ?.substringAfter(':')
            ?.substringBefore(';')
            ?.trim()
            ?.takeIf { it.matches(Regex("[a-z_]+")) }
    }

    private fun setOverlayPermissionMode(context: Context, mode: String) {
        runShellCommand("appops set ${context.packageName} SYSTEM_ALERT_WINDOW $mode")
    }

    private fun runShellCommand(command: String): String {
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(output).bufferedReader().use { it.readText() }
    }
}
