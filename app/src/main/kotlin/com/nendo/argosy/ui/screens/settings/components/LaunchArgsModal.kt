package com.nendo.argosy.ui.screens.settings.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.emulator.LaunchMethod
import com.nendo.argosy.data.emulator.RomBindingFormat
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.components.NestedModal
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.screens.settings.LaunchArgsModalState
import com.nendo.argosy.ui.screens.settings.LaunchArgsRow
import com.nendo.argosy.ui.screens.settings.launchArgsModalRows
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

@Composable
fun LaunchArgsModal(
    state: LaunchArgsModalState,
    onCycleDataBinding: () -> Unit,
    onCycleExtraBinding: () -> Unit,
    onCycleClipDataBinding: () -> Unit,
    onToggleFlag: (Int) -> Unit,
    onCycleMimeType: () -> Unit,
    onOpenCustomExtras: () -> Unit,
    onSaveCustomExtras: (String) -> Unit,
    onDismissCustomExtras: () -> Unit,
    onDismiss: () -> Unit
) {
    val rows = launchArgsModalRows(state)

    val hasOverride = state.override?.hasAnyOverride() == true
    Modal(
        title = stringResource(R.string.settings_launch_args_title, state.platformName, state.emulatorName),
        baseWidth = Dimens.modalWidthXl,
        onDismiss = onDismiss,
        footerHints = buildList {
            add(com.nendo.argosy.ui.components.InputButton.A to stringResource(R.string.settings_launch_args_hint_change))
            add(com.nendo.argosy.ui.components.InputButton.Y to stringResource(R.string.settings_launch_args_hint_reset_field))
            if (hasOverride) {
                add(com.nendo.argosy.ui.components.InputButton.X to stringResource(R.string.settings_launch_args_hint_reset_all))
            }
            add(com.nendo.argosy.ui.components.InputButton.B to stringResource(R.string.settings_launch_args_hint_back))
        }
    ) {
        Text(
            text = stringResource(R.string.settings_launch_args_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Dimens.spacingMd)
        )

        val listState = rememberLazyListState()
        com.nendo.argosy.ui.components.FocusedScroll(listState = listState, focusedIndex = state.focusIndex)

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            rows.forEachIndexed { index, row ->
                item(key = "row_$index") {
                    val focused = index == state.focusIndex
                    when (row) {
                        is LaunchArgsRow.DataBinding -> LaunchArgsOptionRow(
                            label = stringResource(R.string.settings_launch_args_data_uri_label),
                            value = bindingLabel(state.override?.dataBinding, state.defaultDataBinding),
                            subtitle = stringResource(R.string.settings_launch_args_data_uri_subtitle),
                            isOverridden = state.override?.dataBinding != null,
                            isFocused = focused,
                            onClick = onCycleDataBinding
                        )
                        is LaunchArgsRow.ExtraBinding -> LaunchArgsOptionRow(
                            label = stringResource(R.string.settings_launch_args_extras_label),
                            value = bindingLabel(state.override?.extraBinding, state.defaultExtraBinding),
                            subtitle = stringResource(R.string.settings_launch_args_extras_subtitle),
                            isOverridden = state.override?.extraBinding != null,
                            isFocused = focused,
                            onClick = onCycleExtraBinding
                        )
                        is LaunchArgsRow.ClipDataBinding -> LaunchArgsOptionRow(
                            label = stringResource(R.string.settings_launch_args_clip_data_uri_label),
                            value = bindingLabel(state.override?.clipDataBinding, state.defaultClipDataBinding),
                            subtitle = stringResource(R.string.settings_launch_args_clip_data_uri_subtitle),
                            isOverridden = state.override?.clipDataBinding != null,
                            isFocused = focused,
                            onClick = onCycleClipDataBinding
                        )
                        is LaunchArgsRow.LockedBinding -> LaunchArgsOptionRow(
                            label = stringResource(row.labelRes),
                            value = row.value,
                            subtitle = stringResource(R.string.settings_launch_args_locked_binding_subtitle),
                            isOverridden = false,
                            isFocused = focused,
                            onClick = { }
                        )
                        is LaunchArgsRow.Flag -> {
                            val mask = state.override?.intentFlagsMask ?: state.defaultFlagsMask
                            val isOn = (mask and row.bit) != 0
                            val isOverridden = state.override?.intentFlagsMask != null
                            LaunchArgsOptionRow(
                                label = stringResource(row.labelRes),
                                value = if (isOn) {
                                    stringResource(R.string.settings_launch_args_flag_on)
                                } else {
                                    stringResource(R.string.settings_launch_args_flag_off)
                                },
                                subtitle = flagSubtext(row.bit),
                                isOverridden = isOverridden,
                                isFocused = focused,
                                onClick = { onToggleFlag(row.bit) }
                            )
                        }
                        is LaunchArgsRow.MimeType -> LaunchArgsOptionRow(
                            label = stringResource(R.string.settings_launch_args_mime_type_label),
                            value = state.override?.mimeType ?: stringResource(
                                R.string.settings_launch_args_mime_type_default,
                                state.defaultMimeType ?: "*/*"
                            ),
                            subtitle = stringResource(R.string.settings_launch_args_mime_type_subtitle),
                            isOverridden = state.override?.mimeType != null,
                            isFocused = focused,
                            onClick = onCycleMimeType
                        )
                        is LaunchArgsRow.CustomExtras -> LaunchArgsOptionRow(
                            label = stringResource(R.string.settings_launch_args_custom_extras_label),
                            value = state.override?.customExtras?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.settings_launch_args_custom_extras_none),
                            subtitle = stringResource(R.string.settings_launch_args_custom_extras_subtitle),
                            isOverridden = !state.override?.customExtras.isNullOrBlank(),
                            isFocused = focused,
                            onClick = onOpenCustomExtras
                        )
                    }
                }
            }
        }
    }

    if (state.showCustomExtrasInput) {
        CustomExtrasModal(
            initialValue = state.override?.customExtras.orEmpty(),
            onSubmit = onSaveCustomExtras,
            onDismiss = onDismissCustomExtras
        )
    }
}

