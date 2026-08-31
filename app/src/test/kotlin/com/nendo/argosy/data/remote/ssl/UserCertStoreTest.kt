package com.nendo.argosy.data.remote.ssl

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * The store decides what this app will trust, so a file that is not a certificate must be
 * refused rather than stored and silently ignored, and importing must never replace what the
 * device already trusts.
 */
class UserCertStoreTest {

    private lateinit var tempDir: File
    private lateinit var store: UserCertStore

    /** A real self-signed CA, valid to 2046. Only that it parses and composes matters here. */
    private val samplePem = """
        -----BEGIN CERTIFICATE-----
        MIIDTzCCAjegAwIBAgIUDRxJ4VqgDoMJH+WhZ5BStclzzcIwDQYJKoZIhvcNAQEL
        BQAwNzEXMBUGA1UEAwwOQXJnb3N5IFRlc3QgQ0ExDzANBgNVBAoMBkFyZ29zeTEL
        MAkGA1UEBhMCVVMwHhcNMjYwODMxMDcxMzU5WhcNNDYwODI2MDcxMzU5WjA3MRcw
        FQYDVQQDDA5Bcmdvc3kgVGVzdCBDQTEPMA0GA1UECgwGQXJnb3N5MQswCQYDVQQG
        EwJVUzCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAMWxYY6SsEiNYLuc
        oC/Q4kVm4+5SqxwlWrsnIhbpOU14wEgLwsrSc6cHwl+dqPiPDlQnZl0qmrmQ3G8M
        U9mG791ctZI2w2rSwt1Ku3X/DO1kza4TnMt2XOraMxGZWUnEKGep6ZdPE7+uQmn/
        p9rYpBeg9SK1s4h7EjDw/+lJDyPNdNRN62LAs9ExTr998Mw7r0p2L+ej1DAdJa5U
        057L97ho2BUktzfklq4YPWzkocy/5XiKfFNtCFxaH6+3TQT8b/HK6qW9wHSpy7ro
        Z0o1yn2wl/dYAJOfI6tGnDw5dMVQSpbVHscWBT+Qe7z8hhf8+56A9UXQmbtIoRrl
        oW3OH+ECAwEAAaNTMFEwHQYDVR0OBBYEFBoodeuYh74tGkgNzMX/PmRRAKntMB8G
        A1UdIwQYMBaAFBoodeuYh74tGkgNzMX/PmRRAKntMA8GA1UdEwEB/wQFMAMBAf8w
        DQYJKoZIhvcNAQELBQADggEBAFYldnYhV0XgTFwrJ5mW6skBX3JhoV5SH0tPAyIG
        qPHkND7mRwPBAWVawwU6cCTEj2I6H0LSulDoONuqVmMxMyMA81Xx31MVsYsQ/Dhp
        yfBkqV0UI5KUsN+RzA4Zl8gRV75+ZyiXP6kQzZ1DoTJut9+vXBywSUEdjD3IciWl
        HekQS63pzsJtmE9XoGZ794kvMreSKwvnrTii5HTzGBcuhx5iuWBJpOY9ZWcxy+4t
        kspHvMNceyPdMJb+NIyI5E02jND8SH19DT3S0zZjMs1Bxy3M7W3crR75nthkxTag
        wwE8HxmqAJP9y4dF/Aayn6LnRJeAG5eqR5T5QEZ8bTObeEU=
        -----END CERTIFICATE-----
    """.trimIndent()

    @Before
    fun setUp() {
        tempDir = createTempDirectory("user_cert_store").toFile()
        val context = mockk<Context>(relaxed = true)
        every { context.filesDir } returns tempDir
        store = UserCertStore(context)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `an empty store trusts nothing of its own`() = runBlocking {
        store.initialize()

        assertTrue(store.certs.value.isEmpty())
        assertNull("no imported certs means no trust manager to compose", store.trustManager())
    }

    @Test
    fun `a file that is not a certificate is refused`() = runBlocking {
        val junk = File(tempDir, "notacert.pem").apply { writeText("this is not a certificate") }

        val result = store.importFrom(junk.absolutePath)

        assertTrue(result.isFailure)
        assertTrue("nothing is recorded", store.certs.value.isEmpty())
        assertNull(store.trustManager())
    }

    @Test
    fun `a path that does not exist is refused`() = runBlocking {
        val result = store.importFrom(File(tempDir, "absent.pem").absolutePath)

        assertTrue(result.isFailure)
        assertTrue(store.certs.value.isEmpty())
    }

    @Test
    fun `an imported certificate is listed and composes a trust manager`() = runBlocking {
        val pem = File(tempDir, "ca.pem").apply { writeText(samplePem) }

        val imported = store.importFrom(pem.absolutePath).getOrThrow()

        assertEquals(1, store.certs.value.size)
        assertTrue(imported.subject.contains("Argosy Test CA"))
        assertNotNull(store.trustManager())
    }

    @Test
    fun `removing the last certificate leaves nothing trusted`() = runBlocking {
        val pem = File(tempDir, "ca.pem").apply { writeText(samplePem) }
        val imported = store.importFrom(pem.absolutePath).getOrThrow()

        store.remove(imported.fileName)

        assertTrue(store.certs.value.isEmpty())
        assertNull(store.trustManager())
    }
}
