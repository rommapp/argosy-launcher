package com.nendo.argosy.ui.screens.setup

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.ArgosyPermission
import com.nendo.argosy.domain.model.PermissionTier
import com.nendo.argosy.ui.common.androidNameRes
import com.nendo.argosy.ui.common.icon
import com.nendo.argosy.ui.common.rowState
import com.nendo.argosy.ui.common.titleRes
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.PermissionRow
import com.nendo.argosy.ui.components.PermissionTierHeader
import com.nendo.argosy.ui.screens.setup.components.SetupStepCard
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

@get:StringRes
private val ArgosyPermission.setupEnablesRes: Int?
    get() = when (this) {
        ArgosyPermission.ALL_FILES_ACCESS -> R.string.setup_permissions_enables_all_files
        ArgosyPermission.USAGE_ACCESS -> R.string.setup_permissions_enables_usage
        ArgosyPermission.NOTIFICATIONS -> R.string.setup_permissions_enables_notifications
        ArgosyPermission.DISPLAY_OVERLAY -> R.string.setup_permissions_enables_overlay
        ArgosyPermission.BATTERY_UNRESTRICTED -> R.string.setup_permissions_enables_battery
        else -> null
    }

@get:StringRes
private val ArgosyPermission.setupLostRes: Int?
    get() = when (this) {
        ArgosyPermission.DISPLAY_OVERLAY -> R.string.setup_permissions_lost_overlay
        ArgosyPermission.BATTERY_UNRESTRICTED -> R.string.setup_permissions_lost_battery
        else -> null
    }

private sealed interface PermissionListItem {
    val key: String

    data class Header(val tier: PermissionTier) : PermissionListItem {
        override val key: String get() = "header-${tier.name}"
    }

    data class Row(val permission: ArgosyPermission) : PermissionListItem {
        override val key: String get() = permission.token
    }
}

private val PERMISSION_LIST: List<PermissionListItem> =
    listOf<PermissionListItem>(PermissionListItem.Header(PermissionTier.REQUIRED)) +
        SETUP_REQUIRED_PERMISSIONS.map { PermissionListItem.Row(it) } +
        PermissionListItem.Header(PermissionTier.RECOMMENDED) +
        SETUP_RECOMMENDED_PERMISSIONS.map { PermissionListItem.Row(it) }

