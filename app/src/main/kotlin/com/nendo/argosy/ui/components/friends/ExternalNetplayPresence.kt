package com.nendo.argosy.ui.components.friends

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.netplay.external.ExternalNetplay
import com.nendo.argosy.data.social.PresenceGameInfo
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ColorTokens

/**
 * The external netplay a friend's presence shows. An Argosy-hosted session outranks it, so a game
 * carrying both reports none here.
 */
val PresenceGameInfo.shownExternalNetplay: ExternalNetplay?
    get() = externalNetplay.takeIf { netplaySession == null }

val ExternalNetplay.emulatorName: String?
    get() = EmulatorRegistry.getById(emulatorId)?.displayName

@Composable
fun presenceNetplayColor(): Color =
    if (LocalArgosyTheme.current.isDark) ColorTokens.Domain.Presence.Netplay.dark else ColorTokens.Domain.Presence.Netplay.light
