package com.nendo.argosy.ui.screens.quaypass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nendo.argosy.data.quaypass.ble.AvatarCategory
import com.nendo.argosy.ui.util.clickableNoFocus

@Composable
fun SectionLabel(text: String, focused: Boolean) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
        color = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun ActionButton(
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
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content
        ),
        shape = RoundedCornerShape(12.dp)
    ) { Text(label) }
}

@Composable
fun CategoryTabRow(
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
        items(AvatarCategory.entries.toTypedArray(), key = { it.name }) { category ->
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
fun ColorSwatchRow(
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
        items(palette.size, key = { it }) { i ->
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

@Composable
fun ToggleRow(
    state: CustomizerState,
    isSectionFocused: Boolean,
    onFlip: (Boolean) -> Unit,
    onMole: (Boolean) -> Unit
) {
    val toggles = visibleToggles(state.selectedCategory)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        toggles.forEachIndexed { i, toggle ->
            val (label, value, onToggle) = when (toggle) {
                CustomizerToggle.FlipHair -> Triple("Flip", state.avatar.flipHair, { onFlip(!state.avatar.flipHair) })
                CustomizerToggle.Mole -> Triple("Mole", state.avatar.moleEnabled, { onMole(!state.avatar.moleEnabled) })
            }
            TogglePill(
                label = label,
                enabled = value,
                highlight = isSectionFocused && state.toggleFocus == i,
                onClick = onToggle
            )
        }
    }
}

@Composable
private fun TogglePill(label: String, enabled: Boolean, highlight: Boolean, onClick: () -> Unit) {
    val container = when {
        highlight && enabled -> MaterialTheme.colorScheme.primary
        enabled -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        highlight -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when {
        enabled -> MaterialTheme.colorScheme.onPrimary
        highlight -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(container)
            .border(
                width = if (highlight) 2.dp else 0.dp,
                color = if (highlight) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = "$label: ${if (enabled) "On" else "Off"}",
            style = MaterialTheme.typography.labelLarge,
            color = content
        )
    }
}
