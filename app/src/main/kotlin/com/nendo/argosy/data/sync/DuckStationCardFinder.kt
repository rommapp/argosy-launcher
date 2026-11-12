package com.nendo.argosy.data.sync

import com.nendo.argosy.data.emulator.StateSerialResolver
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.util.Logger
import com.nendo.sigil.Sigil
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DuckStationCardFinder @Inject constructor(
    private val fal: FileAccessLayer,
    private val stateSerialResolver: StateSerialResolver
) {
    /**
     * The slot 1 card in [folders] whose saves carry one of the game's disc serials as their
     * product code, or null when no card or more than one card does.
     */
    suspend fun ownedCard(gameId: Long, folders: List<String>): String? {
        val serials = stateSerialResolver.serialsFor(gameId, DUCKSTATION_ID)
        if (serials.isEmpty()) return null
        val fileAccess = FalSigilFileAccess(fal)
        val owned = folders.flatMap { folder ->
            fal.listFiles(folder).orEmpty()
                .filter { it.isFile && it.name.endsWith(SLOT_ONE_SUFFIX, ignoreCase = true) }
                .map { it.path }
        }.distinct().filter { card ->
            fal.prepareSaveAccess(card)
            val owners = runCatching { Sigil.listCard(card, fileAccess).entries.map { it.ownerId } }
                .onFailure { Logger.warn(TAG, "Could not list card $card", it) }
                .getOrDefault(emptyList())
            owners.any { owner -> serials.any { it.equals(owner, ignoreCase = true) } }
        }
        return when (owned.size) {
            1 -> owned.single()
            0 -> null
            else -> {
                Logger.info(TAG, "Several cards hold saves of game $gameId ($serials): $owned; none chosen")
                null
            }
        }
    }

    private companion object {
        const val TAG = "DuckStationCardFinder"
        const val DUCKSTATION_ID = "duckstation"
        const val SLOT_ONE_SUFFIX = "_1.mcd"
    }
}
