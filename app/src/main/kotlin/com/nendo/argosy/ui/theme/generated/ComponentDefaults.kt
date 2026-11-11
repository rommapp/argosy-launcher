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
    object Presentation {
        const val edgeAlpha = 0.4f
        const val edgeClearRatio = 0.5f
        const val titleCenterAlpha = 0.5f
        const val titleMidAlpha = 0.32f
        const val titleMidRatio = 0.6f
        const val titleOverscanRatio = 0.2f
    }

    object GameShowcase {
        const val backdropCrossfadeMs = 380
        const val enterMs = 320
        const val coverDelayMs = 60
        const val textDelayMs = 120
        const val exitMs = 120
        const val coverDriftDp = 8
        const val textRiseDp = 12
        const val floatTravelDp = 6
        const val floatSlideMs = 2400
        const val floatHoldMs = 1600
    }

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
        const val sweepMs = 1400
        const val sweepWidthRatio = 0.35f
    }

    object DownloadItem {
        const val rowHeight = 92
        const val thumbSize = 64
    }

    object GameListRow {
        const val rowHeight = 84
        const val compactRowMinHeight = 32
        const val compactArtworkAspectRatio = 0.75f
        const val compactArtworkMaxWidthRatio = 0.45f
        const val footerHeight = 22
        const val footerSecondTabAlpha = 0.55f
        const val footerThirdTabAlpha = 0.3f
        const val focusBorderWidth = 3
        const val completionBarWidth = 64
        const val completionBarHeight = 4
        const val friendAvatarSize = 20
        const val maxFriendAvatars = 3
        const val friendOverlapRatio = 0.35f
        const val estimateFillAlphaDark = 0.35f
        const val estimateFillAlphaLight = 0.45f
    }

    object CollectionCell {
        const val rowHeight = 132
        const val mosaicCoverGap = 2
        const val mosaicScrollDpPerSecond = 90
        const val mosaicScrollRampMs = 700
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

    object PlayTimeChart {
        const val seriesSlots = 6
        const val surfaceGap = 2
        const val waveRowHeight = 24
        const val waveRowGap = 2
        const val waveLabelWidth = 36
        const val waveSamplesPerHour = 6
        const val waveMinAmplitude = 1
        const val waveRestAlpha = 0.55f
        const val waveEdgeAlpha = 0.6f
        const val calendarCell = 18
        const val calendarLabelWidth = 30
        const val calendarLevels = 4
        const val calendarEmptyAlpha = 0.35f
        const val calendarRampFloor = 0.35f
        const val calendarDetailRows = 6
        const val calendarDetailCover = 28
        const val calendarWeeks = 26
        const val calendarPanelWidth = 300
        const val mosaicHeight = 220
        const val mosaicMinShareRatio = 0.04f
        const val mosaicMaxTiles = 12
        const val mosaicFallbackAlpha = 0.25f
        const val mosaicCollageAlpha = 0.7f
        const val mosaicCollageCell = 40
        const val listCoverWidth = 34
        const val legendSwatch = 10
        const val legendRowsPerColumn = 3
        const val factsColumns = 3
        const val factsTrimRatio = 0.1f
        const val legendShareWidth = 44
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

    object Carousel {
        const val compactWidthDp = 600
        const val headerEdgePaddingDp = 12
        const val edgeTouchHeightDp = 80
        const val badgeHeadroomDp = 24
        const val newBadgeOverflowDp = 20
        const val newBadgeWidthDp = 44
        const val newBadgeHeightDp = 30
        const val newBadgeRotationDegrees = 15
        const val newBadgeCornerShiftDp = 8
        const val newBadgeCornerDropDp = 3
        const val companionCardWidth = 140
        const val companionCardWidthFocused = 200
        const val companionCardGap = 16
        const val focusSnapOffsetPx = -25
        const val viewAllIconSize = 28
        const val dotSize = 6
        const val dotSizeActive = 10
        const val dotGap = 6
        const val dotInactiveAlpha = 0.3f
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

    object OverlayLegibility {
        const val scrimPaddingHorizDp = 12
        const val scrimPaddingVertDp = 6
    }

    object BatteryIndicator {
        const val terminalWidthDp = 3
    }

    object MediaBackdrop {
        const val blurScale = 0.5f
        const val scrimTopAlphaDark = 0.5f
        const val scrimBottomAlphaDark = 0.9f
        const val scrimTopAlphaLight = 0.3f
        const val scrimBottomAlphaLight = 0.7f
        const val surfaceAlpha = 0.6f
        const val requestWidth = 640
        const val requestHeight = 360
        const val crossfadeMs = 380
    }

    object MediaCover {
        const val crossfadeMs = 160
    }

    object BoxArtBorder {
        const val glassBlurDp = 8
        val glassRingBlursDp = listOf(24f, 12f, 6f, 3f, 1.5f, 0.8f, 0.4f, 0.1f)
    }

    object DownloadCover {
        const val waveAmplitudeDp = 5
    }

    object CustomGrid {
        const val swipePageThresholdDp = 48
    }

    object CollectionShowcase {
        const val coverScrollMsPerCover = 1400
        const val coverScrollDwellMs = 2200
        const val coversAcross = 6
    }

    object HomeLayoutPreview {
        const val headerBarWidthRatio = 0.3f
        const val titleBarWidthRatio = 0.42f
        const val subtitleBarWidthRatio = 0.24f
        const val footerBarWidthRatio = 0.55f
        const val sectionHeadingWidthRatio = 0.22f
        const val coverTitleBarWidthRatio = 0.7f
        const val focusedTitleBarWidthRatio = 0.34f
        const val badgeWidthRatio = 0.45f
        const val restingBlockAlpha = 0.45f
        const val maxHeightDp = 260
        const val stepDwellMs = 900
        const val scrollCycleMs = 9000
        const val pageDwellMs = 1800
    }

    object PageChooser {
        const val modalWidthDp = 520
        const val rowHeightDp = 64
        const val previewSizeDp = 48
    }

    object LaunchOverlay {
        const val scrimAlphaDark = 0.8f
        const val scrimAlphaLight = 0.55f
        const val progressWidthDp = 280
        const val fadeMs = 300
        const val veilMs = 700
        const val arrivalMs = 400
        const val audioRiseMs = 1000
        const val patienceMs = 2500
        const val reflowMs = 900
        const val statusFadeMs = 700
        const val statusFadeDelayMs = 500
        const val etaTickMs = 250
        const val artHeightDp = 240
        const val artHeightRatio = 0.4f
        const val titleMaxLines = 2
        const val breathPeakAlpha = 0.18f
        const val breathFloorAlpha = 0f
        const val breathSpreadDp = 28
        const val breathBlurDp = 24
        const val makeRoomCardWidthDp = 240
        const val makeRoomClosedHoldMs = 600
        const val makeRoomDeepScrimAlpha = 0.6f
        const val makeRoomClosedScale = 0.85f
        const val makeRoomClosedAlpha = 0.55f
        const val makeRoomClosedSaturation = 0f
    }

    object RunningLine {
        const val heightDp = 3
        const val edgeGapDp = 1
        const val minEndInsetDp = 8
        const val glowHeightDp = 16
        const val glowSpreadRatio = 0.12f
        const val lineTroughAlpha = 0.7f
        const val linePeakAlpha = 1f
        const val glowTroughAlpha = 0.45f
        const val glowPeakAlpha = 0.85f
        const val badgeGapDp = 4
        const val savingDashDp = 4
        const val savingGapDp = 3
        const val savingGlowAlpha = 0.35f
    }

    object ResumeOverlay {
        const val backdropDownscale = 8f
    }

    object RunningStrip {
        const val unitWidthDp = 7
        const val unitHeightDp = 11
        const val unitGapDp = 2
        const val unitRadiusDp = 2
        const val externalGapDp = 4
        const val loadedAlpha = 0.7f
        const val externalAlpha = 0.45f
        const val closingStripeDp = 2
    }

    object RunningFrame {
        const val panelMaxWidthDp = 320
        const val statsScrimAlpha = 0.7f
    }

    object UnlockRecap {
        const val cardMaxWidthDp = 520
        const val monthlyCardMaxWidthDp = 640
        const val monthlyStackWidthDp = 560
        const val shortHeightDp = 400
        const val rowHeightDp = 104
        const val shortRowHeightDp = 88
        const val tileSizeDp = 80
        const val shortTileSizeDp = 64
        const val tileBorderDp = 4
        const val tileBadgeInsetDp = 8
        const val tileGlowRadiusDp = 14
        const val fadeWidthDp = 32
        const val bodyFadeHeightDp = 24
        const val scrollStepDp = 120
        const val presenceRuleDp = 3
        const val trackAlpha = 0.22f
        const val glowAlpha = 0.65f
        const val popScale = 1.1f
        const val dimmedAlpha = 0.6f
        const val scrollLeadMs = 250
        const val borderDrawMs = 450
        const val popMs = 120
        const val settleMs = 180
        const val perTileMs = 2500
        const val revealVisibleFraction = 0.6f
        const val favoriteCoverWidthDp = 64
        const val favoriteCoverHeightDp = 88
        const val calendarCellMinDp = 20
        const val calendarCellMaxDp = 24
        const val calendarGapDp = 3
        const val calendarRingInsetDp = 4
        const val calendarEmptyFillAlpha = 0.1f
        const val calendarOutsideFillAlpha = 0.035f
    }

    object InlineTilePlayer {
        const val positionPollMs = 500
        const val overlayScrimAlpha = 0.55f
        const val posterIconAlpha = 0.45f
        const val audioFadeMs = 450
    }

    object AutoGrid {
        const val entryStartScale = 0.6f
        const val entryMs = 200
        const val entryStepMs = 20
    }

    object ArtPicker {
        const val stateHeightDp = 160
        const val checkerCellDp = 8
    }

    object AlphabetRail {
        const val widthDp = 40
        const val backgroundAlpha = 0.8f
    }

    object MusicPlayer {
        const val coverSizeDp = 64
        const val coverExpandedMaxHeightDp = 280
        const val transportButtonDp = 40
        const val playButtonDp = 48
        const val progressHeightDp = 4
        const val rowCoverDp = 40
        const val positionPollMs = 500
        const val searchDebounceMs = 350
        const val artBloomAlphaDark = 0.35f
        const val artBloomAlphaLight = 0.18f
    }

    object Segmented {
        const val heightDp = 32
        const val gapDp = 2
        const val trackPaddingDp = 2
        const val minSegmentWidthDp = 56
    }

    object SwitchedSlider {
        const val chipSizeDp = 32
        const val chipRadiusDp = 6
        const val offTrackAlpha = 0.4f
    }

    object QuickPanel {
        const val selectedFillAlpha = 0.108f
        const val offlineAvatarAlpha = 0.6f
        const val disabledContentAlpha = 0.38f
        const val sectionLabelTrackingSp = 1.5f
        const val statsSparklineHeightDp = 20
        const val statsSparklineStrokeDp = 1.5f
        const val statsSparklineAlpha = 0.55f
        const val statsTileFillAlpha = 0.5f
        const val statsTempWarmC = 70
        const val statsTempHotC = 85
    }

    object Connections {
        const val forgetHoldMs = 3000
        const val pulseRingScale = 1.5f
        const val pulseRingAlpha = 0.45f
        const val pairArcDegrees = 90
        const val reducedRingAlpha = 0.5f
        const val foundRiseDp = 12
        const val successTintAlpha = 0.15f
        const val successHoldMs = 600
    }

    object QuickMenuRandom {
        const val coverHeightRatio = 0.66f
        const val coverWidthMaxRatio = 0.4f
        const val coverAspectRatio = 0.75f
        const val backdropBlurDp = 32
        const val backdropScrimStartAlpha = 0.55f
        const val backdropScrimEndAlpha = 0.85f
        const val descriptionMaxLines = 3
    }

    object FrostedSurface {
        const val fillAlpha = 0.8f
        const val chromeHeightDp = 52
        const val compactHeaderViewportHeightDp = 480
        const val compactChromeVerticalPaddingDp = 6
        const val minimumTouchTargetDp = 48
        const val chromeFontSizeSp = 20
        const val navigationTriggerViewportToPaintedHeightRatio = 1.5f
        const val navigationInactiveFontSizeSp = 18
        const val navigationMaxWidthDp = 480
        const val statusBatteryFontSizeRatio = 0.75f
        const val statusPaddingHorizontalDp = 24
        const val footerLabelFontSizeSp = 14
    }

    object Notification {
        const val surfaceAlpha = 0.92f
        const val tintAlpha = 0.15f
        const val bodyAlpha = 0.7f
        const val badgeHeightDp = 16
        const val badgeMinWidthDp = 22
        const val badgeFontSizeSp = 10
        const val badgeFillAlpha = 0.3f
        const val bodyGlyphSizeRatio = 1.1f
    }

    object ControllerKeyboard {
        const val sheetTargetHeightPercent = 45f
        const val sheetMaxHeightPercent = 50f
        const val keyHeightMinDp = 32
        const val compactMaxWindowHeightDp = 480
        const val sizingRowCount = 5
        const val echoFullExtraLineDp = 30
        const val echoFullMaxLines = 3
        const val counterNearLimitPercent = 10f
    }

    object PairingCode {
        const val cellWidthDp = 36
        const val cellHeightDp = 44
    }

    object Setup {
        const val railSegmentHeightDp = 4
        const val railSegmentGapDp = 4
        const val railDoneAlpha = 0.45f
        const val faceButtonDp = 56
        const val miniFaceButtonDp = 24
        const val menuPillWidthDp = 64
        const val menuPillHeightDp = 24
        const val chipHeightDp = 24
        const val chipGrantedFillAlpha = 0.15f
    }

}
