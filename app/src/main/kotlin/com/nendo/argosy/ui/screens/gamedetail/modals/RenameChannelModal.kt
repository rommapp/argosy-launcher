package com.nendo.argosy.ui.screens.gamedetail.modals

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.NestedModal

@Composable
fun RenameChannelModal(
    text: String,
    onTextChange: (String) -> Unit
) {
    NestedModal(
        title = stringResource(R.string.gamedetail_rename_channel_title),
        footerHints = listOf(
            InputButton.A to stringResource(R.string.gamedetail_rename_channel_footer_confirm),
            InputButton.B to stringResource(R.string.gamedetail_rename_channel_footer_cancel)
        )
    ) {
        Text(
            text = stringResource(R.string.gamedetail_rename_channel_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(stringResource(R.string.gamedetail_rename_channel_placeholder))
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}
