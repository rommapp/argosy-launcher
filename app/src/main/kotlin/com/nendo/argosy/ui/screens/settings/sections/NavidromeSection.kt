package com.nendo.argosy.ui.screens.settings.sections

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.NavigationPreference
import com.nendo.argosy.ui.screens.settings.NavidromeConnectionStatus
import com.nendo.argosy.ui.screens.settings.NavidromeState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.NavidromeConfigForm
import com.nendo.argosy.ui.screens.settings.components.NavidromeSignInForm
import com.nendo.argosy.ui.screens.settings.components.SectionHeader
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.screens.settings.search.SearchableSetting
import com.nendo.argosy.ui.theme.Dimens

internal data class NavidromeLayoutState(
    val hasServer: Boolean,
    val isSignedIn: Boolean
) {
    companion object {
        fun from(state: SettingsUiState): NavidromeLayoutState = from(state.navidrome)

        fun from(navidrome: NavidromeState): NavidromeLayoutState = NavidromeLayoutState(
            hasServer = navidrome.hasServer,
            isSignedIn = navidrome.isSignedIn
        )
    }
}

internal sealed class NavidromeItem(
    val key: String,
    val section: String,
    val visibleWhen: (NavidromeLayoutState) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = this is SearchableSetting

    class Header(
        key: String,
        section: String,
        val titleRes: Int
    ) : NavidromeItem(key, section)

    sealed class Row(
        key: String,
        section: String,
        @StringRes override val labelRes: Int,
        visibleWhen: (NavidromeLayoutState) -> Boolean = { true }
    ) : NavidromeItem(key, section, visibleWhen), SearchableSetting

    data object MusicServer : Row("navidromeServer", "server", R.string.settings_navidrome_server_title)
    data object Account : Row(
        "navidromeAccount", "server", R.string.settings_navidrome_account_label,
        { it.hasServer }
    )
    data object Connection : Row(
        "navidromeConnection", "server", R.string.settings_navidrome_connection_title,
        { it.isSignedIn }
    )

    companion object {
        val ALL: List<NavidromeItem>
            get() = listOf(
                Header("navidromeServerHeader", "server", R.string.settings_navidrome_section_server),
                MusicServer, Account, Connection
            )
    }
}

internal val navidromeLayout = SettingsLayout<NavidromeItem, NavidromeLayoutState>(
    allItems = NavidromeItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section },
    sectionTitleRes = {
        when (it) {
            "server" -> R.string.settings_navidrome_section_server
            else -> null
        }
    }
)

internal fun navidromeItemAtFocusIndex(index: Int, state: NavidromeLayoutState): NavidromeItem? =
    navidromeLayout.itemAtFocusIndex(index, state)

internal fun navidromeMaxFocusIndex(state: NavidromeLayoutState): Int =
    navidromeLayout.maxFocusIndex(state)

private fun accountSubtitle(context: Context, navidrome: NavidromeState): String =
    if (navidrome.isSignedIn) {
        context.getString(R.string.settings_navidrome_account_signed_in_as, navidrome.userName)
    } else {
        context.getString(R.string.settings_navidrome_account_password)
    }

private fun connectionSubtitle(context: Context, navidrome: NavidromeState): String =
    when (navidrome.connectionStatus) {
        NavidromeConnectionStatus.CHECKING -> context.getString(R.string.settings_navidrome_connection_checking)
        NavidromeConnectionStatus.CONNECTED -> navidrome.serverVersion?.takeIf { it.isNotBlank() }
            ?.let { context.getString(R.string.settings_navidrome_connection_connected_version, it) }
            ?: context.getString(R.string.settings_navidrome_connection_connected)
        NavidromeConnectionStatus.FAILED -> navidrome.connectionError
            ?: context.getString(R.string.settings_navidrome_signin_error_unreachable)
        NavidromeConnectionStatus.UNKNOWN -> context.getString(R.string.settings_navidrome_connection_unknown)
    }

@Composable
fun NavidromeSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    when {
        uiState.navidrome.configuring -> NavidromeConfigForm(uiState, viewModel)
        uiState.navidrome.showLoginForm -> NavidromeSignInForm(uiState, viewModel)
        else -> NavidromeContent(uiState, viewModel)
    }
}

@Composable
private fun NavidromeContent(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val navidrome = uiState.navidrome
    val context = LocalContext.current
    val layoutState = remember(navidrome) { NavidromeLayoutState.from(navidrome) }
    val visibleItems = remember(layoutState) { navidromeLayout.visibleItems(layoutState) }
    val sections = remember(layoutState, context) { navidromeLayout.buildSections(layoutState, context) }

    fun isFocused(item: NavidromeItem): Boolean =
        uiState.focusedIndex == navidromeLayout.focusIndexOf(item, layoutState)

    SectionPaneLayout(
        items = visibleItems,
        sections = sections,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { navidromeLayout.focusToListIndex(it, layoutState) },
        itemKey = { it.key },
        isNavItem = { false },
        isHeader = { it is NavidromeItem.Header },
        onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) { item ->
        when (item) {
            is NavidromeItem.Header -> SectionHeader(stringResource(item.titleRes))

            is NavidromeItem.MusicServer -> NavigationPreference(
                icon = Icons.Default.Dns,
                title = stringResource(item.labelRes),
                subtitle = navidrome.serverUrl.ifBlank {
                    stringResource(R.string.settings_navidrome_server_unconfigured)
                },
                isFocused = isFocused(item),
                onClick = { viewModel.startNavidromeConfig() }
            )

            is NavidromeItem.Account -> ActionPreference(
                icon = if (navidrome.isSignedIn) Icons.AutoMirrored.Filled.Logout else Icons.Default.Login,
                title = if (navidrome.isSignedIn) {
                    stringResource(R.string.settings_navidrome_account_sign_out)
                } else {
                    stringResource(R.string.settings_navidrome_account_sign_in)
                },
                subtitle = accountSubtitle(context, navidrome),
                isFocused = isFocused(item),
                isDangerous = navidrome.isSignedIn,
                onClick = {
                    if (navidrome.isSignedIn) viewModel.requestNavidromeSignOut()
                    else viewModel.showNavidromeLoginForm()
                }
            )

            is NavidromeItem.Connection -> ActionPreference(
                icon = Icons.Default.Sync,
                title = stringResource(item.labelRes),
                subtitle = connectionSubtitle(context, navidrome),
                isFocused = isFocused(item),
                isEnabled = navidrome.connectionStatus != NavidromeConnectionStatus.CHECKING,
                iconTint = if (navidrome.connectionStatus == NavidromeConnectionStatus.FAILED) {
                    MaterialTheme.colorScheme.error
                } else {
                    null
                },
                spinIcon = navidrome.connectionStatus == NavidromeConnectionStatus.CHECKING,
                onClick = { viewModel.checkNavidromeConnection() }
            )
        }
    }
}
