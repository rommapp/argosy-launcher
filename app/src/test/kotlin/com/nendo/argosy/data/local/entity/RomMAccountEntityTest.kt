package com.nendo.argosy.data.local.entity

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class RomMAccountEntityTest {

    private fun account(baseUrl: String, lanBaseUrl: String?) = RomMAccountEntity(
        rommUserId = 7,
        username = "player",
        baseUrl = baseUrl,
        lanBaseUrl = lanBaseUrl,
        token = "token",
        lastLoginAt = Instant.EPOCH,
        createdAt = Instant.EPOCH
    )

    @Test
    fun `lan address is tried before wan`() {
        val candidates = account("https://romm.example.com/", "http://192.168.1.10:8080/").addressCandidates()

        assertEquals(listOf("http://192.168.1.10:8080/", "https://romm.example.com/"), candidates)
    }

    @Test
    fun `a row without a lan address yields only wan`() {
        assertEquals(listOf("https://romm.example.com/"), account("https://romm.example.com/", null).addressCandidates())
    }

    @Test
    fun `blank and duplicate addresses are dropped`() {
        assertEquals(listOf("https://romm.example.com/"), account("https://romm.example.com/", "   ").addressCandidates())
        assertEquals(
            listOf("https://romm.example.com/"),
            account("https://romm.example.com/", "https://romm.example.com/").addressCandidates()
        )
    }
}
