package com.nendo.argosy.ui.screens.setup

import com.nendo.argosy.R
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.domain.model.CoreDownloadProgress
import com.nendo.argosy.domain.model.FolderSpace
import com.nendo.argosy.domain.model.ScreenRole
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.input.ButtonGlyphSwaps
import com.nendo.argosy.ui.screens.settings.ScreenAssignment
import com.nendo.argosy.ui.screens.setup.components.ButtonCheckState
import com.nendo.argosy.ui.screens.setup.components.PadReading
import com.nendo.argosy.ui.screens.setup.components.SetupCompanionBody
import com.nendo.argosy.ui.screens.setup.components.SetupScreenBlock
import org.junit.Assert.assertEquals
import org.junit.Test

class SetupCompanionBodyTest {

    private val swaps = ButtonGlyphSwaps(ab = false, xy = false, startSelect = false)

    private fun bodyOf(state: SetupUiState): SetupCompanionBody = companionFor(state, swaps).body

    private fun platform(id: Long, enabled: Boolean) = PlatformEntity(
        id = id,
        slug = "p$id",
        name = "p$id",
        shortName = "p$id",
        romExtensions = "bin",
        syncEnabled = enabled
    )

    @Test
    fun `welcome and the home page keep the brand line`() {
        assertEquals(SetupCompanionBody.Brand, bodyOf(SetupUiState(page = SetupPage.WELCOME)))
        assertEquals(SetupCompanionBody.Brand, bodyOf(SetupUiState(page = SetupPage.DEFAULT_HOME)))
    }

    @Test
    fun `the library choice carries its context and the other library views keep the brand line`() {
        val choice = SetupUiState(page = SetupPage.LIBRARY, signIn = RommSignInState(view = LibraryView.CHOICE))
        assertEquals(SetupCompanionBody.LibraryContext, bodyOf(choice))
        val address = choice.copy(signIn = RommSignInState(view = LibraryView.ADDRESS))
        assertEquals(SetupCompanionBody.Brand, bodyOf(address))
    }

    @Test
    fun `accessibility shows its purpose and the live step state`() {
        val access = SetupAccessState(restrictedSettings = true, restrictedStepDone = true)
        val body = bodyOf(SetupUiState(page = SetupPage.ACCESSIBILITY, access = access))
        assertEquals(SetupCompanionBody.Accessibility(R.string.setup_accessibility_purpose, access), body)
    }

    @Test
    fun `platforms summarise the selection against every platform`() {
        val platforms = SetupPlatformsState(all = listOf(platform(1, true), platform(2, false), platform(3, true)))
        val body = bodyOf(SetupUiState(page = SetupPage.PLATFORMS, platforms = platforms))
        assertEquals(SetupCompanionBody.Platforms(selected = 2, total = 3), body)
    }

    @Test
    fun `done carries the same summary values as the page`() {
        val cores = CoreDownloadProgress()
        val state = SetupUiState(
            page = SetupPage.DONE,
            signIn = RommSignInState(url = "https://romm.example/", gameCount = 12),
            folders = FolderChoices(internalPath = "/roms", loaded = true),
            saveSyncOn = false,
            cores = cores
        )
        assertEquals(
            SetupCompanionBody.Done(
                serverName = "romm.example",
                gameCount = 12,
                romPath = "/roms",
                saveSyncOn = false,
                cores = cores
            ),
            bodyOf(state)
        )
    }

    @Test
    fun `a folder Argosy still has to create says so and an unread folder does not`() {
        val missing = FolderChoices(
            internalPath = "/roms",
            space = mapOf("/roms" to FolderSpace(freeBytes = 1, totalBytes = 2, exists = false)),
            loaded = true
        )
        assertEquals(
            SetupCompanionBody.Folder("/roms", 1, 2, exists = false),
            bodyOf(SetupUiState(page = SetupPage.FOLDERS, folders = missing))
        )
        val unread = missing.copy(space = emptyMap())
        assertEquals(
            SetupCompanionBody.Folder("/roms", null, null, exists = true),
            bodyOf(SetupUiState(page = SetupPage.FOLDERS, folders = unread))
        )
    }

    @Test
    fun `the companion title is the page title`() {
        val state = SetupUiState(page = SetupPage.SAVE_SYNC)
        assertEquals(leadFor(state).title, companionFor(state, swaps).title)
    }

    private fun screen(key: String, number: Int, role: ScreenRole) = ScreenAssignment(
        key = key,
        displayId = number,
        number = number,
        widthPx = 1920,
        heightPx = 1080,
        builtIn = true,
        role = role
    )

    @Test
    fun `two screens show one block per screen in physical order under their own heading`() {
        val state = SetupUiState(
            page = SetupPage.SCREENS,
            screens = listOf(screen("lower", 2, ScreenRole.PRIMARY), screen("upper", 1, ScreenRole.PRESENTATION))
        )
        val companion = companionFor(state, swaps)
        assertEquals(R.string.setup_screens_companion_title, companion.title)
        assertEquals(
            SetupCompanionBody.Screens(
                listOf(SetupScreenBlock(1, ScreenRole.PRESENTATION), SetupScreenBlock(2, ScreenRole.PRIMARY))
            ),
            companion.body
        )
    }

    @Test
    fun `three screens keep the page title`() {
        val state = SetupUiState(
            page = SetupPage.SCREENS,
            screens = listOf(
                screen("a", 1, ScreenRole.PRIMARY),
                screen("b", 2, ScreenRole.PRESENTATION),
                screen("c", 3, ScreenRole.OFF)
            )
        )
        assertEquals(leadFor(state).title, companionFor(state, swaps).title)
    }

    @Test
    fun `the button check companion carries the live reading and swaps`() {
        val reading = PadReading(held = setOf(InputButton.LB), lastPress = InputButton.LB)
        val check = ButtonCheckState(testing = true, reading = reading, swapAB = true)
        val state = SetupUiState(page = SetupPage.BUTTON_CHECK, buttonCheck = check, controllerNintendo = true)
        assertEquals(
            SetupCompanionBody.Controller(
                reading = reading,
                swaps = swaps,
                swapped = setOf(InputButton.A, InputButton.B),
                showsLastPress = true,
                testing = true
            ),
            bodyOf(state)
        )
    }
}
