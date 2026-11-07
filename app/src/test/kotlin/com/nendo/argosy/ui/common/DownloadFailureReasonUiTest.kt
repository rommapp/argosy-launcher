package com.nendo.argosy.ui.common

import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.data.download.DownloadFailureReason
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadFailureReasonUiTest {
    @Test fun httpFailuresExposeOnlyStatusArguments() {
        val cases = mapOf(
            401 to R.string.error_download_http_unauthorized,
            403 to R.string.error_download_http_forbidden,
            404 to R.string.error_download_http_not_found,
            501 to R.string.error_download_http
        )
        for ((code, resource) in cases) {
            assertEquals(NotificationText.Res(resource, listOf(code)), DownloadFailureReason.HttpError(code).toNotificationText())
        }
    }

    @Test fun transferFailuresUseLocalizedResources() {
        val cases = mapOf(
            DownloadFailureReason.IntegrityCheckFailed to R.string.error_download_integrity_check_failed,
            DownloadFailureReason.InvalidResponse to R.string.error_download_invalid_response,
            DownloadFailureReason.StorageAccessFailed to R.string.error_download_storage_access_failed,
            DownloadFailureReason.TlsVerificationFailed to R.string.error_download_tls_verification_failed,
            DownloadFailureReason.FileTooSmall to R.string.error_download_file_too_small
        )
        for ((reason, resource) in cases) {
            assertEquals(NotificationText.Res(resource), reason.toNotificationText())
        }
        assertEquals(
            NotificationText.Res(R.string.error_download_invalid_content_type, listOf("text/html")),
            DownloadFailureReason.InvalidContentType("text/html").toNotificationText()
        )
    }
}
