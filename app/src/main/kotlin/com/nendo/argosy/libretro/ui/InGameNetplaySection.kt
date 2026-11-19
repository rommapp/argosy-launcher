package com.nendo.argosy.libretro.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.theme.reducedMotion
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.util.clickableNoFocus

enum class NetplaySectionAction(@StringRes val labelRes: Int, val icon: ImageVector) {
    OpenServer(R.string.ingame_menu_open_netplay_server, Icons.Filled.Groups),
    InviteFriend(R.string.ingame_menu_invite_friend, Icons.Filled.PersonAdd),
    OpenToAllFriends(R.string.ingame_menu_open_to_all_friends, Icons.Filled.LockOpen),
    CloseServer(R.string.ingame_menu_close_netplay_server, Icons.Filled.LinkOff),
    LeaveSession(R.string.ingame_menu_leave_netplay_session, Icons.Filled.LinkOff)
}

internal fun netplaySectionActions(
    isInSession: Boolean,
    role: NetplayMenuRole?,
    sessionIsReserved: Boolean
): List<NetplaySectionAction> = buildList {
    when {
        !isInSession -> add(NetplaySectionAction.OpenServer)
        role == NetplayMenuRole.Host -> {
            add(NetplaySectionAction.InviteFriend)
            if (sessionIsReserved) add(NetplaySectionAction.OpenToAllFriends)
            add(NetplaySectionAction.CloseServer)
        }
        else -> add(NetplaySectionAction.LeaveSession)
    }
}

/**
 * Netplay panel hosted in the in-game menu shell: a session status header followed by the
 * actions that apply to the current session. Up and down wrap through the actions, confirm
 * runs the focused one, back returns to the menu list, and left returns UNHANDLED for the
 * shell to close the section.
 */
@Composable
fun InGameNetplaySection(
    isInSession: Boolean,
    role: NetplayMenuRole?,
    sessionIsReserved: Boolean,
    peerConnected: Boolean,
    quality: NetplayQualityInfo?,
    focusedIndex: Int,
    onFocusChange: (Int) -> Unit,
    onAction: (NetplaySectionAction) -> Unit,
    onDismiss: () -> Unit
): InputHandler {
    val actions = remember(isInSession, role, sessionIsReserved) {
        netplaySectionActions(isInSession, role, sessionIsReserved)
    }

    LaunchedEffect(actions.size) {
        val clamped = focusedIndex.coerceIn(0, (actions.size - 1).coerceAtLeast(0))
        if (clamped != focusedIndex) onFocusChange(clamped)
    }

    val currentActions = rememberUpdatedState(actions)
    val currentFocusedIndex = rememberUpdatedState(focusedIndex)
    val currentOnFocusChange = rememberUpdatedState(onFocusChange)
    val currentOnAction = rememberUpdatedState(onAction)
    val currentOnDismiss = rememberUpdatedState(onDismiss)

    val inputHandler = remember {
        object : InputHandler {
            private fun move(delta: Int): InputResult {
                val count = currentActions.value.size
                if (count > 0) currentOnFocusChange.value((currentFocusedIndex.value + delta).mod(count))
                return InputResult.HANDLED
            }

            override fun onUp(): InputResult = move(-1)

            override fun onDown(): InputResult = move(1)

            override fun onConfirm(): InputResult {
                currentActions.value.getOrNull(currentFocusedIndex.value)?.let { currentOnAction.value(it) }
                return InputResult.HANDLED
            }

            override fun onBack(): InputResult {
                currentOnDismiss.value()
                return InputResult.HANDLED
            }
        }
    }

    val listState = rememberLazyListState()
    val reduced = reducedMotion()
    LaunchedEffect(focusedIndex, actions.size) {
        if (actions.isNotEmpty()) {
            listState.animateScrollToItemCentered(focusedIndex.coerceIn(0, actions.lastIndex), reduced)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusProperties { canFocus = false }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimens.spacingMd),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
        ) {
            NetplaySessionHeader(
                isInSession = isInSession,
                role = role,
                peerConnected = peerConnected,
                quality = quality
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(vertical = Dimens.spacingXs),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                itemsIndexed(actions, key = { _, action -> action.name }) { index, action ->
                    NetplayActionRow(
                        action = action,
                        isFocused = index == focusedIndex,
                        onClick = {
                            onFocusChange(index)
                            onAction(action)
                        }
                    )
                }
            }
        }
    }

    return inputHandler
}

@Composable
private fun NetplaySessionHeader(
    isInSession: Boolean,
    role: NetplayMenuRole?,
    peerConnected: Boolean,
    quality: NetplayQualityInfo?
) {
    val statusRes = when {
        !isInSession -> R.string.ingame_netplay_section_status_idle
        role == NetplayMenuRole.Host -> R.string.ingame_netplay_section_role_host
        else -> R.string.ingame_netplay_section_role_guest
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            Icon(
                imageVector = Icons.Filled.Groups,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Dimens.iconMd)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.ingame_netplay_section_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(statusRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (isInSession) {
            NetplayPeerCard(
                role = role,
                peerConnected = peerConnected,
                quality = quality
            )
        }
    }
}

@Composable
private fun NetplayPeerCard(
    role: NetplayMenuRole?,
    peerConnected: Boolean,
    quality: NetplayQualityInfo?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = PEER_CARD_FILL_ALPHA))
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        if (!peerConnected || quality == null) {
            val waitingRes = if (role == NetplayMenuRole.Host) {
                R.string.ingame_netplay_section_peer_waiting_guest
            } else {
                R.string.ingame_netplay_section_peer_waiting_host
            }
            Text(
                text = stringResource(waitingRes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }
        val peerText = if (quality.role == NetplayMenuRole.Host) {
            stringResource(R.string.ingame_netplay_quality_row_peer_guest, quality.peerDisplayName)
        } else {
            stringResource(R.string.ingame_netplay_quality_row_peer_host, quality.peerDisplayName)
        }
        val qualityLabel = stringResource(quality.label.labelRes)
        val pingText = quality.pingMs?.let {
            stringResource(R.string.ingame_netplay_quality_row_ping_known, it, qualityLabel)
        } ?: stringResource(R.string.ingame_netplay_quality_row_ping_unknown, qualityLabel)
        Text(
            text = peerText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = pingText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = qualityColor(quality.label)
        )
    }
}

@Composable
private fun qualityColor(label: NetplayQualityLabel): Color {
    val semantic = LocalLauncherTheme.current.semanticColors
    return when (label) {
        NetplayQualityLabel.Excellent, NetplayQualityLabel.Good -> semantic.success
        NetplayQualityLabel.Fair, NetplayQualityLabel.Poor -> semantic.warning
        NetplayQualityLabel.Bad -> MaterialTheme.colorScheme.error
    }
}

@Composable
private fun NetplayActionRow(
    action: NetplaySectionAction,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val background = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val content = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(background)
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(Dimens.iconMd)
        )
        Text(
            text = stringResource(action.labelRes),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private const val PEER_CARD_FILL_ALPHA = 0.6f
