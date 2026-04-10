package com.nendo.argosy.data.netplay

import com.nendo.argosy.data.social.ArgosSocialService
import io.mockk.mockk
import org.junit.Assert.assertNotNull
import org.junit.Test

class NetplayHandshakeTest {

    @Test
    fun canConstruct() {
        val handshake = NetplayHandshake(
            candidateGatherer = CandidateGatherer(),
            socialService = mockk<ArgosSocialService>(relaxed = true)
        )
        assertNotNull(handshake)
    }
}
