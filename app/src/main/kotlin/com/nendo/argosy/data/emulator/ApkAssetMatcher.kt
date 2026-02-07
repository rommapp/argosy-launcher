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

        // If user previously selected a variant, try to use it
        if (storedVariant != null) {
            val storedMatch = apkAssets.find { asset ->
                asset.name.contains(storedVariant, ignoreCase = true)
            }
            if (storedMatch != null) {
                return ApkMatchResult.SingleMatch(storedMatch, storedVariant)
            }
        }

        // Check if these are device-type variants (not just ABI variants)
        // If APK names differ by more than just ABI suffix, show picker
        if (hasDeviceTypeVariants(apkAssets)) {
            return ApkMatchResult.MultipleMatches(apkAssets)
        }

        // Try to match by device ABI
        val patterns = ABI_PATTERNS[deviceAbi] ?: ABI_PATTERNS["arm64-v8a"]!!

        for (pattern in patterns) {
            val matches = apkAssets.filter { asset ->
                asset.name.contains(pattern, ignoreCase = true)
            }
            // Only auto-select if exactly one APK matches this ABI
            if (matches.size == 1) {
                return ApkMatchResult.SingleMatch(matches.first(), pattern)
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

    private fun hasDeviceTypeVariants(apkAssets: List<GitHubAsset>): Boolean {
        // Known device-type variant keywords that indicate user should choose
        val deviceVariantKeywords = listOf(
            "chromeos", "handheld", "tablet", "phone", "tv", "desktop",
            "performance", "accuracy", "balanced",
            "mainline", "legacy", "experimental"
        )

        val matchCount = apkAssets.count { asset ->
            val nameLower = asset.name.lowercase()
            deviceVariantKeywords.any { keyword -> nameLower.contains(keyword) }
        }

        // If multiple APKs have device-type keywords, show picker
        return matchCount >= 2
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
