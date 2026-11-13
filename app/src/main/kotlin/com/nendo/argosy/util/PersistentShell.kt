package com.nendo.argosy.util

import java.io.BufferedWriter
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

internal class PersistentShell(private val command: List<String>) {

    private val lock = ReentrantLock()
    private var process: Process? = null
    private var stdin: BufferedWriter? = null
    private var lines: LinkedBlockingQueue<String>? = null

    val isBusy: Boolean get() = lock.isLocked

    fun run(script: String, timeoutSeconds: Long): RootShell.Result? = lock.withLock {
        val queue = ensureStarted() ?: return null
        val token = UUID.randomUUID().toString().replace("-", "")
        val delimiter = "ARGOSY_SCRIPT_$token"
        val marker = "ARGOSY_DONE_$token"
        try {
            stdin!!.apply {
                write("sh <<'$delimiter'\n")
                write(script)
                if (!script.endsWith("\n")) write("\n")
                write("$delimiter\n")
                write("echo \"$marker \$?\"\n")
                flush()
            }
        } catch (e: Exception) {
            stop()
            return null
        }
        awaitResult(queue, marker, System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds))
            ?: run {
                stop()
                null
            }
    }

    private fun awaitResult(queue: LinkedBlockingQueue<String>, marker: String, deadline: Long): RootShell.Result? {
        val output = mutableListOf<String>()
        while (true) {
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0) return null
            val line = queue.poll(remaining, TimeUnit.NANOSECONDS) ?: return null
            if (line === END_OF_STREAM) return null
            val at = line.indexOf(marker)
            if (at < 0) {
                output += line
                continue
            }
            if (at > 0) output += line.substring(0, at)
            return RootShell.Result(line.substring(at + marker.length).trim().toIntOrNull(), output)
        }
    }

    fun stop() = lock.withLock {
        process?.destroyForcibly()
        process = null
        stdin = null
        lines = null
    }

    private fun ensureStarted(): LinkedBlockingQueue<String>? {
        process?.takeIf { it.isAlive }?.let { return lines }
        stop()
        val started = runCatching { ProcessBuilder(command).redirectErrorStream(true).start() }.getOrNull()
            ?: return null
        val queue = LinkedBlockingQueue<String>()
        thread(isDaemon = true, name = "persistent-shell") {
            runCatching { started.inputStream.bufferedReader().forEachLine(queue::put) }
            queue.put(END_OF_STREAM)
        }
        process = started
        stdin = started.outputStream.bufferedWriter()
        lines = queue
        return queue
    }

    private companion object {
        val END_OF_STREAM = String(charArrayOf('\u0000'))
    }
}
