package com.nendo.argosy.data.netplay

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.AEAD
import com.goterl.lazysodium.interfaces.KeyDerivation
import java.security.SecureRandom
import java.util.Arrays

class NetplayCryptoException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class PacketCrypto private constructor(
    private val keyHostToGuest: ByteArray,
    private val keyGuestToHost: ByteArray
) : AutoCloseable {

    enum class Direction { HostToGuest, GuestToHost }

    private val secureRandom = SecureRandom()
    private var closed = false

    fun encrypt(plaintext: ByteArray, direction: Direction): ByteArray {
        check(!closed) { "PacketCrypto is closed" }
        val key = keyFor(direction)
        val nonce = ByteArray(AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES)
        secureRandom.nextBytes(nonce)

        val ciphertext = ByteArray(plaintext.size + AEAD.XCHACHA20POLY1305_IETF_ABYTES)
        val cipherLen = LongArray(1)
        val ok = lazySodium.cryptoAeadXChaCha20Poly1305IetfEncrypt(
            ciphertext,
            cipherLen,
            plaintext,
            plaintext.size.toLong(),
            null,
            0L,
            null,
            nonce,
            key
        )
        if (!ok) throw NetplayCryptoException("XChaCha20-Poly1305 encryption failed")

        val wire = ByteArray(nonce.size + cipherLen[0].toInt())
        System.arraycopy(nonce, 0, wire, 0, nonce.size)
        System.arraycopy(ciphertext, 0, wire, nonce.size, cipherLen[0].toInt())
        return wire
    }

    fun decrypt(wire: ByteArray, direction: Direction): ByteArray? {
        check(!closed) { "PacketCrypto is closed" }
        val nonceLen = AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES
        val tagLen = AEAD.XCHACHA20POLY1305_IETF_ABYTES
        if (wire.size < nonceLen + tagLen) return null
        val key = keyFor(direction)
        val nonce = ByteArray(nonceLen)
        System.arraycopy(wire, 0, nonce, 0, nonceLen)
        val ciphertextLen = wire.size - nonceLen
        val ciphertext = ByteArray(ciphertextLen)
        System.arraycopy(wire, nonceLen, ciphertext, 0, ciphertextLen)

        val plaintext = ByteArray(ciphertextLen - tagLen)
        val plainLen = LongArray(1)
        return try {
            val ok = lazySodium.cryptoAeadXChaCha20Poly1305IetfDecrypt(
                plaintext,
                plainLen,
                null,
                ciphertext,
                ciphertextLen.toLong(),
                null,
                0L,
                nonce,
                key
            )
            if (!ok) null else plaintext
        } catch (_: Throwable) {
            null
        }
    }

    private fun keyFor(direction: Direction): ByteArray = when (direction) {
        Direction.HostToGuest -> keyHostToGuest
        Direction.GuestToHost -> keyGuestToHost
    }

    override fun close() {
        if (closed) return
        closed = true
        Arrays.fill(keyHostToGuest, 0)
        Arrays.fill(keyGuestToHost, 0)
    }

    companion object {
        private const val SUBKEY_LEN = 32
        private const val KDF_CONTEXT = "argnetpl"
        private const val SUBKEY_ID_H2G = 1L
        private const val SUBKEY_ID_G2H = 2L

        private val lazySodium: LazySodiumAndroid by lazy {
            try {
                LazySodiumAndroid(SodiumAndroid())
            } catch (t: Throwable) {
                throw NetplayCryptoException(
                    "Failed to load libsodium native library (lazysodium-android). " +
                        "Check that JNA and the libsodium .so are bundled for this ABI.",
                    t
                )
            }
        }

        fun fromMasterKey(masterKey: ByteArray): PacketCrypto {
            require(masterKey.size == KeyDerivation.MASTER_KEY_BYTES) {
                "Master key must be ${KeyDerivation.MASTER_KEY_BYTES} bytes, got ${masterKey.size}"
            }
            val context = KDF_CONTEXT.toByteArray(Charsets.US_ASCII)
            require(context.size == KeyDerivation.CONTEXT_BYTES) {
                "KDF context must be ${KeyDerivation.CONTEXT_BYTES} bytes"
            }

            val h2g = ByteArray(SUBKEY_LEN)
            val g2h = ByteArray(SUBKEY_LEN)
            val rc1 = lazySodium.cryptoKdfDeriveFromKey(h2g, SUBKEY_LEN, SUBKEY_ID_H2G, context, masterKey)
            val rc2 = lazySodium.cryptoKdfDeriveFromKey(g2h, SUBKEY_LEN, SUBKEY_ID_G2H, context, masterKey)
            if (rc1 != 0 || rc2 != 0) {
                throw NetplayCryptoException("HKDF sub-key derivation failed (rc=$rc1,$rc2)")
            }
            return PacketCrypto(h2g, g2h)
        }
    }
}
