package com.nendo.argosy.data.remote.romm

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import com.nendo.argosy.data.remote.ssl.ClientCertStore
import com.nendo.argosy.data.remote.ssl.ClientCertTarget
import com.nendo.argosy.data.remote.ssl.scopeFor
import com.nendo.argosy.data.remote.ssl.UserCertStore
import com.nendo.argosy.data.remote.ssl.UserCertTrustManager.withUserCertTrust
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.X509TrustManager

private const val TAG = "RomMReachability"
private const val REACHABLE_FRESH_MS = 30_000L
private const val UNREACHABLE_FRESH_MS = 10_000L
private const val PROBE_TIMEOUT_SECONDS = 5L
private const val HEARTBEAT_PATH = "api/heartbeat"

class RomMUnreachableException(message: String) : IOException(message)

private val GATEWAY_FAILURE_CODES = setOf(502, 503, 504)

internal fun isGatewayFailure(code: Int): Boolean = code in GATEWAY_FAILURE_CODES

internal fun heartbeatGotAnyResponse(callFactory: Call.Factory, root: String): Boolean = try {
    callFactory.newCall(Request.Builder().url(root + HEARTBEAT_PATH).build()).execute()
        .use { !isGatewayFailure(it.code) }
} catch (_: Exception) {
    false
}

@Singleton
class RomMReachability @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userCertStore: UserCertStore,
    private val clientCertStore: ClientCertStore
) {
    private val reprobeExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "RomMReprobe").apply { isDaemon = true }
    }
    private val ledger = ReachabilityLedger(
        now = SystemClock::elapsedRealtime,
        probe = { root -> heartbeatGotAnyResponse(probeClient(root), root) },
        runInBackground = reprobeExecutor::execute
    )

    private val probeClients = HashMap<String, OkHttpClient>()
    private var probeClientTrust: X509TrustManager? = null

    private fun probeClient(root: String): OkHttpClient {
        val trust = userCertStore.trustManager()
        return synchronized(probeClients) {
            if (probeClientTrust !== trust) {
                probeClients.clear()
                probeClientTrust = trust
            }
            probeClients.getOrPut(root) {
                OkHttpClient.Builder()
                    .connectTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .readTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .callTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .withUserCertTrust(userCertStore, clientCertStore, ClientCertTarget.ROMM.scopeFor(root))
                    .build()
            }
        }
    }

    val interceptor: Interceptor = Interceptor { chain ->
        val request = chain.request()
        val root = serverRoot(request.url)
        if (root == null || request.url.encodedPath.endsWith(HEARTBEAT_PATH)) {
            return@Interceptor chain.proceed(request)
        }
        if (!isReachable(root)) {
            throw RomMUnreachableException("RomM at $root is not reachable")
        }
        proceedRecording(chain, request, root)
    }

    fun hasActiveNetwork(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun recordReachable(baseUrl: String) = ledger.recordReachable(baseUrl.trimEnd('/') + "/")

    fun mark(): Long = SystemClock.elapsedRealtime()

    fun answeredSince(baseUrl: String, mark: Long): Boolean = ledger.answeredSince(baseUrl.trimEnd('/') + "/", mark)

    fun isReachable(baseUrl: String): Boolean {
        if (!hasActiveNetwork()) return false
        return ledger.isReachable(baseUrl.trimEnd('/') + "/")
    }

    private fun proceedRecording(chain: Interceptor.Chain, request: Request, root: String): Response =
        try {
            chain.proceed(request).also { response ->
                if (isGatewayFailure(response.code)) ledger.recordUnreachable(root) else recordReachable(root)
            }
        } catch (e: IOException) {
            if (chain.call().isCanceled()) throw e
            ledger.recordUnreachable(root)
            throw e
        }

    private fun serverRoot(url: HttpUrl): String? {
        val full = url.toString()
        val apiIndex = full.indexOf("/api/")
        if (apiIndex < 0) return null
        return full.substring(0, apiIndex + 1)
    }
}

internal class ReachabilityLedger(
    private val now: () -> Long,
    private val probe: (String) -> Boolean,
    private val runInBackground: (Runnable) -> Unit
) {
    private val lock = Any()
    private val lastReachableAt = mutableMapOf<String, Long>()
    private val lastUnreachableAt = mutableMapOf<String, Long>()
    private val reprobing = mutableSetOf<String>()

    fun isReachable(root: String): Boolean {
        synchronized(lock) {
            val at = now()
            lastReachableAt[root]?.let { if (at - it < REACHABLE_FRESH_MS) return true }
            lastUnreachableAt[root]?.let { failedAt ->
                if (at - failedAt >= UNREACHABLE_FRESH_MS && reprobing.add(root)) {
                    runInBackground(Runnable { reprobe(root) })
                }
                return false
            }
            return record(root, probe(root))
        }
    }

    fun recordReachable(root: String) {
        synchronized(lock) { record(root, true) }
    }

    fun recordUnreachable(root: String) {
        synchronized(lock) { record(root, false) }
    }

    fun answeredSince(root: String, mark: Long): Boolean =
        synchronized(lock) { lastReachableAt[root]?.let { it >= mark } == true }

    private fun reprobe(root: String) {
        val reachable = probe(root)
        synchronized(lock) {
            reprobing.remove(root)
            record(root, reachable)
        }
        if (reachable) Logger.info(TAG, "heartbeat answered again at $root")
    }

    private fun record(root: String, reachable: Boolean): Boolean {
        if (reachable) {
            lastReachableAt[root] = now()
            lastUnreachableAt.remove(root)
        } else {
            lastReachableAt.remove(root)
            lastUnreachableAt[root] = now()
            Logger.info(TAG, "heartbeat probe failed at $root, failing calls fast until a probe answers")
        }
        return reachable
    }
}
