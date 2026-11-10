package com.nendo.argosy.data.emulator.background

data class RunningTask(
    val taskId: Int,
    val packageName: String,
    val displayId: Int,
    val visible: Boolean
)

data class ProcessStat(val pid: Int, val rssKb: Long, val elapsedMs: Long)

/**
 * Reads the output of `am stack list` and `ps -A -o PID,RSS,ETIME,NAME`. Only tasks of the
 * standard activity type are kept, so home screens, recents and system overlays never appear.
 */
object RunningTaskParser {

    private val ROOT_TASK = Regex("""^\s*(?:RootTask|Stack) id=\d+.*?displayId=(\d+)""")
    private val ACTIVITY_TYPE = Regex("""mActivityType=(\w+)""")
    private val TASK = Regex("""^\s*taskId=(\d+): ([\w.]+)/\S+.*?visible=(true|false)""")
    private val PROCESS = Regex(
        """^\s*(\d+)\s+(\d+)\s+((?:\d+-)?(?:\d+:)?\d+:\d+)\s+([a-zA-Z]\w*(?:\.\w+)+(?::[\w.]+)?)\s*$"""
    )

    fun parseTasks(lines: List<String>): List<RunningTask> {
        val tasks = mutableListOf<RunningTask>()
        var displayId: Int? = null
        var standard = false
        for (line in lines) {
            val root = ROOT_TASK.find(line)
            if (root != null) {
                displayId = root.groupValues[1].toInt()
                standard = false
                continue
            }
            ACTIVITY_TYPE.find(line)?.let { standard = it.groupValues[1] == "standard" }
            val task = TASK.find(line) ?: continue
            val display = displayId ?: continue
            if (!standard) continue
            tasks += RunningTask(
                taskId = task.groupValues[1].toInt(),
                packageName = task.groupValues[2],
                displayId = display,
                visible = task.groupValues[3] == "true"
            )
        }
        return tasks
    }

    /**
     * One entry per package: the main process's pid and running time, and the resident memory of
     * the main process plus every `package:name` process it started.
     */
    fun parseProcesses(lines: List<String>): Map<String, ProcessStat> {
        val main = mutableMapOf<String, Pair<Int, Long>>()
        val rss = mutableMapOf<String, Long>()
        for (line in lines) {
            val match = PROCESS.find(line) ?: continue
            val name = match.groupValues[4]
            val packageName = name.substringBefore(':')
            rss[packageName] = (rss[packageName] ?: 0L) + match.groupValues[2].toLong()
            if (name == packageName) {
                main[packageName] = match.groupValues[1].toInt() to parseElapsedMs(match.groupValues[3])
            }
        }
        return main.mapValues { (packageName, process) ->
            ProcessStat(pid = process.first, rssKb = rss[packageName] ?: 0L, elapsedMs = process.second)
        }
    }

    /**
     * `ps` ETIME, written `[[dd-]hh:]mm:ss`, in milliseconds.
     */
    fun parseElapsedMs(etime: String): Long {
        val days = etime.substringBefore('-', "0").toLong()
        val clock = etime.substringAfter('-').split(':').map { it.toLong() }
        val seconds = clock.fold(0L) { total, part -> total * 60 + part }
        return (days * SECONDS_PER_DAY + seconds) * 1000
    }

    private const val SECONDS_PER_DAY = 86_400L
}
