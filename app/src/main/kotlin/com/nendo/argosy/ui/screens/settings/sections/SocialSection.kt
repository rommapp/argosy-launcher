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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.SocialAuthStatus
import com.nendo.argosy.ui.screens.settings.SocialState
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

private const val SOCIAL_LINK_HOST = "argosy.dev"

internal data class SocialLayoutState(
    val isConnected: Boolean,
    val hasAvatarDoodle: Boolean = false
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

    class Header(key: String, section: String, val titleRes: Int, visibleWhen: (SocialLayoutState) -> Boolean = { it.isConnected })
        : SocialItem(key, section, visibleWhen)

    class SectionSpacer(key: String, section: String, visibleWhen: (SocialLayoutState) -> Boolean = { it.isConnected })
        : SocialItem(key, section, visibleWhen)

    data object AccountInfo : SocialItem("accountInfo", "account")
    data object EditAvatar : SocialItem("editAvatar", "account")
    data object UseDoodleAvatar : SocialItem(
        "useDoodleAvatar", "account",
        visibleWhen = { it.isConnected && it.hasAvatarDoodle }
    )
    data object OnlineStatus : SocialItem("onlineStatus", "privacy")
    data object ShowNowPlaying : SocialItem("showNowPlaying", "privacy")
    data object NotifyFriendOnline : SocialItem("notifyFriendOnline", "notifications")
    data object NotifyFriendPlaying : SocialItem("notifyFriendPlaying", "notifications")
    data object SuppressInGame : SocialItem("suppressInGame", "notifications")
    data object QuayPassEnabled : SocialItem("quayPassEnabled", "quaypass")
    data object Unlink : SocialItem("unlink", "unlink")

    companion object {
        private val AccountHeader = Header("accountHeader", "account", R.string.settings_social_section_account)
        private val PrivacyHeader = Header("privacyHeader", "privacy", R.string.settings_social_section_privacy)
        private val NotificationsHeader =
            Header("notificationsHeader", "notifications", R.string.settings_social_section_notifications)
        private val NotificationsSpacer = SectionSpacer("notificationsSpacer", "notifications")
        private val QuayPassSpacer = SectionSpacer("quayPassSpacer", "quaypass")
        private val QuayPassHeader = Header("quayPassHeader", "quaypass", R.string.settings_social_section_quaypass)
        private val UnlinkSpacer = SectionSpacer("unlinkSpacer", "unlink")

        val ALL: List<SocialItem>
            get() = listOf(
                AccountHeader, AccountInfo, EditAvatar, UseDoodleAvatar,
                PrivacyHeader, OnlineStatus, ShowNowPlaying,
                NotificationsSpacer, NotificationsHeader, NotifyFriendOnline, NotifyFriendPlaying, SuppressInGame,
                QuayPassSpacer, QuayPassHeader, QuayPassEnabled,
                UnlinkSpacer, Unlink
            )
    }
}

private val socialLayout = SettingsLayout<SocialItem, SocialLayoutState>(
    allItems = SocialItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section },
    sectionTitleRes = {
        when (it) {
            "account" -> R.string.settings_social_section_account
            "privacy" -> R.string.settings_social_section_privacy
            "notifications" -> R.string.settings_social_section_notifications
            "quaypass" -> R.string.settings_social_section_quaypass
            else -> null
        }
    }
)

internal fun socialSections(hasAvatarDoodle: Boolean = false) =
    socialLayout.buildSections(SocialLayoutState(isConnected = true, hasAvatarDoodle = hasAvatarDoodle))

internal fun socialMaxFocusIndex(social: SocialState): Int {
    return when (social.authStatus) {
        SocialAuthStatus.CONNECTED -> socialLayout.maxFocusIndex(
            SocialLayoutState(isConnected = true, hasAvatarDoodle = social.avatarDoodle != null)
        )
        else -> 0
    }
}

internal fun socialItemAtFocusIndex(focusIndex: Int, state: SocialLayoutState): SocialItem? =
    socialLayout.itemAtFocusIndex(focusIndex, state)

