package com.nendo.argosy.data.diagnostics

import android.os.Build
import android.os.Environment
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.nendo.argosy.BuildConfig
import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.model.DisplaySnapshot
import com.nendo.argosy.data.preferences.AppPreferencesRepository
import com.nendo.argosy.data.repository.InputConfigRepository
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.storage.FileInfo
import com.nendo.argosy.data.storage.StoragePathUtils
import com.nendo.argosy.data.update.BuildChannel
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DiagnosticsExportResult {
    data class Success(val path: String) : DiagnosticsExportResult
    data class Error(val message: String) : DiagnosticsExportResult
}

/**
 * Writes one diagnostics zip to `Download/Argosy/` through [FileAccessLayer]: recent log files,
 * device build fields, the displays passed in, connected controllers, installed emulators, the
 * Argosy version and a settings dump. Logs and settings pass through [DiagnosticsRedactor].
 */
@Singleton
class DiagnosticsExporter @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val appPreferences: AppPreferencesRepository,
    private val fileAccessLayer: FileAccessLayer,
    private val emulatorDetector: EmulatorDetector,
    private val inputConfigRepository: InputConfigRepository
) {

    suspend fun export(displays: List<DisplaySnapshot>): DiagnosticsExportResult = withContext(Dispatchers.IO) {
        try {
            val settings = dataStore.data.first().asMap().mapKeys { it.key.name }
            val secrets = DiagnosticsRedactor.secretsIn(settings)
            val logDirectory = appPreferences.preferences.first().fileLoggingPath
            val directory = "${StoragePathUtils.primaryExternalRoot}/${Environment.DIRECTORY_DOWNLOADS}/$FOLDER_NAME"
            if (!fileAccessLayer.isDirectory(directory) && !fileAccessLayer.mkdirs(directory)) {
                return@withContext DiagnosticsExportResult.Error("Could not create $directory")
            }
            val target = "$directory/$FILE_PREFIX${LocalDateTime.now().format(TIMESTAMP)}$FILE_SUFFIX"
            val output = fileAccessLayer.getOutputStream(target)
                ?: return@withContext DiagnosticsExportResult.Error("Could not write $target")
            ZipOutputStream(output).use { zip ->
                zip.writeText(ENTRY_VERSION, versionText())
                zip.writeText(ENTRY_DEVICE, deviceText())
                zip.writeText(ENTRY_DISPLAYS, displaysText(displays))
                zip.writeText(ENTRY_CONTROLLERS, controllersText(secrets))
                zip.writeText(ENTRY_EMULATORS, emulatorsText())
                zip.writeText(ENTRY_SETTINGS, settingsText(settings, secrets))
                logFiles(logDirectory).forEach { log -> zip.writeLog(log, secrets) }
            }
            DiagnosticsExportResult.Success(target)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.error(TAG, "Diagnostics export failed", e)
            DiagnosticsExportResult.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun versionText(): String = lines(
        "version_name" to BuildConfig.VERSION_NAME,
        "version_code" to BuildConfig.VERSION_CODE,
        "build_type" to BuildConfig.BUILD_TYPE,
        "prerelease" to BuildChannel.isPrereleaseBuild
    )

    private fun deviceText(): String = lines(
        "manufacturer" to Build.MANUFACTURER,
        "brand" to Build.BRAND,
        "model" to Build.MODEL,
        "device" to Build.DEVICE,
        "product" to Build.PRODUCT,
        "hardware" to Build.HARDWARE,
        "board" to Build.BOARD,
        "soc" to socText(),
        "android_release" to Build.VERSION.RELEASE,
        "sdk" to Build.VERSION.SDK_INT,
        "security_patch" to Build.VERSION.SECURITY_PATCH,
        "build_id" to Build.DISPLAY,
        "abis" to Build.SUPPORTED_ABIS.joinToString(",")
    )

    private fun socText(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else ""

    private fun displaysText(displays: List<DisplaySnapshot>): String = displays.joinToString(BLOCK_SEPARATOR) { d ->
        lines(
            "display_id" to d.displayId,
            "physical_id" to d.physicalId,
            "name" to d.name,
            "size_px" to "${d.widthPx}x${d.heightPx}",
            "size_dp" to "${d.widthDp}x${d.heightDp}",
            "density_dpi" to d.densityDpi,
            "refresh_hz" to d.refreshRateHz,
            "kind" to d.kind.name,
            "role" to d.role?.name
        )
    }

    private suspend fun controllersText(secrets: Set<String>): String =
        inputConfigRepository.getConnectedControllers().joinToString(BLOCK_SEPARATOR) { pad ->
            lines(
                "device_id" to pad.deviceId,
                "name" to DiagnosticsRedactor.redactText(pad.name, secrets),
                "vendor_id" to hex(pad.vendorId),
                "product_id" to hex(pad.productId),
                "layout" to pad.detectedLayout?.name
            )
        }.let { text ->
            val seats = inputConfigRepository.getControllerOrder()
                .joinToString(LINE_SEPARATOR) {
                    "seat_${it.port + 1} = ${DiagnosticsRedactor.redactText(it.controllerName, secrets)}"
                }
            listOf(text, seats).filter { it.isNotBlank() }.joinToString(BLOCK_SEPARATOR)
        }

    private suspend fun emulatorsText(): String =
        emulatorDetector.detectEmulators().joinToString(LINE_SEPARATOR) { emulator ->
            "${emulator.def.packageName} = ${emulator.versionName.orEmpty()} (${emulator.versionCode})"
        }

    private fun settingsText(settings: Map<String, Any?>, secrets: Set<String>): String =
        settings.entries
            .sortedBy { it.key }
            .joinToString(LINE_SEPARATOR) { (name, value) ->
                "$name = ${DiagnosticsRedactor.redactSetting(name, value, secrets)}"
            }

    private fun logFiles(directory: String?): List<FileInfo> {
        if (directory.isNullOrBlank()) return emptyList()
        return fileAccessLayer.listFiles(directory).orEmpty()
            .filter { it.isFile && it.extension == LOG_EXTENSION }
            .sortedByDescending { it.lastModified }
            .take(LOG_FILE_LIMIT)
    }

    private fun ZipOutputStream.writeLog(log: FileInfo, secrets: Set<String>) {
        val input = fileAccessLayer.getInputStream(log.path) ?: return
        putNextEntry(ZipEntry("$LOG_FOLDER/${log.name}"))
        val writer = bufferedWriterOf(this)
        input.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                writer.write(DiagnosticsRedactor.redactText(line, secrets))
                writer.write(LINE_SEPARATOR)
            }
        }
        writer.flush()
        closeEntry()
    }

    private fun ZipOutputStream.writeText(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun bufferedWriterOf(stream: OutputStream) = stream.writer(Charsets.UTF_8).buffered()

    private fun lines(vararg fields: Pair<String, Any?>): String =
        fields.joinToString(LINE_SEPARATOR) { (name, value) -> "$name = ${value ?: ""}" }

    private fun hex(value: Int): String = String.format(java.util.Locale.ROOT, "0x%04X", value)

    private companion object {
        const val TAG = "DiagnosticsExporter"
        const val FOLDER_NAME = "Argosy"
        const val FILE_PREFIX = "argosy-diagnostics-"
        const val FILE_SUFFIX = ".zip"
        const val LOG_FOLDER = "logs"
        const val LOG_EXTENSION = "log"
        const val LOG_FILE_LIMIT = 10
        const val LINE_SEPARATOR = "\n"
        const val BLOCK_SEPARATOR = "\n\n"
        const val ENTRY_VERSION = "argosy.txt"
        const val ENTRY_DEVICE = "device.txt"
        const val ENTRY_DISPLAYS = "displays.txt"
        const val ENTRY_CONTROLLERS = "controllers.txt"
        const val ENTRY_EMULATORS = "emulators.txt"
        const val ENTRY_SETTINGS = "settings.txt"
        val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}
