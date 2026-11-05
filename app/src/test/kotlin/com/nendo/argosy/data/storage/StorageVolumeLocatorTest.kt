package com.nendo.argosy.data.storage

import com.nendo.argosy.core.storage.StorageVolumeType
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.domain.usecase.storage.GameStorageBreakdown
import com.nendo.argosy.domain.usecase.storage.GameStorageFile
import com.nendo.argosy.domain.usecase.storage.GameStorageLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StorageVolumeLocatorTest {

    private val internal = volume("/storage/emulated/0", StorageVolumeType.INTERNAL)
    private val sd = volume("/storage/75D7-DC5F", StorageVolumeType.SD_CARD)
    private val volumes = listOf(internal, sd)

    @Test
    fun `a path on a volume matches that volume`() {
        assertEquals(sd.key, matchVolumeKey("/storage/75D7-DC5F/ROMs/snes/game.sfc", volumes))
        assertEquals(internal.key, matchVolumeKey("/storage/emulated/0/Download/game.zip", volumes))
    }

    @Test
    fun `a sibling directory sharing a prefix does not match`() {
        assertNull(matchVolumeKey("/storage/75D7-DC5F-other/game.sfc", volumes))
    }

    @Test
    fun `a game file on no volume counts as unknown, not internal`() {
        assertEquals(UNKNOWN_LOCATION_KEY, gameFileVolumeKey("/mnt/media_rw/75D7-DC5F/game.sfc", volumes, internal.key))
    }

    @Test
    fun `a game file in app-private data counts as internal`() {
        assertEquals(internal.key, gameFileVolumeKey("/data/user/0/com.nendo.argosy/files/game.chd", volumes, internal.key))
    }

    @Test
    fun `a game on one volume reports that volume`() {
        assertEquals(GameStorageLocation.REMOVABLE, game(GameStorageLocation.REMOVABLE, GameStorageLocation.REMOVABLE).location)
    }

    @Test
    fun `a game split across volumes reports mixed`() {
        assertEquals(GameStorageLocation.MIXED, game(GameStorageLocation.INTERNAL, GameStorageLocation.REMOVABLE).location)
    }

    @Test
    fun `any file in an unknown location makes the game unknown`() {
        assertEquals(GameStorageLocation.UNKNOWN, game(GameStorageLocation.REMOVABLE, GameStorageLocation.UNKNOWN).location)
    }

    private fun game(vararg locations: GameStorageLocation) = GameStorageBreakdown(
        gameId = 1,
        title = "Game",
        source = GameSource.ROMM_SYNCED,
        buckets = emptyList(),
        totalBytes = 0,
        unsyncedSaves = 0,
        files = locations.mapIndexed { index, location -> GameStorageFile("/f$index", 1, location) }
    )

    private fun volume(key: String, type: StorageVolumeType) = StorageVolumeInfo(
        key = key,
        displayName = key,
        type = type,
        totalBytes = 1,
        availableBytes = 0
    )
}
