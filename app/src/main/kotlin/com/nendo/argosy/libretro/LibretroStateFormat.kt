package com.nendo.argosy.libretro

import java.io.File

/**
 * The fixed header a libretro core writes at the start of every save state, for the cores whose
 * header is fixed upstream: FCEUmm's `FCS` (libretro-fceumm `src/state.c`, FCEUSS_SaveMS) and
 * Nestopia's `NST` 0x1A (libretro/nestopia `source/core/NstMachine.cpp`, Machine::SaveState).
 *
 * A state is readable by a core with no known header, and an empty or unreadable state is never
 * judged, so only a state carrying another layout's header is refused.
 */
object LibretroStateFormat {

    const val HEADER_BYTES = 4

    private val HEADERS: Map<String, ByteArray> = mapOf(
        "fceumm" to byteArrayOf('F'.code.toByte(), 'C'.code.toByte(), 'S'.code.toByte()),
        "nestopia" to byteArrayOf('N'.code.toByte(), 'S'.code.toByte(), 'T'.code.toByte(), 0x1A)
    )

    fun readableBy(coreId: String?, header: ByteArray): Boolean {
        val expected = coreId?.let(HEADERS::get) ?: return true
        if (header.isEmpty()) return true
        return header.size >= expected.size && expected.indices.all { header[it] == expected[it] }
    }

    fun readableBy(coreId: String?, file: File): Boolean = readableBy(coreId, headerOf(file))

    /**
     * The known core whose header [header] carries, or null when it matches none.
     */
    fun writerOf(header: ByteArray): String? =
        if (header.isEmpty()) null else HEADERS.entries.firstOrNull { (core, _) -> readableBy(core, header) }?.key

    fun headerOf(file: File): ByteArray =
        runCatching {
            file.inputStream().use { input ->
                val buffer = ByteArray(HEADER_BYTES)
                val read = input.read(buffer)
                if (read <= 0) ByteArray(0) else buffer.copyOf(read)
            }
        }.getOrDefault(ByteArray(0))
}
