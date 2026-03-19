package com.nendo.argosy.ui.screens.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nendo.argosy.data.social.SocialUser
import com.nendo.argosy.data.social.UserProfileData

@Composable
fun ProfileTabContent(
    user: SocialUser?,
    userProfile: UserProfileData?,
    isLoadingProfile: Boolean,
    focusIndex: Int,
    listState: LazyListState
) {
    if (user == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Not connected",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        return
    }

    if (isLoadingProfile && userProfile == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary
            )
        }
        return
    }

    val mostPlayed = userProfile?.mostPlayed ?: emptyList()
    val mostPlayedFocusIndex = (focusIndex - PROFILE_DISPLAY_SECTIONS).coerceAtLeast(0)
    val highlightMostPlayed = focusIndex >= PROFILE_DISPLAY_SECTIONS

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
    ) {
        item {
            AccountInfoCard(user = user, profile = userProfile)
        }

        if (userProfile != null) {
            item {
                ProfileStatsGrid(
                    profile = userProfile,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                PlaytimeLineChart(
                    dailyPlaytime = userProfile.dailyPlaytime,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (mostPlayed.isNotEmpty()) {
                item {
                    SectionHeader("MOST PLAYED")
                }

                mostPlayed.forEachIndexed { index, game ->
                    item(key = "most_played_${game.igdbId}") {
                        MostPlayedGameItem(
                            game = game,
                            isFocused = highlightMostPlayed && index == mostPlayedFocusIndex,
                            isFirst = index == 0,
                            isLast = index == mostPlayed.size - 1,
                            onGameClick = { }
                        )
                    }
                }
            }
        }
    }
}

private const val PROFILE_DISPLAY_SECTIONS = 3

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}
