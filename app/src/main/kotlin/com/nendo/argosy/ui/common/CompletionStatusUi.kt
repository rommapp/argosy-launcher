package com.nendo.argosy.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.nendo.argosy.domain.model.CompletionStatus
import com.nendo.argosy.ui.theme.ALauncherColors

/**
 * The display label. `CompletionStatus` lives in `domain/` and must not import `R`, so the
 * label is attached here beside the icon and colour. `apiValue` stays the wire value.
 */
@get:androidx.annotation.StringRes
val CompletionStatus.labelRes: Int
    get() = when (this) {
        CompletionStatus.INCOMPLETE -> com.nendo.argosy.R.string.completion_status_incomplete
        CompletionStatus.FINISHED -> com.nendo.argosy.R.string.completion_status_finished
        CompletionStatus.COMPLETED_100 -> com.nendo.argosy.R.string.completion_status_completed_100
        CompletionStatus.RETIRED -> com.nendo.argosy.R.string.completion_status_retired
        CompletionStatus.NEVER_PLAYING -> com.nendo.argosy.R.string.completion_status_never_playing
    }

val CompletionStatus.icon: ImageVector
    get() = when (this) {
        CompletionStatus.INCOMPLETE -> Icons.Filled.PlayCircle
        CompletionStatus.FINISHED -> Icons.Filled.CheckCircle
        CompletionStatus.COMPLETED_100 -> Icons.Filled.EmojiEvents
        CompletionStatus.RETIRED -> Icons.Filled.RemoveCircle
        CompletionStatus.NEVER_PLAYING -> Icons.Filled.Block
    }

val CompletionStatus.color: Color
    get() = when (this) {
        CompletionStatus.INCOMPLETE -> ALauncherColors.CompletionPlaying
        CompletionStatus.FINISHED -> ALauncherColors.CompletionBeaten
        CompletionStatus.COMPLETED_100 -> ALauncherColors.CompletionCompleted
        CompletionStatus.RETIRED -> Color(0xFF9E9E9E)
        CompletionStatus.NEVER_PLAYING -> Color(0xFF757575)
    }
