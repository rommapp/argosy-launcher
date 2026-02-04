package com.nendo.argosy.libretro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import com.nendo.argosy.libretro.shader.ShaderChainManager
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.sections.ShaderStackSection
import com.nendo.argosy.ui.theme.Dimens

@Composable
fun InGameShaderChainScreen(
    manager: ShaderChainManager,
    onDismiss: () -> Unit
): InputHandler {
    val isDarkTheme = isSystemInDarkTheme()
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f)
                       else Color.White.copy(alpha = 0.5f)

    val currentOnDismiss = rememberUpdatedState(onDismiss)

    val inputHandler = remember {
        object : InputHandler {
            override fun onUp(): InputResult {
                if (manager.shaderStack.showShaderPicker) {
                    manager.moveShaderPickerFocus(-1)
                } else {
                    manager.moveShaderParamFocus(-1)
                }
                return InputResult.HANDLED
            }

            override fun onDown(): InputResult {
                if (manager.shaderStack.showShaderPicker) {
                    manager.moveShaderPickerFocus(1)
                } else {
                    manager.moveShaderParamFocus(1)
                }
                return InputResult.HANDLED
            }

            override fun onLeft(): InputResult {
                if (!manager.shaderStack.showShaderPicker) {
                    manager.adjustShaderParam(-1)
                }
                return InputResult.HANDLED
            }

            override fun onRight(): InputResult {
                if (!manager.shaderStack.showShaderPicker) {
                    manager.adjustShaderParam(1)
                }
                return InputResult.HANDLED
            }

            override fun onConfirm(): InputResult {
                if (manager.shaderStack.showShaderPicker) {
                    manager.confirmShaderPickerSelection()
                } else {
                    manager.resetShaderParam()
                }
                return InputResult.HANDLED
            }

            override fun onBack(): InputResult {
                if (manager.shaderStack.showShaderPicker) {
                    manager.dismissShaderPicker()
                } else {
                    currentOnDismiss.value()
                }
                return InputResult.HANDLED
            }

            override fun onPrevSection(): InputResult {
                if (!manager.shaderStack.showShaderPicker) {
                    manager.cycleShaderTab(-1)
                }
                return InputResult.HANDLED
            }

            override fun onNextSection(): InputResult {
                if (!manager.shaderStack.showShaderPicker) {
                    manager.cycleShaderTab(1)
                }
                return InputResult.HANDLED
            }

            override fun onMenu(): InputResult {
                if (!manager.shaderStack.showShaderPicker) {
                    manager.showShaderPicker()
                }
                return InputResult.HANDLED
            }

            override fun onSecondaryAction(): InputResult {
                if (!manager.shaderStack.showShaderPicker) {
                    manager.removeShaderFromStack()
                }
                return InputResult.HANDLED
            }

            override fun onPrevTrigger(): InputResult {
                if (!manager.shaderStack.showShaderPicker) {
                    manager.reorderShaderInStack(-1)
                }
                return InputResult.HANDLED
            }

            override fun onNextTrigger(): InputResult {
                if (!manager.shaderStack.showShaderPicker) {
                    manager.reorderShaderInStack(1)
                }
                return InputResult.HANDLED
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor)
            .focusProperties { canFocus = false },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimens.spacingLg)
                .focusProperties { canFocus = false },
            shape = RoundedCornerShape(Dimens.radiusLg),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .focusProperties { canFocus = false }
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .focusProperties { canFocus = false }
                ) {
                    ShaderStackSection(manager = manager)
                }

                FooterBar(
                    hints = buildShaderChainFooterHints(manager.shaderStack.showShaderPicker),
                    onHintClick = { button ->
                        when (button) {
                            InputButton.B -> currentOnDismiss.value()
                            InputButton.START -> manager.showShaderPicker()
                            InputButton.X -> manager.removeShaderFromStack()
                            else -> {}
                        }
                    }
                )
            }
        }
    }

    return inputHandler
}

private fun buildShaderChainFooterHints(
    pickerOpen: Boolean
): List<Pair<InputButton, String>> {
    return buildList {
        if (pickerOpen) {
            add(InputButton.DPAD_VERTICAL to "Browse")
            add(InputButton.A to "Select")
            add(InputButton.B to "Cancel")
        } else {
            add(InputButton.LB_RB to "Shader")
            add(InputButton.DPAD_HORIZONTAL to "Adjust")
            add(InputButton.A to "Reset")
            add(InputButton.START to "Add")
            add(InputButton.X to "Remove")
            add(InputButton.LT_RT to "Reorder")
            add(InputButton.B to "Back")
        }
    }
}
