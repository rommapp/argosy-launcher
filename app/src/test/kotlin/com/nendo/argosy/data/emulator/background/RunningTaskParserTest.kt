package com.nendo.argosy.data.emulator.background

import org.junit.Assert.assertEquals
import org.junit.Test

class RunningTaskParserTest {

    private val stackList = """
        RootTask id=6251 bounds=[0,0][1240,1080] displayId=4 userId=0
         configuration={1.0 winConfig={ mActivityType=home mAlwaysOnTop=undefined}}
          taskId=6252: com.android.launcher3/com.android.launcher3.secondarydisplay.SecondaryDisplayLauncher bounds=[0,0][1240,1080] userId=0 visible=true topActivity=ComponentInfo{com.nendo.argosy.debug/com.nendo.argosy.hardware.SecondaryHomeActivity}

        RootTask id=6699 bounds=[0,0][1240,1080] displayId=4 userId=0
         configuration={1.0 winConfig={ mActivityType=standard mAlwaysOnTop=undefined}}
          taskId=6699: com.android.settings/com.android.settings.Settings${'$'}AccessibilitySettingsActivity bounds=[0,0][1240,1080] userId=0 visible=false topActivity=ComponentInfo{com.android.settings/com.android.settings.Settings${'$'}AccessibilitySettingsActivity}

        RootTask id=6750 bounds=[0,0][1920,1080] displayId=0 userId=0
         configuration={1.0 winConfig={ mActivityType=standard mAlwaysOnTop=undefined}}
          taskId=6750: me.magnum.melondualds/me.magnum.melonds.ui.emulator.EmulatorActivity bounds=[0,0][1920,1080] userId=0 visible=true topActivity=ComponentInfo{me.magnum.melondualds/me.magnum.melonds.ui.emulator.EmulatorActivity}

        RootTask id=6686 bounds=[0,0][1920,1080] displayId=0 userId=0
         configuration={1.0 winConfig={ mActivityType=recents mAlwaysOnTop=undefined}}
          taskId=6687: com.android.launcher3/com.android.quickstep.RecentsActivity bounds=[0,0][1920,1080] userId=0 visible=false topActivity=ComponentInfo{com.android.launcher3/com.android.quickstep.RecentsActivity}

        RootTask id=6384 bounds=[0,0][1920,1080] displayId=0 userId=0
         configuration={1.0 winConfig={ mActivityType=undefined mAlwaysOnTop=undefined}}
          taskId=6385: unknown bounds=[0,0][1920,1080] userId=0 visible=false
    """.trimIndent().lines()

    @Test
    fun `keeps standard tasks with their display and drops home, recents and unknown tasks`() {
        assertEquals(
            listOf(
                RunningTask(6699, "com.android.settings", displayId = 4, visible = false),
                RunningTask(6750, "me.magnum.melondualds", displayId = 0, visible = true)
            ),
            RunningTaskParser.parseTasks(stackList)
        )
    }

    @Test
    fun `reads legacy stack headers`() {
        val lines = listOf(
            "Stack id=12 bounds=[0,0][1080,1920] displayId=0 userId=0",
            " configuration={ mActivityType=standard }",
            "  taskId=40: org.ppsspp.ppsspp/org.ppsspp.ppsspp.PpssppActivity bounds=[0,0][1080,1920] userId=0 visible=true topActivity=ComponentInfo{org.ppsspp.ppsspp/org.ppsspp.ppsspp.PpssppActivity}"
        )
        assertEquals(
            listOf(RunningTask(40, "org.ppsspp.ppsspp", displayId = 0, visible = true)),
            RunningTaskParser.parseTasks(lines)
        )
    }

    @Test
    fun `sums memory over a package's processes and keeps the main process pid`() {
        val ps = listOf(
            "  PID    RSS     ELAPSED NAME                       ",
            "    2      0       30:44 [kthreadd]",
            "  578 124988       30:18 com.android.launcher3",
            "  900  20000     1:02:03 org.ppsspp.ppsspp:remote",
            "  901 300000       12:05 org.ppsspp.ppsspp"
        )
        assertEquals(
            mapOf(
                "com.android.launcher3" to ProcessStat(578, 124988, (30 * 60 + 18) * 1000L),
                "org.ppsspp.ppsspp" to ProcessStat(901, 320000, (12 * 60 + 5) * 1000L)
            ),
            RunningTaskParser.parseProcesses(ps)
        )
    }

    @Test
    fun `a package with only secondary processes has no entry`() {
        val ps = listOf("  900  20000       00:09 org.ppsspp.ppsspp:remote")
        assertEquals(emptyMap<String, ProcessStat>(), RunningTaskParser.parseProcesses(ps))
    }

    @Test
    fun `elapsed time reads minutes, hours and days`() {
        assertEquals(65_000L, RunningTaskParser.parseElapsedMs("01:05"))
        assertEquals((2 * 3600 + 3 * 60 + 4) * 1000L, RunningTaskParser.parseElapsedMs("02:03:04"))
        assertEquals((86_400 + 3600 + 60 + 1) * 1000L, RunningTaskParser.parseElapsedMs("1-01:01:01"))
    }
}
