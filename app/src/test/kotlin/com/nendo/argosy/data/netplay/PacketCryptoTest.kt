package com.nendo.argosy.data.netplay

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PacketCryptoTest {

    private fun masterKey(seed: Byte = 0x11): ByteArray = ByteArray(32) { (seed + it).toByte() }

    @Test
    fun encryptDecrypt_roundTrip_hostToGuest() {
        val crypto = PacketCrypto.fromMasterKey(masterKey())
        val plaintext = "hello world, frame 42".toByteArray()
        val wire = crypto.encrypt(plaintext, PacketCrypto.Direction.HostToGuest)
        val decrypted = crypto.decrypt(wire, PacketCrypto.Direction.HostToGuest)
        assertNotNull(decrypted)
        assertArrayEquals(plaintext, decrypted)
        crypto.close()
    }

    @Test
    fun encryptDecrypt_roundTrip_guestToHost() {
        val crypto = PacketCrypto.fromMasterKey(masterKey(0x22))
        val plaintext = ByteArray(128) { (it * 3).toByte() }
        val wire = crypto.encrypt(plaintext, PacketCrypto.Direction.GuestToHost)
        val decrypted = crypto.decrypt(wire, PacketCrypto.Direction.GuestToHost)
        assertArrayEquals(plaintext, decrypted)
        crypto.close()
    }

    @Test
    fun repeatedEncryption_producesDifferentCiphertexts() {
        val crypto = PacketCrypto.fromMasterKey(masterKey())
        val plaintext = "same input".toByteArray()
        val a = crypto.encrypt(plaintext, PacketCrypto.Direction.HostToGuest)
        val b = crypto.encrypt(plaintext, PacketCrypto.Direction.HostToGuest)
        assertFalse("nonces should differ so ciphertext differs", a.contentEquals(b))
        crypto.close()
    }

    @Test
    fun decryptWithWrongDirection_returnsNull() {
        val crypto = PacketCrypto.fromMasterKey(masterKey())
        val plaintext = "one way only".toByteArray()
        val wire = crypto.encrypt(plaintext, PacketCrypto.Direction.HostToGuest)
        val decrypted = crypto.decrypt(wire, PacketCrypto.Direction.GuestToHost)
        assertNull(decrypted)
        crypto.close()
    }

    @Test
    fun tamperedCiphertext_failsTag() {
        val crypto = PacketCrypto.fromMasterKey(masterKey())
        val plaintext = "integrity matters".toByteArray()
        val wire = crypto.encrypt(plaintext, PacketCrypto.Direction.HostToGuest)
        wire[wire.size - 1] = (wire[wire.size - 1].toInt() xor 0x01).toByte()
        val decrypted = crypto.decrypt(wire, PacketCrypto.Direction.HostToGuest)
        assertNull(decrypted)
        crypto.close()
    }

    @Test
    fun independentSubKeys_crossDirection() {
        val crypto = PacketCrypto.fromMasterKey(masterKey(0x33))
        val a = crypto.encrypt("h2g".toByteArray(), PacketCrypto.Direction.HostToGuest)
        val b = crypto.encrypt("g2h".toByteArray(), PacketCrypto.Direction.GuestToHost)
        assertNull(crypto.decrypt(a, PacketCrypto.Direction.GuestToHost))
        assertNull(crypto.decrypt(b, PacketCrypto.Direction.HostToGuest))
        crypto.close()
    }
}
