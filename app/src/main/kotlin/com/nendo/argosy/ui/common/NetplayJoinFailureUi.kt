package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.data.netplay.NetplayJoinFailure

val NetplayJoinFailure.DownloadConflict.labelRes: Int
    @StringRes get() = R.string.ui_netplay_join_error_download_conflict
