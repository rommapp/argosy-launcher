package com.nendo.argosy.data.remote.ssl

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import kotlin.io.path.createTempDirectory

private val ROMM_LAN = ClientCertTarget.ROMM.scopeFor("https://romm.lan")

class ClientCertStoreTest {

    private class MemoryVault : ClientKeyVault {
        val entries = mutableMapOf<String, ClientIdentity>()
        var refuse = false
        var refuseAlgorithm: String? = null
        var unavailable = false

        override fun put(alias: String, identity: ClientIdentity) {
            if (refuse || identity.keyAlgorithm == refuseAlgorithm) {
                throw IllegalStateException("keystore refused the key")
            }
            entries[alias] = identity
        }

        override fun get(alias: String): ClientIdentity? {
            if (unavailable) throw java.security.KeyStoreException("keystore not ready")
            return entries[alias]
        }

        override fun delete(alias: String) {
            entries.remove(alias)
        }
    }

    private lateinit var tempDir: File
    private lateinit var context: Context
    private lateinit var vault: MemoryVault
    private lateinit var store: ClientCertStore

    private fun fixture(name: String): String {
        val url = javaClass.classLoader!!.getResource("client-cert/$name")
            ?: error("missing fixture $name")
        return File(url.toURI()).absolutePath
    }

    private fun newStore() = ClientCertStore(context, vault)

