package com.nendo.argosy.ui.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GamePlaybackPolicyTest {

    private val bools = listOf(false, true)

    private data class Arrangement(
        val sourceIsLauncher: Boolean,
        val themePreviewActive: Boolean,
        val session: GameSessionAudio
    ) {
        val keeps: Boolean
            get() = GamePlaybackPolicy.keepPlayingDuringGame(sourceIsLauncher, themePreviewActive, session)
    }

    private fun session(
        builtIn: Boolean = false,
        overlays: Boolean = false,
        quickPanel: Boolean = false,
        accessibility: Boolean = false,
        percent: Int = 60
    ) = GameSessionAudio(
        isBuiltIn = builtIn,
        canDrawOverlays = overlays,
        externalQuickPanelEnabled = quickPanel,
        accessibilityServiceEnabled = accessibility,
        musicVolumePercent = percent
    )

    private val everyArrangement: List<Arrangement> =
        bools.flatMap { launcher ->
            bools.flatMap { preview ->
                bools.flatMap { builtIn ->
                    bools.flatMap { overlays ->
                        bools.flatMap { quickPanel ->
                            bools.map { accessibility ->
                                Arrangement(
                                    sourceIsLauncher = launcher,
                                    themePreviewActive = preview,
                                    session = session(builtIn, overlays, quickPanel, accessibility)
                                )
                            }
                        }
                    }
                }
            }
        }

    @Test
    fun `the truth table covers all sixty-four arrangements`() {
        assertEquals(64, everyArrangement.toSet().size)
    }

    @Test
    fun `launcher background music fades in every arrangement`() {
        everyArrangement.filter { it.sourceIsLauncher }.forEach {
            assertFalse("launcher BGM kept playing: $it", it.keeps)
        }
    }

    @Test
    fun `a game theme preview fades in every arrangement`() {
        everyArrangement.filter { it.themePreviewActive }.forEach {
            assertFalse("theme preview kept playing: $it", it.keeps)
        }
    }

    @Test
    fun `user music keeps playing through every built-in session whatever the permissions`() {
        everyArrangement
            .filter { !it.sourceIsLauncher && !it.themePreviewActive && it.session.isBuiltIn }
            .forEach { assertTrue("built-in session stopped user music: $it", it.keeps) }
    }

    @Test
    fun `user music keeps playing through an external session only with overlay, quick panel and accessibility`() {
        val external = everyArrangement.filter {
            !it.sourceIsLauncher && !it.themePreviewActive && !it.session.isBuiltIn
        }
        assertEquals(8, external.size)
        external.forEach {
            val allThree = it.session.canDrawOverlays &&
                it.session.externalQuickPanelEnabled &&
                it.session.accessibilityServiceEnabled
            assertEquals("external arrangement $it", allThree, it.keeps)
        }
    }

    @Test
    fun `exactly the built-in user rows and the one fully enabled external row keep playing`() {
        val keeping = everyArrangement.filter { it.keeps }
        assertEquals(9, keeping.size)
    }

    @Test
    fun `granting any missing external condition never stops music that was playing`() {
        everyArrangement.forEach { base ->
            val upgrades = listOf(
                base.copy(session = base.session.copy(canDrawOverlays = true)),
                base.copy(session = base.session.copy(externalQuickPanelEnabled = true)),
                base.copy(session = base.session.copy(accessibilityServiceEnabled = true)),
                base.copy(session = base.session.copy(isBuiltIn = true))
            )
            upgrades.forEach { upgraded ->
                if (base.keeps) assertTrue("$base kept, $upgraded stopped", upgraded.keeps)
            }
        }
    }

    @Test
    fun `the duck scales the music volume by the session percentage`() {
        assertEquals(0.3f, GamePlaybackPolicy.duckedVolume(0.5f, 60), 0.0001f)
        assertEquals(0.35f, GamePlaybackPolicy.duckedVolume(0.35f, 100), 0.0001f)
        assertEquals(0f, GamePlaybackPolicy.duckedVolume(0.35f, 0), 0.0001f)
        assertEquals(0.035f, GamePlaybackPolicy.duckedVolume(0.35f, 10), 0.0001f)
    }

    @Test
    fun `the duck clamps out-of-range inputs`() {
        assertEquals(0.5f, GamePlaybackPolicy.duckedVolume(0.5f, 150), 0.0001f)
        assertEquals(0f, GamePlaybackPolicy.duckedVolume(0.5f, -20), 0.0001f)
        assertEquals(0.6f, GamePlaybackPolicy.duckedVolume(1.5f, 60), 0.0001f)
        assertEquals(0f, GamePlaybackPolicy.duckedVolume(-1f, 60), 0.0001f)
    }

    @Test
    fun `the duck never raises the music above its own volume`() {
        (0..100 step 10).forEach { percent ->
            assertTrue(GamePlaybackPolicy.duckedVolume(0.35f, percent) <= 0.35f)
        }
    }

    @Test
    fun `companion input resumes with no session or when the policy keeps playing`() {
        assertTrue(GamePlaybackPolicy.companionInputMayResume(null, keepsPlaying = false))
        assertTrue(GamePlaybackPolicy.companionInputMayResume(session(), keepsPlaying = true))
        assertFalse(GamePlaybackPolicy.companionInputMayResume(session(), keepsPlaying = false))
    }

    @Test
    fun `music held for the launch resumes only when kept and recent`() {
        val window = GamePlaybackPolicy.LAUNCH_HOLD_WINDOW_MS
        assertTrue(GamePlaybackPolicy.resumesAtSessionStart(true, heldAtMs = 1_000L, nowMs = 1_000L))
        assertTrue(GamePlaybackPolicy.resumesAtSessionStart(true, heldAtMs = 1_000L, nowMs = 1_000L + window))
        assertFalse(GamePlaybackPolicy.resumesAtSessionStart(true, heldAtMs = 1_000L, nowMs = 1_001L + window))
        assertFalse(GamePlaybackPolicy.resumesAtSessionStart(false, heldAtMs = 1_000L, nowMs = 1_000L))
        assertFalse(GamePlaybackPolicy.resumesAtSessionStart(true, heldAtMs = null, nowMs = 1_000L))
        assertFalse(GamePlaybackPolicy.resumesAtSessionStart(true, heldAtMs = 2_000L, nowMs = 1_000L))
    }
}
