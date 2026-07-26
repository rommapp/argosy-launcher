package com.nendo.argosy.quaypass

import com.nendo.argosy.data.quaypass.QuayPassCredentialBundle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * Golden credential-bundle vectors. These fixed bytes pin the cross-repo
 * (device <-> server) wire layout of the server-signed credential bundle for
 * both key algorithms; the Go server verifies the same base64. The vectors are
 * also published as app/src/test/resources/quaypass/credential-golden-vectors.json.
 * Regenerating them (see git history for the deterministic generator) is a
 * deliberate contract change that must be mirrored on the server.
 */
class QuayPassCredentialVectorTest {

    private val serverPubkey = decode(SERVER_PUBKEY_B64)

    private val accountId = UUID.fromString("11111111-2222-3333-4444-555555555555")
    private val installId = UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa")
    private val issuedAt = Instant.ofEpochSecond(1_700_000_000L)
    private val expiresAt = Instant.ofEpochSecond(1_701_209_600L)

    @After
    fun tearDown() {
        QuayPassCredentialBundle.trustedServerPubKeysOverride = null
    }

    @Test
    fun `ed25519 golden vector parses to the pinned fields`() {
        assertVector(ED25519_BUNDLE_B64, QuayPassCredentialBundle.ALG_ED25519)
    }

    @Test
    fun `ec p256 golden vector parses to the pinned fields`() {
        assertVector(EC_P256_BUNDLE_B64, QuayPassCredentialBundle.ALG_EC_P256)
    }

    @Test
    fun `a foreign server key rejects the vector`() {
        QuayPassCredentialBundle.trustedServerPubKeysOverride = listOf(ByteArray(32))
        assertEquals(null, QuayPassCredentialBundle.parseAndVerifyBase64(ED25519_BUNDLE_B64))
    }

    private fun assertVector(bundleB64: String, expectedAlg: Int) {
        QuayPassCredentialBundle.trustedServerPubKeysOverride = listOf(serverPubkey)
        val parsed = QuayPassCredentialBundle.parseAndVerify(decode(bundleB64))
        assertNotNull("golden vector must verify and parse", parsed)
        parsed!!
        assertEquals(QuayPassCredentialBundle.VERSION_V1, parsed.version)
        assertEquals(accountId, parsed.accountId)
        assertEquals(installId, parsed.clientInstallId)
        assertEquals(expectedAlg, parsed.pubkeyAlg)
        assertEquals(issuedAt, parsed.issuedAt)
        assertEquals(expiresAt, parsed.expiresAt)
    }

    private fun decode(b64: String): ByteArray = Base64.getDecoder().decode(b64)

    companion object {
        private const val SERVER_PUBKEY_B64 =
            "MCowBQYDK2VwAyEAGTrDaZjU7j1nfC4duIILyS4PootiYVSCdJHRBj71OVk="

        private const val ED25519_BUNDLE_B64 =
            "AREREREiIjMzRERVVVVVVVVmZmZmd3eIiJmZqqqqqqqqAAAsMCowBQYDK2VwAyEAj5YZmR2HtBbRnZo5" +
                "7lkCyuoBDf79eLX2r3qHVXuYxdAAAAAAZVPxAAAAAABlZmYANRhiHuIQfdTiS4kN4P490ItBZ0/Whf" +
                "YmUqfS2XtncQg8YEdmzR1N/pK6bvmwI+gxJNR68KvcuXxZIoA3rF3hAw=="

        private const val EC_P256_BUNDLE_B64 =
            "AREREREiIjMzRERVVVVVVVVmZmZmd3eIiJmZqqqqqqqqAQBbMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcD" +
                "QgAEZ3EzxBKde9jwwr8kDi/o0ngTXNbWBt4sbNsAocMEopQ6mQX0uMNZN2lqlvgF3O5fDDjqlTGNeZ" +
                "p7BuErcluMOQAAAABlU/EAAAAAAGVmZgDrZX8wO8FX6EMfHnKG4KSHiVh/ccc77wa7S9nA8cSShSd93" +
                "Fxh6XkPiqcOTUFXrm2FctNwF27UTOeLLgOJmlsF"
    }
}
