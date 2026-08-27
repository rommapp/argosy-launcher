package com.nendo.argosy.ui.screens.settings

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.core.notification.NotificationDuration
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.data.preferences.FontSlot
import com.nendo.argosy.ui.screens.settings.sections.ThemeFontsLayoutState
import com.nendo.argosy.ui.screens.settings.sections.themeFontsMaxFocusIndex
import com.nendo.argosy.ui.theme.CustomFontLoader
import com.nendo.argosy.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal val FONT_PICKER_MIME_TYPES = arrayOf(
    "font/ttf",
    "font/otf",
    "application/x-font-ttf",
    "application/vnd.ms-opentype"
)

private val ALLOWED_FONT_EXTENSIONS = setOf("ttf", "otf")

private sealed class FontImportResult {
    data class Success(val path: String, val name: String) : FontImportResult()
    data class Failure(val reason: String) : FontImportResult()
}

internal fun routeNavigateToThemeFonts(vm: SettingsViewModel) {
    routePushSection(vm, SettingsSection.THEME_FONTS)
}

internal fun routeImportFont(vm: SettingsViewModel, slot: FontSlot, uri: Uri) {
    vm.viewModelScope.launch {
        when (val result = withContext(Dispatchers.IO) { importFontFile(vm.context, slot, uri) }) {
            is FontImportResult.Success -> {
                vm.preferencesRepository.setCustomFont(slot, result.path, result.name)
                vm.updateFontNameState(slot, result.name)
            }
            is FontImportResult.Failure -> vm.notificationManager.show(
                title = NotificationText.Res(R.string.settings_shell_router_font_import_failed_title),
                subtitle = NotificationText.Raw(result.reason),
                type = NotificationType.ERROR,
                duration = NotificationDuration.MEDIUM
            )
        }
    }
}

internal fun routeRevertFont(vm: SettingsViewModel, slot: FontSlot) {
    vm.viewModelScope.launch {
        withContext(Dispatchers.IO) { deleteSlotFonts(vm.context, slot) }
        vm.preferencesRepository.setCustomFont(slot, null, null)
        vm.updateFontNameState(slot, null)
        if (vm._uiState.value.currentSection == SettingsSection.THEME_FONTS) {
            val reverted = ThemeFontsLayoutState.from(vm._uiState.value).let {
                when (slot) {
                    FontSlot.DISPLAY -> it.copy(displayCustom = false)
                    FontSlot.BODY -> it.copy(bodyCustom = false)
                }
            }
            val maxIndex = themeFontsMaxFocusIndex(reverted)
            vm._uiState.update { it.copy(focusedIndex = it.focusedIndex.coerceIn(0, maxIndex)) }
        }
    }
}

private fun importFontFile(context: Context, slot: FontSlot, uri: Uri): FontImportResult {
    val fileName = queryDisplayName(context, uri)
        ?: uri.lastPathSegment?.substringAfterLast('/')
        ?: return FontImportResult.Failure(context.getString(R.string.settings_shell_router_font_error_no_filename))
    val extension = fileName.substringAfterLast('.', "").lowercase()
    if (extension !in ALLOWED_FONT_EXTENSIONS) {
        return FontImportResult.Failure(context.getString(R.string.settings_shell_router_font_error_unsupported_ext))
    }
    val tempFile = File.createTempFile("font-import", ".$extension", context.cacheDir)
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        } ?: return FontImportResult.Failure(context.getString(R.string.settings_shell_router_font_error_no_stream))
        CustomFontLoader.validate(tempFile)
        val displayName = fileName.substringBeforeLast('.')
        val sanitized = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val fontsDir = CustomFontLoader.fontsDir(context).apply { mkdirs() }
        val target = File(fontsDir, "${slot.key}-$sanitized.$extension")
        tempFile.copyTo(target, overwrite = true)
        deleteSlotFonts(context, slot, keep = target)
        return FontImportResult.Success(target.absolutePath, displayName)
    } catch (e: Exception) {
        return FontImportResult.Failure(
            context.getString(R.string.settings_shell_router_font_error_invalid, fileName)
        )
    } finally {
        tempFile.delete()
    }
}

private fun deleteSlotFonts(context: Context, slot: FontSlot, keep: File? = null) {
    CustomFontLoader.fontsDir(context).listFiles()
        ?.filter { it.name.startsWith("${slot.key}-") && it.absolutePath != keep?.absolutePath }
        ?.forEach { it.delete() }
}

private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull()
