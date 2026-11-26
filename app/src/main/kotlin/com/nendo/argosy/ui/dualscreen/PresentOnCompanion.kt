package com.nendo.argosy.ui.dualscreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nendo.argosy.DualScreenManagerHolder

/**
 * Publishes [slot] on the presentation screen for as long as this composable is on screen, and
 * releases it on the way out.
 *
 * Tying publication to composition is what keeps the two screens in step: a screen cannot be shown
 * without publishing or left without releasing, whichever way it is entered or left, and a screen
 * returned to republishes from the state its own ViewModel still holds.
 */
@Composable
fun PresentOnCompanion(owner: SlotOwner, slot: PresentationSlot) {
    val manager = DualScreenManagerHolder.instance ?: return
    LaunchedEffect(owner, slot) { manager.presentSlot(owner, slot) }
    DisposableEffect(owner) { onDispose { manager.releaseSlot(owner) } }
}

/**
 * [PresentOnCompanion] for a slot whose content changes while the screen stays open, such as a
 * playback position. The slot takes the top when the composable enters; later changes replace it
 * where it stands and leave any overlay published above it on top.
 */
@Composable
fun PresentOnCompanionLive(owner: SlotOwner, slot: PresentationSlot) {
    val manager = DualScreenManagerHolder.instance ?: return
    DisposableEffect(owner) {
        manager.presentSlot(owner, slot)
        onDispose { manager.releaseSlot(owner) }
    }
    LaunchedEffect(owner, slot) { manager.refreshSlot(owner, slot) }
}

/**
 * Whether the presentation screen is up and free to describe the control screen: neither a live
 * game nor media playback holds it.
 */
fun presentationTakesSlots(presentationShowsHints: Boolean, shownSlot: PresentationSlot): Boolean =
    presentationShowsHints &&
        shownSlot !is PresentationSlot.InGame &&
        shownSlot !is PresentationSlot.MediaPlayback

/**
 * [presentationTakesSlots] for a composable, recomposing when either input changes.
 */
@Composable
fun presentationTakesSlotsState(): Boolean {
    val manager = DualScreenManagerHolder.instance ?: return false
    val presentationUp by manager.presentationShowsHints.collectAsState()
    val shownSlot by manager.presentationSlot.collectAsState()
    return presentationTakesSlots(presentationUp, shownSlot)
}
