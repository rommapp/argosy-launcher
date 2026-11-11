package com.nendo.argosy.hardware.pad

import android.content.Context
import android.view.InputDevice
import com.nendo.argosy.core.input.ConnectedControllerTracker
import com.nendo.argosy.core.input.controllerIdOf
import com.nendo.argosy.core.input.isPhysicalGamepad
import com.nendo.argosy.data.repository.InputConfigRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps [PadSeats.SAVED_FILE_NAME] matching the controller order settings for the pads connected
 * now, so the pad driver lights a pad's saved seat outside a game.
 */
@Singleton
class SavedPadSeatsPublisher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val inputConfigRepository: InputConfigRepository,
    private val controllerTracker: ConnectedControllerTracker
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    /**
     * Drops the in-game seats a game left behind when it died in front, so they stop outranking
     * the saved seats once Argosy is on screen again.
     */
    fun onLauncherResumed() {
        scope.launch { File(context.filesDir, PadSeats.FILE_NAME).delete() }
    }

    fun start() {
        if (job != null) return
        val file = File(context.filesDir, PadSeats.SAVED_FILE_NAME)
        job = scope.launch {
            combine(inputConfigRepository.observeControllerOrder(), controllerTracker.controllers) { order, _ ->
                val seatById = order.associate { it.controllerId to it.port + 1 }
                InputDevice.getDeviceIds().toList()
                    .mapNotNull { InputDevice.getDevice(it) }
                    .filter { it.isPhysicalGamepad() && it.controllerNumber > 0 }
                    .mapNotNull { pad -> seatById[controllerIdOf(pad)]?.let { pad.controllerNumber to it } }
                    .toMap()
            }.distinctUntilChanged().collect { seats ->
                runCatching { PadSeats.write(file, seats) }
            }
        }
    }
}
