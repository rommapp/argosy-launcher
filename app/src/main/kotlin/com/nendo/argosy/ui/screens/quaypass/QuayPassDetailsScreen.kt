package com.nendo.argosy.ui.screens.quaypass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
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
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Meet QuayPass",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Pass other Argosy travelers when you're nearby. " +
                        "Share a Mii, a greeting, and the last game you played. " +
                        "Encounters appear in the Plaza.",
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(Modifier.height(8.dp))
                FeatureBullet("Hands-free", "QuayPass works in the background. No notification, no popup, no interaction needed.")
                FeatureBullet("Private by default", "Your name, greeting, and recent game travel locally over Bluetooth. Nothing is uploaded.")
                FeatureBullet("Yours to design", "Build a Mii once. It travels with you across devices.")

                Spacer(Modifier.height(24.dp))

                if (!state.avatarConfigured) {
                    Text(
                        text = "Step 1: Build your Mii",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "QuayPass requires you to create an avatar before it can be enabled.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (state.enabled) {
                    Text(
                        text = "QuayPass is on. Find encounters in the Plaza.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.height(8.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    buttons.forEachIndexed { index, button ->
                        FocusableButton(
                            label = button.label,
                            isFocused = focusIndex == index,
                            isPrimary = button.isPrimary,
                            onClick = {
                                focusIndex = index
                                button.action()
                            }
                        )
                    }
                }
            }

            FooterBar(
                hints = listOf(
                    InputButton.B to "Back",
                    InputButton.DPAD_VERTICAL to "Move",
                    InputButton.A to "Select"
                )
            )
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
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        shape = RoundedCornerShape(Dimens.radiusMd)
    ) { Text(label) }
}

@Composable
private fun FeatureBullet(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
