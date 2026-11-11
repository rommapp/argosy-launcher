package com.nendo.argosy.hardware.pad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PadDriverLauncherTest {

    @Test
    fun `no running driver starts one and kills nothing`() {
        val plan = PadDriverLauncher.plan(emptyList(), INSTALLED_APK)

        assertEquals(PadDriverPlan(kill = emptyList(), start = true), plan)
        assertFalse(plan.isSettled)
    }

    @Test
    fun `a driver from a replaced APK is killed and a new one started`() {
        val plan = PadDriverLauncher.plan(listOf(RunningPadDriver(41, OLD_APK)), INSTALLED_APK)

        assertEquals(PadDriverPlan(kill = listOf(41), start = true), plan)
    }

    @Test
    fun `a driver from the installed APK is left alone`() {
        val plan = PadDriverLauncher.plan(listOf(RunningPadDriver(42, INSTALLED_APK)), INSTALLED_APK)

        assertEquals(PadDriverPlan(kill = emptyList(), start = false), plan)
        assertTrue(plan.isSettled)
    }

    @Test
    fun `an old driver beside a current one is killed without starting another`() {
        val plan = PadDriverLauncher.plan(
            listOf(RunningPadDriver(41, OLD_APK), RunningPadDriver(42, INSTALLED_APK)),
            INSTALLED_APK
        )

        assertEquals(PadDriverPlan(kill = listOf(41), start = false), plan)
    }

    @Test
    fun `two drivers from the installed APK keep only the first`() {
        val plan = PadDriverLauncher.plan(
            listOf(RunningPadDriver(42, INSTALLED_APK), RunningPadDriver(43, INSTALLED_APK)),
            INSTALLED_APK
        )

        assertEquals(PadDriverPlan(kill = listOf(43), start = false), plan)
    }

    @Test
    fun `the process listing yields app_process drivers with their class path`() {
        val running = PadDriverLauncher.parseRunning(
            listOf(
                "1234 app_process -Djava.class.path=$OLD_APK /system/bin ${PadDriverLauncher.DRIVER_CLASS} /data/user/0/x/files/pad_seats ",
                "1300 /system/bin/app_process64 -Djava.class.path=$INSTALLED_APK /system/bin ${PadDriverLauncher.DRIVER_CLASS}",
                "1400 su -c for p in /proc/[0-9]*; do case x in *${PadDriverLauncher.DRIVER_CLASS}*) ;; esac; done",
                "1500 app_process -Djava.class.path=$INSTALLED_APK /system/bin com.example.Other",
                "garbage"
            )
        )

        assertEquals(listOf(RunningPadDriver(1234, OLD_APK), RunningPadDriver(1300, INSTALLED_APK)), running)
    }

    @Test
    fun `the start script runs the driver detached from the given APK with the seats path`() {
        val script = PadDriverLauncher.startScript(INSTALLED_APK, "/data/user/0/x/files/pad_seats")

        assertTrue(script.startsWith("setsid nohup app_process -Djava.class.path='$INSTALLED_APK' /system/bin "))
        assertTrue(script.contains("${PadDriverLauncher.DRIVER_CLASS} '/data/user/0/x/files/pad_seats'"))
        assertTrue(script.endsWith("< /dev/null &"))
    }

    private companion object {
        const val OLD_APK = "/data/app/~~old==/com.nendo.argosy-a/base.apk"
        const val INSTALLED_APK = "/data/app/~~new==/com.nendo.argosy-b/base.apk"
    }
}
