package com.nendo.argosy.util

import android.content.Intent
import android.util.Log as AndroidLog
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object LogSanitizer {
    private val IPV4_PATTERN = Regex("""\b(\d{1,3}\.){3}\d{1,3}\b""")
    private val IPV6_PATTERN = Regex("""\b([0-9a-fA-F]{1,4}:){2,7}[0-9a-fA-F]{1,4}\b""")
    private val URL_PATTERN = Regex("""https?://[^\s<>"{}|\\^`\[\]]+""", RegexOption.IGNORE_CASE)
    private val EMAIL_PATTERN = Regex("""\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b""")
    private val AUTH_URL_PATTERN = Regex("""(https?://)([^:]+):([^@]+)@""", RegexOption.IGNORE_CASE)
    private val BEARER_PATTERN = Regex("""\b(bearer\s+)[A-Za-z0-9._~+/=-]+""", RegexOption.IGNORE_CASE)
    private val CREDENTIAL_PATTERN = Regex(
        """\b([A-Za-z_-]*(?:token|api[_-]?key|password|passwd|secret|authorization))(["']?\s*[:=]\s*["']?)([^\s"'&,;\[]+)""",
        RegexOption.IGNORE_CASE
    )

    fun sanitize(message: String): String {
        var result = message
        result = AUTH_URL_PATTERN.replace(result) { "${it.groupValues[1]}[user]:[pass]@" }
        result = URL_PATTERN.replace(result) { match ->
            val url = match.value.substringBefore('?').substringBefore('#')
            val pathStart = url.indexOf('/', 8)
            val query = if (match.value.contains('?')) "?[query]" else ""
            if (pathStart > 0) {
                val scheme = if (url.startsWith("https", ignoreCase = true)) "https" else "http"
                "$scheme://[host]${url.substring(pathStart)}$query"
            } else {
                "[url]"
            }
        }
        result = BEARER_PATTERN.replace(result) { "${it.groupValues[1]}[redacted]" }
        result = CREDENTIAL_PATTERN.replace(result) { "${it.groupValues[1]}${it.groupValues[2]}[redacted]" }
        result = IPV4_PATTERN.replace(result, "[ip]")
        result = IPV6_PATTERN.replace(result, "[ipv6]")
        result = EMAIL_PATTERN.replace(result, "[email]")
        return result
    }

    fun sanitizePath(path: String): String {
        val file = File(path)
        return file.name
    }

    fun describeIntent(intent: Intent?): String {
        if (intent == null) return "null"
        return buildString {
            append("Intent(")
            append("action=${intent.action}")
            intent.`package`?.let { append(", pkg=$it") }
            intent.component?.let { append(", component=${it.shortClassName}") }
            intent.data?.let { uri ->
                val scheme = uri.scheme
                val path = uri.path
                if (scheme != null && path != null) {
                    append(", data=$scheme://.../${File(path).name}")
                } else if (scheme != null) {
                    append(", data=$scheme://...")
                } else if (path != null) {
                    append(", data=path:${File(path).name}")
                }
            }
            val extras = intent.extras
            if (extras != null && !extras.isEmpty) {
                val keys = extras.keySet().take(5)
                append(", extras=[${keys.joinToString()}]")
                if (extras.keySet().size > 5) append("+${extras.keySet().size - 5} more")
            }
            append(")")
        }
    }
}

