package com.nendo.argosy

import org.junit.Assert.assertEquals
import org.junit.Test

class ArgosyActivityNonDefaultDisplayTest {

    private val launchKinds = listOf(true, false)

    @Test
    fun `dex keeps the launcher on its display in every arrangement`() {
        for (dualScreenEnabled in listOf(true, false)) {
            for (docked in listOf(true, false)) {
                for (launchedAsHome in launchKinds) {
                    assertEquals(
                        "dualScreen=$dualScreenEnabled docked=$docked home=$launchedAsHome",
                        NonDefaultDisplayLaunch.RUN_HERE,
                        nonDefaultDisplayLaunch(
                            desktopMode = true,
                            dualScreenEnabled = dualScreenEnabled,
                            docked = docked,
                            launchedAsHome = launchedAsHome
                        )
                    )
                }
            }
        }
    }

    @Test
    fun `outside dex with dual screen on the companion takes the display`() {
        for (docked in listOf(true, false)) {
            for (launchedAsHome in launchKinds) {
                assertEquals(
                    "docked=$docked home=$launchedAsHome",
                    NonDefaultDisplayLaunch.HAND_TO_COMPANION,
                    nonDefaultDisplayLaunch(
                        desktopMode = false,
                        dualScreenEnabled = true,
                        docked = docked,
                        launchedAsHome = launchedAsHome
                    )
                )
            }
        }
    }

    @Test
    fun `outside dex a docked device hands the display to the companion with dual screen off`() {
        for (launchedAsHome in launchKinds) {
            assertEquals(
                NonDefaultDisplayLaunch.HAND_TO_COMPANION,
                nonDefaultDisplayLaunch(
                    desktopMode = false,
                    dualScreenEnabled = false,
                    docked = true,
                    launchedAsHome = launchedAsHome
                )
            )
        }
    }

    @Test
    fun `with dual screen off and no dock a home launch on another display yields it`() {
        assertEquals(
            NonDefaultDisplayLaunch.YIELD,
            nonDefaultDisplayLaunch(desktopMode = false, dualScreenEnabled = false, docked = false, launchedAsHome = true)
        )
    }

    @Test
    fun `with dual screen off and no dock the app opened on another display runs there`() {
        assertEquals(
            NonDefaultDisplayLaunch.RUN_HERE,
            nonDefaultDisplayLaunch(desktopMode = false, dualScreenEnabled = false, docked = false, launchedAsHome = false)
        )
    }
}
