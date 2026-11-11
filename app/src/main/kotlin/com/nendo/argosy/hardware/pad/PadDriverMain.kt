package com.nendo.argosy.hardware.pad

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Entry point of the pad driver, run as root through `app_process` with Argosy's APK on the
 * class path so it outlives the launcher process. It finds Nintendo Switch Online pads among the
 * kernel's hidraw nodes, wakes each one with the Nintendo USB handshake, mutes the generic input
 * device the kernel made from it, and republishes its input as a clean uhid gamepad.
 */
object PadDriverMain {

    private const val TAG = "PadDriver"
    private const val SCAN_INTERVAL_MS = 1_000L
    private const val HANDSHAKE_STEP_MS = 120L
    private const val HIDRAW_CLASS = "/sys/class/hidraw"
    private const val INPUT_CLASS = "/sys/class/input"
    private const val UHID_NODE = "/dev/uhid"
    private const val OOM_SCORE_ADJ = "/proc/self/oom_score_adj"
    private const val NEVER_KILL = "-1000"
    private const val USB_COMMAND = 0x80
    private val HANDSHAKE = intArrayOf(0x02, 0x03, 0x02, 0x04)
    private val HID_ID = Regex("""HID_ID=0003:0000([0-9A-F]{4}):0000([0-9A-F]{4})""")
    private val HID_NAME = Regex("""HID_NAME=(.*)""")
    private val HID_UNIQ = Regex("""HID_UNIQ=(.*)""")

    private val served = ConcurrentHashMap<String, Thread>()

    @JvmStatic
    fun main(args: Array<String>) {
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
        val hidName: String,
        val uniq: String,
        val inputDir: File
    )

    private fun describe(node: File): Pad? {
        val uevent = runCatching { File(node, "device/uevent").readText() }.getOrNull() ?: return null
        if ("HID_PHYS=${UhidGamepad.PHYS}" in uevent) return null
        val ids = HID_ID.find(uevent) ?: return null
        if (ids.groupValues[1].toInt(16) != NsoPadModel.VENDOR_ID) return null
        val model = NsoPadModel.forProduct(ids.groupValues[2].toInt(16)) ?: return null
        return Pad(
            hidraw = "/dev/${node.name}",
            model = model,
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
                    handshake(output)
                    UhidGamepad(FileOutputStream(UHID_NODE)).use { gamepad ->
                        gamepad.create(
                            name = "Nintendo ${pad.model.displayName}",
                            uniq = "argosy-${pad.uniq}",
                            vendorId = NsoPadModel.VENDOR_ID,
                            productId = pad.model.productId
                        )
                        pump(pad.model, input, gamepad)
                    }
                }
            }
        } catch (e: IOException) {
            Log.i(TAG, "${pad.hidraw} closed: ${e.message}")
        } finally {
            unmute(muted)
            served.remove(pad.hidraw.substringAfterLast('/'))
        }
    }

    private fun handshake(output: FileOutputStream) {
        HANDSHAKE.forEach { step ->
            val report = ByteArray(NsoPadModel.REPORT_SIZE)
            report[0] = USB_COMMAND.toByte()
            report[1] = step.toByte()
            output.write(report)
            Thread.sleep(HANDSHAKE_STEP_MS)
        }
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
