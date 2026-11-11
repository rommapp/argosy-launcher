package com.nendo.argosy.ui.notification

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.nendo.argosy.core.notification.NotificationDuration
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.ResolvedNotice
import com.nendo.argosy.core.notification.resolve
import com.nendo.argosy.core.notification.severity
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.theme.ArgosyDarkTheme
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.generated.MotionTokens
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

sealed interface InGameNoticeIcon {
    data class Image(val url: String) : InGameNoticeIcon
    data class Glyph(val vector: ImageVector) : InGameNoticeIcon
}

/**
 * [key] replaces a queued or visible notice with the same key. [accent] replaces the severity
 * color. [durationMs] counts from the latest show of the notice; null keeps it on screen until a
 * notice with the same key replaces it. [badge] draws as a pill on the icon slot, and [bodyGlyph]
 * in place of [NOTICE_GLYPH_MARKER] in the body.
 */
data class InGameNotice(
    val severity: NotificationSeverity,
    val title: NotificationText,
    val body: NotificationText? = null,
    val durationMs: Long? = severity.defaultInGameDuration().ms,
    val key: String? = null,
    val icon: InGameNoticeIcon? = null,
    val accent: Color? = null,
    val badge: NotificationText? = null,
    val bodyGlyph: InputButton? = null
)

fun ResolvedNotice.asInGameNotice(): InGameNotice = InGameNotice(
    severity = type.severity(),
    title = NotificationText.Raw(title),
    body = body?.let { NotificationText.Raw(it) },
    durationMs = duration.ms,
    key = key,
    icon = imagePath?.let { InGameNoticeIcon.Image(it) },
    accent = accentColor?.let { Color(it) }
)

fun NotificationSeverity.defaultInGameDuration(): NotificationDuration = when (this) {
    NotificationSeverity.INFO, NotificationSeverity.SUCCESS -> NotificationDuration.SHORT
    NotificationSeverity.WARNING, NotificationSeverity.PROGRESS -> NotificationDuration.MEDIUM
    NotificationSeverity.ERROR -> NotificationDuration.LONG
}

data class InGameNoticeEntry(val id: Long, val revision: Int, val notice: InGameNotice)

/**
 * FIFO queue of in-game notices, shown one at a time by [InGameNoticeOverlay]. The callback
 * passed to [show] runs once that notice has left the screen; a keyed replacement takes over the
 * callback of the notice it replaces.
 */
class InGameNoticeHost {
    private val lock = Any()
    private var nextId = 0L
    private var pending: List<InGameNoticeEntry> = emptyList()
    private val callbacks = HashMap<Long, () -> Unit>()

    var current by mutableStateOf<InGameNoticeEntry?>(null)
        private set

    fun show(notice: InGameNotice, onDismissed: () -> Unit = {}) {
        synchronized(lock) {
            val key = notice.key
            val visible = current
            if (key != null && visible != null && visible.notice.key == key) {
                callbacks[visible.id] = onDismissed
                current = visible.copy(revision = visible.revision + 1, notice = notice)
                return
            }
            val queuedIndex = if (key == null) -1 else pending.indexOfFirst { it.notice.key == key }
            if (queuedIndex >= 0) {
                val queued = pending[queuedIndex]
                callbacks[queued.id] = onDismissed
                pending = pending.toMutableList().also { it[queuedIndex] = queued.copy(notice = notice) }
                return
            }
            val entry = InGameNoticeEntry(id = nextId++, revision = 0, notice = notice)
            callbacks[entry.id] = onDismissed
            if (visible == null) current = entry else pending = pending + entry
        }
    }

    fun finish(id: Long) {
        val callback = synchronized(lock) {
            if (current?.id != id) return
            current = pending.firstOrNull()
            pending = pending.drop(1)
            callbacks.remove(id)
        }
        callback?.invoke()
    }
}

@Composable
fun InGameNoticeOverlay(
    host: InGameNoticeHost,
    modifier: Modifier = Modifier,
    isSecondaryDisplay: Boolean = false
) {
    val entry = host.current ?: return
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(entry.id, entry.revision) {
        visible = true
        entry.notice.durationMs?.let { delay(it) } ?: awaitCancellation()
        visible = false
        delay(maxOf(MotionTokens.Tween.noticeRevealMs, Motion.durationContent).toLong())
        host.finish(entry.id)
    }

    val noticeIcon = entry.notice.icon
    val iconContent: (@Composable (Color) -> Unit)? = when (noticeIcon) {
        null -> null
        else -> { accent -> InGameNoticeIconContent(noticeIcon, accent) }
    }

    ArgosyDarkTheme(isSecondaryDisplay = isSecondaryDisplay) {
        NotificationCard(
            visible = visible,
            severity = entry.notice.severity,
            title = entry.notice.title.resolve(),
            body = entry.notice.body?.resolve(),
            bodyGlyph = entry.notice.bodyGlyph,
            badge = entry.notice.badge?.resolve(),
            accentOverride = entry.notice.accent,
            icon = iconContent,
            modifier = modifier
        )
    }
}

@Composable
private fun InGameNoticeIconContent(icon: InGameNoticeIcon, accent: Color) {
    when (icon) {
        is InGameNoticeIcon.Image -> AsyncImage(
            model = icon.url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(Dimens.avatarMd)
                .clip(RoundedCornerShape(Dimens.radiusSm))
        )
        is InGameNoticeIcon.Glyph -> Icon(
            imageVector = icon.vector,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(Dimens.iconMd)
        )
    }
}
