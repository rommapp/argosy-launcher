package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.data.emulator.config.ConfigFileState
import com.nendo.argosy.data.emulator.config.ConfigOptionDecoder
import com.nendo.argosy.data.emulator.config.ConfigTokens
import com.nendo.argosy.data.emulator.config.ConfigValueType
import com.nendo.argosy.ui.common.labelRes
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.ExpandablePreference
import com.nendo.argosy.ui.components.ExpandedChildItem
import com.nendo.argosy.ui.components.InfoPreference
import com.nendo.argosy.ui.components.ListSection
import com.nendo.argosy.ui.components.SliderPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.EmulatorOptionUi
import com.nendo.argosy.ui.screens.settings.EmulatorOptionsFileUi
import com.nendo.argosy.ui.screens.settings.EmulatorOptionsPhase
import com.nendo.argosy.ui.screens.settings.EmulatorOptionsState
import com.nendo.argosy.ui.screens.settings.EmulatorOtherKeyUi
import com.nendo.argosy.ui.screens.settings.EmulatorPlayTimeUi
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.SectionHeader
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.components.StatRow
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.util.formatAbsoluteTimestamp
import com.nendo.argosy.util.formatPlayTime
import java.time.Instant
import kotlin.math.roundToInt
import kotlin.math.sign

private const val MILLIS_PER_MINUTE = 60_000L

internal sealed class EmulatorOptionsRow(val key: String, val section: String?, val isFocusable: Boolean) {
    data object Loading : EmulatorOptionsRow("loading", null, false)
    data object Unsupported : EmulatorOptionsRow("unsupported", null, false)
    data object Failed : EmulatorOptionsRow("failed", null, true)
    data object AccessHeader : EmulatorOptionsRow("access_header", null, false)
    data object Access : EmulatorOptionsRow("access", null, false)
    data object NoAccess : EmulatorOptionsRow("no_access", null, false)
    data class FileHeader(val fileIndex: Int, val file: EmulatorOptionsFileUi, val fileSection: String?) :
        EmulatorOptionsRow("file_header_$fileIndex", fileSection, false)
    data class FileInfo(val fileIndex: Int, val file: EmulatorOptionsFileUi, val fileSection: String?) :
        EmulatorOptionsRow("file_info_$fileIndex", fileSection, false)
    data class Option(val fileIndex: Int, val index: Int, val option: EmulatorOptionUi) :
        EmulatorOptionsRow("option_${fileIndex}_$index", option.fileId, true)
    data class Undo(val fileIndex: Int, val index: Int, val option: EmulatorOptionUi) :
        EmulatorOptionsRow("undo_${fileIndex}_$index", option.fileId, true)
    data class PlayTime(val fileIndex: Int, val index: Int, val fileId: String, val entry: EmulatorPlayTimeUi) :
        EmulatorOptionsRow("play_${fileIndex}_$index", fileId, true)
    data class OtherKeysToggle(val fileIndex: Int, val fileId: String, val count: Int, val expanded: Boolean) :
        EmulatorOptionsRow("other_$fileIndex", fileId, true)
    data class OtherKey(val fileIndex: Int, val index: Int, val fileId: String, val entry: EmulatorOtherKeyUi) :
        EmulatorOptionsRow("other_${fileIndex}_$index", fileId, true)
}

internal fun emulatorOptionsRows(state: EmulatorOptionsState): List<EmulatorOptionsRow> = buildList {
    when (state.phase) {
        EmulatorOptionsPhase.LOADING -> add(EmulatorOptionsRow.Loading)
        EmulatorOptionsPhase.UNSUPPORTED -> add(EmulatorOptionsRow.Unsupported)
        EmulatorOptionsPhase.FAILED -> add(EmulatorOptionsRow.Failed)
        EmulatorOptionsPhase.READY -> {
            add(EmulatorOptionsRow.AccessHeader)
            add(EmulatorOptionsRow.Access)
            if (state.readRoute == null) {
                add(EmulatorOptionsRow.NoAccess)
                return@buildList
            }
            val undoable = state.undoableIds.filterTo(mutableSetOf()) { state.canWrite(it) }
            state.files.forEachIndexed { fileIndex, file ->
                val body = fileBody(fileIndex, file, state, undoable)
                val fileSection = file.id.takeIf { body.isNotEmpty() }
                add(EmulatorOptionsRow.FileHeader(fileIndex, file, fileSection))
                add(EmulatorOptionsRow.FileInfo(fileIndex, file, fileSection))
                addAll(body)
            }
        }
    }
}

