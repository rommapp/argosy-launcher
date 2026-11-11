package com.nendo.argosy.hardware.pad

import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Buttons of the virtual gamepad in HID button-usage order. The kernel turns usage n into
 * BTN_GAMEPAD + n - 1, which Android's Generic.kl maps to the key named here.
 */
enum class VirtualButton(usage: Int) {
    A(1), B(2), X(4), Y(5), L1(7), R1(8), L2(9), R2(10), SELECT(11), START(12), MODE(13);

    val bit: Int = 1 shl (usage - 1)
}

enum class Hat(val value: Int) {
    UP(0), UP_RIGHT(1), RIGHT(2), DOWN_RIGHT(3), DOWN(4), DOWN_LEFT(5), LEFT(6), UP_LEFT(7), CENTER(8);

    companion object {
        /**
         * Reads the d-pad bits of a Nintendo full input report's left byte: down 0x01, up 0x02,
         * right 0x04, left 0x08.
         */
        fun from(leftByte: Int): Hat {
            val up = leftByte and 0x02 != 0
            val down = leftByte and 0x01 != 0
            val right = leftByte and 0x04 != 0
            val left = leftByte and 0x08 != 0
            val vertical = if (up == down) 0 else if (up) -1 else 1
            val horizontal = if (left == right) 0 else if (left) -1 else 1
            return when (vertical to horizontal) {
                -1 to 0 -> UP
                -1 to 1 -> UP_RIGHT
                0 to 1 -> RIGHT
                1 to 1 -> DOWN_RIGHT
                1 to 0 -> DOWN
                1 to -1 -> DOWN_LEFT
                0 to -1 -> LEFT
                -1 to -1 -> UP_LEFT
                else -> CENTER
            }
        }
    }
}

data class VirtualPadState(
    val buttons: Int,
    val hat: Hat,
    val leftX: Int = 128,
    val leftY: Int = 128,
    val rightX: Int = 128,
    val rightY: Int = 128
) {
    fun toReport(): ByteArray = byteArrayOf(
        buttons.toByte(),
        (buttons shr 8).toByte(),
        hat.value.toByte(),
        leftX.toByte(),
        leftY.toByte(),
        rightX.toByte(),
        rightY.toByte()
    )
}

/**
 * A gamepad created through the kernel's uhid node. It lives while [output] stays open, and
 * Android sees it as an ordinary USB gamepad with 16 buttons, a hat and two sticks.
 */
class UhidGamepad(private val output: FileOutputStream) : AutoCloseable {

    fun create(name: String, uniq: String, vendorId: Int, productId: Int) {
        val event = newEvent(UHID_CREATE2)
        event.put(fixed(name, NAME_SIZE))
        event.put(fixed(PHYS, PHYS_SIZE))
        event.put(fixed(uniq, UNIQ_SIZE))
        event.putShort(DESCRIPTOR.size.toShort())
        event.putShort(BUS_USB.toShort())
        event.putInt(vendorId)
        event.putInt(productId)
        event.putInt(0)
        event.putInt(0)
        event.put(DESCRIPTOR)
        output.write(event.array())
    }

    fun send(state: VirtualPadState) {
        val report = state.toReport()
        val event = newEvent(UHID_INPUT2)
        event.putShort(report.size.toShort())
        event.put(report)
        output.write(event.array())
    }

    override fun close() {
        runCatching { send(VirtualPadState(buttons = 0, hat = Hat.CENTER)) }
        runCatching { output.write(newEvent(UHID_DESTROY).array()) }
        runCatching { output.close() }
    }

    private fun newEvent(type: Int): ByteBuffer =
        ByteBuffer.allocate(EVENT_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply { putInt(type) }

    private fun fixed(value: String, size: Int): ByteArray =
        value.toByteArray(Charsets.UTF_8).copyOf(size - 1).copyOf(size)

    companion object {
        const val PHYS = "argosy-pad"
        private const val UHID_DESTROY = 1
        private const val UHID_CREATE2 = 11
        private const val UHID_INPUT2 = 12
        private const val BUS_USB = 0x03
        private const val NAME_SIZE = 128
        private const val PHYS_SIZE = 64
        private const val UNIQ_SIZE = 64
        private const val EVENT_SIZE = 4 + NAME_SIZE + PHYS_SIZE + UNIQ_SIZE + 2 + 2 + 4 * 4 + 4096

        private val DESCRIPTOR = intArrayOf(
            0x05, 0x01, 0x09, 0x05, 0xA1, 0x01,
            0x05, 0x09, 0x19, 0x01, 0x29, 0x10, 0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95, 0x10, 0x81, 0x02,
            0x05, 0x01, 0x09, 0x39, 0x15, 0x00, 0x25, 0x07, 0x35, 0x00, 0x46, 0x3B, 0x01, 0x65, 0x14,
            0x75, 0x04, 0x95, 0x01, 0x81, 0x42,
            0x75, 0x04, 0x95, 0x01, 0x81, 0x03,
            0x09, 0x30, 0x09, 0x31, 0x09, 0x32, 0x09, 0x35, 0x15, 0x00, 0x26, 0xFF, 0x00,
            0x75, 0x08, 0x95, 0x04, 0x81, 0x02,
            0xC0
        ).map { it.toByte() }.toByteArray()
    }
}
