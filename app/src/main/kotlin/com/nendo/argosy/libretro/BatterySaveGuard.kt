package com.nendo.argosy.libretro

import android.content.Context
import com.nendo.argosy.hardware.BatteryMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Calls [onCritical] when the battery falls to 5, 3 and 1 percent while discharging, at most once
 * each per discharge, and never while charging. It reads the battery through its own receiver in
 * the process that owns the game. Calls run one at a time on [scope]; [onCritical] picks its own
 * dispatcher.
 */
class BatterySaveGuard(
    context: Context,
    private val scope: CoroutineScope,
    private val onCritical: suspend () -> Unit
) {
    private val monitor = BatteryMonitor(context.applicationContext)
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch(Dispatchers.Main.immediate) {
            monitor.events.collect { step ->
                if (step.events.any { it.isCritical }) onCritical()
            }
        }
        monitor.start()
    }

    fun stop() {
        job?.cancel()
        job = null
        monitor.stop()
    }
}
