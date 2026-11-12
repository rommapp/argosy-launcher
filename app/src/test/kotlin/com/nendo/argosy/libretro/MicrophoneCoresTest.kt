package com.nendo.argosy.libretro

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MicrophoneCoresTest {

    @Test
    fun `melonDS DS reads the host microphone and the legacy melonDS core does not`() {
        assertTrue(MicrophoneCores.usesHostMicrophone("melondsds"))
        assertFalse(MicrophoneCores.usesHostMicrophone("melonds"))
        assertFalse(MicrophoneCores.usesHostMicrophone(null))
    }

    @Test
    fun `the request goes out only on a microphone core never granted or asked`() {
        assertTrue(MicrophoneCores.shouldRequest("melondsds", granted = false, alreadyAsked = false))
        assertFalse(MicrophoneCores.shouldRequest("melondsds", granted = true, alreadyAsked = false))
        assertFalse(MicrophoneCores.shouldRequest("melondsds", granted = false, alreadyAsked = true))
        assertFalse(MicrophoneCores.shouldRequest("melonds", granted = false, alreadyAsked = false))
    }
}
