package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.data.model.PrivilegedReach
import com.nendo.argosy.data.model.PrivilegedRoute

@get:StringRes
val PrivilegedRoute.promptMessageRes: Int
    get() = when (this) {
        PrivilegedRoute.MAGISK -> R.string.privileged_access_prompt_message_root
        PrivilegedRoute.SHIZUKU -> R.string.privileged_access_prompt_message_shizuku
    }

@get:StringRes
val PrivilegedReach.cardDescriptionRes: Int
    get() = when (this) {
        PrivilegedReach.NONE -> R.string.settings_permissions_privileged_description_off
        PrivilegedReach.CHECKING -> R.string.settings_permissions_privileged_description_checking
        PrivilegedReach.ROOT -> R.string.settings_permissions_privileged_description_root
        PrivilegedReach.APP_DATA -> R.string.settings_permissions_privileged_description_app_data
        PrivilegedReach.UNREACHABLE -> R.string.settings_permissions_privileged_description_unreachable
        PrivilegedReach.REFUSED -> R.string.settings_permissions_privileged_description_refused
    }
