package com.nendo.argosy.ui.common

import android.content.Context
import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.core.notification.showSuccess
import com.nendo.argosy.data.emulator.TitleIdRecheck

fun NotificationManager.reportTitleIdRecheck(context: Context, result: TitleIdRecheck) {
    when (result) {
        is TitleIdRecheck.Found -> showSuccess(
            if (result.replaced != null) {
                NotificationText.Res(R.string.title_id_recheck_notice_updated, listOf(result.titleId))
            } else {
                NotificationText.Res(R.string.title_id_recheck_notice_confirmed, listOf(result.titleId))
            }
        )
        is TitleIdRecheck.NotFound -> showError(
            NotificationText.Res(R.string.title_id_recheck_notice_not_found, listOf(result.fileName))
        )
        TitleIdRecheck.NoFile -> showError(NotificationText.Res(R.string.title_id_recheck_notice_no_file))
        TitleIdRecheck.Unsupported -> Unit
    }
}
