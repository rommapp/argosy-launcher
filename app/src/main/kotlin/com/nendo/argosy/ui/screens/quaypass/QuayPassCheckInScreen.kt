package com.nendo.argosy.ui.screens.quaypass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.nendo.argosy.data.quaypass.QuayPassService
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.friends.SocialAvatar
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.quaypass.QuayPassIcons
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.generated.MotionTokens
import com.nendo.argosy.ui.util.clickableNoFocus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun QuayPassCheckInScreen(
    onBack: () -> Unit,
    viewModel: QuayPassCheckInViewModel = hiltViewModel()
) {
    val cards by viewModel.cards.collectAsState()
    val serviceState by viewModel.serviceState.collectAsState()
    val running = serviceState == QuayPassService.QuayPassRunState.RUNNING
    val ticketBalance by viewModel.ticketBalance.collectAsState()
    val greeting by viewModel.greeting.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val inputDispatcher = LocalInputDispatcher.current
    var arrivalTrigger by remember { mutableIntStateOf(0) }
    var arrivalPopup by remember { mutableStateOf<ArrivalPopup?>(null) }

    LaunchedEffect(Unit) {
        viewModel.arrivals.collect { popup ->
            arrivalPopup = popup
            arrivalTrigger++
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.markAllSeen() }
    }

    val handler = remember(viewModel, onBack) { viewModel.createInputHandler(onBack) }

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
      Box(modifier = Modifier.fillMaxSize()) {
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                    ) {
                        Text(
                            text = "Replay",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(Dimens.radiusMd))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickableNoFocus { viewModel.replayArrival() }
                                .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs)
                        )
                        TicketBalanceChip(ticketBalance)
                    }
                }
                Text(
                    text = when (serviceState) {
                        QuayPassService.QuayPassRunState.RUNNING -> "Listening for nearby travelers..."
                        QuayPassService.QuayPassRunState.DISABLED -> "QuayPass is off"
                        QuayPassService.QuayPassRunState.NOT_LINKED -> "Link your Argosy account to check in"
                        QuayPassService.QuayPassRunState.AWAITING_REGISTRATION -> "Registering with the server..."
                        QuayPassService.QuayPassRunState.AWAITING_CREDENTIAL -> "Connect to finish setting up your pass"
                        QuayPassService.QuayPassRunState.KEY_EXPIRED -> "Reconnect to refresh your pass"
                        QuayPassService.QuayPassRunState.CREDENTIAL_REJECTED -> "Server key mismatch"
                        QuayPassService.QuayPassRunState.BLUETOOTH_OFF -> "Turn on Bluetooth to check in"
                        QuayPassService.QuayPassRunState.PERMISSIONS_MISSING -> "Bluetooth permission needed"
                        QuayPassService.QuayPassRunState.BLE_UNSUPPORTED -> "This device can't exchange nearby"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(Dimens.spacingMd))

                if (cards.isEmpty()) {
                    EmptyState()
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(vertical = Dimens.spacingSm),
                            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                        ) {
                            itemsIndexed(
                                cards,
                                key = { _, card -> card.key }
                            ) { index, card ->
                                CheckInCardView(
                                    card = card,
                                    isFocused = index == uiState.focusedIndex,
                                    visible = card.key !in uiState.pendingArrivals,
                                    snapReveal = card.key in uiState.rushedArrivals,
                                    showTicketAward = card.key in uiState.revealedArrivals,
                                    ticketAward = uiState.ticketAwardPerEncounter,
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

            val focusedCard = cards.getOrNull(uiState.focusedIndex)
            val primaryAction = when {
                uiState.arrivalSequenceRunning || focusedCard == null -> null
                focusedCard.isBlocked || focusedCard.isFriend || focusedCard.requestSent -> null
                focusedCard.requestReceived -> "Accept"
                else -> "Add Friend"
            }
            FooterBar(
                hints = buildList {
                    if (primaryAction != null) add(InputButton.A to primaryAction)
                    add(InputButton.Y to "Greeting")
                    add(InputButton.B to "Back")
                    add(InputButton.DPAD_VERTICAL to "Scroll")
                },
                onHintClick = { button ->
                    if (button == InputButton.Y) viewModel.openGreetingEditor()
                }
            )
        }
        QuayPassArrivalOverlay(
            trigger = arrivalTrigger,
            name = arrivalPopup?.name ?: "",
            greeting = arrivalPopup?.greeting,
            avatarBitmap = remember(arrivalPopup?.avatarPngBase64) {
                decodePngAvatar(arrivalPopup?.avatarPngBase64)
            },
            onFinished = {}
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
private fun CheckInCardView(
    card: CheckInCard,
    isFocused: Boolean,
    visible: Boolean,
    snapReveal: Boolean,
    showTicketAward: Boolean,
    ticketAward: Int,
    onClick: () -> Unit
) {
    val avatarBitmap = remember(card.avatarPngBase64) { decodePngAvatar(card.avatarPngBase64) }
    val cardShape = RoundedCornerShape(Dimens.radiusLg)
    val cardAlpha by animateFloatAsState(
        targetValue = when {
            !visible -> 0f
            card.isBlocked -> 0.45f
            else -> 1f
        },
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
            if (avatarBitmap != null) {
                Image(
                    bitmap = avatarBitmap,
                    contentDescription = null,
                    filterQuality = FilterQuality.None,
                    modifier = Modifier
                        .size(Dimens.avatarXl)
                        .clip(CircleShape)
                )
            } else {
                SocialAvatar(
                    displayName = card.username,
                    avatarColor = null,
                    size = Dimens.avatarXl
                )
            }
            Spacer(Modifier.width(Dimens.spacingMd))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = card.displayName ?: "@${card.username}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (card.isFriend) {
                        Spacer(Modifier.width(Dimens.spacingSm))
                        FriendBadge()
                    }
                }
                if (card.displayName != null) {
                    Text(
                        text = "@${card.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val relationshipLabel = when {
                    card.isBlocked -> "Blocked"
                    card.requestReceived -> "Wants to add you"
                    card.requestSent -> "Friend request sent"
                    else -> null
                }
                if (relationshipLabel != null) {
                    Text(
                        text = relationshipLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (card.requestReceived) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!card.greeting.isNullOrBlank()) {
                    Spacer(Modifier.height(Dimens.spacingXs))
                    Text(
                        text = card.greeting,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (!card.lastGameTitle.isNullOrBlank()) {
                    Spacer(Modifier.height(Dimens.spacingXs))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (card.coverThumbUrl != null) {
                            AsyncImage(
                                model = card.coverThumbUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(Dimens.iconLg)
                                    .clip(RoundedCornerShape(Dimens.radiusSm))
                            )
                            Spacer(Modifier.width(Dimens.spacingXs))
                        }
                        Text(
                            text = "Played ${card.lastGameTitle}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(Dimens.spacingXs))
                Text(
                    text = if (card.meetCount > 1) {
                        "Met ${card.meetCount} times · ${formatTimestamp(card.encounteredAt)}"
                    } else {
                        formatTimestamp(card.encounteredAt)
                    },
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

private fun decodePngAvatar(base64: String?): ImageBitmap? = base64?.let {
    runCatching {
        val bytes = android.util.Base64.decode(it, android.util.Base64.NO_WRAP)
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}

private val TIME_FORMAT = DateTimeFormatter.ofPattern("MMM d, HH:mm")
private fun formatTimestamp(instant: Instant): String =
    TIME_FORMAT.format(instant.atZone(ZoneId.systemDefault()))
