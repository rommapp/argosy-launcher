package com.nendo.argosy.data.remote.ssl

import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Whether a connection failed because the chain itself was not trusted, which importing the
 * server's certificate can fix.
 *
 * Deliberately narrower than "the TLS layer threw". A hostname mismatch
 * ([SSLPeerUnverifiedException]) and a protocol or cipher mismatch (a bare handshake failure)
 * also surface from the TLS layer, and importing a certificate fixes neither: this trust manager
 * replaces the socket factory only, and leaves OkHttp's hostname verifier in place. Counting
 * them here would answer "add the certificate" to a user who then adds one and sees the same
 * failure, with nothing new to try.
 *
 * The cause is read through the whole chain because the validator's rejection arrives wrapped,
 * and on some devices wrapped twice.
 */
fun Throwable.isCertificateTrustFailure(): Boolean {
    var current: Throwable? = this
    val seen = mutableSetOf<Throwable>()
    while (current != null && seen.add(current)) {
        when (current) {
            is SSLPeerUnverifiedException -> return false
            is CertPathValidatorException, is CertificateException -> return true
        }
        current = current.cause
    }
    return false
}

private val CLIENT_CERT_ALERT_MARKERS = listOf(
    "certificate_required",
    "certificate required",
    "bad_certificate",
    "bad certificate",
    "unknown_ca",
    "certificate_unknown",
    "certificate_expired",
    "certificate_revoked"
)

fun Throwable.isClientCertificateRejection(requestedWithoutCertificate: Boolean = false): Boolean {
    if (isCertificateTrustFailure()) return false
    var current: Throwable? = this
    val seen = mutableSetOf<Throwable>()
    while (current != null && seen.add(current)) {
        when (current) {
            is UnknownHostException, is ConnectException, is InterruptedIOException -> return false
            is SSLException -> {
                val message = current.message?.lowercase().orEmpty()
                if (CLIENT_CERT_ALERT_MARKERS.any { it in message }) return true
            }
        }
        current = current.cause
    }
    return requestedWithoutCertificate && this is IOException
}

fun isClientCertificateRejection(code: Int, body: String?): Boolean =
    code == 400 && body?.contains("SSL certificate", ignoreCase = true) == true
