package com.nendo.argosy.ui.screens.gamedetail.modals

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.CenteredModal
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.components.InputButton

@Composable
fun MissingDiscModal(
    missingDiscNumbers: List<Int>,
    onDismiss: () -> Unit
) {
    val discNumbers = missingDiscNumbers.joinToString(", ")

    CenteredModal(
        title = stringResource(R.string.gamedetail_missing_disc_title),
        onDismiss = onDismiss,
        footerHints = listOf(
            InputButton.A to stringResource(R.string.gamedetail_missing_disc_footer_download),
            InputButton.B to stringResource(R.string.gamedetail_missing_disc_footer_cancel)
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

        Text(
            text = pluralStringResource(
                R.plurals.gamedetail_missing_disc_message,
                missingDiscNumbers.size,
                discNumbers
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}
