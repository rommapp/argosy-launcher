package com.nendo.argosy.data.emulator

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The game serial DuckStation writes into a save state's uncompressed header
 * (`src/core/save_state_version.h`, `SAVE_STATE_HEADER`: magic, version, title[128], serial[32]).
 */
object DuckStationStateHeader {
    private const val MAGIC = 0x43435544
    private const val SERIAL_OFFSET = 136
    private const val SERIAL_LENGTH = 32

    fun serialOf(file: File): String? = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            if (raf.length() < SERIAL_OFFSET + SERIAL_LENGTH) return null
            val head = ByteArray(Int.SIZE_BYTES)
            raf.readFully(head)
            if (ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN).int != MAGIC) return null
            val serial = ByteArray(SERIAL_LENGTH)
            raf.seek(SERIAL_OFFSET.toLong())
            raf.readFully(serial)
            val end = serial.indexOf(0).let { if (it < 0) SERIAL_LENGTH else it }
            String(serial, 0, end, Charsets.US_ASCII).trim().takeIf { it.isNotEmpty() }
        }
    }.getOrNull()
}

/**
 * The key a cached state may be placed under. A DuckStation state cached under a name that carries
 * no serial, such as a server copy, is placed only when the serial in its own header is one of the
 * game's, and then only under that serial; anything else is refused with null.
 */
fun provenStateKey(pattern: StateSlotPattern, key: StateNameKey, cachedFile: File): StateNameKey? {
    val duckStation = pattern as? StateSlotPattern.DuckStationSerial ?: return key
    if (duckStation.serialOf(cachedFile.name) != null) return key
    val headerSerial = DuckStationStateHeader.serialOf(cachedFile) ?: return null
    val owned = key.serials.firstOrNull { it.equals(headerSerial, ignoreCase = true) } ?: return null
    return key.copy(serials = setOf(owned))
}
