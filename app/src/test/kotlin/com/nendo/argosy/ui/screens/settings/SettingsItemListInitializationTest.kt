package com.nendo.argosy.ui.screens.settings

import com.nendo.argosy.libretro.ui.InGameControlsItem
import com.nendo.argosy.ui.components.QuickSettingsItem
import com.nendo.argosy.ui.screens.gamedetail.components.MenuItem
import com.nendo.argosy.ui.screens.settings.sections.AboutItem
import com.nendo.argosy.ui.screens.settings.sections.AmbientLedItem
import com.nendo.argosy.ui.screens.settings.sections.AudioItem
import com.nendo.argosy.ui.screens.settings.sections.BoxArtItem
import com.nendo.argosy.ui.screens.settings.sections.BuiltinControlsItem
import com.nendo.argosy.ui.screens.settings.sections.DisplaysItem
import com.nendo.argosy.ui.screens.settings.sections.HomeScreenItem
import com.nendo.argosy.ui.screens.settings.sections.InterfaceItem
import com.nendo.argosy.ui.screens.settings.sections.LibraryItem
import com.nendo.argosy.ui.screens.settings.sections.MainSettingsItem
import com.nendo.argosy.ui.screens.settings.sections.NavigationItem
import com.nendo.argosy.ui.screens.settings.sections.PlatformDetailItem
import com.nendo.argosy.ui.screens.settings.sections.SavesItem
import com.nendo.argosy.ui.screens.settings.sections.SocialItem
import com.nendo.argosy.ui.screens.settings.sections.StorageCachesItem
import com.nendo.argosy.ui.screens.settings.sections.StorageItem
import com.nendo.argosy.ui.screens.settings.sections.ThemeBackdropItem
import com.nendo.argosy.ui.screens.settings.sections.ThemeFontsItem
import com.nendo.argosy.ui.screens.settings.sections.ThemeItem
import com.nendo.argosy.ui.screens.settings.sections.ThemeMusicItem
import com.nendo.argosy.ui.screens.settings.sections.ThemeSoundsItem
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the class-initialization trap behind every sealed settings item hierarchy: a stored
 * `ALL` list is a static of the sealed class, so it is built while that class initializes.
 * Touching one item object first makes the JVM begin that object's initialization, then its
 * superclass's, and the list captures the still-unassigned object as null - after which every
 * lookup through the list hands a null item to a non-null parameter. Each case here reproduces
 * that order deliberately: item literal first, list second. A getter-backed list is immune, a
 * stored one is not, so do not "simplify" the order away.
 */
class SettingsItemListInitializationTest {

    @Test
    fun `about item list survives an item-first class init`() {
        touchItemFirst(AboutItem.VersionInfo)
        assertUsableList("AboutItem", AboutItem.ALL)
    }

    @Test
    fun `ambient led item list survives an item-first class init`() {
        touchItemFirst(AmbientLedItem.Enable)
        assertUsableList("AmbientLedItem", AmbientLedItem.ALL)
    }

    @Test
    fun `audio item list survives an item-first class init`() {
        touchItemFirst(AudioItem.Sounds)
        assertUsableList("AudioItem", AudioItem.ALL)
    }

    @Test
    fun `box art item list survives an item-first class init`() {
        touchItemFirst(BoxArtItem.Shape)
        assertUsableList("BoxArtItem", BoxArtItem.ALL)
    }

    @Test
    fun `builtin controls item list survives an item-first class init`() {
        touchItemFirst(BuiltinControlsItem.ControllerOrder)
        assertUsableList("BuiltinControlsItem", BuiltinControlsItem.ALL)
    }

    @Test
    fun `displays item list survives an item-first class init`() {
        touchItemFirst(DisplaysItem.ScreenDimmer)
        assertUsableList("DisplaysItem", DisplaysItem.ALL)
    }

    @Test
    fun `home screen item list survives an item-first class init`() {
        touchItemFirst(HomeScreenItem.LayoutPreview)
        assertUsableList("HomeScreenItem", HomeScreenItem.ALL)
    }

