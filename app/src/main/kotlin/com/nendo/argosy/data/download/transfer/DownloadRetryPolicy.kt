package com.nendo.argosy.data.download.transfer

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

internal fun httpDateMillis(value: String?): Long? = try {
    value?.let { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
} catch (_: RuntimeException) {
    null
}

class DownloadRetryPolicy(private val random: () -> Double = { kotlin.random.Random.nextDouble() }) {
    fun delayMillis(
        attempt: Int,
        retryAfter: String? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val base = (1000L shl attempt.coerceIn(0, 6)).coerceAtMost(60_000L)
        val jittered = (base * (0.8 + random().coerceIn(0.0, 1.0) * 0.4)).toLong().coerceAtMost(60_000L)
        val value = retryAfter?.trim()
        val floor = if (value != null && value.matches(Regex("[0-9]+"))) {
            val seconds = value.toLongOrNull() ?: Long.MAX_VALUE
            if (seconds > Long.MAX_VALUE / 1000) Long.MAX_VALUE else seconds * 1000
        } else {
            httpDateMillis(value)?.let {
                if (it <= nowMillis) 0L
                else if (nowMillis < 0 && it > Long.MAX_VALUE + nowMillis) Long.MAX_VALUE
                else it - nowMillis
            } ?: 0L
        }
        return maxOf(jittered, floor.coerceAtMost(MAX_RETRY_AFTER_MS))
    }

    private companion object {
        const val MAX_RETRY_AFTER_MS = 15 * 60_000L
    }
}
