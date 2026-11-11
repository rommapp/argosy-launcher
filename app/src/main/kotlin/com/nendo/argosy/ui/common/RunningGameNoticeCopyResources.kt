package com.nendo.argosy.ui.common

import com.nendo.argosy.R
import com.nendo.argosy.data.emulator.background.RunningGameNoticeCopy
import javax.inject.Inject

class RunningGameNoticeCopyResources @Inject constructor() : RunningGameNoticeCopy {
    override val stopped = R.string.running_notice_game_stopped
}
