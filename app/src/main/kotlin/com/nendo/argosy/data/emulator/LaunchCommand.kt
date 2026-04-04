package com.nendo.argosy.data.emulator

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.nendo.argosy.util.Logger

private const val TAG = "LaunchCommand"

/**
 * How the emulator is invoked: a standard `Context.startActivity` intent, or a fire-and-forget
 * `am start` shell command. Shell mode bypasses the caller-side URI permission model and matches
 * how Daijisho / ES-DE launch most emulators.
 */
enum class LaunchMethod { INTENT, SHELL }

/**
 * How ROM path values are represented in the outgoing extras or data URI. `AUTO` preserves whatever
 * the [LaunchConfig] declared via its [ExtraValue] mapping; the other values rewrite every
 * path-typed extra consistently.
 */
enum class RomPathFormat {
    AUTO,
    ABSOLUTE_PATH,
    FILE_PROVIDER,
    DOCUMENT_URI
}

/**
 * Strongly-typed representation of a single Intent extra, independent of Android's untyped
 * `putExtra` API. Drives both the Intent builder and the `am start` shell argv builder.
 */
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

/**
 * Fully-resolved launch description for an emulator. Produced by `GameLauncher.buildEffectiveCommand`
 * from an [EmulatorDef.launchConfig] plus any user override, then converted to either an [Intent]
 * (via [toIntent]) or a shell argv array (via [toShellArgv]) depending on [launchMethod].
 *
 * This is the single description the settings UI's Launch Args modal edits and the single
 * description `launchViaShell` and the intent builder consume.
 */
data class EffectiveLaunchCommand(
    val action: String,
    val packageName: String,
    val activityClass: String?,
    val categories: List<String> = listOf(Intent.CATEGORY_DEFAULT),
    val dataUri: Uri? = null,
    val mimeType: String? = null,
    val extras: List<ResolvedExtra> = emptyList(),
    val intentFlags: Int,
    /** URIs to pre-grant read permission to the receiver package before launch. */
    val grantReadUriTo: List<Uri> = emptyList(),
    /** URI to attach to the intent's clipData so FLAG_GRANT_READ_URI_PERMISSION delegates it. */
    val clipDataUri: Uri? = null,
    val launchMethod: LaunchMethod = LaunchMethod.INTENT
)

/**
 * Converts an [EffectiveLaunchCommand] to an Android [Intent]. Pre-grants any URIs declared in
 * [EffectiveLaunchCommand.grantReadUriTo] before returning.
 */
fun EffectiveLaunchCommand.toIntent(context: Context): Intent {
    // Pre-grant URIs outside the Intent.apply block so `this` doesn't shadow the outer receiver.
    for (uri in grantReadUriTo) {
        try {
            context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            Logger.warn(TAG, "Failed to grant URI permission to $packageName for $uri", e)
        }
    }

    // Hoist references that would otherwise collide with Intent properties inside apply { }.
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
            is ResolvedExtra.UriExtra -> intent.putExtra(extra.key, extra.uri.toString())
            is ResolvedExtra.BoolExtra -> intent.putExtra(extra.key, extra.value)
            is ResolvedExtra.StringArrayExtra -> intent.putExtra(extra.key, extra.values)
        }
    }
    if (cmdClipDataUri != null) {
        intent.clipData = ClipData.newRawUri(null, cmdClipDataUri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    if (cmdFlags != 0) {
        intent.addFlags(cmdFlags)
    }
    return intent
}

/**
 * Converts an [EffectiveLaunchCommand] to an `am start` argv array suitable for
 * `Runtime.exec(String[])`. URI pre-grants must still be issued separately by the caller via
 * [Context.grantUriPermission] before invoking the shell, because `am start`'s
 * `--grant-read-uri-permission` flag only sets the intent flag on the started activity -- it does
 * not establish the grant.
 */
fun EffectiveLaunchCommand.toShellArgv(): Array<String> {
    val args = mutableListOf("am", "start", "-a", action)

    if (activityClass != null) {
        args += listOf("-n", "$packageName/$activityClass")
    } else {
        // am start has no direct setPackage equivalent. Use the package in an implicit resolve;
        // Android picks the matching activity in the target package. This is the fallback path
        // for Custom configs without an activityClass (currently unused by any registered emulator).
        args += listOf("--user", "current")
    }

    categories.forEach { category ->
        args += listOf("-c", category)
    }

    if (dataUri != null) {
        args += listOf("-d", dataUri.toString())
    }

    if (mimeType != null) {
        args += listOf("-t", mimeType)
    }

    if (intentFlags != 0) {
        args += listOf("-f", "0x${intentFlags.toString(16)}")
    }

    if (clipDataUri != null || grantReadUriTo.isNotEmpty()) {
        args += "--grant-read-uri-permission"
    }

    extras.forEach { extra ->
        when (extra) {
            is ResolvedExtra.StringExtra -> args += listOf("--es", extra.key, extra.value)
            is ResolvedExtra.UriExtra -> args += listOf("--eu", extra.key, extra.uri.toString())
            is ResolvedExtra.BoolExtra -> args += listOf("--ez", extra.key, extra.value.toString())
            is ResolvedExtra.StringArrayExtra -> args += listOf("--esa", extra.key, extra.values.joinToString(","))
        }
    }

    return args.toTypedArray()
}
