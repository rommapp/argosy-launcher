package com.nendo.argosy.hardware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.nendo.argosy.domain.model.BatteryEvent
import com.nendo.argosy.domain.model.BatterySample
import com.nendo.argosy.domain.model.BatteryStep
import com.nendo.argosy.domain.model.advanceBattery
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

private const val EVENT_BUFFER = 8

/**
 * The battery as the process that started it sees it, read from the battery broadcast.
 * [events] emits each reading that set off at least one [BatteryEvent], with those events in
 * threshold order. The injected instance belongs to the main process; a game slot process builds
 * its own.
 */
@Singleton
class BatteryMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _sample = MutableStateFlow<BatterySample?>(null)
    val sample: StateFlow<BatterySample?> = _sample.asStateFlow()

    private val _events = MutableSharedFlow<BatteryStep>(
        extraBufferCapacity = EVENT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<BatteryStep> = _events.asSharedFlow()

    private var step: BatteryStep? = null
    private var receiver: BroadcastReceiver? = null

    fun start() {
        if (receiver != null) return
        val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                onBatteryChanged(intent)
            }
        }
        receiver = batteryReceiver
        context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    fun stop() {
        receiver?.let { registered -> runCatching { context.unregisterReceiver(registered) } }
        receiver = null
        step = null
    }

    fun chargeTimeRemaining(): Duration? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val batteryManager = context.getSystemService(BatteryManager::class.java) ?: return null
        val remainingMs = batteryManager.computeChargeTimeRemaining()
        return if (remainingMs > 0) Duration.ofMillis(remainingMs) else null
    }

    fun dischargeTimeRemaining(): Duration? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val powerManager = context.getSystemService(PowerManager::class.java) ?: return null
        return runCatching { powerManager.batteryDischargePrediction }.getOrNull()
            ?.takeIf { !it.isNegative && !it.isZero }
    }

    private fun onBatteryChanged(intent: Intent) {
        val current = intent.toBatterySample() ?: return
        val next = advanceBattery(step, current)
        step = next
        _sample.value = current
        if (next.events.isNotEmpty()) _events.tryEmit(next)
    }
}

/**
 * Reads a battery broadcast. Null when it reports no usable level. Charging means a charger is
 * attached or the battery reports charging or full.
 */
fun Intent.toBatterySample(): BatterySample? {
    val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) return null
    val status = getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val plugged = getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
    return BatterySample(
        level = level * 100 / scale,
        charging = plugged != 0 ||
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    )
}
