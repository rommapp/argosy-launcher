package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.data.repository.BiosRepository.DistributeFailure

@get:StringRes
val DistributeFailure.labelRes: Int
    get() = when (this) {
        DistributeFailure.FILES_MISSING -> R.string.settings_bios_distribute_failure_files_missing
        DistributeFailure.KEYS_MISMATCH -> R.string.settings_bios_distribute_failure_keys_mismatch
        DistributeFailure.EMULATOR_MISSING -> R.string.settings_bios_distribute_failure_emulator_missing
        DistributeFailure.INSTALL_FAILED -> R.string.settings_bios_distribute_failure_install_failed
    }
