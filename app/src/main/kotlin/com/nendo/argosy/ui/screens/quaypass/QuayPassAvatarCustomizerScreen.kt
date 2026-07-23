package com.nendo.argosy.ui.screens.quaypass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nendo.argosy.data.quaypass.ble.AvatarCategory
import com.nendo.argosy.data.quaypass.ble.QuayPassAvatar
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.input.QuayPassAvatarCustomizerInputHandler
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.quaypass.avatar.QuayPassAvatarRenderer
import com.nendo.argosy.data.quaypass.ble.QuayPassPartPricing
import com.nendo.argosy.data.quaypass.ble.colorIndexFor
import com.nendo.argosy.data.quaypass.ble.partIndexFor

@Composable
fun QuayPassAvatarCustomizerScreen(
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    viewModel: QuayPassAvatarCustomizerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val inputDispatcher = LocalInputDispatcher.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is QuayPassAvatarCustomizerViewModel.Event.Saved -> onSaved()
                is QuayPassAvatarCustomizerViewModel.Event.Cancelled -> onCancel()
                is QuayPassAvatarCustomizerViewModel.Event.Error -> {}
            }
        }
    }

    val handler = remember(viewModel) {
        QuayPassAvatarCustomizerInputHandler(
            onSectionStep = { viewModel.stepSection(it) },
            onAdjustWithinSection = { viewModel.adjustWithinSection(it) },
            onPageStep = { viewModel.pageStep(it) },
            onConfirmPressed = { viewModel.confirmFocused() },
            onBackPressed = { viewModel.cancel() }
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, handler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.subscribeView(handler, forRoute = Screen.QuayPassAvatarEditor.route)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(handler, forRoute = Screen.QuayPassAvatarEditor.route)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                PreviewPane(
                    avatar = state.avatar,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(24.dp)
                )
                OptionsPane(
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
            FooterBar(hints = footerHints(state))
        }
    }

    state.pendingPurchase?.let { request ->
        PartPurchaseModal(
            request = request,
            balance = state.ticketBalance,
            onConfirm = { viewModel.confirmPurchase(request) },
            onDismiss = { viewModel.dismissPurchase() }
        )
    }
}

private fun footerHints(state: CustomizerState): List<Pair<InputButton, String>> = buildList {
    val focused = state.focusedSection
    val locked = focused == CustomizerSection.Parts &&
        !QuayPassPartPricing.isUnlocked(
            state.selectedCategory,
            state.avatar.partIndexFor(state.selectedCategory),
            state.ownedParts
        )
    if (locked) {
        add(InputButton.A to "Unlock ${QuayPassPartPricing.costFor(state.selectedCategory, state.avatar.partIndexFor(state.selectedCategory))}")
    } else {
        add(InputButton.A to "Select")
    }
    add(InputButton.B to "Back")
    add(InputButton.DPAD_VERTICAL to "Section")
    add(InputButton.DPAD_HORIZONTAL to "Adjust")
    add(InputButton.LB_RB to "Page")
    add(InputButton.SELECT to "${state.ticketBalance} tickets")
}

@Composable
private fun PreviewPane(avatar: QuayPassAvatar, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Build Your Mii",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.weight(1f))
        QuayPassAvatarRenderer(avatar = avatar, size = 240.dp)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun OptionsPane(
    state: CustomizerState,
    viewModel: QuayPassAvatarCustomizerViewModel,
    modifier: Modifier = Modifier
) {
    val usesGrid = state.selectedCategory.usesGrid(viewModel.partCatalog)
    Column(modifier = modifier) {
        val scrollState = rememberScrollState()

        LaunchedEffect(state.focusedSection) {
            val target = when (state.focusedSection) {
                CustomizerSection.Category -> 0
                CustomizerSection.Parts -> 0
                CustomizerSection.Color -> scrollState.maxValue / 2
                CustomizerSection.Toggles -> scrollState.maxValue
                CustomizerSection.Actions -> scrollState.maxValue
            }
            scrollState.animateScrollTo(target)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(start = 8.dp, end = 24.dp, top = 24.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionLabel("Part type", focused = state.focusedSection == CustomizerSection.Category)
            CategoryTabRow(
                selected = state.selectedCategory,
                isSectionFocused = state.focusedSection == CustomizerSection.Category,
                onSelect = { viewModel.selectCategory(it) }
            )

            Spacer(Modifier.height(4.dp))

            SectionLabel(state.selectedCategory.displayName(), focused = state.focusedSection == CustomizerSection.Parts)
            val indices = viewModel.partCatalog.forCategory(state.selectedCategory)
            val selectedIndex = state.avatar.partIndexFor(state.selectedCategory)
            if (usesGrid) {
                PartGrid(
                    category = state.selectedCategory,
                    selectedIndex = selectedIndex,
                    indices = indices,
                    avatar = state.avatar,
                    ownedParts = state.ownedParts,
                    page = state.gridPage,
                    pageCount = state.selectedCategory.pageCount(viewModel.partCatalog),
                    isSectionFocused = state.focusedSection == CustomizerSection.Parts,
                    onSelect = { viewModel.selectPartIndex(state.selectedCategory, it) }
                )
            } else {
                PartCarousel(
                    category = state.selectedCategory,
                    selectedIndex = selectedIndex,
                    indices = indices,
                    avatar = state.avatar,
                    ownedParts = state.ownedParts,
                    isSectionFocused = state.focusedSection == CustomizerSection.Parts,
                    onSelect = { viewModel.selectPartIndex(state.selectedCategory, it) }
                )
            }

            if (state.selectedCategory.isTintable()) {
                Spacer(Modifier.height(4.dp))
                SectionLabel("Color", focused = state.focusedSection == CustomizerSection.Color)
                ColorSwatchRow(
                    palette = paletteFor(state.selectedCategory),
                    selected = state.avatar.colorIndexFor(state.selectedCategory),
                    isSectionFocused = state.focusedSection == CustomizerSection.Color,
                    onSelect = { viewModel.selectColor(state.selectedCategory, it) }
                )
            }

            Spacer(Modifier.height(4.dp))
            SectionLabel("Toggles", focused = state.focusedSection == CustomizerSection.Toggles)
            ToggleRow(
                state = state,
                isSectionFocused = state.focusedSection == CustomizerSection.Toggles,
                onFlip = { viewModel.setFlipHair(it) },
                onMole = { viewModel.setMoleEnabled(it) }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionButton(
                label = "Cancel",
                isSectionFocused = state.focusedSection == CustomizerSection.Actions,
                isFocused = state.actionFocus == 0,
                modifier = Modifier.weight(1f),
                onClick = { viewModel.cancel() }
            )
            ActionButton(
                label = "Save",
                isSectionFocused = state.focusedSection == CustomizerSection.Actions,
                isFocused = state.actionFocus == 1,
                modifier = Modifier.weight(1f),
                isPrimary = true,
                onClick = viewModel::save
            )
        }
    }
}

