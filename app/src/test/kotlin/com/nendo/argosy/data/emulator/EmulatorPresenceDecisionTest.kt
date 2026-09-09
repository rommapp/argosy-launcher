package com.nendo.argosy.data.emulator

import org.junit.Assert.assertEquals
import org.junit.Test

class EmulatorPresenceDecisionTest {

    private val emulatorPackage = "org.emulator"
    private val emulatorActivity = "org.emulator.MainActivity"
    private val ownPackage = "com.nendo.argosy"
    private val launcherPackage = "com.other.launcher"
    private val external = EmulatorIdentity(emulatorPackage)
    private val builtin = EmulatorIdentity(ownPackage, "com.nendo.argosy.libretro.LibretroActivity")
    private val now = 100_000L
    private val grace = 3_000L

    private fun screenOn(at: Long) = PresenceEvent(at, PresenceEventKind.SCREEN_INTERACTIVE)
    private fun screenOff(at: Long) = PresenceEvent(at, PresenceEventKind.SCREEN_NON_INTERACTIVE)
    private fun unlocked(at: Long) = PresenceEvent(at, PresenceEventKind.KEYGUARD_HIDDEN)
    private fun resumed(at: Long, pkg: String, cls: String) = PresenceEvent(at, PresenceEventKind.ACTIVITY_RESUMED, pkg, cls)
    private fun paused(at: Long, pkg: String, cls: String) = PresenceEvent(at, PresenceEventKind.ACTIVITY_PAUSED, pkg, cls)
    private fun stopped(at: Long, pkg: String, cls: String) = PresenceEvent(at, PresenceEventKind.ACTIVITY_STOPPED, pkg, cls)

    private fun verdict(
        events: List<PresenceEvent>,
        emulator: EmulatorIdentity = external,
        keyguardLocked: Boolean = false
    ): PresenceVerdict = EmulatorPresenceDecision.decide(
        events = events,
        emulator = emulator,
        ownPackage = ownPackage,
        keyguardLocked = keyguardLocked,
        now = now,
        wakeGraceMs = grace
    ).verdict

    @Test
    fun `home press to another launcher is gone`() {
        val events = listOf(
            screenOn(1_000),
            resumed(10_000, emulatorPackage, emulatorActivity),
            paused(50_000, emulatorPackage, emulatorActivity),
            resumed(50_010, launcherPackage, "com.other.launcher.Home"),
            stopped(50_500, emulatorPackage, emulatorActivity)
        )
        assertEquals(PresenceVerdict.GONE, verdict(events))
    }

    @Test
    fun `emulator still resumed is present`() {
        val events = listOf(
            screenOn(1_000),
            resumed(10_000, emulatorPackage, emulatorActivity)
        )
        assertEquals(PresenceVerdict.PRESENT, verdict(events))
    }

    @Test
    fun `argosy in front is left to the launcher path`() {
        val events = listOf(
            resumed(10_000, emulatorPackage, emulatorActivity),
            stopped(50_000, emulatorPackage, emulatorActivity),
            resumed(50_010, ownPackage, "com.nendo.argosy.MainActivity")
        )
        assertEquals(PresenceVerdict.OWN_APP_IN_FRONT, verdict(events))
    }

    @Test
    fun `screen off is not a miss`() {
        val events = listOf(
            resumed(10_000, emulatorPackage, emulatorActivity),
            stopped(50_000, emulatorPackage, emulatorActivity),
            resumed(50_010, launcherPackage, "com.other.launcher.Home"),
            screenOff(50_020)
        )
        assertEquals(PresenceVerdict.SCREEN_OFF, verdict(events))
    }

    @Test
    fun `keyguard showing is not a miss`() {
        val events = listOf(
            resumed(10_000, emulatorPackage, emulatorActivity),
            stopped(50_000, emulatorPackage, emulatorActivity),
            resumed(50_010, launcherPackage, "com.other.launcher.Home")
        )
        assertEquals(PresenceVerdict.LOCKED, verdict(events, keyguardLocked = true))
    }

    @Test
    fun `screen on inside the grace window holds`() {
        val events = listOf(
            resumed(10_000, emulatorPackage, emulatorActivity),
            stopped(50_000, emulatorPackage, emulatorActivity),
            resumed(50_010, launcherPackage, "com.other.launcher.Home"),
            screenOn(now - grace + 1)
        )
        assertEquals(PresenceVerdict.WAKING, verdict(events))
    }

    @Test
    fun `unlock inside the grace window holds`() {
        val events = listOf(
            resumed(10_000, emulatorPackage, emulatorActivity),
            stopped(50_000, emulatorPackage, emulatorActivity),
            resumed(50_010, launcherPackage, "com.other.launcher.Home"),
            unlocked(now - grace + 1)
        )
        assertEquals(PresenceVerdict.WAKING, verdict(events))
    }

    @Test
    fun `wake older than the grace window counts again`() {
        val events = listOf(
            resumed(10_000, emulatorPackage, emulatorActivity),
            stopped(50_000, emulatorPackage, emulatorActivity),
            resumed(50_010, launcherPackage, "com.other.launcher.Home"),
            screenOn(now - grace)
        )
        assertEquals(PresenceVerdict.GONE, verdict(events))
    }

