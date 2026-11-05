package com.nendo.argosy.ui.common

import com.nendo.argosy.R
import com.nendo.argosy.data.emulator.SessionEndNoticeCopy
import javax.inject.Inject

class SessionEndNoticeCopyResources @Inject constructor() : SessionEndNoticeCopy {
    override val uploaded = R.string.sync_session_save_uploaded
    override val unchanged = R.string.sync_session_save_unchanged
    override val queued = R.string.sync_session_save_queued
    override val uploadFailed = R.string.sync_session_save_upload_failed
    override val uploadFailedSubtitle = R.string.sync_session_save_upload_failed_subtitle
    override val uploadFailedNameFallback = R.string.sync_session_save_upload_failed_name_fallback
    override val notKept = R.string.sync_session_save_not_kept
    override val uploadedOrphan = R.string.sync_session_save_uploaded_orphan
    override val queuedOrphan = R.string.sync_session_save_queued_orphan
}
