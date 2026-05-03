package com.nendo.argosy.ui.screens.quaypass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.nendo.argosy.data.quaypass.ble.QuayPassAvatar
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.input.QuayPassAvatarCustomizerInputHandler
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.quaypass.avatar.AvatarCategory
import com.nendo.argosy.ui.quaypass.avatar.AvatarPartRequest
import com.nendo.argosy.ui.quaypass.avatar.QuayPassAvatarPalette
import com.nendo.argosy.ui.quaypass.avatar.QuayPassAvatarRenderer
import com.nendo.argosy.ui.quaypass.avatar.colorIndexFor
import com.nendo.argosy.ui.quaypass.avatar.partIndexFor

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
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Build Your Mii",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    QuayPassAvatarRenderer(avatar = state.avatar, size = 200.dp)
                }

                SectionLabel("Part type", focused = state.focusedSection == CustomizerSection.Category)
                CategoryTabRow(
                    selected = state.selectedCategory,
                    isSectionFocused = state.focusedSection == CustomizerSection.Category,
                    onSelect = viewModel::selectCategory
                )

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
                    SectionLabel("Color", focused = state.focusedSection == CustomizerSection.Color)
                    ColorSwatchRow(
                        palette = paletteFor(state.selectedCategory),
                        selected = state.avatar.colorIndexFor(state.selectedCategory),
                        isSectionFocused = state.focusedSection == CustomizerSection.Color,
                        onSelect = { viewModel.selectColor(state.selectedCategory, it) }
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
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

            FooterBar(
                hints = listOf(
                    InputButton.B to "Cancel",
                    InputButton.DPAD_VERTICAL to "Section",
                    InputButton.DPAD_HORIZONTAL to "Adjust",
                    InputButton.A to "Confirm"
                )
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
        modifier = modifier.height(48.dp),
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
    LazyRow(
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
    LazyRow(
        contentPadding = PaddingValues(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (category in OPTIONAL_CATEGORIES) {
            item("none") {
                PartThumbnail(
                    isSelected = selectedIndex == 0,
                    isSectionFocused = isSectionFocused,
                    onClick = { onSelect(0) },
                    label = "None"
                )
            }
        }
        items(indices) { index ->
            PartThumbnail(
                isSelected = selectedIndex == index,
                isSectionFocused = isSectionFocused,
                onClick = { onSelect(index) }
            ) {
                AsyncImage(
                    model = avatar.toThumbnailRequest(category, index),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp)
                )
            }
        }
    }
}

@Composable
private fun PartThumbnail(
    isSelected: Boolean,
    isSectionFocused: Boolean,
    onClick: () -> Unit,
    label: String? = null,
    content: @Composable () -> Unit = {}
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
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            content()
        }
    }
}

@Composable
private fun ColorSwatchRow(
    palette: List<Color>,
    selected: Int,
    isSectionFocused: Boolean,
    onSelect: (Int) -> Unit
) {
    LazyRow(
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

private val OPTIONAL_CATEGORIES = setOf(
    AvatarCategory.Wrinkles,
    AvatarCategory.Makeup,
    AvatarCategory.Mustache,
    AvatarCategory.Goatee,
    AvatarCategory.Glasses,
    AvatarCategory.Hat
)

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
