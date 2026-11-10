package com.nendo.argosy.util

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Root commands through a root manager's `su`, such as Magisk's. [requestGrant] and [execute] can
 * raise the manager's approval prompt and block until it is answered, so both run off the main
 * thread and only after the user asks for a root-backed control. [isInstalled] and [isGranted]
 * never start a process.
 */
interface RootCommandRunner {
    val isInstalled: Boolean
    val isGranted: Boolean

    fun requestGrant(): Boolean

    /**
     * The command's output. Fails when the route is refused, times out, or the command exits
     * non-zero.
     */
    fun execute(command: String): Result<String>

    /**
     * Runs a multi-line [script] and reports its exit code and every output line. Null when the
     * route refuses it or it outlives the route's limit.
     */
    fun runScript(script: String): RootShell.Result?
}

/**
 * The `su` route behind performance modes and, once the user enables it, [RootShell] on rooted
 * devices without a vendor root daemon.
 */
object SuExecutor : RootCommandRunner {

    private const val SU = "su"
    private const val TIMEOUT_SECONDS = 30L
    private const val SCRIPT_TIMEOUT_SECONDS = 600L
    private const val ROOT_UID_MARKER = "uid=0("
    private val knownDirs = listOf("/system/bin", "/system_ext/bin", "/system/xbin", "/sbin", "/debug_ramdisk")

    @Volatile
    private var granted = false

    private val binary: File? by lazy {
        val pathDirs = System.getenv("PATH").orEmpty().split(':').filter { it.isNotEmpty() }
        (pathDirs + knownDirs).distinct().map { File(it, SU) }.firstOrNull { it.exists() }
    }

    override val isInstalled: Boolean get() = binary != null

    override val isGranted: Boolean get() = granted

    override fun requestGrant(): Boolean {
        val isRoot = execute("id").getOrNull()?.contains(ROOT_UID_MARKER) == true
        granted = isRoot
        return isRoot
    }

    override fun execute(command: String): Result<String> = runCatching {
        val su = binary ?: error("su is not installed")
        val process = ProcessBuilder(su.path, "-c", command).redirectErrorStream(true).start()
        process.outputStream.close()
        if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            error("su timed out")
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        check(process.exitValue() == 0) { "su exited ${process.exitValue()}: ${output.trim()}" }
        output
    }

    override fun runScript(script: String): RootShell.Result? {
        val su = binary ?: return null
        return ShellProcess.run(listOf(su.path), script, SCRIPT_TIMEOUT_SECONDS)
    }
}
