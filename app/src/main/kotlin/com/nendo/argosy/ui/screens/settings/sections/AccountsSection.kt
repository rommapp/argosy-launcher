package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.QrCodeWithOverlay
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.screens.settings.AccountRowAction
import com.nendo.argosy.ui.screens.settings.AccountUi
import com.nendo.argosy.ui.screens.settings.AccountsState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.SectionHeader
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

internal sealed class AccountsItem(val key: String) {
    class Header(key: String, val title: String) : AccountsItem(key)
    class Gap(key: String) : AccountsItem(key)
    class Account(val account: AccountUi) : AccountsItem("account-${account.id}")
    data object AddAccount : AccountsItem("addAccount")
    class NeedsSyncNotice(
        key: String,
        val title: String,
        val titles: List<String>
    ) : AccountsItem(key)

    val isFocusable: Boolean
        get() = when (this) {
            is Header, is Gap, is NeedsSyncNotice -> false
            else -> true
        }
}

internal fun accountsItems(state: AccountsState): List<AccountsItem> = buildList {
    add(AccountsItem.Header("accountsHeader", "PAIRED ACCOUNTS"))
    state.accounts.forEach { add(AccountsItem.Account(it)) }
    add(AccountsItem.Gap("manageGap"))
    add(AccountsItem.AddAccount)
    if (state.needsSyncSaveTitles.isNotEmpty()) {
        add(AccountsItem.Gap("needsSyncSaveGap"))
        add(
            AccountsItem.NeedsSyncNotice(
                key = "needsSyncSaves",
                title = "SAVES WAITING ON A SYNC",
                titles = state.needsSyncSaveTitles
            )
        )
    }
    if (state.needsSyncStateTitles.isNotEmpty()) {
        add(AccountsItem.Gap("needsSyncStateGap"))
        add(
            AccountsItem.NeedsSyncNotice(
                key = "needsSyncStates",
                title = "SAVE STATES WAITING ON A SYNC",
                titles = state.needsSyncStateTitles
            )
        )
    }
}

internal fun accountsFocusableItems(state: AccountsState): List<AccountsItem> =
    accountsItems(state).filter { it.isFocusable }

internal fun accountsItemAtFocusIndex(focusIndex: Int, state: AccountsState): AccountsItem? =
    accountsFocusableItems(state).getOrNull(focusIndex)

internal fun accountsMaxFocusIndex(state: AccountsState): Int =
    (accountsFocusableItems(state).size - 1).coerceAtLeast(0)

@Composable
fun AccountsSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val accounts = uiState.accounts

    if (accounts.pairing.active) {
        AccountPairingPane(uiState, viewModel)
        return
    }

    if (accounts.switchInProgress) {
        AccountSwitchProgressPane(accounts)
        return
    }

    val listState = rememberLazyListState()
    val rows = remember(accounts) {
        var focusIndex = 0
        accountsItems(accounts).map { item ->
            if (item.isFocusable) item to focusIndex++ else item to -1
        }
    }

    val focusedListIndex = remember(rows, uiState.focusedIndex) {
        rows.indexOfFirst { it.second == uiState.focusedIndex }.coerceAtLeast(0)
    }

    FocusedScroll(listState = listState, focusedIndex = focusedListIndex)

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.spacingMd),
        contentPadding = PaddingValues(top = Dimens.spacingMd, bottom = Dimens.spacingXxl),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        items(rows, key = { it.first.key }) { (item, focusIndex) ->
            when (item) {
                is AccountsItem.Header -> SectionHeader(item.title)
                is AccountsItem.Gap -> Spacer(modifier = Modifier.height(Dimens.spacingLg))
                is AccountsItem.NeedsSyncNotice -> NeedsSyncCard(item.title, item.titles)
                is AccountsItem.Account -> AccountRow(
                    account = item.account,
                    state = accounts,
                    isFocused = focusIndex == uiState.focusedIndex,
                    onSwitch = { viewModel.requestAccountSwitch(item.account.id) },
                    onRemove = { viewModel.requestAccountRemoval(item.account.id) },
                    onSelectAction = { viewModel.setAccountRowAction(item.account, it) }
                )
                AccountsItem.AddAccount -> ActionPreference(
                    title = "Add Account",
                    subtitle = accounts.activeAccount
                        ?.let { "Pair another user on ${it.serverLabel}" }
                        ?: "Sign in under Game Data first",
                    icon = Icons.Default.PersonAdd,
                    isEnabled = accounts.activeAccount != null,
                    isFocused = focusIndex == uiState.focusedIndex,
                    onClick = { viewModel.startAddAccount() }
                )
            }
        }

        if (accounts.isLoading) {
            item(key = "accountsLoading") { AccountsLoadingCard() }
        } else if (accounts.accounts.isEmpty()) {
            item(key = "accountsEmpty") { NoAccountsCard() }
        }
    }
}

