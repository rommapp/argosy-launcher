package com.nendo.argosy.quaypass

import com.nendo.argosy.data.local.entity.QuayPassOwnedPartEntity
import com.nendo.argosy.data.quaypass.QuayPassPurchaseReconciliation
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class QuayPassPurchaseReconciliationTest {

    private fun part(key: String, at: Long) =
        QuayPassOwnedPartEntity(partKey = key, acquiredAt = Instant.ofEpochSecond(at), synced = false)

    @Test
    fun `equipped parts replay before unequipped`() {
        val parts = listOf(
            part("hair-5", at = 100),
            part("hat-2", at = 50),
            part("glasses-1", at = 200)
        )
        val ordered = QuayPassPurchaseReconciliation.orderForReplay(parts, equipped = setOf("glasses-1"))
        assertEquals("glasses-1", ordered.first().partKey)
    }

    @Test
    fun `within a group the oldest purchase wins`() {
        val parts = listOf(
            part("hair-5", at = 300),
            part("hat-2", at = 100),
            part("glasses-1", at = 200)
        )
        val ordered = QuayPassPurchaseReconciliation.orderForReplay(parts, equipped = emptySet())
        assertEquals(listOf("hat-2", "glasses-1", "hair-5"), ordered.map { it.partKey })
    }

    @Test
    fun `equipped ordering takes precedence over time`() {
        val parts = listOf(
            part("hair-5", at = 10),
            part("hat-2", at = 999)
        )
        val ordered = QuayPassPurchaseReconciliation.orderForReplay(parts, equipped = setOf("hat-2"))
        assertEquals(listOf("hat-2", "hair-5"), ordered.map { it.partKey })
    }
}
