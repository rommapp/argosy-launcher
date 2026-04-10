package com.nendo.argosy.data.netplay

class ReplayWindow(private val capacity: Int = DEFAULT_CAPACITY) {

    private val seen = object : LinkedHashSet<NonceKey>() {
        fun evictIfNeeded() {
            while (size > capacity) {
                val it = iterator()
                if (it.hasNext()) {
                    it.next()
                    it.remove()
                }
            }
        }
    }

    @Synchronized
    fun checkAndRecord(nonce: ByteArray): Boolean {
        val key = NonceKey(nonce)
        if (!seen.add(key)) return false
        seen.evictIfNeeded()
        return true
    }

    @Synchronized
    fun size(): Int = seen.size

    private class NonceKey(bytes: ByteArray) {
        private val bytes: ByteArray = bytes.copyOf()
        private val hash: Int = bytes.contentHashCode()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is NonceKey) return false
            return bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int = hash
    }

    companion object {
        const val DEFAULT_CAPACITY = 1024
    }
}
