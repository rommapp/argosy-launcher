package com.nendo.argosy.ui.screens.quaypass

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.theme.Dimens
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nendo.argosy.data.local.entity.QuayPassEncounterEntity
import com.nendo.argosy.data.quaypass.ble.QuayPassDoodleCodec
import com.nendo.argosy.ui.components.friends.SocialAvatar
import com.nendo.argosy.ui.screens.doodle.CanvasSize
import com.nendo.argosy.ui.screens.doodle.DecodedDoodle
import com.nendo.argosy.ui.screens.doodle.DoodleColor
import com.nendo.argosy.ui.screens.doodle.DoodlePreview
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun QuayPassPlazaScreen(
    viewModel: QuayPassPlazaViewModel = hiltViewModel()
) {
    val encounters by viewModel.encounters.collectAsState()
    val running by viewModel.isServiceRunning.collectAsState()
    val ticketBalance by viewModel.ticketBalance.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val inputDispatcher = LocalInputDispatcher.current

    DisposableEffect(Unit) {
        onDispose { viewModel.markAllSeen() }
    }

    val handler = remember(listState) {
        object : InputHandler {
            override fun onUp(): InputResult {
                scope.launch {
                    val target = (listState.firstVisibleItemIndex - 1).coerceAtLeast(0)
                    listState.animateScrollToItem(target)
                }
                return InputResult.HANDLED
            }
            override fun onDown(): InputResult {
                scope.launch {
                    val target = listState.firstVisibleItemIndex + 1
                    listState.animateScrollToItem(target)
                }
                return InputResult.HANDLED
            }
        }
    }

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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Plaza",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    TicketBalanceChip(ticketBalance)
                }
                Text(
                    text = if (running) "Listening for nearby travelers..." else "QuayPass is offline",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(16.dp))

                if (encounters.isEmpty()) {
                    EmptyState()
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(encounters, key = { it.credentialFingerprint }) { encounter ->
                            EncounterCard(encounter)
                        }
                    }
                }
            }

            FooterBar(
                hints = listOf(
                    InputButton.B to "Back",
                    InputButton.DPAD_VERTICAL to "Scroll"
                )
            )
        }
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
private fun EncounterCard(encounter: QuayPassEncounterEntity) {
    val doodle = remember(encounter.avatarBlobBase64) { decodeAvatarDoodle(encounter.avatarBlobBase64) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (doodle != null) {
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
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = encounter.displayName ?: "@${encounter.username}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                if (encounter.displayName != null) {
                    Text(
                        text = "@${encounter.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!encounter.greeting.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = encounter.greeting,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (!encounter.lastGameTitle.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Played ${encounter.lastGameTitle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatTimestamp(encounter.encounteredAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
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
