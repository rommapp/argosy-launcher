package com.nendo.argosy.ui.screens.settings.dialogs

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.primitives.ModalScaffold
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import kotlinx.coroutines.launch

private data class LicenseEntry(
    val name: String,
    val license: String,
    val url: String
)

private val licenses = listOf(
    LicenseEntry("rcheevos", "MIT", "github.com/RetroAchievements/rcheevos"),
    LicenseEntry("Oboe", "Apache 2.0", "github.com/google/oboe"),
    LicenseEntry("libretro-common", "MIT", "github.com/libretro/libretro-common"),
    LicenseEntry("AndroidX", "Apache 2.0", "developer.android.com/jetpack/androidx"),
    LicenseEntry("Jetpack Compose", "Apache 2.0", "developer.android.com/jetpack/compose"),
    LicenseEntry("Kotlin", "Apache 2.0", "kotlinlang.org"),
    LicenseEntry("Retrofit", "Apache 2.0", "github.com/square/retrofit"),
    LicenseEntry("OkHttp", "Apache 2.0", "github.com/square/okhttp"),
    LicenseEntry("Moshi", "Apache 2.0", "github.com/square/moshi"),
    LicenseEntry("Coil", "Apache 2.0", "github.com/coil-kt/coil"),
    LicenseEntry("Hilt", "Apache 2.0", "dagger.dev/hilt"),
    LicenseEntry("Room", "Apache 2.0", "developer.android.com/training/data-storage/room")
)

@Composable
fun LicensesDialog(onDismiss: () -> Unit) {
    val theme = LocalArgosyTheme.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val scrollStepPx = with(LocalDensity.current) { (Dimens.menuRowHeight * 3).toPx() }
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    val inputHandler = remember {
        object : InputHandler {
            private fun scroll(direction: Int) {
                scope.launch { scrollState.animateScrollBy(direction * scrollStepPx) }
            }

            override fun onUp(): InputResult {
                scroll(-1)
                return InputResult.HANDLED
            }

            override fun onDown(): InputResult {
                scroll(1)
                return InputResult.HANDLED
            }

            override fun onConfirm(): InputResult {
                currentOnDismiss()
                return InputResult.HANDLED
            }

            override fun onBack(): InputResult {
                currentOnDismiss()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }

            override fun onLeft(): InputResult = InputResult.HANDLED
            override fun onRight(): InputResult = InputResult.HANDLED
            override fun onMenu(): InputResult = InputResult.HANDLED
            override fun onSecondaryAction(): InputResult = InputResult.HANDLED
            override fun onContextMenu(): InputResult = InputResult.HANDLED
            override fun onPrevSection(): InputResult = InputResult.HANDLED
            override fun onNextSection(): InputResult = InputResult.HANDLED
            override fun onPrevTrigger(): InputResult = InputResult.HANDLED
            override fun onNextTrigger(): InputResult = InputResult.HANDLED
            override fun onSelect(): InputResult = InputResult.HANDLED
            override fun onLeftStickClick(): InputResult = InputResult.HANDLED
            override fun onRightStickClick(): InputResult = InputResult.HANDLED
            override fun onLongConfirm(): InputResult = InputResult.HANDLED
        }
    }

    ModalInputEffect(active = true, handler = inputHandler)

    ModalScaffold(
        visible = true,
        onDismiss = onDismiss,
        maxWidth = Dimens.modalWidthLg
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
        ) {
            Text(
                text = stringResource(R.string.settings_licenses_dialog_title),
                style = MaterialTheme.typography.titleLarge,
                color = theme.textPrimary,
                modifier = Modifier.padding(Dimens.spacingLg)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.spacingLg)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                licenses.forEach { entry ->
                    LicenseItem(entry)
                }

                Spacer(Modifier.height(Dimens.spacingMd))
                Text(
                    text = stringResource(R.string.settings_licenses_dialog_cores_heading),
                    style = MaterialTheme.typography.titleSmall,
                    color = theme.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(Dimens.spacingXs))
                Text(
                    text = stringResource(R.string.settings_licenses_dialog_cores_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textDim
                )
                Spacer(Modifier.height(Dimens.spacingSm))
            }

            HorizontalDivider(color = theme.hairlineLow)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs),
                horizontalArrangement = Arrangement.End
            ) {
                ActionButton(
                    label = stringResource(R.string.settings_licenses_dialog_close_button),
                    onClick = onDismiss,
                    primary = true,
                    focused = true
                )
            }
        }
    }
}

@Composable
private fun LicenseItem(entry: LicenseEntry) {
    val theme = LocalArgosyTheme.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = entry.url,
                style = MaterialTheme.typography.bodySmall,
                color = theme.textDim
            )
        }
        Spacer(Modifier.width(Dimens.spacingMd))
        Text(
            text = entry.license,
            style = MaterialTheme.typography.labelSmall,
            color = theme.focusAccent
        )
    }
}
