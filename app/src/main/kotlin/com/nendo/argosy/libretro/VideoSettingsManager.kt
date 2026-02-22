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
import com.nendo.argosy.ui.screens.settings.libretro.LibretroSettingDef
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
    private val getRetroView: () -> GLRetroView
) {
    var currentShader by mutableStateOf("None")
    var resolvedCustomShader: ShaderConfig = ShaderConfig.Default
    var currentFilter by mutableStateOf("Auto")
    var currentAspectRatio by mutableStateOf("Core Provided")
    var currentRotation by mutableStateOf("Auto")
    var currentOverscanCrop by mutableStateOf("Off")
    var currentBFI by mutableStateOf(false)
    var currentFastForwardSpeed by mutableStateOf("4x")
    var currentRewindEnabled by mutableStateOf(true)
    var currentSkipDupFrames by mutableStateOf(false)
    var currentLowLatencyAudio by mutableStateOf(true)
    var currentForceSoftwareTiming by mutableStateOf(false)
    var currentRumbleEnabled by mutableStateOf(true)
    var currentAnalogAsDpad by mutableStateOf(false)
    var currentDpadAsAnalog by mutableStateOf(false)
    var currentFrame by mutableStateOf<String?>(null)

    var aspectRatioMode: String = "Auto"
    var fastForwardSpeed: Int = 4
    var overscanCrop: Int = 0
    var rotationDegrees: Int = -1
    var rewindEnabled: Boolean = false

    private var screenWidth: Int = 0
    private var screenHeight: Int = 0

    var onRewindToggled: ((enabled: Boolean) -> Unit)? = null

    fun applySettings(settings: BuiltinEmulatorSettings) {
        aspectRatioMode = settings.aspectRatio
        fastForwardSpeed = settings.fastForwardSpeed
        overscanCrop = settings.overscanCrop
        rotationDegrees = settings.rotation
        rewindEnabled = settings.rewindEnabled
        currentShader = settings.shader
        currentFilter = settings.filter
        currentAspectRatio = settings.aspectRatio
        currentRotation = settings.rotationDisplay
        currentOverscanCrop = settings.overscanCropDisplay
        currentBFI = settings.blackFrameInsertion
        currentFastForwardSpeed = settings.fastForwardSpeedDisplay
        currentRewindEnabled = settings.rewindEnabled
        currentSkipDupFrames = settings.skipDuplicateFrames
        currentLowLatencyAudio = settings.lowLatencyAudio
        currentForceSoftwareTiming = settings.forceSoftwareTiming
        currentRumbleEnabled = settings.rumbleEnabled
        currentAnalogAsDpad = settings.analogAsDpad
        currentDpadAsAnalog = settings.dpadAsAnalog
        currentFrame = settings.frame
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
        LibretroSettingDef.Rotation -> currentRotation
        LibretroSettingDef.OverscanCrop -> currentOverscanCrop
        LibretroSettingDef.Frame -> currentFrame?.let {
            frameRegistry.findById(it)?.displayName
        } ?: "None"
        LibretroSettingDef.BlackFrameInsertion -> currentBFI.toString()
        LibretroSettingDef.FastForwardSpeed -> currentFastForwardSpeed
        LibretroSettingDef.RewindEnabled -> currentRewindEnabled.toString()
        LibretroSettingDef.SkipDuplicateFrames -> currentSkipDupFrames.toString()
        LibretroSettingDef.LowLatencyAudio -> currentLowLatencyAudio.toString()
        LibretroSettingDef.ForceSoftwareTiming -> currentForceSoftwareTiming.toString()
    }

    fun getGlobalVideoSettingValue(setting: LibretroSettingDef): String = when (setting) {
        LibretroSettingDef.Shader -> globalSettings.shader
        LibretroSettingDef.Filter -> globalSettings.filter
        LibretroSettingDef.AspectRatio -> globalSettings.aspectRatio
        LibretroSettingDef.Rotation -> globalSettings.rotationDisplay
        LibretroSettingDef.OverscanCrop -> globalSettings.overscanCropDisplay
        LibretroSettingDef.Frame -> getGlobalFrameForPlatform()?.let {
            frameRegistry.findById(it)?.displayName
        } ?: "None"
        LibretroSettingDef.BlackFrameInsertion -> globalSettings.blackFrameInsertion.toString()
        LibretroSettingDef.FastForwardSpeed -> globalSettings.fastForwardSpeedDisplay
        LibretroSettingDef.RewindEnabled -> globalSettings.rewindEnabled.toString()
        LibretroSettingDef.SkipDuplicateFrames -> globalSettings.skipDuplicateFrames.toString()
        LibretroSettingDef.LowLatencyAudio -> globalSettings.lowLatencyAudio.toString()
        LibretroSettingDef.ForceSoftwareTiming -> globalSettings.forceSoftwareTiming.toString()
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
            LibretroSettingDef.Rotation -> currentRotation = globalValue
            LibretroSettingDef.OverscanCrop -> currentOverscanCrop = globalValue
            LibretroSettingDef.FastForwardSpeed -> currentFastForwardSpeed = globalValue
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
            LibretroSettingDef.ForceSoftwareTiming -> {
                currentForceSoftwareTiming = globalSettings.forceSoftwareTiming
                applyVideoSettingChange(setting, currentForceSoftwareTiming.toString())
            }
            LibretroSettingDef.Frame -> {
                applyVideoSettingChange(setting, currentFrame ?: "None")
            }
            else -> applyVideoSettingChange(setting, globalValue)
        }
        scope.launch {
            val current = platformLibretroSettingsDao.getByPlatformId(platformId) ?: return@launch
            val updated = when (setting) {
                LibretroSettingDef.Shader -> current.copy(shader = null)
                LibretroSettingDef.Filter -> current.copy(filter = null)
                LibretroSettingDef.AspectRatio -> current.copy(aspectRatio = null)
                LibretroSettingDef.Rotation -> current.copy(rotation = null)
                LibretroSettingDef.OverscanCrop -> current.copy(overscanCrop = null)
                LibretroSettingDef.Frame -> current.copy(frame = null)
                LibretroSettingDef.BlackFrameInsertion -> current.copy(blackFrameInsertion = null)
                LibretroSettingDef.FastForwardSpeed -> current.copy(fastForwardSpeed = null)
                LibretroSettingDef.RewindEnabled -> current.copy(rewindEnabled = null)
                LibretroSettingDef.SkipDuplicateFrames -> current.copy(skipDuplicateFrames = null)
                LibretroSettingDef.LowLatencyAudio -> current.copy(lowLatencyAudio = null)
                LibretroSettingDef.ForceSoftwareTiming -> return@launch
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
        val options = type.options
        val current = getVideoSettingValue(setting)
        val currentIndex = options.indexOf(current).coerceAtLeast(0)
        val nextIndex = (currentIndex + direction + options.size) % options.size
        val newValue = options[nextIndex]

        when (setting) {
            LibretroSettingDef.Shader -> currentShader = newValue
            LibretroSettingDef.Filter -> currentFilter = newValue
            LibretroSettingDef.AspectRatio -> currentAspectRatio = newValue
            LibretroSettingDef.Rotation -> currentRotation = newValue
            LibretroSettingDef.OverscanCrop -> currentOverscanCrop = newValue
            LibretroSettingDef.FastForwardSpeed -> currentFastForwardSpeed = newValue
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
            LibretroSettingDef.ForceSoftwareTiming -> {
                currentForceSoftwareTiming = !currentForceSoftwareTiming
                applyVideoSettingChange(setting, currentForceSoftwareTiming.toString())
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
                val frameId = if (value == "None") null else currentFrame
                if (frameId != null) {
                    val bitmap = frameRegistry.loadFrame(frameId)
                    if (bitmap != null) {
                        retroView.setBackgroundFrame(bitmap)
                    } else {
                        retroView.clearBackgroundFrame()
                    }
                } else {
                    retroView.clearBackgroundFrame()
                }
            }
            LibretroSettingDef.SkipDuplicateFrames,
            LibretroSettingDef.LowLatencyAudio,
            LibretroSettingDef.ForceSoftwareTiming -> {
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
                LibretroSettingDef.Rotation -> current.copy(rotation = parseRotation(value))
                LibretroSettingDef.OverscanCrop -> current.copy(overscanCrop = parseOverscan(value))
                LibretroSettingDef.Frame -> current.copy(frame = if (value == "None") null else currentFrame)
                LibretroSettingDef.BlackFrameInsertion -> current.copy(blackFrameInsertion = value.toBooleanStrictOrNull())
                LibretroSettingDef.FastForwardSpeed -> current.copy(fastForwardSpeed = value.removeSuffix("x").toIntOrNull())
                LibretroSettingDef.RewindEnabled -> current.copy(rewindEnabled = value.toBooleanStrictOrNull())
                LibretroSettingDef.SkipDuplicateFrames -> current.copy(skipDuplicateFrames = value.toBooleanStrictOrNull())
                LibretroSettingDef.LowLatencyAudio -> current.copy(lowLatencyAudio = value.toBooleanStrictOrNull())
                LibretroSettingDef.ForceSoftwareTiming -> {
                    scope.launch {
                        preferencesRepository.setBuiltinForceSoftwareTiming(value.toBooleanStrictOrNull() ?: false)
                    }
                    return@launch
                }
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
            val updated = current.copy(frame = frameId)
            if (updated.hasAnyOverrides()) {
                platformLibretroSettingsDao.upsert(updated)
            } else {
                platformLibretroSettingsDao.deleteByPlatformId(platformId)
            }
        }
    }

    companion object {
        private const val TAG = "VideoSettingsManager"

        private val PLATFORM_TEXTURE_CROP = mapOf(
            "3do" to RectF(0f, 25f / 240f, 0f, 0f)
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
