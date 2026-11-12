package com.nendo.argosy.ui.screens.setup.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.CoreDownloadProgress
import com.nendo.argosy.ui.primitives.ArgosyIndeterminateProgressBar
import com.nendo.argosy.ui.theme.Dimens

@Composable
fun SetupDoneSummary(
    serverName: String,
    gameCount: Int,
    romPath: String?,
    saveSyncOn: Boolean,
    cores: CoreDownloadProgress,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SetupSummaryRow(
            icon = Icons.Default.Check,
            label = stringResource(R.string.setup_done_summary_library),
            value = pluralStringResource(R.plurals.setup_done_library_value, gameCount, serverName, gameCount),
        )
        romPath?.let { path ->
            SetupSummaryRow(
                icon = Icons.Default.Check,
                label = stringResource(R.string.setup_done_summary_games),
                value = path,
            )
        }
        SetupSummaryRow(
            icon = Icons.Default.Check,
            label = stringResource(R.string.setup_done_summary_save_sync),
            value = stringResource(if (saveSyncOn) R.string.setup_done_summary_on else R.string.setup_done_summary_off),
        )
        EmulatorSummary(cores)
    }
}

@Composable
private fun EmulatorSummary(cores: CoreDownloadProgress) {
    if (cores.total == 0) return
    val value = when {
        cores.running -> pluralStringResource(
            R.plurals.setup_done_emulators_downloading,
            cores.total,
            cores.completed + cores.failed,
            cores.total
        )
        cores.failed > 0 -> pluralStringResource(R.plurals.setup_done_emulators_partial, cores.total, cores.completed, cores.total)
        else -> stringResource(R.string.setup_done_emulators_done)
    }
    SetupSummaryRow(
        icon = if (cores.running) Icons.Default.Download else Icons.Default.Check,
        label = stringResource(R.string.setup_done_summary_emulators),
        value = value,
    )
    if (cores.running) {
        ArgosyIndeterminateProgressBar(
            modifier = Modifier
                .padding(start = Dimens.iconSm + Dimens.spacingMd)
                .fillMaxWidth()
        )
    }
}
