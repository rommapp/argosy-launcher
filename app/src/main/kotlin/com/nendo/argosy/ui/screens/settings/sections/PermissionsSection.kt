package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.ArgosyPermission
import com.nendo.argosy.domain.model.PermissionTier
import com.nendo.argosy.ui.common.androidNameRes
import com.nendo.argosy.ui.common.cardDescriptionRes
import com.nendo.argosy.ui.common.icon
import com.nendo.argosy.ui.common.rowState
import com.nendo.argosy.ui.common.titleRes
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.PermissionRow
import com.nendo.argosy.ui.components.PermissionTierHeader
import com.nendo.argosy.ui.screens.settings.PermissionsState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.LocalSettingsTouchFocus
import com.nendo.argosy.ui.screens.settings.components.settingsRowItems
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

internal sealed class PermissionsItem(val key: String, val section: String? = null) {
    data object Intro : PermissionsItem("intro")
    data class Header(val tier: PermissionTier) : PermissionsItem("header_${tier.name}", tier.name)
    data class Row(val permission: ArgosyPermission) :
        PermissionsItem("row_${permission.token}", permission.tier.name)
}

private val permissionsItems: List<PermissionsItem> =
    listOf(PermissionsItem.Intro) + PermissionTier.entries.flatMap { tier ->
        listOf(PermissionsItem.Header(tier)) + ArgosyPermission.inTier(tier).map { PermissionsItem.Row(it) }
    }

private val permissionsLayout = SettingsLayout<PermissionsItem, PermissionsState>(
    allItems = permissionsItems,
    isFocusable = { it is PermissionsItem.Row },
    visibleWhen = { item, state ->
        item !is PermissionsItem.Row || item.permission in state.snapshot.relevant
    },
    sectionOf = { it.section }
)

internal fun permissionsItemAt(focusIndex: Int, permissions: PermissionsState): PermissionsItem? =
    permissionsLayout.itemAtFocusIndex(focusIndex, permissions)

internal fun permissionsMaxFocusIndex(permissions: PermissionsState): Int =
    permissionsLayout.maxFocusIndex(permissions)

internal fun PermissionsItem.confirm(viewModel: SettingsViewModel) {
    if (this is PermissionsItem.Row) viewModel.actOnPermission(permission)
}

@Composable
fun PermissionsSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val listState = rememberLazyListState()
    val permissions = uiState.permissions
    val visibleItems = remember(permissions) { permissionsLayout.visibleItems(permissions) }

    FocusedScroll(
        listState = listState,
        focusedIndex = permissionsLayout.focusToListIndex(uiState.focusedIndex, permissions),
        isTouchMode = LocalSettingsTouchFocus.current.isTouchMode
    )

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        settingsRowItems(
            visibleItems,
            key = { it.key },
            focusIndexOf = { permissionsLayout.focusIndexOf(it, permissions) }
        ) { item ->
            when (item) {
                PermissionsItem.Intro -> Text(
                    text = stringResource(R.string.permissions_settings_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalArgosyTheme.current.textDim,
                    modifier = Modifier.padding(bottom = Dimens.spacingSm)
                )
                is PermissionsItem.Header -> PermissionTierHeader(
                    tier = item.tier,
                    modifier = Modifier.padding(top = Dimens.spacingSm)
                )
                is PermissionsItem.Row -> PermissionSettingsRow(
                    permission = item.permission,
                    permissions = permissions,
                    isFocused = uiState.focusedIndex == permissionsLayout.focusIndexOf(item, permissions),
                    onClick = { item.confirm(viewModel) }
                )
            }
        }
    }
}

@Composable
private fun PermissionSettingsRow(
    permission: ArgosyPermission,
    permissions: PermissionsState,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val isPrivileged = permission == ArgosyPermission.PRIVILEGED_ACCESS
    val title = if (isPrivileged) {
        stringResource(permission.titleRes, permissions.privilegedRoute?.productName.orEmpty())
    } else {
        stringResource(permission.titleRes)
    }
    val secondLine = permission.androidNameRes?.let { stringResource(it) }
        ?: stringResource(permissions.privilegedReach.cardDescriptionRes)
    PermissionRow(
        icon = permission.icon,
        title = title,
        androidName = secondLine,
        state = permissions.snapshot.of(permission).rowState,
        isFocused = isFocused,
        onClick = onClick
    )
}
