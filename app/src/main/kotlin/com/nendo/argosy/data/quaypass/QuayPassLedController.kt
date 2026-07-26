package com.nendo.argosy.data.quaypass

import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drives the device's system notification LED (the sysfs RGB indicator) for
 * QuayPass. This is the physical status light some handhelds carry (the Odin 3
 * has one; many devices, including the RP6, do not), distinct from the joystick
 * ambient LEDs owned by AmbientLedManager. Every entry point no-ops when the
 * sysfs nodes are absent or unwritable, so it is safe to call unconditionally.
 * Restored from the original QuayPass reward-glow behaviour.
 */
@Singleton
class QuayPassLedController @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentAnimation: Job? = null

    private val redPath = "/sys/class/leds/red/brightness"
    private val greenPath = "/sys/class/leds/green/brightness"
    private val bluePath = "/sys/class/leds/blue/brightness"

    fun isSupported(): Boolean {
        val isOdin = Build.MODEL?.contains("Odin", ignoreCase = true) == true
        return isOdin && runCatching { File(greenPath).canWrite() }.getOrDefault(false)
    }

    fun playNewPassAnimation() {
        if (!isSupported()) return
        currentAnimation?.cancel()
        currentAnimation = scope.launch {
            repeat(3) {
                setColor(0, 100, 0)
                delay(100)
                setColor(0, 0, 0)
                delay(100)
            }
            for (brightness in 100 downTo 0 step 5) {
                setColor(0, brightness, 0)
                delay(25)
            }
            setColor(0, 0, 0)
        }
    }

    fun stop() {
        if (!isSupported()) return
        currentAnimation?.cancel()
        currentAnimation = null
        scope.launch { setColor(0, 0, 0) }
    }

    private fun setColor(r: Int, g: Int, b: Int) {
        runCatching {
            File(redPath).writeText(r.toString())
            File(greenPath).writeText(g.toString())
            File(bluePath).writeText(b.toString())
        }.onFailure { Log.w(TAG, "Failed to set system LED: ${it.message}") }
    }

    companion object {
        private const val TAG = "QuayPassLedController"
    }
}