private fun fileBody(
    fileIndex: Int,
    file: EmulatorOptionsFileUi,
    state: EmulatorOptionsState,
    undoable: Set<String>
): List<EmulatorOptionsRow> = buildList {
    if (file.state == ConfigFileState.BLOCKED && file.options.none { it.value != null }) return@buildList
    file.options.forEachIndexed { index, option ->
        add(EmulatorOptionsRow.Option(fileIndex, index, option))
        if (option.id in undoable) add(EmulatorOptionsRow.Undo(fileIndex, index, option))
    }
    file.playTimes.forEachIndexed { index, entry ->
        add(EmulatorOptionsRow.PlayTime(fileIndex, index, file.id, entry))
    }
    if (file.otherKeys.isNotEmpty()) {
        val expanded = file.id in state.expandedFileIds
        add(EmulatorOptionsRow.OtherKeysToggle(fileIndex, file.id, file.otherKeys.size, expanded))
        if (expanded) {
            file.otherKeys.forEachIndexed { index, entry ->
                add(EmulatorOptionsRow.OtherKey(fileIndex, index, file.id, entry))
            }
        }
    }
}

private fun emulatorOptionsLayout(rows: List<EmulatorOptionsRow>) = SettingsLayout<EmulatorOptionsRow, Unit>(
    allItems = rows,
    isFocusable = { it.isFocusable },
    visibleWhen = { _, _ -> true },
    sectionOf = { it.section }
)

internal fun emulatorOptionsMaxFocusIndex(state: EmulatorOptionsState): Int =
    emulatorOptionsLayout(emulatorOptionsRows(state)).maxFocusIndex(Unit)

internal fun emulatorOptionsRowAtFocusIndex(index: Int, state: EmulatorOptionsState): EmulatorOptionsRow? =
    emulatorOptionsLayout(emulatorOptionsRows(state)).itemAtFocusIndex(index, Unit)

internal fun emulatorOptionsSections(state: EmulatorOptionsState, named: Boolean = false): List<ListSection> {
    val rows = emulatorOptionsRows(state)
    val layout = emulatorOptionsLayout(rows)
    val visible = layout.visibleItems(Unit)
    return layout.buildSections(Unit).map { section ->
        val header = visible.getOrNull(section.listStartIndex) as? EmulatorOptionsRow.FileHeader
        if (named) section.copy(name = header?.file?.id?.substringAfterLast('/')) else section
    }
}

@Composable
fun EmulatorOptionsSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
) {
    val state = uiState.emulatorOptions
    val rows = remember(state) { emulatorOptionsRows(state) }
    val layout = remember(rows) { emulatorOptionsLayout(rows) }
    val visible = remember(layout) { layout.visibleItems(Unit) }
    val sections = remember(state) { emulatorOptionsSections(state, named = true) }

    SectionPaneLayout(
        items = visible,
        sections = sections,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { layout.focusToListIndex(it, Unit) },
        itemKey = { it.key },
        isNavItem = { false },
        isHeader = { it is EmulatorOptionsRow.FileHeader || it is EmulatorOptionsRow.AccessHeader },
        onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) { row ->
        val index = layout.focusIndexOf(row, Unit)
        val isFocused = index >= 0 && uiState.focusedIndex == index
        when (row) {
            is EmulatorOptionsRow.Option -> OptionRow(
                option = row.option,
                state = state,
                isFocused = isFocused,
                pickerToken = if (uiState.enumPickerKey == emulatorOptionPickerKey(row.option)) uiState.enumPickerToken else 0,
                onFocus = { viewModel.setFocusIndex(index) },
                viewModel = viewModel
            )
            is EmulatorOptionsRow.Undo -> ActionPreference(
                title = stringResource(R.string.settings_emulator_options_undo_title),
                subtitle = stringResource(R.string.settings_emulator_options_undo_subtitle, row.option.key),
                isFocused = isFocused,
                icon = Icons.AutoMirrored.Filled.Undo,
                isEnabled = !state.writing,
                onClick = {
                    viewModel.setFocusIndex(index)
                    viewModel.undoEmulatorOption(row.option.id)
                }
            )
            else -> EmulatorOptionsRowContent(
                row = row,
                state = state,
                isFocused = isFocused,
                onToggleOtherKeys = { fileId ->
                    viewModel.setFocusIndex(index)
                    viewModel.toggleEmulatorOptionsOtherKeys(fileId)
                },
                onRetry = {
                    viewModel.setFocusIndex(index)
                    viewModel.retryEmulatorOptions()
                }
            )
        }
    }
}

internal fun emulatorOptionPickerKey(option: EmulatorOptionUi): String = "emulator_option:${option.id}"

