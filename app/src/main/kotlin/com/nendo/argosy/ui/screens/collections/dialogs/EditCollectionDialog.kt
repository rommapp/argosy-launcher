package com.nendo.argosy.ui.screens.collections.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R

@Composable
fun EditCollectionDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    CollectionNameDialog(
        title = stringResource(R.string.collections_edit_dialog_title),
        label = stringResource(R.string.collections_edit_dialog_name_label),
        confirmLabel = stringResource(R.string.collections_edit_dialog_confirm),
        initialName = currentName,
        gamepadInput = true,
        onDismiss = onDismiss,
        onSubmit = onSave
    )
}
