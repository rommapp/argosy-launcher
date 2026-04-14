package com.nendo.argosy.data.netplay

internal object NetplaySecurityBounds {
    const val MAX_FRAME_LOOKAHEAD = 300L
    const val MAX_FRAME_LOOKBACK_EXTRA = 60L
    const val MAX_INPUT_MAP_ENTRIES = 10_000
    const val MAX_CHUNKS_PER_SNAPSHOT = 2048
    const val MAX_CONCURRENT_SNAPSHOTS = 2
    const val REASSEMBLY_TTL_NANOS = 30_000_000_000L
}
