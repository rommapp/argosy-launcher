package com.nendo.argosy.hardware.pad

import android.os.FileObserver
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale
import kotlin.math.abs

/**
 * An input device as the kernel and Android both name it: the evdev id and name that sysfs shows
 * under /sys/class/input and that [android.view.InputDevice] reports.
 */
data class PadDeviceIdentity(val vendorId: Int, val productId: Int, val name: String)

/**
 * The input devices the pad driver has muted for the pads it serves now. The driver writes
 * [FILE_NAME] beside the seats file whenever the set changes; every Argosy process reads it back
 * into [devices]. A file written in an earlier boot reads as empty.
 */
object MutedPadDevices {
    const val FILE_NAME = "pad_driver_muted"

    private const val BOOT_HEADER = "boot "
    private const val SAME_BOOT_SLACK_MS = 10_000L
    private const val WATCHED_EVENTS = FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO or
        FileObserver.DELETE or FileObserver.CREATE

    private val _devices = MutableStateFlow<Set<PadDeviceIdentity>>(emptySet())
    val devices: StateFlow<Set<PadDeviceIdentity>> = _devices.asStateFlow()

    @Volatile
    private var observer: FileObserver? = null

    /**
     * Loads [FILE_NAME] from [directory] and follows its changes. Blocking; call it off the main
     * thread, once per process.
     */
    fun watch(directory: File) {
        if (observer != null) return
        val file = File(directory, FILE_NAME)
        @Suppress("DEPRECATION")
        val watcher = object : FileObserver(directory.absolutePath, WATCHED_EVENTS) {
            override fun onEvent(event: Int, path: String?) {
                if (path == FILE_NAME) reload(file)
            }
        }
        watcher.startWatching()
        observer = watcher
        reload(file)
    }

    fun write(file: File, devices: Collection<PadDeviceIdentity>, bootStartedAt: Long) {
        val partial = File(file.parentFile, "${file.name}.part")
        partial.writeText(format(devices, bootStartedAt))
        partial.setReadable(true, false)
        if (!partial.renameTo(file)) partial.delete()
    }

    fun bootStartedAt(): Long = System.currentTimeMillis() - SystemClock.elapsedRealtime()

    /**
     * Whether [device] is one the pad driver muted. The driver's own virtual pads never count,
     * whatever the file says.
     */
    fun isDriverMuted(device: PadDeviceIdentity, muted: Set<PadDeviceIdentity>): Boolean =
        device in muted && NsoPadModel.entries.none { it.virtualIdentity == device }

    internal fun format(devices: Collection<PadDeviceIdentity>, bootStartedAt: Long): String =
        (listOf("$BOOT_HEADER$bootStartedAt") + devices.distinct().map { device ->
            String.format(Locale.ROOT, "%04x %04x %s", device.vendorId, device.productId, device.name)
        }).joinToString("\n")

    internal fun parse(lines: List<String>, bootStartedAt: Long): Set<PadDeviceIdentity> {
        val recordedBoot = lines.firstOrNull()?.removePrefix(BOOT_HEADER)?.trim()?.toLongOrNull()
            ?: return emptySet()
        if (abs(recordedBoot - bootStartedAt) > SAME_BOOT_SLACK_MS) return emptySet()
        return lines.drop(1).mapNotNull { line ->
            val parts = line.split(' ', limit = 3)
            if (parts.size < 3 || parts[2].isEmpty()) return@mapNotNull null
            val vendor = parts[0].toIntOrNull(16) ?: return@mapNotNull null
            val product = parts[1].toIntOrNull(16) ?: return@mapNotNull null
            PadDeviceIdentity(vendor, product, parts[2])
        }.toSet()
    }

    @Synchronized
    private fun reload(file: File) {
        val lines = runCatching { file.readLines() }.getOrDefault(emptyList())
        _devices.value = parse(lines, bootStartedAt())
    }
}
