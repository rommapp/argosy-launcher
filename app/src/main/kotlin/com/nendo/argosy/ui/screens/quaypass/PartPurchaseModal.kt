package com.nendo.argosy.ui.screens.quaypass

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.NestedModal
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.LocalInputDispatcher

@Composable
fun PartPurchaseModal(
    request: PurchaseRequest,
    balance: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val inputDispatcher = LocalInputDispatcher.current

    val inputHandler = remember(onConfirm, onDismiss) {
        object : InputHandler {
            override fun onConfirm(): InputResult {
                onConfirm()
                return InputResult.HANDLED
            }

            override fun onBack(): InputResult {
                onDismiss()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.pushModal(inputHandler)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.pushModal(inputHandler)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            inputDispatcher.popModal()
        }
    }

    NestedModal(
        title = "Unlock Part",
        onDismiss = onDismiss,
        footerHints = listOf(
            InputButton.A to "Confirm",
            InputButton.B to "Cancel"
        ),
        content = {
            Text(
                text = "Unlock this part for ${request.cost} tickets?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Balance: $balance -> ${balance - request.cost}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}
