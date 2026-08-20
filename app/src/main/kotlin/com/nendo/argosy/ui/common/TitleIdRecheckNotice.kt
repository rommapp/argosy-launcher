package com.nendo.argosy.ui.common

import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.core.notification.showSuccess
import com.nendo.argosy.data.emulator.TitleIdRecheck

fun NotificationManager.reportTitleIdRecheck(result: TitleIdRecheck) {
    when (result) {
        is TitleIdRecheck.Found -> showSuccess(
            if (result.replaced != null) "Title ID updated to ${result.titleId}"
            else "Title ID confirmed as ${result.titleId}"
        )
        is TitleIdRecheck.NotFound -> showError("No title ID found in ${result.fileName}")
        TitleIdRecheck.NoFile -> showError("Game file is not available to read")
        TitleIdRecheck.Unsupported -> Unit
    }
}