object Logger {
    private val loggerExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        android.util.Log.e("Logger", "Uncaught exception in Logger scope", throwable)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + loggerExceptionHandler)
    private val logChannel = Channel<LogEntry>(Channel.BUFFERED)

    private val writeLock = Any()
    private var fileWriter: FileWriter? = null
    private var currentLogDate: LocalDate? = null
    private var logDirectory: String? = null
    private var versionName: String = "unknown"
    private var fileLogLevel: LogLevel = LogLevel.INFO

    @Volatile
    private var fileLoggingEnabled = false

    private const val CRASH_TAG = "Crash"
    private val timestampFormat = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    init {
        scope.launch {
            logChannel.receiveAsFlow().collect { entry ->
                writeToFile(entry)
            }
        }
    }

    fun configure(
        versionName: String,
        logDirectory: String?,
        enabled: Boolean,
        level: LogLevel
    ) {
        this.versionName = versionName
        this.logDirectory = logDirectory
        this.fileLoggingEnabled = enabled && logDirectory != null
        this.fileLogLevel = level

        if (!fileLoggingEnabled) {
            synchronized(writeLock) { closeCurrentFile() }
        }
    }

    val isVerbose: Boolean
        get() = fileLoggingEnabled && fileLogLevel == LogLevel.DEBUG

    fun debug(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun info(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun warn(tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.WARN, tag, message, throwable)
    fun error(tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.ERROR, tag, message, throwable)

    inline fun verbose(tag: String, message: () -> String) {
        if (isVerbose) {
            debug(tag, message())
        }
    }

    @PublishedApi
    internal fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        when (level) {
            LogLevel.DEBUG -> if (throwable != null) AndroidLog.d(tag, message, throwable) else AndroidLog.d(tag, message)
            LogLevel.INFO -> if (throwable != null) AndroidLog.i(tag, message, throwable) else AndroidLog.i(tag, message)
            LogLevel.WARN -> if (throwable != null) AndroidLog.w(tag, message, throwable) else AndroidLog.w(tag, message)
            LogLevel.ERROR -> if (throwable != null) AndroidLog.e(tag, message, throwable) else AndroidLog.e(tag, message)
        }

        if (fileLoggingEnabled && level.ordinal >= fileLogLevel.ordinal) {
            val entry = LogEntry(
                timestamp = LocalDateTime.now(),
                level = level,
                tag = tag,
                message = message,
                throwable = throwable
            )
            scope.launch { logChannel.send(entry) }
        }
    }

    /**
     * Logs [message] and [throwable] at ERROR and, when file logging is on, writes them to the log
     * file on the calling thread before returning, for a process that is about to die.
     */
    fun recordCrash(message: String, throwable: Throwable) {
        AndroidLog.e(CRASH_TAG, message, throwable)
        if (!fileLoggingEnabled) return
        writeToFile(LogEntry(LocalDateTime.now(), LogLevel.ERROR, CRASH_TAG, message, throwable))
    }

    private fun writeToFile(entry: LogEntry) = synchronized(writeLock) { writeEntry(entry) }

    private fun writeEntry(entry: LogEntry) {
        val dir = logDirectory ?: return

        val today = entry.timestamp.toLocalDate()
        if (today != currentLogDate) {
            rotateLogFile(dir, today)
        }

        val writer = fileWriter ?: return

        val sanitizedMessage = LogSanitizer.sanitize(entry.message)

        val line = buildString {
            append(entry.timestamp.format(timestampFormat))
            append(" ")
            append(entry.level.name.padEnd(5))
            append(" [")
            append(entry.tag)
            append("] ")
            append(sanitizedMessage)
        }

        writer.appendLine(line)

        entry.throwable?.let { t ->
            val sw = StringWriter()
            t.printStackTrace(PrintWriter(sw))
            writer.appendLine(LogSanitizer.sanitize(sw.toString()))
        }

        writer.flush()
    }

    private fun rotateLogFile(directory: String, date: LocalDate) {
        closeCurrentFile()

        val fileName = "$versionName-${date.format(dateFormat)}.log"
        val logFile = File(directory, fileName)

        try {
            logFile.parentFile?.mkdirs()
            fileWriter = FileWriter(logFile, true)
            currentLogDate = date

            fileWriter?.appendLine("=== Argosy $versionName - ${date.format(dateFormat)} ===")
            fileWriter?.flush()
        } catch (e: Exception) {
            AndroidLog.e("Logger", "Failed to create log file", e)
            fileWriter = null
        }
    }

    private fun closeCurrentFile() {
        try {
            fileWriter?.close()
        } catch (_: Exception) { }
        fileWriter = null
        currentLogDate = null
    }
}

enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR;

    companion object {
        fun fromString(value: String?): LogLevel =
            entries.find { it.name == value } ?: INFO
    }
}

private data class LogEntry(
    val timestamp: LocalDateTime,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null
)
