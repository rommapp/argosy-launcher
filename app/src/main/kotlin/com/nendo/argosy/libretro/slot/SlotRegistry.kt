package com.nendo.argosy.libretro.slot

import android.os.Messenger
import android.os.RemoteException
import android.os.Message
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class LiveSlot(
    val slotIndex: Int,
    val pid: Int,
    val taskId: Int,
    val gameId: Long,
    val variantFileId: Long?,
    val platformSlug: String?,
    val startedAtMillis: Long,
    val hardcore: Boolean
)

/**
 * The game slots holding a built-in game right now, as their processes registered them with
 * [SlotHostService]. Lives in Argosy's main process; slot processes never read it.
 */
@Singleton
class SlotRegistry @Inject constructor() {

    private val _slots = MutableStateFlow<Map<Int, LiveSlot>>(emptyMap())
    val slots: StateFlow<Map<Int, LiveSlot>> = _slots.asStateFlow()

    private val clients = ConcurrentHashMap<Int, Messenger>()

    fun put(slot: LiveSlot, client: Messenger) {
        clients[slot.slotIndex] = client
        _slots.update { it + (slot.slotIndex to slot) }
    }

    fun update(slotIndex: Int, transform: (LiveSlot) -> LiveSlot) {
        _slots.update { current -> current[slotIndex]?.let { current + (slotIndex to transform(it)) } ?: current }
    }

    fun remove(slotIndex: Int) {
        clients.remove(slotIndex)
        _slots.update { it - slotIndex }
    }

    fun client(slotIndex: Int): Messenger? = clients[slotIndex]

    fun usedUnits(): Int = _slots.value.values.sumOf { GameSlots.weightOf(it.platformSlug) }

    /**
     * The lowest slot no game holds, when the open games leave room for a [platformSlug] game;
     * null when they do not.
     */
    fun freeSlotFor(platformSlug: String?): Int? {
        if (usedUnits() + GameSlots.weightOf(platformSlug) > GameSlots.CAPACITY_UNITS) return null
        return (1..GameSlots.activities.size).firstOrNull { it !in _slots.value }
    }

    fun slotHolding(gameId: Long, variantFileId: Long?): LiveSlot? =
        _slots.value.values.firstOrNull { it.gameId == gameId && it.variantFileId == variantFileId }

    /**
     * Asks [slotIndex] to save and close. False when the slot is gone or the message could not be
     * delivered.
     */
    fun requestClose(slotIndex: Int): Boolean {
        val client = clients[slotIndex] ?: return false
        return try {
            client.send(Message.obtain(null, SlotProtocol.SAVE_AND_CLOSE))
            true
        } catch (e: RemoteException) {
            Logger.warn(TAG, "slot $slotIndex did not take the close request", e)
            false
        }
    }

    private companion object {
        const val TAG = "SlotRegistry"
    }
}
