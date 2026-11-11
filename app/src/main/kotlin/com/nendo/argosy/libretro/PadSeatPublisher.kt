package com.nendo.argosy.libretro

import android.view.InputDevice
import com.nendo.argosy.core.input.controllerIdOf
import com.nendo.argosy.core.input.isPhysicalGamepad
import com.nendo.argosy.hardware.pad.PadSeats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Publishes this game's pad seats for the pad driver's player lights while the game is in front.
 * Seats are read on the calling thread; the file is written off it.
 */
class PadSeatPublisher(
    filesDir: File,
    private val resolver: ControllerPortResolver,
    private val scope: CoroutineScope
) {
    private val file = File(filesDir, PadSeats.FILE_NAME)
    private val writer = Dispatchers.IO.limitedParallelism(1)

    fun publish() {
        val seats = InputDevice.getDeviceIds().toList()
            .mapNotNull { InputDevice.getDevice(it) }
            .filter { it.isPhysicalGamepad() && it.controllerNumber > 0 }
            .mapNotNull { pad -> resolver.seatOf(controllerIdOf(pad))?.let { seat -> pad.controllerNumber to seat + 1 } }
            .toMap()
        scope.launch(writer) { runCatching { PadSeats.write(file, seats) } }
    }

    fun clear() {
        scope.launch(writer) { file.delete() }
    }
}
