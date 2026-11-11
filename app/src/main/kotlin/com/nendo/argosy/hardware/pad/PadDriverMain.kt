package com.nendo.argosy.hardware.pad

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Entry point of the pad driver, run as root through `app_process` with Argosy's APK on the
 * class path so it outlives the launcher process. It finds Nintendo Switch Online pads among the
 * kernel's hidraw nodes, wakes each one with the Nintendo USB handshake, mutes the generic input
 * device the kernel made from it, and republishes its input as a clean uhid gamepad. The muted
 * devices of every pad it serves are listed in [MutedPadDevices.FILE_NAME] beside the seats file.
 */
object PadDriverMain {

    private const val TAG = "PadDriver"
    private const val SCAN_INTERVAL_MS = 250L
    private const val UNPUBLISH_GRACE_MS = 1_000L
    private const val HANDSHAKE_STEP_MS = 120L
    private const val HIDRAW_CLASS = "/sys/class/hidraw"
    private const val INPUT_CLASS = "/sys/class/input"
    private const val UHID_NODE = "/dev/uhid"
    private const val OOM_SCORE_ADJ = "/proc/self/oom_score_adj"
    private const val NEVER_KILL = "-1000"
    private const val USB_COMMAND = 0x80
    private const val SUBCOMMAND_REPORT = 0x01
    private const val SET_INPUT_MODE = 0x03
    private const val BUS_USB = "0003"
    private const val BUS_BLUETOOTH = "0005"
    private val HANDSHAKE = intArrayOf(0x02, 0x03, 0x02, 0x04)
    private const val SET_PLAYER_LIGHTS = 0x30
    private const val LIGHTS_CHECK_MS = 1_000L
    private val PLAYER_LIGHTS = intArrayOf(0x1, 0x3, 0x7, 0xF, 0x9, 0xA, 0xB, 0x6)
    private val DEVICE_HEADER = Regex("""^\s+\d+: .*""")
    private val CONTROLLER_NUMBER = Regex("""ControllerNumber: (\d+)""")
    private val NEUTRAL_RUMBLE = intArrayOf(0x00, 0x01, 0x40, 0x40, 0x00, 0x01, 0x40, 0x40)

    private var seatsFile: File? = null
    private var mutedFile: File? = null
    private val HID_ID = Regex("""HID_ID=(0003|0005):0000([0-9A-F]{4}):0000([0-9A-F]{4})""")
    private val HID_NAME = Regex("""HID_NAME=(.*)""")
    private val HID_UNIQ = Regex("""HID_UNIQ=(.*)""")

    private val served = ConcurrentHashMap<String, Thread>()
    private val mutedByPad = ConcurrentHashMap<String, List<PadDeviceIdentity>>()

    @JvmStatic
    fun main(args: Array<String>) {
        seatsFile = args.getOrNull(0)?.let(::File)
        mutedFile = seatsFile?.parentFile?.let { File(it, MutedPadDevices.FILE_NAME) }
        publishMuted()
        runCatching { File(OOM_SCORE_ADJ).writeText(NEVER_KILL) }
            .onFailure { Log.w(TAG, "oom_score_adj not set: ${it.message}") }
        Log.i(TAG, "driver up, uid=${android.os.Process.myUid()} pid=${android.os.Process.myPid()}")
        while (true) {
            scan()
            Thread.sleep(SCAN_INTERVAL_MS)
        }
    }

    private fun scan() {
        File(HIDRAW_CLASS).listFiles()?.forEach { node ->
            val name = node.name
            if (served[name]?.isAlive == true) return@forEach
            val pad = describe(node) ?: return@forEach
            served[name] = Thread({ serve(pad) }, "pad-$name").apply {
                isDaemon = true
                start()
            }
        }
    }

    private data class Pad(
        val hidraw: String,
        val model: NsoPadModel,
        val bluetooth: Boolean,
        val hidName: String,
        val uniq: String,
        val inputDir: File
    )

