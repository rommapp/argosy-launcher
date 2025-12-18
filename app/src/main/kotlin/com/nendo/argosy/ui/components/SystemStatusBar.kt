package com.nendo.argosy.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

data class BatteryState(
    val level: Int = 100,
    val isCharging: Boolean = false
)

@Composable
fun rememberBatteryState(): State<BatteryState> {
    val context = LocalContext.current
    val batteryState = remember { mutableStateOf(BatteryState()) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                batteryState.value = BatteryState(
                    level = (level * 100) / scale,
                    isCharging = isCharging
                )
            }
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val sticky = context.registerReceiver(receiver, filter)

        sticky?.let { intent ->
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            batteryState.value = BatteryState(
                level = (level * 100) / scale,
                isCharging = isCharging
            )
        }

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    return batteryState
}

@Composable
fun SystemStatusBar(
    modifier: Modifier = Modifier,
    contentColor: Color = Color.Unspecified
) {
    val effectiveColor = if (contentColor == Color.Unspecified) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    } else {
        contentColor
    }
    val currentTime = remember { mutableLongStateOf(System.currentTimeMillis()) }
    val batteryState by rememberBatteryState()

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
            currentTime.longValue = System.currentTimeMillis()
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
                .format(java.util.Date(currentTime.longValue)),
            style = MaterialTheme.typography.titleMedium,
            color = effectiveColor
        )

        BatteryIndicator(
            level = batteryState.level,
            isCharging = batteryState.isCharging,
            color = effectiveColor
        )
    }
}

@Composable
private fun BatteryIndicator(
    level: Int,
    isCharging: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        BatteryIcon(
            level = level,
            isCharging = isCharging,
            color = color,
            modifier = Modifier.size(width = 24.dp, height = 12.dp)
        )
        Text(
            text = "$level%",
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}

@Composable
private fun BatteryIcon(
    level: Int,
    isCharging: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val fillColor = when {
        isCharging -> Color(0xFF4CAF50)
        level <= 20 -> Color(0xFFE53935)
        else -> color
    }

    Canvas(modifier = modifier) {
        val bodyWidth = size.width - 4.dp.toPx()
        val bodyHeight = size.height
        val cornerRadius = 2.dp.toPx()
        val strokeWidth = 1.5f.dp.toPx()
        val padding = strokeWidth

        drawRoundRect(
            color = color,
            topLeft = Offset(0f, 0f),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
            style = Stroke(width = strokeWidth)
        )

        drawRect(
            color = color,
            topLeft = Offset(bodyWidth, bodyHeight * 0.3f),
            size = Size(3.dp.toPx(), bodyHeight * 0.4f)
        )

        val fillWidth = ((bodyWidth - padding * 2) * (level / 100f)).coerceAtLeast(0f)
        if (fillWidth > 0) {
            drawRoundRect(
                color = fillColor,
                topLeft = Offset(padding, padding),
                size = Size(fillWidth, bodyHeight - padding * 2),
                cornerRadius = CornerRadius(cornerRadius / 2, cornerRadius / 2)
            )
        }

        if (isCharging) {
            val centerX = bodyWidth / 2
            val centerY = bodyHeight / 2
            val boltSize = bodyHeight * 0.6f

            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(centerX + boltSize * 0.1f, centerY - boltSize * 0.4f)
                lineTo(centerX - boltSize * 0.2f, centerY)
                lineTo(centerX + boltSize * 0.05f, centerY)
                lineTo(centerX - boltSize * 0.1f, centerY + boltSize * 0.4f)
                lineTo(centerX + boltSize * 0.2f, centerY)
                lineTo(centerX - boltSize * 0.05f, centerY)
                close()
            }
            drawPath(path, Color.White)
        }
    }
}
