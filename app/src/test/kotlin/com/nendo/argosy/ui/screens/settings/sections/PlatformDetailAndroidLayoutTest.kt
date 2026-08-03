package com.nendo.argosy.ui.screens.settings.sections

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformDetailAndroidLayoutTest {

    private fun rows(isAndroid: Boolean): List<PlatformDetailItem> {
        val visibility = PlatformDetailVisibility(
            isAndroid = isAndroid,
            showCore = true,
            showExtension = true,
            showSavePath = true,
            showStatePath = true,
            hasDownloads = true,
            hasBios = true,
            biosMissing = true,
            biosDownloaded = true,
            canDistribute = true
        )
        return PlatformDetailItem.ALL.filter { it.visibleWhen(visibility) }
    }

    @Test
    fun `android offers the app scan and not the file scan`() {
        val android = rows(isAndroid = true)
        assertTrue(android.contains(PlatformDetailItem.ScanApps))
        assertFalse(android.contains(PlatformDetailItem.ScanFiles))
    }

    @Test
    fun `every other platform offers the file scan and not the app scan`() {
        val other = rows(isAndroid = false)
        assertTrue(other.contains(PlatformDetailItem.ScanFiles))
        assertFalse(other.contains(PlatformDetailItem.ScanApps))
    }

    @Test
    fun `android never offers an emulator, which nothing would ever read`() {
        assertFalse(rows(isAndroid = true).contains(PlatformDetailItem.Emulator))
        assertTrue(rows(isAndroid = false).contains(PlatformDetailItem.Emulator))
    }

    @Test
    fun `android hides every row that acts on rom files or bios`() {
        val android = rows(isAndroid = true)
        val meaningless = listOf(
            PlatformDetailItem.Core,
            PlatformDetailItem.Extension,
            PlatformDetailItem.LaunchArgs,
            PlatformDetailItem.RomPath,
            PlatformDetailItem.SavePath,
            PlatformDetailItem.StatePath,
            PlatformDetailItem.DownloadDefaults,
            PlatformDetailItem.RemoveFiles,
            PlatformDetailItem.BiosDownload,
            PlatformDetailItem.BiosInstall,
            PlatformDetailItem.BiosCopy
        )
        for (item in meaningless) {
            assertFalse(item.key, android.contains(item))
        }
    }

    @Test
    fun `android keeps sync, which is real when romm serves an android platform`() {
        val android = rows(isAndroid = true)
        assertTrue(android.contains(PlatformDetailItem.SyncToggle))
        assertTrue(android.contains(PlatformDetailItem.SyncNow))
    }

    @Test
    fun `android shows no emulator or bios headers`() {
        val headerKeys = rows(isAndroid = true)
            .filterIsInstance<PlatformDetailItem.Header>()
            .map { it.key }
        assertFalse(headerKeys.contains("header_emulator"))
        assertFalse(headerKeys.contains("header_bios"))
    }
}
