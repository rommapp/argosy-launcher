package com.nendo.argosy.hardware.pad

import android.view.InputDevice
import com.nendo.argosy.core.input.isMutedByPadDriver
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MutedPadDevicesTest {

    private val kernelNode = PadDeviceIdentity(0x057E, 0x2017, "SNES Controller")
    private val retroidMirror = PadDeviceIdentity(0x2022, 0x3001, "SNES Controller")
    private val muted = setOf(kernelNode, retroidMirror)

    @Test
    fun `a written list reads back in the same boot`() {
        val text = MutedPadDevices.format(muted, BOOT)

        assertEquals(muted, MutedPadDevices.parse(text.lines(), BOOT + 40))
    }

    @Test
    fun `a list written in an earlier boot reads as empty`() {
        val text = MutedPadDevices.format(muted, BOOT)

        assertTrue(MutedPadDevices.parse(text.lines(), BOOT + 3_600_000).isEmpty())
    }

    @Test
    fun `a list without its boot header reads as empty`() {
        assertTrue(MutedPadDevices.parse(listOf("057e 2017 SNES Controller"), BOOT).isEmpty())
    }

    @Test
    fun `malformed rows are skipped and names keep their spaces`() {
        val lines = listOf("boot $BOOT", "zz 2017 Bad", "057e 2019 Nintendo Co., Ltd. N64 Controller", "057e 2017")

        assertEquals(
            setOf(PadDeviceIdentity(0x057E, 0x2019, "Nintendo Co., Ltd. N64 Controller")),
            MutedPadDevices.parse(lines, BOOT)
        )
    }

    @Test
    fun `the kernel node and the vendor mirror of a served pad are muted`() {
        assertTrue(MutedPadDevices.isDriverMuted(kernelNode, muted))
        assertTrue(MutedPadDevices.isDriverMuted(retroidMirror, muted))
    }

    @Test
    fun `the driver's virtual pad is never muted even when listed`() {
        val virtual = NsoPadModel.SNES.virtualIdentity

        assertFalse(MutedPadDevices.isDriverMuted(virtual, muted + virtual))
    }

    @Test
    fun `a pad with the same ids under another name is not muted`() {
        assertFalse(MutedPadDevices.isDriverMuted(PadDeviceIdentity(0x057E, 0x2017, "Other Pad"), muted))
    }

    @Test
    fun `an input device matching a muted identity is filtered and others are not`() {
        assertTrue(device(0x057E, 0x2017, "SNES Controller").isMutedByPadDriver(muted))
        assertTrue(device(0x2022, 0x3001, "SNES Controller").isMutedByPadDriver(muted))
        assertFalse(device(0x057E, 0x2017, "Nintendo SNES Controller").isMutedByPadDriver(muted))
        assertFalse(device(0x2022, 0x3001, "Retroid Pocket Controller").isMutedByPadDriver(muted))
    }

    @Test
    fun `nothing is filtered while the driver serves no pad`() {
        assertFalse(device(0x057E, 0x2017, "SNES Controller").isMutedByPadDriver(emptySet()))
    }

    private fun device(vendor: Int, product: Int, name: String): InputDevice = mockk {
        every { vendorId } returns vendor
        every { productId } returns product
        every { this@mockk.name } returns name
    }

    private companion object {
        const val BOOT = 1_762_800_000_000L
    }
}
