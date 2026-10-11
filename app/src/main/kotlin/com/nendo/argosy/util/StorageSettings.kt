package com.nendo.argosy.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

fun Context.openStorageAccessSettings() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

    val packageUri = Uri.fromParts("package", packageName, null)
    val intents = listOf(
        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
    )
    var lastException: ActivityNotFoundException? = null

    for (intent in intents) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (exception: ActivityNotFoundException) {
            lastException = exception
        }
    }

    lastException?.let {
        Log.e(TAG, "No storage access settings activity found for $packageName", it)
    }
}

private const val TAG = "StorageSettings"
