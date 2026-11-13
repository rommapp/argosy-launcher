package com.nendo.argosy.ui.screens.setup

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.ArgosyPermission
import com.nendo.argosy.domain.model.PermissionAvailability
import com.nendo.argosy.domain.model.ScreenRole
import com.nendo.argosy.ui.screens.settings.ScreenAssignment
import com.nendo.argosy.ui.screens.setup.components.SetupStepLead

sealed interface SetupTarget {
    data object RommSource : SetupTarget
    data object ServerField : SetupTarget
    data object PairingField : SetupTarget
    data class Permission(val permission: ArgosyPermission) : SetupTarget
    data class AccessibilityStep(val step: AccessibilityStepKind) : SetupTarget
    data class RomFolder(val kind: RomFolderKind) : SetupTarget
    data class CoverStorage(val kind: CoverStorageKind) : SetupTarget
    data class SaveSyncChoice(val enabled: Boolean) : SetupTarget
    data object PlatformHeader : SetupTarget
    data class Platform(val id: Long) : SetupTarget
    data object CoreDownloads : SetupTarget
    data object SelectAllPlatforms : SetupTarget
    data class Screen(val key: String) : SetupTarget
    data object SwapScreens : SetupTarget
    data class ControllerLayoutChoice(val nintendo: Boolean) : SetupTarget
    data object ThemeMode : SetupTarget
    data object Accent : SetupTarget
    data object HomeLayout : SetupTarget
    data object LibraryLayout : SetupTarget
    data object BoxArtShape : SetupTarget
    data class Action(val id: SetupActionId) : SetupTarget
}

enum class SetupActionId {
    PRIMARY,
    IMPORT_CERTIFICATE,
    IMPORT_CLIENT_CERTIFICATE,
    USE_PAIRING_CODE,
    EDIT_ADDRESS,
    APPROVE_ON_DEVICE,
    DIFFERENT_SERVER,
    NOT_NOW,
    DECLINE_HOME,
    CHECK_AGAIN,
    SKIP_CUSTOMIZATION
}

data class SetupActionSpec(
    val id: SetupActionId,
    @StringRes val label: Int,
    val primary: Boolean = false,
    val hiddenWhenDocked: Boolean = false,
    val labelCount: Int? = null,
    val labelArg: String? = null
)

enum class SetupMove(val dx: Int, val dy: Int) {
    UP(0, -1),
    DOWN(0, 1),
    LEFT(-1, 0),
    RIGHT(1, 0)
}

val SETUP_REQUIRED_PERMISSIONS: List<ArgosyPermission> = listOf(
    ArgosyPermission.ALL_FILES_ACCESS,
    ArgosyPermission.USAGE_ACCESS,
    ArgosyPermission.NOTIFICATIONS
)

val SETUP_RECOMMENDED_PERMISSIONS: List<ArgosyPermission> = listOf(
    ArgosyPermission.DISPLAY_OVERLAY,
    ArgosyPermission.BATTERY_UNRESTRICTED
)

/**
 * The first required permission still blocking the page. A permission the device has no screen
 * for counts as met, as it does for routing.
 */
fun firstUnmetRequired(access: SetupAccessState): ArgosyPermission? =
    SETUP_REQUIRED_PERMISSIONS.firstOrNull {
        when (access.availabilityOf(it)) {
            PermissionAvailability.GRANTED, PermissionAvailability.UNAVAILABLE -> false
            PermissionAvailability.MISSING, PermissionAvailability.CHECKING -> true
        }
    }

@StringRes
private fun allowLabelFor(permission: ArgosyPermission?): Int = when (permission) {
    ArgosyPermission.ALL_FILES_ACCESS -> R.string.setup_permissions_primary_allow_all_files
    ArgosyPermission.USAGE_ACCESS -> R.string.setup_permissions_primary_allow_usage
    ArgosyPermission.NOTIFICATIONS -> R.string.setup_permissions_primary_allow_notifications
    else -> R.string.setup_permissions_primary_next
}

