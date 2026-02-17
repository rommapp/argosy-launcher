package com.nendo.argosy.ui.screens.settings.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nendo.argosy.ui.theme.Dimens

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
    val listState = rememberLazyListState()
    val canScrollDown by remember {
        derivedStateOf {
            listState.canScrollForward
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(Dimens.radiusXl),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Open Source Licenses",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(Dimens.spacingLg)
                )

                Box(modifier = Modifier.weight(1f, fill = false)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp)
                            .padding(horizontal = Dimens.spacingLg),
                        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                    ) {
                        items(licenses, key = { it.name }) { entry ->
                            LicenseItem(entry)
                        }

                        item {
                            Spacer(Modifier.height(Dimens.spacingMd))
                            Column {
                                Text(
                                    text = "Emulator Cores",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Cores are downloaded from the libretro buildbot. See docs.libretro.com/development/licenses for core licenses.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(Dimens.spacingSm))
                        }
                    }

                    if (canScrollDown) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(24.dp)
                                .background(
                                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                                            MaterialTheme.colorScheme.surface
                                        )
                                    )
                                )
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.alpha(0.5f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun LicenseItem(entry: LicenseEntry) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = entry.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(Dimens.spacingMd))
        Text(
            text = entry.license,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
