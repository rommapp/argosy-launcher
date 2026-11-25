package com.nendo.argosy.data.emulator

import org.junit.Assert.assertEquals
import org.junit.Test

class EdenDriverConfigTest {

    private val driver = "/storage/emulated/0/Android/data/dev.eden.eden_emulator/files/gpu_drivers/new.adpkg.zip"

    private fun fixture(): String =
        requireNotNull(javaClass.classLoader?.getResource("emulator-config/config.ini")).readText()

    @Test
    fun `a configured driver changes only its driver_path line`() {
        val before = fixture()

        val after = EdenDriverConfig.withDriverPath(before, driver)

        val old = "driver_path=/storage/emulated/0/Android/data/dev.eden.eden_emulator/files/gpu_drivers/turnip_mrpurple_T24-toasted.adpkg.zip"
        assertEquals(before.replace(old, "driver_path=$driver"), after)
        assertEquals(driver, EdenDriverConfig.driverPath(after))
    }

    @Test
    fun `missing keys go below the section header, flag first`() {
        val before = "[Renderer]\nbackend=1\n\n[GpuDriver]\nother=1\n\n[UI]\ntheme=0\n"

        assertEquals(
            "[Renderer]\nbackend=1\n\n[GpuDriver]\ndriver_path\\default=false\ndriver_path=$driver\nother=1\n\n[UI]\ntheme=0\n",
            EdenDriverConfig.withDriverPath(before, driver)
        )
    }

    @Test
    fun `a present flag is cleared and the path goes right after it`() {
        val before = "[GpuDriver]\nother=1\ndriver_path\\default=true\n"

        assertEquals(
            "[GpuDriver]\nother=1\ndriver_path\\default=false\ndriver_path=$driver\n",
            EdenDriverConfig.withDriverPath(before, driver)
        )
    }

    @Test
    fun `a missing flag beside a present path goes right below the header`() {
        val before = "[GpuDriver]\nother=1\ndriver_path=old.zip\n"

        assertEquals(
            "[GpuDriver]\ndriver_path\\default=false\nother=1\ndriver_path=$driver\n",
            EdenDriverConfig.withDriverPath(before, driver)
        )
    }

    @Test
    fun `a file without the section or without content gets one`() {
        assertEquals(
            "[UI]\ntheme=0\n\n[GpuDriver]\ndriver_path\\default=false\ndriver_path=$driver\n",
            EdenDriverConfig.withDriverPath("[UI]\ntheme=0\n", driver)
        )
        assertEquals(
            "[GpuDriver]\ndriver_path\\default=false\ndriver_path=$driver\n",
            EdenDriverConfig.withDriverPath("", driver)
        )
    }
}
