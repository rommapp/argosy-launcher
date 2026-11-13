package com.nendo.argosy.ui.screens.setup

import com.nendo.argosy.R
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.domain.model.ArgosyPermission
import com.nendo.argosy.domain.model.PermissionAvailability
import com.nendo.argosy.domain.model.PermissionSnapshot
import com.nendo.argosy.domain.model.ScreenRole
import com.nendo.argosy.domain.model.SetupFacts
import com.nendo.argosy.ui.screens.settings.ScreenAssignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupFocusTest {

    private fun snapshot(vararg pairs: Pair<ArgosyPermission, PermissionAvailability>) =
        PermissionSnapshot(availability = ArgosyPermission.entries.associateWith { PermissionAvailability.MISSING } + pairs)

    private fun permissionsPage(access: SetupAccessState) =
        SetupUiState(page = SetupPage.PERMISSIONS, access = access)

    private val primary = SetupTarget.Action(SetupActionId.PRIMARY)

    @Test
    fun `the permissions primary asks for the first missing required permission`() {
        val state = permissionsPage(
            SetupAccessState(snapshot = snapshot(ArgosyPermission.ALL_FILES_ACCESS to PermissionAvailability.GRANTED))
        )
        assertEquals(ArgosyPermission.USAGE_ACCESS, firstUnmetRequired(state.access))
        assertEquals(R.string.setup_permissions_primary_allow_usage, actionsFor(state).single().label)
        assertEquals(SetupTarget.Permission(ArgosyPermission.USAGE_ACCESS), focusedTarget(state))
    }

    @Test
    fun `usage access refused on a restricted device puts allow restricted settings first`() {
        val restrictedStep = SetupTarget.AccessibilityStep(AccessibilityStepKind.RESTRICTED_SETTINGS)
        val refused = permissionsPage(
            SetupAccessState(
                snapshot = snapshot(ArgosyPermission.ALL_FILES_ACCESS to PermissionAvailability.GRANTED),
                restrictedSettings = true,
                usageBlocked = true
            )
        )

        assertEquals(restrictedStep, targetsFor(refused).first())
        assertEquals(restrictedStep, focusedTarget(refused))
        assertEquals(R.string.setup_accessibility_step_restricted_title, actionsFor(refused).single().label)

        val allowed = refused.copy(access = refused.access.copy(restrictedStepDone = true))
        assertEquals(restrictedStep, targetsFor(allowed).first())
        assertEquals(SetupTarget.Permission(ArgosyPermission.USAGE_ACCESS), focusedTarget(allowed))
        assertEquals(R.string.setup_permissions_primary_allow_usage, actionsFor(allowed).single().label)

        val granted = allowed.copy(
            access = allowed.access.copy(
                snapshot = snapshot(
                    ArgosyPermission.ALL_FILES_ACCESS to PermissionAvailability.GRANTED,
                    ArgosyPermission.USAGE_ACCESS to PermissionAvailability.GRANTED
                )
            )
        )
        assertFalse(restrictedStep in targetsFor(granted))
    }

    @Test
    fun `usage access not yet tried shows no restricted step`() {
        val state = permissionsPage(SetupAccessState(snapshot = snapshot(), restrictedSettings = true))

        assertFalse(targetsFor(state).any { it is SetupTarget.AccessibilityStep })
    }

    @Test
    fun `the permissions primary reads next once required permissions are met, recommended ones never block`() {
        val state = permissionsPage(
            SetupAccessState(
                snapshot = snapshot(
                    ArgosyPermission.ALL_FILES_ACCESS to PermissionAvailability.GRANTED,
                    ArgosyPermission.USAGE_ACCESS to PermissionAvailability.UNAVAILABLE,
                    ArgosyPermission.NOTIFICATIONS to PermissionAvailability.GRANTED
                )
            )
        )
        assertNull(firstUnmetRequired(state.access))
        assertEquals(R.string.setup_permissions_primary_next, actionsFor(state).single().label)
        assertEquals(primary, focusedTarget(state))
    }

    @Test
    fun `a permission the root route is granting stays unmet until it lands`() {
        val access = SetupAccessState(
            snapshot = snapshot(
                ArgosyPermission.ALL_FILES_ACCESS to PermissionAvailability.GRANTED,
                ArgosyPermission.USAGE_ACCESS to PermissionAvailability.GRANTED,
                ArgosyPermission.NOTIFICATIONS to PermissionAvailability.GRANTED
            ),
            rootGranting = setOf(ArgosyPermission.NOTIFICATIONS)
        )
        assertEquals(PermissionAvailability.CHECKING, access.availabilityOf(ArgosyPermission.NOTIFICATIONS))
        assertEquals(ArgosyPermission.NOTIFICATIONS, firstUnmetRequired(access))
    }

    @Test
    fun `accessibility variants follow the access level`() {
        val rooted = SetupUiState(page = SetupPage.ACCESSIBILITY, access = SetupAccessState(rootRoute = true))
        assertEquals(AccessibilityVariant.ROOTED, rooted.access.variant)
        assertTrue(contentTargetsFor(rooted).isEmpty())

        val restricted = SetupUiState(page = SetupPage.ACCESSIBILITY, access = SetupAccessState(restrictedSettings = true))
        assertEquals(AccessibilityVariant.TWO_STEP, restricted.access.variant)
        assertEquals(
            listOf(
                SetupTarget.AccessibilityStep(AccessibilityStepKind.RESTRICTED_SETTINGS),
                SetupTarget.AccessibilityStep(AccessibilityStepKind.SERVICE)
            ),
            contentTargetsFor(restricted)
        )

        val direct = SetupUiState(page = SetupPage.ACCESSIBILITY)
        assertEquals(AccessibilityVariant.ONE_STEP, direct.access.variant)
        assertEquals(listOf(SetupTarget.AccessibilityStep(AccessibilityStepKind.SERVICE)), contentTargetsFor(direct))
    }

    @Test
    fun `a root route that fails to turn the service on falls back to the steps`() {
        val access = SetupAccessState(rootRoute = true, restrictedSettings = true, rootAccessibilityFailed = true)
        assertEquals(AccessibilityVariant.TWO_STEP, access.variant)
    }

    @Test
    fun `accessibility reads skip while off and next once on, focusing the first open step`() {
        val off = SetupUiState(page = SetupPage.ACCESSIBILITY, access = SetupAccessState(restrictedSettings = true))
        assertEquals(R.string.setup_accessibility_button_skip, actionsFor(off).single().label)
        assertEquals(SetupTarget.AccessibilityStep(AccessibilityStepKind.RESTRICTED_SETTINGS), focusedTarget(off))

        val stepOneDone = off.copy(access = off.access.copy(restrictedStepDone = true))
        assertEquals(SetupTarget.AccessibilityStep(AccessibilityStepKind.SERVICE), focusedTarget(stepOneDone))

        val on = off.copy(access = off.access.copy(snapshot = snapshot(ArgosyPermission.ACCESSIBILITY to PermissionAvailability.GRANTED)))
        assertEquals(R.string.setup_accessibility_button_next, actionsFor(on).single().label)
        assertEquals(primary, focusedTarget(on))
    }

    @Test
    fun `the approve view offers the browser only when one resolves`() {
        val approve = SetupUiState(page = SetupPage.LIBRARY, signIn = RommSignInState(view = LibraryView.APPROVE))
        val withoutBrowser = actionsFor(approve)
        assertEquals(listOf(SetupActionId.USE_PAIRING_CODE), withoutBrowser.map { it.id })
        assertTrue(withoutBrowser.single().primary)

        val withBrowser = actionsFor(approve.copy(signIn = approve.signIn.copy(browserAvailable = true)))
        assertEquals(listOf(SetupActionId.USE_PAIRING_CODE, SetupActionId.PRIMARY), withBrowser.map { it.id })
    }

    @Test
    fun `an untrusted certificate moves focus to the import action`() {
        val address = SetupUiState(
            page = SetupPage.LIBRARY,
            signIn = RommSignInState(view = LibraryView.ADDRESS, error = SetupError(certificate = true))
        )
        assertEquals(SetupTarget.Action(SetupActionId.IMPORT_CERTIFICATE), focusedTarget(address))
    }

    @Test
    fun `the local library row is never a stop`() {
        val choice = SetupUiState(page = SetupPage.LIBRARY)
        assertEquals(listOf(SetupTarget.RommSource), contentTargetsFor(choice))
    }

    @Test
    fun `down from the last row lands on the primary and up returns to the rows`() {
        val state = SetupUiState(page = SetupPage.SAVE_SYNC, focused = SetupTarget.SaveSyncChoice(false))
        assertEquals(primary, moveFocus(state, SetupMove.DOWN))
        val onPrimary = state.copy(focused = primary)
        assertEquals(SetupTarget.SaveSyncChoice(false), moveFocus(onPrimary, SetupMove.UP))
        assertNull(moveFocus(onPrimary, SetupMove.DOWN))
        assertNull(moveFocus(state.copy(focused = SetupTarget.SaveSyncChoice(true)), SetupMove.UP))
    }

    @Test
    fun `left and right walk the action row only`() {
        val signedIn = SetupUiState(
            page = SetupPage.LIBRARY,
            signIn = RommSignInState(view = LibraryView.SIGNED_IN)
        )
        assertEquals(primary, focusedTarget(signedIn))
        assertEquals(SetupTarget.Action(SetupActionId.DIFFERENT_SERVER), moveFocus(signedIn, SetupMove.LEFT))
        assertNull(moveFocus(signedIn, SetupMove.RIGHT))
        val onRow = SetupUiState(page = SetupPage.SAVE_SYNC)
        assertNull(moveFocus(onRow, SetupMove.LEFT))
    }

    @Test
    fun `done asks for the home role only on a single screen that does not hold it`() {
        val single = SetupUiState(page = SetupPage.DONE)
        assertEquals(listOf(SetupActionId.NOT_NOW, SetupActionId.PRIMARY), actionsFor(single).map { it.id })
        assertEquals(R.string.setup_done_button_set_home, actionsFor(single).last().label)

        val dual = single.copy(facts = SetupFacts(dualScreenDevice = true))
        assertEquals(R.string.setup_done_button_go_home, actionsFor(dual).single().label)

        val home = single.copy(facts = SetupFacts(defaultHome = true))
        assertEquals(R.string.setup_done_button_go_home, actionsFor(home).single().label)
    }

    private fun platform(id: Long) = PlatformEntity(
        id = id,
        slug = "p$id",
        name = "p$id",
        shortName = "p$id",
        romExtensions = "bin",
        syncEnabled = true
    )

    @Test
    fun `select all is the first row under the header, above every platform`() {
        val shown = listOf(platform(1), platform(2))
        val state = SetupUiState(
            page = SetupPage.PLATFORMS,
            platforms = SetupPlatformsState(all = shown, shown = shown, missingCoreIds = listOf("core"))
        )
        assertEquals(
            listOf(
                SetupTarget.PlatformHeader,
                SetupTarget.SelectAllPlatforms,
                SetupTarget.Platform(1),
                SetupTarget.Platform(2),
                SetupTarget.CoreDownloads
            ),
            contentTargetsFor(state)
        )
        assertEquals(SetupTarget.SelectAllPlatforms, moveFocus(state.copy(focused = SetupTarget.Platform(1)), SetupMove.UP))
        assertEquals(primary, primaryTarget(state))
    }

    @Test
    fun `a removed card drops out of the folder stops`() {
        val folders = folderChoicesFrom("/internal/ROMs", "/sd/ROMs", null, null).withSdCard(null)
        val state = SetupUiState(page = SetupPage.FOLDERS, folders = folders)
        assertTrue(SetupTarget.RomFolder(RomFolderKind.SD_CARD) !in contentTargetsFor(state))
        assertTrue(SetupTarget.RomFolder(RomFolderKind.CUSTOM) in contentTargetsFor(state))
    }

    private val folderConfirmed = SetupFacts(romFolderPresent = true, romFolderConfirmed = true)

    private val customFolders = folderChoicesFrom("/internal/ROMs", null, "/custom/ROMs", null)

    @Test
    fun `a confirmed game folder opens on next, not on the selected folder row`() {
        val state = SetupUiState(page = SetupPage.FOLDERS, facts = folderConfirmed, folders = customFolders)
        assertEquals(RomFolderKind.CUSTOM, state.folders.selectedRom)
        assertTrue(pageSatisfied(state))
        assertEquals(primary, focusedTarget(state))
    }

    @Test
    fun `an unconfirmed game folder keeps focus on the selected folder row`() {
        val state = SetupUiState(page = SetupPage.FOLDERS, folders = customFolders)
        assertFalse(pageSatisfied(state))
        assertEquals(SetupTarget.RomFolder(RomFolderKind.CUSTOM), focusedTarget(state))
    }

    @Test
    fun `a confirmed game folder that cannot be written returns focus to its row`() {
        val state = SetupUiState(
            page = SetupPage.FOLDERS,
            facts = folderConfirmed,
            folders = customFolders.copy(unwritable = RomFolderKind.CUSTOM)
        )
        assertFalse(pageSatisfied(state))
        assertEquals(SetupTarget.RomFolder(RomFolderKind.CUSTOM), focusedTarget(state))
    }

    @Test
    fun `a stored focus on a satisfied page wins over the forward action`() {
        val state = SetupUiState(
            page = SetupPage.FOLDERS,
            facts = folderConfirmed,
            folders = customFolders,
            focused = SetupTarget.RomFolder(RomFolderKind.INTERNAL)
        )
        assertEquals(SetupTarget.RomFolder(RomFolderKind.INTERNAL), focusedTarget(state))
    }

    @Test
    fun `a platforms page already moved past opens on next, a first visit on the first platform`() {
        val shown = listOf(platform(1), platform(2))
        val unseen = SetupUiState(
            page = SetupPage.PLATFORMS,
            platforms = SetupPlatformsState(all = shown, shown = shown)
        )
        assertFalse(pageSatisfied(unseen))
        assertEquals(SetupTarget.Platform(1), focusedTarget(unseen))

        val seen = unseen.copy(facts = SetupFacts(pagesSeen = setOf("platforms")))
        assertTrue(pageSatisfied(seen))
        assertEquals(primary, focusedTarget(seen))
    }

    @Test
    fun `an answered save sync opens on next, an unanswered one on the current choice`() {
        val unanswered = SetupUiState(page = SetupPage.SAVE_SYNC, saveSyncOn = false)
        assertEquals(SetupTarget.SaveSyncChoice(false), focusedTarget(unanswered))

        val answered = unanswered.copy(facts = SetupFacts(saveSyncAnswered = true))
        assertEquals(primary, focusedTarget(answered))
    }

    @Test
    fun `every optional page with choices opens on next once seen and on its choices before`() {
        val pads = SetupUiState(gamepadConnected = true)
        val firstVisit = mapOf(
            SetupPage.CONTROLLER to SetupTarget.ControllerLayoutChoice(false),
            SetupPage.THEME to SetupTarget.ThemeMode,
            SetupPage.HOME_LAYOUT to SetupTarget.HomeLayout,
            SetupPage.LIBRARY_LOOK to SetupTarget.LibraryLayout
        )
        firstVisit.forEach { (page, choice) ->
            val unseen = pads.copy(page = page)
            assertEquals(choice, focusedTarget(unseen))
            val seen = unseen.copy(facts = SetupFacts(pagesSeen = setOfNotNull(page.seenToken)))
            assertEquals(primary, focusedTarget(seen))
        }
    }

    @Test
    fun `a library still choosing its source keeps focus on the source row`() {
        val choice = SetupUiState(page = SetupPage.LIBRARY, facts = SetupFacts(librarySignedIn = true, librarySourceConfirmed = true))
        assertFalse(pageSatisfied(choice))
        assertEquals(SetupTarget.RommSource, focusedTarget(choice))
    }

    private fun builtInScreen(key: String, number: Int, role: ScreenRole) =
        ScreenAssignment(key, displayId = number, number = number, widthPx = 1920, heightPx = 1080, builtIn = true, role = role)

    @Test
    fun `two built-in screens offer the swap row, three keep the screen sketch`() {
        val two = SetupUiState(
            page = SetupPage.SCREENS,
            screens = listOf(builtInScreen("a", 1, ScreenRole.PRESENTATION), builtInScreen("b", 2, ScreenRole.PRIMARY))
        )
        assertEquals(listOf(SetupTarget.SwapScreens), contentTargetsFor(two))
        assertEquals(SetupTarget.SwapScreens, focusedTarget(two))

        val three = two.copy(screens = two.screens + builtInScreen("c", 3, ScreenRole.OFF))
        assertEquals(listOf("a", "b", "c").map { SetupTarget.Screen(it) }, contentTargetsFor(three))
        assertEquals(SetupTarget.Screen("b"), focusedTarget(three))
    }
}
