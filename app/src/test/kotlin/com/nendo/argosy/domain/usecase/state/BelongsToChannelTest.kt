package com.nendo.argosy.domain.usecase.state

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BelongsToChannelTest {

    @Test
    fun `default channel accepts a state with no channel`() {
        assertTrue(belongsToChannel(stateChannel = null, activeChannel = null))
    }

    @Test
    fun `default channel treats empty and absent as the same`() {
        assertTrue(belongsToChannel(stateChannel = "", activeChannel = null))
        assertTrue(belongsToChannel(stateChannel = null, activeChannel = ""))
    }

    @Test
    fun `a named channel accepts its own states`() {
        assertTrue(belongsToChannel(stateChannel = "Speedrun", activeChannel = "Speedrun"))
    }

    @Test
    fun `channel names round-tripped through a file name compare without case`() {
        assertTrue(belongsToChannel(stateChannel = "speedrun", activeChannel = "Speedrun"))
    }

    @Test
    fun `a named channel refuses another channel's states`() {
        assertFalse(belongsToChannel(stateChannel = "Speedrun", activeChannel = "Casual"))
    }

    @Test
    fun `the default channel refuses a named channel's states`() {
        assertFalse(belongsToChannel(stateChannel = "Speedrun", activeChannel = null))
    }

    @Test
    fun `a named channel refuses the default channel's states`() {
        assertFalse(belongsToChannel(stateChannel = null, activeChannel = "Speedrun"))
    }
}
