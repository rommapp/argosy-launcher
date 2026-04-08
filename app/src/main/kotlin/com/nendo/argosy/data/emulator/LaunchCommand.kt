package com.nendo.argosy.data.emulator

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.nendo.argosy.util.Logger

private const val TAG = "LaunchCommand"

enum class LaunchMethod { INTENT, SHELL }

enum class RomPathFormat {
    AUTO,
    ABSOLUTE_PATH,
    FILE_PROVIDER,
    DOCUMENT_URI
}

/**
 * - [NONE]: slot is cleared -- don't bind the ROM here.
 * - [ABSOLUTE_PATH]: raw filesystem path (for data slot, an opaque URI wrapping the path).
 * - [FILE_PROVIDER]: content:// URI via Argosy's FileProvider, with URI grant + clipData delegation.
 * - [DOCUMENT_URI]: content:// URI from DocumentsContract.buildDocumentUri (plain document form,
 *   not tree-delegated).
 */
enum class RomBindingFormat {
    NONE,
    ABSOLUTE_PATH,
    FILE_PROVIDER,
    DOCUMENT_URI
}

sealed class ResolvedExtra {
    abstract val key: String
    data class StringExtra(override val key: String, val value: String) : ResolvedExtra()
    data class UriExtra(override val key: String, val uri: Uri) : ResolvedExtra()
    data class BoolExtra(override val key: String, val value: Boolean) : ResolvedExtra()
    data class StringArrayExtra(override val key: String, val values: Array<String>) : ResolvedExtra() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is StringArrayExtra) return false
            return key == other.key && values.contentEquals(other.values)
        }
        override fun hashCode(): Int = 31 * key.hashCode() + values.contentHashCode()
    }
}

data class EffectiveLaunchCommand(
    val action: String,
    val packageName: String,
    val activityClass: String?,
    val categories: List<String> = listOf(Intent.CATEGORY_DEFAULT),
    val dataUri: Uri? = null,
    val mimeType: String? = null,
    val extras: List<ResolvedExtra> = emptyList(),
    val intentFlags: Int,
    val grantReadUriTo: List<Uri> = emptyList(),
    val clipDataUri: Uri? = null,
    val launchMethod: LaunchMethod = LaunchMethod.INTENT
)

fun EffectiveLaunchCommand.toIntent(context: Context): Intent {
    for (uri in grantReadUriTo) {
        try {
            context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            Logger.warn(TAG, "Failed to grant URI permission to $packageName for $uri", e)
        }
    }

    val cmdCategories = categories
    val cmdExtras = extras
    val cmdClipDataUri = clipDataUri
    val cmdFlags = intentFlags

    val intent = Intent(action)
    if (activityClass != null) {
        intent.component = ComponentName(packageName, activityClass)
    } else {
        intent.setPackage(packageName)
    }
    for (category in cmdCategories) {
        intent.addCategory(category)
    }
    if (dataUri != null) {
        if (mimeType != null) {
            intent.setDataAndType(dataUri, mimeType)
        } else {
            intent.data = dataUri
        }
    }
    for (extra in cmdExtras) {
        when (extra) {
            is ResolvedExtra.StringExtra -> intent.putExtra(extra.key, extra.value)
            is ResolvedExtra.UriExtra -> intent.putExtra(extra.key, extra.uri)
            is ResolvedExtra.BoolExtra -> intent.putExtra(extra.key, extra.value)
            is ResolvedExtra.StringArrayExtra -> intent.putExtra(extra.key, extra.values)
        }
    }
    if (cmdClipDataUri != null) {
        intent.clipData = ClipData.newRawUri(null, cmdClipDataUri)
    }
    if (cmdFlags != 0) {
        intent.addFlags(cmdFlags)
    }

    val hasContentUri = dataUri?.scheme == "content" ||
        cmdExtras.any { it is ResolvedExtra.UriExtra && it.uri.scheme == "content" } ||
        cmdClipDataUri?.scheme == "content" ||
        grantReadUriTo.isNotEmpty()
    if (hasContentUri) {
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return intent
}

private fun shellEscape(value: String): String =
    "'" + value.replace("'", "'\\''") + "'"

fun EffectiveLaunchCommand.toShellArgv(): Array<String> {
    val cmd = StringBuilder("/system/bin/am start")
    cmd.append(" -a ").append(shellEscape(action))

    if (activityClass != null) {
        cmd.append(" -n ").append(shellEscape("$packageName/$activityClass"))
    } else {
        cmd.append(" -p ").append(shellEscape(packageName))
    }

    categories.forEach { category ->
        cmd.append(" -c ").append(shellEscape(category))
    }

    if (dataUri != null) {
        cmd.append(" -d ").append(shellEscape(dataUri.toString()))
    }

    if (mimeType != null) {
        cmd.append(" -t ").append(shellEscape(mimeType))
    }

    if (intentFlags != 0) {
        cmd.append(" -f 0x").append(intentFlags.toString(16))
    }

    if (clipDataUri != null || grantReadUriTo.isNotEmpty()) {
        cmd.append(" --grant-read-uri-permission")
    }

    extras.forEach { extra ->
        when (extra) {
            is ResolvedExtra.StringExtra -> {
                cmd.append(" --es ").append(shellEscape(extra.key))
                    .append(" ").append(shellEscape(extra.value))
            }
            is ResolvedExtra.UriExtra -> {
                cmd.append(" --eu ").append(shellEscape(extra.key))
                    .append(" ").append(shellEscape(extra.uri.toString()))
            }
            is ResolvedExtra.BoolExtra -> {
                cmd.append(" --ez ").append(shellEscape(extra.key))
                    .append(" ").append(extra.value)
            }
            is ResolvedExtra.StringArrayExtra -> {
                cmd.append(" --esa ").append(shellEscape(extra.key))
                    .append(" ").append(shellEscape(extra.values.joinToString(",")))
            }
        }
    }

    return arrayOf("sh", "-c", cmd.toString())
}
