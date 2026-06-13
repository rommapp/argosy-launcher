package com.nendo.argosy.ui.screens.quaypass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.data.quaypass.ble.QuayPassAvatar
import com.nendo.argosy.ui.quaypass.avatar.AvatarCategory
import com.nendo.argosy.ui.quaypass.avatar.AvatarPartRequest
import com.nendo.argosy.ui.quaypass.avatar.QuayPassAvatarPalette
import com.nendo.argosy.ui.quaypass.avatar.QuayPassPartPricing
import com.nendo.argosy.ui.util.clickableNoFocus

val OPTIONAL_CATEGORIES = setOf(
    AvatarCategory.Wrinkles,
    AvatarCategory.Makeup,
    AvatarCategory.Mustache,
    AvatarCategory.Goatee,
    AvatarCategory.Glasses,
    AvatarCategory.Hat
)

fun AvatarCategory.displayName(): String = when (this) {
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

fun paletteFor(category: AvatarCategory): List<Color> = when (category) {
    AvatarCategory.Face -> QuayPassAvatarPalette.skin
    AvatarCategory.Hair -> QuayPassAvatarPalette.hair
    AvatarCategory.Eyes -> QuayPassAvatarPalette.eye
    AvatarCategory.Eyebrows -> QuayPassAvatarPalette.hair
    AvatarCategory.Mouth -> QuayPassAvatarPalette.mouth
    AvatarCategory.Mustache, AvatarCategory.Goatee -> QuayPassAvatarPalette.hair
    AvatarCategory.Glasses, AvatarCategory.Hat -> QuayPassAvatarPalette.accessory
    else -> QuayPassAvatarPalette.skin
}

fun QuayPassAvatar.toThumbnailRequest(category: AvatarCategory, index: Int): AvatarPartRequest =
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

@Composable
fun PartCarousel(
    category: AvatarCategory,
    selectedIndex: Int,
    indices: List<Int>,
    avatar: QuayPassAvatar,
    ownedParts: Set<String>,
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
        items(displayIndices, key = { it }) { index ->
            PartThumbnail(
                category = category,
                index = index,
                avatar = avatar,
                isSelected = selectedIndex == index,
                isSectionFocused = isSectionFocused,
                locked = !QuayPassPartPricing.isUnlocked(category, index, ownedParts),
                cost = QuayPassPartPricing.costFor(category, index),
                showNone = index == 0 && category in OPTIONAL_CATEGORIES,
                onClick = { onSelect(index) }
            )
        }
    }
}

@Composable
fun PartGrid(
    category: AvatarCategory,
    selectedIndex: Int,
    indices: List<Int>,
    avatar: QuayPassAvatar,
    ownedParts: Set<String>,
    page: Int,
    pageCount: Int,
    isSectionFocused: Boolean,
    onSelect: (Int) -> Unit
) {
    val pageIndices = remember(indices, page) {
        indices.drop(page * GRID_PAGE_SIZE).take(GRID_PAGE_SIZE)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GridPageHeader(page = page, pageCount = pageCount, focused = isSectionFocused)
        LazyVerticalGrid(
            columns = GridCells.Fixed(GRID_COLUMNS),
            modifier = Modifier.heightIn(max = 320.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(pageIndices, key = { it }) { index ->
                PartThumbnail(
                    category = category,
                    index = index,
                    avatar = avatar,
                    isSelected = selectedIndex == index,
                    isSectionFocused = isSectionFocused,
                    locked = !QuayPassPartPricing.isUnlocked(category, index, ownedParts),
                    cost = QuayPassPartPricing.costFor(category, index),
                    showNone = false,
                    onClick = { onSelect(index) }
                )
            }
        }
        GridProgressTrack(page = page, pageCount = pageCount)
    }
}

@Composable
private fun GridPageHeader(page: Int, pageCount: Int, focused: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "LB",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Page ${page + 1}/$pageCount",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            color = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "RB",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GridProgressTrack(page: Int, pageCount: Int) {
    val fraction = if (pageCount <= 1) 1f else (page + 1).toFloat() / pageCount
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
private fun PartThumbnail(
    category: AvatarCategory,
    index: Int,
    avatar: QuayPassAvatar,
    isSelected: Boolean,
    isSectionFocused: Boolean,
    locked: Boolean,
    cost: Int,
    showNone: Boolean,
    onClick: () -> Unit
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
        Box(
            modifier = Modifier.alpha(if (locked) 0.4f else 1f),
            contentAlignment = Alignment.Center
        ) {
            if (showNone) {
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
        if (locked) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = "Locked",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "$cost",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            )
        }
    }
}
