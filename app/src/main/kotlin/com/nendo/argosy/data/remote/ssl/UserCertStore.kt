package com.nendo.argosy.data.remote.ssl

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

data class ImportedCert(
    val fileName: String,
    val subject: String,
    val issuer: String,
    val expiresAtMillis: Long
)

/**
 * Certificates the user imported by hand, for a server whose issuer the device does not carry.
 *
 * Android trusts a CA only once it is installed through system settings, which several handhelds
 * bury or lock down entirely. A certificate imported here is trusted by this app alone, so it
 * never widens what the rest of the device accepts.
 *
 * [trustManager] is read on the thread that builds an HTTP client, so it serves a value composed
 * during [initialize] or the last [importFrom] rather than touching disk on the caller's thread.
 */
@Singleton
class UserCertStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _certs = MutableStateFlow<List<ImportedCert>>(emptyList())
    val certs: StateFlow<List<ImportedCert>> = _certs.asStateFlow()

    @Volatile
    private var composed: X509TrustManager? = null

    private val certsDir: File
        get() = File(context.filesDir, CERTS_DIR)

    suspend fun initialize() = withContext(Dispatchers.IO) {
        reload()
    }

    fun trustManager(): X509TrustManager? = composed

    suspend fun importFrom(sourcePath: String): Result<ImportedCert> = withContext(Dispatchers.IO) {
        val source = File(sourcePath)
        if (!source.isFile) {
            return@withContext Result.failure(IllegalArgumentException("Not a file: $sourcePath"))
        }

        val parsed = runCatching { source.inputStream().use { readCertificates(it) } }
            .getOrElse { return@withContext Result.failure(it) }

        if (parsed.isEmpty()) {
            return@withContext Result.failure(
                IllegalArgumentException("No X.509 certificate in ${source.name}")
            )
        }

        val target = File(certsDir.apply { mkdirs() }, uniqueNameFor(source.name))
        runCatching { source.copyTo(target, overwrite = true) }
            .onFailure { return@withContext Result.failure(it) }

        reload()
        val imported = _certs.value.firstOrNull { it.fileName == target.name }
            ?: return@withContext Result.failure(IllegalStateException("Import did not take"))
        Result.success(imported)
    }

    suspend fun remove(fileName: String) = withContext(Dispatchers.IO) {
        File(certsDir, fileName).delete()
        reload()
    }

    private fun reload() {
        val files = certsDir.listFiles()?.filter { it.isFile }.orEmpty().sortedBy { it.name }
        val loaded = files.mapNotNull { file ->
            val cert = runCatching { file.inputStream().use { readCertificates(it) } }
                .getOrNull()
                ?.firstOrNull()
                ?: return@mapNotNull null
            ImportedCert(
                fileName = file.name,
                subject = cert.subjectX500Principal.name,
                issuer = cert.issuerX500Principal.name,
                expiresAtMillis = cert.notAfter.time
            )
        }
        _certs.value = loaded
        composed = buildTrustManager(files)
    }

    private fun readCertificates(stream: java.io.InputStream): List<X509Certificate> =
        CertificateFactory.getInstance("X.509")
            .generateCertificates(stream)
            .filterIsInstance<X509Certificate>()

    private fun buildTrustManager(files: List<File>): X509TrustManager? {
        if (files.isEmpty()) return null
        return runCatching {
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null) }
            var index = 0
            files.forEach { file ->
                file.inputStream().use { readCertificates(it) }.forEach { cert ->
                    keyStore.setCertificateEntry("imported-${index++}", cert)
                }
            }
            if (index == 0) return null

            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                .apply { init(keyStore) }
                .trustManagers
                .filterIsInstance<X509TrustManager>()
                .firstOrNull()
        }.getOrNull()
    }

    private fun uniqueNameFor(name: String): String {
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "certificate.pem" }
        if (!File(certsDir, safe).exists()) return safe
        val stem = safe.substringBeforeLast('.', safe)
        val ext = safe.substringAfterLast('.', "")
        var suffix = 2
        while (true) {
            val candidate = if (ext.isEmpty()) "$stem-$suffix" else "$stem-$suffix.$ext"
            if (!File(certsDir, candidate).exists()) return candidate
            suffix++
        }
    }

    private companion object {
        const val CERTS_DIR = "certs"
    }
}
