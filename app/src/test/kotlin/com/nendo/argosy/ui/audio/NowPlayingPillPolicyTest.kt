package com.nendo.argosy.ui.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingPillPolicyTest {

    @Test
    fun `nothing is suppressed at first`() {
        assertFalse(NowPlayingPillPolicy().suppressed.value)
    }

    @Test
    fun `one owner suppresses until it releases`() {
        val policy = NowPlayingPillPolicy()

        policy.setSuppressed("resume_card", true)
        assertTrue(policy.suppressed.value)

        policy.setSuppressed("resume_card", false)
        assertFalse(policy.suppressed.value)
    }

    @Test
    fun `two holds by one owner need two releases`() {
        val policy = NowPlayingPillPolicy()

        policy.setSuppressed("resume_card", true)
        policy.setSuppressed("resume_card", true)
        policy.setSuppressed("resume_card", false)
        assertTrue(policy.suppressed.value)

        policy.setSuppressed("resume_card", false)
        assertFalse(policy.suppressed.value)
    }

    @Test
    fun `one owner releasing leaves another owner's hold in place`() {
        val policy = NowPlayingPillPolicy()

        policy.setSuppressed("resume_card", true)
        policy.setSuppressed("other", true)
        policy.setSuppressed("resume_card", false)

        assertTrue(policy.suppressed.value)
    }

    @Test
    fun `a release without a hold never goes negative`() {
        val policy = NowPlayingPillPolicy()

        policy.setSuppressed("resume_card", false)
        policy.setSuppressed("resume_card", true)

        assertTrue(policy.suppressed.value)
    }
}
