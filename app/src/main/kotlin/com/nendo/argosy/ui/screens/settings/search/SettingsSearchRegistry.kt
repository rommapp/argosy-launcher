package com.nendo.argosy.ui.screens.settings.search

import com.nendo.argosy.R
import com.nendo.argosy.ui.common.DisplayText
import com.nendo.argosy.ui.screens.settings.SettingsSection
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.PlatformDetailState
import com.nendo.argosy.ui.screens.settings.inPlatformContext
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.screens.settings.sections.BuiltinEmulatorItem
import com.nendo.argosy.ui.screens.settings.sections.BuiltinSavesLayoutState
import com.nendo.argosy.ui.screens.settings.sections.BuiltinVideoLayoutState
import com.nendo.argosy.ui.screens.settings.sections.LibraryLayoutState
import com.nendo.argosy.ui.screens.settings.sections.builtinControlsLayout
import com.nendo.argosy.ui.screens.settings.sections.builtinEmulatorLayout
import com.nendo.argosy.ui.screens.settings.sections.builtinSavesLayout
import com.nendo.argosy.ui.screens.settings.sections.builtinVideoLayout
import com.nendo.argosy.ui.screens.settings.sections.createPlatformDetailLayout
import com.nendo.argosy.ui.screens.settings.sections.homeScreenLayout
import com.nendo.argosy.ui.screens.settings.sections.libraryLayout
import com.nendo.argosy.ui.screens.settings.sections.navigationLayout
import com.nendo.argosy.ui.screens.settings.sections.platformDetailVisibilityOf
import com.nendo.argosy.ui.screens.settings.sections.presentationLayout
import com.nendo.argosy.ui.screens.settings.sections.DisplaysItem
import com.nendo.argosy.ui.screens.settings.sections.DisplaysLayoutState
import com.nendo.argosy.ui.screens.settings.sections.AboutLayoutState
import com.nendo.argosy.ui.screens.settings.sections.JellyfinLayoutState
import com.nendo.argosy.ui.screens.settings.sections.RALayoutState
import com.nendo.argosy.ui.screens.settings.sections.RomMItem
import com.nendo.argosy.ui.screens.settings.sections.RomMLayoutState
import com.nendo.argosy.ui.screens.settings.sections.SavesLayoutState
import com.nendo.argosy.ui.screens.settings.sections.SocialLayoutState
import com.nendo.argosy.ui.screens.settings.sections.aboutLayout
import com.nendo.argosy.ui.screens.settings.sections.jellyfinLayout
import com.nendo.argosy.ui.screens.settings.sections.permissionsLayout
import com.nendo.argosy.ui.screens.settings.sections.raLayout
import com.nendo.argosy.ui.screens.settings.sections.rommFocusIndexOf
import com.nendo.argosy.ui.screens.settings.sections.rommLayout
import com.nendo.argosy.ui.screens.settings.sections.savesLayout
import com.nendo.argosy.ui.screens.settings.sections.socialLayout
import com.nendo.argosy.ui.screens.settings.sections.syncSettingsLayout
import com.nendo.argosy.ui.screens.settings.sections.ambientLedLayout
import com.nendo.argosy.ui.screens.settings.sections.audioLayout
import com.nendo.argosy.ui.screens.settings.sections.displaysFocusIndexOf
import com.nendo.argosy.ui.screens.settings.sections.displaysLayout
import com.nendo.argosy.ui.screens.settings.sections.themeLayout
import com.nendo.argosy.ui.screens.settings.sections.AudioItem
import com.nendo.argosy.ui.screens.settings.sections.InterfaceItem
import com.nendo.argosy.ui.screens.settings.sections.InterfaceLayoutState
import com.nendo.argosy.ui.screens.settings.sections.StorageCachesLayoutState
import com.nendo.argosy.ui.screens.settings.sections.StorageItem
import com.nendo.argosy.ui.screens.settings.sections.StorageLayoutState
import com.nendo.argosy.ui.screens.settings.sections.ThemeBackdropLayoutState
import com.nendo.argosy.ui.screens.settings.sections.ThemeFontsLayoutState
import com.nendo.argosy.ui.screens.settings.sections.ThemeItem
import com.nendo.argosy.ui.screens.settings.sections.ThemeMusicLayoutState
import com.nendo.argosy.ui.screens.settings.sections.ThemeSoundsLayoutState
import com.nendo.argosy.ui.screens.settings.sections.audioFocusIndexOf
import com.nendo.argosy.ui.screens.settings.sections.biosSettingsLayout
import com.nendo.argosy.ui.screens.settings.sections.boxArtLayout
import com.nendo.argosy.ui.screens.settings.sections.controllerGripLayout
import com.nendo.argosy.ui.screens.settings.sections.createStorageLayoutInfo
import com.nendo.argosy.ui.screens.settings.sections.interfaceFocusIndexOf
import com.nendo.argosy.ui.screens.settings.sections.interfaceLayout
import com.nendo.argosy.ui.screens.settings.sections.storageCachesLayout
import com.nendo.argosy.ui.screens.settings.sections.storageFocusIndexOf
import com.nendo.argosy.ui.screens.settings.sections.storageLayout
import com.nendo.argosy.ui.screens.settings.sections.themeBackdropLayout
import com.nendo.argosy.ui.screens.settings.sections.themeFocusIndexOf
import com.nendo.argosy.ui.screens.settings.sections.themeFontsLayout
import com.nendo.argosy.ui.screens.settings.sections.themeMusicLayout
import com.nendo.argosy.ui.screens.settings.sections.themeSoundsLayout

