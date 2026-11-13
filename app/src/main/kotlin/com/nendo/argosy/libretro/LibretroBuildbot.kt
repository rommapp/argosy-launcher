package com.nendo.argosy.libretro

import android.os.Build
import android.os.Process
import android.util.Log

private const val TAG = "LibretroBuildbot"

object LibretroBuildbot {
    private val SUPPORTED_ABIS = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

    /**
     * The ABI of the currently running Argosy process. This is determined by the APK variant
     * that was installed -- arm32 APK on a 64-bit device still runs as a 32-bit process and
     * cannot dlopen 64-bit native libraries. We pick the ABI from the bitness-specific device
     * list, not the full SUPPORTED_ABIS list, so a 32-bit install on a 64-bit device correctly
     * identifies as armeabi-v7a instead of silently preferring arm64-v8a.
     */
    val detectedAbi: String by lazy {
        val is64Bit = Process.is64Bit()
        val bitnessAbis = (if (is64Bit) Build.SUPPORTED_64_BIT_ABIS else Build.SUPPORTED_32_BIT_ABIS)
            ?.toList().orEmpty()
        val fallback = if (is64Bit) "arm64-v8a" else "armeabi-v7a"
        val selected = bitnessAbis.firstOrNull { it in SUPPORTED_ABIS } ?: fallback
        Log.i(TAG, "Process is64Bit=$is64Bit, bitness ABIs=$bitnessAbis, selected=$selected")
        selected
    }

    val processIs64Bit: Boolean
        get() = Process.is64Bit()

    var abiOverride: String? = null
        set(value) {
            if (value != null && value !in SUPPORTED_ABIS) {
                Log.w(TAG, "Ignoring unknown ABI override: $value")
                return
            }
            if (value != null && !isAbiCompatibleWithProcess(value)) {
                Log.w(TAG, "Ignoring ABI override '$value' (incompatible with process bitness is64Bit=${Process.is64Bit()})")
                return
            }
            field = value
            Log.i(TAG, "ABI override set to: ${value ?: "(none, using detected: $detectedAbi)"}")
        }

    /** True if [abi] matches the bitness of the running process. */
    fun isAbiCompatibleWithProcess(abi: String): Boolean {
        val abiIs64Bit = abi == "arm64-v8a" || abi == "x86_64"
        return abiIs64Bit == Process.is64Bit()
    }

    val deviceAbi: String
        get() = abiOverride ?: detectedAbi

    val baseUrl: String
        get() = baseUrlFor(deviceAbi)

    fun baseUrlFor(abi: String): String = "https://buildbot.libretro.com/nightly/android/latest/$abi"
}
