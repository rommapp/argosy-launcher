package com.nendo.argosy.hardware

import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.RootCommandRunner
import java.io.File
import kotlin.math.abs

internal data class CpuPolicyTable(val id: Int, val frequenciesKhz: List<Int>) {
    val minKhz: Int get() = frequenciesKhz.first()
    val maxKhz: Int get() = frequenciesKhz.last()
}

internal data class QualcommClockTables(val policies: List<CpuPolicyTable>, val gpuHz: List<Int>)

internal data class QualcommModeProfile(
    val cpuMinKhz: List<Int>,
    val cpuMaxKhz: List<Int>,
    val gpuMinHz: Int,
    val gpuMaxHz: Int
)

internal object QualcommProfiles {
    private const val ECO_CPU_CAP = 0.55
    private const val ECO_GPU_CAP = 0.40
    private const val GAMING_GPU_FLOOR = 0.68
    private const val MAX_CPU_FLOOR = 0.50

    fun derive(tables: QualcommClockTables): Map<PerformanceMode, QualcommModeProfile> {
        val policies = tables.policies
        val gpu = tables.gpuHz
        val stockCpuMin = policies.map { it.minKhz }
        val stockCpuMax = policies.map { it.maxKhz }
        val candidates = listOf(
            PerformanceMode.ECO to QualcommModeProfile(
                cpuMinKhz = stockCpuMin,
                cpuMaxKhz = policies.map { it.frequenciesKhz.nearest(it.maxKhz * ECO_CPU_CAP) },
                gpuMinHz = gpu.first(),
                gpuMaxHz = gpu.nearest(gpu.last() * ECO_GPU_CAP)
            ),
            PerformanceMode.BALANCED to QualcommModeProfile(stockCpuMin, stockCpuMax, gpu.first(), gpu.last()),
            PerformanceMode.GAMING to QualcommModeProfile(
                cpuMinKhz = stockCpuMin,
                cpuMaxKhz = stockCpuMax,
                gpuMinHz = gpu.nearest(gpu.last() * GAMING_GPU_FLOOR),
                gpuMaxHz = gpu.last()
            ),
            PerformanceMode.MAX to QualcommModeProfile(
                cpuMinKhz = policies.map { it.frequenciesKhz.nearest(it.maxKhz * MAX_CPU_FLOOR) },
                cpuMaxKhz = stockCpuMax,
                gpuMinHz = gpu[(gpu.lastIndex - 1).coerceAtLeast(0)],
                gpuMaxHz = gpu.last()
            )
        )
        return candidates.distinctBy { it.second }.toMap()
    }

    /**
     * The mode [live] is running. The GPU floor must equal the mode's; every cap may sit at or
     * below the mode's own, since thermal and vendor limits lower scaling_max_freq and the GPU
     * ceiling on their own; and a CPU floor counts when it reaches the mode's floor or its capped
     * ceiling, since touch and launch boosts raise scaling_min_freq. Of the modes that fit, the
     * one whose caps sit nearest the live caps wins.
     */
    fun modeMatching(
        profiles: Map<PerformanceMode, QualcommModeProfile>,
        live: QualcommModeProfile
    ): PerformanceMode? = profiles.entries
        .filter { (_, profile) -> fits(profile, live) }
        .minByOrNull { (_, profile) -> capDistance(profile, live) }
        ?.key

    private fun fits(profile: QualcommModeProfile, live: QualcommModeProfile): Boolean {
        if (profile.cpuMaxKhz.size != live.cpuMaxKhz.size || profile.cpuMinKhz.size != live.cpuMinKhz.size) {
            return false
        }
        if (profile.gpuMinHz != live.gpuMinHz || live.gpuMaxHz > profile.gpuMaxHz) return false
        return profile.cpuMaxKhz.indices.all { i ->
            live.cpuMaxKhz[i] <= profile.cpuMaxKhz[i] &&
                live.cpuMinKhz[i] >= minOf(profile.cpuMinKhz[i], live.cpuMaxKhz[i])
        }
    }

    private fun capDistance(profile: QualcommModeProfile, live: QualcommModeProfile): Long =
        profile.cpuMaxKhz.indices.sumOf { i -> (profile.cpuMaxKhz[i] - live.cpuMaxKhz[i]).toLong() } +
            (profile.gpuMaxHz - live.gpuMaxHz) / HZ_PER_KHZ

    private const val HZ_PER_KHZ = 1_000L

    private fun List<Int>.nearest(target: Double): Int = minBy { abs(it - target) }
}

