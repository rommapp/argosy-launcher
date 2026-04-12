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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
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
        NetplayProgressStage.RequestingJoin -> "Requesting join..."
        NetplayProgressStage.WaitingForHost -> "Waiting for host to accept..."
        NetplayProgressStage.Connecting -> "Connecting..."
        NetplayProgressStage.Measuring -> "Measuring connection..."
        NetplayProgressStage.LoadingState -> "Loading game state..."
        NetplayProgressStage.Ready -> "Ready!"
        NetplayProgressStage.Failed -> state.message ?: "Couldn't connect"
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
                    text = "Netplay",
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
                        text = "OK",
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
fun NetplayReconnectingOverlay() {
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
                    text = "Reconnecting...",
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
                    text = "Friend Disconnected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$peerDisplayName lost connection. Keep the session open for them to rejoin, or close and exit?",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NetplayPromptButton(
                        text = "Keep Open",
                        isFocused = focusedIndex == 0,
                        onClick = onKeepOpen,
                        modifier = Modifier.weight(1f)
                    )
                    NetplayPromptButton(
                        text = "Close and End Game",
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
                    text = "Connection Warning",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Connection quality is $ratingLabel (${rttMs}ms ping, ${jitterMs}ms jitter). Gameplay may be choppy.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NetplayPromptButton(
                        text = "Continue Anyway",
                        isFocused = focusedIndex == 0,
                        onClick = onAccept,
                        modifier = Modifier.weight(1f)
                    )
                    NetplayPromptButton(
                        text = "Cancel",
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
                    text = "Invite Friend",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (friends.isEmpty()) {
                    Text(
                        text = "No online friends available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    NetplayPromptButton(
                        text = "Close",
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
                text = if (friend.isOnline) "Online" else "Offline",
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
