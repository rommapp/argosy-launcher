package com.nendo.argosy.hardware

import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import com.nendo.argosy.ui.util.launcherContent
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nendo.argosy.DualScreenManager
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.ui.dualscreen.PresentationSlot
import com.nendo.argosy.ui.dualscreen.PresentationSlotContent
import com.nendo.argosy.ui.theme.CustomFontFamilies
import com.nendo.argosy.ui.theme.ThemeState
import com.nendo.argosy.ui.theme.toThemeState
import com.nendo.argosy.util.LauncherSystemBars

/**
 * The surface on the display holding the app-target role. Holding that display is the point: it
 * stops the system mirroring another screen onto it, and gives a launch there somewhere to land.
 */
class AppScreenActivity : ComponentActivity(), DualScreenManager.AppScreenHost {

    private var hostDisplayId: Int = Display.DEFAULT_DISPLAY
    private var coveredByApp = false
    private val systemBars by lazy { LauncherSystemBars(window) }

    override fun releaseAppScreen() {
        runOnUiThread { finish() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dsm = DualScreenManagerHolder.instance
        if (dsm == null) {
            finish()
            return
        }
        hostDisplayId = ContextCompat.getDisplayOrDefault(this).displayId
        if (!dsm.holdsAppScreen(hostDisplayId)) {
            finish()
            return
        }
        dsm.registerAppScreenHost(hostDisplayId, this)
        systemBars.follow(lifecycleScope, dsm.preferencesRepository.userPreferences)

        setContent {
            val themeState = remember { mutableStateOf(ThemeState()) }
            val fonts = remember { mutableStateOf(CustomFontFamilies()) }
            LaunchedEffect(Unit) {
                dsm.preferencesRepository.userPreferences.collect { prefs ->
                    themeState.value = prefs.toThemeState()
                }
            }
            SecondaryHomeTheme(themeState = themeState.value, fonts = fonts.value) {
                val sink = remember { FocusRequester() }
                LaunchedEffect(Unit) { sink.requestFocus() }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.launcherContent)
                        .focusRequester(sink)
                        .focusable()
                ) {
                    com.nendo.argosy.ui.components.ProvideStatusBarItems(dsm.preferencesRepository.userPreferences) {
                        PresentationSlotContent(PresentationSlot.Fallback, showControlHints = false)
                    }
                }
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        DualScreenManagerHolder.instance?.notifyUserActivity("appScreenUserInteraction")
    }

    override fun onResume() {
        super.onResume()
        systemBars.apply()
        val dsm = DualScreenManagerHolder.instance ?: return
        if (!dsm.holdsAppScreen(hostDisplayId)) {
            finish()
            return
        }
        dsm.registerAppScreenHost(hostDisplayId, this)
        if (coveredByApp) {
            coveredByApp = false
            dsm.interactiveDisplayId()
                ?.takeIf { it != hostDisplayId }
                ?.let { dsm.focusDisplay(it) }
        }
    }

    override fun onPause() {
        coveredByApp = !isFinishing
        super.onPause()
    }

    override fun onDestroy() {
        DualScreenManagerHolder.instance?.unregisterAppScreenHost(hostDisplayId, this)
        super.onDestroy()
    }
}