    @Before
    fun setUp() {
        tempDir = createTempDirectory("client_cert_store").toFile()
        context = mockk(relaxed = true)
        every { context.filesDir } returns tempDir
        vault = MemoryVault()
        store = newStore()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun importReason(name: String, password: String): ClientCertImportException.Reason? = runBlocking {
        val result = store.importFrom(fixture(name), password.toCharArray(), ClientCertTarget.ROMM)
        (result.exceptionOrNull() as? ClientCertImportException)?.reason
    }

    @Test
    fun `an rsa file in the current openssl format imports`() = runBlocking {
        val info = store.importFrom(fixture("rsa-aes.p12"), "argosy".toCharArray(), ClientCertTarget.ROMM)
            .getOrThrow()
        assertEquals("argosy-test-rsa", info.displayName)
        assertEquals(setOf(ClientCertTarget.ROMM), info.targets)
        assertEquals("RSA", vault.entries.values.single().keyAlgorithm)
    }

    @Test
    fun `an rsa file in the legacy openssl format imports`() {
        assertNull(importReason("rsa-legacy.p12", "argosy"))
    }

    @Test
    fun `an ec key imports`() {
        assertNull(importReason("ec-aes.p12", "argosy"))
        assertEquals("EC", vault.entries.values.single().keyAlgorithm)
    }

    @Test
    fun `a file without a password imports with an empty one`() {
        assertNull(importReason("ec-nopass.p12", ""))
    }

    @Test
    fun `a wrong password is reported as such`() {
        assertEquals(ClientCertImportException.Reason.WRONG_PASSWORD, importReason("rsa-aes.p12", "nope"))
        assertTrue(vault.entries.isEmpty())
    }

    @Test
    fun `a file holding only a certificate has no key to import`() {
        assertEquals(ClientCertImportException.Reason.NO_PRIVATE_KEY, importReason("cert-only.p12", "argosy"))
    }

    @Test
    fun `an expired certificate is refused`() {
        assertEquals(ClientCertImportException.Reason.EXPIRED, importReason("expired.p12", "argosy"))
    }

    @Test
    fun `a missing file is refused`() = runBlocking {
        val result = store.importFrom(File(tempDir, "absent.p12").path, CharArray(0), ClientCertTarget.ROMM)
        assertEquals(
            ClientCertImportException.Reason.NOT_A_FILE,
            (result.exceptionOrNull() as ClientCertImportException).reason
        )
    }

    @Test
    fun `a key the vault refuses is reported as unsupported`() {
        vault.refuse = true
        assertEquals(ClientCertImportException.Reason.UNSUPPORTED_KEY, importReason("rsa-aes.p12", "argosy"))
        assertNull(store.info.value)
    }

    @Test
    fun `a replacement the vault refuses keeps the certificate already in use`() = runBlocking {
        store.importFrom(fixture("rsa-aes.p12"), "argosy".toCharArray(), ClientCertTarget.ROMM).getOrThrow()
        vault.refuseAlgorithm = "EC"

        val result = store.importFrom(fixture("ec-aes.p12"), "argosy".toCharArray(), ClientCertTarget.JELLYFIN)

        assertEquals(
            ClientCertImportException.Reason.UNSUPPORTED_KEY,
            (result.exceptionOrNull() as ClientCertImportException).reason
        )
        assertEquals("argosy-test-rsa", store.info.value?.displayName)
        assertEquals(setOf(ClientCertTarget.ROMM), store.info.value?.targets)
        assertEquals(listOf("RSA"), vault.entries.values.map { it.keyAlgorithm })
        assertNotNull(store.identityFor(ROMM_LAN, "romm.lan"))
    }

    @Test
    fun `every change to the certificate or its targets moves the revision`() = runBlocking {
        val start = store.revision
        store.importFrom(fixture("rsa-aes.p12"), "argosy".toCharArray(), ClientCertTarget.ROMM).getOrThrow()
        val imported = store.revision
        store.setTargetEnabled(ClientCertTarget.ROMM, true)
        assertEquals(imported, store.revision)
        store.setTargetEnabled(ClientCertTarget.ROMM, false)
        val toggled = store.revision
        store.remove()

        assertTrue(start < imported)
        assertTrue(imported < toggled)
        assertTrue(toggled < store.revision)
    }

    @Test
    fun `a target is announced each time its certificate changes`() = runBlocking {
        val announced = mutableListOf<ClientCertTarget>()
        val collector = launch(Dispatchers.Unconfined) { store.changedFor.collect { announced += it } }

        store.importFrom(fixture("rsa-aes.p12"), "argosy".toCharArray(), ClientCertTarget.ROMM).getOrThrow()
        store.setTargetEnabled(ClientCertTarget.JELLYFIN, true)
        store.setTargetEnabled(ClientCertTarget.JELLYFIN, false)
        store.setTargetEnabled(ClientCertTarget.ROMM, true)
        store.remove()
        store.setTargetEnabled(ClientCertTarget.ROMM, true)
        collector.cancel()

        assertEquals(
            listOf(
                ClientCertTarget.ROMM,
                ClientCertTarget.JELLYFIN,
                ClientCertTarget.JELLYFIN,
                ClientCertTarget.ROMM
            ),
            announced
        )
    }

    @Test
    fun `the socket factory rebuilds its tls context only when the revision moves`() = runBlocking {
        var built = 0
        val factory = ClientCertRevisionSocketFactory(store) {
            built++
            SSLContext.getInstance("TLS").apply { init(null, null, null) }
        }
        factory.defaultCipherSuites
        factory.supportedCipherSuites
        assertEquals(1, built)

        store.importFrom(fixture("rsa-aes.p12"), "argosy".toCharArray(), ClientCertTarget.ROMM).getOrThrow()
        factory.defaultCipherSuites
        assertEquals(2, built)
    }

    @Test
    fun `a keystore that fails to answer at startup keeps the configuration for a later attempt`() = runBlocking {
        store.importFrom(fixture("rsa-aes.p12"), "argosy".toCharArray(), ClientCertTarget.ROMM).getOrThrow()
        vault.unavailable = true

        val reopened = newStore()
        reopened.initialize()
        assertNull(reopened.identityFor(ROMM_LAN, "romm.lan"))
        assertTrue(File(tempDir, ClientCertStore.DIR).listFiles().orEmpty().any { it.isFile })

        vault.unavailable = false
        assertNotNull(reopened.identityFor(ROMM_LAN, "romm.lan"))
        assertEquals(setOf(ClientCertTarget.ROMM), reopened.info.value?.targets)
    }

    @Test
    fun `a replacement survives a restart under the slot it was stored in`() = runBlocking {
        store.importFrom(fixture("rsa-aes.p12"), "argosy".toCharArray(), ClientCertTarget.ROMM).getOrThrow()
        store.importFrom(fixture("ec-aes.p12"), "argosy".toCharArray(), ClientCertTarget.ROMM).getOrThrow()
        assertEquals(listOf("EC"), vault.entries.values.map { it.keyAlgorithm })

        val reopened = newStore()
        reopened.initialize()
        assertEquals("argosy-test-ec", reopened.info.value?.displayName)
        assertEquals("EC", reopened.identityFor(ROMM_LAN, "romm.lan")?.keyAlgorithm)
    }

    @Test
    fun `a changed host list moves the revision and an unchanged one does not`() {
        store.updateHosts(ClientCertTarget.ROMM, listOf("https://romm.lan"))
        val first = store.revision
        store.updateHosts(ClientCertTarget.ROMM, listOf("romm.lan:443"))
        assertEquals(first, store.revision)
        store.updateHosts(ClientCertTarget.ROMM, listOf("https://other.lan"))
        assertTrue(store.revision > first)
    }

    @Test
    fun `the key manager hands out the chain and key of the identity it chose`() = runBlocking {
        store.importFrom(fixture("rsa-aes.p12"), "argosy".toCharArray(), ClientCertTarget.ROMM).getOrThrow()
        val keyManager = ClientCertKeyManager(store, ROMM_LAN)
        val engine = mockk<SSLEngine> {
            every { handshakeSession } returns null
            every { peerHost } returns "romm.lan"
        }
        val rsaAlias = keyManager.chooseEngineClientAlias(arrayOf("RSA", "EC"), null, engine)!!

        store.importFrom(fixture("ec-aes.p12"), "argosy".toCharArray(), ClientCertTarget.ROMM).getOrThrow()
        val ecAlias = keyManager.chooseEngineClientAlias(arrayOf("RSA", "EC"), null, engine)!!

        assertTrue(rsaAlias != ecAlias)
        assertEquals("RSA", keyManager.getPrivateKey(rsaAlias)?.algorithm)
        assertEquals("RSA", keyManager.getCertificateChain(rsaAlias)?.first()?.publicKey?.algorithm)
        assertEquals("EC", keyManager.getPrivateKey(ecAlias)?.algorithm)
        assertEquals("EC", keyManager.getCertificateChain(ecAlias)?.first()?.publicKey?.algorithm)
    }

    @Test
    fun `targets and the identity survive a restart`() = runBlocking {
        store.importFrom(fixture("rsa-aes.p12"), "argosy".toCharArray(), ClientCertTarget.ROMM).getOrThrow()
        store.setTargetEnabled(ClientCertTarget.JELLYFIN, true)

        val reopened = newStore()
        reopened.initialize()
        assertEquals(setOf(ClientCertTarget.ROMM, ClientCertTarget.JELLYFIN), reopened.info.value?.targets)
    }

    @Test
    fun `metadata restored without its key reads as no certificate`() = runBlocking {
        store.importFrom(fixture("rsa-aes.p12"), "argosy".toCharArray(), ClientCertTarget.ROMM).getOrThrow()
        vault.entries.clear()

        val reopened = newStore()
        reopened.initialize()
        assertNull(reopened.info.value)
        assertNull(reopened.identityFor(ROMM_LAN, "romm.lan"))
        assertFalse(File(tempDir, ClientCertStore.DIR).listFiles().orEmpty().any { it.isFile })
    }

    @Test
    fun `replacing keeps the targets already enabled and adds the new one`() = runBlocking {
        store.importFrom(fixture("rsa-aes.p12"), "argosy".toCharArray(), ClientCertTarget.ROMM).getOrThrow()
        val info = store.importFrom(fixture("ec-aes.p12"), "argosy".toCharArray(), ClientCertTarget.JELLYFIN)
            .getOrThrow()
        assertEquals("argosy-test-ec", info.displayName)
        assertEquals(setOf(ClientCertTarget.ROMM, ClientCertTarget.JELLYFIN), info.targets)
    }

    @Test
    fun `removing forgets the key and the targets`() = runBlocking {
        store.importFrom(fixture("rsa-aes.p12"), "argosy".toCharArray(), ClientCertTarget.ROMM).getOrThrow()
        store.remove()
        assertNull(store.info.value)
        assertTrue(vault.entries.isEmpty())
        assertNull(store.identityFor(ROMM_LAN, "romm.lan"))
    }

    @Test
    fun `a dedicated client presents only for an enabled target`() = runBlocking {
        store.importFrom(fixture("rsa-aes.p12"), "argosy".toCharArray(), ClientCertTarget.ROMM).getOrThrow()
        val newServer = ClientCertTarget.ROMM.scopeFor("https://New-Server.example:8443/romm")
        assertNotNull(store.identityFor(newServer, "new-server.example"))
        assertNull(store.identityFor(ClientCertTarget.JELLYFIN.scopeFor("https://media.example"), "media.example"))

        store.setTargetEnabled(ClientCertTarget.ROMM, false)
        assertNull(store.identityFor(newServer, "new-server.example"))
    }

    @Test
    fun `a dedicated client presents only to its own server and the recorded hosts`() = runBlocking {
        store.importFrom(fixture("rsa-aes.p12"), "argosy".toCharArray(), ClientCertTarget.ROMM).getOrThrow()
        store.setTargetEnabled(ClientCertTarget.JELLYFIN, true)
        store.updateHosts(ClientCertTarget.ROMM, listOf("https://romm.lan", "https://10.0.0.5"))
        store.updateHosts(ClientCertTarget.JELLYFIN, listOf("https://media.lan"))

        assertNotNull(store.identityFor(ROMM_LAN, "romm.lan"))
        assertNotNull(store.identityFor(ROMM_LAN, "10.0.0.5"))
        assertNull(store.identityFor(ROMM_LAN, "downloads.cdn.example"))
        assertNull(store.identityFor(ROMM_LAN, "media.lan"))
        assertNull(store.identityFor(ROMM_LAN, null))

        assertNotNull(store.identityFor(JELLYFIN_CLIENT_CERT_SCOPE, "media.lan"))
        assertNull(store.identityFor(JELLYFIN_CLIENT_CERT_SCOPE, "transcoder.elsewhere.example"))
        assertNull(store.identityFor(JELLYFIN_CLIENT_CERT_SCOPE, "romm.lan"))
    }

    @Test
    fun `a socket opened before the certificate or its targets changed is closed, busy or not`() = runBlocking {
        val factory = ClientCertRevisionSocketFactory(store) {
            SSLContext.getInstance("TLS").apply { init(null, null, null) }
        }
        val before = factory.createSocket()
        factory.closeStale()
        assertFalse(before.isClosed)

        store.importFrom(fixture("rsa-aes.p12"), "argosy".toCharArray(), ClientCertTarget.ROMM).getOrThrow()
        val after = factory.createSocket()
        factory.closeStale()
        assertTrue(before.isClosed)
        assertFalse(after.isClosed)

        store.updateHosts(ClientCertTarget.ROMM, listOf("https://romm.lan"))
        factory.closeStale()
        assertFalse(after.isClosed)

        store.setTargetEnabled(ClientCertTarget.ROMM, false)
        factory.closeStale()
        assertTrue(after.isClosed)
    }

    @Test
    fun `an interrupted settings write leaves the saved targets in place`() = runBlocking {
        store.importFrom(fixture("rsa-aes.p12"), "argosy".toCharArray(), ClientCertTarget.ROMM).getOrThrow()
        store.setTargetEnabled(ClientCertTarget.JELLYFIN, true)
        val dir = File(tempDir, ClientCertStore.DIR)
        assertEquals(listOf("identity.properties"), dir.list()?.toList())
        File(dir, "identity.properties.pending").writeText("alias=argosy-client-cert")

        val reopened = newStore()
        reopened.initialize()
        assertEquals(setOf(ClientCertTarget.ROMM, ClientCertTarget.JELLYFIN), reopened.info.value?.targets)

        reopened.setTargetEnabled(ClientCertTarget.JELLYFIN, false)
        assertEquals(listOf("identity.properties"), dir.list()?.toList())
        assertEquals(setOf(ClientCertTarget.ROMM), newStore().apply { initialize() }.info.value?.targets)
    }

    @Test
    fun `a shared client presents only to a host recorded for an enabled target`() = runBlocking {
        store.importFrom(fixture("rsa-aes.p12"), "argosy".toCharArray(), ClientCertTarget.ROMM).getOrThrow()
        store.updateHosts(ClientCertTarget.ROMM, listOf("https://RomM.Home.Lan:8443/", "10.0.0.5"))
        store.updateHosts(ClientCertTarget.JELLYFIN, listOf("https://media.home.lan"))

        val identity = store.identityFor(ClientCertScope.ByHost, "romm.home.lan")
        assertNotNull(identity)
        assertSame(identity, store.identityFor(ClientCertScope.ByHost, "10.0.0.5"))
        assertNull(store.identityFor(ClientCertScope.ByHost, "media.home.lan"))
        assertNull(store.identityFor(ClientCertScope.ByHost, "images.example.com"))
        assertNull(store.identityFor(ClientCertScope.ByHost, null))
    }

    @Test
    fun `a pinned system host never receives the certificate`() = runBlocking {
        store.importFrom(fixture("rsa-aes.p12"), "argosy".toCharArray(), ClientCertTarget.ROMM).getOrThrow()
        store.updateHosts(ClientCertTarget.ROMM, listOf("https://github.com"))
        assertNull(store.identityFor(ClientCertScope.ByHost, "github.com"))
        assertNull(store.identityFor(ClientCertTarget.ROMM.scopeFor("https://api.github.com"), "api.github.com"))
    }

    @Test
    fun `an unanswered certificate request is remembered per host until one is presented`() {
        store.recordCertificateRequest("RomM.Lan", presented = false)
        assertTrue(store.requestedWithoutCertificate("https://romm.lan:8443/"))
        assertFalse(store.requestedWithoutCertificate("https://other.lan"))

        store.recordCertificateRequest("romm.lan", presented = true)
        assertFalse(store.requestedWithoutCertificate("https://romm.lan:8443/"))
    }

    @Test
    fun `host extraction tolerates bare addresses`() {
        assertEquals("romm.lan", ClientCertStore.hostOf("romm.lan:8080"))
        assertEquals("192.168.1.2", ClientCertStore.hostOf("http://192.168.1.2/"))
        assertNull(ClientCertStore.hostOf("  "))
    }
}
