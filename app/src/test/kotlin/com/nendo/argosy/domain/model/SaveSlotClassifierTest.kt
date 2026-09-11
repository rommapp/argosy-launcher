package com.nendo.argosy.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveSlotClassifierTest {

    @Test
    fun `a null channel that is the latest save is the autosave slot`() {
        assertEquals(
            SaveSlotKind.AUTOSAVE,
            SaveSlotClassifier.kindOf(channelName = null, isLatest = true, isArchival = false)
        )
    }

    @Test
    fun `the literal autosave channel is the same slot as a null latest`() {
        assertEquals(
            SaveSlotClassifier.slotKeyOf(null, isLatest = true, isArchival = false),
            SaveSlotClassifier.slotKeyOf("autosave", isLatest = false, isArchival = false)
        )
    }

    @Test
    fun `autosave matching ignores case`() {
        assertEquals(
            SaveSlotKind.AUTOSAVE,
            SaveSlotClassifier.kindOf("AutoSave", isLatest = false, isArchival = false)
        )
    }

    @Test
    fun `a null channel that is not the latest is an archive`() {
        assertEquals(
            SaveSlotKind.ARCHIVE,
            SaveSlotClassifier.kindOf(channelName = null, isLatest = false, isArchival = false)
        )
    }

    @Test
    fun `an archival entry is an archive even when it carries a channel`() {
        assertEquals(
            SaveSlotKind.ARCHIVE,
            SaveSlotClassifier.kindOf("checkpoint", isLatest = false, isArchival = true)
        )
    }

    @Test
    fun `a named channel keeps its own slot`() {
        assertEquals(
            SaveSlotKind.NAMED,
            SaveSlotClassifier.kindOf("checkpoint", isLatest = false, isArchival = false)
        )
        assertEquals(
            "checkpoint",
            SaveSlotClassifier.slotKeyOf("checkpoint", isLatest = false, isArchival = false)
        )
    }

    @Test
    fun `an archive files under no slot`() {
        assertNull(SaveSlotClassifier.slotKeyOf(null, isLatest = false, isArchival = false))
    }

    @Test
    fun `a null active channel means the autosave slot is active`() {
        assertTrue(SaveSlotClassifier.isActiveSlot("autosave", activeChannel = null))
        assertFalse(SaveSlotClassifier.isActiveSlot("checkpoint", activeChannel = null))
    }

    @Test
    fun `a named active channel activates only that slot`() {
        assertTrue(SaveSlotClassifier.isActiveSlot("checkpoint", activeChannel = "checkpoint"))
        assertFalse(SaveSlotClassifier.isActiveSlot("autosave", activeChannel = "checkpoint"))
    }

    @Test
    fun `an archive slot is never the active one`() {
        assertFalse(SaveSlotClassifier.isActiveSlot(null, activeChannel = null))
    }
}
