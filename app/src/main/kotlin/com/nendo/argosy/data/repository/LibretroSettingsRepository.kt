package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.PlatformLibretroSettingsDao
import com.nendo.argosy.data.local.entity.FastForwardMode
import com.nendo.argosy.data.local.entity.PlatformLibretroSettingsEntity
import com.nendo.argosy.data.preferences.BuiltinEmulatorPreferencesRepository
import com.nendo.argosy.data.preferences.BuiltinEmulatorSettings
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Settings UI facade over per-platform libretro overrides plus the runtime
 * emulation config preferences exposed by [BuiltinEmulatorPreferencesRepository].
 *
 * Exposes only the surface area the settings screens need for the built-in
 * libretro core. Theme, sound, and other launcher preferences remain on
 * [com.nendo.argosy.data.preferences.UserPreferencesRepository].
 */
@Singleton
class LibretroSettingsRepository @Inject constructor(
    private val platformLibretroSettingsDao: PlatformLibretroSettingsDao,
    private val builtinPrefs: BuiltinEmulatorPreferencesRepository
) {
    // Per-platform override DAO passthroughs

    suspend fun getByPlatformId(platformId: Long): PlatformLibretroSettingsEntity? =
        platformLibretroSettingsDao.getByPlatformId(platformId)

    fun observeByPlatformId(platformId: Long): Flow<PlatformLibretroSettingsEntity?> =
        platformLibretroSettingsDao.observeByPlatformId(platformId)

    fun observeAll(): Flow<List<PlatformLibretroSettingsEntity>> =
        platformLibretroSettingsDao.observeAll()

    suspend fun upsert(settings: PlatformLibretroSettingsEntity): Long =
        platformLibretroSettingsDao.upsert(settings)

    suspend fun deleteByPlatformId(platformId: Long) =
        platformLibretroSettingsDao.deleteByPlatformId(platformId)

    // Built-in emulator runtime configuration

    fun getBuiltinEmulatorSettings(): Flow<BuiltinEmulatorSettings> =
        builtinPrefs.getBuiltinEmulatorSettings()

    fun getBuiltinCoreSelections(): Flow<Map<String, String>> =
        builtinPrefs.getBuiltinCoreSelections()

    fun getArchitectureOverride(): Flow<String?> =
        builtinPrefs.getArchitectureOverride()

    suspend fun setArchitectureOverride(abi: String?) =
        builtinPrefs.setArchitectureOverride(abi)

    suspend fun setBuiltinShader(shader: String) =
        builtinPrefs.setBuiltinShader(shader)

    suspend fun setBuiltinShaderChain(chainJson: String) =
        builtinPrefs.setBuiltinShaderChain(chainJson)

    suspend fun setBuiltinFilter(filter: String) =
        builtinPrefs.setBuiltinFilter(filter)

    suspend fun setBuiltinAspectRatio(aspectRatio: String) =
        builtinPrefs.setBuiltinAspectRatio(aspectRatio)

    suspend fun setBuiltinSkipDuplicateFrames(enabled: Boolean) =
        builtinPrefs.setBuiltinSkipDuplicateFrames(enabled)

    suspend fun setBuiltinLowLatencyAudio(enabled: Boolean) =
        builtinPrefs.setBuiltinLowLatencyAudio(enabled)

    suspend fun setBuiltinForceSoftwareTiming(enabled: Boolean) =
        builtinPrefs.setBuiltinForceSoftwareTiming(enabled)

    suspend fun setBuiltinBlackFrameInsertion(enabled: Boolean) =
        builtinPrefs.setBuiltinBlackFrameInsertion(enabled)

    suspend fun setBuiltinFramesEnabled(enabled: Boolean) =
        builtinPrefs.setBuiltinFramesEnabled(enabled)

    suspend fun setBuiltinFastForwardEnabled(enabled: Boolean) =
        builtinPrefs.setBuiltinFastForwardEnabled(enabled)

    suspend fun setBuiltinFastForwardSpeed(speed: Int) =
        builtinPrefs.setBuiltinFastForwardSpeed(speed)

    suspend fun setBuiltinFastForwardMode(mode: FastForwardMode) =
        builtinPrefs.setBuiltinFastForwardMode(mode)

    suspend fun setBuiltinFastForwardPreservePitch(enabled: Boolean) =
        builtinPrefs.setBuiltinFastForwardPreservePitch(enabled)

    suspend fun setBuiltinRotation(rotation: Int) =
        builtinPrefs.setBuiltinRotation(rotation)

    suspend fun setBuiltinOverscanCrop(crop: Int) =
        builtinPrefs.setBuiltinOverscanCrop(crop)

    suspend fun setBuiltinRewindEnabled(enabled: Boolean) =
        builtinPrefs.setBuiltinRewindEnabled(enabled)

    suspend fun setBuiltinRewindSpeed(speed: Int) =
        builtinPrefs.setBuiltinRewindSpeed(speed)

    suspend fun setBuiltinRewindBufferDuration(duration: Int) =
        builtinPrefs.setBuiltinRewindBufferDuration(duration)

    suspend fun setBuiltinAutoSaveState(enabled: Boolean) =
        builtinPrefs.setBuiltinAutoSaveState(enabled)

    suspend fun setBuiltinAutoRestoreState(enabled: Boolean) =
        builtinPrefs.setBuiltinAutoRestoreState(enabled)

    suspend fun setBuiltinAutoRestoreStateMode(mode: String) =
        builtinPrefs.setBuiltinAutoRestoreStateMode(mode)

    suspend fun setBuiltinCustomSavePath(path: String?) =
        builtinPrefs.setBuiltinCustomSavePath(path)

    suspend fun setBuiltinCustomStatePath(path: String?) =
        builtinPrefs.setBuiltinCustomStatePath(path)

    suspend fun setBuiltinRumbleEnabled(enabled: Boolean) =
        builtinPrefs.setBuiltinRumbleEnabled(enabled)

    suspend fun setBuiltinLimitHotkeysToPlayer1(enabled: Boolean) =
        builtinPrefs.setBuiltinLimitHotkeysToPlayer1(enabled)

    suspend fun setBuiltinAnalogAsDpad(enabled: Boolean) =
        builtinPrefs.setBuiltinAnalogAsDpad(enabled)

    suspend fun setBuiltinDpadAsAnalog(enabled: Boolean) =
        builtinPrefs.setBuiltinDpadAsAnalog(enabled)

    suspend fun setBuiltinLibretroEnabled(enabled: Boolean) =
        builtinPrefs.setBuiltinLibretroEnabled(enabled)

    suspend fun setBuiltinCoreForPlatform(platformSlug: String, coreId: String) =
        builtinPrefs.setBuiltinCoreForPlatform(platformSlug, coreId)

    suspend fun setTouchControlsShowWhenNoGamepad(enabled: Boolean) =
        builtinPrefs.setTouchControlsShowWhenNoGamepad(enabled)
    suspend fun setTouchControlsOpacityLandscape(opacity: Float) =
        builtinPrefs.setTouchControlsOpacityLandscape(opacity)
    suspend fun setTouchControlsOpacityPortrait(opacity: Float) =
        builtinPrefs.setTouchControlsOpacityPortrait(opacity)
    suspend fun setTouchControlsSizeScale(scale: Float) =
        builtinPrefs.setTouchControlsSizeScale(scale)
    suspend fun setTouchControlsHaptic(enabled: Boolean) =
        builtinPrefs.setTouchControlsHaptic(enabled)
    suspend fun setTouchControlsFadeOnIdle(enabled: Boolean) =
        builtinPrefs.setTouchControlsFadeOnIdle(enabled)
    suspend fun setTouchControlsSwapHanded(enabled: Boolean) =
        builtinPrefs.setTouchControlsSwapHanded(enabled)
    suspend fun setTouchControlsLockOrientation(enabled: Boolean) =
        builtinPrefs.setTouchControlsLockOrientation(enabled)
    suspend fun setTouchControlsMirror180(enabled: Boolean) =
        builtinPrefs.setTouchControlsMirror180(enabled)
    suspend fun setTouchControlsColouredFaceButtons(enabled: Boolean) =
        builtinPrefs.setTouchControlsColouredFaceButtons(enabled)
    suspend fun setTouchControlsGenesis6Button(enabled: Boolean) =
        builtinPrefs.setTouchControlsGenesis6Button(enabled)
}
