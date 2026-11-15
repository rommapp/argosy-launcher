package com.nendo.argosy.data.remote.ssl

import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton

class ClientIdentity(
    val privateKey: PrivateKey,
    val chain: Array<X509Certificate>
) {
    val keyAlgorithm: String get() = privateKey.algorithm
}

interface ClientKeyVault {
    fun put(alias: String, identity: ClientIdentity)
    fun get(alias: String): ClientIdentity?
    fun delete(alias: String)
}

@Singleton
class AndroidKeyStoreVault @Inject constructor() : ClientKeyVault {

    override fun put(alias: String, identity: ClientIdentity) {
        val ks = open()
        val isRsa = identity.keyAlgorithm.equals(KeyProperties.KEY_ALGORITHM_RSA, ignoreCase = true)
        val protection = KeyProtection.Builder(
            if (isRsa) KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_DECRYPT
            else KeyProperties.PURPOSE_SIGN
        )
            .setDigests(
                KeyProperties.DIGEST_NONE,
                KeyProperties.DIGEST_MD5,
                KeyProperties.DIGEST_SHA1,
                KeyProperties.DIGEST_SHA224,
                KeyProperties.DIGEST_SHA256,
                KeyProperties.DIGEST_SHA384,
                KeyProperties.DIGEST_SHA512
            )
            .apply {
                if (isRsa) {
                    setSignaturePaddings(
                        KeyProperties.SIGNATURE_PADDING_RSA_PKCS1,
                        KeyProperties.SIGNATURE_PADDING_RSA_PSS
                    )
                    setEncryptionPaddings(
                        KeyProperties.ENCRYPTION_PADDING_NONE,
                        KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1,
                        KeyProperties.ENCRYPTION_PADDING_RSA_OAEP
                    )
                }
            }
            .build()
        ks.deleteEntry(alias)
        ks.setEntry(
            alias,
            KeyStore.PrivateKeyEntry(identity.privateKey, identity.chain),
            protection
        )
    }

    override fun get(alias: String): ClientIdentity? {
        val entry = open().getEntry(alias, null) as? KeyStore.PrivateKeyEntry ?: return null
        val chain = entry.certificateChain.filterIsInstance<X509Certificate>().toTypedArray()
        if (chain.isEmpty()) return null
        return ClientIdentity(entry.privateKey, chain)
    }

    override fun delete(alias: String) {
        runCatching { open().deleteEntry(alias) }
    }

    private fun open(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}