@Composable
fun SocialSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
) {
    val social = uiState.social

    val layoutState = remember(social.authStatus, social.avatarDoodle) {
        SocialLayoutState(
            isConnected = social.authStatus == SocialAuthStatus.CONNECTED,
            hasAvatarDoodle = social.avatarDoodle != null
        )
    }

    fun isFocused(item: SocialItem): Boolean =
        uiState.focusedIndex == socialLayout.focusIndexOf(item, layoutState)

    if (social.authStatus == SocialAuthStatus.CONNECTED) {
        val context = LocalContext.current
        val visibleItems = remember(layoutState) { socialLayout.visibleItems(layoutState) }
        val sections = remember(layoutState, context) { socialLayout.buildSections(layoutState, context) }

        SectionPaneLayout(
            items = visibleItems,
            sections = sections,
            focusedIndex = uiState.focusedIndex,
            focusToListIndex = { socialLayout.focusToListIndex(it, layoutState) },
            itemKey = { it.key },
            isNavItem = { it is SocialItem.SectionSpacer },
            isHeader = { it is SocialItem.Header },
            onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
            modifier = Modifier.fillMaxSize().padding(horizontal = Dimens.spacingMd),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) { item ->
                    when (item) {
                        is SocialItem.Header -> SectionHeader(stringResource(item.titleRes))

                        is SocialItem.SectionSpacer -> Spacer(modifier = Modifier.height(Dimens.spacingLg))

                        SocialItem.AccountInfo -> AccountInfoCard(
                            username = social.username ?: "",
                            displayName = social.displayName,
                            avatarColor = social.avatarColor,
                            avatarDoodle = social.avatarDoodle.takeIf { social.avatarUseDoodle },
                            isFocused = isFocused(item)
                        )

                        SocialItem.EditAvatar -> ActionPreference(
                            title = stringResource(R.string.settings_social_edit_avatar_title),
                            subtitle = stringResource(R.string.settings_social_edit_avatar_subtitle),
                            isFocused = isFocused(item),
                            onClick = { viewModel.openAvatarEditor() }
                        )

                        SocialItem.UseDoodleAvatar -> SwitchPreference(
                            title = stringResource(R.string.settings_social_use_doodle_title),
                            subtitle = if (social.avatarUseDoodle) {
                                stringResource(R.string.settings_social_use_doodle_subtitle_on)
                            } else {
                                stringResource(R.string.settings_social_use_doodle_subtitle_off)
                            },
                            isEnabled = social.avatarUseDoodle,
                            isFocused = isFocused(item),
                            onToggle = { viewModel.setSocialAvatarUseDoodle(it) }
                        )

                        SocialItem.OnlineStatus -> SwitchPreference(
                            title = stringResource(R.string.settings_social_online_status_title),
                            subtitle = if (social.onlineStatusEnabled) {
                                stringResource(R.string.settings_social_online_status_subtitle_on)
                            } else {
                                stringResource(R.string.settings_social_online_status_subtitle_off)
                            },
                            isEnabled = social.onlineStatusEnabled,
                            isFocused = isFocused(item),
                            onToggle = { viewModel.setSocialOnlineStatus(it) }
                        )

                        SocialItem.ShowNowPlaying -> SwitchPreference(
                            title = stringResource(R.string.settings_social_now_playing_title),
                            subtitle = if (!social.onlineStatusEnabled) {
                                stringResource(R.string.settings_social_now_playing_subtitle_locked)
                            } else if (social.showNowPlaying) {
                                stringResource(R.string.settings_social_now_playing_subtitle_on)
                            } else {
                                stringResource(R.string.settings_social_now_playing_subtitle_off)
                            },
                            isEnabled = social.showNowPlaying && social.onlineStatusEnabled,
                            isFocused = isFocused(item),
                            onToggle = { if (social.onlineStatusEnabled) viewModel.setSocialShowNowPlaying(it) }
                        )

                        SocialItem.NotifyFriendOnline -> SwitchPreference(
                            title = stringResource(R.string.settings_social_notify_online_title),
                            subtitle = if (!social.onlineStatusEnabled) {
                                stringResource(R.string.settings_social_notify_online_subtitle_locked)
                            } else if (social.notifyFriendOnline) {
                                stringResource(R.string.settings_social_notify_online_subtitle_on)
                            } else {
                                stringResource(R.string.settings_social_notify_online_subtitle_off)
                            },
                            isEnabled = social.notifyFriendOnline && social.onlineStatusEnabled,
                            isFocused = isFocused(item),
                            onToggle = { if (social.onlineStatusEnabled) viewModel.setSocialNotifyFriendOnline(it) }
                        )

                        SocialItem.NotifyFriendPlaying -> SwitchPreference(
                            title = stringResource(R.string.settings_social_notify_playing_title),
                            subtitle = if (!social.onlineStatusEnabled) {
                                stringResource(R.string.settings_social_notify_playing_subtitle_locked)
                            } else if (social.notifyFriendPlaying) {
                                stringResource(R.string.settings_social_notify_playing_subtitle_on)
                            } else {
                                stringResource(R.string.settings_social_notify_playing_subtitle_off)
                            },
                            isEnabled = social.notifyFriendPlaying && social.onlineStatusEnabled,
                            isFocused = isFocused(item),
                            onToggle = { if (social.onlineStatusEnabled) viewModel.setSocialNotifyFriendPlaying(it) }
                        )

                        SocialItem.SuppressInGame -> SwitchPreference(
                            title = stringResource(R.string.settings_social_suppress_title),
                            subtitle = if (!social.onlineStatusEnabled) {
                                stringResource(R.string.settings_social_suppress_subtitle_locked)
                            } else if (social.suppressNotificationsInGame) {
                                stringResource(R.string.settings_social_suppress_subtitle_on)
                            } else {
                                stringResource(R.string.settings_social_suppress_subtitle_off)
                            },
                            isEnabled = social.suppressNotificationsInGame && social.onlineStatusEnabled,
                            isFocused = isFocused(item),
                            onToggle = { if (social.onlineStatusEnabled) viewModel.setSocialSuppressNotificationsInGame(it) }
                        )

                        SocialItem.QuayPassEnabled -> SwitchPreference(
                            title = stringResource(R.string.settings_social_quaypass_title),
                            subtitle = if (social.quayPassEnabled) {
                                stringResource(R.string.settings_social_quaypass_subtitle_on)
                            } else {
                                stringResource(R.string.settings_social_quaypass_subtitle_off)
                            },
                            isEnabled = social.quayPassEnabled,
                            isFocused = isFocused(item),
                            onToggle = { requested ->
                                if (requested) {
                                    viewModel.requestEnableQuayPass()
                                } else {
                                    viewModel.setQuayPassEnabled(false)
                                }
                            }
                        )

                        SocialItem.Unlink -> ActionPreference(
                            title = stringResource(R.string.settings_social_unlink_title),
                            subtitle = stringResource(R.string.settings_social_unlink_subtitle),
                            isFocused = isFocused(item),
                            isDangerous = true,
                            onClick = { viewModel.logoutSocial() }
                        )
                    }
        }
    } else {
        val listState = rememberLazyListState()

        LaunchedEffect(uiState.focusedIndex) {
            if (uiState.focusedIndex == 0) {
                listState.animateScrollToItemCentered(0)
            }
        }

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
                    item { ConnectingContent() }
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
                SocialAuthStatus.ERROR -> {
                    item {
                        ErrorContent(
                            message = social.errorMessage
                                ?: stringResource(R.string.settings_social_error_generic),
                            isFocused = uiState.focusedIndex == 0,
                            onRetry = { viewModel.startSocialAuth() }
                        )
                    }
                }
                else -> {}
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
            text = stringResource(R.string.settings_social_not_linked_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(Dimens.spacingMd))

        Text(
            text = stringResource(R.string.settings_social_not_linked_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Dimens.spacingLg))

        ActionButton(
            label = stringResource(R.string.settings_social_not_linked_action),
            onClick = onStartAuth,
            focused = isFocused,
            primary = true
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
            text = stringResource(R.string.settings_social_connecting),
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
                text = stringResource(R.string.settings_social_qr_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            if (loginCode != null) {
                Spacer(modifier = Modifier.height(Dimens.spacingSm))

                Text(
                    text = buildAnnotatedString {
                        append(stringResource(R.string.settings_social_qr_visit_prefix))
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(SOCIAL_LINK_HOST)
                        }
                        append(stringResource(R.string.settings_social_qr_visit_suffix))
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

            ActionButton(
                label = stringResource(R.string.settings_social_qr_cancel),
                onClick = onCancel,
                focused = isFocused
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
            text = stringResource(R.string.settings_social_error_title),
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

        ActionButton(
            label = stringResource(R.string.settings_social_error_retry),
            onClick = onRetry,
            focused = isFocused,
            primary = true
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
                contentDescription = stringResource(R.string.settings_social_qr_image_description),
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
    avatarDoodle: String?,
    isFocused: Boolean
) {
    val backgroundColor = if (isFocused) {
        LocalArgosyTheme.current.focusAccent.copy(alpha = 0.15f)
            .compositeOver(MaterialTheme.colorScheme.surface)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isFocused) {
        lerp(LocalArgosyTheme.current.focusAccent, Color.White, 0.45f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusControl))
            .background(backgroundColor)
            .padding(Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        com.nendo.argosy.ui.components.friends.SocialAvatar(
            displayName = displayName ?: username,
            avatarColor = avatarColor,
            size = Dimens.avatarMd,
            avatarDoodle = avatarDoodle
        )

        Spacer(modifier = Modifier.width(Dimens.spacingMd))

        Column {
            Text(
                text = stringResource(R.string.settings_social_account_handle, username),
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
