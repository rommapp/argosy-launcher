package com.nendo.argosy.util

import androidx.core.view.WindowInsetsCompat.Type
import com.nendo.argosy.data.preferences.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class SystemBarVisibilityTest {

    private val topBars = Type.statusBars() or Type.captionBar()

    private fun launcher(hideStatus: Boolean, hideNavigation: Boolean) = SystemBarVisibility.forLauncher(
        UserPreferences(hideSystemStatusBar = hideStatus, hideSystemNavigationBar = hideNavigation)
    )

    @Test
    fun `a launcher on default preferences hides every bar, as gameplay and playback do`() {
        assertEquals(SystemBarVisibility.IMMERSIVE, SystemBarVisibility.forLauncher(UserPreferences()))
        assertEquals(Type.systemBars(), SystemBarVisibility.IMMERSIVE.hiddenTypes)
        assertEquals(0, SystemBarVisibility.IMMERSIVE.shownTypes)
    }

    @Test
    fun `a launcher keeping the navigation bar hides only the status and caption bars`() {
        val bars = launcher(hideStatus = true, hideNavigation = false)
        assertEquals(topBars, bars.hiddenTypes)
        assertEquals(Type.navigationBars(), bars.shownTypes)
    }

    @Test
    fun `a launcher keeping the status bar hides only the navigation bar`() {
        val bars = launcher(hideStatus = false, hideNavigation = true)
        assertEquals(Type.navigationBars(), bars.hiddenTypes)
        assertEquals(topBars, bars.shownTypes)
    }

    @Test
    fun `a launcher keeping both bars hides nothing`() {
        val bars = launcher(hideStatus = false, hideNavigation = false)
        assertEquals(0, bars.hiddenTypes)
        assertEquals(Type.systemBars(), bars.shownTypes)
    }

    @Test
    fun `a held status bar stays hidden over a preference that shows it and leaves navigation alone`() {
        val prefs = UserPreferences(hideSystemStatusBar = false, hideSystemNavigationBar = false)
        val held = SystemBarVisibility.forLauncher(prefs, statusHeldHidden = true)
        assertEquals(topBars, held.hiddenTypes)
        assertEquals(Type.navigationBars(), held.shownTypes)
        assertEquals(launcher(hideStatus = false, hideNavigation = false), SystemBarVisibility.forLauncher(prefs, statusHeldHidden = false))
    }

    @Test
    fun `every preference pair splits the system bars into disjoint hidden and shown sets`() {
        for (hideStatus in listOf(true, false)) {
            for (hideNavigation in listOf(true, false)) {
                val bars = launcher(hideStatus, hideNavigation)
                assertEquals(0, bars.hiddenTypes and bars.shownTypes)
                assertEquals(Type.systemBars(), bars.hiddenTypes or bars.shownTypes)
            }
        }
    }
}
