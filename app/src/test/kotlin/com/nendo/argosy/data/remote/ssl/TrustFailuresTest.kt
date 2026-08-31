package com.nendo.argosy.data.remote.ssl

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

class TrustFailuresTest {

    @Test
    fun `an untrusted chain is a trust failure`() {
        assertTrue(CertPathValidatorException("no trust anchor").isCertificateTrustFailure())
        assertTrue(CertificateException("unable to find valid path").isCertificateTrustFailure())
    }

    @Test
    fun `the validator's rejection is found through the wrapping`() {
        val wrapped = SSLHandshakeException("handshake failed").apply {
            initCause(CertPathValidatorException("no trust anchor"))
        }

        assertTrue(IOException("request failed", wrapped).isCertificateTrustFailure())
    }

    @Test
    fun `a hostname mismatch is not a trust failure`() {
        assertFalse(SSLPeerUnverifiedException("hostname mismatch").isCertificateTrustFailure())
    }

    @Test
    fun `a hostname mismatch stays false even when something wraps it`() {
        val wrapped = IOException("request failed", SSLPeerUnverifiedException("hostname mismatch"))

        assertFalse(wrapped.isCertificateTrustFailure())
    }

    @Test
    fun `a bare handshake failure is not a trust failure`() {
        assertFalse(SSLHandshakeException("protocol or cipher mismatch").isCertificateTrustFailure())
    }

    @Test
    fun `an ordinary transport error is not a trust failure`() {
        assertFalse(IOException("connection reset").isCertificateTrustFailure())
    }

    @Test
    fun `a self referencing cause does not hang the walk`() {
        val looping = IOException("outer")
        val inner = IOException("inner", looping)
        looping.initCause(inner)

        assertFalse(looping.isCertificateTrustFailure())
    }
}
