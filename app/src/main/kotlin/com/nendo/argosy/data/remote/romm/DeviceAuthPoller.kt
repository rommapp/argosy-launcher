package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.util.Logger
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.TimeSource

sealed class DeviceAuthOutcome {
    data class Approved(val token: String) : DeviceAuthOutcome()
    data class AddedAccount(val accountId: Long) : DeviceAuthOutcome()
    data object Denied : DeviceAuthOutcome()
    data object Expired : DeviceAuthOutcome()
    data class Failed(val message: String) : DeviceAuthOutcome()
}

private const val TAG = "DeviceAuthPoller"
private const val SLOW_DOWN_STEP_MS = 5_000L
private const val MAX_INTERVAL_MS = 30_000L
private const val MAX_CONSECUTIVE_FAILURES = 5

/**
 * Runs the RFC 8628 poll loop until the flow resolves, the code expires, or transient failures
 * stop looking transient.
 *
 * Only `access_denied` and `expired_token` end the flow early. Everything else - a dropped
 * request while the browser is foreground, a 5xx, a proxy error page - is retried with backoff,
 * because a device that gives up on one bad response strands a user who is mid-approval. The
 * deadline is measured on a monotonic clock rather than by summing sleeps, so request latency
 * cannot push the loop past the server's real expiry window.
 */
suspend fun pollDeviceAuthUntilResolved(
    interval: Int,
    expiresIn: Int,
    poll: suspend () -> DeviceAuthPoll
): DeviceAuthOutcome {
    val baseIntervalMs = interval.coerceAtLeast(1) * 1000L
    var intervalMs = baseIntervalMs
    var consecutiveFailures = 0
    val started = TimeSource.Monotonic.markNow()
    val deadlineMs = expiresIn.coerceAtLeast(1) * 1000L

    while (currentCoroutineContext().isActive) {
        delay(intervalMs)

        when (val result = poll()) {
            is DeviceAuthPoll.Approved -> return DeviceAuthOutcome.Approved(result.token)
            is DeviceAuthPoll.AddedAccount -> return DeviceAuthOutcome.AddedAccount(result.accountId)
            DeviceAuthPoll.Denied -> return DeviceAuthOutcome.Denied
            DeviceAuthPoll.Expired -> return DeviceAuthOutcome.Expired
            DeviceAuthPoll.Pending -> {
                consecutiveFailures = 0
                intervalMs = (intervalMs - 1000L).coerceAtLeast(baseIntervalMs)
            }
            DeviceAuthPoll.SlowDown -> {
                consecutiveFailures = 0
                intervalMs = (intervalMs + SLOW_DOWN_STEP_MS).coerceAtMost(MAX_INTERVAL_MS)
            }
            is DeviceAuthPoll.Failed -> {
                if (!result.retryable) return DeviceAuthOutcome.Failed(result.message)
                consecutiveFailures++
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    Logger.info(TAG, "giving up after $consecutiveFailures failed polls: ${result.message}")
                    return DeviceAuthOutcome.Failed(result.message)
                }
                Logger.info(
                    TAG,
                    "poll $consecutiveFailures/$MAX_CONSECUTIVE_FAILURES failed, retrying: ${result.message}"
                )
                intervalMs = (intervalMs * 2).coerceAtMost(MAX_INTERVAL_MS)
            }
        }

        if (started.elapsedNow().inWholeMilliseconds >= deadlineMs) break
    }

    return DeviceAuthOutcome.Expired
}
