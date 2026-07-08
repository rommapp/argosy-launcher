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

    fun fileName(romBaseName: String, slotNumber: Int): String = when (slotNumber) {
        AUTO_SLOT -> "$romBaseName.state.auto"
        RESUME_SLOT -> "$romBaseName.state.resume"
        in QUICK_SLOT_BASE until QUICK_SLOT_BASE + QUICK_RING_SIZE ->
            "$romBaseName.state.q${slotNumber - QUICK_SLOT_BASE}"
        0 -> "$romBaseName.state"
        else -> "$romBaseName.state$slotNumber"
    }
}
