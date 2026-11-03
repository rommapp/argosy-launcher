package com.nendo.argosy.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class PrimaryUnderAnchorTest {

    @Test
    fun `an anchor already at a subfolder primary is kept as is`() {
        assertEquals(
            "/saves/fbneo/mslug.fs",
            primaryUnderAnchor("/saves/fbneo/mslug.fs", "/saves/fbneo", "fbneo/mslug.fs")
        )
    }

    @Test
    fun `resolving twice lands on the same subfolder primary`() {
        val once = primaryUnderAnchor("/saves/mslug.fs", "/saves", "fbneo/mslug.fs")
        val twice = primaryUnderAnchor(once, once.substringBeforeLast('/'), "fbneo/mslug.fs")

        assertEquals("/saves/fbneo/mslug.fs", once)
        assertEquals(once, twice)
    }

    @Test
    fun `a stale archive-named anchor moves to the extracted entry primary`() {
        assertEquals(
            "/saves/Crystal (USA).srm",
            primaryUnderAnchor("/saves/Crystal.srm", "/saves", "Crystal (USA).srm")
        )
    }

    @Test
    fun `an anchor at the flat primary is kept as is`() {
        assertEquals(
            "/saves/Crystal (USA).srm",
            primaryUnderAnchor("/saves/Crystal (USA).srm", "/saves", "Crystal (USA).srm")
        )
    }
}
