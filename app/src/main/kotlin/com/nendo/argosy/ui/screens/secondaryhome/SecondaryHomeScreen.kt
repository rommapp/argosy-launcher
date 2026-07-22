package com.nendo.argosy.ui.screens.secondaryhome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.ui.coil.AppIconData
import com.nendo.argosy.ui.common.LongPressAnimationConfig
import com.nendo.argosy.ui.common.longPressGesture
import com.nendo.argosy.ui.common.longPressGraphicsLayer
import com.nendo.argosy.ui.common.rememberLongPressAnimationState
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.util.clickableNoFocus

@Composable
fun AllAppsDrawerOverlay(
    apps: List<DrawerAppUi>,
    focusedIndex: Int,
    screenWidthDp: Int,
    onPinToggle: (String) -> Unit,
    onAppClick: (String) -> Unit,
    onClose: () -> Unit
) {
    val columns = 4
    val drawerGridState = rememberLazyGridState()

    LaunchedEffect(focusedIndex) {
        if (apps.isNotEmpty() && focusedIndex in apps.indices) {
            val viewportHeight = drawerGridState.layoutInfo.viewportSize.height
            val itemHeight = drawerGridState.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.height ?: 0
            val centerOffset = if (itemHeight > 0) (viewportHeight - itemHeight) / 2 else 0
            drawerGridState.animateScrollToItem(focusedIndex, -centerOffset)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.65f)
            .background(
                MaterialTheme.colorScheme.surface,
                RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
            .clickableNoFocus {}
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }

        Text(
            text = "All Apps",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = drawerGridState,
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                DrawerAppItem(
                    app = app,
                    isFocused = index == focusedIndex,
                    onClick = { onAppClick(app.packageName) },
                    onPinToggle = { onPinToggle(app.packageName) }
                )
            }
        }
    }
}

@Composable
private fun DrawerAppItem(
    app: DrawerAppUi,
    isFocused: Boolean,
    onClick: () -> Unit,
    onPinToggle: () -> Unit
) {
    val longPressState = rememberLongPressAnimationState(
        config = LongPressAnimationConfig(
            targetScale = 1.2f,
            tapThreshold = 1.05f,
            withFadeEffect = false,
        ),
    )

    Column(
        modifier = Modifier
            .focusProperties { canFocus = false }
            .then(
                if (isFocused) Modifier.border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(Dimens.radiusSm)
                ) else Modifier
            )
            .longPressGraphicsLayer(longPressState, applyAlpha = false)
            .longPressGesture(
                key = app.packageName,
                state = longPressState,
                onClick = onClick,
                onLongPress = onPinToggle,
            )
            .padding(Dimens.spacingXs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            AsyncImage(
                model = AppIconData(app.packageName),
                contentDescription = app.label,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(Dimens.radiusSm))
            )

            if (app.isPinned) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Pinned",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Dimens.spacingXs))

        Text(
            text = app.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
