package com.nendo.argosy.ui.screens.social

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem

enum class FeedOption {
    CREATE_POST,
    FIND_COMMUNITIES,
    VIEW_PROFILE,
    SHARE_SCREENSHOT,
    REPORT_POST,
    HIDE_POST
}

enum class ReportReason(val value: String, @StringRes val labelRes: Int) {
    SPAM("spam", R.string.social_reportreason_spam),
    HARASSMENT("harassment", R.string.social_reportreason_harassment),
    INAPPROPRIATE("inappropriate", R.string.social_reportreason_inappropriate),
    MISINFORMATION("misinformation", R.string.social_reportreason_misinformation)
}

@Composable
fun FeedOptionsModal(
    focusIndex: Int,
    userName: String?,
    hasEvent: Boolean,
    isCommunityMode: Boolean = false,
    onAction: (FeedOption) -> Unit,
    onDismiss: () -> Unit
) {
    Modal(title = stringResource(R.string.social_feedoptions_modal_title), onDismiss = onDismiss) {
        var currentIndex = 0

        if (isCommunityMode) {
            OptionItem(
                icon = Icons.Default.Search,
                label = stringResource(R.string.social_feedoptions_option_find_communities),
                isFocused = focusIndex == currentIndex,
                onClick = { onAction(FeedOption.FIND_COMMUNITIES) }
            )
            currentIndex++
        }

        OptionItem(
            icon = Icons.Default.Create,
            label = stringResource(R.string.social_feedoptions_option_create_post),
            isFocused = focusIndex == currentIndex,
            onClick = { onAction(FeedOption.CREATE_POST) }
        )
        currentIndex++

        if (userName != null && hasEvent) {
            OptionItem(
                icon = Icons.Default.Person,
                label = stringResource(R.string.social_feedoptions_option_view_profile, userName),
                isFocused = focusIndex == currentIndex,
                onClick = { onAction(FeedOption.VIEW_PROFILE) }
            )
            currentIndex++
        }

        if (hasEvent) {
            OptionItem(
                icon = Icons.Default.Share,
                label = stringResource(R.string.social_feedoptions_option_share_screenshot),
                isFocused = focusIndex == currentIndex,
                onClick = { onAction(FeedOption.SHARE_SCREENSHOT) }
            )
            currentIndex++

            OptionItem(
                icon = Icons.Default.Flag,
                label = stringResource(R.string.social_feedoptions_option_report_post),
                isFocused = focusIndex == currentIndex,
                onClick = { onAction(FeedOption.REPORT_POST) }
            )
            currentIndex++

            OptionItem(
                icon = Icons.Default.VisibilityOff,
                label = stringResource(R.string.social_feedoptions_option_hide_post),
                isFocused = focusIndex == currentIndex,
                onClick = { onAction(FeedOption.HIDE_POST) }
            )
        }
    }
}

@Composable
fun AvatarOptionsModal(
    options: List<AvatarModalOption>,
    focusIndex: Int,
    onAction: (AvatarModalOption) -> Unit,
    onDismiss: () -> Unit
) {
    Modal(title = stringResource(R.string.social_avataroptions_modal_title), onDismiss = onDismiss) {
        options.forEachIndexed { index, option ->
            OptionItem(
                icon = when (option) {
                    AvatarModalOption.EDIT_DOODLE -> Icons.Default.Create
                    AvatarModalOption.USE_DOODLE -> Icons.Default.Brush
                    AvatarModalOption.USE_INITIALS -> Icons.Default.Person
                },
                label = stringResource(option.labelRes),
                isFocused = focusIndex == index,
                onClick = { onAction(option) }
            )
        }
    }
}

@Composable
fun ReportReasonModal(
    focusIndex: Int,
    onReasonSelect: (ReportReason) -> Unit,
    onDismiss: () -> Unit
) {
    Modal(title = stringResource(R.string.social_reportreason_modal_title), onDismiss = onDismiss) {
        ReportReason.entries.forEachIndexed { index, reason ->
            OptionItem(
                icon = when (reason) {
                    ReportReason.SPAM -> Icons.Default.Block
                    ReportReason.HARASSMENT -> Icons.Default.Report
                    ReportReason.INAPPROPRIATE -> Icons.Default.Flag
                    ReportReason.MISINFORMATION -> Icons.Default.Report
                },
                label = stringResource(reason.labelRes),
                isFocused = focusIndex == index,
                onClick = { onReasonSelect(reason) }
            )
        }
    }
}

const val REPORT_REASON_COUNT = 4
