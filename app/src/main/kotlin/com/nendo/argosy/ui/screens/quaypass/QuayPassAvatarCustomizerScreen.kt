package com.nendo.argosy.ui.screens.quaypass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.nendo.argosy.data.quaypass.ble.QuayPassAvatar
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.input.QuayPassAvatarCustomizerInputHandler
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.quaypass.avatar.AvatarCategory
import com.nendo.argosy.ui.quaypass.avatar.AvatarPartRequest
import com.nendo.argosy.ui.quaypass.avatar.QuayPassAvatarPalette
import com.nendo.argosy.ui.quaypass.avatar.QuayPassAvatarRenderer
import com.nendo.argosy.ui.quaypass.avatar.colorIndexFor
import com.nendo.argosy.ui.quaypass.avatar.partIndexFor
import com.nendo.argosy.ui.util.clickableNoFocus

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
            onConfirm = { viewModel.confirmFocused() },
            onBack = { viewModel.cancel() }
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
        Row(modifier = Modifier.fillMaxSize()) {
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
    }
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
    Column(modifier = modifier) {
        val scrollState = rememberScrollState()

        LaunchedEffect(state.focusedSection) {
            val target = when (state.focusedSection) {
                CustomizerSection.Category -> 0
                CustomizerSection.Parts -> 0
                CustomizerSection.Color -> scrollState.maxValue / 2
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
            PartCarousel(
                category = state.selectedCategory,
                selectedIndex = state.avatar.partIndexFor(state.selectedCategory),
                indices = viewModel.partCatalog.forCategory(state.selectedCategory),
                avatar = state.avatar,
                isSectionFocused = state.focusedSection == CustomizerSection.Parts,
                onSelect = { viewModel.selectPartIndex(state.selectedCategory, it) }
            )

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

@Composable
private fun SectionLabel(text: String, focused: Boolean) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
        color = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ActionButton(
    label: String,
    isSectionFocused: Boolean,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    onClick: () -> Unit
) {
    val highlight = isSectionFocused && isFocused
    val container = when {
        highlight && isPrimary -> MaterialTheme.colorScheme.primary
        highlight -> MaterialTheme.colorScheme.primaryContainer
        isPrimary -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
        else -> Color.Transparent
    }
    val content = when {
        highlight && isPrimary -> MaterialTheme.colorScheme.onPrimary
        highlight -> MaterialTheme.colorScheme.onPrimaryContainer
        isPrimary -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content
        ),
        shape = RoundedCornerShape(12.dp)
    ) { Text(label) }
}

@Composable
private fun CategoryTabRow(
    selected: AvatarCategory,
    isSectionFocused: Boolean,
    onSelect: (AvatarCategory) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selected) {
        val idx = AvatarCategory.entries.indexOf(selected)
        if (idx >= 0) listState.animateScrollToItem(idx)
    }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(AvatarCategory.entries.toTypedArray()) { category ->
            val isSelected = category == selected
            val highlight = isSectionFocused && isSelected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when {
                            highlight -> MaterialTheme.colorScheme.primary
                            isSelected -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                    .clickableNoFocus { onSelect(category) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = category.displayName(),
                    style = MaterialTheme.typography.labelLarge,
                    color = when {
                        highlight -> MaterialTheme.colorScheme.onPrimary
                        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun PartCarousel(
    category: AvatarCategory,
    selectedIndex: Int,
    indices: List<Int>,
    avatar: QuayPassAvatar,
    isSectionFocused: Boolean,
    onSelect: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    val displayIndices = remember(category, indices) {
        if (category in OPTIONAL_CATEGORIES) listOf(0) + indices.filter { it != 0 } else indices
    }
    LaunchedEffect(selectedIndex, displayIndices) {
        val pos = displayIndices.indexOf(selectedIndex).coerceAtLeast(0)
        listState.animateScrollToItem(pos)
    }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(displayIndices) { index ->
            PartThumbnail(
                isSelected = selectedIndex == index,
                isSectionFocused = isSectionFocused,
                onClick = { onSelect(index) }
            ) {
                if (index == 0 && category in OPTIONAL_CATEGORIES) {
                    Text(
                        text = "None",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    AsyncImage(
                        model = avatar.toThumbnailRequest(category, index),
                        contentDescription = null,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PartThumbnail(
    isSelected: Boolean,
    isSectionFocused: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val borderColor = when {
        isSectionFocused && isSelected -> MaterialTheme.colorScheme.primary
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        else -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = if (isSectionFocused && isSelected) 3.dp else 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickableNoFocus(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun ColorSwatchRow(
    palette: List<Color>,
    selected: Int,
    isSectionFocused: Boolean,
    onSelect: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selected) {
        listState.animateScrollToItem(selected.coerceAtLeast(0))
    }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(palette.size) { i ->
            val isSelected = i == selected
            val highlight = isSectionFocused && isSelected
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(palette[i])
                    .border(
                        width = when {
                            highlight -> 3.dp
                            isSelected -> 2.dp
                            else -> 1.dp
                        },
                        color = when {
                            highlight -> MaterialTheme.colorScheme.primary
                            isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        },
                        shape = CircleShape
                    )
                    .clickableNoFocus { onSelect(i) }
            )
        }
    }
}

private val OPTIONAL_CATEGORIES = setOf(
    AvatarCategory.Wrinkles,
    AvatarCategory.Makeup,
    AvatarCategory.Mustache,
    AvatarCategory.Goatee,
    AvatarCategory.Glasses,
    AvatarCategory.Hat
)

private fun AvatarCategory.displayName(): String = when (this) {
    AvatarCategory.Face -> "Face"
    AvatarCategory.Wrinkles -> "Wrinkles"
    AvatarCategory.Makeup -> "Makeup"
    AvatarCategory.Eyes -> "Eyes"
    AvatarCategory.Eyebrows -> "Brows"
    AvatarCategory.Nose -> "Nose"
    AvatarCategory.Mouth -> "Mouth"
    AvatarCategory.Mustache -> "Mustache"
    AvatarCategory.Goatee -> "Goatee"
    AvatarCategory.Hair -> "Hair"
    AvatarCategory.Glasses -> "Glasses"
    AvatarCategory.Hat -> "Hat"
}

private fun QuayPassAvatar.toThumbnailRequest(category: AvatarCategory, index: Int): AvatarPartRequest =
    AvatarPartRequest(
        category = category,
        index = index,
        skin = QuayPassAvatarPalette.skinAt(skinColor),
        hair = QuayPassAvatarPalette.hairAt(hairColor),
        eyebrow = QuayPassAvatarPalette.hairAt(eyebrowColor),
        eye = QuayPassAvatarPalette.eyeAt(eyeColor),
        mouth = QuayPassAvatarPalette.mouthAt(mouthColor),
        facialHair = QuayPassAvatarPalette.hairAt(facialHairColor),
        glasses = QuayPassAvatarPalette.accessoryAt(glassesColor),
        hat = QuayPassAvatarPalette.accessoryAt(hatColor)
    )

private fun paletteFor(category: AvatarCategory): List<Color> = when (category) {
    AvatarCategory.Face -> QuayPassAvatarPalette.skin
    AvatarCategory.Hair -> QuayPassAvatarPalette.hair
    AvatarCategory.Eyes -> QuayPassAvatarPalette.eye
    AvatarCategory.Eyebrows -> QuayPassAvatarPalette.hair
    AvatarCategory.Mouth -> QuayPassAvatarPalette.mouth
    AvatarCategory.Mustache, AvatarCategory.Goatee -> QuayPassAvatarPalette.hair
    AvatarCategory.Glasses, AvatarCategory.Hat -> QuayPassAvatarPalette.accessory
    else -> QuayPassAvatarPalette.skin
}
