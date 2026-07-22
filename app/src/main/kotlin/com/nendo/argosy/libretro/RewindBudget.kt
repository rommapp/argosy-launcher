package com.nendo.argosy.libretro

/**
 * Memory ceiling for the rewind snapshot buffer. A 32-bit process cannot address the
 * multi-gigabyte buffers a long rewind window implies, so the ceiling is ABI-scaled and
 * the requested window is additionally clamped on 32-bit.
 */
object RewindBudget {
    const val BUDGET_64BIT_BYTES = 1_200L * 1024 * 1024
    const val BUDGET_32BIT_BYTES = 256L * 1024 * 1024
    const val MAX_DURATION_32BIT_SECONDS = 15

    private const val AVAILABLE_MEMORY_FRACTION = 4

    fun budgetBytes(availableMemoryBytes: Long, is64Bit: Boolean): Long {
        val ceiling = if (is64Bit) BUDGET_64BIT_BYTES else BUDGET_32BIT_BYTES
        return minOf(availableMemoryBytes / AVAILABLE_MEMORY_FRACTION, ceiling).coerceAtLeast(0)
    }

    fun durationSeconds(requestedSeconds: Int, is64Bit: Boolean): Int =
        if (is64Bit) requestedSeconds else minOf(requestedSeconds, MAX_DURATION_32BIT_SECONDS)
}
