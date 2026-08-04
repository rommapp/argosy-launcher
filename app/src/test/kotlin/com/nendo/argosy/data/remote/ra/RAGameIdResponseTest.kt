package com.nendo.argosy.data.remote.ra

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the `dorequest.php?r=gameid` response shape to what RetroAchievements actually sends.
 *
 * The bodies below are copied verbatim from the vendored rcheevos fixtures in
 * `libretrodroid/src/main/cpp/rcheevos/test/rapi/test_rc_api_runtime.c`, which is the
 * authority for Connect wire shapes. This endpoint was read as a bare number for five months
 * because the shape was inferred rather than read from there, and a RomM-supplied id masked it.
 * A field renamed or recased here fails this test rather than silently deserialising to null.
 */
class RAGameIdResponseTest {

    private val adapter = Moshi.Builder()
        .build()
        .adapter(RAGameIdResponse::class.java)

    @Test
    fun `a registered hash carries the game id`() {
        val parsed = adapter.fromJson("""{"Success":true,"GameID":1446}""")!!
        assertTrue(parsed.success)
        assertEquals(1446L, parsed.gameId)
    }

    @Test
    fun `a known hash with no game reports zero rather than omitting the field`() {
        val parsed = adapter.fromJson("""{"Success":true,"GameID":0}""")!!
        assertTrue(parsed.success)
        assertEquals(0L, parsed.gameId)
    }

    @Test
    fun `a failure carries the error and no id`() {
        val parsed = adapter.fromJson("""{"Success":false,"Error":"Unknown game"}""")!!
        assertFalse(parsed.success)
        assertEquals("Unknown game", parsed.error)
        assertNull(parsed.gameId)
    }

    @Test
    fun `an absent game id is null rather than a parse failure`() {
        val parsed = adapter.fromJson("""{"Success":true}""")!!
        assertTrue(parsed.success)
        assertNull(parsed.gameId)
    }

    @Test
    fun `a bare number is not a valid response`() {
        val threw = runCatching { adapter.fromJson("1446") }.isFailure
        assertTrue("the shape this endpoint was wrongly assumed to have", threw)
    }
}