@Composable
private fun OptionRow(
    option: EmulatorOptionUi,
    state: EmulatorOptionsState,
    isFocused: Boolean,
    pickerToken: Int,
    onFocus: () -> Unit,
    viewModel: SettingsViewModel
) {
    val section = option.section.ifEmpty { null }
    val isDriver = option.id == state.driverOptionId
    if (!option.editable || !state.canWrite(option.id)) {
        val block = if (option.editable && !isDriver && state.editRoute == null) {
            stringResource(state.editBlock?.labelRes ?: R.string.settings_emulator_options_edit_needs_root)
        } else {
            null
        }
        InfoPreference(
            title = option.key,
            value = if (isDriver) driverValueText(option, state) else optionValueText(option),
            isFocused = isFocused,
            subtitle = when {
                block == null -> section
                section == null -> block
                else -> stringResource(R.string.settings_emulator_options_option_blocked, section, block)
            }
        )
        return
    }
    val adjust: (Int) -> Unit = { direction ->
        onFocus()
        viewModel.adjustEmulatorOption(option.id, direction)
    }
    when {
        isDriver -> {
            val systemName = stringResource(R.string.settings_emulator_options_driver_system)
            val names = remember(state.drivers, systemName) { listOf(systemName) + state.drivers.map { it.name } }
            CyclePreference(
                title = option.key,
                value = driverValueText(option, state),
                isFocused = isFocused,
                onClick = { adjust(1) },
                onPrev = { adjust(-1) },
                subtitle = section,
                options = names,
                onSelect = { choice ->
                    onFocus()
                    state.driverTokens.getOrNull(choice)?.let { viewModel.selectEmulatorOptionValue(option.id, it) }
                },
                pickerRequestToken = pickerToken,
                selectedIndex = state.driverTokens.indexOf(option.token.orEmpty())
            )
        }
        option.choices.isNotEmpty() -> {
            val names = remember(option.choices) { option.choices.map { it.name } }
            val token = option.token
            CyclePreference(
                title = option.key,
                value = optionValueText(option),
                isFocused = isFocused,
                onClick = { adjust(1) },
                onPrev = { adjust(-1) },
                subtitle = section,
                options = names,
                onSelect = { choice ->
                    onFocus()
                    option.choices.getOrNull(choice)?.let { viewModel.selectEmulatorOptionValue(option.id, it.token) }
                },
                pickerRequestToken = pickerToken,
                selectedIndex = token?.let { current ->
                    option.choices.indexOfFirst { ConfigOptionDecoder.same(option.type, it.token, current) }
                }
            )
        }
        option.type == ConfigValueType.BOOL -> SwitchPreference(
            title = option.key,
            isEnabled = ConfigTokens.isOn(option.token),
            isFocused = isFocused,
            onToggle = {
                onFocus()
                viewModel.toggleEmulatorOption(option.id)
            },
            subtitle = section
        )
        else -> NumberOptionRow(option, isFocused, adjust)
    }
}

@Composable
private fun NumberOptionRow(option: EmulatorOptionUi, isFocused: Boolean, adjust: (Int) -> Unit) {
    val range = option.range ?: return
    val current = option.token?.toDoubleOrNull() ?: range.min
    val steps = ((range.max - range.min) / range.step).roundToInt()
    val position = ((current - range.min) / range.step).roundToInt().coerceIn(0, steps)
    SliderPreference(
        title = option.key,
        value = position,
        minValue = 0,
        maxValue = steps,
        isFocused = isFocused,
        onAdjust = { delta -> adjust(delta.sign) },
        valueLabel = optionValueText(option)
    )
}