internal class RootedQualcommPerformance(
    private val root: File,
    private val runner: RootCommandRunner,
    private val tables: QualcommClockTables
) : DevicePerformance {
    private val profiles = QualcommProfiles.derive(tables)

    override val modes: List<PerformanceMode> = profiles.keys.toList()
    override val controlsFan = false
    override val controlsRefreshRate = false
    override val needsRootGrant = true
    override val restoresSavedMode = true
    override val canWrite: Boolean get() = runner.isGranted

    override fun requestAccess(): Boolean = runner.isGranted || runner.requestGrant()

    override fun currentMode(): PerformanceMode? {
        val cpuMinPaths = tables.policies.map { policyPath(it.id, "scaling_min_freq") }
        val cpuMaxPaths = tables.policies.map { policyPath(it.id, "scaling_max_freq") }
        val gpuPaths = listOf(path("$GPU/devfreq/min_freq"), path("$GPU/devfreq/max_freq"))
        val values = readInts(cpuMinPaths + cpuMaxPaths + gpuPaths) ?: return null
        val count = tables.policies.size
        val live = QualcommModeProfile(
            cpuMinKhz = values.subList(0, count),
            cpuMaxKhz = values.subList(count, count * 2),
            gpuMinHz = values[count * 2],
            gpuMaxHz = values[count * 2 + 1]
        )
        return QualcommProfiles.modeMatching(profiles, live)
    }

    override fun applyMode(mode: PerformanceMode): Boolean {
        val profile = profiles[mode] ?: return false
        if (!runner.isGranted) return false
        runner.execute(writeCommands(profile).joinToString("; ")).onFailure { failure ->
            Logger.warn(TAG, "writing $mode reported: ${failure.message}")
        }
        return currentMode() == mode
    }

    internal fun writeCommands(profile: QualcommModeProfile): List<String> {
        val cpu = tables.policies.flatMapIndexed { i, policy ->
            val min = policyPath(policy.id, "scaling_min_freq")
            listOf(
                "echo ${policy.minKhz} > $min",
                "echo ${profile.cpuMaxKhz[i]} > ${policyPath(policy.id, "scaling_max_freq")}",
                "echo ${profile.cpuMinKhz[i]} > $min"
            )
        }
        val samsungMin = path("$SAMSUNG_GPU/gpu_min_clock")
        val samsungMax = path("$SAMSUNG_GPU/gpu_max_clock")
        val samsung = listOf(
            "[ -e $samsungMin ] && echo ${tables.gpuHz.first() / HZ_PER_MHZ} > $samsungMin",
            "[ -e $samsungMax ] && echo ${profile.gpuMaxHz / HZ_PER_MHZ} > $samsungMax",
            "[ -e $samsungMin ] && echo ${profile.gpuMinHz / HZ_PER_MHZ} > $samsungMin"
        )
        val devfreqMin = path("$GPU/devfreq/min_freq")
        val kgsl = listOf(
            "echo ${tables.gpuHz.first()} > $devfreqMin",
            "echo ${profile.gpuMaxHz} > ${path("$GPU/devfreq/max_freq")}",
            "echo ${profile.gpuMaxHz} > ${path("$GPU/max_gpuclk")}",
            "echo ${profile.gpuMinHz} > $devfreqMin"
        )
        return cpu + samsung + kgsl
    }

    private fun path(relative: String): String = File(root, relative).path

    private fun policyPath(policy: Int, name: String): String = path("$CPUFREQ/policy$policy/$name")

    private fun readInts(paths: List<String>): List<Int>? {
        val direct = paths.map { runCatching { File(it).readText().trim().toInt() }.getOrNull() }
        if (direct.all { it != null }) return direct.filterNotNull()
        if (!runner.isGranted) return null
        val script = paths.joinToString("; ") { "cat $it 2>/dev/null || echo" }
        val lines = runner.execute(script).getOrNull()?.lines() ?: return null
        return paths.indices.map { i -> direct[i] ?: lines.getOrNull(i)?.trim()?.toIntOrNull() ?: return null }
    }

    companion object {
        private const val TAG = "RootedQualcommPerformance"
        private const val CPUFREQ = "sys/devices/system/cpu/cpufreq"
        private const val GPU = "sys/class/kgsl/kgsl-3d0"
        private const val SAMSUNG_GPU = "sys/kernel/gpu"
        private const val HZ_PER_MHZ = 1_000_000
        private val POLICY = Regex("policy(\\d+)")

        fun detect(root: File, runner: RootCommandRunner): RootedQualcommPerformance? {
            if (!File(root, GPU).isDirectory) return null
            val tables = readTables(root) ?: return null
            if (!runner.isInstalled) return null
            return RootedQualcommPerformance(root, runner, tables)
        }

        private fun readTables(root: File): QualcommClockTables? {
            val policyIds = File(root, CPUFREQ).listFiles()
                ?.mapNotNull { POLICY.matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull() }
                ?.sorted()
                .orEmpty()
            if (policyIds.isEmpty()) return null
            val policies = policyIds.map { id -> readPolicyTable(root, id) ?: return null }
            val gpu = readNumbers(File(root, "$GPU/devfreq/available_frequencies")).distinct().sorted()
            if (gpu.size < 2) return null
            return QualcommClockTables(policies, gpu)
        }

        private fun readPolicyTable(root: File, id: Int): CpuPolicyTable? {
            val dir = File(root, "$CPUFREQ/policy$id")
            val min = readNumbers(File(dir, "cpuinfo_min_freq")).firstOrNull() ?: return null
            val max = readNumbers(File(dir, "cpuinfo_max_freq")).firstOrNull() ?: return null
            val steps = (readNumbers(File(dir, "scaling_available_frequencies")) + min + max)
                .filter { it in min..max }
                .distinct()
                .sorted()
            return CpuPolicyTable(id, steps)
        }

        private fun readNumbers(file: File): List<Int> =
            runCatching { file.readText() }.getOrNull()
                ?.split(Regex("\\s+"))
                ?.mapNotNull { it.toIntOrNull() }
                .orEmpty()
    }
}
