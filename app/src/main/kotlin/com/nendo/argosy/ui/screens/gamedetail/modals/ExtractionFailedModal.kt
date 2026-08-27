package com.nendo.argosy.ui.screens.gamedetail.modals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.CenteredModal
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.screens.gamedetail.ExtractionFailedInfo

@Composable
fun ExtractionFailedModal(
    info: ExtractionFailedInfo,
    focusIndex: Int,
    onRetry: () -> Unit,
    onRedownload: () -> Unit,
    onDismiss: () -> Unit
) {
    CenteredModal(
        title = stringResource(R.string.gamedetail_extraction_failed_title),
        onDismiss = onDismiss,
        footerHints = listOf(
            InputButton.A to stringResource(R.string.gamedetail_extraction_failed_footer_select),
            InputButton.B to stringResource(R.string.gamedetail_extraction_failed_footer_cancel)
        )
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .size(Dimens.iconXl)
                .align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(Dimens.spacingSm))

        Text(
            text = stringResource(
                R.string.gamedetail_extraction_failed_message,
                info.fileName
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(Dimens.spacingMd))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.radiusLg, Alignment.CenterHorizontally)
        ) {
            ActionButton(
                label = stringResource(R.string.gamedetail_extraction_failed_retry),
                onClick = onRetry,
                focused = focusIndex == 0,
                primary = true
            )

            ActionButton(
                label = stringResource(R.string.gamedetail_extraction_failed_redownload),
                onClick = onRedownload,
                focused = focusIndex == 1
            )
        }
    }
}