fun actionsFor(state: SetupUiState): List<SetupActionSpec> = when (state.page) {
    SetupPage.WELCOME -> listOf(primary(R.string.setup_welcome_button_start))
    SetupPage.LIBRARY -> libraryActions(state)
    SetupPage.PERMISSIONS -> listOf(primary(allowLabelFor(firstUnmetRequired(state.access))))
    SetupPage.ACCESSIBILITY -> listOf(
        primary(if (state.access.accessibilityOn) R.string.setup_accessibility_button_next else R.string.setup_accessibility_button_skip)
    )
    SetupPage.FOLDERS -> listOf(primary(R.string.setup_folders_button_next))
    SetupPage.SAVE_SYNC -> listOf(primary(R.string.setup_save_sync_button_next))
    SetupPage.PLATFORMS -> listOf(primary(R.string.setup_platforms_button_next))
    SetupPage.DEFAULT_HOME -> if (state.facts.defaultHome) {
        listOf(primary(R.string.setup_home_button_next))
    } else {
        listOf(
            SetupActionSpec(SetupActionId.DECLINE_HOME, R.string.setup_home_button_decline),
            primary(R.string.setup_home_button_set)
        )
    }
    SetupPage.SCREENS -> listOf(primary(R.string.setup_screens_button_next))
    SetupPage.CONTROLLER -> if (state.gamepadConnected) {
        listOf(primary(R.string.setup_controller_button_next))
    } else {
        listOf(
            SetupActionSpec(SetupActionId.CHECK_AGAIN, R.string.setup_controller_button_check_again),
            primary(R.string.setup_controller_button_skip)
        )
    }
    SetupPage.BUTTON_CHECK -> emptyList()
    SetupPage.THEME, SetupPage.HOME_LAYOUT, SetupPage.LIBRARY_LOOK -> listOf(
        SetupActionSpec(SetupActionId.SKIP_CUSTOMIZATION, R.string.setup_look_button_skip),
        primary(R.string.setup_look_button_next)
    )
    SetupPage.DONE -> if (offersHomeRole(state)) {
        listOf(
            SetupActionSpec(SetupActionId.NOT_NOW, R.string.setup_done_button_not_now),
            primary(R.string.setup_done_button_set_home)
        )
    } else {
        listOf(primary(R.string.setup_done_button_go_home))
    }
}

fun offersHomeRole(state: SetupUiState): Boolean = !state.facts.dualScreenDevice && !state.facts.defaultHome

private fun libraryActions(state: SetupUiState): List<SetupActionSpec> {
    val signIn = state.signIn
    return when (signIn.view) {
        LibraryView.CHOICE -> listOf(primary(R.string.setup_library_button_next))
        LibraryView.ADDRESS -> listOf(
            if (signIn.importedCertCount > 0) {
                SetupActionSpec(
                    SetupActionId.IMPORT_CERTIFICATE,
                    R.string.firstrun_romm_certificate_button_count,
                    labelCount = signIn.importedCertCount
                )
            } else {
                SetupActionSpec(SetupActionId.IMPORT_CERTIFICATE, R.string.firstrun_romm_certificate_button)
            },
            signIn.clientCert.summary?.let { summary ->
                SetupActionSpec(
                    SetupActionId.IMPORT_CLIENT_CERTIFICATE,
                    R.string.firstrun_romm_client_certificate_button_active,
                    labelArg = summary.displayName
                )
            } ?: SetupActionSpec(SetupActionId.IMPORT_CLIENT_CERTIFICATE, R.string.firstrun_romm_client_certificate_button),
            primary(
                if (signIn.isConnecting) R.string.firstrun_romm_url_button_checking else R.string.setup_library_address_button_continue,
                hiddenWhenDocked = true
            )
        )
        LibraryView.APPROVE -> if (signIn.browserAvailable) {
            listOf(
                SetupActionSpec(SetupActionId.USE_PAIRING_CODE, R.string.firstrun_device_pairing_button_manual_code),
                primary(R.string.setup_library_approve_button_open)
            )
        } else {
            listOf(
                SetupActionSpec(
                    SetupActionId.USE_PAIRING_CODE,
                    R.string.firstrun_device_pairing_button_manual_code,
                    primary = true
                )
            )
        }
        LibraryView.PAIRING -> buildList {
            add(SetupActionSpec(SetupActionId.EDIT_ADDRESS, R.string.setup_library_pairing_button_edit_address))
            if (signIn.supportsDeviceAuth) {
                add(SetupActionSpec(SetupActionId.APPROVE_ON_DEVICE, R.string.setup_library_pairing_button_approve))
            }
            add(
                primary(
                    if (signIn.isConnecting) {
                        R.string.firstrun_pairing_code_button_connecting
                    } else {
                        R.string.firstrun_pairing_code_button_connect
                    },
                    hiddenWhenDocked = true
                )
            )
        }
        LibraryView.SIGNED_IN -> listOf(
            SetupActionSpec(SetupActionId.DIFFERENT_SERVER, R.string.setup_library_signed_in_button_different),
            primary(R.string.setup_library_button_signed_in_next)
        )
    }
}

