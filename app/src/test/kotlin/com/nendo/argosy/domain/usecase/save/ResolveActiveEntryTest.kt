package com.nendo.argosy.domain.usecase.save

import com.nendo.argosy.domain.model.UnifiedSaveEntry
import com.nendo.argosy.domain.model.UnifiedSaveEntry.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * Locks down the single "which save is active" resolution over the unified cache+server view --
 * the heart of the #18 consolidation. All cases are pure (no cache/server access).
 */
class ResolveActiveEntryTest {

    private fun entry(
        timestampMillis: Long,
        channelName: String? = null,
        source: Source = Source.LOCAL,
        isHardcore: Boolean = false,
        isLatest: Boolean = false,
        isArchival: Boolean = false,
        isRollback: Boolean = false,
        localCacheId: Long? = timestampMillis,
        serverSaveId: Long? = null
    ) = UnifiedSaveEntry(
        localCacheId = localCacheId,
        serverSaveId = serverSaveId,
        timestamp = Instant.ofEpochMilli(timestampMillis),
        size = 100,
        channelName = channelName,
        source = source,
        isHardcore = isHardcore,
        isLatest = isLatest,
        isArchival = isArchival,
        isRollback = isRollback
    )

    @Test
    fun `no entries resolves to null`() {
        assertNull(resolveActiveEntry(emptyList(), activeChannel = null, activeSaveTimestamp = null))
    }

    @Test
    fun `pinned timestamp wins over channel and recency`() {
        val pinned = entry(timestampMillis = 1000, channelName = "slot1")
        val newer = entry(timestampMillis = 2000, isLatest = true)
        val active = resolveActiveEntry(
            listOf(newer, pinned),
            activeChannel = "autosave",
            activeSaveTimestamp = 1000
        )
        assertEquals(pinned, active)
    }

    @Test
    fun `pinned timestamp with no match falls through to recency`() {
        val a = entry(timestampMillis = 1000)
        val b = entry(timestampMillis = 3000)
        val active = resolveActiveEntry(
            listOf(a, b),
            activeChannel = null,
            activeSaveTimestamp = 9999
        )
        assertEquals(b, active)
    }

    @Test
    fun `named channel resolves to most recent entry in that channel`() {
        val old = entry(timestampMillis = 1000, channelName = "slot1")
        val new = entry(timestampMillis = 2000, channelName = "slot1")
        val other = entry(timestampMillis = 3000, channelName = "slot2")
        val active = resolveActiveEntry(
            listOf(old, new, other),
            activeChannel = "slot1",
            activeSaveTimestamp = null
        )
        assertEquals(new, active)
    }

    @Test
    fun `named channel with no entries resolves to null, not another channel's save`() {
        // An empty named channel means no active save. Falling back cross-channel would disagree
        // with SaveStateManager, which restores strictly within the channel and finds nothing.
        val otherChannel = entry(timestampMillis = 3000, channelName = "slot2", isHardcore = true)
        val unchannelled = entry(timestampMillis = 5000, isLatest = true)
        val active = resolveActiveEntry(
            listOf(otherChannel, unchannelled),
            activeChannel = "slot1",
            activeSaveTimestamp = null
        )
        assertNull(active)
    }

    @Test
    fun `literal autosave channel is not looked up as a named channel`() {
        // A named entry literally called "autosave" must NOT be selected; the coordinate means the
        // latest/null bucket (invariant A: activeSaveChannel is null OR the literal "autosave").
        val namedAutosave = entry(timestampMillis = 3000, channelName = "autosave")
        val latest = entry(timestampMillis = 1000, isLatest = true)
        val active = resolveActiveEntry(
            listOf(namedAutosave, latest),
            activeChannel = "autosave",
            activeSaveTimestamp = null
        )
        assertEquals(latest, active)
    }

    @Test
    fun `null channel prefers the explicit latest entry`() {
        val latest = entry(timestampMillis = 1000, isLatest = true)
        val newerDated = entry(timestampMillis = 5000, channelName = null)
        val active = resolveActiveEntry(
            listOf(newerDated, latest),
            activeChannel = null,
            activeSaveTimestamp = null
        )
        assertEquals(latest, active)
    }

    @Test
    fun `null channel with no latest uses most recent non-channel entry`() {
        val older = entry(timestampMillis = 1000)
        val newer = entry(timestampMillis = 4000)
        val channelled = entry(timestampMillis = 9000, channelName = "slot1")
        val active = resolveActiveEntry(
            listOf(older, newer, channelled),
            activeChannel = null,
            activeSaveTimestamp = null
        )
        assertEquals(newer, active)
    }

    @Test
    fun `archival and rollback entries are excluded from the recency fallback`() {
        val rollback = entry(timestampMillis = 9000, isRollback = true)
        val archival = entry(timestampMillis = 8000, isArchival = true)
        val real = entry(timestampMillis = 1000)
        val active = resolveActiveEntry(
            listOf(rollback, archival, real),
            activeChannel = null,
            activeSaveTimestamp = null
        )
        assertEquals(real, active)
    }

    @Test
    fun `a server-only save can be the active save`() {
        val serverOnly = entry(
            timestampMillis = 2000,
            source = Source.SERVER,
            isLatest = true,
            localCacheId = null,
            serverSaveId = 42
        )
        val active = resolveActiveEntry(
            listOf(serverOnly),
            activeChannel = null,
            activeSaveTimestamp = null
        )
        assertEquals(serverOnly, active)
    }
}
