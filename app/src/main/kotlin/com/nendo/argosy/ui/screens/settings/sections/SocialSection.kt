package com.nendo.argosy.ui.screens.settings.sections

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.SectionFocusedScroll
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.SocialAuthStatus
import com.nendo.argosy.ui.screens.settings.SocialState
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens

internal data class SocialLayoutState(
    val isConnected: Boolean
)

internal sealed class SocialItem(
    val key: String,
    val section: String,
    val visibleWhen: (SocialLayoutState) -> Boolean = { it.isConnected }
) {
    val isFocusable: Boolean get() = when (this) {
        is Header, is SectionSpacer -> false
        else -> true
    }

    class Header(key: String, section: String, val title: String, visibleWhen: (SocialLayoutState) -> Boolean = { it.isConnected })
        : SocialItem(key, section, visibleWhen)

    class SectionSpacer(key: String, section: String, visibleWhen: (SocialLayoutState) -> Boolean = { it.isConnected })
        : SocialItem(key, section, visibleWhen)

    data object AccountInfo : SocialItem("accountInfo", "account")
    data object OnlineStatus : SocialItem("onlineStatus", "privacy")
    data object ShowNowPlaying : SocialItem("showNowPlaying", "privacy")
    data object NotifyFriendOnline : SocialItem("notifyFriendOnline", "notifications")
    data object NotifyFriendPlaying : SocialItem("notifyFriendPlaying", "notifications")
    data object Unlink : SocialItem("unlink", "unlink")

    companion object {
        private val AccountHeader = Header("accountHeader", "account", "ACCOUNT")
        private val PrivacyHeader = Header("privacyHeader", "privacy", "PRIVACY")
        private val NotificationsHeader = Header("notificationsHeader", "notifications", "NOTIFICATIONS")
        private val NotificationsSpacer = SectionSpacer("notificationsSpacer", "notifications")
        private val UnlinkSpacer = SectionSpacer("unlinkSpacer", "unlink")

        val ALL: List<SocialItem> = listOf(
            AccountHeader, AccountInfo,
            PrivacyHeader, OnlineStatus, ShowNowPlaying,
            NotificationsSpacer, NotificationsHeader, NotifyFriendOnline, NotifyFriendPlaying,
            UnlinkSpacer, Unlink
        )
    }
}

private val socialLayout = SettingsLayout<SocialItem, SocialLayoutState>(
    allItems = SocialItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section }
)

internal fun socialMaxFocusIndex(social: SocialState): Int {
    return when (social.authStatus) {
        SocialAuthStatus.CONNECTED -> socialLayout.maxFocusIndex(SocialLayoutState(isConnected = true))
        else -> 0
    }
}

internal fun socialItemAtFocusIndex(focusIndex: Int, state: SocialLayoutState): SocialItem? =
    socialLayout.itemAtFocusIndex(focusIndex, state)

@Composable
fun SocialSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val listState = rememberLazyListState()
    val social = uiState.social

    val layoutState = remember(social.authStatus) {
        SocialLayoutState(isConnected = social.authStatus == SocialAuthStatus.CONNECTED)
    }

    if (social.authStatus == SocialAuthStatus.CONNECTED) {
        val sections = remember { socialLayout.buildSections(layoutState) }

        SectionFocusedScroll(
            listState = listState,
            focusedIndex = uiState.focusedIndex,
            focusToListIndex = { socialLayout.focusToListIndex(it, layoutState) },
            sections = sections
        )
    } else {
        LaunchedEffect(uiState.focusedIndex) {
            if (uiState.focusedIndex == 0) {
                val layoutInfo = listState.layoutInfo
                val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                val itemHeight = layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 60
                val centerOffset = (viewportHeight - itemHeight) / 2
                listState.animateScrollToItem(0, -centerOffset)
            }
        }
    }

    fun isFocused(item: SocialItem): Boolean =
        uiState.focusedIndex == socialLayout.focusIndexOf(item, layoutState)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = Dimens.spacingMd),
        contentPadding = PaddingValues(top = Dimens.spacingMd, bottom = Dimens.spacingXxl),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        when (social.authStatus) {
            SocialAuthStatus.NOT_LINKED -> {
                item {
                    NotLinkedContent(
                        isFocused = uiState.focusedIndex == 0,
                        onStartAuth = { viewModel.startSocialAuth() }
                    )
                }
            }

            SocialAuthStatus.CONNECTING -> {
                item {
                    ConnectingContent()
                }
            }

            SocialAuthStatus.AWAITING_AUTH -> {
                item {
                    AwaitingAuthContent(
                        qrUrl = social.qrUrl,
                        loginCode = social.loginCode,
                        isFocused = uiState.focusedIndex == 0,
                        onCancel = { viewModel.cancelSocialAuth() }
                    )
                }
            }

            SocialAuthStatus.CONNECTED -> {
                val visibleItems = socialLayout.visibleItems(layoutState)
                items(visibleItems, key = { it.key }) { item ->
                    when (item) {
                        is SocialItem.Header -> SectionHeader(item.title)

                        is SocialItem.SectionSpacer -> Spacer(modifier = Modifier.height(Dimens.spacingLg))

                        SocialItem.AccountInfo -> AccountInfoCard(
                            username = social.username ?: "",
                            displayName = social.displayName,
                            avatarColor = social.avatarColor,
                            isFocused = isFocused(item)
                        )

                        SocialItem.OnlineStatus -> SwitchPreference(
                            title = "Online Status",
                            subtitle = if (social.onlineStatusEnabled) {
                                "Appear online to friends"
                            } else {
                                "Appear offline to friends"
                            },
                            isEnabled = social.onlineStatusEnabled,
                            isFocused = isFocused(item),
                            onToggle = { viewModel.setSocialOnlineStatus(it) }
                        )

                        SocialItem.ShowNowPlaying -> SwitchPreference(
                            title = "Show Now Playing",
                            subtitle = if (!social.onlineStatusEnabled) {
                                "Enable Online Status first"
                            } else if (social.showNowPlaying) {
                                "Share which game you're playing"
                            } else {
                                "Hide game activity from friends"
                            },
                            isEnabled = social.showNowPlaying && social.onlineStatusEnabled,
                            isFocused = isFocused(item),
                            onToggle = { if (social.onlineStatusEnabled) viewModel.setSocialShowNowPlaying(it) }
                        )

                        SocialItem.NotifyFriendOnline -> SwitchPreference(
                            title = "Friend Comes Online",
                            subtitle = if (!social.onlineStatusEnabled) {
                                "Enable Online Status first"
                            } else if (social.notifyFriendOnline) {
                                "Show notification when friends come online"
                            } else {
                                "Notifications disabled"
                            },
                            isEnabled = social.notifyFriendOnline && social.onlineStatusEnabled,
                            isFocused = isFocused(item),
                            onToggle = { if (social.onlineStatusEnabled) viewModel.setSocialNotifyFriendOnline(it) }
                        )

                        SocialItem.NotifyFriendPlaying -> SwitchPreference(
                            title = "Friend Starts Playing",
                            subtitle = if (!social.onlineStatusEnabled) {
                                "Enable Online Status first"
                            } else if (social.notifyFriendPlaying) {
                                "Show notification when friends start a game"
                            } else {
                                "Notifications disabled"
                            },
                            isEnabled = social.notifyFriendPlaying && social.onlineStatusEnabled,
                            isFocused = isFocused(item),
                            onToggle = { if (social.onlineStatusEnabled) viewModel.setSocialNotifyFriendPlaying(it) }
                        )

                        SocialItem.Unlink -> ActionPreference(
                            title = "Unlink Account",
                            subtitle = "Disconnect from social features",
                            isFocused = isFocused(item),
                            isDangerous = true,
                            onClick = { viewModel.logoutSocial() }
                        )
                    }
                }
            }

            SocialAuthStatus.ERROR -> {
                item {
                    ErrorContent(
                        message = social.errorMessage ?: "An error occurred",
                        isFocused = uiState.focusedIndex == 0,
                        onRetry = { viewModel.startSocialAuth() }
                    )
                }
            }
        }
    }

}

