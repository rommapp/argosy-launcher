package com.nendo.argosy.ui.components

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.ui.common.RunningGameUi
import com.nendo.argosy.ui.common.runDurationText
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalLauncherTheme

/**
 * The words one surface uses for a running game. [runningWithTime] takes the play time as its
 * argument; a surface without it shows the time as separate dimmed text after [running].
 */
data class RunningLineLabels(
    @StringRes val running: Int,
    @StringRes val runningIn: Int,
    @StringRes val saving: Int,
    @StringRes val runningWithTime: Int? = null
)

/**
 * The words of a running line: [lead] in the running green, then [trailingTime] dimmed when the
 * surface shows the play time apart from it.
 */
data class RunningLineText(val lead: String, val trailingTime: String?)

/**
 * "Running" with this run's play time, "Running in" the emulator for a game an external emulator
 * holds, or "Saving" while the game closes, in [labels]' wording.
 */
fun runningLineText(resources: Resources, running: RunningGameUi, labels: RunningLineLabels): RunningLineText {
    val showsTime = !running.isClosing && running.playedThisRunMs >= MIN_SHOWN_PLAY_MS
    val duration = if (showsTime) runDurationText(resources, running.playedThisRunMs) else null
    val emulatorName = if (running.isBuiltIn) null else EmulatorRegistry.getByPackage(running.emulatorPackage)?.displayName
    val withTime = labels.runningWithTime
    val lead = when {
        running.isClosing -> resources.getString(labels.saving)
        emulatorName != null -> resources.getString(labels.runningIn, emulatorName)
        withTime != null && duration != null -> resources.getString(withTime, duration)
        else -> resources.getString(labels.running)
    }
    return RunningLineText(lead, duration?.takeIf { withTime == null || emulatorName != null })
}

@Composable
fun RunningSubLine(
    running: RunningGameUi,
    labels: RunningLineLabels,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    timeColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val success = LocalLauncherTheme.current.semanticColors.success
    val resources = LocalContext.current.resources
    val configuration = LocalConfiguration.current
    val text = remember(running, labels, resources, configuration) { runningLineText(resources, running, labels) }
    val lead = text.lead
    val trailingTime = text.trailingTime
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Text(
            text = lead,
            style = style,
            fontWeight = FontWeight.Medium,
            color = success,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (trailingTime != null) {
            Text(
                text = trailingTime,
                style = style,
                color = timeColor,
                maxLines = 1
            )
        }
    }
}

private const val MIN_SHOWN_PLAY_MS = 60_000L
