package com.nendo.argosy.ui.screens.quaypass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.input.QuayPassDetailsInputHandler
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.theme.Dimens

@Composable
fun QuayPassDetailsScreen(
    onEditAvatar: () -> Unit,
    onClose: () -> Unit,
    viewModel: QuayPassDetailsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val inputDispatcher = LocalInputDispatcher.current
    var focusIndex by remember { mutableIntStateOf(0) }

    val buttons = buildButtons(state, onEditAvatar, onClose, viewModel::enableQuayPass)

    val handler = remember(buttons) {
        QuayPassDetailsInputHandler(
            getFocusIndex = { focusIndex },
            getMaxIndex = { (buttons.size - 1).coerceAtLeast(0) },
            onFocusChange = { focusIndex = it.coerceIn(0, buttons.size - 1) },
            onConfirmAt = { idx -> buttons.getOrNull(idx)?.action?.invoke() },
            onBack = onClose
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, handler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.subscribeView(handler, forRoute = Screen.QuayPassDetails.route)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(handler, forRoute = Screen.QuayPassDetails.route)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Meet QuayPass",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "Pass nearby Argosy travelers and trade Miis, greetings, " +
                    "and your most recent game. Bluetooth handles the meeting; " +
                    "your Mii and account live across devices.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            when {
                !state.avatarConfigured -> StatusLine(
                    "Build your Mii to enable QuayPass.",
                    MaterialTheme.colorScheme.onSurfaceVariant
                )
                state.enabled -> StatusLine(
                    "QuayPass is on. Encounters appear in the Plaza.",
                    MaterialTheme.colorScheme.primary
                )
                else -> StatusLine(
                    "Avatar set. You can enable QuayPass any time.",
                    MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                buttons.forEachIndexed { index, button ->
                    FocusableButton(
                        label = button.label,
                        isFocused = focusIndex == index,
                        isPrimary = button.isPrimary,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            focusIndex = index
                            button.action()
                        }
                    )
                }
            }
        }
    }
}

private data class DetailsButton(
    val label: String,
    val isPrimary: Boolean,
    val action: () -> Unit
)

private fun buildButtons(
    state: QuayPassDetailsState,
    onEditAvatar: () -> Unit,
    onClose: () -> Unit,
    onEnable: () -> Unit
): List<DetailsButton> = buildList {
    when {
        !state.avatarConfigured -> add(DetailsButton("Build my Mii", isPrimary = true, action = onEditAvatar))
        !state.enabled -> add(DetailsButton("Enable QuayPass", isPrimary = true, action = onEnable))
    }
    if (state.avatarConfigured) {
        add(DetailsButton("Edit avatar", isPrimary = false, action = onEditAvatar))
    }
    add(DetailsButton(if (state.enabled) "Done" else "Maybe later", isPrimary = false, action = onClose))
}

@Composable
private fun FocusableButton(
    label: String,
    isFocused: Boolean,
    isPrimary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val container = when {
        isFocused && isPrimary -> MaterialTheme.colorScheme.primary
        isFocused -> MaterialTheme.colorScheme.primaryContainer
        isPrimary -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
        else -> Color.Transparent
    }
    val content = when {
        isFocused && isPrimary -> MaterialTheme.colorScheme.onPrimary
        isFocused -> MaterialTheme.colorScheme.onPrimaryContainer
        isPrimary -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        shape = RoundedCornerShape(Dimens.radiusMd)
    ) { Text(label) }
}

@Composable
private fun StatusLine(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = color
    )
}
