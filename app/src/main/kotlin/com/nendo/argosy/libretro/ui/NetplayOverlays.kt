package com.nendo.argosy.libretro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.nendo.argosy.data.social.NetplaySessionMode
import androidx.compose.foundation.layout.width
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.theme.gripReserveBottomInset
import com.nendo.argosy.ui.util.clickableNoFocus

enum class NetplayProgressStage { RequestingJoin, WaitingForHost, Connecting, Measuring, LoadingState, Ready, Failed }

data class NetplayProgressState(
    val stage: NetplayProgressStage,
    val message: String? = null
)

@Composable
fun NetplayConnectionProgressOverlay(
    state: NetplayProgressState,
    onDismiss: () -> Unit
): InputHandler {
    val currentOnDismiss = rememberUpdatedState(onDismiss)
    val label = when (state.stage) {
        NetplayProgressStage.RequestingJoin ->
            stringResource(R.string.ingame_netplay_progress_requesting_join)
        NetplayProgressStage.WaitingForHost ->
            stringResource(R.string.ingame_netplay_progress_waiting_for_host)
        NetplayProgressStage.Connecting ->
            stringResource(R.string.ingame_netplay_progress_connecting)
        NetplayProgressStage.Measuring ->
            stringResource(R.string.ingame_netplay_progress_measuring)
        NetplayProgressStage.LoadingState ->
            stringResource(R.string.ingame_netplay_progress_loading_state)
        NetplayProgressStage.Ready ->
            state.message ?: stringResource(R.string.ingame_netplay_progress_ready)
        NetplayProgressStage.Failed ->
            state.message ?: stringResource(R.string.ingame_netplay_progress_failed)
    }
    val isTerminal = state.stage == NetplayProgressStage.Failed

    val inputHandler = remember(isTerminal) {
        object : InputHandler {
            override fun onConfirm(): InputResult {
                if (isTerminal) currentOnDismiss.value()
                return InputResult.HANDLED
            }
            override fun onBack(): InputResult {
                currentOnDismiss.value()
                return InputResult.HANDLED
            }
        }
    }

    NetplayScrim {
        Surface(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .padding(32.dp)
                .focusProperties { canFocus = false },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.ingame_netplay_progress_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!isTerminal && state.stage != NetplayProgressStage.Ready) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isTerminal) {
                    NetplayPromptButton(
                        text = stringResource(R.string.ingame_netplay_progress_dismiss),
                        isFocused = true,
                        onClick = { currentOnDismiss.value() }
                    )
                }
            }
        }
    }

    return inputHandler
}

