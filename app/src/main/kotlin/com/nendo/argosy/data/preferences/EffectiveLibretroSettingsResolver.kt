package com.nendo.argosy.data.preferences

import com.nendo.argosy.data.local.dao.PlatformLibretroSettingsDao
import com.nendo.argosy.data.platform.PlatformWeightRegistry
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EffectiveLibretroSettingsResolver @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val platformLibretroSettingsDao: PlatformLibretroSettingsDao
) {
    suspend fun getEffectiveSettings(
        platformId: Long,
        platformSlug: String
    ): BuiltinEmulatorSettings {
        val global = preferencesRepository.getBuiltinEmulatorSettings().first()
        val perPlatform = platformLibretroSettingsDao.getByPlatformId(platformId)

        val isHeavyPlatform = PlatformWeightRegistry.getWeight(platformSlug) == PlatformWeightRegistry.Weight.HEAVY

        return BuiltinEmulatorSettings(
            shader = perPlatform?.shader ?: global.shader,
            filter = perPlatform?.filter ?: global.filter,
            aspectRatio = perPlatform?.aspectRatio ?: global.aspectRatio,
            rotation = perPlatform?.rotation ?: global.rotation,
            overscanCrop = perPlatform?.overscanCrop ?: global.overscanCrop,
            skipDuplicateFrames = perPlatform?.skipDuplicateFrames ?: global.skipDuplicateFrames,
            lowLatencyAudio = perPlatform?.lowLatencyAudio ?: global.lowLatencyAudio,
            blackFrameInsertion = perPlatform?.blackFrameInsertion ?: global.blackFrameInsertion,
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
}
