package com.nendo.argosy.data.quaypass

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Stable per-install identifier; identity only, not a credential. */
@Singleton
class ClientFingerprint @Inject constructor(
    @ApplicationContext private val context: Context
) {

    val deviceToken: String by lazy {
        try {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: ""
        } catch (_: Throwable) {
            ""
        }
    }

    val fingerprintHash: String by lazy {
        val canonical = listOf(
            Build.FINGERPRINT.orEmpty(),
            Build.MODEL.orEmpty(),
            Build.MANUFACTURER.orEmpty(),
            Build.BOARD.orEmpty(),
            deviceToken,
            apkSigningCertHash
        ).joinToString("|")
        sha256Hex(canonical.toByteArray(Charsets.UTF_8))
    }

    val apkSigningCertHash: String by lazy {
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
            }
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }
            val firstSig = signatures?.firstOrNull()?.toByteArray()
                ?: return@lazy ""
            sha256Hex(firstSig)
        } catch (_: Throwable) {
            ""
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
