package com.nendo.argosy.data.remote.ssl

import java.io.IOException
import java.io.InputStream
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.PrivateKey
import java.security.UnrecoverableKeyException
import java.security.cert.X509Certificate
import javax.crypto.BadPaddingException

class ClientCertImportException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { NOT_A_FILE, WRONG_PASSWORD, NO_PRIVATE_KEY, UNREADABLE, EXPIRED, UNSUPPORTED_KEY }
}

internal object Pkcs12Reader {
    private val SUPPORTED_KEY_ALGORITHMS = setOf("RSA", "EC")
    private val WRONG_PASSWORD_MARKERS = listOf("password", "mac invalid", "mac verify", "integrity check")

    fun read(stream: InputStream, password: CharArray): ClientIdentity {
        val ks = KeyStore.getInstance("PKCS12")
        try {
            ks.load(stream, password)
        } catch (e: IOException) {
            fail(
                if (e.isWrongPassword()) ClientCertImportException.Reason.WRONG_PASSWORD
                else ClientCertImportException.Reason.UNREADABLE
            )
        } catch (e: GeneralSecurityException) {
            fail(ClientCertImportException.Reason.UNREADABLE)
        }
        val alias = ks.aliases().toList().firstOrNull { ks.isKeyEntry(it) }
            ?: fail(ClientCertImportException.Reason.NO_PRIVATE_KEY)
        val key = try {
            ks.getKey(alias, password) as? PrivateKey
        } catch (e: UnrecoverableKeyException) {
            fail(ClientCertImportException.Reason.WRONG_PASSWORD)
        } ?: fail(ClientCertImportException.Reason.NO_PRIVATE_KEY)
        val chain = ks.getCertificateChain(alias)
            ?.filterIsInstance<X509Certificate>()
            ?.toTypedArray()
            ?.takeIf { it.isNotEmpty() }
            ?: fail(ClientCertImportException.Reason.NO_PRIVATE_KEY)
        if (key.algorithm.uppercase() !in SUPPORTED_KEY_ALGORITHMS) {
            fail(ClientCertImportException.Reason.UNSUPPORTED_KEY)
        }
        return ClientIdentity(key, chain)
    }

    private fun Throwable.isWrongPassword(): Boolean {
        var current: Throwable? = this
        val seen = mutableSetOf<Throwable>()
        while (current != null && seen.add(current)) {
            if (current is UnrecoverableKeyException || current is BadPaddingException) return true
            val message = current.message?.lowercase().orEmpty()
            if (WRONG_PASSWORD_MARKERS.any { it in message }) return true
            current = current.cause
        }
        return false
    }

    private fun fail(reason: ClientCertImportException.Reason): Nothing = throw ClientCertImportException(reason)
}
