package com.nendo.argosy.data.quaypass.ble

import java.util.UUID

object QuayPassConfig {

    /** 16-bit service UUID in the vendor range; stable across builds, not a secret. */
    const val SERVICE_UUID_16BIT: Int = 0xFD63
    val SERVICE_UUID: UUID = uuidFrom16(SERVICE_UUID_16BIT)
    val CHARACTERISTIC_PROFILE_UUID: UUID = uuidFrom16(SERVICE_UUID_16BIT + 1)
    val CHARACTERISTIC_WRITE_UUID: UUID = uuidFrom16(SERVICE_UUID_16BIT + 2)

    /** SIG "test" range manufacturer id for advert matching; not a security primitive. */
    const val MANUFACTURER_ID: Int = 0xFFFF
    val MAGIC_BYTES: ByteArray = byteArrayOf(0x41, 0x52)

    const val PROTOCOL_MAJOR: Byte = 2
    const val PROTOCOL_MINOR: Byte = 0

    /** Hard ceiling on the whole signed envelope (profile + credential + signature). */
    const val MAX_PROFILE_BYTES: Int = 1536

    /**
     * Sanity cap on the doodle raster field (514 bytes for a 32px canvas).
     */
    const val MAX_AVATAR_RASTER_BYTES: Int = 600
    const val MAX_TEXT_BYTES: Int = 384

    const val MAX_USERNAME_BYTES: Int = 32
    const val MAX_DISPLAY_NAME_BYTES: Int = 32
    const val MAX_GREETING_BYTES: Int = 128
    const val MAX_GAME_TITLE_BYTES: Int = 64
    const val MAX_PLATFORM_SLUG_BYTES: Int = 16

    /** Reject payloads whose timestamp drifts beyond +/- this window (replay guard). */
    const val FRESHNESS_WINDOW_SECS: Long = 5L * 60
    const val NONCE_TTL_SECS: Long = FRESHNESS_WINDOW_SECS

    /** Re-broadcast cooldown per peer credential fingerprint. */
    const val EXCHANGE_COOLDOWN_SECS: Long = 12L * 60 * 60

    /** RSSI floor approximating "same room", not "same building". */
    const val RSSI_THRESHOLD: Int = -80

    const val MAX_CONCURRENT_CONNECTIONS: Int = 4
    const val PER_PEER_WRITE_RATE_LIMIT_SECS: Long = 60

    /**
     * Hard ceiling on a single pass. A passing encounter transfers in well under
     * a second at a negotiated MTU; this bounds the failure case so a stalled peer
     * is dropped rather than lingering. Applies to the client exchange as a whole
     * and to the server-side per-connection watchdog.
     */
    const val EXCHANGE_TIMEOUT_MS: Long = 6_000
    const val CONNECT_TIMEOUT_MS: Long = 3_000
    const val GATT_STAGE_TIMEOUT_MS: Long = 2_000

    const val LENGTH_PREFIX_BYTES: Int = 2
    const val SIGNATURE_BYTES: Int = 64

    /**
     * Profile body after the length prefix:
     * protocol_major(u8) | protocol_minor(u8) | nonce(16) | timestamp(i64 BE) |
     * avatar_len(u16 BE) | avatar_raster(avatar_len) | text_len(u16 BE) | text
     */
    const val NONCE_BYTES: Int = 16
    const val TIMESTAMP_BYTES: Int = 8

    private fun uuidFrom16(short: Int): UUID =
        UUID.fromString("0000${short.toString(16).uppercase().padStart(4, '0')}-0000-1000-8000-00805F9B34FB")
}
