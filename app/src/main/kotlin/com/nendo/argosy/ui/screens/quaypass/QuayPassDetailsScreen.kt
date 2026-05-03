package com.nendo.argosy.ui.screens.quaypass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun QuayPassDetailsScreen(
    onEditAvatar: () -> Unit,
    onClose: () -> Unit,
    viewModel: QuayPassDetailsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Meet QuayPass",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Pass other Argosy travelers when you're nearby. " +
                    "Share a Mii, a greeting, and the last game you played. " +
                    "Encounters appear in the Plaza.",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(Modifier.height(8.dp))

            FeatureBullet("Hands-free", "QuayPass works in the background. No notification, no popup, no interaction needed.")
            FeatureBullet("Private by default", "Your name, greeting, and recent game travel locally over Bluetooth. Nothing is uploaded.")
            FeatureBullet("Yours to design", "Build a Mii once. It travels with you across devices.")

            Spacer(Modifier.height(24.dp))

            if (!state.avatarConfigured) {
                Text(
                    text = "Step 1: Build your Mii",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "QuayPass requires you to create an avatar before it can be enabled.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onEditAvatar
                ) { Text("Build my Mii") }
            } else if (!state.enabled) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { viewModel.enableQuayPass() }
                ) { Text("Enable QuayPass") }
            } else {
                Text(
                    text = "QuayPass is on. Find encounters in the Plaza.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.avatarConfigured) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = onEditAvatar
                    ) { Text("Edit avatar") }
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onClose
                ) { Text(if (state.enabled) "Done" else "Maybe later") }
            }
        }
    }
}

@Composable
private fun FeatureBullet(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
