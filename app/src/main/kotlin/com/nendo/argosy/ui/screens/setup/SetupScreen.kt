package com.nendo.argosy.ui.screens.setup

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.dualscreen.PresentOnCompanion
import com.nendo.argosy.ui.dualscreen.PresentationSlot
import com.nendo.argosy.ui.dualscreen.SlotOwner
import com.nendo.argosy.ui.dualscreen.selectSwapModeState
import com.nendo.argosy.ui.screens.setup.components.currentGlyphSwaps
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.permissions.rememberPermissionGrantLauncher
import com.nendo.argosy.ui.filebrowser.FileBrowserMode
import com.nendo.argosy.ui.filebrowser.FileBrowserScreen
import com.nendo.argosy.ui.filebrowser.FileFilter
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.screens.setup.components.SetupAction
import com.nendo.argosy.ui.screens.setup.components.SetupStepBody
import com.nendo.argosy.ui.screens.setup.components.SetupStepScaffold
import com.nendo.argosy.util.Logger
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

private enum class SetupPicker { ROM_FOLDER, COVER_FOLDER, CERTIFICATE }

@Composable
fun SetupScreen(
    onComplete: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var picker by remember { mutableStateOf<SetupPicker?>(null) }
    val currentOnComplete by rememberUpdatedState(onComplete)

    val grant = rememberPermissionGrantLauncher(
        grantFor = viewModel::grantFor,
        onRootGrant = viewModel::grantOnRoot,
        onPrivilegedEnable = {},
        onResult = viewModel::refreshAccess
    )
    val homeRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onHomeRoleReturned()
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SetupEffect.Grant -> grant(effect.permission)
                SetupEffect.PickRomFolder -> picker = SetupPicker.ROM_FOLDER
                SetupEffect.PickCoverFolder -> picker = SetupPicker.COVER_FOLDER
                SetupEffect.PickCertificate -> picker = SetupPicker.CERTIFICATE
                is SetupEffect.OpenBrowser -> context.openInBrowser(effect.url)
                SetupEffect.RequestHomeRole -> homeRoleRequest(context)?.let { homeRole.launch(it) }
                SetupEffect.Finish -> currentOnComplete()
            }
        }
    }

    val inputDispatcher = LocalInputDispatcher.current
    val inputHandler = remember(viewModel) { viewModel.createInputHandler() }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.subscribeView(inputHandler, forRoute = Screen.FirstRun.route)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler, forRoute = Screen.FirstRun.route)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    LaunchedEffect(state.loaded, state.page, state.focused) { viewModel.recordPosition() }
    LaunchedEffect(state.page, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(ComponentDefaults.Setup.permissionRecheckMs.toLong())
                viewModel.recheckPage()
            }
        }
    }
    val isTv = remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) }

    val goBack: (() -> Unit)? = if (state.page == SetupPage.WELCOME) null else ({ viewModel.back() })
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (state.loaded) {
                SetupStepScaffold(
                    page = state.page,
                    direction = state.direction,
                    rail = railFor(state.page, state.plan),
                    pageTitle = leadFor(state).title,
                    onBack = goBack,
                    isTv = isTv,
                ) { page ->
                    SetupPageContent(state = state.copy(page = page), viewModel = viewModel)
                }
            }
        }
        val swapMode = selectSwapModeState()
        val testingButtons = state.page == SetupPage.BUTTON_CHECK && state.buttonCheck.testing
        if (state.page != SetupPage.PLATFORMS && !testingButtons && swapMode != null && setupSwapAllowed(state)) {
            FooterBar(
                hints = listOf(InputButton.SELECT to stringResource(setupSwapFooterLabel(swapMode))),
                onHintClick = { if (it == InputButton.SELECT) DualScreenManagerHolder.instance?.swapRoles() }
            )
        }
    }

    if (state.loaded) {
        val swaps = currentGlyphSwaps()
        PresentOnCompanion(
            owner = SlotOwner.of(SETUP_SLOT_OWNER, viewModel),
            slot = PresentationSlot.Setup(companionFor(state, swaps))
        )
    }

    SetupKeyboards(state = state, viewModel = viewModel)

    picker?.let { shown ->
        FileBrowserScreen(
            mode = if (shown == SetupPicker.CERTIFICATE) FileBrowserMode.FILE_SELECTION else FileBrowserMode.FOLDER_SELECTION,
            fileFilter = if (shown == SetupPicker.CERTIFICATE) FileFilter.CERTIFICATE else null,
            onPathSelected = { path ->
                picker = null
                when (shown) {
                    SetupPicker.ROM_FOLDER -> viewModel.onRomFolderPicked(path)
                    SetupPicker.COVER_FOLDER -> viewModel.onCoverFolderPicked(path)
                    SetupPicker.CERTIFICATE -> viewModel.onCertificatePicked(path)
                }
            },
            onDismiss = { picker = null }
        )
    }
}

