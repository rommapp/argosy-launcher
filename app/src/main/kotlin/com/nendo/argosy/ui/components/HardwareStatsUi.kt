package com.nendo.argosy.ui.components

import com.nendo.argosy.hardware.HardwareSample

data class HardwareStatsUi(
    val cpuMhz: Int? = null,
    val cpuLoad: Float? = null,
    val cpuTempC: Float? = null,
    val cpuTempHistory: List<Float> = emptyList(),
    val gpuMhz: Int? = null,
    val gpuLoad: Float? = null,
    val gpuTempC: Float? = null,
    val gpuTempHistory: List<Float> = emptyList(),
    val fanRpm: Int? = null,
    val fanLoad: Float? = null,
    val batteryTempC: Float? = null
) {
    val hasCpu: Boolean get() = cpuMhz != null || cpuTempC != null
    val hasGpu: Boolean get() = gpuMhz != null || gpuTempC != null
    val hasCooling: Boolean get() = fanRpm != null || batteryTempC != null
    val isEmpty: Boolean get() = !hasCpu && !hasGpu && !hasCooling

    companion object {
        const val HISTORY_SIZE = 30

        /**
         * Builds the next reading from [sample], appending its temperatures to the history kept in
         * [previous]. A null [previous] starts the history over.
         */
        fun from(sample: HardwareSample, previous: HardwareStatsUi?): HardwareStatsUi = HardwareStatsUi(
            cpuMhz = sample.cpuKhz?.let { it / 1000 },
            cpuLoad = fraction(sample.cpuKhz?.toLong(), sample.cpuMaxKhz?.toLong()),
            cpuTempC = sample.cpuTempC,
            cpuTempHistory = appended(previous?.cpuTempHistory, sample.cpuTempC),
            gpuMhz = sample.gpuHz?.let { (it / 1_000_000).toInt() },
            gpuLoad = sample.gpuBusyPercent?.let { (it / 100f).coerceIn(0f, 1f) }
                ?: fraction(sample.gpuHz, sample.gpuMaxHz),
            gpuTempC = sample.gpuTempC,
            gpuTempHistory = appended(previous?.gpuTempHistory, sample.gpuTempC),
            fanRpm = sample.fanRpm,
            fanLoad = sample.fanFraction,
            batteryTempC = sample.batteryTempC
        )

        private fun fraction(current: Long?, max: Long?): Float? {
            if (current == null || max == null || max <= 0) return null
            return (current.toFloat() / max).coerceIn(0f, 1f)
        }

        private fun appended(history: List<Float>?, value: Float?): List<Float> {
            val base = history.orEmpty()
            if (value == null) return base
            return (base + value).takeLast(HISTORY_SIZE)
        }
    }
}
