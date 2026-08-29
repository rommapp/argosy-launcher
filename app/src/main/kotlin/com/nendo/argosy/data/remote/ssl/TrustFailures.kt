package com.nendo.argosy.data.remote.ssl

import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
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
