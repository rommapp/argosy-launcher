package com.nendo.argosy.hardware

import com.nendo.argosy.util.RootCommandRunner
import com.nendo.argosy.util.RootShell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RootedQualcommPerformanceTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val tables = QualcommClockTables(
        policies = POLICY_TABLES.map { (id, steps) -> CpuPolicyTable(id, steps) },
        gpuHz = GPU_TABLE.sorted()
    )

    @Test
    fun `modes derive from the sm8650 tables`() {
        val profiles = QualcommProfiles.derive(tables)

        assertEquals(
            listOf(PerformanceMode.ECO, PerformanceMode.BALANCED, PerformanceMode.GAMING, PerformanceMode.MAX),
            profiles.keys.toList()
        )
        assertEquals(
            QualcommModeProfile(STOCK_MIN, listOf(1_248_000, 1_708_800, 1_612_800, 1_824_000), 80_000_000, 422_000_000),
            profiles[PerformanceMode.ECO]
        )
        assertEquals(
            QualcommModeProfile(STOCK_MIN, STOCK_MAX, 80_000_000, 1_000_000_000),
            profiles[PerformanceMode.BALANCED]
        )
        assertEquals(
            QualcommModeProfile(STOCK_MIN, STOCK_MAX, 680_000_000, 1_000_000_000),
            profiles[PerformanceMode.GAMING]
        )
        assertEquals(
            QualcommModeProfile(listOf(1_132_800, 1_612_800, 1_497_600, 1_708_800), STOCK_MAX, 950_000_000, 1_000_000_000),
            profiles[PerformanceMode.MAX]
        )
    }

    @Test
    fun `a mode whose clocks equal an earlier one is dropped`() {
        val tiny = QualcommClockTables(listOf(CpuPolicyTable(0, listOf(300_000, 600_000))), listOf(100, 200))

        val profiles = QualcommProfiles.derive(tiny)

        assertEquals(listOf(PerformanceMode.ECO, PerformanceMode.BALANCED), profiles.keys.toList())
    }

    @Test
    fun `every mode reads back as itself once applied`() {
        val root = buildTree()
        val runner = ShellRunner()
        val performance = RootedQualcommPerformance.detect(root, runner)!!

        performance.modes.forEach { mode ->
            assertTrue("apply $mode", performance.applyMode(mode))
            assertEquals(mode, performance.currentMode())
        }
    }

    @Test
    fun `stock clocks read as balanced and a hand-tuned gpu floor as no mode`() {
        val root = buildTree()
        val performance = RootedQualcommPerformance.detect(root, ShellRunner())!!

        assertEquals(PerformanceMode.BALANCED, performance.currentMode())

        File(root, "$GPU/devfreq/min_freq").writeText("500000000\n")
        assertNull(performance.currentMode())
    }

    @Test
    fun `a vendor boost raising the cpu floor does not hide the applied mode`() {
        val root = buildTree()
        val performance = RootedQualcommPerformance.detect(root, ShellRunner())!!
        assertTrue(performance.applyMode(PerformanceMode.ECO))

        val cap = File(root, "$CPUFREQ/policy0/scaling_max_freq").readText().trim()
        File(root, "$CPUFREQ/policy0/scaling_min_freq").writeText("$cap\n")

        assertEquals(PerformanceMode.ECO, performance.currentMode())
    }

    @Test
    fun `a thermal limit lowering the prime core cap does not hide gaming`() {
        val root = buildTree()
        val performance = RootedQualcommPerformance.detect(root, ShellRunner())!!
        assertTrue(performance.applyMode(PerformanceMode.GAMING))

        File(root, "$CPUFREQ/policy7/scaling_max_freq").writeText("3340800\n")

        assertEquals(PerformanceMode.GAMING, performance.currentMode())
    }

    @Test
    fun `stock caps read as balanced, not eco, when both floors fit`() {
        val root = buildTree()
        val performance = RootedQualcommPerformance.detect(root, ShellRunner())!!
        assertTrue(performance.applyMode(PerformanceMode.BALANCED))

        assertEquals(PerformanceMode.BALANCED, performance.currentMode())
        assertTrue(performance.applyMode(PerformanceMode.ECO))
        assertEquals(PerformanceMode.ECO, performance.currentMode())
    }

    @Test
    fun `the governor is never written`() {
        val root = buildTree()
        val runner = ShellRunner()
        val performance = RootedQualcommPerformance.detect(root, runner)!!

        performance.modes.forEach { performance.applyMode(it) }

        assertTrue(runner.commands.none { it.contains("scaling_governor") })
        POLICY_TABLES.forEach { (id, _) ->
            assertEquals("walt", File(root, "$CPUFREQ/policy$id/scaling_governor").readText().trim())
        }
    }

    @Test
    fun `samsung gpu clocks are written in megahertz when the node exists`() {
        val root = buildTree(samsung = true)
        val performance = RootedQualcommPerformance.detect(root, ShellRunner())!!

        assertTrue(performance.applyMode(PerformanceMode.ECO))

        assertEquals("80", File(root, "$SAMSUNG_GPU/gpu_min_clock").readText().trim())
        assertEquals("422", File(root, "$SAMSUNG_GPU/gpu_max_clock").readText().trim())
    }

    @Test
    fun `without a grant a mode write runs nothing and fails`() {
        val root = buildTree()
        val runner = ShellRunner(granted = false)
        val performance = RootedQualcommPerformance.detect(root, runner)!!

        assertFalse(performance.canWrite)
        assertFalse(performance.applyMode(PerformanceMode.MAX))
        assertTrue(runner.commands.isEmpty())
    }

    @Test
    fun `detection needs the adreno node, cpufreq policies and an installed su, and starts no process`() {
        val full = buildTree()
        val runner = ShellRunner(granted = false)
        assertNotNull(RootedQualcommPerformance.detect(full, runner))
        assertEquals(0, runner.grantRequests)
        assertTrue(runner.commands.isEmpty())

        assertNull(RootedQualcommPerformance.detect(full, ShellRunner(isInstalled = false)))

        val noGpu = buildTree()
        File(noGpu, GPU).deleteRecursively()
        assertNull(RootedQualcommPerformance.detect(noGpu, ShellRunner()))

        val noPolicies = buildTree()
        File(noPolicies, CPUFREQ).deleteRecursively()
        assertNull(RootedQualcommPerformance.detect(noPolicies, ShellRunner()))
    }

    private fun buildTree(samsung: Boolean = false): File {
        val root = folder.newFolder()
        POLICY_TABLES.forEach { (id, steps) ->
            val dir = File(root, "$CPUFREQ/policy$id").apply { mkdirs() }
            File(dir, "scaling_available_frequencies").writeText(steps.joinToString(" ") + " \n")
            File(dir, "cpuinfo_min_freq").writeText("${steps.first()}\n")
            File(dir, "cpuinfo_max_freq").writeText("${steps.last()}\n")
            File(dir, "scaling_min_freq").writeText("${steps.first()}\n")
            File(dir, "scaling_max_freq").writeText("${steps.last()}\n")
            File(dir, "scaling_governor").writeText("walt\n")
        }
        val devfreq = File(root, "$GPU/devfreq").apply { mkdirs() }
        File(devfreq, "available_frequencies").writeText(GPU_TABLE.joinToString(" ") + " \n")
        File(devfreq, "min_freq").writeText("${GPU_TABLE.min()}\n")
        File(devfreq, "max_freq").writeText("${GPU_TABLE.max()}\n")
        File(root, "$GPU/max_gpuclk").writeText("${GPU_TABLE.max()}\n")
        if (samsung) {
            val gpu = File(root, SAMSUNG_GPU).apply { mkdirs() }
            File(gpu, "gpu_min_clock").writeText("680\n")
            File(gpu, "gpu_max_clock").writeText("1000\n")
        }
        return root
    }

    private class ShellRunner(
        override val isInstalled: Boolean = true,
        private var granted: Boolean = true
    ) : RootCommandRunner {
        override val isGranted: Boolean get() = granted
        val commands = mutableListOf<String>()
        var grantRequests = 0
            private set

        override fun requestGrant(): Boolean {
            grantRequests++
            granted = true
            return true
        }

        override fun execute(command: String): Result<String> {
            commands += command
            command.split("; ").forEach { step ->
                val match = WRITE.matchEntire(step) ?: return@forEach
                val (guard, value, target) = match.destructured
                if (guard.isNotEmpty() && !File(guard).exists()) return@forEach
                File(target).writeText("$value\n")
            }
            return Result.success("")
        }

        override fun runScript(script: String): RootShell.Result? = null

        private companion object {
            val WRITE = Regex("(?:\\[ -e (\\S+) ] && )?echo (\\S+) > (\\S+)")
        }
    }

    private companion object {
        const val CPUFREQ = "sys/devices/system/cpu/cpufreq"
        const val GPU = "sys/class/kgsl/kgsl-3d0"
        const val SAMSUNG_GPU = "sys/kernel/gpu"

        val GPU_TABLE = listOf(
            1_000_000_000, 950_000_000, 903_000_000, 834_000_000, 770_000_000, 720_000_000, 680_000_000,
            629_000_000, 578_000_000, 500_000_000, 422_000_000, 366_000_000, 310_000_000, 231_000_000,
            155_000_000, 80_000_000
        )

        private val PRIME = listOf(
            480_000, 576_000, 672_000, 787_200, 902_400, 1_017_600, 1_132_800, 1_248_000, 1_363_200,
            1_478_400, 1_593_600, 1_708_800, 1_824_000, 1_939_200, 2_035_200, 2_112_000, 2_169_600,
            2_246_400, 2_304_000, 2_380_800, 2_438_400, 2_496_000, 2_553_600, 2_630_400, 2_688_000,
            2_745_600, 2_803_200, 2_880_000, 2_937_600, 2_995_200, 3_052_800, 3_110_400, 3_187_200,
            3_264_000, 3_302_400, 3_398_400
        )

        private val GOLD = listOf(
            499_200, 614_400, 729_600, 844_800, 960_000, 1_075_200, 1_190_400, 1_286_400, 1_401_600,
            1_497_600, 1_612_800, 1_708_800, 1_824_000, 1_920_000, 2_035_200, 2_131_200, 2_188_800,
            2_246_400, 2_323_200, 2_380_800, 2_438_400, 2_515_200, 2_572_800, 2_630_400, 2_707_200,
            2_764_800, 2_841_600, 2_899_200, 2_956_800, 3_014_400, 3_072_000, 3_148_800
        )

        private val SILVER = listOf(
            364_800, 460_800, 556_800, 672_000, 787_200, 902_400, 1_017_600, 1_132_800, 1_248_000,
            1_344_000, 1_459_200, 1_574_400, 1_689_600, 1_804_800, 1_920_000, 2_035_200, 2_150_400,
            2_265_600
        )

        val POLICY_TABLES = listOf(
            0 to SILVER,
            2 to GOLD,
            5 to GOLD.filter { it <= 2_956_800 },
            7 to PRIME
        )

        val STOCK_MIN = listOf(364_800, 499_200, 499_200, 480_000)
        val STOCK_MAX = listOf(2_265_600, 3_148_800, 2_956_800, 3_398_400)
    }
}
