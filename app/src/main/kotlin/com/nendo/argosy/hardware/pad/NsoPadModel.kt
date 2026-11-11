package com.nendo.argosy.hardware.pad

/**
 * A Nintendo Switch Online USB pad. Each model reads the shared full input report (id 0x30) and
 * fills a [VirtualPadState] by printed label: the pad's A button becomes BUTTON_A, as Android's
 * own Nintendo key layouts map it.
 */
enum class NsoPadModel(val productId: Int, val displayName: String) {
    SNES(0x2017, "SNES Controller") {
        override fun decode(report: ByteArray, center: StickCenter?): VirtualPadState {
            val right = report.u8(3)
            val shared = report.u8(4)
            val left = report.u8(5)
            return VirtualPadState(
                buttons = buttonMask(
                    VirtualButton.A to right.has(0x08),
                    VirtualButton.B to right.has(0x04),
                    VirtualButton.X to right.has(0x02),
                    VirtualButton.Y to right.has(0x01),
                    VirtualButton.L1 to left.has(0x40),
                    VirtualButton.R1 to right.has(0x40),
                    VirtualButton.L2 to left.has(0x80),
                    VirtualButton.R2 to right.has(0x80),
                    VirtualButton.SELECT to shared.has(0x01),
                    VirtualButton.START to shared.has(0x02),
                    VirtualButton.MODE to shared.has(0x10)
                ),
                hat = Hat.from(left)
            )
        }
    },
    N64(0x2019, "N64 Controller") {
        override fun decode(report: ByteArray, center: StickCenter?): VirtualPadState {
            val right = report.u8(3)
            val shared = report.u8(4)
            val left = report.u8(5)
            val rawX = report.u8(6) or ((report.u8(7) and 0x0F) shl 8)
            val rawY = (report.u8(7) shr 4) or (report.u8(8) shl 4)
            val origin = center ?: NOMINAL_CENTER
            return VirtualPadState(
                buttons = buttonMask(
                    VirtualButton.A to right.has(0x08),
                    VirtualButton.B to right.has(0x04),
                    VirtualButton.L1 to left.has(0x40),
                    VirtualButton.R1 to right.has(0x40),
                    VirtualButton.L2 to left.has(0x80),
                    VirtualButton.R2 to shared.has(0x08),
                    VirtualButton.START to shared.has(0x02),
                    VirtualButton.MODE to shared.has(0x10)
                ),
                hat = Hat.from(left),
                leftX = stickAxis(rawX - origin.x),
                leftY = stickAxis(origin.y - rawY),
                rightX = digitalAxis(negative = right.has(0x80), positive = right.has(0x02)),
                rightY = digitalAxis(negative = right.has(0x01), positive = shared.has(0x01))
            )
        }

        override fun centerOf(report: ByteArray): StickCenter? {
            val x = report.u8(6) or ((report.u8(7) and 0x0F) shl 8)
            val y = (report.u8(7) shr 4) or (report.u8(8) shl 4)
            return StickCenter(x, y).takeIf { x in RESTING_BAND && y in RESTING_BAND }
        }
    };

    abstract fun decode(report: ByteArray, center: StickCenter?): VirtualPadState

    open fun centerOf(report: ByteArray): StickCenter? = null

    companion object {
        const val VENDOR_ID = 0x057E
        const val REPORT_SIZE = 64
        const val FULL_INPUT_REPORT = 0x30

        private const val STICK_RANGE = 1350
        private const val STICK_DEADZONE = 40
        private const val AXIS_CENTER = 128
        private const val AXIS_MAX = 255
        private val RESTING_BAND = 1700..2400
        private val NOMINAL_CENTER = StickCenter(2048, 2048)

        fun forProduct(productId: Int): NsoPadModel? = entries.firstOrNull { it.productId == productId }

        private fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xFF

        private fun Int.has(bit: Int): Boolean = this and bit != 0

        private fun buttonMask(vararg pressed: Pair<VirtualButton, Boolean>): Int =
            pressed.fold(0) { mask, (button, down) -> if (down) mask or button.bit else mask }

        private fun stickAxis(offset: Int): Int {
            if (kotlin.math.abs(offset) <= STICK_DEADZONE) return AXIS_CENTER
            return (AXIS_CENTER + offset * (AXIS_MAX - AXIS_CENTER) / STICK_RANGE).coerceIn(0, AXIS_MAX)
        }

        private fun digitalAxis(negative: Boolean, positive: Boolean): Int = when {
            negative && !positive -> 0
            positive && !negative -> AXIS_MAX
            else -> AXIS_CENTER
        }
    }
}

data class StickCenter(val x: Int, val y: Int)