internal enum class SettingsSearchProvider {
    ENABLED_PLATFORMS,
    ENABLED_PLATFORM_ROWS,
    DOWNLOADED_CORE_OPTIONS
}

internal sealed interface SettingsSearchSource {
    val section: SettingsSection

    class Rows<Item : Any, State>(
        override val section: SettingsSection,
        val path: List<Int>,
        val layout: SettingsLayout<Item, State>,
        val stateOf: (SettingsUiState) -> State,
        val keyOf: (Item) -> String,
        val reachable: (SettingsUiState) -> Boolean = { true },
        val open: (SettingsViewModel) -> Unit
    ) : SettingsSearchSource {
        fun entries(ui: SettingsUiState): List<SettingsSearchEntry> {
            if (!reachable(ui)) return emptyList()
            val crumbs = path.map { DisplayText.Res(it) }
            return layout.focusableItems(stateOf(ui)).mapNotNull { item ->
                val row = item as? SearchableSetting ?: return@mapNotNull null
                SettingsSearchEntry(
                    target = SettingsSearchTarget.Row(section, keyOf(item)),
                    label = DisplayText.Res(row.labelRes),
                    keywords = row.keywordsRes?.let { DisplayText.Res(it) },
                    path = crumbs
                )
            }
        }

        fun focusIndexOf(key: String, ui: SettingsUiState): Int =
            layout.focusableItems(stateOf(ui)).indexOfFirst { keyOf(it) == key }
    }

    class Provided(
        override val section: SettingsSection,
        val provider: SettingsSearchProvider
    ) : SettingsSearchSource

    class NotSearchable(override val section: SettingsSection) : SettingsSearchSource

    class NotYetIndexed(override val section: SettingsSection) : SettingsSearchSource
}

