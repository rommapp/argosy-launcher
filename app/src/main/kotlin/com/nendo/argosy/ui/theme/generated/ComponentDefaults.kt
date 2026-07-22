// AUTO-GENERATED. DO NOT EDIT.
// Source: design-system-docs/tokens.json
// Run: node scripts/gen-tokens.mjs

@file:Suppress("unused")

package com.nendo.argosy.ui.theme.generated

import com.nendo.argosy.data.cache.GradientPreset
import com.nendo.argosy.data.preferences.BoxArtBorderStyle
import com.nendo.argosy.data.preferences.BoxArtBorderThickness
import com.nendo.argosy.data.preferences.BoxArtCornerRadius
import com.nendo.argosy.data.preferences.BoxArtGlowStrength
import com.nendo.argosy.data.preferences.BoxArtInnerEffect
import com.nendo.argosy.data.preferences.BoxArtInnerEffectThickness
import com.nendo.argosy.data.preferences.BoxArtOuterEffect
import com.nendo.argosy.data.preferences.BoxArtOuterEffectThickness
import com.nendo.argosy.data.preferences.BoxArtShape
import com.nendo.argosy.data.preferences.DefaultView
import com.nendo.argosy.data.preferences.DisplayRoleOverride
import com.nendo.argosy.data.preferences.DualScreenInputFocus
import com.nendo.argosy.data.preferences.GlassBorderTint
import com.nendo.argosy.data.preferences.GlowColorMode
import com.nendo.argosy.data.preferences.GridDensity
import com.nendo.argosy.data.preferences.SystemIconPadding
import com.nendo.argosy.data.preferences.SystemIconPosition
import com.nendo.argosy.data.preferences.ThemeMode

object ComponentDefaults {
    object BoxArt {
        val shape = BoxArtShape.STANDARD
        val cornerRadius = BoxArtCornerRadius.MEDIUM
        val borderThickness = BoxArtBorderThickness.MEDIUM
        val borderStyle = BoxArtBorderStyle.SOLID
        val glassBorderTint = GlassBorderTint.OFF
        val glowStrength = BoxArtGlowStrength.MEDIUM
        val outerEffect = BoxArtOuterEffect.GLOW
        val outerEffectThickness = BoxArtOuterEffectThickness.THIN
        val innerEffect = BoxArtInnerEffect.SHADOW
        val innerEffectThickness = BoxArtInnerEffectThickness.MEDIUM
        val glowColorMode = GlowColorMode.AUTO
        val systemIconPosition = SystemIconPosition.TOP_LEFT
        val systemIconPadding = SystemIconPadding.MEDIUM
    }

    object Launcher {
        const val overlayLightAlpha = 0.3f
        const val overlayDarkAlpha = 0.7f
        const val focusGlowAlpha = 0.4f
        val themeMode = ThemeMode.SYSTEM
        val defaultView = DefaultView.HOME
        val gridDensity = GridDensity.NORMAL
        const val installedOnlyHome = false
        const val useAccentColorFooter = false
    }

    object Focus {
        const val scaleFocused = 1.1f
        const val scaleDefault = 1f
        const val alphaFocused = 1f
        const val alphaUnfocused = 0.85f
        const val saturationFocused = 1f
        const val saturationUnfocused = 0.3f
        const val glowAlphaFocused = 0.4f
        const val glowAlphaUnfocused = 0f
    }

    object ActionButton {
        const val restFillAlpha = 0.82f
        const val restRimAlpha = 0.9f
        const val disabledFillAlpha = 0.6f
    }

    object Toggle {
        const val trackWidth = 44
        const val trackHeight = 24
        const val trackRadius = 7
        const val knobWidth = 12
        const val knobHeight = 20
        const val knobRadius = 5
    }

    object TrackSlider {
        const val trackHeight = 6
        const val trackRadius = 2
        const val thumbSize = 10
        const val gradientShiftRatio = 0.1f
    }

    object ProgressBar {
        const val height = 6
        const val stripeWidth = 7
        const val stripeGap = 5
        const val gradientShiftRatio = 0.1f
    }

    object DownloadItem {
        const val rowHeight = 92
        const val thumbSize = 64
    }

    object VolumeMeter {
        const val height = 12
        const val radius = 2
        const val segmentGap = 2
        const val otherFillAlpha = 0.35f
    }

    object StorageLegend {
        const val swatchSize = 10
        const val gap = 12
    }

    object CategoryTile {
        const val minHeight = 52
    }

    object HoldButton {
        const val height = 44
        const val radius = 8
        const val fillAlpha = 0.3f
        const val holdDurationMs = 5000f
        const val tickMs = 50
        const val repeatStallMs = 300
        const val repeatGraceMs = 600
    }

    object Modal {
        const val blurRadius = 8
    }

    object Drawer {
        const val blurRadius = 24
    }

    object Background {
        const val blur = 40
        const val saturation = 100f
        const val opacity = 100
        const val useGameBackground = true
        val gradientPreset = GradientPreset.BALANCED
        const val gradientAdvanced = false
        const val videoWallpaperEnabled = false
        const val videoWallpaperDelaySeconds = 3
        const val videoWallpaperMuted = false
    }

    object ScreenDimmer {
        const val enabled = true
        const val timeoutMin = 2
        const val level = 50
    }

    object DualScreen {
        val displayRoleOverride = DisplayRoleOverride.AUTO
        val dualScreenInputFocus = DualScreenInputFocus.AUTO
    }

    object SurfaceTint {
        const val maxSaturationRatio = 0.3f
        const val valueLiftRatio = 0.12f
    }

    object SurfaceBackdrop {
        const val contentMaxAlpha = 0.25f
        const val wallpaperMaxAlpha = 0.5f
        const val driftCellsPerSecondRatio = 0.08f
        const val swayAmplitudeCellRatio = 0.35f
        const val swayPeriodSeconds = 24
        const val directionRingDiameter = 168
        const val cellSizeMinDp = 16
        const val cellSizeMaxDp = 120
        const val cellSizeStepDp = 8
        const val cellSizeDefaultDp = 104
        const val scatterMaxCellRatio = 0.35f
        const val jitterMaxScaleDropRatio = 0.5f
        const val jitterMaxRotationDegrees = 60
        const val stampCellRatio = 0.6f
    }

}