private fun primary(@StringRes label: Int, hiddenWhenDocked: Boolean = false) =
    SetupActionSpec(SetupActionId.PRIMARY, label, primary = true, hiddenWhenDocked = hiddenWhenDocked)

fun contentTargetsFor(state: SetupUiState): List<SetupTarget> = when (state.page) {
    SetupPage.WELCOME, SetupPage.DONE, SetupPage.DEFAULT_HOME, SetupPage.BUTTON_CHECK -> emptyList()
    SetupPage.SCREENS -> if (screensUseSwapRow(state)) {
        listOf(SetupTarget.SwapScreens)
    } else {
        setupScreens(state).map { SetupTarget.Screen(it.key) }
    }
    SetupPage.CONTROLLER -> if (state.gamepadConnected) {
        listOf(SetupTarget.ControllerLayoutChoice(false), SetupTarget.ControllerLayoutChoice(true))
    } else {
        emptyList()
    }
    SetupPage.THEME -> listOf(SetupTarget.ThemeMode, SetupTarget.Accent)
    SetupPage.HOME_LAYOUT -> listOf(SetupTarget.HomeLayout)
    SetupPage.LIBRARY_LOOK -> listOf(SetupTarget.LibraryLayout, SetupTarget.BoxArtShape)
    SetupPage.LIBRARY -> when (state.signIn.view) {
        LibraryView.CHOICE -> listOf(SetupTarget.RommSource)
        LibraryView.ADDRESS -> listOf(SetupTarget.ServerField)
        LibraryView.PAIRING -> listOf(SetupTarget.PairingField)
        LibraryView.APPROVE, LibraryView.SIGNED_IN -> emptyList()
    }
    SetupPage.PERMISSIONS ->
        (SETUP_REQUIRED_PERMISSIONS + SETUP_RECOMMENDED_PERMISSIONS).map { SetupTarget.Permission(it) }
    SetupPage.ACCESSIBILITY -> when (state.access.variant) {
        AccessibilityVariant.ROOTED -> emptyList()
        AccessibilityVariant.TWO_STEP -> listOf(
            SetupTarget.AccessibilityStep(AccessibilityStepKind.RESTRICTED_SETTINGS),
            SetupTarget.AccessibilityStep(AccessibilityStepKind.SERVICE)
        )
        AccessibilityVariant.ONE_STEP -> listOf(SetupTarget.AccessibilityStep(AccessibilityStepKind.SERVICE))
    }
    SetupPage.FOLDERS -> state.folders.romKinds
        .filter { state.folders.isAvailable(it) }
        .map<RomFolderKind, SetupTarget> { SetupTarget.RomFolder(it) } +
        CoverStorageKind.entries.map { SetupTarget.CoverStorage(it) }
    SetupPage.SAVE_SYNC -> listOf(SetupTarget.SaveSyncChoice(true), SetupTarget.SaveSyncChoice(false))
    SetupPage.PLATFORMS -> buildList<SetupTarget> {
        add(SetupTarget.PlatformHeader)
        add(SetupTarget.SelectAllPlatforms)
        state.platforms.shown.forEach { add(SetupTarget.Platform(it.id)) }
        if (state.platforms.missingCoreIds.isNotEmpty()) add(SetupTarget.CoreDownloads)
    }
}

