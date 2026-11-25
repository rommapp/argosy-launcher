package com.nendo.argosy.domain.usecase.state

import com.nendo.argosy.data.remote.romm.RomMState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class SupersededServerStatesTest {

    private fun state(id: Long, emulator: String?, slot: Int, writtenAt: Long) =
        Triple(
            RomMState(id = id, romId = 1L, userId = 1L, emulator = emulator, fileName = "s$id", updatedAt = ""),
            slot,
            Instant.ofEpochSecond(writtenAt)
        )

    private fun superseded(vararg rows: Triple<RomMState, Int, Instant>): List<Long> {
        val bySlot = rows.associate { it.first.id to it.second }
        val byTime = rows.associate { it.first.id to it.third }
        return supersededServerStates(
            states = rows.map { it.first },
            slotOf = { bySlot.getValue(it.id) },
            writtenAt = { byTime[it.id] }
        ).sorted()
    }

    @Test
    fun `the older state of one core and slot is superseded`() {
        assertEquals(listOf(1L), superseded(state(1, "swanstation", -1, 10), state(2, "swanstation", -1, 20)))
    }

    @Test
    fun `states of different cores on the same slot never supersede each other`() {
        assertEquals(
            emptyList<Long>(),
            superseded(state(1, "mednafen_psx_hw", -1, 30), state(2, "swanstation", -1, 10))
        )
    }

    @Test
    fun `the newer core state survives whichever order the server lists them`() {
        assertEquals(listOf(1L), superseded(state(2, "swanstation", -1, 20), state(1, "swanstation", -1, 10)))
    }

    @Test
    fun `different slots of one core are independent`() {
        assertEquals(emptyList<Long>(), superseded(state(1, "swanstation", 1, 10), state(2, "swanstation", 2, 20)))
    }

    @Test
    fun `untagged states group together`() {
        assertEquals(listOf(1L), superseded(state(1, null, -1, 10), state(2, null, -1, 20)))
    }
}