@Composable
fun NetplayReconnectingOverlay(lastRttMs: Int? = null) {
    val detail = if (lastRttMs != null) {
        stringResource(R.string.ingame_netplay_reconnecting_with_ping, lastRttMs)
    } else {
        stringResource(R.string.ingame_netplay_reconnecting)
    }
    NetplayScrim {
        Surface(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .padding(32.dp)
                .focusProperties { canFocus = false },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun NetplayHostDisconnectPrompt(
    peerDisplayName: String,
    focusedIndex: Int,
    onFocusChange: (Int) -> Unit,
    onKeepOpen: () -> Unit,
    onCloseAndEnd: () -> Unit
): InputHandler {
    val currentFocus = rememberUpdatedState(focusedIndex)
    val currentOnFocusChange = rememberUpdatedState(onFocusChange)
    val currentOnKeepOpen = rememberUpdatedState(onKeepOpen)
    val currentOnClose = rememberUpdatedState(onCloseAndEnd)

    val inputHandler = remember {
        object : InputHandler {
            override fun onLeft(): InputResult {
                if (currentFocus.value != 0) currentOnFocusChange.value(0)
                return InputResult.HANDLED
            }
            override fun onRight(): InputResult {
                if (currentFocus.value != 1) currentOnFocusChange.value(1)
                return InputResult.HANDLED
            }
            override fun onConfirm(): InputResult {
                if (currentFocus.value == 0) currentOnKeepOpen.value() else currentOnClose.value()
                return InputResult.HANDLED
            }
            override fun onBack(): InputResult {
                currentOnKeepOpen.value()
                return InputResult.HANDLED
            }
        }
    }

    NetplayScrim {
        Surface(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .padding(32.dp)
                .focusProperties { canFocus = false },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.ingame_netplay_disconnect_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(
                        R.string.ingame_netplay_disconnect_message,
                        peerDisplayName
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NetplayPromptButton(
                        text = stringResource(R.string.ingame_netplay_disconnect_keep_open),
                        isFocused = focusedIndex == 0,
                        onClick = onKeepOpen,
                        modifier = Modifier.weight(1f)
                    )
                    NetplayPromptButton(
                        text = stringResource(R.string.ingame_netplay_disconnect_close),
                        isFocused = focusedIndex == 1,
                        onClick = onCloseAndEnd,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    return inputHandler
}

@Composable
fun NetplayQualityWarningPrompt(
    rttMs: Int,
    jitterMs: Int,
    ratingLabel: String,
    focusedIndex: Int,
    onFocusChange: (Int) -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit
): InputHandler {
    val currentFocus = rememberUpdatedState(focusedIndex)
    val currentOnFocusChange = rememberUpdatedState(onFocusChange)
    val currentOnAccept = rememberUpdatedState(onAccept)
    val currentOnDecline = rememberUpdatedState(onDecline)

    val inputHandler = remember {
        object : InputHandler {
            override fun onLeft(): InputResult {
                if (currentFocus.value != 0) currentOnFocusChange.value(0)
                return InputResult.HANDLED
            }
            override fun onRight(): InputResult {
                if (currentFocus.value != 1) currentOnFocusChange.value(1)
                return InputResult.HANDLED
            }
            override fun onConfirm(): InputResult {
                if (currentFocus.value == 0) currentOnAccept.value() else currentOnDecline.value()
                return InputResult.HANDLED
            }
            override fun onBack(): InputResult {
                currentOnDecline.value()
                return InputResult.HANDLED
            }
        }
    }

    NetplayScrim {
        Surface(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .padding(32.dp)
                .focusProperties { canFocus = false },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.ingame_netplay_quality_warning_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(
                        R.string.ingame_netplay_quality_warning_message,
                        ratingLabel,
                        rttMs,
                        jitterMs
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NetplayPromptButton(
                        text = stringResource(R.string.ingame_netplay_quality_warning_accept),
                        isFocused = focusedIndex == 0,
                        onClick = onAccept,
                        modifier = Modifier.weight(1f)
                    )
                    NetplayPromptButton(
                        text = stringResource(R.string.ingame_netplay_quality_warning_decline),
                        isFocused = focusedIndex == 1,
                        onClick = onDecline,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    return inputHandler
}

data class NetplayFriendPickerEntry(
    val userId: String,
    val displayName: String,
    val avatarColorHex: String,
    val isOnline: Boolean
)

@Composable
fun NetplayFriendPickerDialog(
    friends: List<NetplayFriendPickerEntry>,
    focusedIndex: Int,
    onFocusChange: (Int) -> Unit,
    onSelect: (NetplayFriendPickerEntry) -> Unit,
    onDismiss: () -> Unit
): InputHandler {
    val currentFocus = rememberUpdatedState(focusedIndex)
    val currentOnFocusChange = rememberUpdatedState(onFocusChange)
    val currentOnSelect = rememberUpdatedState(onSelect)
    val currentOnDismiss = rememberUpdatedState(onDismiss)

    val inputHandler = remember(friends.size) {
        object : InputHandler {
            override fun onUp(): InputResult {
                if (friends.isEmpty()) return InputResult.HANDLED
                val next = (currentFocus.value - 1).mod(friends.size)
                currentOnFocusChange.value(next)
                return InputResult.HANDLED
            }
            override fun onDown(): InputResult {
                if (friends.isEmpty()) return InputResult.HANDLED
                val next = (currentFocus.value + 1).mod(friends.size)
                currentOnFocusChange.value(next)
                return InputResult.HANDLED
            }
            override fun onConfirm(): InputResult {
                friends.getOrNull(currentFocus.value)?.let { currentOnSelect.value(it) }
                return InputResult.HANDLED
            }
            override fun onBack(): InputResult {
                currentOnDismiss.value()
                return InputResult.HANDLED
            }
        }
    }

    NetplayScrim {
        Surface(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .heightIn(max = 480.dp)
                .padding(32.dp)
                .focusProperties { canFocus = false },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.ingame_netplay_friend_picker_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (friends.isEmpty()) {
                    Text(
                        text = stringResource(R.string.ingame_netplay_friend_picker_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    NetplayPromptButton(
                        text = stringResource(R.string.ingame_netplay_friend_picker_close),
                        isFocused = true,
                        onClick = onDismiss
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(friends, key = { it.userId }) { friend ->
                            val index = friends.indexOf(friend)
                            FriendPickerRow(
                                friend = friend,
                                isFocused = index == focusedIndex,
                                onClick = { onSelect(friend) }
                            )
                        }
                    }
                }
            }
        }
    }

    return inputHandler
}

@Composable
private fun FriendPickerRow(
    friend: NetplayFriendPickerEntry,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val background = if (isFocused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val avatarColor = runCatching {
        Color(android.graphics.Color.parseColor(friend.avatarColorHex))
    }.getOrDefault(Color(0xFF6366F1))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(avatarColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = friend.displayName.take(1).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = friend.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (friend.isOnline) {
                    stringResource(R.string.ingame_netplay_friend_online)
                } else {
                    stringResource(R.string.ingame_netplay_friend_offline)
                },
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun NetplayScrim(content: @Composable () -> Unit) {
    val isDarkTheme = isSystemInDarkTheme()
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor)
            .padding(bottom = gripReserveBottomInset())
            .focusProperties { canFocus = false },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun NetplayPromptButton(
    text: String,
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isFocused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickableNoFocus(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun NetplayModePickerDialog(
    focusedIndex: Int,
    onFocusChange: (Int) -> Unit,
    onSelectOpen: () -> Unit,
    onSelectPrivate: () -> Unit,
    onSelectInvite: () -> Unit,
    onDismiss: () -> Unit
): InputHandler {
    val currentFocus = rememberUpdatedState(focusedIndex)
    val currentOnFocusChange = rememberUpdatedState(onFocusChange)
    val currentOnSelectOpen = rememberUpdatedState(onSelectOpen)
    val currentOnSelectPrivate = rememberUpdatedState(onSelectPrivate)
    val currentOnSelectInvite = rememberUpdatedState(onSelectInvite)
    val currentOnDismiss = rememberUpdatedState(onDismiss)

    data class ModeOption(val label: String, val description: String, val onSelect: () -> Unit)
    val openLabel = stringResource(R.string.ingame_netplay_mode_open_label)
    val openDescription = stringResource(R.string.ingame_netplay_mode_open_description)
    val privateLabel = stringResource(R.string.ingame_netplay_mode_private_label)
    val privateDescription = stringResource(R.string.ingame_netplay_mode_private_description)
    val inviteLabel = stringResource(R.string.ingame_netplay_mode_invite_label)
    val inviteDescription = stringResource(R.string.ingame_netplay_mode_invite_description)
    val options = remember(
        openLabel,
        openDescription,
        privateLabel,
        privateDescription,
        inviteLabel,
        inviteDescription
    ) {
        listOf(
            ModeOption(openLabel, openDescription) { currentOnSelectOpen.value() },
            ModeOption(privateLabel, privateDescription) { currentOnSelectPrivate.value() },
            ModeOption(inviteLabel, inviteDescription) { currentOnSelectInvite.value() }
        )
    }

    val inputHandler = remember {
        object : InputHandler {
            override fun onUp(): InputResult {
                val next = (currentFocus.value - 1).mod(options.size)
                currentOnFocusChange.value(next)
                return InputResult.HANDLED
            }
            override fun onDown(): InputResult {
                val next = (currentFocus.value + 1).mod(options.size)
                currentOnFocusChange.value(next)
                return InputResult.HANDLED
            }
            override fun onConfirm(): InputResult {
                options.getOrNull(currentFocus.value)?.onSelect?.invoke()
                return InputResult.HANDLED
            }
            override fun onBack(): InputResult {
                currentOnDismiss.value()
                return InputResult.HANDLED
            }
        }
    }

    NetplayScrim {
        Surface(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .padding(32.dp)
                .focusProperties { canFocus = false },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.ingame_netplay_mode_picker_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    options.forEachIndexed { index, option ->
                        ModePickerRow(
                            label = option.label,
                            description = option.description,
                            isFocused = index == focusedIndex,
                            onClick = option.onSelect
                        )
                    }
                }
            }
        }
    }

    return inputHandler
}

@Composable
private fun ModePickerRow(
    label: String,
    description: String,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val background = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isFocused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val descColor = if (isFocused) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .clickableNoFocus(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = description,
            color = descColor,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun NetplayJoinRequestDialog(
    username: String,
    focusedIndex: Int,
    onFocusChange: (Int) -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit
): InputHandler {
    val currentFocus = rememberUpdatedState(focusedIndex)
    val currentOnFocusChange = rememberUpdatedState(onFocusChange)
    val currentOnAccept = rememberUpdatedState(onAccept)
    val currentOnDecline = rememberUpdatedState(onDecline)

    val inputHandler = remember {
        object : InputHandler {
            override fun onLeft(): InputResult {
                if (currentFocus.value != 0) currentOnFocusChange.value(0)
                return InputResult.HANDLED
            }
            override fun onRight(): InputResult {
                if (currentFocus.value != 1) currentOnFocusChange.value(1)
                return InputResult.HANDLED
            }
            override fun onConfirm(): InputResult {
                if (currentFocus.value == 0) currentOnAccept.value() else currentOnDecline.value()
                return InputResult.HANDLED
            }
            override fun onBack(): InputResult {
                currentOnDecline.value()
                return InputResult.HANDLED
            }
        }
    }

    NetplayScrim {
        Surface(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .padding(32.dp)
                .focusProperties { canFocus = false },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.ingame_netplay_join_request_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.ingame_netplay_join_request_message, username),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NetplayPromptButton(
                        text = stringResource(R.string.ingame_netplay_join_request_accept),
                        isFocused = focusedIndex == 0,
                        onClick = onAccept,
                        modifier = Modifier.weight(1f)
                    )
                    NetplayPromptButton(
                        text = stringResource(R.string.ingame_netplay_join_request_decline),
                        isFocused = focusedIndex == 1,
                        onClick = onDecline,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    return inputHandler
}

fun mapSessionStateToProgress(
    stateName: String,
    errorMessage: String? = null
): NetplayProgressState? {
    return when (stateName) {
        "Opening" -> NetplayProgressState(NetplayProgressStage.RequestingJoin)
        "Handshaking" -> NetplayProgressState(NetplayProgressStage.Connecting)
        "Connected" -> NetplayProgressState(NetplayProgressStage.Ready)
        "Error" -> NetplayProgressState(NetplayProgressStage.Failed, errorMessage ?: "Connection failed")
        else -> null
    }
}

@Composable
fun NetplayBorderHud(
    gameTitle: String,
    sessionMode: NetplaySessionMode,
    playerCount: Int,
    averagePingMs: Int?,
    hostUsername: String,
    guestUsername: String?,
    hostAvatarColor: String?,
    guestAvatarColor: String?,
    observers: List<Pair<String, String?>> = emptyList(),
    modifier: Modifier = Modifier
) {
    val nameColor = Color.White.copy(alpha = 0.6f)
    val statusColor = Color.White.copy(alpha = 0.4f)
    val tagColor = Color.White.copy(alpha = 0.35f)
    val headerColor = Color.White.copy(alpha = 0.35f)
    val accentColor = MaterialTheme.colorScheme.primary
    val titleStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 11.sp,
        lineHeight = 15.sp
    )
    val nameStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.sp,
        lineHeight = 14.sp
    )
    val statusStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 9.sp,
        lineHeight = 13.sp
    )
    val pingStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 9.sp,
        lineHeight = 13.sp,
        fontFamily = FontFamily.Monospace
    )
    val sectionHeaderStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 9.sp,
        lineHeight = 13.sp
    )

    val hostDotColor = parseAvatarColor(hostAvatarColor)
    val guestDotColor = parseAvatarColor(guestAvatarColor)

    val modeLabel = when (sessionMode) {
        NetplaySessionMode.OPEN -> stringResource(R.string.ingame_netplay_hud_mode_open)
        NetplaySessionMode.PRIVATE -> stringResource(R.string.ingame_netplay_hud_mode_private)
        NetplaySessionMode.INVITE_ONLY ->
            stringResource(R.string.ingame_netplay_hud_mode_invite_only)
    }

    val pingColor = when {
        averagePingMs == null -> statusColor
        averagePingMs < 80 -> Color(0xFF22C55E)
        averagePingMs < 150 -> Color(0xFFFBBF24)
        averagePingMs < 200 -> Color(0xFFF97316)
        averagePingMs < 300 -> Color(0xFFEF4444)
        else -> Color(0xFFDC2626)
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.35f))
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .heightIn(min = 36.dp)
                .background(accentColor)
        )
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = gameTitle,
                style = titleStyle,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = modeLabel,
                    style = statusStyle,
                    color = statusColor,
                    maxLines = 1
                )
                if (averagePingMs != null) {
                    Text(text = " | ", style = statusStyle, color = statusColor)
                    Text(
                        text = "${averagePingMs}ms",
                        style = pingStyle,
                        color = pingColor,
                        maxLines = 1
                    )
                }
            }
            Text(
                text = pluralStringResource(
                    R.plurals.ingame_netplay_hud_players,
                    playerCount,
                    playerCount
                ),
                style = statusStyle,
                color = tagColor,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.ingame_netplay_hud_player1_header),
                style = sectionHeaderStyle,
                color = headerColor
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(hostDotColor)
                )
                Text(
                    text = hostUsername,
                    style = nameStyle,
                    color = nameColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = stringResource(R.string.ingame_netplay_hud_host_tag),
                    style = nameStyle,
                    color = accentColor
                )
            }
            if (guestUsername != null && playerCount >= 2) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.ingame_netplay_hud_player2_header),
                    style = sectionHeaderStyle,
                    color = headerColor
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(guestDotColor)
                    )
                    Text(
                        text = guestUsername,
                        style = nameStyle,
                        color = nameColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
            if (observers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.ingame_netplay_hud_observers),
                    style = sectionHeaderStyle,
                    color = headerColor
                )
                for ((name, avatarColor) in observers) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(parseAvatarColor(avatarColor))
                        )
                        Text(
                            text = name,
                            style = nameStyle,
                            color = nameColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }
        }
    }
}

private fun parseAvatarColor(hex: String?): Color {
    if (hex == null) return Color(0xFF6366F1)
    return runCatching {
        Color(android.graphics.Color.parseColor(hex))
    }.getOrDefault(Color(0xFF6366F1))
}
