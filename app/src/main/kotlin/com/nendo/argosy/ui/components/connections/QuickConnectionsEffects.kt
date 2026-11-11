package com.nendo.argosy.ui.components.connections

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Drives [controller] from the host: page shown and hidden, re-reads when the host resumes, the
 * runtime permission prompts, and the Android screens the page sends the player to. A host whose
 * window sits above activities passes [onLeave], which runs after each of those screens opens,
 * and gets the Bluetooth prompt as a plain launch without a result.
 */
@Composable
fun QuickConnectionsEffects(
    controller: QuickConnectionsController,
    pageVisible: Boolean,
    onLeave: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = LocalActivityResultRegistryOwner.current as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnLeave by rememberUpdatedState(onLeave)
    var pendingRadio by remember { mutableStateOf<ConnectionsList?>(null) }

    LaunchedEffect(controller, pageVisible) {
        if (pageVisible) controller.onPageShown() else controller.onPageHidden()
    }
    DisposableEffect(controller) {
        onDispose { controller.onPageHidden() }
    }
    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) controller.onResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val radio = pendingRadio ?: return@rememberLauncherForActivityResult
        pendingRadio = null
        val granted = results.isNotEmpty() && results.values.all { it }
        val permanentlyDenied = !granted && (
            results.isEmpty() || activity == null ||
                results.keys.none { activity.shouldShowRequestPermissionRationale(it) }
            )
        controller.onPermissionResult(radio, permanentlyDenied)
    }
    val enableLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        controller.onResumed()
    }

    LaunchedEffect(controller, permissionLauncher) {
        controller.permissionRequests.collect { request ->
            if (request.permissions.isEmpty()) {
                controller.onPermissionResult(request.radio, permanentlyDenied = false)
            } else {
                pendingRadio = request.radio
                permissionLauncher.launch(request.permissions.toTypedArray())
            }
        }
    }
    LaunchedEffect(controller, enableLauncher) {
        controller.outsideLaunches.collect { launch ->
            val leave = currentOnLeave
            val viaResult = launch == OutsideLaunch.BLUETOOTH_ENABLE_PROMPT && leave == null
            val opened = if (viaResult) {
                launchForResult { enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) } ||
                    openOutside(context, OutsideLaunch.BLUETOOTH_SETTINGS)
            } else {
                openOutside(context, launch)
            }
            if (opened) leave?.invoke()
        }
    }
}

private inline fun launchForResult(launch: () -> Unit): Boolean = try {
    launch()
    true
} catch (_: ActivityNotFoundException) {
    false
}

private fun openOutside(context: Context, launch: OutsideLaunch): Boolean {
    val primary = intentFor(context, launch)
    val fallback = if (launch == OutsideLaunch.WIFI_PANEL) Intent(Settings.ACTION_WIFI_SETTINGS) else null
    return listOfNotNull(primary, fallback).any { intent ->
        try {
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}

private fun intentFor(context: Context, launch: OutsideLaunch): Intent = when (launch) {
    OutsideLaunch.BLUETOOTH_ENABLE_PROMPT -> Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
    OutsideLaunch.BLUETOOTH_SETTINGS -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
    OutsideLaunch.WIFI_PANEL -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        Intent(Settings.Panel.ACTION_WIFI)
    } else {
        Intent(Settings.ACTION_WIFI_SETTINGS)
    }
    OutsideLaunch.APP_PERMISSIONS -> Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    )
}
