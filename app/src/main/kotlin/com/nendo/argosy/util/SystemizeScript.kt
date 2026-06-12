package com.nendo.argosy.util

import android.content.Context
import android.os.Build
import com.nendo.argosy.R
import com.nendo.argosy.data.storage.StoragePathUtils
import java.io.File

data class VendorSteps(val deviceLabel: String, val steps: List<String>)

sealed interface SystemizeWriteResult {
    data class Success(val scriptPath: String, val vendor: VendorSteps) : SystemizeWriteResult
    data class Error(val message: String) : SystemizeWriteResult
}

object SystemizeScript {
    private const val SCRIPT_NAME = "argosy-systemize.sh"

    fun targetPath(): String = "${StoragePathUtils.primaryExternalRoot}/$SCRIPT_NAME"

    fun write(context: Context): SystemizeWriteResult {
        return try {
            val target = File(targetPath())
            context.resources.openRawResource(R.raw.systemize_argosy).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            SystemizeWriteResult.Success(target.absolutePath, vendorSteps())
        } catch (e: Exception) {
            SystemizeWriteResult.Error(e.message ?: "Could not write the script")
        }
    }

    fun vendorSteps(): VendorSteps {
        val fields = listOf(Build.MANUFACTURER, Build.BRAND, Build.MODEL, Build.DEVICE)
            .joinToString(" ") { it.lowercase() }
        val finalStep = "Set Argosy as your default launcher"
        return when {
            "retroid" in fields -> VendorSteps(
                deviceLabel = Build.MODEL,
                steps = listOf(
                    "Open Settings > Advanced > Run script as Root",
                    "Tap Select a script and choose \"$SCRIPT_NAME\"",
                    "Confirm Run, then reboot when it finishes",
                    finalStep
                )
            )
            "ayn" in fields || "odin" in fields -> VendorSteps(
                deviceLabel = Build.MODEL,
                steps = listOf(
                    "Open Settings > Advanced Settings > Run script as Root",
                    "Tap Select a script and choose \"$SCRIPT_NAME\"",
                    "Confirm Run, then reboot when it finishes",
                    finalStep
                )
            )
            "ayaneo" in fields || "konkr" in fields || "pocket fit" in fields -> VendorSteps(
                deviceLabel = Build.MODEL,
                steps = listOf(
                    "Open Settings > Optimization Assistant > Root Script",
                    "Add (+) and import \"$SCRIPT_NAME\" from internal storage",
                    "Run it (Full Script Execution), then reboot when it finishes",
                    finalStep
                )
            )
            else -> VendorSteps(
                deviceLabel = Build.MODEL,
                steps = listOf(
                    "Open your device's \"run script as root\" setting",
                    "Select \"$SCRIPT_NAME\" from internal storage",
                    "Confirm and reboot when it finishes",
                    finalStep
                )
            )
        }
    }
}
