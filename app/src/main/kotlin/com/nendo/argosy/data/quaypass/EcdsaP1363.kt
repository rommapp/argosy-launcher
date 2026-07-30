package com.nendo.argosy.data.quaypass

/** Converts ECDSA signatures between ASN.1 DER and fixed-width P1363 (r||s) form. */
object EcdsaP1363 {

    fun fromDer(der: ByteArray, componentBytes: Int): ByteArray {
        require(der.isNotEmpty() && der[0] == 0x30.toByte()) { "not a DER sequence" }
        var i = 2
        if (der[1].toInt() and 0x80 != 0) i += der[1].toInt() and 0x7F
        val (r, afterR) = readInteger(der, i)
        val (s, _) = readInteger(der, afterR)
        return pad(r, componentBytes) + pad(s, componentBytes)
    }

    fun toDer(p1363: ByteArray): ByteArray {
        require(p1363.size % 2 == 0) { "odd P1363 length" }
        val half = p1363.size / 2
        val body = encodeInteger(p1363.copyOfRange(0, half)) +
            encodeInteger(p1363.copyOfRange(half, p1363.size))
        return if (body.size < 128) {
            byteArrayOf(0x30, body.size.toByte()) + body
        } else {
            byteArrayOf(0x30, 0x81.toByte(), body.size.toByte()) + body
        }
    }

    private fun readInteger(bytes: ByteArray, offset: Int): Pair<ByteArray, Int> {
        require(offset + 1 < bytes.size && bytes[offset] == 0x02.toByte()) { "expected DER integer" }
        val len = bytes[offset + 1].toInt() and 0xFF
        val start = offset + 2
        require(start + len <= bytes.size) { "DER integer overruns buffer" }
        var valueStart = start
        while (valueStart < start + len - 1 && bytes[valueStart] == 0.toByte()) valueStart++
        return bytes.copyOfRange(valueStart, start + len) to (start + len)
    }

    private fun encodeInteger(value: ByteArray): ByteArray {
        var start = 0
        while (start < value.size - 1 && value[start] == 0.toByte()) start++
        val stripped = value.copyOfRange(start, value.size)
        val content = if (stripped[0].toInt() and 0x80 != 0) byteArrayOf(0) + stripped else stripped
        return byteArrayOf(0x02, content.size.toByte()) + content
    }

    private fun pad(value: ByteArray, size: Int): ByteArray {
        require(value.size <= size) { "integer wider than $size bytes" }
        return ByteArray(size - value.size) + value
    }
}
