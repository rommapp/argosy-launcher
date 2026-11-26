package com.nendo.argosy.data.music.library

import com.nendo.argosy.data.local.dao.MusicDownloadDao
import com.nendo.argosy.data.local.dao.MusicDownloadServerRow
import com.nendo.argosy.data.local.entity.MusicDownloadEntity
import com.nendo.argosy.data.local.entity.RomMAccountEntity
import com.nendo.argosy.data.music.identity.MusicAccountReader
import com.nendo.argosy.data.music.library.MusicDownloadOwnership.Disposal
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class MusicDownloadOwnershipTest {

    private val serverA = MusicDownloadEntity.serverKeyOf("romm", "https://romm.a.example")!!
    private val serverB = MusicDownloadEntity.serverKeyOf("romm", "https://romm.b.example")!!
    private val navidromeServer = MusicDownloadEntity.serverKeyOf("navidrome", "https://nd.example")!!

    private fun account(userId: Long, baseUrl: String, active: Boolean = false) = RomMAccountEntity(
        id = userId,
        rommUserId = userId,
        username = "user$userId",
        baseUrl = baseUrl,
        token = "token",
        isActive = active,
        lastLoginAt = Instant.EPOCH,
        createdAt = Instant.EPOCH
    )

    @Test
    fun `romm rows pass to another account on the same server`() {
        val plan = MusicDownloadOwnership.plan(
            listOf(MusicDownloadServerRow("romm", serverA)),
            listOf(account(2L, "https://romm.b.example"), account(3L, "http://ROMM.A.example/"))
        )
        assertEquals(listOf(Disposal.Reassign(serverA, 3L)), plan)
    }

    @Test
    fun `romm rows are dropped when no remaining account shares the server`() {
        val plan = MusicDownloadOwnership.plan(
            listOf(MusicDownloadServerRow("romm", serverA)),
            listOf(account(2L, "https://romm.b.example", active = true))
        )
        assertEquals(listOf(Disposal.Drop(serverA)), plan)
    }

    @Test
    fun `the active account wins among same-server heirs`() {
        val plan = MusicDownloadOwnership.plan(
            listOf(MusicDownloadServerRow("romm", serverA)),
            listOf(account(2L, "https://romm.a.example"), account(3L, "https://romm.a.example", active = true))
        )
        assertEquals(listOf(Disposal.Reassign(serverA, 3L)), plan)
    }

    @Test
    fun `navidrome and unscoped rows are never dropped`() {
        val plan = MusicDownloadOwnership.plan(
            listOf(
                MusicDownloadServerRow("navidrome", navidromeServer),
                MusicDownloadServerRow("romm", MusicDownloadEntity.UNSCOPED_SERVER_KEY)
            ),
            emptyList()
        )
        assertEquals(
            listOf(
                Disposal.Reassign(navidromeServer, MusicAccountReader.NO_ACCOUNT),
                Disposal.Reassign(MusicDownloadEntity.UNSCOPED_SERVER_KEY, MusicAccountReader.NO_ACCOUNT)
            ),
            plan
        )
    }

    @Test
    fun `release reassigns and deletes only the removed owner's rows per server`() = runTest {
        val dao = mockk<MusicDownloadDao>(relaxed = true)
        coEvery { dao.serversOwnedBy(1L) } returns listOf(
            MusicDownloadServerRow("romm", serverA),
            MusicDownloadServerRow("romm", serverB)
        )

        MusicDownloadOwnership.release(dao, 1L, listOf(account(3L, "https://romm.a.example")))

        coVerify(exactly = 1) { dao.reassignOwner(serverA, 1L, 3L) }
        coVerify(exactly = 1) { dao.deleteForOwner(serverB, 1L) }
        coVerify(exactly = 0) { dao.deleteForOwner(serverA, any()) }
        coVerify(exactly = 0) { dao.reassignOwner(serverB, any(), any()) }
    }
}
