package com.nendo.argosy.core.notification

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import coil.compose.AsyncImage
import com.nendo.argosy.ui.components.PlatformIconAssets
import com.nendo.argosy.ui.notification.NotificationCard
import com.nendo.argosy.ui.notification.NotificationSeverity
import com.nendo.argosy.ui.theme.Dimens
import kotlinx.coroutines.delay

@Composable
fun NotificationText.resolve(): String = when (this) {
    is NotificationText.Raw -> value
    is NotificationText.Res -> stringResource(id, *args.toTypedArray())
    is NotificationText.Plural -> pluralStringResource(id, count, *args.toTypedArray())
}

fun NotificationText.resolve(context: android.content.Context): String = when (this) {
    is NotificationText.Raw -> value
    is NotificationText.Res -> context.getString(id, *args.toTypedArray())
    is NotificationText.Plural -> context.resources.getQuantityString(id, count, *args.toTypedArray())
}

@Composable
fun NotificationHost(
    manager: NotificationManager,
    modifier: Modifier = Modifier,
    mutedKeys: Set<String> = emptySet()
) {
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val started = lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    DisposableEffect(manager, started) {
        if (started) manager.onSurfaceStarted()
        onDispose { if (started) manager.onSurfaceStopped() }
    }

    val notifications by manager.notifications.collectAsState()
    val persistentAll by manager.persistentNotifications.collectAsState()
    val noticePreferences by manager.preferences.collectAsState()
    val visiblePersistent = if (started && noticePreferences.enabled) {
        persistentAll.filterNot { it.key in mutedKeys }
    } else {
        emptyList()
    }
    val persistent = visiblePersistent.lastOrNull()
    val current = if (started) notifications.firstOrNull { it.key == null || it.key !in mutedKeys } else null

    LaunchedEffect(current?.id) {
        current?.let { notification ->
            delay(notification.duration.ms)
            manager.dismiss(notification.id)
        }
    }

    val shownTransient = rememberLatest(current)
    val shownPersistent = rememberLatest(persistent)

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = Dimens.spacingMd, bottom = Dimens.footerHeight + Dimens.spacingXs),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            shownTransient?.let { notification ->
                TransientNotificationCard(
                    notification = notification,
                    visible = current != null
                )
            }
            shownPersistent?.let { notification ->
                PersistentNotificationCard(
                    notification = notification,
                    alsoRunning = visiblePersistent.size - 1,
                    visible = persistent != null
                )
            }
        }
    }
}

@Composable
private fun <T : Any> rememberLatest(value: T?): T? {
    val latest = remember { mutableStateOf(value) }
    SideEffect {
        if (value != null) latest.value = value
    }
    return value ?: latest.value
}

@Composable
private fun rememberPlatformIconUri(platformSlug: String?): String? {
    val context = LocalContext.current
    return remember(platformSlug) {
        platformSlug?.let { slug -> PlatformIconAssets.resolveAssetUri(context, slug) }
    }
}

@Composable
private fun TransientNotificationCard(
    notification: Notification,
    visible: Boolean
) {
    val platformIconUri = rememberPlatformIconUri(notification.platformSlug)
    val imagePath = notification.imagePath
    val icon: (@Composable (Color) -> Unit)? = when {
        imagePath != null -> { _ ->
            AsyncImage(
                model = imagePath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(Dimens.avatarMd)
                    .clip(RoundedCornerShape(Dimens.radiusSm))
            )
        }
        platformIconUri != null -> { _ ->
            AsyncImage(
                model = platformIconUri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(Dimens.iconMd)
            )
        }
        else -> null
    }

    NotificationCard(
        visible = visible,
        severity = notification.type.severity(),
        title = notification.title.resolve(),
        body = notification.subtitle?.resolve(),
        accentOverride = notification.accentColor?.let { Color(it) },
        icon = icon
    )
}

@Composable
private fun PersistentNotificationCard(
    notification: Notification,
    alsoRunning: Int,
    visible: Boolean
) {
    val platformIconUri = rememberPlatformIconUri(notification.platformSlug)

    NotificationCard(
        visible = visible,
        severity = NotificationSeverity.PROGRESS,
        title = notification.title.resolve(),
        body = notification.subtitle?.resolve(),
        badge = if (alsoRunning > 0) "+$alsoRunning" else null,
        progress = notification.progress?.fraction,
        icon = { accent ->
            CircularProgressIndicator(
                modifier = Modifier.size(Dimens.iconLg),
                color = accent,
                strokeWidth = Dimens.borderMedium
            )
            platformIconUri?.let { uri ->
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(Dimens.iconSm)
                )
            }
        }
    )
}

internal fun NotificationType.severity(): NotificationSeverity = when (this) {
    NotificationType.SUCCESS -> NotificationSeverity.SUCCESS
    NotificationType.INFO -> NotificationSeverity.INFO
    NotificationType.WARNING -> NotificationSeverity.WARNING
    NotificationType.ERROR -> NotificationSeverity.ERROR
}
