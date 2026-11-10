package com.nendo.argosy.util

import android.content.Context
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Reaches other apps' Android/data folders without root: runs scripts there and opens files for
 * Argosy to read or write through the returned descriptor.
 */
interface AppDataShell {
    fun runScript(script: String): RootShell.Result?
    fun open(path: String, mode: Int): ParcelFileDescriptor?
}

/**
 * Runs shell scripts as root through the AYN/Retroid daemon [PServerExecutor], Ayaneo's
 * [XsuExecutor], or a root route the user enabled in Argosy ([adopt]). The vendor routes report
 * neither a script's exit code nor more than one output line, so their scripts are written to a
 * file, run through a wrapper that captures the full output, and reported as an exit code plus
 * output lines.
 */
object RootShell {

    data class Result(val exitCode: Int?, val output: List<String>) {
        val succeeded: Boolean get() = exitCode == 0
    }

    private const val WORK_DIR = "root-shell"
    private const val SCRIPT_NAME = "root-op.sh"
    private const val WRAPPER_NAME = "root-run.sh"
    private const val OUTPUT_NAME = "run-output.txt"
    private const val TAG = "RootShell"

    private val lock = Any()

    @Volatile
    var enabledRoot: RootCommandRunner? = null
        private set

    @Volatile
    var appDataShell: AppDataShell? = null
        private set

    val hasVendorRoute: Boolean
        get() = PServerExecutor.isAvailable || XsuExecutor.isAvailable

    val isAvailable: Boolean
        get() = hasVendorRoute || enabledRoot != null

    /**
     * Installs the routes the user enabled: [root] backs every root caller, [appData] only the
     * Android/data mirror. Null clears a route.
     */
    fun adopt(root: RootCommandRunner?, appData: AppDataShell?) {
        enabledRoot = root
        appDataShell = appData
    }

    fun execute(command: String): kotlin.Result<String?> = when {
        PServerExecutor.isAvailable -> PServerExecutor.execute(command)
        XsuExecutor.isAvailable -> XsuExecutor.execute(command)
        else -> enabledRoot?.execute(command)
            ?: kotlin.Result.failure(IllegalStateException("no root route"))
    }

    fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    fun run(context: Context, body: String): Result? {
        if (hasVendorRoute) return runThroughVendor(context, body)
        return enabledRoot?.runScript(body)
    }

    private fun runThroughVendor(context: Context, body: String): Result? = synchronized(lock) {
        val dir = File(context.cacheDir, WORK_DIR)
        val script = File(dir, SCRIPT_NAME)
        val output = File(dir, OUTPUT_NAME)
        val wrapper = File(dir, WRAPPER_NAME)
        try {
            dir.mkdirs()
            dir.setExecutable(true, false)
            script.writeText(body + "\n")
            output.delete()
            wrapper.apply {
                writeText(
                    "sh ${script.absolutePath} > ${output.absolutePath} 2>&1\n" +
                        "rc=\$?\nchmod 644 ${output.absolutePath}\necho \$rc\n"
                )
            }
            listOf(script, wrapper).forEach { it.setReadable(true, false) }
            val exit = execute("sh ${wrapper.absolutePath}").getOrElse { error ->
                Logger.warn(TAG, "root route refused the call: ${error.message}")
                return null
            }?.trim()?.toIntOrNull()
            val lines = if (output.canRead()) output.readLines() else emptyList()
            Result(exit, lines)
        } catch (e: Exception) {
            Logger.warn(TAG, "root shell failed: ${e.message}")
            null
        } finally {
            script.delete()
            wrapper.delete()
            output.delete()
        }
    }
}
