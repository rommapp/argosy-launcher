package com.nendo.argosy.hardware.pad

import android.content.Context
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.RootShell
import java.io.File

data class RunningPadDriver(val pid: Int, val classPath: String)

/**
 * What it takes to leave exactly one pad driver running from the installed APK: the processes to
 * kill, and whether a new driver starts after them.
 */
data class PadDriverPlan(val kill: List<Int>, val start: Boolean) {
    val isSettled: Boolean get() = kill.isEmpty() && !start
}

/**
 * Starts the root pad driver ([PadDriverMain]) as a detached `app_process` from the installed APK,
 * and replaces a driver still running from an APK an update has since replaced.
 */
object PadDriverLauncher {
    const val DRIVER_CLASS = "com.nendo.argosy.hardware.pad.PadDriverMain"

    private const val TAG = "PadDriverLauncher"
    private const val LOG_PATH = "/data/local/tmp/paddriver.log"
    private const val SETTLE_SECONDS = 1
    private val CLASS_PATH = Regex("""-Djava\.class\.path=(\S+)""")

    private val lock = Any()

    /**
     * Leaves exactly one driver running from the installed APK. Does nothing without root.
     * Blocking; call it off the main thread.
     */
    fun ensureRunning(context: Context) {
        if (!RootShell.isAvailable) return
        synchronized(lock) {
            val running = running(context) ?: return
            val apk = context.applicationInfo.sourceDir
            val plan = plan(running, apk)
            if (plan.isSettled) {
                Logger.debug(TAG, "driver ${running.single().pid} already runs from the installed APK")
                return
            }
            apply(context, plan, apk)
        }
    }

    /**
     * Stops every running driver and starts a new one from the installed APK.
     */
    fun restart(context: Context): Boolean = synchronized(lock) {
        val running = running(context) ?: return false
        apply(context, PadDriverPlan(kill = running.map { it.pid }, start = true), context.applicationInfo.sourceDir)
    }

    fun stop(context: Context): Boolean = synchronized(lock) {
        val running = running(context) ?: return false
        if (running.isEmpty()) return true
        RootShell.run(context, killScript(running.map { it.pid }))?.succeeded == true
    }

    /**
     * The drivers running now, or null when the root route could not list them.
     */
    fun running(context: Context): List<RunningPadDriver>? {
        val result = RootShell.run(context, listScript()) ?: run {
            Logger.warn(TAG, "root route did not list running drivers")
            return null
        }
        return parseRunning(result.output)
    }

    internal fun plan(running: List<RunningPadDriver>, installedApk: String): PadDriverPlan {
        val keep = running.firstOrNull { it.classPath == installedApk }
        return PadDriverPlan(
            kill = running.filter { it !== keep }.map { it.pid },
            start = keep == null
        )
    }

    internal fun parseRunning(lines: List<String>): List<RunningPadDriver> = lines.mapNotNull { line ->
        val pid = line.substringBefore(' ').toIntOrNull() ?: return@mapNotNull null
        val args = line.substringAfter(' ', "").trim().split(' ')
        if (args.firstOrNull()?.substringAfterLast('/')?.startsWith("app_process") != true) return@mapNotNull null
        if (DRIVER_CLASS !in args) return@mapNotNull null
        val classPath = args.firstNotNullOfOrNull { CLASS_PATH.matchEntire(it)?.groupValues?.get(1) }
            ?: return@mapNotNull null
        RunningPadDriver(pid, classPath)
    }

    internal fun listScript(): String =
        "for p in /proc/[0-9]*; do\n" +
            "  c=\$(tr '\\000' ' ' < \"\$p/cmdline\" 2>/dev/null)\n" +
            "  case \"\$c\" in *$DRIVER_CLASS*) echo \"\${p#/proc/} \$c\" ;; esac\n" +
            "done\n" +
            "exit 0"

    internal fun killScript(pids: List<Int>): String =
        "kill ${pids.joinToString(" ")}\nsleep $SETTLE_SECONDS"

    internal fun startScript(apk: String, seatsPath: String): String =
        "setsid nohup app_process -Djava.class.path=${RootShell.quote(apk)} /system/bin $DRIVER_CLASS " +
            "${RootShell.quote(seatsPath)} > $LOG_PATH 2>&1 < /dev/null &"

    private fun apply(context: Context, plan: PadDriverPlan, apk: String): Boolean {
        val seats = File(context.filesDir, PadSeats.FILE_NAME).absolutePath
        val script = buildList {
            if (plan.kill.isNotEmpty()) add(killScript(plan.kill))
            if (plan.start) add(startScript(apk, seats))
        }.joinToString("\n")
        val result = RootShell.run(context, script)
        val succeeded = result?.succeeded == true
        if (succeeded) {
            Logger.info(TAG, "driver plan applied | killed=${plan.kill} started=${plan.start} apk=$apk")
        } else {
            Logger.warn(TAG, "driver launch failed | exit=${result?.exitCode} output=${result?.output}")
        }
        return succeeded
    }
}
