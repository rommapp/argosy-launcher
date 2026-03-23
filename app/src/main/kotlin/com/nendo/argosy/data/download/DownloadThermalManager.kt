package com.nendo.argosy.data.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.util.Log
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class ThermalState { NORMAL, THROTTLED, PAUSED }

data class ThermalStatus(
    val state: ThermalState = ThermalState.NORMAL,
    val cpuTemp: Float = 0f,
    val batteryTemp: Float = 0f,
    val throttleMultiplier: Float = 1.0f
)

@Singleton
class DownloadThermalManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager
) {
    private val _thermalStatus = MutableStateFlow(ThermalStatus())
    val thermalStatus: StateFlow<ThermalStatus> = _thermalStatus.asStateFlow()

    private val scope = SafeCoroutineScope(Dispatchers.IO, "DownloadThermalManager")
    private val wakeLock: PowerManager.WakeLock =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)
    private var monitorJob: Job? = null
    private var fanRefreshJob: Job? = null
    private var isScreenOff = false
    private var isReceiverRegistered = false
    private var fanWasControlled = false
    private var currentFanDuty = 0

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
                Intent.ACTION_SCREEN_ON -> onScreenOn()
            }
        }
    }

    fun start() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            context.registerReceiver(screenReceiver, filter)
            isReceiverRegistered = true
        }
        observeDownloads()
    }

    fun stop() {
        if (isReceiverRegistered) {
            runCatching { context.unregisterReceiver(screenReceiver) }
            isReceiverRegistered = false
        }
        monitorJob?.cancel()
        fanRefreshJob?.cancel()
        releaseFanControl()
        releaseWakeLock()
        scope.cancel()
    }

    private fun observeDownloads() {
        scope.launch {
            downloadManager.state.collect { state ->
                val hasWork = state.activeDownloads.isNotEmpty()
                if (hasWork && monitorJob?.isActive != true) {
                    startThermalMonitoring()
                    if (isScreenOff) startFanRefresh()
                } else if (!hasWork) {
                    stopThermalMonitoring()
                    fanRefreshJob?.cancel()
                    releaseFanControl()
                }
            }
        }
    }

    private fun startThermalMonitoring() {
        monitorJob = scope.launch {
            Log.d(TAG, "Thermal monitoring started")
            while (isActive) {
                val cpuTemp = readMaxCpuTemp()
                val batteryTemp = readBatteryTemp()
                val newStatus = calculateThermalStatus(cpuTemp, batteryTemp)
                _thermalStatus.value = newStatus
                delay(MONITOR_INTERVAL_MS)
            }
        }
    }

    private fun stopThermalMonitoring() {
        monitorJob?.cancel()
        _thermalStatus.value = ThermalStatus()
    }

    private fun onScreenOff() {
        isScreenOff = true
        val active = hasActiveDownloads()
        Log.d(TAG, "Screen off, active downloads=$active")
        if (active) startFanRefresh()
    }

    private fun onScreenOn() {
        isScreenOff = false
        Log.d(TAG, "Screen on, cancelling fan refresh")
        fanRefreshJob?.cancel()
        releaseFanControl()
    }

    private fun startFanRefresh() {
        if (fanRefreshJob?.isActive == true) return
        Log.d(TAG, "Starting fan refresh loop")
        acquireWakeLock()
        fanRefreshJob = scope.launch {
            try {
                while (isActive && isScreenOff && hasActiveDownloads()) {
                    val target = computeFanDuty(_thermalStatus.value)
                    val duty = if (target >= currentFanDuty) {
                        target
                    } else {
                        maxOf(target, currentFanDuty - FAN_RAMP_DOWN_STEP)
                    }
                    setFanDuty(duty)
                    delay(FAN_REFRESH_INTERVAL_MS)
                }
            } finally {
                releaseFanControl()
                releaseWakeLock()
            }
        }
    }

    private fun computeFanDuty(status: ThermalStatus): Int {
        val cpuDuty = interpolateDuty(
            status.cpuTemp, CPU_FAN_OFF_TEMP, CPU_FAN_MAX_TEMP
        )
        val batteryDuty = interpolateDuty(
            status.batteryTemp, BAT_FAN_OFF_TEMP, BAT_FAN_MAX_TEMP
        )
        return maxOf(cpuDuty, batteryDuty)
    }

    private fun interpolateDuty(temp: Float, lowTemp: Float, highTemp: Float): Int {
        if (temp <= lowTemp) return FAN_DUTY_MIN
        if (temp >= highTemp) return FAN_DUTY_MAX
        val fraction = (temp - lowTemp) / (highTemp - lowTemp)
        return (FAN_DUTY_MIN + fraction * (FAN_DUTY_MAX - FAN_DUTY_MIN)).toInt()
    }

    private fun setFanDuty(duty: Int) {
        if (!fanControlAvailable()) return
        val changed = duty != currentFanDuty
        runCatching {
            File(FAN_STATE_PATH).writeText("1")
            File(FAN_DUTY_PATH).writeText(duty.toString())
            fanWasControlled = true
            currentFanDuty = duty
            if (changed) {
                val s = _thermalStatus.value
                Log.d(TAG, "Fan duty=$duty cpu=${"%.1f".format(s.cpuTemp)}C bat=${"%.1f".format(s.batteryTemp)}C")
            }
        }
    }

    private fun releaseFanControl() {
        if (!fanWasControlled) return
        runCatching {
            File(FAN_STATE_PATH).writeText("0")
            Log.d(TAG, "Fan control released to system governor")
        }
        fanWasControlled = false
        currentFanDuty = 0
    }

    private fun readMaxCpuTemp(): Float {
        return runCatching {
            val thermalDir = File(THERMAL_PATH)
            val zones = thermalDir.list()
                ?.filter { it.startsWith("thermal_zone") }
                ?: return@runCatching 0f
            zones.mapNotNull { name ->
                val type = File(thermalDir, "$name/type").readText().trim()
                if (type.startsWith("cpu")) {
                    File(thermalDir, "$name/temp").readText().trim()
                        .toFloatOrNull()?.div(1000)
                } else null
            }.maxOrNull() ?: 0f
        }.getOrDefault(0f)
    }

    private fun readBatteryTemp(): Float {
        return runCatching {
            File(BATTERY_TEMP_PATH).readText().trim().toFloat() / 10f
        }.getOrDefault(0f)
    }

    private fun calculateThermalStatus(cpuTemp: Float, batteryTemp: Float): ThermalStatus {
        val cpuState = when {
            cpuTemp >= CPU_PAUSE_TEMP -> ThermalState.PAUSED
            cpuTemp >= CPU_THROTTLE_TEMP -> ThermalState.THROTTLED
            else -> ThermalState.NORMAL
        }
        val batteryState = when {
            batteryTemp >= BATTERY_PAUSE_TEMP -> ThermalState.PAUSED
            batteryTemp >= BATTERY_THROTTLE_TEMP -> ThermalState.THROTTLED
            else -> ThermalState.NORMAL
        }
        val worstState = maxOf(cpuState, batteryState)
        val multiplier = when (worstState) {
            ThermalState.PAUSED -> 0f
            ThermalState.THROTTLED -> 0.5f
            ThermalState.NORMAL -> 1.0f
        }
        return ThermalStatus(worstState, cpuTemp, batteryTemp, multiplier)
    }

    private fun hasActiveDownloads(): Boolean {
        return downloadManager.state.value.activeDownloads.isNotEmpty()
    }

    private fun acquireWakeLock() {
        if (!wakeLock.isHeld) {
            wakeLock.acquire(MAX_WAKELOCK_DURATION_MS)
            Log.d(TAG, "Wake lock acquired for fan control")
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock.isHeld) {
            wakeLock.release()
            Log.d(TAG, "Wake lock released")
        }
    }

    private fun fanControlAvailable() = File(FAN_STATE_PATH).exists()

    companion object {
        private const val TAG = "DownloadThermalManager"
        private const val MONITOR_INTERVAL_MS = 5000L
        private const val FAN_REFRESH_INTERVAL_MS = 1_000L

        private const val CPU_THROTTLE_TEMP = 85f
        private const val CPU_PAUSE_TEMP = 90f
        private const val BATTERY_THROTTLE_TEMP = 38f
        private const val BATTERY_PAUSE_TEMP = 43f

        private const val FAN_DUTY_MIN = 8000
        private const val FAN_DUTY_MAX = 30000
        private const val CPU_FAN_OFF_TEMP = 45f
        private const val CPU_FAN_MAX_TEMP = 85f
        private const val BAT_FAN_OFF_TEMP = 32f
        private const val BAT_FAN_MAX_TEMP = 42f
        private const val FAN_RAMP_DOWN_STEP = 50

        private const val WAKELOCK_TAG = "argosy:thermal_fan"
        private const val MAX_WAKELOCK_DURATION_MS = 60 * 60 * 1000L

        private const val THERMAL_PATH = "/sys/class/thermal/"
        private const val FAN_STATE_PATH = "/sys/class/gpio5_pwm2/state"
        private const val FAN_DUTY_PATH = "/sys/class/gpio5_pwm2/duty"
        private const val BATTERY_TEMP_PATH = "/sys/class/power_supply/battery/temp"
    }
}
