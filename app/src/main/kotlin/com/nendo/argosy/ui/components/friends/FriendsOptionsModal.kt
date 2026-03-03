package com.nendo.argosy.ui.components.friends

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem

enum class FriendsOption {
    ADD_FRIEND,
    SHOW_CODE
}

@Composable
fun FriendsOptionsModal(
    onSelectOption: (FriendsOption) -> Unit,
    onDismiss: () -> Unit
) {
    val inputDispatcher = LocalInputDispatcher.current
    val focusIndex = remember { mutableIntStateOf(0) }

    val inputHandler = remember(onSelectOption, onDismiss) {
        object : InputHandler {
            override fun onUp(): InputResult {
                if (focusIndex.intValue > 0) {
                    focusIndex.intValue--
                    return InputResult.HANDLED
                }
                return InputResult.UNHANDLED
            }

            override fun onDown(): InputResult {
                if (focusIndex.intValue < FriendsOption.entries.lastIndex) {
                    focusIndex.intValue++
                    return InputResult.HANDLED
                }
                return InputResult.UNHANDLED
            }

            override fun onConfirm(): InputResult {
                onSelectOption(FriendsOption.entries[focusIndex.intValue])
                return InputResult.HANDLED
            }

            override fun onBack(): InputResult {
                onDismiss()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }

            override fun onSecondaryAction(): InputResult {
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
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            inputDispatcher.popModal()
        }
    }

    Modal(
        title = "Friends",
        onDismiss = onDismiss,
        footerHints = listOf(
            InputButton.A to "Select",
            InputButton.B to "Close"
        ),
        content = {
            OptionItem(
                label = "Add Friend by Code",
                icon = Icons.Default.PersonAdd,
                isFocused = focusIndex.intValue == 0,
                onClick = { onSelectOption(FriendsOption.ADD_FRIEND) }
            )
            OptionItem(
                label = "Show My Friend Code",
                icon = Icons.Default.QrCode,
                isFocused = focusIndex.intValue == 1,
                onClick = { onSelectOption(FriendsOption.SHOW_CODE) }
            )
        }
    )
}
