package com.nendo.argosy.libretro

import java.io.File

/**
 * The fixed header a libretro core writes at the start of every save state, for the cores whose
 * header is fixed upstream: FCEUmm's `FCS` (libretro-fceumm `src/state.c`, FCEUSS_SaveMS) and
 * Nestopia's `NST` 0x1A (libretro/nestopia `source/core/NstMachine.cpp`, Machine::SaveState).
 *
 * Only a state carrying a known core's header is judged, and it is refused only by a different
 * known core. An empty state, an unrecognised layout and a container such as RetroArch's RZIP
 * compression are always readable.
 */
object LibretroStateFormat {

    const val HEADER_BYTES = 4

    private val HEADERS: Map<String, ByteArray> = mapOf(
        "fceumm" to byteArrayOf('F'.code.toByte(), 'C'.code.toByte(), 'S'.code.toByte()),
        "nestopia" to byteArrayOf('N'.code.toByte(), 'S'.code.toByte(), 'T'.code.toByte(), 0x1A)
    )

    fun readableBy(coreId: String?, header: ByteArray): Boolean {
        if (coreId == null || coreId !in HEADERS) return true
        val writer = writerOf(header) ?: return true
        return writer == coreId
    }

    fun readableBy(coreId: String?, file: File): Boolean = readableBy(coreId, headerOf(file))

    /**
     * The known core whose header [header] carries, or null when it matches none.
     */
    fun writerOf(header: ByteArray): String? =
        HEADERS.entries.firstOrNull { (_, expected) -> header.startsWith(expected) }?.key

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    fun headerOf(file: File): ByteArray =
        runCatching {
            file.inputStream().use { input ->
                val buffer = ByteArray(HEADER_BYTES)
                val read = input.read(buffer)
                if (read <= 0) ByteArray(0) else buffer.copyOf(read)
            }
        }.getOrDefault(ByteArray(0))
}
