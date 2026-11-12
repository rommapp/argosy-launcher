package com.nendo.argosy.ui.components.permissions

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.nendo.argosy.ArgosyActivity
import com.nendo.argosy.data.permissions.PermissionGrant
import com.nendo.argosy.domain.model.ArgosyPermission
import com.nendo.argosy.ui.common.rememberNotificationPermissionRequest
import com.nendo.argosy.ui.common.rememberStorageAccessRequest
import com.nendo.argosy.util.Logger

/**
 * Runs the grant action [grantFor] names for a permission. [onRootGrant] and [onPrivilegedEnable]
 * carry the routes that need the caller's state; [onResult] runs once a runtime prompt answers.
 */
@Composable
fun rememberPermissionGrantLauncher(
    grantFor: (ArgosyPermission) -> PermissionGrant,
    onRootGrant: (ArgosyPermission) -> Unit,
    onPrivilegedEnable: () -> Unit,
    onResult: () -> Unit
): (ArgosyPermission) -> Unit {
    val context = LocalContext.current
    val currentGrantFor by rememberUpdatedState(grantFor)
    val currentOnRootGrant by rememberUpdatedState(onRootGrant)
    val currentOnPrivilegedEnable by rememberUpdatedState(onPrivilegedEnable)
    val requestStorage = rememberStorageAccessRequest(onResult = onResult)
    val requestNotifications = rememberNotificationPermissionRequest(onResult = onResult)
    return remember(context, requestStorage, requestNotifications) {
        { permission ->
            when (val grant = currentGrantFor(permission)) {
                is PermissionGrant.Screen -> context.openGrantScreen(grant.intent)
                PermissionGrant.StorageRequest -> requestStorage()
                PermissionGrant.NotificationRequest -> requestNotifications()
                PermissionGrant.ScreenCaptureRequest ->
                    (context.findActivity() as? ArgosyActivity)?.requestScreenCapturePermission()
                PermissionGrant.RootGrant -> currentOnRootGrant(permission)
                PermissionGrant.PrivilegedEnable -> currentOnPrivilegedEnable()
                PermissionGrant.None -> Unit
            }
        }
    }
}

private fun Context.openGrantScreen(intent: Intent) {
    try {
        startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Logger.warn(TAG, "Permission screen did not open", e)
    } catch (e: SecurityException) {
        Logger.warn(TAG, "Permission screen refused to open", e)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val TAG = "PermissionGrant"
