package com.nendo.argosy.data.remote.ssl

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

data class ClientCertInfo(
    val displayName: String,
    val subject: String,
    val issuer: String,
    val expiresAtMillis: Long,
    val targets: Set<ClientCertTarget>
)

/**
 * The one client certificate the user imported from a PKCS#12 file, and the servers it is
 * presented to.
 *
 * [identityFor] runs on the thread performing a TLS handshake and answers from memory, loading
 * from the vault on first use.
 */
@Singleton
class ClientCertStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vault: ClientKeyVault
) {
    private val _info = MutableStateFlow<ClientCertInfo?>(null)
    val info: StateFlow<ClientCertInfo?> = _info.asStateFlow()

    private val _changedFor = MutableSharedFlow<ClientCertTarget>(extraBufferCapacity = 8)

    /**
     * A target whose certificate a user action just changed: imported, replaced, removed, or
     * switched on or off for it.
     */
    val changedFor: SharedFlow<ClientCertTarget> = _changedFor.asSharedFlow()

    private val _revision = AtomicInteger()

    /**
     * Increments on every change to the certificate or its targets. A connection or TLS session
     * opened under an earlier revision is stale.
     */
    val revision: Int get() = _revision.get()

    @Volatile
    private var identity: ClientIdentity? = null

    @Volatile
    private var loaded = false

    private val lock = Any()
    private val hostsByTarget = ConcurrentHashMap<ClientCertTarget, Set<String>>()
    private val unansweredRequests = ConcurrentHashMap<String, Long>()

    @Volatile
    private var alias: String? = null

    private val metaFile: File
        get() = File(File(context.filesDir, DIR), META_FILE)

    suspend fun initialize() = withContext(Dispatchers.IO) { ensureLoaded() }

    suspend fun importFrom(
        sourcePath: String,
        password: CharArray,
        enableFor: ClientCertTarget
    ): Result<ClientCertInfo> = withContext(Dispatchers.IO) {
        runCatching {
            ensureLoaded()
            val source = File(sourcePath)
            if (!source.isFile) fail(ClientCertImportException.Reason.NOT_A_FILE)
            val parsed = source.inputStream().use { Pkcs12Reader.read(it, password) }
            if (parsed.chain.first().notAfter.time < System.currentTimeMillis()) {
                fail(ClientCertImportException.Reason.EXPIRED)
            }
            val info = synchronized(lock) {
                val previousAlias = alias
                val nextAlias = ALIASES.first { it != previousAlias }
                val stored = runCatching {
                    vault.put(nextAlias, parsed)
                    vault.get(nextAlias)
                }.getOrNull()
                if (stored == null) {
                    runCatching { vault.delete(nextAlias) }
                    fail(ClientCertImportException.Reason.UNSUPPORTED_KEY)
                }
                val targets = _info.value?.targets.orEmpty() + enableFor
                writeMeta(nextAlias, targets)
                if (previousAlias != null) runCatching { vault.delete(previousAlias) }
                alias = nextAlias
                identity = stored
                loaded = true
                _revision.incrementAndGet()
                infoOf(stored, targets).also { _info.value = it }
            }
            info.targets.forEach { _changedFor.tryEmit(it) }
            info
        }
    }

    suspend fun remove() = withContext(Dispatchers.IO) {
        val lost = synchronized(lock) {
            _info.value?.targets.orEmpty().also { forget() }
        }
        lost.forEach { _changedFor.tryEmit(it) }
    }

    suspend fun setTargetEnabled(target: ClientCertTarget, enabled: Boolean) =
        withContext(Dispatchers.IO) {
            val changed = synchronized(lock) {
                val current = _info.value ?: return@synchronized false
                val currentAlias = alias ?: return@synchronized false
                val targets = if (enabled) current.targets + target else current.targets - target
                if (targets == current.targets) return@synchronized false
                writeMeta(currentAlias, targets)
                _revision.incrementAndGet()
                _info.value = current.copy(targets = targets)
                true
            }
            if (changed) _changedFor.tryEmit(target)
        }

    fun updateHosts(target: ClientCertTarget, urls: Collection<String>) {
        val hosts = urls.mapNotNull(::hostOf).toSet()
        if (hostsByTarget.put(target, hosts) != hosts) _revision.incrementAndGet()
    }

    fun recordCertificateRequest(host: String?, presented: Boolean) {
        val peer = host?.lowercase() ?: return
        if (presented) {
            unansweredRequests.remove(peer)
        } else {
            unansweredRequests[peer] = nowMillis()
        }
    }

    /**
     * Whether a handshake with [url]'s host in the last [UNANSWERED_REQUEST_WINDOW_MS] asked for a
     * client certificate and got none, the one signal that survives a proxy ending the connection
     * without a readable reason.
     */
    fun requestedWithoutCertificate(url: String): Boolean {
        val at = hostOf(url)?.let { unansweredRequests[it] } ?: return false
        return nowMillis() - at <= UNANSWERED_REQUEST_WINDOW_MS
    }

    fun identityFor(scope: ClientCertScope, host: String?): ClientIdentity? {
        ensureLoaded()
        val current = identity ?: return null
        val targets = _info.value?.targets ?: return null
        if (host != null && !UserCertTrustManager.acceptsImportedCertFor(host)) return null
        val allowed = when (scope) {
            is ClientCertScope.Dedicated -> scope.target in targets
            ClientCertScope.ByHost -> {
                val peer = host?.lowercase() ?: return null
                targets.any { peer in hostsByTarget[it].orEmpty() }
            }
        }
        return current.takeIf { allowed }
    }

    private fun nowMillis(): Long = System.nanoTime() / 1_000_000

    private fun forget() {
        ALIASES.forEach { runCatching { vault.delete(it) } }
        metaFile.delete()
        alias = null
        identity = null
        loaded = true
        _revision.incrementAndGet()
        _info.value = null
    }

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            loaded = reload()
        }
    }

    private fun reload(): Boolean {
        val meta = readMeta()
        if (meta == null) {
            alias = null
            identity = null
            _info.value = null
            return true
        }
        val stored = try {
            vault.get(meta.alias)
        } catch (e: Exception) {
            return false
        }
        if (stored == null) {
            metaFile.delete()
            alias = null
            identity = null
            _info.value = null
            return true
        }
        alias = meta.alias
        identity = stored
        _revision.incrementAndGet()
        _info.value = infoOf(stored, meta.targets)
        return true
    }

    private class Meta(val alias: String, val targets: Set<ClientCertTarget>)

    private fun readMeta(): Meta? {
        val file = metaFile
        if (!file.isFile) return null
        val props = runCatching { Properties().apply { file.inputStream().use { load(it) } } }
            .getOrNull() ?: return null
        val targets = props.getProperty(KEY_TARGETS).orEmpty()
            .split(',')
            .mapNotNull { ClientCertTarget.fromToken(it.trim()) }
            .toSet()
        val storedAlias = props.getProperty(KEY_ALIAS)?.takeIf { it in ALIASES } ?: ALIASES.first()
        return Meta(storedAlias, targets)
    }

    private fun writeMeta(keyAlias: String, targets: Set<ClientCertTarget>) {
        val file = metaFile
        file.parentFile?.mkdirs()
        val props = Properties().apply {
            setProperty(KEY_ALIAS, keyAlias)
            setProperty(KEY_TARGETS, targets.joinToString(",") { it.token })
        }
        file.outputStream().use { props.store(it, null) }
    }

    private fun infoOf(id: ClientIdentity, targets: Set<ClientCertTarget>): ClientCertInfo {
        val leaf = id.chain.first()
        val subject = leaf.subjectX500Principal.name
        return ClientCertInfo(
            displayName = COMMON_NAME.find(subject)?.groupValues?.get(1)?.trim() ?: subject,
            subject = subject,
            issuer = leaf.issuerX500Principal.name,
            expiresAtMillis = leaf.notAfter.time,
            targets = targets
        )
    }

    companion object {
        const val DIR = "client_cert"
        private const val META_FILE = "identity.properties"
        private const val KEY_TARGETS = "targets"
        private const val KEY_ALIAS = "alias"
        private val ALIASES = listOf("argosy-client-certificate", "argosy-client-certificate-b")
        const val UNANSWERED_REQUEST_WINDOW_MS = 30_000L
        private val COMMON_NAME = Regex("(?:^|,)\\s*CN=([^,]+)")

        fun hostOf(url: String): String? {
            val trimmed = url.trim()
            if (trimmed.isEmpty()) return null
            val withScheme = if ("://" in trimmed) trimmed else "https://${trimmed.removePrefix("//")}"
            return withScheme.toHttpUrlOrNull()?.host?.lowercase()
        }

        private fun fail(reason: ClientCertImportException.Reason): Nothing =
            throw ClientCertImportException(reason)
    }
}
