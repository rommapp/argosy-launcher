package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.RaProgressFilter

@get:StringRes
val RaProgressFilter.labelRes: Int
    get() = when (this) {
        RaProgressFilter.NOT_STARTED -> R.string.ui_feature_setup_ra_progress_not_started
        RaProgressFilter.IN_PROGRESS -> R.string.ui_feature_setup_ra_progress_in_progress
        RaProgressFilter.MASTERED -> R.string.ui_feature_setup_ra_progress_mastered
    }
