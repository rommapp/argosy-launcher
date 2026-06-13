package com.nendo.argosy.data.quaypass

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import javax.inject.Inject
import javax.inject.Singleton

/** Per-install signing keypair (Ed25519 on API 33+, EC P-256 fallback). */
@Singleton
class QuayPassKeystore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    enum class KeyBacking { STRONGBOX, TEE, SOFTWARE, UNKNOWN }
    enum class Algorithm { ED25519, EC_P256 }

    data class KeyInfo(
        val publicKey: PublicKey,
        val publicKeyEncoded: ByteArray,
        val backing: KeyBacking,
        val algorithm: Algorithm
    )

    @Synchronized
    fun getOrCreateKeyInfo(): KeyInfo {
        val ks = openKeystore()
        if (ks.containsAlias(KEY_ALIAS)) {
            val pub = ks.getCertificate(KEY_ALIAS).publicKey
            return KeyInfo(
                publicKey = pub,
                publicKeyEncoded = pub.encoded,
                backing = inferBackingFromExisting(),
                algorithm = inferAlgorithmFromKey(pub)
            )
        }
        return generateKey()
    }

    /** Signs with the install key; both algorithms return a fixed 64-byte signature. */
    fun sign(data: ByteArray): ByteArray {
        val privateKey = getPrivateKey()
        val info = getOrCreateKeyInfo()
        val sigAlg = when (info.algorithm) {
            Algorithm.ED25519 -> "Ed25519"
            Algorithm.EC_P256 -> "SHA256withECDSA"
        }
        val signer = Signature.getInstance(sigAlg)
        signer.initSign(privateKey)
        signer.update(data)
        val raw = signer.sign()
        return when (info.algorithm) {
            Algorithm.ED25519 -> raw
            Algorithm.EC_P256 -> EcdsaP1363.fromDer(raw, P256_COMPONENT_BYTES)
        }
    }

    @Synchronized
    fun clear() {
        try {
            val ks = openKeystore()
            if (ks.containsAlias(KEY_ALIAS)) ks.deleteEntry(KEY_ALIAS)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to clear keypair", t)
        }
    }

    private fun openKeystore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun getPrivateKey(): PrivateKey {
        val ks = openKeystore()
        if (!ks.containsAlias(KEY_ALIAS)) {
            generateKey()
        }
        return ks.getKey(KEY_ALIAS, null) as? PrivateKey
            ?: error("QuayPass private key missing or wrong type")
    }

    private fun generateKey(): KeyInfo {
        val attempts = sequence {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                yield(Triple(Algorithm.ED25519, /* strongBox = */ true, "Ed25519+StrongBox"))
                yield(Triple(Algorithm.ED25519, /* strongBox = */ false, "Ed25519+TEE"))
            }
            yield(Triple(Algorithm.EC_P256, true, "EC-P256+StrongBox"))
            yield(Triple(Algorithm.EC_P256, false, "EC-P256+TEE"))
        }

        var lastError: Throwable? = null
        for ((alg, sb, label) in attempts) {
            try {
                val gen = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE
                )
                val builder = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )

                when (alg) {
                    Algorithm.ED25519 ->
                        builder
                            .setAlgorithmParameterSpec(
                                java.security.spec.ECGenParameterSpec("ed25519")
                            )
                            .setDigests(KeyProperties.DIGEST_NONE)
                    Algorithm.EC_P256 ->
                        builder
                            .setAlgorithmParameterSpec(
                                java.security.spec.ECGenParameterSpec("secp256r1")
                            )
                            .setDigests(KeyProperties.DIGEST_SHA256)
                }
                if (sb && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    builder.setIsStrongBoxBacked(true)
                }
                gen.initialize(builder.build())
                val pair: KeyPair = gen.generateKeyPair()
                trySign(alg)
                Log.i(TAG, "Generated QuayPass keypair via $label")
                return KeyInfo(
                    publicKey = pair.public,
                    publicKeyEncoded = pair.public.encoded,
                    backing = if (sb) KeyBacking.STRONGBOX else KeyBacking.TEE,
                    algorithm = alg
                )
            } catch (t: Throwable) {
                lastError = t
                Log.w(TAG, "QuayPass keypair attempt failed ($label): ${t.message}")
                runCatching { openKeystore().deleteEntry(KEY_ALIAS) }
            }
        }

        try {
            val gen = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
            gen.initialize(spec)
            val pair = gen.generateKeyPair()
            trySign(Algorithm.EC_P256)
            Log.w(TAG, "Generated QuayPass keypair in software (no hardware backing available)")
            return KeyInfo(
                publicKey = pair.public,
                publicKeyEncoded = pair.public.encoded,
                backing = KeyBacking.SOFTWARE,
                algorithm = Algorithm.EC_P256
            )
        } catch (t: Throwable) {
            throw IllegalStateException(
                "QuayPass keypair generation failed in all configurations",
                lastError ?: t
            )
        }
    }

    private fun trySign(alg: Algorithm) {
        val key = openKeystore().getKey(KEY_ALIAS, null) as? PrivateKey
            ?: error("generated key not retrievable")
        val signer = Signature.getInstance(
            when (alg) {
                Algorithm.ED25519 -> "Ed25519"
                Algorithm.EC_P256 -> "SHA256withECDSA"
            }
        )
        signer.initSign(key)
        signer.update(ByteArray(32))
        signer.sign()
    }

    private fun inferBackingFromExisting(): KeyBacking = KeyBacking.UNKNOWN

    private fun inferAlgorithmFromKey(pub: PublicKey): Algorithm =
        when (pub.algorithm.uppercase()) {
            "ED25519", "EDDSA", "1.3.101.112" -> Algorithm.ED25519
            else -> Algorithm.EC_P256
        }

    companion object {
        private const val TAG = "QuayPassKeystore"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "quaypass_install_v1"
        private const val P256_COMPONENT_BYTES = 32
    }
}
