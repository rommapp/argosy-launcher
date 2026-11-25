package com.nendo.argosy.data.remote.navidrome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavidromeAuthTest {

    @Test
    fun `token is md5 of password and salt for the subsonic documented vector`() {
        assertEquals("26719a1196d2a940705a59634eb18eab", NavidromeAuth.token("sesame", "c19b2d"))
    }

    @Test
    fun `token of empty input is the md5 of the empty string`() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", NavidromeAuth.token("", ""))
    }

    @Test
    fun `auth params carry user, token, salt, version, client and json format but never the password`() {
        val params = NavidromeAuth.authParams("alice", "sesame", "c19b2d")

        assertEquals(
            listOf(
                "u" to "alice",
                "t" to "26719a1196d2a940705a59634eb18eab",
                "s" to "c19b2d",
                "v" to "1.16.1",
                "c" to "argosy",
                "f" to "json"
            ),
            params
        )
        assertTrue(params.none { it.second == "sesame" })
        assertTrue(params.none { it.first == "p" })
    }

    @Test
    fun `each salt is fresh lowercase hex`() {
        val first = NavidromeAuth.newSalt()
        val second = NavidromeAuth.newSalt()

        assertNotEquals(first, second)
        assertTrue(first.matches(Regex("[0-9a-f]{16}")))
    }
}
