package com.nendo.argosy.ui.screens.collections.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.primitives.ArgosyConfirmModalHost

@Composable
fun DeleteCollectionDialog(
    collectionName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    ArgosyConfirmModalHost(
        visible = true,
        title = stringResource(R.string.collections_delete_dialog_title),
        message = stringResource(R.string.collections_delete_dialog_message, collectionName),
        confirmLabel = stringResource(R.string.collections_delete_dialog_confirm),
        destructive = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}
