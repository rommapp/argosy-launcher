package com.nendo.argosy.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Signing out consults [AccountPendingWork.blocksRemoval], not [AccountPendingWork.isEmpty].
 *
 * Queued social rows are telemetry with no destination once an account has no social login, and
 * a device that cannot reach the social backend keeps them forever, so counting them made
 * signing out impossible rather than merely delayed. Everything else is the account's own data
 * and still refuses.
 */
class AccountPendingWorkTest {

    private fun work(
        sync: Int = 0,
        saves: Int = 0,
        social: Int = 0,
        quayPass: Int = 0,
        downloads: Int = 0
    ) = AccountPendingWork(
        queuedSyncOperations = sync,
        savesAwaitingUpload = saves,
        queuedSocialEvents = social,
        queuedQuayPassReports = quayPass,
        unfinishedDownloads = downloads
    )

    @Test
    fun `queued social events alone do not block removal`() {
        val pending = work(social = 42)

        assertFalse(pending.blocksRemoval)
        assertFalse("the tally still reports them", pending.isEmpty)
    }

    @Test
    fun `unsent saves block removal`() {
        assertTrue(work(saves = 1).blocksRemoval)
    }

    @Test
    fun `queued sync operations block removal`() {
        assertTrue(work(sync = 1).blocksRemoval)
    }

    @Test
    fun `queued quaypass reports block removal`() {
        assertTrue(work(quayPass = 1).blocksRemoval)
    }

    @Test
    fun `unfinished downloads block removal`() {
        assertTrue(work(downloads = 1).blocksRemoval)
    }

    @Test
    fun `nothing queued blocks nothing`() {
        val pending = work()

        assertFalse(pending.blocksRemoval)
        assertTrue(pending.isEmpty)
    }

    @Test
    fun `social events are still named in the description so the discard is visible`() {
        assertTrue(work(social = 3).describe().orEmpty().contains("3 queued social events"))
    }
}