    @Test
    fun `in game controls item list survives an item-first class init`() {
        touchItemFirst(InGameControlsItem.GameSpecificControls)
        assertUsableList("InGameControlsItem", InGameControlsItem.ALL)
    }

    @Test
    fun `interface item list survives an item-first class init`() {
        touchItemFirst(InterfaceItem.UiScale)
        assertUsableList("InterfaceItem", InterfaceItem.ALL)
    }

    @Test
    fun `library item list survives an item-first class init`() {
        touchItemFirst(LibraryItem.GridDensityItem)
        assertUsableList("LibraryItem", LibraryItem.ALL)
    }

    @Test
    fun `main settings item list survives an item-first class init`() {
        touchItemFirst(MainSettingsItem.Theme)
        assertUsableList("MainSettingsItem", MainSettingsItem.ALL)
    }

    @Test
    fun `game detail menu item list survives an item-first class init`() {
        touchItemFirst(MenuItem.Play)
        assertUsableList("MenuItem", MenuItem.ALL)
    }

    @Test
    fun `navigation item list survives an item-first class init`() {
        touchItemFirst(NavigationItem.ControllerLayout)
        assertUsableList("NavigationItem", NavigationItem.ALL)
    }

    @Test
    fun `platform detail item list survives an item-first class init`() {
        touchItemFirst(PlatformDetailItem.Emulator)
        assertUsableList("PlatformDetailItem", PlatformDetailItem.ALL)
    }

    @Test
    fun `quick settings item list survives an item-first class init`() {
        touchItemFirst(QuickSettingsItem.Performance)
        assertUsableList("QuickSettingsItem", QuickSettingsItem.ALL)
    }

    @Test
    fun `saves item list survives an item-first class init`() {
        touchItemFirst(SavesItem.SaveSync)
        assertUsableList("SavesItem", SavesItem.ALL)
    }

    @Test
    fun `social item list survives an item-first class init`() {
        touchItemFirst(SocialItem.AccountInfo)
        assertUsableList("SocialItem", SocialItem.ALL)
    }

    @Test
    fun `storage item list survives an item-first class init`() {
        touchItemFirst(StorageItem.VolumeHero)
        assertUsableList("StorageItem", StorageItem.ALL)
    }

    @Test
    fun `storage caches item list survives an item-first class init`() {
        touchItemFirst(StorageCachesItem.PendingUploads)
        assertUsableList("StorageCachesItem", StorageCachesItem.ALL)
    }

    @Test
    fun `theme item list survives an item-first class init`() {
        touchItemFirst(ThemeItem.Mode)
        assertUsableList("ThemeItem", ThemeItem.ALL)
    }

    @Test
    fun `theme backdrop item list survives an item-first class init`() {
        touchItemFirst(ThemeBackdropItem.Enabled)
        assertUsableList("ThemeBackdropItem", ThemeBackdropItem.ALL)
    }

    @Test
    fun `theme fonts item list survives an item-first class init`() {
        touchItemFirst(ThemeFontsItem.DisplaySlot)
        assertUsableList("ThemeFontsItem", ThemeFontsItem.ALL)
    }

    @Test
    fun `theme music item list survives an item-first class init`() {
        touchItemFirst(ThemeMusicItem.BgmToggle)
        assertUsableList("ThemeMusicItem", ThemeMusicItem.ALL)
    }

    @Test
    fun `theme sounds item list survives an item-first class init`() {
        touchItemFirst(ThemeSoundsItem.UiSoundsToggle)
        assertUsableList("ThemeSoundsItem", ThemeSoundsItem.ALL)
    }

    private fun touchItemFirst(item: Any) {
        assertNotNull("item object read before its list is null", item)
    }

    private fun assertUsableList(name: String, items: List<Any?>) {
        assertTrue("$name.ALL is empty", items.isNotEmpty())
        items.forEachIndexed { index, item ->
            assertNotNull("$name.ALL[$index] is null after an item-first class init", item)
        }
    }
}
