package com.nendo.argosy.ui.screens.quaypass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.nendo.argosy.data.quaypass.ble.QuayPassAvatar
import com.nendo.argosy.ui.quaypass.avatar.AvatarCategory
import com.nendo.argosy.ui.quaypass.avatar.AvatarPartRequest
import com.nendo.argosy.ui.quaypass.avatar.QuayPassAvatarPalette
import com.nendo.argosy.ui.quaypass.avatar.QuayPassAvatarRenderer

@Composable
fun QuayPassAvatarCustomizerScreen(
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    viewModel: QuayPassAvatarCustomizerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is QuayPassAvatarCustomizerViewModel.Event.Saved -> onSaved()
                is QuayPassAvatarCustomizerViewModel.Event.Error -> {}
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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

            CategoryTabRow(
                selected = state.selectedCategory,
                onSelect = viewModel::selectCategory
            )

            PartCarousel(
                category = state.selectedCategory,
                selectedIndex = state.avatar.partIndexFor(state.selectedCategory),
                indices = viewModel.partCatalog.forCategory(state.selectedCategory),
                avatar = state.avatar,
                onSelect = { viewModel.selectPartIndex(state.selectedCategory, it) }
            )

            if (state.selectedCategory.isTintable()) {
                Text(
                    text = "Color",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                ColorSwatchRow(
                    palette = paletteFor(state.selectedCategory),
                    selected = state.avatar.colorIndexFor(state.selectedCategory),
                    onSelect = { viewModel.selectColor(state.selectedCategory, it) }
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onCancel
                ) { Text("Cancel") }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = viewModel::save
                ) { Text("Save") }
            }
        }
    }
}

@Composable
private fun CategoryTabRow(
    selected: AvatarCategory,
    onSelect: (AvatarCategory) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(AvatarCategory.entries.toTypedArray()) { category ->
            val isSelected = category == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onSelect(category) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = category.displayName(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
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
                    onClick = { onSelect(0) },
                    label = "None"
                )
            }
        }
        items(indices) { index ->
            PartThumbnail(
                isSelected = selectedIndex == index,
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
    onClick: () -> Unit,
    label: String? = null,
    content: @Composable () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 2.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick),
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
    onSelect: (Int) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(palette.size) { i ->
            val isSelected = i == selected
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(palette[i])
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        shape = CircleShape
                    )
                    .clickable { onSelect(i) }
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

private fun AvatarCategory.isTintable(): Boolean = this !in setOf(
    AvatarCategory.Wrinkles,
    AvatarCategory.Makeup,
    AvatarCategory.Nose
)

private val OPTIONAL_CATEGORIES = setOf(
    AvatarCategory.Wrinkles,
    AvatarCategory.Makeup,
    AvatarCategory.Mustache,
    AvatarCategory.Goatee,
    AvatarCategory.Glasses,
    AvatarCategory.Hat
)

private fun QuayPassAvatar.partIndexFor(category: AvatarCategory): Int = when (category) {
    AvatarCategory.Face -> faceShape
    AvatarCategory.Wrinkles -> wrinkles
    AvatarCategory.Makeup -> makeup
    AvatarCategory.Eyes -> eyeType
    AvatarCategory.Eyebrows -> eyebrowType
    AvatarCategory.Nose -> noseType
    AvatarCategory.Mouth -> mouthType
    AvatarCategory.Mustache -> mustacheType
    AvatarCategory.Goatee -> goateeType
    AvatarCategory.Hair -> hairType
    AvatarCategory.Glasses -> glassesType
    AvatarCategory.Hat -> hatType
}

private fun QuayPassAvatar.colorIndexFor(category: AvatarCategory): Int = when (category) {
    AvatarCategory.Face -> skinColor
    AvatarCategory.Hair -> hairColor
    AvatarCategory.Eyes -> eyeColor
    AvatarCategory.Eyebrows -> eyebrowColor
    AvatarCategory.Mouth -> mouthColor
    AvatarCategory.Mustache, AvatarCategory.Goatee -> facialHairColor
    AvatarCategory.Glasses -> glassesColor
    AvatarCategory.Hat -> hatColor
    else -> 0
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
