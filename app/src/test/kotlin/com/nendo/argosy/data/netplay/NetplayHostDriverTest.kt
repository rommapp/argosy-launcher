package com.nendo.argosy.data.netplay

import com.swordfish.libretrodroid.GLRetroView
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.net.InetSocketAddress

class NetplayHostDriverTest {

    @Test
    fun canConstructHostDriver() {
        val scope = CoroutineScope(StandardTestDispatcher() + Job())
        val driver = NetplayHostDriver(
            retroView = mockk(relaxed = true),
            transport = mockk(relaxed = true),
            peerAddress = InetSocketAddress("127.0.0.1", 12345),
            peerUserId = "peer",
            localPort = 0,
            guestPort = 1,
            inputShadow = NetplayInputShadow(),
            scope = scope,
            onSessionEnd = {}
        )
        assertNotNull(driver)
        driver.stop()
    }

    @Test
    fun canConstructGuestDriver() {
        val scope = CoroutineScope(StandardTestDispatcher() + Job())
        val driver = NetplayGuestDriver(
            retroView = mockk(relaxed = true),
            transport = mockk(relaxed = true),
            peerAddress = InetSocketAddress("127.0.0.1", 12345),
            peerUserId = "host",
            localPort = 1,
            hostPort = 0,
            inputShadow = NetplayInputShadow(),
            scope = scope,
            onSessionEnd = {}
        )
        assertNotNull(driver)
        driver.stop()
    }
}
