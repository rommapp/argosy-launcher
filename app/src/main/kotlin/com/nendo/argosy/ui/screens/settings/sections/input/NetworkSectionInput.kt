package com.nendo.argosy.ui.screens.settings.sections.input

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.components.ListSection
import com.nendo.argosy.ui.components.connections.QuickConnectionsController
import com.nendo.argosy.ui.components.connections.QuickConnectionsInputHandler
import com.nendo.argosy.ui.components.connections.quickConnectionsFocusIndex
import com.nendo.argosy.ui.components.connections.quickConnectionsFocusableRows
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.settings.nextSectionFocus
import com.nendo.argosy.ui.screens.settings.prevSectionFocus
import com.nendo.argosy.ui.screens.settings.sections.networkSettingsSections

internal class NetworkSectionInput(
    private val connections: QuickConnectionsInputHandler,
    private val controller: QuickConnectionsController
) : InputHandler {

    override fun onUp(): InputResult = claimed(connections.onUp())

    override fun onDown(): InputResult = claimed(connections.onDown())

    override fun onLeft(): InputResult = claimed(connections.onLeft())

    override fun onRight(): InputResult = claimed(connections.onRight())

    override fun onConfirm(): InputResult = claimed(connections.onConfirm())

    override fun onContextMenu(): InputResult = connections.onContextMenu()

    override fun onPrevTrigger(): InputResult = connections.onPrevTrigger()

    override fun onNextTrigger(): InputResult = connections.onNextTrigger()

    override fun onPrevSection(): InputResult = jump(::prevSectionFocus)

    override fun onNextSection(): InputResult = jump(::nextSectionFocus)

    private fun claimed(result: InputResult): InputResult =
        if (result.handled) result else InputResult.handled(SoundType.SILENT)

    private fun jump(target: (List<ListSection>, Int) -> Int?): InputResult {
        val state = controller.state.value
        val focus = target(networkSettingsSections(state), quickConnectionsFocusIndex(state))
            ?: return InputResult.UNHANDLED
        if (focus !in quickConnectionsFocusableRows(state).indices) return InputResult.UNHANDLED
        controller.setFocus(focus)
        return InputResult.HANDLED
    }
}
