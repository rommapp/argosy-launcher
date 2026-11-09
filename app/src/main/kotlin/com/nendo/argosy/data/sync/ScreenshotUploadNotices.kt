package com.nendo.argosy.data.sync

import android.content.Context
import com.nendo.argosy.R
import com.nendo.argosy.core.locale.LocaleHelper
import com.nendo.argosy.data.preferences.SessionStateStore
import com.nendo.argosy.data.social.SocialOverlayService
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenshotUploadNotices @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun drainFinished(uploaded: Int, rejected: Int) {
        if (uploaded <= 0 && rejected <= 0) return
        try {
            val localized = LocaleHelper.wrap(context, SessionStateStore(context).getAppLanguage())
            val res = localized.resources
            val uploadedLine = res.getQuantityString(R.plurals.sync_screenshot_uploaded, uploaded, uploaded)
            val rejectedLine = res.getQuantityString(R.plurals.sync_screenshot_rejected, rejected, rejected)
            when {
                uploaded > 0 && rejected > 0 -> SocialOverlayService.show(context, uploadedLine, rejectedLine)
                uploaded > 0 -> SocialOverlayService.show(context, uploadedLine)
                else -> SocialOverlayService.show(context, rejectedLine)
            }
        } catch (e: Exception) {
            Logger.warn(TAG, "Could not show the screenshot upload notice", e)
        }
    }

    private companion object {
        const val TAG = "ScreenshotUploadNotices"
    }
}
