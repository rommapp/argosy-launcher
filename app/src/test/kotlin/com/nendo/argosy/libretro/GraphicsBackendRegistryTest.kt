package com.nendo.argosy.libretro

import com.swordfish.libretrodroid.Variable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphicsBackendRegistryTest {

    private val vulkanTokens = mapOf(
        "ppsspp" to ("ppsspp_backend" to "vulkan"),
        "mednafen_psx_hw" to ("beetle_psx_hw_renderer" to "hardware_vk"),
        "swanstation" to ("swanstation_GPU_Renderer" to "Vulkan"),
        "azahar" to ("citra_graphics_api" to "Vulkan"),
        "parallel_n64" to ("parallel-n64-gfxplugin" to "parallel"),
        "mupen64plus_next_gles3" to ("mupen64plus-rdp-plugin" to "parallel"),
        "mupen64plus_next_gles2" to ("mupen64plus-rdp-plugin" to "parallel"),
        "armsx2" to ("armsx2_renderer" to "Vulkan")
    )

    @Test
    fun `option cores map to their upstream renderer option and Vulkan token`() {
        vulkanTokens.forEach { (coreId, expected) ->
            val backend = GraphicsBackendRegistry.forCore(coreId) as GraphicsBackend.CoreOption
            assertEquals(coreId, expected, backend.optionKey to backend.vulkanValue)
        }
    }

    @Test
    fun `every option backend names a manifest option that offers its Vulkan token`() {
        LibretroCoreRegistry.coresFor(is64BitProcess = true)
            .mapNotNull { GraphicsBackendRegistry.forCore(it.coreId) as? GraphicsBackend.CoreOption }
            .forEach { backend ->
                val def = GraphicsBackendRegistry.optionDef(backend)
                assertNotNull("${backend.coreId} ${backend.optionKey}", def)
                backend.vulkanValue?.let { assertTrue("${backend.coreId} $it", it in def!!.values) }
            }
    }

    @Test
    fun `dolphin and flycast follow the frontend context`() {
        listOf("dolphin", "flycast").forEach { coreId ->
            val backend = GraphicsBackendRegistry.forCore(coreId)
            assertTrue(coreId, backend is GraphicsBackend.FrontendApi)
            assertTrue(coreId, GraphicsBackendRegistry.supportsVulkan(backend))
        }
    }

    @Test
    fun `melonDS cores have no renderer choice on Android`() {
        listOf("melonds", "melondsds").forEach { coreId ->
            assertNull(coreId, GraphicsBackendRegistry.forCore(coreId))
        }
    }

    @Test
    fun `a core without a renderer choice has no backend`() {
        assertNull(GraphicsBackendRegistry.forCore("snes9x"))
        assertNull(GraphicsBackendRegistry.forCore(null))
        assertFalse(GraphicsBackendRegistry.prefersVulkan("snes9x", emptyMap(), GraphicsApi.VULKAN.token))
    }

    @Test
    fun `an option core prefers Vulkan only when the resolved variable is its Vulkan token`() {
        assertTrue(GraphicsBackendRegistry.prefersVulkan("ppsspp", mapOf("ppsspp_backend" to "vulkan"), null))
        assertFalse(GraphicsBackendRegistry.prefersVulkan("ppsspp", mapOf("ppsspp_backend" to "opengl"), null))
        assertFalse(GraphicsBackendRegistry.prefersVulkan("ppsspp", mapOf("ppsspp_backend" to "auto"), null))
        assertFalse(
            GraphicsBackendRegistry.prefersVulkan("ppsspp", mapOf("ppsspp_backend" to "opengl"), GraphicsApi.VULKAN.token)
        )
    }

    @Test
    fun `an option missing from the variables runs at the core default`() {
        assertFalse(GraphicsBackendRegistry.prefersVulkan("mednafen_psx_hw", emptyMap(), null))
        assertFalse(GraphicsBackendRegistry.prefersVulkan("azahar", emptyMap(), null))
    }

    @Test
    fun `a frontend core prefers Vulkan unless the platform stored OpenGL`() {
        assertTrue(GraphicsBackendRegistry.prefersVulkan("dolphin", emptyMap(), "vulkan"))
        assertFalse(GraphicsBackendRegistry.prefersVulkan("dolphin", emptyMap(), "opengl"))
        assertTrue(GraphicsBackendRegistry.prefersVulkan("flycast", emptyMap(), null))
        assertTrue(GraphicsBackendRegistry.prefersVulkan("flycast", mapOf("ppsspp_backend" to "opengl"), "garbage"))
    }

    @Test
    fun `withoutVulkan swaps only a Vulkan renderer choice for the core default`() {
        val vulkan = listOf(Variable("beetle_psx_hw_renderer", "hardware_vk"), Variable("beetle_psx_hw_dither_mode", "1x"))
        val software = listOf(Variable("beetle_psx_hw_renderer", "software"))

        assertEquals(
            setOf("beetle_psx_hw_renderer" to "hardware", "beetle_psx_hw_dither_mode" to "1x"),
            GraphicsBackendRegistry.withoutVulkan("mednafen_psx_hw", vulkan).map { it.key to it.value }.toSet()
        )
        assertEquals(software, GraphicsBackendRegistry.withoutVulkan("mednafen_psx_hw", software))
        assertEquals(vulkan, GraphicsBackendRegistry.withoutVulkan("dolphin", vulkan))
    }

    @Test
    fun `ARMSX2 sends Vulkan by default and falls back to OpenGL without Vulkan`() {
        val sent = listOf(Variable("armsx2_renderer", "Vulkan"))
        val software = listOf(Variable("armsx2_renderer", "Software"))

        assertTrue(GraphicsBackendRegistry.prefersVulkan("armsx2", mapOf("armsx2_renderer" to "Vulkan"), null))
        assertEquals(
            listOf("armsx2_renderer" to "OpenGL"),
            GraphicsBackendRegistry.withoutVulkan("armsx2", sent).map { it.key to it.value }
        )
        assertEquals(software, GraphicsBackendRegistry.withoutVulkan("armsx2", software))
    }

    @Test
    fun `only ARMSX2 refuses a custom Vulkan driver`() {
        assertFalse(GraphicsBackendRegistry.acceptsCustomVulkanDriver("armsx2"))
        listOf("ppsspp", "dolphin", "flycast", "azahar", "mednafen_psx_hw", null).forEach {
            assertTrue("$it", GraphicsBackendRegistry.acceptsCustomVulkanDriver(it))
        }
    }

    @Test
    fun `a game backend replaces the resolved core option and keeps the other variables`() {
        val resolved = listOf(Variable("ppsspp_backend", "opengl"), Variable("ppsspp_cpu_core", "JIT"))

        val applied = GraphicsBackendRegistry.applyGameBackend("ppsspp", resolved, "vulkan")

        assertEquals(
            setOf("ppsspp_backend" to "vulkan", "ppsspp_cpu_core" to "JIT"),
            applied.map { it.key to it.value }.toSet()
        )
        assertEquals(1, applied.count { it.key == "ppsspp_backend" })
    }

    @Test
    fun `a game backend is added when the core option was left at its default`() {
        val applied = GraphicsBackendRegistry.applyGameBackend("mednafen_psx_hw", emptyList(), "hardware_vk")

        assertEquals(listOf("beetle_psx_hw_renderer" to "hardware_vk"), applied.map { it.key to it.value })
    }

    @Test
    fun `no game backend, a frontend core, or a core without a backend leave variables untouched`() {
        val resolved = listOf(Variable("ppsspp_backend", "opengl"))

        assertEquals(resolved, GraphicsBackendRegistry.applyGameBackend("ppsspp", resolved, null))
        assertEquals(resolved, GraphicsBackendRegistry.applyGameBackend("ppsspp", resolved, " "))
        assertEquals(resolved, GraphicsBackendRegistry.applyGameBackend("dolphin", resolved, "vulkan"))
        assertEquals(resolved, GraphicsBackendRegistry.applyGameBackend("snes9x", resolved, "vulkan"))
    }

    @Test
    fun `a stored game value the core does not offer falls back to the option default`() {
        val backend = GraphicsBackendRegistry.forCore("ppsspp")!!

        assertEquals("vulkan", GraphicsBackendRegistry.gameValue(backend, "metal"))
        assertEquals("vulkan", GraphicsBackendRegistry.gameValue(backend, "vulkan"))
        assertNull(GraphicsBackendRegistry.gameValue(backend, null))
    }

    @Test
    fun `a frontend game value must be a known api token`() {
        val backend = GraphicsBackendRegistry.forCore("flycast")!!

        assertEquals("vulkan", GraphicsBackendRegistry.gameValue(backend, "vulkan"))
        assertNull(GraphicsBackendRegistry.gameValue(backend, "Vulkan"))
    }

    @Test
    fun `effective value reads the variable, then the core default, and frontends default to Vulkan`() {
        val psx = GraphicsBackendRegistry.forCore("mednafen_psx_hw")!!
        val dolphin = GraphicsBackendRegistry.forCore("dolphin")!!

        assertEquals("software", GraphicsBackendRegistry.effectiveValue(psx, mapOf("beetle_psx_hw_renderer" to "software"), null))
        assertEquals("hardware", GraphicsBackendRegistry.effectiveValue(psx, emptyMap(), null))
        assertEquals("vulkan", GraphicsBackendRegistry.effectiveValue(dolphin, emptyMap(), null))
        assertEquals("opengl", GraphicsBackendRegistry.effectiveValue(dolphin, emptyMap(), "opengl"))
        assertEquals("vulkan", GraphicsBackendRegistry.effectiveValue(dolphin, emptyMap(), "vulkan"))
    }

    @Test
    fun `graphics api tokens round trip and unknown tokens resolve to nothing`() {
        GraphicsApi.entries.forEach { assertEquals(it, GraphicsApi.fromToken(it.token)) }
        assertNull(GraphicsApi.fromToken("Vulkan"))
        assertNull(GraphicsApi.fromToken(null))
    }
}