internal fun settingsSearchSource(section: SettingsSection): SettingsSearchSource = when (section) {
    SettingsSection.MAIN -> SettingsSearchSource.NotSearchable(SettingsSection.MAIN)
    SettingsSection.ACCOUNTS -> SettingsSearchSource.NotSearchable(SettingsSection.ACCOUNTS)
    SettingsSection.ROMM -> SettingsSearchSource.Rows(
        section = SettingsSection.ROMM,
        path = listOf(R.string.settings_search_crumb_romm),
        layout = rommLayout,
        stateOf = { RomMLayoutState.from(it) },
        keyOf = { it.key },
        reachable = { !it.server.rommConfiguring },
        open = { it.navigateToSection(SettingsSection.ROMM) }
    )
    SettingsSection.SAVES -> SettingsSearchSource.Rows(
        section = SettingsSection.SAVES,
        path = listOf(R.string.settings_search_crumb_saves),
        layout = savesLayout,
        stateOf = { SavesLayoutState.from(it) },
        keyOf = { it.key },
        open = { it.navigateToSection(SettingsSection.SAVES) }
    )
    SettingsSection.SYNC_SETTINGS -> SettingsSearchSource.Rows(
        section = SettingsSection.SYNC_SETTINGS,
        path = listOf(R.string.settings_search_crumb_romm, R.string.settings_search_crumb_sync_settings),
        layout = syncSettingsLayout,
        stateOf = { false },
        keyOf = { it.key },
        reachable = { RomMLayoutState.from(it).isConnected && !it.server.rommConfiguring },
        open = { vm ->
            val parentFocus = rommFocusIndexOf(RomMItem.SyncSettings, RomMLayoutState.from(vm.uiState.value))
            vm.navigateToSection(SettingsSection.ROMM)
            vm.setFocusIndex(parentFocus)
            vm.navigateToSection(SettingsSection.SYNC_SETTINGS)
        }
    )
    SettingsSection.STEAM_SETTINGS -> SettingsSearchSource.NotSearchable(SettingsSection.STEAM_SETTINGS)
    SettingsSection.JELLYFIN -> SettingsSearchSource.Rows(
        section = SettingsSection.JELLYFIN,
        path = listOf(R.string.settings_search_crumb_jellyfin),
        layout = jellyfinLayout,
        stateOf = { JellyfinLayoutState.from(it) },
        keyOf = { it.key },
        reachable = { !it.jellyfin.configuring && !it.jellyfin.showLoginForm },
        open = { it.navigateToSection(SettingsSection.JELLYFIN) }
    )
    SettingsSection.NETWORK_BLUETOOTH -> SettingsSearchSource.NotSearchable(SettingsSection.NETWORK_BLUETOOTH)
    SettingsSection.RETRO_ACHIEVEMENTS -> SettingsSearchSource.Rows(
        section = SettingsSection.RETRO_ACHIEVEMENTS,
        path = listOf(R.string.settings_search_crumb_retroachievements),
        layout = raLayout,
        stateOf = { RALayoutState.from(it) },
        keyOf = { it.key },
        reachable = { !it.retroAchievements.showLoginForm },
        open = { it.navigateToSection(SettingsSection.RETRO_ACHIEVEMENTS) }
    )
    SettingsSection.STORAGE -> SettingsSearchSource.Rows(
        section = SettingsSection.STORAGE,
        path = listOf(R.string.settings_search_crumb_storage),
        layout = storageLayout,
        stateOf = { StorageLayoutState.from(it) },
        keyOf = { it.key },
        open = { it.navigateToSection(SettingsSection.STORAGE) }
    )
    SettingsSection.STORAGE_GAMES -> SettingsSearchSource.NotSearchable(SettingsSection.STORAGE_GAMES)
    SettingsSection.STORAGE_MEDIA -> SettingsSearchSource.NotSearchable(SettingsSection.STORAGE_MEDIA)
    SettingsSection.STORAGE_PLATFORM_GAMES ->
        SettingsSearchSource.NotSearchable(SettingsSection.STORAGE_PLATFORM_GAMES)
    SettingsSection.STORAGE_CACHES -> SettingsSearchSource.Rows(
        section = SettingsSection.STORAGE_CACHES,
        path = listOf(R.string.settings_search_crumb_storage, R.string.settings_search_crumb_storage_caches),
        layout = storageCachesLayout,
        stateOf = { StorageCachesLayoutState.from(it) },
        keyOf = { it.key },
        open = { vm ->
            vm.navigateToSection(SettingsSection.STORAGE)
            vm.setFocusIndex(storageFocusIndexOf(StorageItem.CachesTile, createStorageLayoutInfo(vm.uiState.value)))
            vm.navigateToStorageCaches()
        }
    )
    SettingsSection.PLAY_TIME -> SettingsSearchSource.NotSearchable(SettingsSection.PLAY_TIME)
    SettingsSection.PLAY_TIME_PLATFORMS -> SettingsSearchSource.NotSearchable(SettingsSection.PLAY_TIME_PLATFORMS)
    SettingsSection.PLAY_TIME_DEVICES -> SettingsSearchSource.NotSearchable(SettingsSection.PLAY_TIME_DEVICES)
    SettingsSection.PLAY_TIME_GAMES -> SettingsSearchSource.NotSearchable(SettingsSection.PLAY_TIME_GAMES)
    SettingsSection.BIOS -> SettingsSearchSource.Rows(
        section = SettingsSection.BIOS,
        path = listOf(R.string.settings_search_crumb_bios),
        layout = biosSettingsLayout,
        stateOf = { },
        keyOf = { it.key },
        open = { it.navigateToSection(SettingsSection.BIOS) }
    )
    SettingsSection.THEME -> SettingsSearchSource.Rows(
        section = SettingsSection.THEME,
        path = listOf(R.string.settings_search_crumb_theme),
        layout = themeLayout,
        stateOf = { },
        keyOf = { it.key },
        open = { it.navigateToSection(SettingsSection.THEME) }
    )
    SettingsSection.AUDIO -> SettingsSearchSource.Rows(
        section = SettingsSection.AUDIO,
        path = listOf(R.string.settings_search_crumb_audio),
        layout = audioLayout,
        stateOf = { },
        keyOf = { it.key },
        open = { it.navigateToSection(SettingsSection.AUDIO) }
    )
    SettingsSection.THEME_SOUNDS -> SettingsSearchSource.Rows(
        section = SettingsSection.THEME_SOUNDS,
        path = listOf(R.string.settings_search_crumb_audio, R.string.settings_search_crumb_sounds),
        layout = themeSoundsLayout,
        stateOf = { ThemeSoundsLayoutState.from(it) },
        keyOf = { it.key },
        open = { vm ->
            vm.navigateToSection(SettingsSection.AUDIO)
            vm.setFocusIndex(audioFocusIndexOf(AudioItem.Sounds))
            vm.navigateToThemeSounds()
        }
    )
    SettingsSection.THEME_MUSIC -> SettingsSearchSource.Rows(
        section = SettingsSection.THEME_MUSIC,
        path = listOf(R.string.settings_search_crumb_audio, R.string.settings_search_crumb_music),
        layout = themeMusicLayout,
        stateOf = { ThemeMusicLayoutState.from(it) },
        keyOf = { it.key },
        open = { vm ->
            vm.navigateToSection(SettingsSection.AUDIO)
            vm.setFocusIndex(audioFocusIndexOf(AudioItem.Music))
            vm.navigateToThemeMusic()
        }
    )
    SettingsSection.THEME_FONTS -> SettingsSearchSource.Rows(
        section = SettingsSection.THEME_FONTS,
        path = listOf(R.string.settings_search_crumb_theme, R.string.settings_search_crumb_fonts),
        layout = themeFontsLayout,
        stateOf = { ThemeFontsLayoutState.from(it) },
        keyOf = { it.key },
        open = { vm ->
            vm.navigateToSection(SettingsSection.THEME)
            vm.setFocusIndex(themeFocusIndexOf(ThemeItem.Fonts))
            vm.navigateToThemeFonts()
        }
    )
    SettingsSection.THEME_BACKDROP -> SettingsSearchSource.Rows(
        section = SettingsSection.THEME_BACKDROP,
        path = listOf(R.string.settings_search_crumb_theme, R.string.settings_search_crumb_backdrop),
        layout = themeBackdropLayout,
        stateOf = { ThemeBackdropLayoutState.from(it) },
        keyOf = { it.key },
        open = { vm ->
            vm.navigateToSection(SettingsSection.THEME)
            vm.setFocusIndex(themeFocusIndexOf(ThemeItem.Backdrop))
            vm.navigateToThemeBackdrop()
        }
    )
    SettingsSection.INTERFACE -> SettingsSearchSource.Rows(
        section = SettingsSection.INTERFACE,
        path = listOf(R.string.settings_search_crumb_interface),
        layout = interfaceLayout,
        stateOf = { InterfaceLayoutState.from(it) },
        keyOf = { it.key },
        open = { it.navigateToSection(SettingsSection.INTERFACE) }
    )
    SettingsSection.BOX_ART -> SettingsSearchSource.Rows(
        section = SettingsSection.BOX_ART,
        path = listOf(R.string.settings_search_crumb_interface, R.string.settings_search_crumb_box_art),
        layout = boxArtLayout,
        stateOf = { it.display },
        keyOf = { it.key },
        open = { vm ->
            vm.navigateToSection(SettingsSection.INTERFACE)
            vm.setFocusIndex(
                interfaceFocusIndexOf(InterfaceItem.BoxArt, InterfaceLayoutState.from(vm.uiState.value))
            )
            vm.navigateToBoxArt()
        }
    )
    SettingsSection.CONTROLLER_GRIP -> SettingsSearchSource.Rows(
        section = SettingsSection.CONTROLLER_GRIP,
        path = listOf(R.string.settings_search_crumb_interface, R.string.settings_search_crumb_controller_grip),
        layout = controllerGripLayout,
        stateOf = { it.display },
        keyOf = { it.key },
        open = { vm ->
            vm.navigateToSection(SettingsSection.INTERFACE)
            vm.setFocusIndex(
                interfaceFocusIndexOf(InterfaceItem.ControllerGrip, InterfaceLayoutState.from(vm.uiState.value))
            )
            vm.navigateToControllerGrip()
        }
    )
    SettingsSection.HOME_SCREEN -> SettingsSearchSource.Rows(
        section = SettingsSection.HOME_SCREEN,
        path = listOf(R.string.settings_search_crumb_interface, R.string.settings_search_crumb_home_screen),
        layout = homeScreenLayout,
        stateOf = { it.display },
        keyOf = { it.key },
        open = { vm ->
            vm.navigateToSection(SettingsSection.INTERFACE)
            vm.setFocusIndex(
                interfaceFocusIndexOf(InterfaceItem.HomeScreen, InterfaceLayoutState.from(vm.uiState.value))
            )
            vm.navigateToHomeScreen()
        }
    )
    SettingsSection.PRESENTATION -> SettingsSearchSource.Rows(
        section = SettingsSection.PRESENTATION,
        path = listOf(R.string.settings_search_crumb_interface, R.string.settings_search_crumb_presentation),
        layout = presentationLayout,
        stateOf = { it.display.presentationStyle },
        keyOf = { it.key },
        reachable = { interfaceFocusIndexOf(InterfaceItem.Presentation, InterfaceLayoutState.from(it)) >= 0 },
        open = { vm ->
            vm.navigateToSection(SettingsSection.INTERFACE)
            vm.setFocusIndex(
                interfaceFocusIndexOf(InterfaceItem.Presentation, InterfaceLayoutState.from(vm.uiState.value))
            )
            vm.navigateToPresentation()
        }
    )
    SettingsSection.LIBRARY_VIEW -> SettingsSearchSource.Rows(
        section = SettingsSection.LIBRARY_VIEW,
        path = listOf(R.string.settings_search_crumb_interface, R.string.settings_search_crumb_library_view),
        layout = libraryLayout,
        stateOf = { LibraryLayoutState.from(it) },
        keyOf = { it.key },
        open = { vm ->
            vm.navigateToSection(SettingsSection.INTERFACE)
            vm.setFocusIndex(
                interfaceFocusIndexOf(InterfaceItem.LibraryView, InterfaceLayoutState.from(vm.uiState.value))
            )
            vm.navigateToLibraryView()
        }
    )
    SettingsSection.DISPLAYS -> SettingsSearchSource.Rows(
        section = SettingsSection.DISPLAYS,
        path = listOf(R.string.settings_search_crumb_displays),
        layout = displaysLayout,
        stateOf = { DisplaysLayoutState.from(it) },
        keyOf = { it.key },
        open = { it.navigateToSection(SettingsSection.DISPLAYS) }
    )
    SettingsSection.SCREENS -> SettingsSearchSource.NotSearchable(SettingsSection.SCREENS)
    SettingsSection.LAUNCH_TARGETS -> SettingsSearchSource.NotSearchable(SettingsSection.LAUNCH_TARGETS)
    SettingsSection.AMBIENT_LED -> SettingsSearchSource.Rows(
        section = SettingsSection.AMBIENT_LED,
        path = listOf(R.string.settings_search_crumb_displays, R.string.settings_search_crumb_ambient_led),
        layout = ambientLedLayout,
        stateOf = { it.display },
        keyOf = { it.key },
        reachable = { it.display.ambientLedAvailable },
        open = { vm ->
            vm.navigateToSection(SettingsSection.DISPLAYS)
            vm.setFocusIndex(
                displaysFocusIndexOf(DisplaysItem.AmbientLedSettings, DisplaysLayoutState.from(vm.uiState.value))
            )
            vm.navigateToAmbientLed()
        }
    )
    SettingsSection.NAVIGATION -> SettingsSearchSource.Rows(
        section = SettingsSection.NAVIGATION,
        path = listOf(R.string.settings_search_crumb_navigation),
        layout = navigationLayout,
        stateOf = { it.controls },
        keyOf = { it.key },
        open = { it.navigateToSection(SettingsSection.NAVIGATION) }
    )
    SettingsSection.PLATFORMS ->
        SettingsSearchSource.Provided(SettingsSection.PLATFORMS, SettingsSearchProvider.ENABLED_PLATFORMS)
    SettingsSection.BUILTIN_EMULATOR -> SettingsSearchSource.Rows(
        section = SettingsSection.BUILTIN_EMULATOR,
        path = listOf(R.string.settings_search_crumb_builtin_emulator),
        layout = builtinEmulatorLayout,
        stateOf = { it.emulators },
        keyOf = { it.key },
        open = { it.navigateToSection(SettingsSection.BUILTIN_EMULATOR) }
    )
    SettingsSection.BUILTIN_VIDEO -> SettingsSearchSource.Rows(
        section = SettingsSection.BUILTIN_VIDEO,
        path = listOf(R.string.settings_search_crumb_builtin_emulator, R.string.settings_search_crumb_builtin_video),
        layout = builtinVideoLayout,
        stateOf = { BuiltinVideoLayoutState.global(it.builtinVideo) },
        keyOf = { it.key },
        reachable = { it.emulators.builtinLibretroEnabled },
        open = { vm -> openBuiltinChild(vm, BuiltinEmulatorItem.VIDEO, SettingsSection.BUILTIN_VIDEO) }
    )
    SettingsSection.BUILTIN_CONTROLS -> SettingsSearchSource.Rows(
        section = SettingsSection.BUILTIN_CONTROLS,
        path = listOf(R.string.settings_search_crumb_builtin_emulator, R.string.settings_search_crumb_builtin_controls),
        layout = builtinControlsLayout,
        stateOf = { it.builtinControls.inPlatformContext(null, false).copy(controllerPorts = emptyList()) },
        keyOf = { it.key },
        reachable = { it.emulators.builtinLibretroEnabled },
        open = { vm -> openBuiltinChild(vm, BuiltinEmulatorItem.CONTROLS, SettingsSection.BUILTIN_CONTROLS) }
    )
    SettingsSection.BUILTIN_SAVES -> SettingsSearchSource.Rows(
        section = SettingsSection.BUILTIN_SAVES,
        path = listOf(R.string.settings_search_crumb_builtin_emulator, R.string.settings_search_crumb_builtin_saves),
        layout = builtinSavesLayout,
        stateOf = { BuiltinSavesLayoutState.global(it.builtinVideo) },
        keyOf = { it.key },
        reachable = { it.emulators.builtinLibretroEnabled },
        open = { vm -> openBuiltinChild(vm, BuiltinEmulatorItem.SAVES, SettingsSection.BUILTIN_SAVES) }
    )
    SettingsSection.SHADER_STACK -> SettingsSearchSource.NotSearchable(SettingsSection.SHADER_STACK)
    SettingsSection.FRAME_PICKER -> SettingsSearchSource.NotSearchable(SettingsSection.FRAME_PICKER)
    SettingsSection.CORE_MANAGEMENT -> SettingsSearchSource.NotSearchable(SettingsSection.CORE_MANAGEMENT)
    SettingsSection.CORE_OPTIONS ->
        SettingsSearchSource.Provided(SettingsSection.CORE_OPTIONS, SettingsSearchProvider.DOWNLOADED_CORE_OPTIONS)
    SettingsSection.PLATFORM_DETAIL ->
        SettingsSearchSource.Provided(SettingsSection.PLATFORM_DETAIL, SettingsSearchProvider.ENABLED_PLATFORM_ROWS)
    SettingsSection.EMULATOR_OPTIONS -> SettingsSearchSource.NotSearchable(SettingsSection.EMULATOR_OPTIONS)
    SettingsSection.SOCIAL -> SettingsSearchSource.Rows(
        section = SettingsSection.SOCIAL,
        path = listOf(R.string.settings_search_crumb_social),
        layout = socialLayout,
        stateOf = { SocialLayoutState.from(it) },
        keyOf = { it.key },
        open = { it.navigateToSection(SettingsSection.SOCIAL) }
    )
    SettingsSection.PERMISSIONS -> SettingsSearchSource.Rows(
        section = SettingsSection.PERMISSIONS,
        path = listOf(R.string.settings_search_crumb_permissions),
        layout = permissionsLayout,
        stateOf = { it.permissions },
        keyOf = { it.key },
        open = { it.navigateToSection(SettingsSection.PERMISSIONS) }
    )
    SettingsSection.DRIVERS -> SettingsSearchSource.NotSearchable(SettingsSection.DRIVERS)
    SettingsSection.MANAGED_INSTALLERS -> SettingsSearchSource.NotSearchable(SettingsSection.MANAGED_INSTALLERS)
    SettingsSection.ABOUT -> SettingsSearchSource.Rows(
        section = SettingsSection.ABOUT,
        path = listOf(R.string.settings_search_crumb_about),
        layout = aboutLayout,
        stateOf = { AboutLayoutState.from(it) },
        keyOf = { it.key },
        open = { it.navigateToSection(SettingsSection.ABOUT) }
    )
}

