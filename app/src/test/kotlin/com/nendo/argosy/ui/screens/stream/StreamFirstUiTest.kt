package com.nendo.argosy.ui.screens.stream

import com.nendo.argosy.data.preferences.NotOnDeviceAction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamFirstUiTest {

    private val streamMode = StreamFirstUi(NotOnDeviceAction.STREAM, setOf("psx"))

    @Test
    fun `a romm game on a served platform leads with stream whatever the slug case`() {
        assertTrue(streamMode.leads("PSX", isRommGame = true, isOnDevice = false))
    }

    @Test
    fun `a platform without a container keeps download`() {
        assertFalse(streamMode.leads("snes", isRommGame = true, isOnDevice = false))
        assertFalse(streamMode.leads(null, isRommGame = true, isOnDevice = false))
    }

    @Test
    fun `a game on the device or outside romm never leads with stream`() {
        assertFalse(streamMode.leads("psx", isRommGame = true, isOnDevice = true))
        assertFalse(streamMode.leads("psx", isRommGame = false, isOnDevice = false))
    }

    @Test
    fun `download mode never leads with stream`() {
        val downloadMode = StreamFirstUi(NotOnDeviceAction.DOWNLOAD, setOf("psx"))

        assertFalse(downloadMode.leads("psx", isRommGame = true, isOnDevice = false))
    }
}