    private fun describe(node: File): Pad? {
        val uevent = runCatching { File(node, "device/uevent").readText() }.getOrNull() ?: return null
        if ("HID_PHYS=${UhidGamepad.PHYS}" in uevent) return null
        val ids = HID_ID.find(uevent) ?: return null
        if (ids.groupValues[2].toInt(16) != NsoPadModel.VENDOR_ID) return null
        val model = NsoPadModel.forProduct(ids.groupValues[3].toInt(16)) ?: return null
        return Pad(
            hidraw = "/dev/${node.name}",
            model = model,
            bluetooth = ids.groupValues[1] == BUS_BLUETOOTH,
            hidName = HID_NAME.find(uevent)?.groupValues?.get(1).orEmpty(),
            uniq = HID_UNIQ.find(uevent)?.groupValues?.get(1).orEmpty(),
            inputDir = File(node, "device/input")
        )
    }

    private fun serve(pad: Pad) {
        Log.i(TAG, "serving ${pad.model} on ${pad.hidraw}")
        val muted = mute(pad)
        try {
            FileInputStream(pad.hidraw).use { input ->
                FileOutputStream(pad.hidraw).use { output ->
                    if (pad.bluetooth) selectFullReports(output) else handshake(output)
                    UhidGamepad(FileOutputStream(UHID_NODE)).use { gamepad ->
                        val uniq = "argosy-${pad.uniq}"
                        gamepad.create(
                            name = pad.model.virtualName,
                            uniq = uniq,
                            vendorId = NsoPadModel.VENDOR_ID,
                            productId = pad.model.productId
                        )
                        mutedByPad[pad.hidraw] = muted.mapNotNull { flag -> flag.parentFile?.let(::identityOf) }
                        publishMuted()
                        val pumping = AtomicBoolean(true)
                        val lights = Thread({ lightPlayers(output, uniq, pumping::get) }, "lights-${pad.hidraw}").apply {
                            isDaemon = true
                            start()
                        }
                        try {
                            pump(pad.model, input, gamepad)
                        } finally {
                            pumping.set(false)
                            lights.interrupt()
                        }
                    }
                }
            }
        } catch (e: IOException) {
            Log.i(TAG, "${pad.hidraw} closed: ${e.message}")
        } finally {
            unmute(muted)
            if (mutedByPad.containsKey(pad.hidraw)) {
                runCatching { Thread.sleep(UNPUBLISH_GRACE_MS) }
                mutedByPad.remove(pad.hidraw)
                publishMuted()
            }
            served.remove(pad.hidraw.substringAfterLast('/'))
        }
    }

    @Synchronized
    private fun publishMuted() {
        val file = mutedFile ?: return
        runCatching { MutedPadDevices.write(file, mutedByPad.values.flatten(), MutedPadDevices.bootStartedAt()) }
            .onFailure { Log.w(TAG, "muted devices not published: ${it.message}") }
    }

    private fun identityOf(inputDir: File): PadDeviceIdentity? = runCatching {
        PadDeviceIdentity(
            vendorId = File(inputDir, "id/vendor").readText().trim().toInt(16),
            productId = File(inputDir, "id/product").readText().trim().toInt(16),
            name = File(inputDir, "name").readText().trim()
        )
    }.getOrNull()

    private fun handshake(output: FileOutputStream) {
        HANDSHAKE.forEach { step ->
            val report = ByteArray(NsoPadModel.REPORT_SIZE)
            report[0] = USB_COMMAND.toByte()
            report[1] = step.toByte()
            output.write(report)
            Thread.sleep(HANDSHAKE_STEP_MS)
        }
    }

    private fun selectFullReports(output: FileOutputStream) {
        subcommand(output, counter = 0, id = SET_INPUT_MODE, argument = NsoPadModel.FULL_INPUT_REPORT)
        Thread.sleep(HANDSHAKE_STEP_MS)
    }

    private fun subcommand(output: FileOutputStream, counter: Int, id: Int, argument: Int) {
        val report = ByteArray(NsoPadModel.REPORT_SIZE)
        report[0] = SUBCOMMAND_REPORT.toByte()
        report[1] = (counter and 0x0F).toByte()
        NEUTRAL_RUMBLE.forEachIndexed { index, value -> report[2 + index] = value.toByte() }
        report[2 + NEUTRAL_RUMBLE.size] = id.toByte()
        report[3 + NEUTRAL_RUMBLE.size] = argument.toByte()
        output.write(report)
    }

