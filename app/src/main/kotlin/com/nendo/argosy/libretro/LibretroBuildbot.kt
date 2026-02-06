package com.nendo.argosy.libretro

import android.os.Build

object LibretroBuildbot {
    private val SUPPORTED_ABIS = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

    val deviceAbi: String by lazy {
        Build.SUPPORTED_ABIS.firstOrNull { it in SUPPORTED_ABIS } ?: "arm64-v8a"
    }

    val baseUrl: String by lazy {
        "https://buildbot.libretro.com/nightly/android/latest/$deviceAbi"
    }
}
