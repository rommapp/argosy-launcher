package com.nendo.argosy.ui.screens.quaypass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nendo.argosy.data.local.entity.QuayPassEncounterEntity
import com.nendo.argosy.data.quaypass.QuayPassService
import com.nendo.argosy.data.quaypass.ble.QuayPassDoodleCodec
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.friends.SocialAvatar
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.quaypass.QuayPassIcons
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.screens.doodle.CanvasSize
import com.nendo.argosy.ui.screens.doodle.DecodedDoodle
import com.nendo.argosy.ui.screens.doodle.DoodleColor
import com.nendo.argosy.ui.screens.doodle.DoodlePreview
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.generated.MotionTokens
import com.nendo.argosy.ui.util.clickableNoFocus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun QuayPassCheckInScreen(
    viewModel: QuayPassCheckInViewModel = hiltViewModel()
) {
    val encounters by viewModel.encounters.collectAsState()
    val serviceState by viewModel.serviceState.collectAsState()
    val running = serviceState == QuayPassService.QuayPassRunState.RUNNING
    val ticketBalance by viewModel.ticketBalance.collectAsState()
    val greeting by viewModel.greeting.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val inputDispatcher = LocalInputDispatcher.current

    DisposableEffect(Unit) {
        onDispose { viewModel.markAllSeen() }
    }

    val handler = remember(viewModel) { viewModel.createInputHandler() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, handler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.subscribeView(handler, forRoute = Screen.QuayPass.route)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(handler, forRoute = Screen.QuayPass.route)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    FocusedScroll(listState = listState, focusedIndex = uiState.focusedIndex)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(Dimens.spacingMd)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                    ) {
                        Icon(
                            imageVector = if (running) QuayPassIcons.Encounter else QuayPassIcons.Off,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(Dimens.iconLg)
                        )
                        Text(
                            text = "Check-In",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    TicketBalanceChip(ticketBalance)
                }
                Text(
                    text = when (serviceState) {
                        QuayPassService.QuayPassRunState.RUNNING -> "Listening for nearby travelers..."
                        QuayPassService.QuayPassRunState.DISABLED -> "QuayPass is off"
                        QuayPassService.QuayPassRunState.NOT_LINKED -> "Link your Argosy account to check in"
                        QuayPassService.QuayPassRunState.AWAITING_REGISTRATION -> "Registering with the server..."
                        QuayPassService.QuayPassRunState.BLUETOOTH_OFF -> "Turn on Bluetooth to check in"
                        QuayPassService.QuayPassRunState.PERMISSIONS_MISSING -> "Bluetooth permission needed"
                        QuayPassService.QuayPassRunState.BLE_UNSUPPORTED -> "This device can't exchange nearby"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(Dimens.spacingMd))

                if (encounters.isEmpty()) {
                    EmptyState()
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(vertical = Dimens.spacingSm),
                            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                        ) {
                            itemsIndexed(
                                encounters,
                                key = { _, encounter -> encounter.credentialFingerprint }
                            ) { index, encounter ->
                                val fingerprint = encounter.credentialFingerprint
                                val accountId = encounter.accountId
                                EncounterCard(
                                    encounter = encounter,
                                    isFocused = index == uiState.focusedIndex,
                                    visible = fingerprint !in uiState.pendingArrivals,
                                    snapReveal = fingerprint in uiState.rushedArrivals,
                                    showTicketAward = fingerprint in uiState.revealedArrivals,
                                    ticketAward = uiState.ticketAwardPerEncounter,
                                    requestSent = accountId != null && accountId in uiState.sentAccountIds,
                                    requestQueued = accountId != null && accountId in uiState.queuedFriendAccountIds,
                                    isFriend = accountId != null && accountId in uiState.friendAccountIds,
                                    friendAvatarDoodle = accountId?.let { uiState.friendAvatars[it] },
                                    onClick = { viewModel.onCardTapped(index) }
                                )
                            }
                        }
                        if (uiState.arrivalSequenceRunning) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickableNoFocus { viewModel.rushArrivals() }
                            )
                        }
                    }
                }
            }

            val focusedAccountId = encounters.getOrNull(uiState.focusedIndex)?.accountId
            val canAddFriend = !uiState.arrivalSequenceRunning &&
                focusedAccountId != null &&
                focusedAccountId !in uiState.friendAccountIds &&
                focusedAccountId !in uiState.sentAccountIds
            FooterBar(
                hints = buildList {
                    if (canAddFriend) add(InputButton.A to "Add Friend")
                    add(InputButton.Y to "Greeting")
                    add(InputButton.B to "Back")
                    add(InputButton.DPAD_VERTICAL to "Scroll")
                },
                onHintClick = { button ->
                    if (button == InputButton.Y) viewModel.openGreetingEditor()
                }
            )
        }
    }

    if (uiState.showGreetingEditor) {
        GreetingEditModal(
            initial = greeting,
            onSubmit = {
                viewModel.setGreeting(it)
                viewModel.dismissGreetingEditor()
            },
            onDismiss = { viewModel.dismissGreetingEditor() }
        )
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            Text(
                text = "No travelers yet",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Take Argosy with you. You'll meet other players passively when you're nearby.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun EncounterCard(
    encounter: QuayPassEncounterEntity,
    isFocused: Boolean,
    visible: Boolean,
    snapReveal: Boolean,
    showTicketAward: Boolean,
    ticketAward: Int,
    requestSent: Boolean,
    requestQueued: Boolean,
    isFriend: Boolean,
    friendAvatarDoodle: String?,
    onClick: () -> Unit
) {
    val doodle = remember(encounter.avatarBlobBase64) { decodeAvatarDoodle(encounter.avatarBlobBase64) }
    val cardShape = RoundedCornerShape(Dimens.radiusLg)
    val cardAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (snapReveal) snap() else MotionTokens.Tween.page,
        label = "arrival-fade"
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = cardAlpha }
            .argosyFocusIndicators(
                focused = isFocused,
                indicators = FocusIndicators.Ring,
                shape = cardShape
            )
            .clickableNoFocus(onClick = onClick),
        shape = cardShape
    ) {
        Row(
            modifier = Modifier.padding(Dimens.spacingMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isFriend) {
                SocialAvatar(
                    displayName = encounter.displayName ?: encounter.username,
                    avatarColor = null,
                    size = Dimens.avatarXl,
                    avatarDoodle = friendAvatarDoodle,
                    userId = encounter.accountId
                )
            } else if (doodle != null) {
                DoodlePreview(
                    canvasSize = doodle.size,
                    pixels = doodle.pixels,
                    modifier = Modifier
                        .size(Dimens.avatarXl)
                        .clip(CircleShape)
                )
            } else {
                SocialAvatar(
                    displayName = encounter.username,
                    avatarColor = null,
                    size = Dimens.avatarXl
                )
            }
            Spacer(Modifier.width(Dimens.spacingMd))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = encounter.displayName ?: "@${encounter.username}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (isFriend) {
                        Spacer(Modifier.width(Dimens.spacingSm))
                        FriendBadge()
                    }
                }
                if (encounter.displayName != null) {
                    Text(
                        text = "@${encounter.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (requestSent) {
                    Text(
                        text = if (requestQueued) "Friend request queued" else "Friend request sent",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (!encounter.greeting.isNullOrBlank()) {
                    Spacer(Modifier.height(Dimens.spacingXs))
                    Text(
                        text = encounter.greeting,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (!encounter.lastGameTitle.isNullOrBlank()) {
                    Spacer(Modifier.height(Dimens.spacingXs))
                    Text(
                        text = "Played ${encounter.lastGameTitle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(Dimens.spacingXs))
                Text(
                    text = formatTimestamp(encounter.encounteredAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (showTicketAward) {
                Spacer(Modifier.width(Dimens.spacingSm))
                TicketAwardChip(ticketAward)
            }
        }
    }
}

@Composable
private fun FriendBadge(modifier: Modifier = Modifier) {
    Text(
        text = "Friend",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(Dimens.radiusSm))
            .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs)
    )
}

@Composable
private fun TicketAwardChip(amount: Int, modifier: Modifier = Modifier) {
    Text(
        text = "+$amount",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(Dimens.radiusMd))
            .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs)
    )
}

private fun decodeAvatarDoodle(base64: String?): DecodedDoodle? = base64?.let {
    runCatching {
        val raster = QuayPassDoodleCodec.decode(android.util.Base64.decode(it, android.util.Base64.NO_WRAP))
            ?: return@runCatching null
        rasterToDecodedDoodle(raster.size, raster.paletteIndices)
    }.getOrNull()
}

private fun rasterToDecodedDoodle(size: Int, paletteIndices: IntArray): DecodedDoodle? {
    val canvasSize = when (size) {
        CanvasSize.SMALL.pixels -> CanvasSize.SMALL
        CanvasSize.MEDIUM.pixels -> CanvasSize.MEDIUM
        else -> return null
    }
    val pixels = mutableMapOf<Pair<Int, Int>, DoodleColor>()
    for (y in 0 until size) {
        for (x in 0 until size) {
            val index = paletteIndices[y * size + x]
            if (index != 0) {
                pixels[x to y] = DoodleColor.fromIndex(index)
            }
        }
    }
    return DecodedDoodle(canvasSize, pixels)
}

private val TIME_FORMAT = DateTimeFormatter.ofPattern("MMM d, HH:mm")
private fun formatTimestamp(instant: Instant): String =
    TIME_FORMAT.format(instant.atZone(ZoneId.systemDefault()))
