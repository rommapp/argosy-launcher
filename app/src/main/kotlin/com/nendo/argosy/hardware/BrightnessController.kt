package com.nendo.argosy.hardware

import com.nendo.argosy.util.SystemSettings
import javax.inject.Inject
import javax.inject.Singleton

data class DisplayBrightness(
    val primary: Float?,
    val secondary: Float?
)

@Singleton
class BrightnessController @Inject constructor(
    private val systemSettings: SystemSettings
) {
    fun getBrightness(): DisplayBrightness =
        DisplayBrightness(primary = systemSettings.screenBrightness(), secondary = null)

    fun setPrimaryBrightness(brightness: Float): Boolean =
        systemSettings.setScreenBrightness(brightness)
}
