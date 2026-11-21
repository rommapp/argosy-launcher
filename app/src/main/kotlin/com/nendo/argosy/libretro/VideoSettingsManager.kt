package com.nendo.argosy.libretro

import android.graphics.RectF
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nendo.argosy.data.local.dao.PlatformLibretroSettingsDao
import com.nendo.argosy.data.local.entity.PlatformLibretroSettingsEntity
import com.nendo.argosy.data.preferences.BuiltinEmulatorSettings
import com.nendo.argosy.data.preferences.EffectiveLibretroSettingsResolver
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.libretro.frame.FrameRegistry
import com.nendo.argosy.libretro.shader.ShaderRegistry
import com.nendo.argosy.core.emulator.AudioLatencySteps
import com.nendo.argosy.core.emulator.LibretroSettingDef
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.ShaderConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class VideoSettingsManager(
    private val platformId: Long,
    private val platformSlug: String,
    private val globalSettings: BuiltinEmulatorSettings,
    private val platformLibretroSettingsDao: PlatformLibretroSettingsDao,
    private val effectiveLibretroSettingsResolver: EffectiveLibretroSettingsResolver,
    private val preferencesRepository: UserPreferencesRepository,
    private val frameRegistry: FrameRegistry,
    private val scope: CoroutineScope,
    private val shaderRegistryProvider: () -> ShaderRegistry,
    private val getRetroView: () -> GLRetroView,
    private val setAudioVolume: (Float) -> Unit
) {
    var currentShader by mutableStateOf("None")
    var resolvedCustomShader: ShaderConfig = ShaderConfig.Default
    var currentFilter by mutableStateOf("Auto")
    var currentAspectRatio by mutableStateOf("Core Provided")
    var currentPortraitPosition by mutableStateOf("Auto")
    var currentRotation by mutableStateOf("Auto")
    var currentOverscanCrop by mutableStateOf("Off")
    var currentBFI by mutableStateOf(false)
    var currentFastForwardEnabled by mutableStateOf(true)
    var currentFastForwardSpeed by mutableStateOf("4x")
    var currentRewindEnabled by mutableStateOf(true)
    var currentSkipDupFrames by mutableStateOf(false)
    var currentLowLatencyAudio by mutableStateOf(true)
    var currentAudioVolume by mutableStateOf("100%")
    var currentAudioLatency by mutableStateOf(AudioLatencySteps.AUTO_TOKEN)
    var currentVSync by mutableStateOf(true)
    var currentRewindSpeed by mutableStateOf("1x")
    var currentRewindBufferDuration by mutableStateOf("15s")
    var currentRumbleEnabled by mutableStateOf(true)
    var currentMotionEnabled by mutableStateOf(true)
    var currentAnalogAsDpad by mutableStateOf(false)
    var currentDpadAsAnalog by mutableStateOf(false)
    var currentFrame by mutableStateOf<String?>(null)

    var aspectRatioMode: String = "Auto"
    var fastForwardEnabled: Boolean = true
    var fastForwardSpeed: Int = 4
    // Compose-observable so InGameSettingsScreen rows refresh when toggled via
    // the in-game controls tab. Plain `var`s here silently dropped updates on
    // the UI side even though videoSettings itself held the new value.
    var fastForwardMode: com.nendo.argosy.data.local.entity.FastForwardMode by mutableStateOf(
        com.nendo.argosy.data.local.entity.FastForwardMode.HOLD
    )
    var fastForwardPreservePitch: Boolean by mutableStateOf(false)
    var overscanCrop: Int = 0
    var rotationDegrees: Int = -1
    var rewindEnabled: Boolean = false

    private var screenWidth: Int = 0
    private var screenHeight: Int = 0

    var onRewindToggled: ((enabled: Boolean) -> Unit)? = null
    var onRewindConfigChanged: (() -> Unit)? = null
    var onPortraitPositionChanged: ((String) -> Unit)? = null

    fun applySettings(settings: BuiltinEmulatorSettings) {
        aspectRatioMode = settings.aspectRatio
        fastForwardEnabled = settings.fastForwardEnabled
        fastForwardSpeed = settings.fastForwardSpeed
        fastForwardMode = settings.fastForwardMode
        fastForwardPreservePitch = settings.fastForwardPreservePitch
        overscanCrop = settings.overscanCrop
        rotationDegrees = settings.rotation
        rewindEnabled = settings.rewindEnabled
        currentShader = settings.shader
        currentFilter = settings.filter
        currentAspectRatio = settings.aspectRatio
        currentPortraitPosition = settings.portraitPosition
        currentRotation = settings.rotationDisplay
        currentOverscanCrop = settings.overscanCropDisplay
        currentBFI = settings.blackFrameInsertion
        currentFastForwardEnabled = settings.fastForwardEnabled
        currentFastForwardSpeed = settings.fastForwardSpeedDisplay
        currentRewindEnabled = settings.rewindEnabled
        currentSkipDupFrames = settings.skipDuplicateFrames
        currentLowLatencyAudio = settings.lowLatencyAudio
        currentAudioVolume = settings.audioVolumeDisplay
        currentAudioLatency = settings.audioLatencyDisplay
        currentVSync = !settings.forceSoftwareTiming
        currentRewindSpeed = settings.rewindSpeedDisplay
        currentRewindBufferDuration = settings.rewindBufferDurationDisplay
        currentRumbleEnabled = settings.rumbleEnabled
        currentMotionEnabled = settings.motionEnabled
        currentAnalogAsDpad = settings.analogAsDpad
        currentDpadAsAnalog = settings.dpadAsAnalog
        currentFrame = settings.frame
        scope.launch {
            val stored = platformLibretroSettingsDao.getByPlatformId(platformId)
            frameOffsetX = stored?.frameOffsetX ?: 0f
            frameOffsetY = stored?.frameOffsetY ?: 0f
            frameZoom = stored?.frameZoom ?: 1f
        }
    }

    fun resolveCustomShader(settings: BuiltinEmulatorSettings) {
        if (settings.shader == "Custom") {
            resolvedCustomShader = shaderRegistryProvider().resolveChain(settings.shaderChainConfig)
        }
    }

    fun setScreenSize(width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
    }

    fun getVideoSettingValue(setting: LibretroSettingDef): String = when (setting) {
        LibretroSettingDef.Shader -> currentShader
        LibretroSettingDef.Filter -> currentFilter
        LibretroSettingDef.AspectRatio -> currentAspectRatio
        LibretroSettingDef.PortraitPosition -> currentPortraitPosition
        LibretroSettingDef.Rotation -> currentRotation
        LibretroSettingDef.OverscanCrop -> currentOverscanCrop
        LibretroSettingDef.Frame -> currentFrame?.let {
            frameRegistry.findById(it)?.displayName
        } ?: "None"
        LibretroSettingDef.BlackFrameInsertion -> currentBFI.toString()
        LibretroSettingDef.FastForwardEnabled -> currentFastForwardEnabled.toString()
        LibretroSettingDef.FastForwardSpeed -> currentFastForwardSpeed
        LibretroSettingDef.RewindEnabled -> currentRewindEnabled.toString()
        LibretroSettingDef.SkipDuplicateFrames -> currentSkipDupFrames.toString()
        LibretroSettingDef.LowLatencyAudio -> currentLowLatencyAudio.toString()
        LibretroSettingDef.AudioVolume -> currentAudioVolume
        LibretroSettingDef.AudioLatency -> currentAudioLatency
        LibretroSettingDef.VSync -> currentVSync.toString()
        LibretroSettingDef.RewindSpeed -> currentRewindSpeed
        LibretroSettingDef.RewindBufferDuration -> currentRewindBufferDuration
        LibretroSettingDef.AutoSaveState,
        LibretroSettingDef.AutoRestoreState,
        LibretroSettingDef.PreferNewerServerSave,
        LibretroSettingDef.HwCoreSaveStates -> ""
    }

    fun getGlobalVideoSettingValue(setting: LibretroSettingDef): String = when (setting) {
        LibretroSettingDef.Shader -> globalSettings.shader
        LibretroSettingDef.Filter -> globalSettings.filter
        LibretroSettingDef.AspectRatio -> globalSettings.aspectRatio
        LibretroSettingDef.PortraitPosition -> globalSettings.portraitPosition
        LibretroSettingDef.Rotation -> globalSettings.rotationDisplay
        LibretroSettingDef.OverscanCrop -> globalSettings.overscanCropDisplay
        LibretroSettingDef.Frame -> getGlobalFrameForPlatform()?.let {
            frameRegistry.findById(it)?.displayName
        } ?: "None"
        LibretroSettingDef.BlackFrameInsertion -> globalSettings.blackFrameInsertion.toString()
        LibretroSettingDef.FastForwardEnabled -> globalSettings.fastForwardEnabled.toString()
        LibretroSettingDef.FastForwardSpeed -> globalSettings.fastForwardSpeedDisplay
        LibretroSettingDef.RewindEnabled -> globalSettings.rewindEnabled.toString()
        LibretroSettingDef.SkipDuplicateFrames -> globalSettings.skipDuplicateFrames.toString()
        LibretroSettingDef.LowLatencyAudio -> globalSettings.lowLatencyAudio.toString()
        LibretroSettingDef.AudioVolume -> globalSettings.audioVolumeDisplay
        LibretroSettingDef.AudioLatency -> globalSettings.audioLatencyDisplay
        LibretroSettingDef.VSync -> (!globalSettings.forceSoftwareTiming).toString()
        LibretroSettingDef.RewindSpeed -> globalSettings.rewindSpeedDisplay
        LibretroSettingDef.RewindBufferDuration -> globalSettings.rewindBufferDurationDisplay
        LibretroSettingDef.AutoSaveState,
        LibretroSettingDef.AutoRestoreState,
        LibretroSettingDef.PreferNewerServerSave,
        LibretroSettingDef.HwCoreSaveStates -> ""
    }

    /**
     * Netplay and speedrun runs give the whole surface to the game: a frame shrinks it, and both
     * modes are judged on what the player can see.
     */
    var framesSuppressed: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            applyFrame(currentFrame)
        }

    var frameOffsetX by mutableStateOf(0f)
    var frameOffsetY by mutableStateOf(0f)
    var frameZoom by mutableStateOf(1f)

    /**
     * Whether the game can be moved and zoomed under [frameId]: with no frame, or on a plate that
     * places the game itself. A cutout bezel fixes where the game shows through, and a suppressed
     * frame hands the whole surface to the game.
     */
    fun isFrameAdjustable(frameId: String?): Boolean {
        if (framesSuppressed) return false
        return frameId == null || frameRegistry.findById(frameId)?.screenRect != null
    }

    fun adjustFrame(frameId: String?, offsetX: Float, offsetY: Float, zoom: Float) {
        frameOffsetX = (frameOffsetX + offsetX).coerceIn(-0.5f, 0.5f)
        frameOffsetY = (frameOffsetY + offsetY).coerceIn(-0.5f, 0.5f)
        frameZoom = (frameZoom * zoom).coerceIn(0.2f, 3f)
        applyFrame(frameId)
    }

    fun resetFrameAdjustment(frameId: String?) {
        frameOffsetX = 0f
        frameOffsetY = 0f
        frameZoom = 1f
        applyFrame(frameId)
    }

    fun persistFrameAdjustment() {
        val hasOverride = frameOffsetX != 0f || frameOffsetY != 0f || frameZoom != 1f
        scope.launch {
            val current = platformLibretroSettingsDao.getByPlatformId(platformId)
                ?: PlatformLibretroSettingsEntity(platformId = platformId)
            platformLibretroSettingsDao.upsert(
                current.copy(
                    frameOffsetX = frameOffsetX.takeIf { hasOverride },
                    frameOffsetY = frameOffsetY.takeIf { hasOverride },
                    frameZoom = frameZoom.takeIf { hasOverride }
                )
            )
        }
    }

    fun applyFrame(frameId: String?) {
        val retroView = getRetroView()
        val entry = frameId?.takeUnless { framesSuppressed }?.let { frameRegistry.findById(it) }
        val bitmap = entry?.let { frameRegistry.loadFrame(it.id) }

        if (bitmap == null) {
            retroView.clearBackgroundFrame()
            retroView.backgroundFrameBehind = false
            retroView.viewport = if (frameId == null && !framesSuppressed) {
                adjustedViewport(FULL_SURFACE)
            } else {
                RectF(0f, 0f, 1f, 1f)
            }
            return
        }

        val rect = entry.screenRect
        retroView.backgroundFrameBehind = rect != null
        retroView.viewport = if (rect != null) adjustedViewport(rect) else RectF(0f, 0f, 1f, 1f)
        retroView.setBackgroundFrame(bitmap)
    }

    private fun adjustedViewport(rect: FrameRegistry.ScreenRect): RectF {
        val centerX = (rect.left + rect.right) / 2f + frameOffsetX
        val centerY = (rect.top + rect.bottom) / 2f + frameOffsetY
        val halfWidth = (rect.right - rect.left) / 2f * frameZoom
        val halfHeight = (rect.bottom - rect.top) / 2f * frameZoom
        return RectF(
            centerX - halfWidth,
            centerY - halfHeight,
            centerX + halfWidth,
            centerY + halfHeight
        )
    }

    private fun getGlobalFrameForPlatform(): String? {
        if (!globalSettings.framesEnabled) return null
        return frameRegistry.getFramesForPlatform(platformSlug).firstOrNull()?.id
    }

    fun resetVideoSetting(setting: LibretroSettingDef) {
        val globalValue = getGlobalVideoSettingValue(setting)
        when (setting) {
            LibretroSettingDef.Shader -> currentShader = globalValue
            LibretroSettingDef.Filter -> currentFilter = globalValue
            LibretroSettingDef.AspectRatio -> currentAspectRatio = globalValue
            LibretroSettingDef.PortraitPosition -> currentPortraitPosition = globalValue
            LibretroSettingDef.Rotation -> currentRotation = globalValue
            LibretroSettingDef.OverscanCrop -> currentOverscanCrop = globalValue
            LibretroSettingDef.FastForwardEnabled -> currentFastForwardEnabled = globalValue.toBooleanStrictOrNull() ?: true
            LibretroSettingDef.FastForwardSpeed -> currentFastForwardSpeed = globalValue
            LibretroSettingDef.AudioVolume -> currentAudioVolume = globalValue
            LibretroSettingDef.AudioLatency -> currentAudioLatency = globalValue
            LibretroSettingDef.Frame -> currentFrame = getGlobalFrameForPlatform()
            else -> {}
        }
        when (setting) {
            LibretroSettingDef.BlackFrameInsertion -> {
                currentBFI = globalSettings.blackFrameInsertion
                applyVideoSettingChange(setting, currentBFI.toString())
            }
            LibretroSettingDef.RewindEnabled -> {
                currentRewindEnabled = globalSettings.rewindEnabled
                applyVideoSettingChange(setting, currentRewindEnabled.toString())
            }
            LibretroSettingDef.SkipDuplicateFrames -> {
                currentSkipDupFrames = globalSettings.skipDuplicateFrames
                applyVideoSettingChange(setting, currentSkipDupFrames.toString())
            }
            LibretroSettingDef.LowLatencyAudio -> {
                currentLowLatencyAudio = globalSettings.lowLatencyAudio
                applyVideoSettingChange(setting, currentLowLatencyAudio.toString())
            }
            LibretroSettingDef.VSync -> {
                currentVSync = !globalSettings.forceSoftwareTiming
                applyVideoSettingChange(setting, currentVSync.toString())
            }
            LibretroSettingDef.RewindSpeed -> {
                currentRewindSpeed = globalSettings.rewindSpeedDisplay
                applyVideoSettingChange(setting, currentRewindSpeed)
            }
            LibretroSettingDef.RewindBufferDuration -> {
                currentRewindBufferDuration = globalSettings.rewindBufferDurationDisplay
                applyVideoSettingChange(setting, currentRewindBufferDuration)
            }
            LibretroSettingDef.Frame -> {
                applyVideoSettingChange(setting, currentFrame ?: "None")
            }
            else -> applyVideoSettingChange(setting, globalValue)
        }
        scope.launch {
            val current = platformLibretroSettingsDao.getByPlatformId(platformId) ?: return@launch
            val updated = when (setting) {
                LibretroSettingDef.Shader -> current.copy(shader = null, shaderChain = null)
                LibretroSettingDef.Filter -> current.copy(filter = null)
                LibretroSettingDef.AspectRatio -> current.copy(aspectRatio = null)
                LibretroSettingDef.PortraitPosition -> current.copy(portraitPosition = null)
                LibretroSettingDef.Rotation -> current.copy(rotation = null)
                LibretroSettingDef.OverscanCrop -> current.copy(overscanCrop = null)
                LibretroSettingDef.Frame -> current.copy(frame = null)
                LibretroSettingDef.BlackFrameInsertion -> current.copy(blackFrameInsertion = null)
                LibretroSettingDef.FastForwardEnabled -> current.copy(fastForwardEnabled = null)
                LibretroSettingDef.FastForwardSpeed -> current.copy(fastForwardSpeed = null)
                LibretroSettingDef.RewindEnabled -> current.copy(rewindEnabled = null)
                LibretroSettingDef.SkipDuplicateFrames -> current.copy(skipDuplicateFrames = null)
                LibretroSettingDef.LowLatencyAudio -> current.copy(lowLatencyAudio = null)
                LibretroSettingDef.AudioVolume -> current.copy(audioVolume = null)
                LibretroSettingDef.AudioLatency -> current.copy(audioLatencyMs = null)
                LibretroSettingDef.VSync -> current.copy(vsync = null)
                LibretroSettingDef.RewindSpeed -> current.copy(rewindSpeed = null)
                LibretroSettingDef.RewindBufferDuration -> current.copy(rewindBufferDuration = null)
                LibretroSettingDef.AutoSaveState,
                LibretroSettingDef.AutoRestoreState,
                LibretroSettingDef.PreferNewerServerSave,
                LibretroSettingDef.HwCoreSaveStates -> current
            }
            if (updated.hasAnyOverrides()) {
                platformLibretroSettingsDao.upsert(updated)
            } else {
                platformLibretroSettingsDao.deleteByPlatformId(platformId)
            }
        }

    }

    fun cycleVideoSetting(setting: LibretroSettingDef, direction: Int) {
        val type = setting.type as? LibretroSettingDef.SettingType.Cycle ?: return
        val newValue = type.next(getVideoSettingValue(setting), direction)

        when (setting) {
            LibretroSettingDef.Shader -> currentShader = newValue
            LibretroSettingDef.Filter -> currentFilter = newValue
            LibretroSettingDef.AspectRatio -> currentAspectRatio = newValue
            LibretroSettingDef.PortraitPosition -> currentPortraitPosition = newValue
            LibretroSettingDef.Rotation -> currentRotation = newValue
            LibretroSettingDef.OverscanCrop -> currentOverscanCrop = newValue
            LibretroSettingDef.FastForwardEnabled -> currentFastForwardEnabled = newValue.toBooleanStrictOrNull() ?: true
            LibretroSettingDef.FastForwardSpeed -> currentFastForwardSpeed = newValue
            LibretroSettingDef.AudioVolume -> currentAudioVolume = newValue
            LibretroSettingDef.AudioLatency -> currentAudioLatency = newValue
            LibretroSettingDef.RewindSpeed -> currentRewindSpeed = newValue
            LibretroSettingDef.RewindBufferDuration -> currentRewindBufferDuration = newValue
            else -> {}
        }

        applyVideoSettingChange(setting, newValue)

    }

    fun toggleVideoSetting(setting: LibretroSettingDef) {
        when (setting) {
            LibretroSettingDef.BlackFrameInsertion -> {
                currentBFI = !currentBFI
                applyVideoSettingChange(setting, currentBFI.toString())
            }
            LibretroSettingDef.RewindEnabled -> {
                currentRewindEnabled = !currentRewindEnabled
                applyVideoSettingChange(setting, currentRewindEnabled.toString())
            }
            LibretroSettingDef.SkipDuplicateFrames -> {
                currentSkipDupFrames = !currentSkipDupFrames
                applyVideoSettingChange(setting, currentSkipDupFrames.toString())
            }
            LibretroSettingDef.LowLatencyAudio -> {
                currentLowLatencyAudio = !currentLowLatencyAudio
                applyVideoSettingChange(setting, currentLowLatencyAudio.toString())
            }
            LibretroSettingDef.VSync -> {
                currentVSync = !currentVSync
                applyVideoSettingChange(setting, currentVSync.toString())
            }
            else -> {}
        }

    }

    fun applyVideoSettingChange(setting: LibretroSettingDef, value: String) {
        Log.d(TAG, "Video setting changed: ${setting.key} = $value")
        val retroView = getRetroView()

        when (setting) {
            LibretroSettingDef.Shader -> {
                val shaderConfig = when (value) {
                    "CRT" -> ShaderConfig.CRT
                    "LCD" -> ShaderConfig.LCD
                    "Sharp" -> ShaderConfig.Sharp
                    "CUT" -> ShaderConfig.CUT()
                    "CUT2" -> ShaderConfig.CUT2()
                    "CUT3" -> ShaderConfig.CUT3()
                    "Custom" -> {
                        if (resolvedCustomShader is ShaderConfig.Default) {
                            val settings = kotlinx.coroutines.runBlocking {
                                effectiveLibretroSettingsResolver.getEffectiveSettings(platformId, platformSlug)
                            }
                            resolvedCustomShader = shaderRegistryProvider().resolveChain(settings.shaderChainConfig)
                        }
                        resolvedCustomShader
                    }
                    else -> ShaderConfig.Default
                }
                retroView.shader = shaderConfig
            }
            LibretroSettingDef.Filter -> {
                retroView.filterMode = when (value) {
                    "Nearest" -> 0
                    "Bilinear" -> 1
                    else -> -1
                }
            }
            LibretroSettingDef.AspectRatio -> {
                aspectRatioMode = value
                applyAspectRatio()
            }
            LibretroSettingDef.PortraitPosition -> {
                onPortraitPositionChanged?.invoke(value)
            }
            LibretroSettingDef.Rotation -> {
                rotationDegrees = parseRotation(value)
                applyRotation()
            }
            LibretroSettingDef.OverscanCrop -> {
                overscanCrop = parseOverscan(value)
                applyOverscanCrop()
            }
            LibretroSettingDef.BlackFrameInsertion -> {
                retroView.blackFrameInsertion = value.toBooleanStrictOrNull() ?: false
            }
            LibretroSettingDef.FastForwardEnabled -> {
                fastForwardEnabled = value.toBooleanStrictOrNull() ?: true
            }
            LibretroSettingDef.FastForwardSpeed -> {
                val speed = value.removeSuffix("x").toIntOrNull() ?: 4
                fastForwardSpeed = speed
            }
            LibretroSettingDef.RewindEnabled -> {
                val enabled = value.toBooleanStrictOrNull() ?: false
                rewindEnabled = enabled
                onRewindToggled?.invoke(enabled)
            }
            LibretroSettingDef.Frame -> {
                applyFrame(if (value == "None") null else currentFrame)
            }
            LibretroSettingDef.RewindSpeed -> {
                onRewindConfigChanged?.invoke()
            }
            LibretroSettingDef.RewindBufferDuration -> {
                onRewindConfigChanged?.invoke()
            }
            LibretroSettingDef.AudioVolume -> {
                setAudioVolume((value.removeSuffix("%").toIntOrNull() ?: 100) / 100f)
            }
            LibretroSettingDef.SkipDuplicateFrames,
            LibretroSettingDef.LowLatencyAudio,
            LibretroSettingDef.AudioLatency,
            LibretroSettingDef.VSync -> {
            }
            LibretroSettingDef.AutoSaveState,
            LibretroSettingDef.AutoRestoreState,
            LibretroSettingDef.PreferNewerServerSave,
            LibretroSettingDef.HwCoreSaveStates -> {
            }
        }

        persistVideoSetting(setting, value)
    }

    fun applyAspectRatio() {
        if (screenWidth == 0 || screenHeight == 0) {
            Log.w(TAG, "Cannot apply aspect ratio: screen size not available")
            return
        }

        val retroView = getRetroView()
        val screenRatio = screenWidth.toFloat() / screenHeight.toFloat()

        if (aspectRatioMode == "Integer") {
            Log.d(TAG, "Enabling integer scaling")
            retroView.integerScaling = true
            retroView.aspectRatioOverride = -1f
            return
        }

        retroView.integerScaling = false
        val overrideRatio = when (aspectRatioMode) {
            "4:3" -> 4f / 3f
            "3:2" -> 3f / 2f
            "16:9" -> 16f / 9f
            "Stretch" -> screenRatio
            else -> -1f
        }

        Log.d(TAG, "Setting aspect ratio override: $overrideRatio for mode: $aspectRatioMode")
        retroView.aspectRatioOverride = overrideRatio
    }

    fun applyOverscanCrop() {
        val retroView = getRetroView()
        val platformCrop = PLATFORM_TEXTURE_CROP[platformSlug]

        if (overscanCrop == 0 && platformCrop == null) {
            retroView.textureCrop = RectF(0f, 0f, 0f, 0f)
            return
        }

        val cropPercentX = overscanCrop / 256f
        val cropPercentY = overscanCrop / 240f

        val left = cropPercentX + (platformCrop?.left ?: 0f)
        val top = cropPercentY + (platformCrop?.top ?: 0f)
        val right = cropPercentX + (platformCrop?.right ?: 0f)
        val bottom = cropPercentY + (platformCrop?.bottom ?: 0f)

        Log.d(TAG, "Applying overscan crop: ${overscanCrop}px + platform=$platformSlug -> textureCrop($left, $top, $right, $bottom)")
        retroView.textureCrop = RectF(left, top, right, bottom)
    }

    fun applyRotation() {
        Log.d(TAG, "Applying rotation: $rotationDegrees degrees")
        getRetroView().rotation = rotationDegrees
    }

    fun persistVideoSetting(setting: LibretroSettingDef, value: String) {
        scope.launch {
            val current = platformLibretroSettingsDao.getByPlatformId(platformId)
                ?: PlatformLibretroSettingsEntity(platformId = platformId)

            val updated = when (setting) {
                LibretroSettingDef.Shader -> current.copy(shader = value)
                LibretroSettingDef.Filter -> current.copy(filter = value)
                LibretroSettingDef.AspectRatio -> current.copy(aspectRatio = value)
                LibretroSettingDef.PortraitPosition -> current.copy(portraitPosition = value)
                LibretroSettingDef.Rotation -> current.copy(rotation = parseRotation(value))
                LibretroSettingDef.OverscanCrop -> current.copy(overscanCrop = parseOverscan(value))
                LibretroSettingDef.Frame ->
                    current.copy(frame = if (value == "None") FrameRegistry.NO_FRAME_ID else currentFrame)
                LibretroSettingDef.BlackFrameInsertion -> current.copy(blackFrameInsertion = value.toBooleanStrictOrNull())
                LibretroSettingDef.FastForwardEnabled -> current.copy(fastForwardEnabled = value.toBooleanStrictOrNull())
                LibretroSettingDef.FastForwardSpeed -> current.copy(fastForwardSpeed = value.removeSuffix("x").toIntOrNull())
                LibretroSettingDef.RewindEnabled -> current.copy(rewindEnabled = value.toBooleanStrictOrNull())
                LibretroSettingDef.SkipDuplicateFrames -> current.copy(skipDuplicateFrames = value.toBooleanStrictOrNull())
                LibretroSettingDef.LowLatencyAudio -> current.copy(lowLatencyAudio = value.toBooleanStrictOrNull())
                LibretroSettingDef.AudioVolume -> current.copy(audioVolume = value.removeSuffix("%").toIntOrNull())
                LibretroSettingDef.AudioLatency -> current.copy(audioLatencyMs = AudioLatencySteps.latencyMsFor(value))
                LibretroSettingDef.VSync -> current.copy(vsync = value.toBooleanStrictOrNull())
                LibretroSettingDef.RewindSpeed -> current.copy(rewindSpeed = value.removeSuffix("x").toIntOrNull())
                LibretroSettingDef.RewindBufferDuration -> current.copy(rewindBufferDuration = value.removeSuffix("s").toIntOrNull())
                LibretroSettingDef.AutoSaveState,
                LibretroSettingDef.AutoRestoreState,
                LibretroSettingDef.PreferNewerServerSave,
                LibretroSettingDef.HwCoreSaveStates -> current
            }

            if (updated.hasAnyOverrides()) {
                platformLibretroSettingsDao.upsert(updated)
            } else {
                platformLibretroSettingsDao.deleteByPlatformId(platformId)
            }
        }
    }

    fun persistControlSetting(field: String, value: Boolean) {
        scope.launch {
            val current = platformLibretroSettingsDao.getByPlatformId(platformId)
                ?: PlatformLibretroSettingsEntity(platformId = platformId)
            val updated = when (field) {
                "analogAsDpad" -> current.copy(analogAsDpad = value)
                "dpadAsAnalog" -> current.copy(dpadAsAnalog = value)
                "rumbleEnabled" -> current.copy(rumbleEnabled = value)
                "motionEnabled" -> current.copy(motionEnabled = value)
                else -> return@launch
            }
            if (updated.hasAnyOverrides()) {
                platformLibretroSettingsDao.upsert(updated)
            } else {
                platformLibretroSettingsDao.deleteByPlatformId(platformId)
            }
        }
    }

    fun persistShaderChain(json: String) {
        scope.launch {
            val current = platformLibretroSettingsDao.getByPlatformId(platformId)
                ?: PlatformLibretroSettingsEntity(platformId = platformId)
            val updated = current.copy(shaderChain = json)
            if (updated.hasAnyOverrides()) {
                platformLibretroSettingsDao.upsert(updated)
            } else {
                platformLibretroSettingsDao.deleteByPlatformId(platformId)
            }
        }
    }

    fun persistFrame(frameId: String?) {
        scope.launch {
            val current = platformLibretroSettingsDao.getByPlatformId(platformId)
                ?: PlatformLibretroSettingsEntity(platformId = platformId)
            val updated = current.copy(frame = frameId ?: FrameRegistry.NO_FRAME_ID)
            if (updated.hasAnyOverrides()) {
                platformLibretroSettingsDao.upsert(updated)
            } else {
                platformLibretroSettingsDao.deleteByPlatformId(platformId)
            }
        }
    }

    companion object {
        private const val TAG = "VideoSettingsManager"
        private val FULL_SURFACE = FrameRegistry.ScreenRect(0f, 0f, 1f, 1f)

        private val PLATFORM_TEXTURE_CROP = mapOf(
            // 3DO opera core emits ~16 VBI lines as black at the top of the 240-line frame.
            // 25 rows was clipping a sliver of real game content on some titles.
            "3do" to RectF(0f, 16f / 240f, 0f, 0f)
        )

        fun parseRotation(value: String): Int = when (value) {
            "Auto" -> -1
            "0\u00B0" -> 0
            "90\u00B0" -> 90
            "180\u00B0" -> 180
            "270\u00B0" -> 270
            else -> value.removeSuffix("\u00B0").toIntOrNull() ?: -1
        }

        fun parseOverscan(value: String): Int = when (value) {
            "Off" -> 0
            else -> value.removeSuffix("px").toIntOrNull() ?: 0
        }
    }
}
