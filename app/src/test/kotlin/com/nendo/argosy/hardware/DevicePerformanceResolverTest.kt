package com.nendo.argosy.hardware

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicePerformanceResolverTest {

    private val generic = mockk<DevicePerformance>()
    private var detections = 0

    private fun resolver(fanAvailable: Boolean, product: String?, detected: DevicePerformance?) =
        DevicePerformanceResolver(
            fanController = mockk<FanController> { every { isAvailable() } returns fanAvailable },
            product = product,
            detectRootedQualcomm = {
                detections++
                detected
            }
        )

    @Test
    fun `an ayn device resolves to its own backend before the generic probe runs`() {
        val resolved = resolver(fanAvailable = true, product = null, detected = generic).resolve()

        assertTrue(resolved!!.controlsFan)
        assertEquals(0, detections)
    }

    @Test
    fun `an ayaneo pocket fit resolves to its measured table before the generic probe runs`() {
        val resolved = resolver(fanAvailable = false, product = "AYANEO_Pocket_FIT", detected = generic).resolve()

        assertTrue(resolved is AyaneoPerformance)
        assertEquals(0, detections)
    }

    @Test
    fun `any other device gets the rooted qualcomm backend when it is detected`() {
        val resolved = resolver(fanAvailable = false, product = OTHER_PRODUCT, detected = generic).resolve()

        assertSame(generic, resolved)
        assertEquals(1, detections)
    }

    @Test
    fun `a device the generic probe rejects has no performance controls`() {
        assertNull(resolver(fanAvailable = false, product = OTHER_PRODUCT, detected = null).resolve())
    }

    private companion object {
        const val OTHER_PRODUCT = "generic_qualcomm_box"
    }
}
