package com.nendo.argosy.libretro

/**
 * Canonical on-disk naming for built-in libretro save-state slots. Live states are flat
 * ({statesDir}/rom.state.X) -- mirroring SRAM and external emulators -- with channels held
 * only in the cache. This is the single source of truth for slot <-> filename, shared by
 * SaveStateManager (live engine) and StateCacheManager (cache/restore).
 */
object LibretroStateSlots {
    const val AUTO_SLOT = -1
    const val RESUME_SLOT = -2
    const val MAX_SLOT = 9
    const val QUICK_SLOT_BASE = 100
    const val QUICK_RING_SIZE = 10

    /** Every slot the built-in core can write, for callers that mean "all states" rather than a range. */
    val ALL_SLOTS: List<Int> =
        listOf(RESUME_SLOT, AUTO_SLOT) +
            (0..MAX_SLOT) +
            (QUICK_SLOT_BASE until QUICK_SLOT_BASE + QUICK_RING_SIZE)

    private val NUMBERED_SUFFIX = Regex("""\.state(\d+)""", RegexOption.IGNORE_CASE)

    fun fileName(romBaseName: String, slotNumber: Int): String = when (slotNumber) {
        AUTO_SLOT -> "$romBaseName.state.auto"
        RESUME_SLOT -> "$romBaseName.state.resume"
        in QUICK_SLOT_BASE until QUICK_SLOT_BASE + QUICK_RING_SIZE ->
            "$romBaseName.state.q${slotNumber - QUICK_SLOT_BASE}"
        0 -> "$romBaseName.state"
        else -> "$romBaseName.state$slotNumber"
    }

    /**
     * Inverse of [fileName] for the cache/sync-eligible slots -- the auto slot and the numbered
     * slots (0..N). Returns null for everything else, including the live-only resume and
     * quick-ring states ("$romBaseName.state.resume" / ".state.qN"): those are never discovered,
     * cached, or synced by design. Keeping this next to [fileName] makes the write and read codecs
     * one source of truth, so a change to the flat naming can't silently diverge from the parser.
     */
    fun parseSlotNumber(romBaseName: String, fileName: String): Int? {
        if (!fileName.startsWith(romBaseName, ignoreCase = true)) return null
        val suffix = fileName.substring(romBaseName.length)
        return when {
            suffix.equals(".state", ignoreCase = true) -> 0
            suffix.equals(".state.auto", ignoreCase = true) -> AUTO_SLOT
            else -> NUMBERED_SUFFIX.matchEntire(suffix)?.groupValues?.get(1)?.toIntOrNull()
        }
    }
}
