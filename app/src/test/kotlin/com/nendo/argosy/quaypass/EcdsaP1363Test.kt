package com.nendo.argosy.quaypass

import com.nendo.argosy.data.quaypass.EcdsaP1363
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class EcdsaP1363Test {

    private fun roundTrip(p1363: ByteArray) {
        val der = EcdsaP1363.toDer(p1363)
        assertArrayEquals(p1363, EcdsaP1363.fromDer(der, 32))
    }

    @Test
    fun `round trips a plain signature`() {
        roundTrip(ByteArray(64) { (it + 1).toByte() })
    }

    @Test
    fun `round trips components with the high bit set`() {
        val p1363 = ByteArray(64) { 0x10 }
        p1363[0] = 0x80.toByte()
        p1363[32] = 0xFF.toByte()
        roundTrip(p1363)
    }

    @Test
    fun `round trips components with leading zeros`() {
        val p1363 = ByteArray(64) { 0x00 }
        p1363[31] = 0x07
        p1363[63] = 0x09
        roundTrip(p1363)
    }
}