    @Test
    fun `emulator resuming after wake is present`() {
        val events = listOf(
            resumed(10_000, emulatorPackage, emulatorActivity),
            stopped(50_000, emulatorPackage, emulatorActivity),
            screenOff(50_100),
            screenOn(80_000),
            unlocked(80_500),
            resumed(81_000, emulatorPackage, emulatorActivity)
        )
        assertEquals(PresenceVerdict.PRESENT, verdict(events))
    }

    @Test
    fun `handoff between the emulator's own activities is present`() {
        val events = listOf(
            resumed(10_000, emulatorPackage, "org.emulator.Splash"),
            resumed(12_000, emulatorPackage, emulatorActivity),
            stopped(12_500, emulatorPackage, "org.emulator.Splash")
        )
        assertEquals(PresenceVerdict.PRESENT, verdict(events))
    }

    @Test
    fun `no emulator events is no evidence`() {
        val events = listOf(
            screenOn(1_000),
            resumed(50_010, launcherPackage, "com.other.launcher.Home")
        )
        assertEquals(PresenceVerdict.NO_EVIDENCE, verdict(events))
    }

    @Test
    fun `paused with nobody resumed since is not a miss`() {
        val events = listOf(
            resumed(10_000, emulatorPackage, emulatorActivity),
            paused(50_000, emulatorPackage, emulatorActivity)
        )
        assertEquals(PresenceVerdict.NO_TAKEOVER, verdict(events))
    }

    @Test
    fun `foreign resume before the emulator's last resume is not a takeover`() {
        val events = listOf(
            resumed(9_000, launcherPackage, "com.other.launcher.Home"),
            resumed(10_000, emulatorPackage, emulatorActivity),
            paused(50_000, emulatorPackage, emulatorActivity)
        )
        assertEquals(PresenceVerdict.NO_TAKEOVER, verdict(events))
    }

    @Test
    fun `foreign resume at the same instant as the emulator's counts as takeover`() {
        val events = listOf(
            resumed(10_000, emulatorPackage, emulatorActivity),
            resumed(10_000, launcherPackage, "com.other.launcher.Home"),
            paused(50_000, emulatorPackage, emulatorActivity)
        )
        assertEquals(PresenceVerdict.GONE, verdict(events))
    }

    @Test
    fun `builtin core stopped with argosy home resumed is left to the launcher path`() {
        val events = listOf(
            resumed(10_000, ownPackage, builtin.className!!),
            stopped(50_000, ownPackage, builtin.className!!),
            resumed(50_010, ownPackage, "com.nendo.argosy.MainActivity")
        )
        assertEquals(PresenceVerdict.OWN_APP_IN_FRONT, verdict(events, emulator = builtin))
    }

    @Test
    fun `builtin core stopped with another launcher resumed is gone`() {
        val events = listOf(
            resumed(10_000, ownPackage, builtin.className!!),
            stopped(50_000, ownPackage, builtin.className!!),
            resumed(50_010, launcherPackage, "com.other.launcher.Home")
        )
        assertEquals(PresenceVerdict.GONE, verdict(events, emulator = builtin))
    }

    @Test
    fun `builtin core resumed after argosy home is present`() {
        val events = listOf(
            resumed(5_000, ownPackage, "com.nendo.argosy.MainActivity"),
            paused(10_000, ownPackage, "com.nendo.argosy.MainActivity"),
            resumed(10_000, ownPackage, builtin.className!!)
        )
        assertEquals(PresenceVerdict.PRESENT, verdict(events, emulator = builtin))
    }

    @Test
    fun `carried emulator state survives the window aging out`() {
        val first = EmulatorPresenceDecision.decide(
            events = listOf(
                resumed(10_000, emulatorPackage, emulatorActivity),
                stopped(20_000, emulatorPackage, emulatorActivity),
                screenOff(20_100)
            ),
            emulator = external,
            ownPackage = ownPackage,
            keyguardLocked = false,
            now = 30_000,
            wakeGraceMs = grace
        )
        assertEquals(PresenceVerdict.SCREEN_OFF, first.verdict)

        val windowOnly = listOf(
            screenOn(90_000),
            resumed(90_100, launcherPackage, "com.other.launcher.Home")
        )
        val second = EmulatorPresenceDecision.decide(
            events = first.emulatorActivities.values + windowOnly,
            emulator = external,
            ownPackage = ownPackage,
            keyguardLocked = false,
            now = now,
            wakeGraceMs = grace
        )
        assertEquals(PresenceVerdict.GONE, second.verdict)
        assertEquals(PresenceVerdict.NO_EVIDENCE, verdict(windowOnly))
    }

    @Test
    fun `carried state is the latest event per emulator activity`() {
        val decision = EmulatorPresenceDecision.decide(
            events = listOf(
                resumed(10_000, emulatorPackage, "org.emulator.Splash"),
                resumed(12_000, emulatorPackage, emulatorActivity),
                stopped(12_500, emulatorPackage, "org.emulator.Splash"),
                resumed(15_000, launcherPackage, "com.other.launcher.Home")
            ),
            emulator = external,
            ownPackage = ownPackage,
            keyguardLocked = false,
            now = now,
            wakeGraceMs = grace
        )
        assertEquals(
            mapOf(
                "org.emulator.Splash" to stopped(12_500, emulatorPackage, "org.emulator.Splash"),
                emulatorActivity to resumed(12_000, emulatorPackage, emulatorActivity)
            ),
            decision.emulatorActivities
        )
    }
}