@Composable
private fun CustomExtrasModal(
    initialValue: String,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val inputDispatcher = LocalInputDispatcher.current
    var text by remember { mutableStateOf(initialValue) }

    val inputHandler = remember(onSubmit, onDismiss) {
        object : InputHandler {
            override fun onConfirm(): InputResult {
                onSubmit(text)
                return InputResult.HANDLED
            }

            override fun onBack(): InputResult {
                onDismiss()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.pushModal(inputHandler)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            inputDispatcher.removeModal(inputHandler)
        }
    }

    NestedModal(
        title = stringResource(R.string.settings_launch_args_custom_extras_title),
        onDismiss = onDismiss,
        footerHints = listOf(
            InputButton.A to stringResource(R.string.settings_launch_args_custom_extras_save_hint),
            InputButton.B to stringResource(R.string.settings_launch_args_custom_extras_cancel_hint)
        ),
        content = {
            Column {
                Text(
                    text = stringResource(R.string.settings_launch_args_custom_extras_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.settings_launch_args_custom_extras_placeholder),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            }
        }
    )
}

@Composable
private fun LaunchArgsOptionRow(
    label: String,
    value: String,
    subtitle: String,
    isOverridden: Boolean,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val background = if (isFocused) theme.focusAccent.copy(alpha = 0.15f) else Color.Transparent
    val focusedContent = lerp(theme.focusAccent, Color.White, 0.45f)
    val labelColor = if (isFocused) focusedContent else MaterialTheme.colorScheme.onSurface
    val valueColor = if (isFocused) {
        focusedContent.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val subtitleColor = if (isFocused) {
        focusedContent.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(background, RoundedCornerShape(Dimens.radiusMd))
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.radiusLg, vertical = Dimens.spacingSm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = labelColor
                )
                if (isOverridden) {
                    Text(
                        text = stringResource(R.string.settings_launch_args_custom_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isFocused) focusedContent else MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = valueColor
            )
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = subtitleColor,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

private fun methodLabel(override: String?, defaultName: String): String = when (override) {
    null -> "Default (${defaultName.lowercase().replaceFirstChar { it.uppercase() }})"
    LaunchMethod.INTENT.name -> "Intent"
    LaunchMethod.SHELL.name -> "Shell (am start)"
    else -> "Default"
}

@Composable
private fun bindingLabel(override: String?, defaultLabel: String): String = when (override) {
    null -> stringResource(R.string.settings_launch_args_binding_default, defaultLabel)
    RomBindingFormat.NONE.name -> stringResource(R.string.settings_launch_args_binding_none)
    RomBindingFormat.ABSOLUTE_PATH.name -> stringResource(R.string.settings_launch_args_binding_absolute_path)
    RomBindingFormat.FILE_PROVIDER.name -> stringResource(R.string.settings_launch_args_binding_file_provider)
    RomBindingFormat.DOCUMENT_URI.name -> stringResource(R.string.settings_launch_args_binding_document_uri)
    else -> stringResource(R.string.settings_launch_args_binding_default, defaultLabel)
}

@Composable
private fun flagSubtext(bit: Int): String = when (bit) {
    Intent.FLAG_ACTIVITY_NEW_TASK -> stringResource(R.string.settings_launch_args_flag_new_task_subtitle)
    Intent.FLAG_ACTIVITY_CLEAR_TASK -> stringResource(R.string.settings_launch_args_flag_clear_task_subtitle)
    Intent.FLAG_ACTIVITY_NO_HISTORY -> stringResource(R.string.settings_launch_args_flag_no_history_subtitle)
    Intent.FLAG_ACTIVITY_SINGLE_TOP -> stringResource(R.string.settings_launch_args_flag_single_top_subtitle)
    Intent.FLAG_GRANT_READ_URI_PERMISSION -> stringResource(R.string.settings_launch_args_flag_grant_uri_subtitle)
    Intent.FLAG_ACTIVITY_CLEAR_TOP -> stringResource(R.string.settings_launch_args_flag_clear_top_subtitle)
    else -> ""
}

