package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.data.model.DisplayKind
import com.nendo.argosy.domain.model.ScreenRole

@get:StringRes
val DisplayKind.labelRes: Int
    get() = when (this) {
        DisplayKind.BUILT_IN -> R.string.about_tools_display_kind_built_in
        DisplayKind.EXTERNAL -> R.string.about_tools_display_kind_external
        DisplayKind.VIRTUAL -> R.string.about_tools_display_kind_virtual
    }

@get:StringRes
val ScreenRole?.liveRoleLabelRes: Int
    get() = when (this) {
        ScreenRole.PRIMARY -> R.string.about_tools_display_role_primary
        ScreenRole.PRESENTATION -> R.string.about_tools_display_role_presentation
        ScreenRole.APP_TARGET -> R.string.about_tools_display_role_app_target
        ScreenRole.OFF -> R.string.about_tools_display_role_off
        null -> R.string.about_tools_display_role_none
    }
