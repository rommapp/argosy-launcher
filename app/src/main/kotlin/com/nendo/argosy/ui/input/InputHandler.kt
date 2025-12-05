package com.nendo.argosy.ui.input

interface InputHandler {
    fun onUp(): Boolean
    fun onDown(): Boolean
    fun onLeft(): Boolean
    fun onRight(): Boolean
    fun onConfirm(): Boolean
    fun onBack(): Boolean
    fun onMenu(): Boolean
    fun onSecondaryAction(): Boolean = false
    fun onContextMenu(): Boolean = false
    fun onPrevSection(): Boolean = false
    fun onNextSection(): Boolean = false
    fun onSelect(): Boolean = false
}
