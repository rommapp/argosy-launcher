package com.nendo.argosy.libretro.coreoptions

import com.nendo.argosy.libretro.coreoptions.manifests.Mupen64PlusNextGles2Manifest
import com.nendo.argosy.libretro.coreoptions.manifests.Mupen64PlusNextGles3Manifest
import com.nendo.argosy.libretro.coreoptions.manifests.ParallelN64Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class N64RendererScopeTest {

    private fun CoreOptionManifest.option(key: String) = options.single { it.key == key }

    private fun CoreOptionDef.appliesUnder(rendererKey: String, renderer: String?) =
        appliesWith { key -> renderer.takeIf { key == rendererKey } }

    private val mupenRdp = "mupen64plus-rdp-plugin"
    private val n64Gfx = "parallel-n64-gfxplugin"

    @Test
    fun `mupen GLideN64 resolution rows apply only under GLideN64`() {
        for (key in listOf("mupen64plus-43screensize", "mupen64plus-EnableNativeResFactor", "mupen64plus-aspect")) {
            val option = Mupen64PlusNextGles3Manifest.option(key)
            assertTrue(key, option.appliesUnder(mupenRdp, "gliden64"))
            assertFalse(key, option.appliesUnder(mupenRdp, "parallel"))
            assertFalse(key, option.appliesUnder(mupenRdp, "angrylion"))
        }
    }

    @Test
    fun `mupen ParaLLEl upscaling applies only under ParaLLEl`() {
        val upscaling = Mupen64PlusNextGles3Manifest.option("mupen64plus-parallel-rdp-upscaling")
        assertTrue(upscaling.appliesUnder(mupenRdp, "parallel"))
        assertFalse(upscaling.appliesUnder(mupenRdp, "gliden64"))
        assertFalse(upscaling.appliesUnder(mupenRdp, "angrylion"))
    }

    @Test
    fun `mupen Angrylion rows apply only under Angrylion`() {
        val sync = Mupen64PlusNextGles3Manifest.option("mupen64plus-angrylion-sync")
        assertTrue(sync.appliesUnder(mupenRdp, "angrylion"))
        assertFalse(sync.appliesUnder(mupenRdp, "parallel"))
    }

    @Test
    fun `an unknown renderer shows every row, as the core does`() {
        val resolution = Mupen64PlusNextGles3Manifest.option("mupen64plus-43screensize")
        assertTrue(resolution.appliesUnder(mupenRdp, null))
    }

    @Test
    fun `options outside a renderer always apply`() {
        val pak = Mupen64PlusNextGles3Manifest.option("mupen64plus-pak1")
        assertNull(pak.requires)
        assertNull(Mupen64PlusNextGles3Manifest.option(mupenRdp).requires)
    }

    @Test
    fun `GLES2 drops MSAA and the color copy modes GLES2 builds lack`() {
        assertTrue(Mupen64PlusNextGles2Manifest.options.none { it.key == "mupen64plus-MultiSampling" })
        val copy = Mupen64PlusNextGles2Manifest.option("mupen64plus-EnableCopyColorToRDRAM")
        assertEquals(listOf("Off", "Sync"), copy.values)
        assertEquals("Sync", copy.defaultValue)
    }

    @Test
    fun `GLES2 keeps the renderer scoping`() {
        val resolution = Mupen64PlusNextGles2Manifest.option("mupen64plus-43screensize")
        assertFalse(resolution.appliesUnder(mupenRdp, "parallel"))
    }

    @Test
    fun `ParaLLEl-N64 upscaling and screensize split by renderer`() {
        val upscaling = ParallelN64Manifest.option("parallel-n64-upscaling")
        val screensize = ParallelN64Manifest.option("parallel-n64-screensize")
        for (renderer in listOf("parallel", "angrylion")) {
            assertTrue(renderer, upscaling.appliesUnder(n64Gfx, renderer))
            assertFalse(renderer, screensize.appliesUnder(n64Gfx, renderer))
        }
        for (renderer in listOf("gliden64", "glide64", "rice")) {
            assertFalse(renderer, upscaling.appliesUnder(n64Gfx, renderer))
            assertTrue(renderer, screensize.appliesUnder(n64Gfx, renderer))
        }
    }

    @Test
    fun `ParaLLEl-N64 GLideN64 resolution applies only under GLideN64`() {
        val factor = ParallelN64Manifest.option("parallel-n64-gliden64-EnableNativeResFactor")
        assertTrue(factor.appliesUnder(n64Gfx, "gliden64"))
        assertFalse(factor.appliesUnder(n64Gfx, "parallel"))
    }

    @Test
    fun `ParaLLEl-N64 Glide64 rows apply only under Glide64`() {
        val filtering = ParallelN64Manifest.option("parallel-n64-filtering")
        assertTrue(filtering.appliesUnder(n64Gfx, "glide64"))
        assertFalse(filtering.appliesUnder(n64Gfx, "gliden64"))
    }
}
