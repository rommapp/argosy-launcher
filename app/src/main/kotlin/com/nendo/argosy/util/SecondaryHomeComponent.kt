package com.nendo.argosy.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.nendo.argosy.hardware.SecondaryHomeActivity

/**
 * Enable/disable the SECONDARY_HOME activity component. Disabling is the only way to
 * release the bottom screen back to the OEM secondary home; finish() alone gets the
 * activity respawned by the OS.
 */
object SecondaryHomeComponent {

    fun setEnabled(context: Context, enabled: Boolean) {
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        val component = ComponentName(context, SecondaryHomeActivity::class.java)
        if (context.packageManager.getComponentEnabledSetting(component) == state) return
        context.packageManager.setComponentEnabledSetting(
            component, state, PackageManager.DONT_KILL_APP
        )
    }

    fun isDefaultHome(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(
            intent, PackageManager.MATCH_DEFAULT_ONLY
        )
        return resolved?.activityInfo?.packageName == context.packageName
    }
}
