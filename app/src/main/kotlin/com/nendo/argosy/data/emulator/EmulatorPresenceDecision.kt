package com.nendo.argosy.data.emulator

enum class PresenceEventKind {
    SCREEN_INTERACTIVE,
    SCREEN_NON_INTERACTIVE,
    KEYGUARD_HIDDEN,
    ACTIVITY_RESUMED,
    ACTIVITY_PAUSED,
    ACTIVITY_STOPPED;

    val isActivityLifecycle: Boolean
        get() = this == ACTIVITY_RESUMED || this == ACTIVITY_PAUSED || this == ACTIVITY_STOPPED
}

data class PresenceEvent(
    val timestamp: Long,
    val kind: PresenceEventKind,
    val packageName: String? = null,
    val className: String? = null
)

/**
 * Which activities count as the emulator. Every activity of [packageName] by default; only
 * [className] when the emulator runs inside the launcher's own process, where the rest of the
 * package is the launcher UI rather than the game.
 */
data class EmulatorIdentity(
    val packageName: String,
    val className: String? = null
) {
    fun matches(event: PresenceEvent): Boolean =
        event.packageName == packageName && (className == null || event.className == className)
}

enum class PresenceVerdict(val countsAsMiss: Boolean) {
    GONE(true),
    PRESENT(false),
    SCREEN_OFF(false),
    LOCKED(false),
    WAKING(false),
    OWN_APP_IN_FRONT(false),
    NO_TAKEOVER(false),
    NO_EVIDENCE(false)
}

data class PresenceDecision(
    val verdict: PresenceVerdict,
    val emulatorActivities: Map<String, PresenceEvent>
)

/**
 * Decides from one usage-event window whether the player has left the emulator. Only
 * [PresenceVerdict.GONE] counts; [PresenceDecision.emulatorActivities] is fed back in as events
 * on the next call so a pause that aged out of the window is still known.
 */
object EmulatorPresenceDecision {

    fun decide(
        events: List<PresenceEvent>,
        emulator: EmulatorIdentity,
        ownPackage: String,
        keyguardLocked: Boolean,
        now: Long,
        wakeGraceMs: Long
    ): PresenceDecision {
        val emulatorActivities = latestEmulatorEventByActivity(events, emulator)
        val verdict = verdictFor(events, emulator, emulatorActivities, ownPackage, keyguardLocked, now, wakeGraceMs)
        return PresenceDecision(verdict, emulatorActivities)
    }

    private fun verdictFor(
        events: List<PresenceEvent>,
        emulator: EmulatorIdentity,
        emulatorActivities: Map<String, PresenceEvent>,
        ownPackage: String,
        keyguardLocked: Boolean,
        now: Long,
        wakeGraceMs: Long
    ): PresenceVerdict {
        if (keyguardLocked) return PresenceVerdict.LOCKED
        val lastScreen = events
            .filter { it.kind == PresenceEventKind.SCREEN_INTERACTIVE || it.kind == PresenceEventKind.SCREEN_NON_INTERACTIVE }
            .maxByOrNull { it.timestamp }
        if (lastScreen?.kind == PresenceEventKind.SCREEN_NON_INTERACTIVE) return PresenceVerdict.SCREEN_OFF
        val lastWake = events
            .filter { it.kind == PresenceEventKind.SCREEN_INTERACTIVE || it.kind == PresenceEventKind.KEYGUARD_HIDDEN }
            .maxOfOrNull { it.timestamp }
        if (lastWake != null && now - lastWake < wakeGraceMs) return PresenceVerdict.WAKING
        if (emulatorActivities.isEmpty()) return PresenceVerdict.NO_EVIDENCE
        if (emulatorActivities.values.any { it.kind == PresenceEventKind.ACTIVITY_RESUMED }) return PresenceVerdict.PRESENT

        val emulatorLastResumedAt = events
            .filter { emulator.matches(it) && it.kind == PresenceEventKind.ACTIVITY_RESUMED }
            .maxOfOrNull { it.timestamp }
        val takeover = events
            .filter { it.kind == PresenceEventKind.ACTIVITY_RESUMED && !emulator.matches(it) }
            .filter { emulatorLastResumedAt == null || it.timestamp >= emulatorLastResumedAt }
            .maxByOrNull { it.timestamp }
            ?: return PresenceVerdict.NO_TAKEOVER
        if (takeover.packageName == ownPackage) return PresenceVerdict.OWN_APP_IN_FRONT
        return PresenceVerdict.GONE
    }

    private fun latestEmulatorEventByActivity(
        events: List<PresenceEvent>,
        emulator: EmulatorIdentity
    ): Map<String, PresenceEvent> {
        val latest = HashMap<String, PresenceEvent>()
        for (event in events) {
            if (!event.kind.isActivityLifecycle || !emulator.matches(event)) continue
            val key = event.className ?: continue
            val previous = latest[key]
            if (previous == null || event.timestamp >= previous.timestamp) latest[key] = event
        }
        return latest
    }
}
