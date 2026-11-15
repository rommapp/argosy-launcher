package com.nendo.argosy.data.remote.ssl

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLProtocolException

class ClientCertDetectionTest {

    @Test
    fun `a tls 1_3 certificate_required alert is a client certificate rejection`() {
        val alert = SSLProtocolException(
            "Read error: ssl=0x7a: Failure in SSL library, usually a protocol error\n" +
                "error:10000074:SSL routines:OPENSSL_internal:TLSV1_ALERT_CERTIFICATE_REQUIRED"
        )
        assertTrue(IOException("unexpected end of stream", alert).isClientCertificateRejection())
    }

    @Test
    fun `a bad_certificate alert is a client certificate rejection`() {
        assertTrue(SSLHandshakeException("SSLV3_ALERT_BAD_CERTIFICATE").isClientCertificateRejection())
    }

    @Test
    fun `an untrusted server chain is not a client certificate rejection`() {
        val untrusted = SSLHandshakeException("java.security.cert.CertPathValidatorException")
            .apply { initCause(CertificateException("Trust anchor for certification path not found.")) }
        assertFalse(untrusted.isClientCertificateRejection())
        assertTrue(untrusted.isCertificateTrustFailure())
    }

    @Test
    fun `a plain handshake failure is not a client certificate rejection`() {
        assertFalse(SSLException("Connection reset").isClientCertificateRejection())
    }

    @Test
    fun `a dropped connection after an unanswered certificate request is a client certificate rejection`() {
        val shutdown = IOException("okhttp3.internal.http2.ConnectionShutdownException")
        assertTrue(shutdown.isClientCertificateRejection(requestedWithoutCertificate = true))
        assertFalse(shutdown.isClientCertificateRejection(requestedWithoutCertificate = false))
    }

    @Test
    fun `failures before any handshake are never blamed on the certificate`() {
        assertFalse(ConnectException("refused").isClientCertificateRejection(requestedWithoutCertificate = true))
        assertFalse(UnknownHostException("nope").isClientCertificateRejection(requestedWithoutCertificate = true))
        assertFalse(SocketTimeoutException("slow").isClientCertificateRejection(requestedWithoutCertificate = true))
        assertFalse(InterruptedIOException("timeout").isClientCertificateRejection(requestedWithoutCertificate = true))
    }

    @Test
    fun `a failure that is not about the connection is never blamed on the certificate`() {
        assertFalse(IllegalStateException("bad json").isClientCertificateRejection(requestedWithoutCertificate = true))
    }

    @Test
    fun `a server refusing the presented certificate is a client certificate rejection`() {
        assertTrue(SSLHandshakeException("TLSV1_ALERT_UNKNOWN_CA").isClientCertificateRejection())
    }

    @Test
    fun `an untrusted server chain wins over an unanswered certificate request`() {
        val untrusted = SSLHandshakeException("chain").apply { initCause(CertificateException("no anchor")) }
        assertFalse(untrusted.isClientCertificateRejection(requestedWithoutCertificate = true))
    }

    @Test
    fun `nginx's 400 page is a client certificate rejection`() {
        val body = "<html><head><title>400 No required SSL certificate was sent</title></head></html>"
        assertTrue(isClientCertificateRejection(400, body))
        assertTrue(isClientCertificateRejection(400, "<title>400 The SSL certificate error</title>"))
    }

    @Test
    fun `other 400 responses are not a client certificate rejection`() {
        assertFalse(isClientCertificateRejection(400, "{\"detail\":\"bad request\"}"))
        assertFalse(isClientCertificateRejection(500, "No required SSL certificate was sent"))
        assertFalse(isClientCertificateRejection(400, null))
    }

    @Test
    fun `key type matching accepts the forms tls stacks ask for`() {
        assertTrue(ClientCertKeyManager.matchesKeyType("RSA", arrayOf("RSA")))
        assertTrue(ClientCertKeyManager.matchesKeyType("EC", arrayOf("EC_EC")))
        assertTrue(ClientCertKeyManager.matchesKeyType("RSA", arrayOf("RSASSA-PSS")))
        assertTrue(ClientCertKeyManager.matchesKeyType("EC", null))
        assertFalse(ClientCertKeyManager.matchesKeyType("EC", arrayOf("RSA")))
    }
}