@Composable
private fun AccountRow(
    account: AccountUi,
    state: AccountsState,
    isFocused: Boolean,
    onSwitch: () -> Unit,
    onRemove: () -> Unit,
    onSelectAction: (AccountRowAction) -> Unit
) {
    val theme = LocalArgosyTheme.current
    val actions = state.actionsFor(account)
    val selected = if (isFocused) state.selectedActionFor(account) else null
    val removable = state.canRemove(account)
    val shape = RoundedCornerShape(Dimens.radiusControl)
    val background = if (isFocused) {
        theme.focusAccent.copy(alpha = FOCUS_WASH_ALPHA)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(
                width = Dimens.borderThin,
                color = if (isFocused) theme.focusAccent else theme.hairlineLow,
                shape = shape
            )
            .padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.username,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isFocused) theme.focusAccent else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = account.serverLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (account.isActive) {
                Text(
                    text = "ACTIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.focusAccent
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
            actions.forEach { action ->
                when (action) {
                    AccountRowAction.SWITCH -> ActionButton(
                        label = "Switch",
                        onClick = {
                            onSelectAction(AccountRowAction.SWITCH)
                            onSwitch()
                        },
                        focused = selected == AccountRowAction.SWITCH,
                        primary = true
                    )
                    AccountRowAction.REMOVE -> ActionButton(
                        label = "Remove",
                        onClick = {
                            onSelectAction(AccountRowAction.REMOVE)
                            onRemove()
                        },
                        focused = selected == AccountRowAction.REMOVE,
                        enabled = removable
                    )
                }
            }
        }

        if (!removable) {
            Text(
                text = if (state.accounts.size <= 1) {
                    "This is the only account on this device. Sign out from Game Data to leave it."
                } else {
                    "Switch to another account before removing this one."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NeedsSyncCard(title: String, titles: List<String>) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = SURFACE_WASH_ALPHA))
            .padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = theme.focusAccent
        )
        Text(
            text = "These were left empty because the copy on this device was known to be older " +
                "than the server's. Sync to fill them in.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        titles.forEach { gameTitle ->
            Text(
                text = gameTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun NoAccountsCard() {
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = SURFACE_WASH_ALPHA))
            .padding(Dimens.spacingLg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Dimens.iconLg)
        )
        Text(
            text = "No RomM account is paired yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Sign in under Game Data. Once one account is paired you can add more here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AccountsLoadingCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.spacingLg),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(Dimens.iconMd),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Loading accounts...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun AccountSwitchProgressPane(state: AccountsState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.spacingLg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd, Alignment.CenterVertically)
    ) {
        Text(
            text = if (state.isResumingSwitch) {
                "Finishing the interrupted switch"
            } else {
                "Switching account"
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = state.switchProgressLabel ?: "Working",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CircularProgressIndicator(
            modifier = Modifier.size(Dimens.iconXl),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Saves are archived and verified before anything is removed. " +
                "Leave this screen open.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AccountPairingPane(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val pairing = uiState.accounts.pairing
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.spacingMd),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Text(
            text = "Scan to add an account",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Sign in to RomM on your phone as the user you want to add, scan this code, " +
                "then approve this device. Argosy signs in as them once approved.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (pairing.connecting) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.spacingLg),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        pairing.verificationUrl?.let { url ->
            QrCodeWithOverlay(data = url)
        }

        pairing.userCode?.let { code ->
            Text(
                text = code,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        pairing.verificationUrl?.let { url ->
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        pairing.error?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(Dimens.spacingSm))

        ActionButton(
            label = if (pairing.error != null) "Try Again" else "Cancel",
            onClick = {
                if (pairing.error != null) {
                    viewModel.retryAddAccountPairing()
                } else {
                    viewModel.cancelAddAccount()
                }
            },
            focused = true,
            primary = pairing.error != null
        )
    }
}

private const val FOCUS_WASH_ALPHA = 0.15f
private const val SURFACE_WASH_ALPHA = 0.5f