@Composable
private fun NotLinkedContent(
    isFocused: Boolean,
    onStartAuth: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(Dimens.spacingLg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Log in to enable social features",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(Dimens.spacingMd))

        Text(
            text = "Connect with friends, share collections, and see what others are playing.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Dimens.spacingLg))

        FocusableButton(
            text = "Link Account",
            isFocused = isFocused,
            onClick = onStartAuth
        )
    }
}

@Composable
private fun ConnectingContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.spacingXl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(Dimens.spacingMd))

        Text(
            text = "Connecting...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun AwaitingAuthContent(
    qrUrl: String?,
    loginCode: String?,
    isFocused: Boolean,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(Dimens.spacingLg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.weight(0.3f),
            contentAlignment = Alignment.Center
        ) {
            if (qrUrl != null) {
                QrCodeImage(
                    url = qrUrl,
                    modifier = Modifier.size(160.dp)
                )
            }
        }

        Column(
            modifier = Modifier.weight(0.7f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            Text(
                text = "Scan to link your account",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            if (loginCode != null) {
                Spacer(modifier = Modifier.height(Dimens.spacingSm))

                Text(
                    text = buildAnnotatedString {
                        append("Or visit ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("argosy.dev")
                        }
                        append(" and enter:")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = loginCode,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 2.sp
                )
            }

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            FocusableButton(
                text = "Cancel",
                isFocused = isFocused,
                onClick = onCancel
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    isFocused: Boolean,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
            .padding(Dimens.spacingLg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Connection Error",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(Dimens.spacingSm))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Dimens.spacingMd))

        FocusableButton(
            text = "Try Again",
            isFocused = isFocused,
            onClick = onRetry
        )
    }
}

@Composable
private fun FocusableButton(
    text: String,
    isFocused: Boolean,
    onClick: () -> Unit
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
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(backgroundColor)
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingMd),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = textColor
        )
    }
}

@Composable
private fun QrCodeImage(
    url: String,
    modifier: Modifier = Modifier
) {
    val qrBitmap = remember(url) {
        generateQrCode(url, 512)
    }

    if (qrBitmap != null) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = "QR Code",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun generateQrCode(content: String, size: Int): Bitmap? {
    return try {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )

        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)

        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                pixels[y * width + x] = if (bitMatrix[x, y]) {
                    android.graphics.Color.BLACK
                } else {
                    android.graphics.Color.WHITE
                }
            }
        }

        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(
            start = Dimens.spacingSm,
            top = Dimens.spacingMd,
            bottom = Dimens.spacingXs
        )
    )
}

@Composable
private fun AccountInfoCard(
    username: String,
    displayName: String?,
    avatarColor: String?,
    isFocused: Boolean
) {
    val backgroundColor = if (isFocused) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val parsedColor = remember(avatarColor) {
        try {
            avatarColor?.let { Color(android.graphics.Color.parseColor(it)) }
        } catch (e: Exception) {
            null
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusLg))
            .background(backgroundColor)
            .padding(Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(parsedColor ?: MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = (displayName ?: username).take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.width(Dimens.spacingMd))

        Column {
            Text(
                text = "@$username",
                style = MaterialTheme.typography.titleMedium,
                color = contentColor
            )
            displayName?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}
