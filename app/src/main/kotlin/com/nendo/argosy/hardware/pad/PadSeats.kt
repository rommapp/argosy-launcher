package com.nendo.argosy.hardware.pad

import java.io.File

/**
 * Pad seats for the pad driver's player lights, keyed by Android's controller number and counted
 * from player 1. [FILE_NAME] holds the seats a built-in game in front has given its pads; the
 * game deletes it when it leaves the screen. [SAVED_FILE_NAME] holds the seats saved in the
 * controller order settings for the pads connected now. A pad lights its in-game seat first,
 * then its saved seat, then its controller number.
 */
object PadSeats {
    const val FILE_NAME = "pad_seats"
    const val SAVED_FILE_NAME = "pad_seats_saved"

    fun playerFor(controllerNumber: Int, live: Map<Int, Int>, saved: Map<Int, Int>): Int =
        live[controllerNumber] ?: saved[controllerNumber] ?: controllerNumber

    fun write(file: File, seats: Map<Int, Int>) {
        val partial = File(file.parentFile, "${file.name}.part")
        partial.writeText(seats.entries.joinToString("\n") { (controller, player) -> "$controller $player" })
        if (!partial.renameTo(file)) partial.delete()
    }

    fun read(file: File): Map<Int, Int> = runCatching {
        file.readLines().mapNotNull { line ->
            val parts = line.trim().split(' ')
            val controller = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val player = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            controller to player
        }.toMap()
    }.getOrDefault(emptyMap())
}
