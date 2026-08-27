package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.RequiredAction

/**
 * The display label for a changelog's required action.
 *
 * `RequiredAction` lives in `domain/model` and must not import `R`, so the label is attached here
 * instead, following the shape of [CompletionStatusUi]. The sealed class keeps its own `label`
 * as the value the domain was built around.
 */
@get:StringRes
val RequiredAction.labelRes: Int
    get() = when (this) {
        RequiredAction.ReloginRomM -> R.string.ui_changelog_action_relogin_romm
        RequiredAction.ResyncLibrary -> R.string.ui_changelog_action_resync_library
        RequiredAction.ClearCache -> R.string.ui_changelog_action_clear_cache
        RequiredAction.SetSteamInstallPath -> R.string.ui_changelog_action_set_steam_path
    }
