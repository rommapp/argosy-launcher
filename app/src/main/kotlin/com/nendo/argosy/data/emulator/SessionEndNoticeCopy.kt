package com.nendo.argosy.data.emulator

import androidx.annotation.StringRes

interface SessionEndNoticeCopy {
    @get:StringRes val uploaded: Int
    @get:StringRes val unchanged: Int
    @get:StringRes val queued: Int
    @get:StringRes val uploadFailed: Int
    @get:StringRes val uploadFailedSubtitle: Int
    @get:StringRes val uploadFailedNameFallback: Int
    @get:StringRes val notKept: Int
    @get:StringRes val uploadedOrphan: Int
    @get:StringRes val queuedOrphan: Int
}
