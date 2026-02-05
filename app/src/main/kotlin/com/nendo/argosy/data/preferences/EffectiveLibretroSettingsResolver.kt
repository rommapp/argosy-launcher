package com.nendo.argosy.data.preferences

import com.nendo.argosy.data.local.dao.PlatformLibretroSettingsDao
import com.nendo.argosy.data.platform.PlatformWeightRegistry
import com.nendo.argosy.libretro.frame.FrameRegistry
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EffectiveLibretroSettingsResolver @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val platformLibretroSettingsDao: PlatformLibretroSettingsDao,
    private val frameRegistry: FrameRegistry
) {
    suspend fun getEffectiveSettings(
        platformId: Long,
        platformSlug: String
    ): BuiltinEmulatorSettings {
        val global = preferencesRepository.getBuiltinEmulatorSettings().first()
        val perPlatform = platformLibretroSettingsDao.getByPlatformId(platformId)

        val isHeavyPlatform = PlatformWeightRegistry.getWeight(platformSlug) == PlatformWeightRegistry.Weight.HEAVY

        val effectiveShader = perPlatform?.shader ?: global.shader
        val effectiveChainJson = perPlatform?.shaderChain ?: global.shaderChainJson

        return BuiltinEmulatorSettings(
            shader = effectiveShader,
            shaderChainJson = effectiveChainJson,
            filter = perPlatform?.filter ?: global.filter,
            aspectRatio = perPlatform?.aspectRatio ?: global.aspectRatio,
            rotation = perPlatform?.rotation ?: global.rotation,
            overscanCrop = perPlatform?.overscanCrop ?: global.overscanCrop,
            skipDuplicateFrames = perPlatform?.skipDuplicateFrames ?: global.skipDuplicateFrames,
            lowLatencyAudio = perPlatform?.lowLatencyAudio ?: global.lowLatencyAudio,
            blackFrameInsertion = perPlatform?.blackFrameInsertion ?: global.blackFrameInsertion,
            framesEnabled = global.framesEnabled,
            frame = resolveEffectiveFrame(global.framesEnabled, platformSlug, perPlatform?.frame),
            fastForwardSpeed = perPlatform?.fastForwardSpeed ?: global.fastForwardSpeed,
            rewindEnabled = if (isHeavyPlatform) false
                           else (perPlatform?.rewindEnabled ?: global.rewindEnabled),
            rumbleEnabled = perPlatform?.rumbleEnabled ?: global.rumbleEnabled,
            limitHotkeysToPlayer1 = global.limitHotkeysToPlayer1,
            analogAsDpad = perPlatform?.analogAsDpad
                ?: !PlatformWeightRegistry.hasAnalogStick(platformSlug),
            dpadAsAnalog = perPlatform?.dpadAsAnalog ?: false
        )
    }

    private fun resolveEffectiveFrame(
        framesEnabled: Boolean,
        platformSlug: String,
        platformOverride: String?
    ): String? {
        if (!framesEnabled) return null
        return when (platformOverride) {
            null -> frameRegistry.getFramesForPlatform(platformSlug).firstOrNull()?.id
            "none" -> null
            else -> platformOverride
        }
    }
}
