package com.nendo.argosy.libretro

import android.os.Build
import android.util.Log

private const val TAG = "LibretroBuildbot"

object LibretroBuildbot {
    private val SUPPORTED_ABIS = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

    val deviceAbi: String by lazy {
        val allAbis = Build.SUPPORTED_ABIS.toList()
        val selected = allAbis.firstOrNull { it in SUPPORTED_ABIS } ?: "arm64-v8a"
        Log.i(TAG, "Device ABIs: $allAbis, selected: $selected")
        selected
    }

    val baseUrl: String by lazy {
        "https://buildbot.libretro.com/nightly/android/latest/$deviceAbi"
    }
}
