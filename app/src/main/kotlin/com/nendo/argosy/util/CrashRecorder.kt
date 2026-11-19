package com.nendo.argosy.util

import android.app.Application
import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Records every uncaught exception in this process before the platform handler kills it: one file
 * per crash under `files/crashes/` (the newest [KEPT_CRASHES] are kept), plus an ERROR entry in
 * the file log when file logging is on. The previous default handler always runs afterwards.
 */
object CrashRecorder {
    private const val DIRECTORY = "crashes"
    private const val EXTENSION = "txt"
    private const val KEPT_CRASHES = 5
    private val FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
    private val HEADER_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    fun install(context: Context, versionName: String) {
        val directory = directoryOf(context)
        val process = processName()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { record(directory, versionName, process, thread.name, throwable, LocalDateTime.now()) }
            runCatching { Logger.recordCrash("Uncaught exception on ${thread.name} in $process", throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /**
     * The recorded crash files, newest first.
     */
    fun crashFiles(context: Context): List<File> = filesIn(directoryOf(context))

    internal fun record(
        directory: File,
        versionName: String,
        process: String,
        threadName: String,
        throwable: Throwable,
        now: LocalDateTime
    ): File {
        directory.mkdirs()
        val file = File(directory, "$versionName-${now.format(FILE_STAMP)}-${fileSafe(process)}.$EXTENSION")
        file.writeText(
            buildString {
                appendLine("=== Argosy $versionName crash ===")
                appendLine("time = ${now.format(HEADER_STAMP)}")
                appendLine("process = $process")
                appendLine("thread = $threadName")
                appendLine("device = ${Build.MANUFACTURER} ${Build.MODEL}, sdk ${Build.VERSION.SDK_INT}")
                appendLine()
                append(LogSanitizer.sanitize(stackTraceOf(throwable)))
            }
        )
        filesIn(directory).drop(KEPT_CRASHES).forEach { it.delete() }
        return file
    }

    internal fun filesIn(directory: File): List<File> =
        directory.listFiles { file -> file.isFile && file.extension == EXTENSION }
            .orEmpty()
            .sortedByDescending { it.lastModified() }

    private fun directoryOf(context: Context) = File(context.filesDir, DIRECTORY)

    private fun processName(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Application.getProcessName() else "main"

    private fun fileSafe(name: String) = name.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun stackTraceOf(throwable: Throwable): String =
        StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
}
