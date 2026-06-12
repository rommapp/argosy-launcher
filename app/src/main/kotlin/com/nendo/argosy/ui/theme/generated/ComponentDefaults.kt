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

}
