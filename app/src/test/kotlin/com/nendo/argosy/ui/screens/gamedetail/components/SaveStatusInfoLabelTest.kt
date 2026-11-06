package com.nendo.argosy.ui.screens.gamedetail.components

import com.nendo.argosy.data.repository.SaveSyncApiClient
import org.junit.Assert.assertEquals
import org.junit.Test

class SaveStatusInfoLabelTest {

    private fun labelFor(channelName: String?) =
        SaveStatusInfo(SaveSyncStatus.SYNCED, channelName, activeSaveTimestamp = 1L, lastSyncTime = null).displayLabel

    @Test
    fun `the stored autosave slot shows its name as stored`() {
        assertEquals(SaveSyncApiClient.AUTOSAVE_SLOT_NAME, labelFor(SaveSyncApiClient.AUTOSAVE_SLOT_NAME))
    }

    @Test
    fun `no slot name shows the slot the save syncs to`() {
        assertEquals(SaveSyncApiClient.AUTOSAVE_SLOT_NAME, labelFor(null))
    }

    @Test
    fun `a named slot shows its own name without changing its case`() {
        assertEquals("speedRun", labelFor("speedRun"))
    }
}