@Composable
private fun EmulatorOptionsRowContent(
    row: EmulatorOptionsRow,
    state: EmulatorOptionsState,
    isFocused: Boolean,
    onToggleOtherKeys: (String) -> Unit,
    onRetry: () -> Unit
) {
    when (row) {
        EmulatorOptionsRow.Loading -> InfoPreference(
            title = stringResource(R.string.settings_emulator_options_loading),
            value = "",
            isFocused = false
        )
        EmulatorOptionsRow.Unsupported -> InfoPreference(
            title = stringResource(R.string.settings_emulator_options_unsupported_title),
            value = "",
            isFocused = false,
            subtitle = stringResource(R.string.settings_emulator_options_unsupported_message)
        )
        EmulatorOptionsRow.Failed -> ActionPreference(
            title = stringResource(R.string.settings_emulator_options_failed_title),
            subtitle = stringResource(R.string.settings_emulator_options_failed_retry),
            isFocused = isFocused,
            icon = Icons.Default.Refresh,
            onClick = onRetry
        )
        EmulatorOptionsRow.AccessHeader -> SectionHeader(stringResource(R.string.settings_emulator_options_section_access))
        EmulatorOptionsRow.Access -> AccessSummary(state)
        EmulatorOptionsRow.NoAccess -> InfoPreference(
            title = stringResource(R.string.settings_emulator_options_no_access_title),
            value = "",
            isFocused = false,
            subtitle = stringResource(R.string.settings_emulator_options_no_access_message)
        )
        is EmulatorOptionsRow.FileHeader -> SectionHeader(row.file.id.substringAfterLast('/'))
        is EmulatorOptionsRow.FileInfo -> FileSummary(row.file)
        is EmulatorOptionsRow.Option, is EmulatorOptionsRow.Undo -> Unit
        is EmulatorOptionsRow.PlayTime -> InfoPreference(
            title = row.entry.gameId,
            value = formatPlayTime(LocalContext.current, (row.entry.playedMs / MILLIS_PER_MINUTE).toInt()),
            isFocused = isFocused
        )
        is EmulatorOptionsRow.OtherKeysToggle -> ExpandablePreference(
            title = stringResource(R.string.settings_emulator_options_other_keys_title),
            subtitle = pluralStringResource(
                R.plurals.settings_emulator_options_other_keys_count,
                row.count,
                row.count
            ),
            isExpanded = row.expanded,
            isFocused = isFocused,
            onToggle = { onToggleOtherKeys(row.fileId) }
        )
        is EmulatorOptionsRow.OtherKey -> ExpandedChildItem(
            title = listOf(row.entry.section, row.entry.key).filter { it.isNotEmpty() }.joinToString("/"),
            value = if (row.entry.hidden) {
                stringResource(R.string.settings_emulator_options_value_hidden)
            } else {
                row.entry.value.orEmpty()
            },
            isFocused = isFocused,
            onClick = {}
        )
    }
}

@Composable
private fun SummaryColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        content()
    }
}

@Composable
private fun AccessSummary(state: EmulatorOptionsState) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val valueColor = MaterialTheme.colorScheme.onSurface
    val style = MaterialTheme.typography.bodySmall
    SummaryColumn {
        StatRow(
            stringResource(R.string.settings_emulator_options_emulator),
            state.emulatorName.orEmpty(),
            labelColor,
            valueColor,
            style
        )
        StatRow(
            stringResource(R.string.settings_emulator_options_read_access),
            stringResource(state.readRoute?.labelRes ?: R.string.settings_emulator_options_read_none),
            labelColor,
            valueColor,
            style
        )
        StatRow(
            stringResource(R.string.settings_emulator_options_editing),
            stringResource(
                when {
                    state.writing -> R.string.settings_emulator_options_edit_applying
                    state.editRoute != null -> R.string.settings_emulator_options_edit_available
                    else -> state.editBlock?.labelRes ?: R.string.settings_emulator_options_edit_needs_root
                }
            ),
            labelColor,
            valueColor,
            style
        )
        Text(
            text = stringResource(R.string.settings_emulator_options_data_folder),
            style = style,
            color = labelColor
        )
        Text(
            text = state.dataRoot ?: stringResource(R.string.settings_emulator_options_data_folder_none),
            style = style,
            color = valueColor
        )
    }
}

@Composable
private fun FileSummary(file: EmulatorOptionsFileUi) {
    val context = LocalContext.current
    val modifiedAt = file.modifiedAt
    val style = MaterialTheme.typography.bodySmall
    SummaryColumn {
        Text(
            text = if (file.state == ConfigFileState.READ && modifiedAt != null) {
                stringResource(
                    R.string.settings_emulator_options_file_modified,
                    formatAbsoluteTimestamp(context, Instant.ofEpochMilli(modifiedAt))
                )
            } else {
                stringResource(file.state.labelRes)
            },
            style = style,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(text = file.path, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun driverValueText(option: EmulatorOptionUi, state: EmulatorOptionsState): String {
    val token = option.token
    return when {
        token.isNullOrEmpty() -> stringResource(R.string.settings_emulator_options_driver_system)
        else -> state.drivers.firstOrNull { it.path == token }?.name ?: token.trimEnd('/').substringAfterLast('/')
    }
}

@Composable
private fun optionValueText(option: EmulatorOptionUi): String {
    val value = option.value
    return when {
        option.hidden -> stringResource(R.string.settings_emulator_options_value_hidden)
        value.isNullOrEmpty() && option.isDefault -> stringResource(R.string.settings_emulator_options_value_default)
        value == null -> stringResource(R.string.settings_emulator_options_value_unknown)
        option.isDefault -> stringResource(R.string.settings_emulator_options_value_with_default, value)
        else -> value
    }
}
