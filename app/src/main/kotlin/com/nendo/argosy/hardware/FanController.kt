package com.nendo.argosy.hardware

import com.nendo.argosy.util.AppPaths
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FanController @Inject constructor() {

    private val speedFile = File(AppPaths.FAN_SPEED_PATH)
    private val stateFile = File(AppPaths.FAN_STATE_PATH)
    private val dutyFile = File(AppPaths.FAN_DUTY_PATH)
    private val periodFile = File(AppPaths.FAN_PERIOD_PATH)

    fun isAvailable(): Boolean = speedFile.exists()

    fun rpm(): Int? = readInt(speedFile)

    /**
     * How hard the fan is driven, as its PWM duty over the PWM period, from 0 to 1.
     */
    fun dutyFraction(): Float? {
        val duty = readInt(dutyFile) ?: return null
        val period = readInt(periodFile)?.takeIf { it > 0 } ?: return null
        return (duty.toFloat() / period).coerceIn(0f, 1f)
    }

    private fun readInt(file: File): Int? = runCatching { file.readText().trim().toInt() }.getOrNull()

    fun isControllable(): Boolean = stateFile.exists()

    fun setSpeed(duty: Int): Boolean = runCatching {
        stateFile.writeText("1")
        dutyFile.writeText(duty.toString())
    }.isSuccess

    fun release(): Boolean = runCatching {
        stateFile.writeText("0")
    }.isSuccess

    companion object {
        const val CUSTOM_DUTY_MIN = 10_000
        const val CUSTOM_DUTY_MAX = 50_000
        const val CUSTOM_DUTY_STEP = 2_500
        const val SPORT_DUTY = 25_000
    }
}
