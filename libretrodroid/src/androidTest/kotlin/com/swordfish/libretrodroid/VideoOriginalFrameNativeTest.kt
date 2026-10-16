package com.swordfish.libretrodroid

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VideoOriginalFrameNativeTest {

    @Test
    fun hardwareTextureAllocationDiffersFromActiveImage() {
        System.loadLibrary("libretrodroid")
        assertTrue(renderPaddedHardwareFrame())
    }

    private external fun renderPaddedHardwareFrame(): Boolean
}
