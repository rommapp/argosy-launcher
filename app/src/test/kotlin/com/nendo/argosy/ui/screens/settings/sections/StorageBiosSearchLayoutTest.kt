package com.nendo.argosy.ui.screens.settings.sections

import com.nendo.argosy.ui.screens.settings.BiosFirmwareItem
import com.nendo.argosy.ui.screens.settings.BiosPlatformGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageBiosSearchLayoutTest {

    private fun storageState(locked: Boolean) =
        StorageLayoutState(steamVisible = false, mediaVisible = false, remoteInstallsLocked = locked)

    @Test
    fun `a locked remote installs row is shown without taking focus`() {
        val state = storageState(locked = true)

        assertTrue(storageLayout.visibleItems(state).contains(StorageItem.RemoteInstallsLocked))
        assertFalse(storageLayout.visibleItems(state).contains(StorageItem.RemoteInstalls))
        assertFalse(storageLayout.focusableItems(state).contains(StorageItem.RemoteInstallsLocked))
    }

    @Test
    fun `an unlocked remote installs row takes focus and hides the locked notice`() {
        val state = storageState(locked = false)

        assertTrue(storageLayout.focusableItems(state).contains(StorageItem.RemoteInstalls))
        assertFalse(storageLayout.visibleItems(state).contains(StorageItem.RemoteInstallsLocked))
    }

    @Test
    fun `locking remote installs drops exactly one focus stop`() {
        assertEquals(
            storageLayout.focusableItems(storageState(locked = false)).size - 1,
            storageLayout.focusableItems(storageState(locked = true)).size
        )
    }

    @Test
    fun `bios settings rows keep their focus index once platform rows exist`() {
        val firmware = BiosFirmwareItem(
            id = 1,
            rommId = 1,
            platformSlug = "psx",
            fileName = "scph1001.bin",
            fileSizeBytes = 524288,
            isDownloaded = false
        )
        val groups = listOf(
            BiosPlatformGroup("psx", "PlayStation", totalFiles = 1, downloadedFiles = 0, firmwareItems = listOf(firmware))
        )

        listOf(-1, 0).forEach { expanded ->
            val live = createBiosLayoutInfo(groups, expanded)
            listOf(BiosItem.Summary, BiosItem.BiosPath).forEach { row ->
                assertEquals(
                    biosSettingsLayout.focusIndexOf(row, Unit),
                    live.layout.focusIndexOf(row, Unit)
                )
            }
        }
    }

    @Test
    fun `the bios search layout holds only labelled rows`() {
        assertEquals(
            listOf<BiosItem>(BiosItem.Summary, BiosItem.BiosPath),
            biosSettingsLayout.focusableItems(Unit)
        )
    }
}
