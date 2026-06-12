package com.nendo.argosy.libretro.touch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TouchLayoutSerializerTest {

    private val baseline = ResolvedLayout(
        mapOf(
            GroupId.DPAD to GroupPlacement(0.10f, 0.78f, 1.0f),
            GroupId.FACE to GroupPlacement(0.90f, 0.78f, 1.0f),
            GroupId.SYSTEM to GroupPlacement(0.50f, 0.92f, 1.0f)
        )
    )

    @Test
    fun `round-trip preserves placements`() {
        val json = TouchLayoutSerializer.toJson(baseline)
        val parsed = TouchLayoutSerializer.fromJson(json, default = baseline)
        baseline.placements.forEach { (id, p) ->
            val q = parsed.placements[id]!!
            assertEquals(p.anchorX, q.anchorX, 0.0001f)
            assertEquals(p.anchorY, q.anchorY, 0.0001f)
            assertEquals(p.scale, q.scale, 0.0001f)
        }
    }

    @Test
    fun `unknown group ids in saved json are dropped silently`() {
        val rogue = """{"schemaVersion":1,"groups":[{"group":"NOPE","x":0.5,"y":0.5,"scale":1.0},{"group":"DPAD","x":0.2,"y":0.8,"scale":1.0}]}"""
        val parsed = TouchLayoutSerializer.fromJson(rogue, default = baseline)
        assertEquals(0.2f, parsed.placements[GroupId.DPAD]!!.anchorX, 0.0001f)
    }

    @Test
    fun `missing groups in saved json are filled from default`() {
        val partial = """{"schemaVersion":1,"groups":[{"group":"FACE","x":0.5,"y":0.5,"scale":1.0}]}"""
        val parsed = TouchLayoutSerializer.fromJson(partial, default = baseline)
        // DPAD is missing in the saved JSON; should be merged from baseline
        assertNotNull(parsed.placements[GroupId.DPAD])
        assertEquals(0.10f, parsed.placements[GroupId.DPAD]!!.anchorX, 0.0001f)
        // FACE comes from the saved payload
        assertEquals(0.5f, parsed.placements[GroupId.FACE]!!.anchorX, 0.0001f)
    }

    @Test
    fun `malformed json falls back to default`() {
        val parsed = TouchLayoutSerializer.fromJson("not actually json", default = baseline)
        assertEquals(baseline.placements, parsed.placements)
    }

    @Test
    fun `empty json falls back to default`() {
        val parsed = TouchLayoutSerializer.fromJson("{}", default = baseline)
        assertEquals(baseline.placements, parsed.placements)
    }
}
