package com.nendo.argosy.libretro.touch

import com.nendo.argosy.data.repository.MappingPlatforms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchLayoutRegistryTest {

    @Test
    fun `nes returns NES preset with 2 face buttons and no shoulders`() {
        val spec = TouchLayoutRegistry.forPlatform("nes")
        assertEquals(MappingPlatforms.NES, spec.mappingPlatform)
        assertEquals(FaceShape.HorizontalPair, spec.face)
        assertEquals(2, spec.faceSlots.size)
        assertEquals(ShoulderShape.None, spec.shoulders)
        assertEquals(AnalogConfig.None, spec.analog)
    }

    @Test
    fun `snes returns SNES preset with diamond face and shoulders`() {
        val spec = TouchLayoutRegistry.forPlatform("snes")
        assertEquals(MappingPlatforms.SNES, spec.mappingPlatform)
        assertEquals(FaceShape.Diamond4, spec.face)
        assertEquals(4, spec.faceSlots.size)
        assertEquals(ShoulderShape.TopPair, spec.shoulders)
    }

    @Test
    fun `psx uses diamond + four corners shoulders + dual analog`() {
        val spec = TouchLayoutRegistry.forPlatform("psx")
        assertEquals(FaceShape.Diamond4, spec.face)
        assertEquals(ShoulderShape.FourCorners, spec.shoulders)
        assertEquals(4, spec.shoulderSlots.size)
        assertEquals(AnalogConfig.LeftAndRight, spec.analog)
    }

    @Test
    fun `ps2 uses dual analog`() {
        val spec = TouchLayoutRegistry.forPlatform("ps2")
        assertEquals(AnalogConfig.LeftAndRight, spec.analog)
    }

    @Test
    fun `n64 uses right analog for C-cluster instead of discrete buttons`() {
        val spec = TouchLayoutRegistry.forPlatform("n64")
        assertEquals(AnalogConfig.LeftAndRight, spec.analog)
        assertEquals(ShoulderShape.TopPairPlusZ, spec.shoulders)
        assertEquals(3, spec.shoulderSlots.size)
    }

    @Test
    fun `genesis defaults to 3-button arc`() {
        val spec = TouchLayoutRegistry.forPlatform("genesis", genesis6Button = false)
        assertEquals(FaceShape.HorizontalTrio, spec.face)
        assertEquals(3, spec.faceSlots.size)
    }

    @Test
    fun `genesis with 6-button setting uses 2x3 stack`() {
        val spec = TouchLayoutRegistry.forPlatform("genesis", genesis6Button = true)
        assertEquals(FaceShape.Stack2x3, spec.face)
        assertEquals(6, spec.faceSlots.size)
    }

    @Test
    fun `saturn uses 2x3 stack with two shoulders and Mode select`() {
        val spec = TouchLayoutRegistry.forPlatform("saturn")
        assertEquals(FaceShape.Stack2x3, spec.face)
        assertEquals(6, spec.faceSlots.size)
        assertEquals(ShoulderShape.TopPair, spec.shoulders)
        assertTrue(spec.system.any { it.label.equals("Mode", ignoreCase = true) })
    }

    @Test
    fun `arcade is 6-button row with Coin and Start`() {
        val spec = TouchLayoutRegistry.forPlatform("arcade")
        assertEquals(FaceShape.Row6, spec.face)
        assertEquals(6, spec.faceSlots.size)
        assertTrue(spec.system.any { it.label.equals("Coin", ignoreCase = true) })
    }

    @Test
    fun `atari2600 single fire button`() {
        val spec = TouchLayoutRegistry.forPlatform("atari2600")
        assertEquals(FaceShape.Single, spec.face)
        assertEquals(1, spec.faceSlots.size)
    }

    @Test
    fun `atari5200 uses analog only and no separate dpad`() {
        val spec = TouchLayoutRegistry.forPlatform("atari5200")
        assertEquals(DpadStyle.AnalogOnly, spec.dpad)
        assertEquals(AnalogConfig.LeftOnly, spec.analog)
    }

    @Test
    fun `vectrex is analog plus row of 4 buttons`() {
        val spec = TouchLayoutRegistry.forPlatform("vectrex")
        assertEquals(DpadStyle.AnalogOnly, spec.dpad)
        assertEquals(FaceShape.Row4, spec.face)
        assertEquals(4, spec.faceSlots.size)
    }

    @Test
    fun `unknown slug falls back to generic`() {
        val spec = TouchLayoutRegistry.forPlatform("nonexistent_platform_xyz")
        assertEquals(MappingPlatforms.UNIVERSAL, spec.mappingPlatform)
        assertEquals(FaceShape.Diamond4, spec.face)
    }

    @Test
    fun `dreamcast face order matches DC controller layout`() {
        val spec = TouchLayoutRegistry.forPlatform("dreamcast")
        assertEquals(FaceShape.Diamond4, spec.face)
        assertEquals(AnalogConfig.LeftOnly, spec.analog)
        assertTrue(spec.system.size == 1)
    }

    @Test
    fun `3do uses A-B-C arc with shoulders and Pause-Start`() {
        val spec = TouchLayoutRegistry.forPlatform("3do")
        assertEquals(FaceShape.HorizontalTrio, spec.face)
        assertEquals(3, spec.faceSlots.size)
        assertEquals(ShoulderShape.TopPair, spec.shoulders)
    }
}
