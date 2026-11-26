package com.nendo.argosy.data.music.library

import com.nendo.argosy.data.local.dao.MusicDownloadDao
import com.nendo.argosy.data.local.dao.MusicDownloadServerRow
import com.nendo.argosy.data.local.entity.MusicDownloadEntity
import com.nendo.argosy.data.local.entity.RomMAccountEntity
import com.nendo.argosy.data.music.identity.MusicAccountReader
import com.nendo.argosy.domain.model.MusicSourceKind

object MusicDownloadOwnership {

    sealed interface Disposal {
        val serverKey: String

        data class Reassign(override val serverKey: String, val toOwnerKey: Long) : Disposal
        data class Drop(override val serverKey: String) : Disposal
    }

    fun plan(owned: List<MusicDownloadServerRow>, remaining: List<RomMAccountEntity>): List<Disposal> {
        val fallbackOwner = preferred(remaining)?.rommUserId ?: MusicAccountReader.NO_ACCOUNT
        return owned.map { row ->
            if (row.source != MusicSourceKind.ROMM.token || row.serverKey == MusicDownloadEntity.UNSCOPED_SERVER_KEY) {
                Disposal.Reassign(row.serverKey, fallbackOwner)
            } else {
                val heirs = remaining.filter {
                    MusicDownloadEntity.serverKeyOf(MusicSourceKind.ROMM.token, it.baseUrl) == row.serverKey
                }
                preferred(heirs)?.let { Disposal.Reassign(row.serverKey, it.rommUserId) }
                    ?: Disposal.Drop(row.serverKey)
            }
        }
    }

    suspend fun release(dao: MusicDownloadDao, ownerKey: Long, remaining: List<RomMAccountEntity>) {
        plan(dao.serversOwnedBy(ownerKey), remaining).forEach { disposal ->
            when (disposal) {
                is Disposal.Reassign -> dao.reassignOwner(disposal.serverKey, ownerKey, disposal.toOwnerKey)
                is Disposal.Drop -> dao.deleteForOwner(disposal.serverKey, ownerKey)
            }
        }
    }

    private fun preferred(accounts: List<RomMAccountEntity>): RomMAccountEntity? =
        accounts.firstOrNull { it.isActive } ?: accounts.firstOrNull()
}
