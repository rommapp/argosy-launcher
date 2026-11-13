package com.nendo.argosy.ui.screens.setup

import androidx.annotation.StringRes
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.preferences.BoxArtShape
import com.nendo.argosy.data.preferences.LibraryLayout
import com.nendo.argosy.data.preferences.ThemeMode
import com.nendo.argosy.domain.model.HomeLayoutSettings
import com.nendo.argosy.ui.screens.common.ClientCertState
import com.nendo.argosy.ui.screens.settings.ScreenAssignment
import com.nendo.argosy.ui.screens.setup.components.ButtonCheckState
import com.nendo.argosy.domain.model.ArgosyPermission
import com.nendo.argosy.domain.model.CoreDownloadProgress
import com.nendo.argosy.domain.model.FolderSpace
import com.nendo.argosy.domain.model.PermissionAvailability
import com.nendo.argosy.domain.model.PermissionSnapshot
import com.nendo.argosy.domain.model.SetupFacts
import com.nendo.argosy.ui.screens.setup.components.SetupDirection
import com.nendo.argosy.util.PlatformFilterLogic

enum class LibraryView { CHOICE, ADDRESS, APPROVE, PAIRING, SIGNED_IN }

enum class SetupField { SERVER_ADDRESS, PAIRING_CODE }

/**
 * A sign-in failure to show on the library page. Either app-authored text, held as [textRes] plus
 * an optional [arg] so the render site resolves it in the person's locale, or [serverMessage],
 * which the server supplied and the app cannot translate. [certificate] marks a failure the
 * person fixes by importing a server certificate, [clientCertificate] one they fix by importing a
 * client certificate.
 */
data class SetupError(
    @StringRes val textRes: Int? = null,
    val arg: String? = null,
    val serverMessage: String? = null,
    val certificate: Boolean = false,
    val clientCertificate: Boolean = false
)

data class RommSignInState(
    val view: LibraryView = LibraryView.CHOICE,
    val url: String = "",
    val pairingCode: String = "",
    val supportsDeviceAuth: Boolean = false,
    val deviceUserCode: String? = null,
    val deviceVerificationUrl: String? = null,
    val browserAvailable: Boolean = false,
    val isConnecting: Boolean = false,
    val error: SetupError? = null,
    val importedCertCount: Int = 0,
    val clientCert: ClientCertState = ClientCertState(),
    val gameCount: Int = 0,
    val platformCount: Int = 0,
    val summaryLoaded: Boolean = false
) {
    val serverName: String get() = url.substringAfter("://").trimEnd('/')
}

enum class AccessibilityStepKind { RESTRICTED_SETTINGS, SERVICE }

enum class AccessibilityVariant { ROOTED, TWO_STEP, ONE_STEP }

data class SetupAccessState(
    val snapshot: PermissionSnapshot = PermissionSnapshot(),
    val rootRoute: Boolean = false,
    val restrictedSettings: Boolean = false,
    val accessibilityOffered: Boolean = true,
    val rootGranting: Set<ArgosyPermission> = emptySet(),
    val rootGranted: Boolean = false,
    val rootAccessibilityFailed: Boolean = false,
    val restrictedStepDone: Boolean = false,
    val stillBlocked: Boolean = false,
    val awaiting: AccessibilityStepKind? = null
) {
    fun availabilityOf(permission: ArgosyPermission): PermissionAvailability =
        if (permission in rootGranting) PermissionAvailability.CHECKING else snapshot.of(permission)

    val accessibilityOn: Boolean get() = snapshot.isGranted(ArgosyPermission.ACCESSIBILITY)

    val variant: AccessibilityVariant
        get() = when {
            rootRoute && !rootAccessibilityFailed -> AccessibilityVariant.ROOTED
            restrictedSettings -> AccessibilityVariant.TWO_STEP
            else -> AccessibilityVariant.ONE_STEP
        }
}

enum class RomFolderKind { INTERNAL, SD_CARD, CUSTOM }

enum class CoverStorageKind { PRIVATE, PUBLIC }

/**
 * The folder page's choices. [space] is keyed by path; a path missing from it has not been read
 * yet. [sdCardRemoved] marks a card that was present when the page opened and has since gone.
 */
