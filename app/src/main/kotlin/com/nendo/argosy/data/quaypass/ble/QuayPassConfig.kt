package com.nendo.argosy.data.quaypass.ble

import java.util.UUID

object QuayPassConfig {

    // 16-bit service UUID in vendor range. Stable across builds; not a secret.
    const val SERVICE_UUID_16BIT: Int = 0xFD63
    val SERVICE_UUID: UUID = uuidFrom16(SERVICE_UUID_16BIT)
    val CHARACTERISTIC_PROFILE_UUID: UUID = uuidFrom16(SERVICE_UUID_16BIT + 1)
    val CHARACTERISTIC_WRITE_UUID: UUID = uuidFrom16(SERVICE_UUID_16BIT + 2)

    // BLE manufacturer ID and magic prefix for advertisement packet matching.
    // Picked from the Bluetooth SIG "test" range; not a security primitive.
    const val MANUFACTURER_ID: Int = 0xFFFF
    val MAGIC_BYTES: ByteArray = byteArrayOf(0x41, 0x52)  // 'A','R'

    // Wire format version. Increment major on breaking changes.
    const val PROTOCOL_MAJOR: Byte = 1
    const val PROTOCOL_MINOR: Byte = 0

    // Hard ceiling on a single profile_payload_bytes + credential_bytes + sig.
    const val MAX_PROFILE_BYTES: Int = 1024

    // Avatar fixed-size block (bit-packed; see QuayPassAvatar).
    const val AVATAR_BLOCK_BYTES: Int = 32

    // Text section budget (CBOR map with strict per-field caps).
    const val MAX_TEXT_BYTES: Int = 384

    // Per-field caps (bytes, UTF-8).
    const val MAX_USERNAME_BYTES: Int = 32
    const val MAX_DISPLAY_NAME_BYTES: Int = 32
    const val MAX_GREETING_BYTES: Int = 128
    const val MAX_GAME_TITLE_BYTES: Int = 64
    const val MAX_PLATFORM_SLUG_BYTES: Int = 16

    // Replay window. Reject payloads with timestamp drift outside [-FRESH, +FRESH].
    const val FRESHNESS_WINDOW_SECS: Long = 5L * 60

    // Per-credential nonce TTL (matches freshness window).
    const val NONCE_TTL_SECS: Long = FRESHNESS_WINDOW_SECS

    // Rebroadcast cooldown per peer credential pubkey fingerprint.
    const val EXCHANGE_COOLDOWN_SECS: Long = 12L * 60 * 60

    // RSSI floor - rough proxy for "in the same room", not "same building".
    const val RSSI_THRESHOLD: Int = -80

    // GATT-server hardening.
    const val MAX_CONCURRENT_CONNECTIONS: Int = 4
    const val PER_PEER_WRITE_RATE_LIMIT_SECS: Long = 60

    // Length prefix size on the wire envelope (u16 big-endian).
    const val LENGTH_PREFIX_BYTES: Int = 2

    // Ed25519 signature length.
    const val SIGNATURE_BYTES: Int = 64

    // Profile struct fixed header (after length prefix):
    // protocol_major (u8) | protocol_minor (u8) | nonce (16) | timestamp (i64 BE) | avatar (AVATAR_BLOCK_BYTES)
    const val NONCE_BYTES: Int = 16
    const val TIMESTAMP_BYTES: Int = 8

    private fun uuidFrom16(short: Int): UUID =
        UUID.fromString("0000${short.toString(16).uppercase().padStart(4, '0')}-0000-1000-8000-00805F9B34FB")
}