@Composable
internal fun PermissionsPage(state: SetupUiState, viewModel: SetupViewModel) {
    val focus = focusedTarget(state)
    val focusedPermission = (focus as? SetupTarget.Permission)?.permission
    SetupPageBody(
        state = state,
        viewModel = viewModel,
        context = if (focusedPermission != null) ({ PermissionContext(focusedPermission) }) else null,
    ) {
        val listState = rememberLazyListState()
        val focusedIndex = PERMISSION_LIST.indexOfFirst {
            it is PermissionListItem.Row && it.permission == focusedPermission
        }
        FocusedScroll(listState = listState, focusedIndex = focusedIndex)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(Dimens.listGap),
        ) {
            items(PERMISSION_LIST, key = { it.key }) { item ->
                when (item) {
                    is PermissionListItem.Header -> PermissionTierHeader(
                        tier = item.tier,
                        note = if (item.tier == PermissionTier.REQUIRED && state.access.rootGranted) {
                            stringResource(R.string.setup_permissions_root_note)
                        } else {
                            null
                        },
                    )
                    is PermissionListItem.Row -> PermissionRow(
                        icon = item.permission.icon,
                        title = stringResource(item.permission.titleRes),
                        androidName = item.permission.androidNameRes?.let { stringResource(it) }.orEmpty(),
                        state = state.access.availabilityOf(item.permission).rowState,
                        isFocused = focusedPermission == item.permission,
                        onClick = { viewModel.tap(SetupTarget.Permission(item.permission)) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun PermissionContext(permission: ArgosyPermission) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
        permission.setupEnablesRes?.let { Text(stringResource(it)) }
        permission.setupLostRes?.let {
            Text(text = stringResource(it), color = LocalArgosyTheme.current.textPrimary)
        }
    }
}

@Composable
internal fun AccessibilityPage(state: SetupUiState, viewModel: SetupViewModel) {
    val access = state.access
    val focus = focusedTarget(state)
    SetupPageBody(
        state = state,
        viewModel = viewModel,
        context = if (access.variant == AccessibilityVariant.ROOTED) null else ({ AccessibilityContext(access.variant) }),
    ) {
        AccessibilitySteps(access = access, focus = focus, onTap = viewModel::tap)
    }
}

@Composable
internal fun AccessibilitySteps(access: SetupAccessState, focus: SetupTarget?, onTap: ((SetupTarget) -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.listGap)) {
        when (access.variant) {
            AccessibilityVariant.ROOTED -> RootedAccessibilityRow(access)
            AccessibilityVariant.TWO_STEP -> {
                RestrictedStep(access, focus, onTap)
                ServiceStep(access, number = 2, focus = focus, onTap = onTap)
            }
            AccessibilityVariant.ONE_STEP -> ServiceStep(access, number = null, focus = focus, onTap = onTap)
        }
        if (!access.accessibilityOn) {
            Text(
                text = stringResource(R.string.setup_accessibility_lost),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalArgosyTheme.current.textDim,
                modifier = Modifier.padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
            )
        }
    }
}

@Composable
private fun RootedAccessibilityRow(access: SetupAccessState) {
    val checking = ArgosyPermission.ACCESSIBILITY in access.rootGranting
    SetupStepCard(
        number = null,
        leadingIcon = Icons.Default.CheckCircle,
        title = stringResource(
            if (access.accessibilityOn) R.string.setup_accessibility_rooted_title else R.string.setup_accessibility_step_service_title
        ),
        subtitle = stringResource(R.string.setup_accessibility_rooted_subtitle),
        chip = stringResource(
            when {
                checking -> R.string.setup_accessibility_chip_checking
                access.accessibilityOn -> R.string.setup_accessibility_chip_on
                else -> R.string.setup_accessibility_chip_off
            }
        ),
        chipPositive = access.accessibilityOn,
        focused = false,
        onClick = null,
    )
}

@Composable
private fun RestrictedStep(access: SetupAccessState, focus: SetupTarget?, onTap: ((SetupTarget) -> Unit)?) {
    val target = SetupTarget.AccessibilityStep(AccessibilityStepKind.RESTRICTED_SETTINGS)
    val done = access.restrictedStepDone || access.accessibilityOn
    SetupStepCard(
        number = 1,
        title = stringResource(R.string.setup_accessibility_step_restricted_title),
        subtitle = stringResource(
            if (access.stillBlocked) R.string.setup_accessibility_still_blocked else R.string.setup_accessibility_step_restricted_subtitle
        ),
        chip = stringResource(if (done) R.string.setup_accessibility_chip_done else R.string.setup_accessibility_chip_todo),
        chipPositive = done,
        focused = focus == target,
        onClick = onTap?.let { tap -> { tap(target) } },
    )
}

@Composable
private fun ServiceStep(access: SetupAccessState, number: Int?, focus: SetupTarget?, onTap: ((SetupTarget) -> Unit)?) {
    val target = SetupTarget.AccessibilityStep(AccessibilityStepKind.SERVICE)
    SetupStepCard(
        number = number,
        title = stringResource(R.string.setup_accessibility_step_service_title),
        subtitle = stringResource(R.string.setup_accessibility_step_service_subtitle),
        chip = stringResource(if (access.accessibilityOn) R.string.setup_accessibility_chip_on else R.string.setup_accessibility_chip_off),
        chipPositive = access.accessibilityOn,
        focused = focus == target,
        onClick = onTap?.let { tap -> { tap(target) } },
    )
}

@Composable
internal fun AccessibilityContext(variant: AccessibilityVariant) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
        Text(
            text = stringResource(R.string.setup_accessibility_context_where),
            color = LocalArgosyTheme.current.textPrimary,
        )
        if (variant == AccessibilityVariant.TWO_STEP) {
            Text(stringResource(R.string.setup_accessibility_context_restricted_path))
        }
        Text(stringResource(R.string.setup_accessibility_context_service_path))
    }
}