data class FolderChoices(
    val internalPath: String = "",
    val sdCardPath: String? = null,
    val sdCardRemoved: Boolean = false,
    val customPath: String? = null,
    val selectedRom: RomFolderKind = RomFolderKind.INTERNAL,
    val publicCoverPath: String? = null,
    val selectedCover: CoverStorageKind = CoverStorageKind.PRIVATE,
    val space: Map<String, FolderSpace> = emptyMap(),
    val unwritable: RomFolderKind? = null,
    val loaded: Boolean = false
) {
    val romKinds: List<RomFolderKind>
        get() = buildList {
            add(RomFolderKind.INTERNAL)
            if (sdCardPath != null) add(RomFolderKind.SD_CARD)
            add(RomFolderKind.CUSTOM)
        }

    fun pathOf(kind: RomFolderKind): String? = when (kind) {
        RomFolderKind.INTERNAL -> internalPath
        RomFolderKind.SD_CARD -> sdCardPath
        RomFolderKind.CUSTOM -> customPath
    }

    fun isAvailable(kind: RomFolderKind): Boolean = when (kind) {
        RomFolderKind.SD_CARD -> sdCardPath != null && !sdCardRemoved
        else -> true
    }

    val selectedRomPath: String?
        get() = pathOf(selectedRom)?.takeIf { it.isNotBlank() && isAvailable(selectedRom) }

    val coverPath: String?
        get() = publicCoverPath.takeIf { selectedCover == CoverStorageKind.PUBLIC }
}

/**
 * Places the saved ROM folder and cover folder among the page's choices, so re-entry shows what
 * was confirmed before.
 */
fun folderChoicesFrom(
    internalPath: String,
    sdCardPath: String?,
    savedRomPath: String?,
    savedCoverPath: String?
): FolderChoices {
    val saved = savedRomPath?.takeIf { it.isNotBlank() }
    val romKind = when (saved) {
        null, internalPath -> RomFolderKind.INTERNAL
        sdCardPath -> RomFolderKind.SD_CARD
        else -> RomFolderKind.CUSTOM
    }
    val cover = savedCoverPath?.takeIf { it.isNotBlank() }
    return FolderChoices(
        internalPath = internalPath,
        sdCardPath = sdCardPath,
        customPath = saved.takeIf { romKind == RomFolderKind.CUSTOM },
        selectedRom = romKind,
        publicCoverPath = cover,
        selectedCover = if (cover != null) CoverStorageKind.PUBLIC else CoverStorageKind.PRIVATE,
        loaded = true
    )
}

/**
 * The folder choices after the mounted card changed to [sdCardPath]. A card that leaves locks its
 * row and hands its selection back to internal storage; a card that returns unlocks it.
 */
fun FolderChoices.withSdCard(sdCardPath: String?): FolderChoices = when {
    sdCardPath != null -> copy(sdCardPath = sdCardPath, sdCardRemoved = false)
    this.sdCardPath == null -> this
    else -> copy(
        sdCardRemoved = true,
        selectedRom = if (selectedRom == RomFolderKind.SD_CARD) RomFolderKind.INTERNAL else selectedRom
    )
}

data class SetupPlatformsState(
    val all: List<PlatformEntity> = emptyList(),
    val shown: List<PlatformEntity> = emptyList(),
    val sortMode: PlatformFilterLogic.SortMode = PlatformFilterLogic.SortMode.DEFAULT,
    val filterMode: PlatformFilterLogic.FilterMode = PlatformFilterLogic.FilterMode.ALL,
    val searchQuery: String = "",
    val headerIndex: Int = 0,
    val searchActive: Boolean = false,
    val sortMenuOpen: Boolean = false,
    val sortMenuIndex: Int = 0,
    val missingCoreIds: List<String> = emptyList(),
    val downloadCores: Boolean = true,
    val loaded: Boolean = false
)

data class SetupLookState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val primaryColor: Int? = null,
    val homeLayout: HomeLayoutSettings = HomeLayoutSettings(),
    val libraryLayout: LibraryLayout = LibraryLayout.GRID,
    val boxArtShape: BoxArtShape = BoxArtShape.STANDARD
)

val SETUP_BOX_ART_SHAPES: List<BoxArtShape> = listOf(BoxArtShape.TALL, BoxArtShape.STANDARD, BoxArtShape.SQUARE)

data class SetupUiState(
    val page: SetupPage = SetupPage.WELCOME,
    val direction: SetupDirection = SetupDirection.FORWARD,
    val offers: SetupOffers = SetupOffers(),
    val focused: SetupTarget? = null,
    val openField: SetupField? = null,
    val saveSyncOn: Boolean = true,
    val facts: SetupFacts = SetupFacts(),
    val loaded: Boolean = false,
    val signIn: RommSignInState = RommSignInState(),
    val access: SetupAccessState = SetupAccessState(),
    val folders: FolderChoices = FolderChoices(),
    val platforms: SetupPlatformsState = SetupPlatformsState(),
    val cores: CoreDownloadProgress = CoreDownloadProgress(),
    val screens: List<ScreenAssignment> = emptyList(),
    val gamepadConnected: Boolean = false,
    val controllerNintendo: Boolean = false,
    val buttonCheck: ButtonCheckState = ButtonCheckState(),
    val look: SetupLookState = SetupLookState()
) {
    val plan: List<SetupPage> get() = setupPlan(offers)

    fun withPersonal(personal: SetupPersonalizeState): SetupUiState = copy(
        gamepadConnected = personal.gamepadConnected,
        controllerNintendo = personal.controllerNintendo,
        buttonCheck = personal.buttonCheck,
        look = personal.look
    )
}
