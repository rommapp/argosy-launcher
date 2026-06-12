package com.nendo.argosy.libretro.touch

import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolvedLayoutTest {

    private val sample = TouchLayoutRegistry.forPlatform("snes")

    @Test
    fun `landscape defaults include face system shoulders and dpad`() {
        val r = LayoutDefaults.forOrientation(sample, Configuration.ORIENTATION_LANDSCAPE)
        assertNotNull(r.get(GroupId.DPAD))
        assertNotNull(r.get(GroupId.FACE))
        assertNotNull(r.get(GroupId.SYSTEM))
        assertNotNull(r.get(GroupId.SHOULDERS))
        assertNull(r.get(GroupId.LEFT_ANALOG))
    }

    @Test
    fun `psx landscape defaults include left analog`() {
        val r = LayoutDefaults.forOrientation(
            TouchLayoutRegistry.forPlatform("psx"),
            Configuration.ORIENTATION_LANDSCAPE
        )
        assertNotNull(r.get(GroupId.LEFT_ANALOG))
    }

    @Test
    fun `ps2 landscape defaults include both analogs`() {
        val r = LayoutDefaults.forOrientation(
            TouchLayoutRegistry.forPlatform("ps2"),
            Configuration.ORIENTATION_LANDSCAPE
        )
        assertNotNull(r.get(GroupId.LEFT_ANALOG))
        assertNotNull(r.get(GroupId.RIGHT_ANALOG))
    }

    @Test
    fun `applyHandedness mirrors X anchors`() {
        val r = LayoutDefaults.forOrientation(sample, Configuration.ORIENTATION_LANDSCAPE)
        val swapped = r.applyHandedness(swap = true)
        r.placements.forEach { (id, p) ->
            val s = swapped.placements[id]!!
            assertEquals(1f - p.anchorX, s.anchorX, 0.0001f)
            assertEquals(p.anchorY, s.anchorY, 0.0001f)
        }
    }

    @Test
    fun `applyHandedness with false is identity`() {
        val r = LayoutDefaults.forOrientation(sample, Configuration.ORIENTATION_LANDSCAPE)
        val swapped = r.applyHandedness(swap = false)
        assertEquals(r.placements, swapped.placements)
    }

    @Test
    fun `applySizeScale multiplies all group scales uniformly`() {
        val r = LayoutDefaults.forOrientation(sample, Configuration.ORIENTATION_LANDSCAPE)
        val scaled = r.applySizeScale(1.5f)
        r.placements.forEach { (id, p) ->
            val s = scaled.placements[id]!!
            assertEquals(p.scale * 1.5f, s.scale, 0.0001f)
            assertEquals(p.anchorX, s.anchorX, 0.0001f)
        }
    }

    @Test
    fun `applySizeScale of 1 is identity`() {
        val r = LayoutDefaults.forOrientation(sample, Configuration.ORIENTATION_LANDSCAPE)
        val scaled = r.applySizeScale(1.0f)
        assertEquals(r.placements, scaled.placements)
    }

    @Test
    fun `applyMirror180 mirrors only when rotation is 180 off baseline`() {
        val r = LayoutDefaults.forOrientation(sample, Configuration.ORIENTATION_LANDSCAPE)
        val noMirror = r.applyMirror180(mirror = true, rotation = 1, baseline = 1)
        assertEquals(r.placements, noMirror.placements)

        val mirrored = r.applyMirror180(mirror = true, rotation = 3, baseline = 1)
        r.placements.forEach { (id, p) ->
            assertEquals(1f - p.anchorX, mirrored.placements[id]!!.anchorX, 0.0001f)
        }
    }

    @Test
    fun `applyMirror180 off is identity regardless of rotation`() {
        val r = LayoutDefaults.forOrientation(sample, Configuration.ORIENTATION_LANDSCAPE)
        val identity = r.applyMirror180(mirror = false, rotation = 3, baseline = 1)
        assertEquals(r.placements, identity.placements)
    }

    @Test
    fun `mirror180 works across all rotation pairs`() {
        val r = LayoutDefaults.forOrientation(sample, Configuration.ORIENTATION_LANDSCAPE)
        listOf(0 to 2, 1 to 3, 2 to 0, 3 to 1).forEach { (base, rot) ->
            val out = r.applyMirror180(mirror = true, rotation = rot, baseline = base)
            r.placements.forEach { (id, p) ->
                assertEquals(
                    "rotation=$rot base=$base group=$id",
                    1f - p.anchorX,
                    out.placements[id]!!.anchorX,
                    0.0001f
                )
            }
        }
    }
}
