package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.remote.github.GitHubAsset
import com.nendo.argosy.libretro.LibretroBuildbot

sealed class ApkMatchResult {
    data class SingleMatch(val asset: GitHubAsset, val variant: String?) : ApkMatchResult()
    data class MultipleMatches(val assets: List<GitHubAsset>) : ApkMatchResult()
    data object NoMatch : ApkMatchResult()
}

object ApkAssetMatcher {

    private val ABI_PATTERNS = mapOf(
        "arm64-v8a" to listOf("arm64-v8a", "arm64", "aarch64", "64bit", "a64"),
        "armeabi-v7a" to listOf("armeabi-v7a", "armeabi", "arm32", "armv7", "32bit", "a32"),
        "x86_64" to listOf("x86_64", "x86-64", "x64"),
        "x86" to listOf("x86", "i686", "i386")
    )

    private val UNIVERSAL_PATTERNS = listOf("universal", "all", "fat", "multi")

    fun matchApk(
        assets: List<GitHubAsset>,
        deviceAbi: String = LibretroBuildbot.deviceAbi,
        storedVariant: String? = null
    ): ApkMatchResult {
        val apkAssets = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }

        if (apkAssets.isEmpty()) return ApkMatchResult.NoMatch
        if (apkAssets.size == 1) return ApkMatchResult.SingleMatch(apkAssets.first(), null)

        if (storedVariant != null) {
            val storedMatch = apkAssets.find { asset ->
                asset.name.contains(storedVariant, ignoreCase = true)
            }
            if (storedMatch != null) {
                return ApkMatchResult.SingleMatch(storedMatch, storedVariant)
            }
        }

        val patterns = ABI_PATTERNS[deviceAbi] ?: ABI_PATTERNS["arm64-v8a"]!!

        for (pattern in patterns) {
            val match = apkAssets.find { asset ->
                asset.name.contains(pattern, ignoreCase = true)
            }
            if (match != null) {
                return ApkMatchResult.SingleMatch(match, pattern)
            }
        }

        for (pattern in UNIVERSAL_PATTERNS) {
            val match = apkAssets.find { asset ->
                asset.name.contains(pattern, ignoreCase = true)
            }
            if (match != null) {
                return ApkMatchResult.SingleMatch(match, "universal")
            }
        }

        return ApkMatchResult.MultipleMatches(apkAssets)
    }

    fun extractVariantFromAssetName(assetName: String): String? {
        val nameLower = assetName.lowercase()

        for ((abi, patterns) in ABI_PATTERNS) {
            if (patterns.any { nameLower.contains(it) }) {
                return abi
            }
        }

        if (UNIVERSAL_PATTERNS.any { nameLower.contains(it) }) {
            return "universal"
        }

        return null
    }

    fun formatVariantDisplay(variant: String?): String = when (variant) {
        "arm64-v8a", "arm64", "aarch64", "64bit", "a64" -> "ARM64"
        "armeabi-v7a", "armeabi", "arm32", "armv7", "32bit", "a32" -> "ARM32"
        "x86_64", "x86-64", "x64" -> "x86_64"
        "x86", "i686", "i386" -> "x86"
        "universal", "all", "fat", "multi" -> "Universal"
        null -> "Default"
        else -> variant.replaceFirstChar { it.uppercase() }
    }
}
