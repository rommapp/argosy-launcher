package com.nendo.argosy.util

import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

internal object ShellProcess {

    private const val READER_JOIN_MS = 2_000L

    fun run(command: List<String>, script: String, timeoutSeconds: Long): RootShell.Result? = runCatching {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val lines = mutableListOf<String>()
        val reader = thread(isDaemon = true) {
            process.inputStream.bufferedReader().useLines { output -> output.forEach { synchronized(lines) { lines += it } } }
        }
        process.outputStream.bufferedWriter().use { it.write(script + "\n") }
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return@runCatching null
        }
        reader.join(READER_JOIN_MS)
        RootShell.Result(process.exitValue(), synchronized(lines) { lines.toList() })
    }.getOrNull()
}
