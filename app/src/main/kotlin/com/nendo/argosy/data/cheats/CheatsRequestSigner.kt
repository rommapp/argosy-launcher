package com.nendo.argosy.data.cheats

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.nendo.argosy.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URLEncoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CheatsRequestSigner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val secret: String = BuildConfig.CHEATSDB_API_SECRET

    data class SignedRequest(
        val deviceToken: String,
        val timestamp: Long,
        val signature: String,
        val fpHash: String
    )

    fun sign(deviceToken: String, query: String): SignedRequest? {
        if (secret.isBlank()) return null

        val timestamp = System.currentTimeMillis() / 1000
        val fpHash = collectFingerprint().hashCode().toString()
        val encodedQuery = URLEncoder.encode(query, "UTF-8")

        val payload = "$deviceToken|$timestamp|$encodedQuery|$fpHash"
        val signature = hmacSha256(payload, secret)

        return SignedRequest(
            deviceToken = deviceToken,
            timestamp = timestamp,
            signature = signature,
            fpHash = fpHash
        )
    }

    fun isConfigured(): Boolean = secret.isNotBlank()

    private fun collectFingerprint(): String {
        return listOf(
            Build.FINGERPRINT,
            Build.MODEL,
            Build.MANUFACTURER,
            Build.BOARD,
            getAndroidId(),
            getPackageSignatureHash()
        ).joinToString("|")
    }

    private fun getAndroidId(): String {
        return try {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    @Suppress("DEPRECATION")
    private fun getPackageSignatureHash(): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.GET_SIGNATURES
                )
            }

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                packageInfo.signatures
            }

            signatures?.firstOrNull()?.hashCode()?.toString() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun hmacSha256(data: String, key: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