fun targetsFor(state: SetupUiState): List<SetupTarget> =
    contentTargetsFor(state) + actionsFor(state).map { SetupTarget.Action(it.id) }

fun primaryTarget(state: SetupUiState): SetupTarget? =
    actionsFor(state).lastOrNull { it.primary }?.let { SetupTarget.Action(it.id) }

fun defaultFocus(state: SetupUiState): SetupTarget? {
    val content = contentTargetsFor(state)
    val preferred: SetupTarget? = when (state.page) {
        SetupPage.LIBRARY -> {
            val error = state.signIn.error
            when {
                state.signIn.view != LibraryView.ADDRESS -> content.firstOrNull()
                error?.certificate == true -> SetupTarget.Action(SetupActionId.IMPORT_CERTIFICATE)
                error?.clientCertificate == true -> SetupTarget.Action(SetupActionId.IMPORT_CLIENT_CERTIFICATE)
                else -> content.firstOrNull()
            }
        }
        SetupPage.PERMISSIONS -> firstUnmetRequired(state.access)?.let { SetupTarget.Permission(it) }
        SetupPage.ACCESSIBILITY -> firstIncompleteStep(state.access)?.let { SetupTarget.AccessibilityStep(it) }
        SetupPage.FOLDERS -> SetupTarget.RomFolder(state.folders.selectedRom)
        SetupPage.SAVE_SYNC -> SetupTarget.SaveSyncChoice(state.saveSyncOn)
        SetupPage.PLATFORMS -> content.firstOrNull { it is SetupTarget.Platform } ?: content.firstOrNull()
        SetupPage.SCREENS -> setupScreens(state).firstOrNull { it.role == ScreenRole.PRIMARY }
            ?.let { SetupTarget.Screen(it.key) }?.takeIf { it in content } ?: content.firstOrNull()
        SetupPage.CONTROLLER -> if (state.gamepadConnected) {
            SetupTarget.ControllerLayoutChoice(state.controllerNintendo)
        } else {
            null
        }
        SetupPage.THEME, SetupPage.HOME_LAYOUT, SetupPage.LIBRARY_LOOK -> content.firstOrNull()
        SetupPage.WELCOME, SetupPage.DONE, SetupPage.DEFAULT_HOME, SetupPage.BUTTON_CHECK -> null
    }
    return preferred ?: primaryTarget(state)
}

/**
 * The built-in screens the Screens page maps. Attached monitors are left to Settings > Screens.
 */
fun setupScreens(state: SetupUiState): List<ScreenAssignment> = state.screens.filter { it.builtIn }

fun screensUseSwapRow(state: SetupUiState): Boolean = setupScreens(state).size == 2

fun firstIncompleteStep(access: SetupAccessState): AccessibilityStepKind? = when (access.variant) {
    AccessibilityVariant.ROOTED -> null
    AccessibilityVariant.TWO_STEP -> when {
        !access.restrictedStepDone && !access.accessibilityOn -> AccessibilityStepKind.RESTRICTED_SETTINGS
        !access.accessibilityOn -> AccessibilityStepKind.SERVICE
        else -> null
    }
    AccessibilityVariant.ONE_STEP -> if (access.accessibilityOn) null else AccessibilityStepKind.SERVICE
}

