package com.nendo.argosy.ui.screens.settings

data class SettingsPlatformScope(val platformId: Long, val platformName: String)

internal val SECTIONS_WITH_OWN_PRESENTATION = setOf(
    SettingsSection.PLAY_TIME,
    SettingsSection.STORAGE,
    SettingsSection.HOME_SCREEN,
    SettingsSection.PRESENTATION,
    SettingsSection.SHADER_STACK,
    SettingsSection.FRAME_PICKER
)

internal val BUILTIN_PLATFORM_CONTEXT_SECTIONS = setOf(
    SettingsSection.BUILTIN_VIDEO,
    SettingsSection.BUILTIN_CONTROLS,
    SettingsSection.BUILTIN_SAVES
)

internal fun settingsPlatformScope(state: SettingsUiState): SettingsPlatformScope? =
    when (state.currentSection) {
        SettingsSection.PLATFORM_DETAIL, SettingsSection.EMULATOR_OPTIONS ->
            state.emulators.platforms.getOrNull(state.platformDetail.platformIndex)?.platform
                ?.let { SettingsPlatformScope(it.id, it.name) }
        SettingsSection.STORAGE_PLATFORM_GAMES ->
            state.storagePlatformGames.takeIf { it.selectedPlatformId >= 0 }
                ?.let { SettingsPlatformScope(it.selectedPlatformId, it.platformName) }
        in BUILTIN_PLATFORM_CONTEXT_SECTIONS ->
            state.builtinVideo.currentPlatformContext?.let { SettingsPlatformScope(it.platformId, it.platformName) }
        SettingsSection.CORE_OPTIONS ->
            state.coreOptions.currentPlatformContext?.let { SettingsPlatformScope(it.platformId, it.platformName) }
        SettingsSection.BIOS -> {
            val slug = state.bios.platformGroups.getOrNull(state.bios.expandedPlatformIndex)?.platformSlug
            state.emulators.platforms.firstOrNull { it.platform.slug == slug }?.platform
                ?.let { SettingsPlatformScope(it.id, it.name) }
        }
        else -> null
    }
