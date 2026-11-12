package com.nendo.argosy.data.model

import com.squareup.moshi.JsonClass

/**
 * The newest Argosy release a check found, with the one apk asset this build installs.
 * [assetDigest] is GitHub's asset digest (`sha256:<hex>`), or null when the release has none.
 */
@JsonClass(generateAdapter = true)
data class AppUpdateRelease(
    val tag: String,
    val name: String,
    val notes: String?,
    val assetName: String,
    val assetUrl: String,
    val assetSize: Long,
    val assetDigest: String?,
    val abi: String
) {
    val displayVersion: String get() = tag.removePrefix("v")
}

sealed interface AppUpdateDownload {
    data object Idle : AppUpdateDownload
    data class Downloading(val tag: String, val progress: Int) : AppUpdateDownload
    data class HandedOff(val tag: String) : AppUpdateDownload
    data class Failed(val tag: String, val message: String) : AppUpdateDownload
}
