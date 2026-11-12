package com.nendo.argosy.ui.screens.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.nendo.argosy.R
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.screens.setup.components.SetupDoneSummary
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

@Composable
internal fun WelcomePage(state: SetupUiState, onStart: () -> Unit) {
    val theme = LocalArgosyTheme.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.spacingXl, vertical = Dimens.spacingLg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_helm),
            contentDescription = null,
            tint = theme.focusAccent,
            modifier = Modifier.size(Dimens.iconXl),
        )
        Text(
            text = stringResource(R.string.firstrun_welcome_wordmark),
            style = MaterialTheme.typography.titleLarge,
            color = theme.focusAccent,
            modifier = Modifier.padding(top = Dimens.spacingSm),
        )
        Text(
            text = stringResource(R.string.setup_welcome_title),
            style = MaterialTheme.typography.headlineSmall,
            color = theme.textPrimary,
            modifier = Modifier.padding(top = Dimens.spacingMd),
        )
        Text(
            text = stringResource(R.string.setup_welcome_body),
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textDim,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Dimens.spacingSm),
        )
        ActionButton(
            label = stringResource(R.string.setup_welcome_button_start),
            onClick = onStart,
            focused = focusedTarget(state) == SetupTarget.Action(SetupActionId.PRIMARY),
            primary = true,
            modifier = Modifier.padding(top = Dimens.spacingLg),
        )
    }
}

@Composable
internal fun DonePage(state: SetupUiState, viewModel: SetupViewModel) {
    val theme = LocalArgosyTheme.current
    SetupPageBody(state = state, viewModel = viewModel) {
        Text(
            text = stringResource(
                if (offersHomeRole(state)) R.string.setup_done_body_home else R.string.setup_done_body_ready
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textDim,
            modifier = Modifier.padding(bottom = Dimens.spacingMd),
        )
        SetupDoneSummary(
            serverName = state.signIn.serverName,
            gameCount = state.signIn.gameCount,
            romPath = state.folders.selectedRomPath,
            saveSyncOn = state.saveSyncOn,
            cores = state.cores,
        )
    }
}