fun focusedTarget(state: SetupUiState): SetupTarget? {
    val stored = state.focused
    return if (stored != null && stored in targetsFor(state)) stored else defaultFocus(state)
}

/**
 * Where [move] takes focus, or null at a boundary. Content runs top to bottom; the action row is
 * the last stop, entered on its primary and walked left and right.
 */
fun moveFocus(state: SetupUiState, move: SetupMove): SetupTarget? {
    val current = focusedTarget(state) ?: return null
    val content = contentTargetsFor(state)
    val actions = actionsFor(state).map { SetupTarget.Action(it.id) }
    val onAction = current is SetupTarget.Action
    return when (move) {
        SetupMove.UP -> if (onAction) content.lastOrNull() else content.getOrNull(content.indexOf(current) - 1)
        SetupMove.DOWN -> if (onAction) null else content.getOrNull(content.indexOf(current) + 1) ?: primaryTarget(state)
        SetupMove.LEFT -> if (onAction) actions.getOrNull(actions.indexOf(current) - 1) else null
        SetupMove.RIGHT -> if (onAction) actions.getOrNull(actions.indexOf(current) + 1) else null
    }
}

fun leadFor(state: SetupUiState): SetupStepLead = when (state.page) {
    SetupPage.WELCOME -> SetupStepLead(R.string.setup_welcome_title, R.string.setup_welcome_body)
    SetupPage.LIBRARY -> when (state.signIn.view) {
        LibraryView.CHOICE -> SetupStepLead(R.string.setup_library_choice_title, R.string.setup_library_choice_purpose)
        LibraryView.ADDRESS -> SetupStepLead(R.string.setup_library_address_title)
        LibraryView.APPROVE -> SetupStepLead(R.string.setup_library_approve_title, R.string.setup_library_approve_purpose)
        LibraryView.PAIRING -> SetupStepLead(R.string.firstrun_pairing_code_title, R.string.firstrun_pairing_code_hint)
        LibraryView.SIGNED_IN -> SetupStepLead(R.string.setup_library_signed_in_title)
    }
    SetupPage.PERMISSIONS -> SetupStepLead(R.string.setup_permissions_title, R.string.setup_permissions_purpose)
    SetupPage.ACCESSIBILITY -> SetupStepLead(R.string.setup_accessibility_title, R.string.setup_accessibility_purpose)
    SetupPage.FOLDERS -> SetupStepLead(R.string.setup_folders_title, R.string.setup_folders_purpose)
    SetupPage.SAVE_SYNC -> SetupStepLead(R.string.firstrun_save_sync_title)
    SetupPage.PLATFORMS -> SetupStepLead(R.string.setup_platforms_title, R.string.setup_platforms_purpose)
    SetupPage.DEFAULT_HOME -> SetupStepLead(R.string.setup_home_title, R.string.setup_home_purpose)
    SetupPage.SCREENS -> SetupStepLead(R.string.setup_screens_title, R.string.setup_screens_purpose)
    SetupPage.CONTROLLER -> if (state.gamepadConnected) {
        SetupStepLead(R.string.setup_controller_title, R.string.setup_controller_purpose)
    } else {
        SetupStepLead(R.string.setup_controller_none_title, R.string.setup_controller_none_body)
    }
    SetupPage.BUTTON_CHECK -> SetupStepLead(R.string.setup_check_title, R.string.setup_check_purpose)
    SetupPage.THEME -> SetupStepLead(R.string.setup_look_theme_title, R.string.setup_look_theme_purpose)
    SetupPage.HOME_LAYOUT -> SetupStepLead(R.string.setup_look_home_title, R.string.setup_look_home_purpose)
    SetupPage.LIBRARY_LOOK -> SetupStepLead(R.string.setup_look_library_title, R.string.setup_look_library_purpose)
    SetupPage.DONE -> SetupStepLead(R.string.setup_done_title)
}