@Composable
private fun SetupPageContent(state: SetupUiState, viewModel: SetupViewModel) {
    when (state.page) {
        SetupPage.WELCOME -> WelcomePage(state = state, onStart = { viewModel.tap(SetupTarget.Action(SetupActionId.PRIMARY)) })
        SetupPage.LIBRARY -> LibraryPage(state = state, viewModel = viewModel)
        SetupPage.PERMISSIONS -> PermissionsPage(state = state, viewModel = viewModel)
        SetupPage.ACCESSIBILITY -> AccessibilityPage(state = state, viewModel = viewModel)
        SetupPage.FOLDERS -> FoldersPage(state = state, viewModel = viewModel)
        SetupPage.SAVE_SYNC -> SaveSyncPage(state = state, viewModel = viewModel)
        SetupPage.PLATFORMS -> PlatformsPage(state = state, viewModel = viewModel)
        SetupPage.DEFAULT_HOME -> DefaultHomePage(state = state, viewModel = viewModel)
        SetupPage.SCREENS -> ScreensPage(state = state, viewModel = viewModel)
        SetupPage.CONTROLLER -> ControllerPage(state = state, viewModel = viewModel)
        SetupPage.BUTTON_CHECK -> ButtonCheckPage(state = state, viewModel = viewModel)
        SetupPage.THEME -> ThemePage(state = state, viewModel = viewModel)
        SetupPage.HOME_LAYOUT -> HomeLayoutPage(state = state, viewModel = viewModel)
        SetupPage.LIBRARY_LOOK -> LibraryLookPage(state = state, viewModel = viewModel)
        SetupPage.DONE -> DonePage(state = state, viewModel = viewModel)
    }
}

@Composable
internal fun SetupPageBody(
    state: SetupUiState,
    viewModel: SetupViewModel,
    context: (@Composable () -> Unit)? = null,
    leadTrailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val contextOnCompanion = setupShownOnCompanion()
    val focus = focusedTarget(state)
    val specs = actionsFor(state)
    SetupStepBody(
        lead = leadFor(state),
        actions = specs.map {
            SetupAction(
                label = it.label,
                primary = it.primary,
                focused = focus == SetupTarget.Action(it.id),
                hiddenWhenDocked = it.hiddenWhenDocked,
                labelCount = it.labelCount,
            )
        },
        onAction = { index -> specs.getOrNull(index)?.let { viewModel.tap(SetupTarget.Action(it.id)) } },
        contextOnCompanion = contextOnCompanion,
        context = context,
        leadTrailing = leadTrailing,
        content = content,
    )
}

@Composable
internal fun setupShownOnCompanion(): Boolean =
    DualScreenManagerHolder.instance?.presentationShowsHints?.collectAsState()?.value == true

private fun homeRoleRequest(context: Context): Intent? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roles = context.getSystemService(RoleManager::class.java)
        if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
            return roles.createRequestRoleIntent(RoleManager.ROLE_HOME)
        }
    }
    return Intent(Settings.ACTION_HOME_SETTINGS).takeIf { it.resolveActivity(context.packageManager) != null }
}

private fun Context.openInBrowser(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Logger.warn(TAG, "Sign-in page did not open", e)
    }
}

private const val TAG = "SetupScreen"
private const val SETUP_SLOT_OWNER = "setup"
