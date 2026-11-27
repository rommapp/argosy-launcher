package com.nendo.argosy.data.mod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SwitchModCatalogTest {
    private val title = "0100F2C0115B6000"
    private val root = "/eden"
    private fun receipt(titleId: String = title, id: String = "removed-file") = ModReceipt(titleId, root, id, "archive", emptyList())

    @Test
    fun `installed receipts remain removable after their server file row disappears`() {
        val record = StoredModPackage(receipt(), null)
        val result = SwitchModCatalog.merge(emptyList(), listOf(record), root, title).single()
        assertEquals(title, result.titleId)
        assertTrue(result.installed)
        assertFalse(result.needsRecovery)
        assertNull(result.archivePath)
    }

    @Test
    fun `a first install journal is recoverable from another game's mods screen`() {
        val record = StoredModPackage(null, receipt("01007EF00011E000"))
        val result = SwitchModCatalog.merge(emptyList(), listOf(record), root, title).single()
        assertEquals("01007EF00011E000", result.titleId)
        assertFalse(result.installed)
        assertTrue(result.needsRecovery)
        assertNull(result.archivePath)
    }

    @Test
    fun `a different resolved emulator root cannot expose mutation actions`() {
        val record = StoredModPackage(receipt(), receipt())
        assertTrue(SwitchModCatalog.merge(emptyList(), listOf(record), "/another-eden", title).isEmpty())
    }
}
