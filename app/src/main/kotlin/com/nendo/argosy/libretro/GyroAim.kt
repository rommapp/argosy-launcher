package com.nendo.argosy.libretro

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import com.nendo.argosy.data.preferences.GyroAimSensitivity
import com.swordfish.libretrodroid.GLRetroView
import kotlin.math.abs
import kotlin.math.hypot

/**
 * The angle the device has turned through since aiming began, as a right-stick position. A turn of
 * [BASE_RANGE_RADIANS] at 100% sensitivity reaches the edge of the stick, and the position holds
 * while the device holds still.
 */
class GyroAimTracker {
    private var yaw = 0f
    private var pitch = 0f
    private var range = BASE_RANGE_RADIANS
    private var invertX = false
    private var invertY = false

    fun configure(invertX: Boolean, invertY: Boolean, sensitivityPercent: Int) {
        this.invertX = invertX
        this.invertY = invertY
        range = BASE_RANGE_RADIANS * 100f / GyroAimSensitivity.clamp(sensitivityPercent)
        yaw = yaw.coerceIn(-range, range)
        pitch = pitch.coerceIn(-range, range)
    }

    /**
     * Turns at [aboutScreenX] and [aboutScreenY] rad/s, about the screen's horizontal and vertical
     * axes, for [dtSeconds].
     */
    fun rotate(aboutScreenX: Float, aboutScreenY: Float, dtSeconds: Float) {
        val dx = -aboutScreenY * dtSeconds * if (invertX) -1f else 1f
        val dy = -aboutScreenX * dtSeconds * if (invertY) -1f else 1f
        yaw = (yaw + dx).coerceIn(-range, range)
        pitch = (pitch + dy).coerceIn(-range, range)
    }

    fun holdAt(x: Float, y: Float) {
        yaw = x * range
        pitch = y * range
    }

    fun recenter() {
        yaw = 0f
        pitch = 0f
    }

    fun stick(): Pair<Float, Float> = (yaw / range) to (pitch / range)

    companion object {
        const val BASE_RANGE_RADIANS = 0.35f
    }
}

/**
 * Points the right stick where the device is turned, for cores that aim a cursor with it. Moving
 * the physical right stick takes over and leaves the aim wherever the stick lets go.
 */
class GyroAim(
    context: Context,
    private val getRetroView: () -> GLRetroView
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val tracker = GyroAimTracker()

    private var lastTimestampNs = 0L
    private var physicalHeld = false

    var active: Boolean = false
        private set

    fun configure(invertX: Boolean, invertY: Boolean, sensitivityPercent: Int) =
        tracker.configure(invertX, invertY, sensitivityPercent)

    fun start() {
        if (active || gyroscope == null) return
        lastTimestampNs = 0L
        active = sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        if (!active) return
        sensorManager.unregisterListener(this)
        active = false
        send(0f, 0f)
    }

    fun recenter() {
        tracker.recenter()
        if (active) send(0f, 0f)
    }

    /**
     * The stick value to hand the core for a physical right-stick reading [x], [y]. A deflected
     * stick wins and moves the aim with it; a released stick leaves the gyro aim where it was.
     */
    fun combine(x: Float, y: Float): Pair<Float, Float> {
        physicalHeld = hypot(x, y) > STICK_TAKEOVER
        if (physicalHeld) {
            tracker.holdAt(x, y)
            return x to y
        }
        return tracker.stick()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val previous = lastTimestampNs
        lastTimestampNs = event.timestamp
        if (previous == 0L || physicalHeld) return
        val dt = (event.timestamp - previous) / 1_000_000_000f
        if (dt <= 0f || dt > MAX_STEP_SECONDS) return

        val (aboutScreenX, aboutScreenY) = screenAxes(event.values[0], event.values[1])
        tracker.rotate(aboutScreenX, aboutScreenY, dt)
        val (x, y) = tracker.stick()
        send(x, y)
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    private fun send(x: Float, y: Float) {
        getRetroView().sendMotionEvent(
            GLRetroView.MOTION_SOURCE_ANALOG_RIGHT,
            if (abs(x) < DEADBAND) 0f else x,
            if (abs(y) < DEADBAND) 0f else y,
            0
        )
    }

    @Suppress("DEPRECATION")
    private fun screenAxes(deviceX: Float, deviceY: Float): Pair<Float, Float> =
        when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_90 -> -deviceY to deviceX
            Surface.ROTATION_180 -> -deviceX to -deviceY
            Surface.ROTATION_270 -> deviceY to -deviceX
            else -> deviceX to deviceY
        }

    companion object {
        private const val STICK_TAKEOVER = 0.2f
        private const val MAX_STEP_SECONDS = 0.1f
        private const val DEADBAND = 0.01f
    }
}