internal fun buildSettingsSearchIndex(ui: SettingsUiState): List<SettingsSearchEntry> =
    SettingsSection.entries.flatMap { section ->
        when (val source = settingsSearchSource(section)) {
            is SettingsSearchSource.Rows<*, *> -> source.entries(ui)
            is SettingsSearchSource.Provided -> providedEntries(source.provider, ui)
            is SettingsSearchSource.NotSearchable, is SettingsSearchSource.NotYetIndexed -> emptyList()
        }
    }

private fun providedEntries(provider: SettingsSearchProvider, ui: SettingsUiState): List<SettingsSearchEntry> =
    when (provider) {
        SettingsSearchProvider.ENABLED_PLATFORMS -> enabledPlatformEntries(ui)
        SettingsSearchProvider.ENABLED_PLATFORM_ROWS -> enabledPlatformRowEntries(ui)
        SettingsSearchProvider.DOWNLOADED_CORE_OPTIONS ->
            if (ui.emulators.builtinLibretroEnabled) coreOptionEntries(ui.settingsSearch.coreOptions) else emptyList()
    }

internal fun enabledPlatformEntries(ui: SettingsUiState): List<SettingsSearchEntry> {
    val crumbs = listOf(DisplayText.Res(R.string.settings_search_crumb_platforms))
    return ui.emulators.platforms
        .filter { it.platform.syncEnabled }
        .map { config ->
            SettingsSearchEntry(
                target = SettingsSearchTarget.Platform(config.platform.id),
                label = DisplayText.Raw(config.platform.name),
                keywords = DisplayText.Raw(config.platform.shortName),
                path = crumbs
            )
        }
}

