package com.nendo.argosy.hardware

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One reading of the device's clocks, temperatures and fan. Each value is null where the device
 * does not expose it or blocks the read.
 */
data class HardwareSample(
    val cpuKhz: Int?,
    val cpuMaxKhz: Int?,
    val gpuHz: Long?,
    val gpuMaxHz: Long?,
    val gpuBusyPercent: Int?,
    val cpuTempC: Float?,
    val gpuTempC: Float?,
    val fanRpm: Int?,
    val fanFraction: Float?,
    val batteryTempC: Float?,
    val batteryLevel: Int?
)

/**
 * Reads [HardwareSample]s from sysfs and the battery broadcast. The CPU reading is the fastest
 * cluster's current clock; each temperature is the hottest zone of its kind.
 */
@Singleton
class HardwareStatsReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fanController: FanController
) {
    private val cpuPolicies: List<File> by lazy {
        File(CPUFREQ_DIR).listFiles { file -> file.name.startsWith("policy") }?.sortedBy { it.name }.orEmpty()
    }

    private val thermalZones: List<Pair<String, File>> by lazy {
        File(THERMAL_DIR).listFiles { file -> file.name.startsWith("thermal_zone") }
            ?.mapNotNull { zone ->
                val type = runCatching { File(zone, "type").readText().trim() }.getOrNull() ?: return@mapNotNull null
                type to File(zone, "temp")
            }
            .orEmpty()
    }

    private val cpuZones by lazy { thermalZones.filter { (type, _) -> CPU_ZONE.matches(type) }.map { it.second } }
    private val gpuZones by lazy { thermalZones.filter { (type, _) -> GPU_ZONE.matches(type) }.map { it.second } }

    fun read(): HardwareSample {
        val clusters = cpuPolicies.mapNotNull { policy ->
            val current = readLong(File(policy, "scaling_cur_freq"))?.toInt() ?: return@mapNotNull null
            current to readLong(File(policy, "cpuinfo_max_freq"))?.toInt()
        }
        val fastest = clusters.maxByOrNull { it.first }
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val fanAvailable = fanController.isAvailable()
        return HardwareSample(
            cpuKhz = fastest?.first,
            cpuMaxKhz = clusters.mapNotNull { it.second }.maxOrNull(),
            gpuHz = readLong(File(KGSL_DIR, "gpuclk")),
            gpuMaxHz = readLong(File(KGSL_DIR, "max_gpuclk")),
            gpuBusyPercent = readLong(File(KGSL_DIR, "gpu_busy_percentage"))?.toInt(),
            cpuTempC = hottest(cpuZones),
            gpuTempC = hottest(gpuZones),
            fanRpm = if (fanAvailable) fanController.rpm() else null,
            fanFraction = if (fanAvailable) fanController.dutyFraction() else null,
            batteryTempC = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                ?.takeIf { it != Int.MIN_VALUE }
                ?.let { it / 10f },
            batteryLevel = battery?.let { intent ->
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) level * 100 / scale else null
            }
        )
    }

    private fun hottest(zones: List<File>): Float? =
        zones.mapNotNull { readLong(it) }.maxOrNull()?.let { millidegrees -> millidegrees / 1000f }

    private fun readLong(file: File): Long? =
        runCatching { file.readText().trim().filter { it.isDigit() }.toLong() }.getOrNull()

    private companion object {
        const val CPUFREQ_DIR = "/sys/devices/system/cpu/cpufreq"
        const val THERMAL_DIR = "/sys/class/thermal"
        const val KGSL_DIR = "/sys/class/kgsl/kgsl-3d0"
        val CPU_ZONE = Regex("^(cpu|cpuss)[-_].*")
        val GPU_ZONE = Regex("^(gpu|gpuss)[-_].*")
    }
}
