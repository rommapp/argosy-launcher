package com.nendo.argosy.util

import org.junit.Assert.assertEquals
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
}