internal fun enabledPlatformRowEntries(ui: SettingsUiState): List<SettingsSearchEntry> {
    val layout = createPlatformDetailLayout()
    return ui.emulators.platforms
        .filter { it.platform.syncEnabled }
        .flatMap { config ->
            val crumbs = listOf(
                DisplayText.Res(R.string.settings_search_crumb_platforms),
                DisplayText.Raw(config.platform.name)
            )
            layout.focusableItems(platformDetailVisibilityOf(ui, config, PlatformDetailState()))
                .mapNotNull { item ->
                    val row = item as? SearchableSetting ?: return@mapNotNull null
                    SettingsSearchEntry(
                        target = SettingsSearchTarget.Platform(config.platform.id, item.key),
                        label = DisplayText.Res(row.labelRes),
                        keywords = row.keywordsRes?.let { DisplayText.Res(it) },
                        path = crumbs
                    )
                }
        }
}

private fun openBuiltinChild(vm: SettingsViewModel, parentRow: BuiltinEmulatorItem, child: SettingsSection) {
    vm.navigateToSection(SettingsSection.BUILTIN_EMULATOR)
    val context = vm.uiState.value.builtinVideo.platformContextIndex
    if (context != 0) vm.cyclePlatformContext(-context)
    vm.setFocusIndex(parentRow.focusIndex)
    vm.navigateToSection(child)
}

internal fun coreOptionEntries(options: List<SettingsSearchCoreOption>): List<SettingsSearchEntry> =
    options.map { option ->
        SettingsSearchEntry(
            target = SettingsSearchTarget.CoreOption(option.coreId, option.optionKey),
            label = DisplayText.Raw(option.optionName),
            keywords = null,
            path = listOf(
                DisplayText.Res(R.string.settings_search_crumb_builtin_emulator),
                DisplayText.Res(R.string.settings_search_crumb_core_options),
                DisplayText.Raw(option.coreName)
            )
        )
    }