    private fun lightPlayers(output: FileOutputStream, uniq: String, running: () -> Boolean) {
        var counter = 1
        var controllerNumber = 0
        var lit = 0
        try {
            while (running()) {
                if (controllerNumber == 0) controllerNumber = controllerNumberOf(uniq)
                val live = seatsFile?.let(PadSeats::read).orEmpty()
                val saved = seatsFile?.let { PadSeats.read(File(it.parentFile, PadSeats.SAVED_FILE_NAME)) }.orEmpty()
                val player = PadSeats.playerFor(controllerNumber, live, saved)
                if (player > 0 && player != lit) {
                    val mask = PLAYER_LIGHTS.getOrElse(player - 1) { PLAYER_LIGHTS.last() }
                    runCatching { subcommand(output, counter++, SET_PLAYER_LIGHTS, mask) }
                        .onSuccess {
                            lit = player
                            Log.i(TAG, "$uniq lights player $player (controller $controllerNumber)")
                        }
                        .onFailure {
                            Log.w(TAG, "$uniq lights not set: ${it.message}")
                            return
                        }
                }
                Thread.sleep(LIGHTS_CHECK_MS)
            }
        } catch (_: InterruptedException) {
            return
        }
    }

    private fun controllerNumberOf(uniq: String): Int {
        val event = File(INPUT_CLASS).listFiles().orEmpty().firstOrNull { node ->
            runCatching { File(node, "uniq").readText().trim() == uniq && File(node, "phys").readText().trim() == UhidGamepad.PHYS }
                .getOrDefault(false)
        }?.listFiles()?.firstOrNull { it.name.startsWith("event") }?.name ?: return 0
        val dump = runCatching {
            ProcessBuilder("dumpsys", "input").redirectErrorStream(true).start().inputStream.bufferedReader().readLines()
        }.getOrNull() ?: return 0
        val pathLine = dump.indexOfFirst { it.trim() == "Path: /dev/input/$event" }
        if (pathLine < 0) return 0
        return dump.drop(pathLine + 1)
            .takeWhile { !DEVICE_HEADER.matches(it) }
            .firstNotNullOfOrNull { CONTROLLER_NUMBER.find(it)?.groupValues?.get(1)?.toIntOrNull() }
            ?: 0
    }

    private fun pump(model: NsoPadModel, input: FileInputStream, gamepad: UhidGamepad) {
        val report = ByteArray(NsoPadModel.REPORT_SIZE)
        var center: StickCenter? = null
        var last: VirtualPadState? = null
        while (true) {
            val read = input.read(report)
            if (read < 0) throw IOException("end of stream")
            if (read < 12 || (report[0].toInt() and 0xFF) != NsoPadModel.FULL_INPUT_REPORT) continue
            if (center == null) center = model.centerOf(report)
            val state = model.decode(report, center)
            if (state != last) {
                gamepad.send(state)
                last = state
            }
        }
    }

    private fun mute(pad: Pad): List<File> {
        val owned = pad.inputDir.listFiles().orEmpty().map { File(it, "inhibited") }
        val named = File(INPUT_CLASS).listFiles().orEmpty()
            .filter { runCatching { File(it, "name").readText().trim() }.getOrNull() == pad.hidName }
            .filter { runCatching { File(it, "phys").readText() }.getOrNull()?.contains(UhidGamepad.PHYS) != true }
            .map { File(it, "inhibited") }
        return (owned + named).distinctBy { it.canonicalPath }.filter { flag ->
            runCatching { flag.writeText("1") }.isSuccess
        }.also { Log.i(TAG, "muted ${it.size} generic input nodes for ${pad.hidraw}") }
    }

    private fun unmute(flags: List<File>) {
        flags.forEach { runCatching { it.writeText("0") } }
    }
}
