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
