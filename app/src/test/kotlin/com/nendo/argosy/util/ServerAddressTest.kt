package com.nendo.argosy.util

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ServerAddressTest {

    @Test
    fun `bare domain gains https`() {
        assertEquals("https://navi.example.com", ServerAddress.withDefaultScheme("navi.example.com"))
    }

    @Test
    fun `domain with port and path keeps them`() {
        assertEquals("https://example.com:4533/music", ServerAddress.withDefaultScheme(" example.com:4533/music "))
    }

    @Test
    fun `ipv4 gains http`() {
        assertEquals("http://192.168.1.20:4533", ServerAddress.withDefaultScheme("192.168.1.20:4533"))
    }

    @Test
    fun `localhost and bracketed ipv6 gain http`() {
        assertEquals("http://localhost:8096", ServerAddress.withDefaultScheme("localhost:8096"))
        assertEquals("http://[fe80::1]:4533", ServerAddress.withDefaultScheme("[fe80::1]:4533"))
    }

    @Test
    fun `existing scheme is kept`() {
        assertEquals("http://navi.example.com", ServerAddress.withDefaultScheme("http://navi.example.com"))
        assertEquals("HTTPS://x.org", ServerAddress.withDefaultScheme("HTTPS://x.org"))
    }

    @Test
    fun `blank stays blank`() {
        assertEquals("", ServerAddress.withDefaultScheme("   "))
    }

    @Test
    fun `a bare domain tries https then http`() {
        assertEquals(listOf("https://x.org", "http://x.org"), ServerAddress.candidates("x.org"))
    }

    @Test
    fun `a bare ip tries http then https`() {
        assertEquals(listOf("http://10.0.0.2:8096", "https://10.0.0.2:8096"), ServerAddress.candidates("10.0.0.2:8096"))
    }

    @Test
    fun `a typed scheme is the only candidate`() {
        assertEquals(listOf("http://x.org"), ServerAddress.candidates("http://x.org"))
    }

    @Test
    fun `resolve takes https when the server answers there`() = runBlocking {
        assertEquals("https://x.org", ServerAddress.resolve("x.org") { true })
    }

    @Test
    fun `resolve falls back to http when https does not answer`() = runBlocking {
        assertEquals("http://x.org", ServerAddress.resolve("x.org") { it.startsWith("http://") })
    }

    @Test
    fun `resolve keeps the first scheme when nothing answers`() = runBlocking {
        assertEquals("https://x.org", ServerAddress.resolve("x.org") { false })
    }

    @Test
    fun `resolve never probes a typed scheme`() = runBlocking {
        var probed = false
        assertEquals("http://x.org", ServerAddress.resolve("http://x.org") { probed = true; true })
        assertFalse(probed)
    }
}
