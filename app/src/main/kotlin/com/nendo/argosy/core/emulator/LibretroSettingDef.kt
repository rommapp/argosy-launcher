package com.nendo.argosy.core.emulator

import androidx.annotation.StringRes
import com.nendo.argosy.R

sealed class LibretroSettingDef(
    val key: String,
    val section: String,
    @StringRes val title: Int,
    @StringRes val subtitle: Int? = null,
    val type: SettingType
) {
    sealed class SettingType {
        /**
         * [options] are values, not copy. They are what gets written to DataStore and to
         * `platform_libretro_settings`, they are parsed back by the video pipeline, and the
         * shader entries are upstream `ShaderConfig` names. They must never be translated or
         * tidied. [labels] is the parallel display list, one resource id per option in the same
         * order, and is the half that is translatable.
         */
        data class Cycle(
            val options: List<String>,
            @StringRes val labels: List<Int>
        ) : SettingType() {
            @StringRes
            fun labelResFor(value: String): Int? =
                options.indexOf(value).takeIf { it >= 0 }?.let { labels.getOrNull(it) }
        }

        data object Switch : SettingType()
    }

    data object Shader : LibretroSettingDef(
        key = "shader",
        section = "shaders",
        title = R.string.settings_libretro_def_shader_title,
        type = SettingType.Cycle(
            options = listOf("None", "Sharp", "CUT", "CUT2", "CUT3", "CRT", "LCD", "Custom"),
            labels = listOf(
                R.string.settings_libretro_def_shader_option_none,
                R.string.settings_libretro_def_shader_option_sharp,
                R.string.settings_libretro_def_shader_option_cut,
                R.string.settings_libretro_def_shader_option_cut2,
                R.string.settings_libretro_def_shader_option_cut3,
                R.string.settings_libretro_def_shader_option_crt,
                R.string.settings_libretro_def_shader_option_lcd,
                R.string.settings_libretro_def_shader_option_custom
            )
        )
    )

    data object Filter : LibretroSettingDef(
        key = "filter",
        section = "shaders",
        title = R.string.settings_libretro_def_filter_title,
        type = SettingType.Cycle(
            options = listOf("Auto", "Nearest", "Bilinear"),
            labels = listOf(
                R.string.settings_libretro_def_filter_option_auto,
                R.string.settings_libretro_def_filter_option_nearest,
                R.string.settings_libretro_def_filter_option_bilinear
            )
        )
    )

    data object AspectRatio : LibretroSettingDef(
        key = "aspectRatio",
        section = "display",
        title = R.string.settings_libretro_def_aspect_ratio_title,
        type = SettingType.Cycle(
            options = listOf("Core Provided", "4:3", "3:2", "16:9", "Integer", "Stretch"),
            labels = listOf(
                R.string.settings_libretro_def_aspect_ratio_option_core_provided,
                R.string.settings_libretro_def_aspect_ratio_option_4_3,
                R.string.settings_libretro_def_aspect_ratio_option_3_2,
                R.string.settings_libretro_def_aspect_ratio_option_16_9,
                R.string.settings_libretro_def_aspect_ratio_option_integer,
                R.string.settings_libretro_def_aspect_ratio_option_stretch
            )
        )
    )

    data object PortraitPosition : LibretroSettingDef(
        key = "portraitPosition",
        section = "display",
        title = R.string.settings_libretro_def_portrait_position_title,
        subtitle = R.string.settings_libretro_def_portrait_position_subtitle,
        type = SettingType.Cycle(
            options = listOf("Auto", "Top", "Center", "Bottom"),
            labels = listOf(
                R.string.settings_libretro_def_portrait_position_option_auto,
                R.string.settings_libretro_def_portrait_position_option_top,
                R.string.settings_libretro_def_portrait_position_option_center,
                R.string.settings_libretro_def_portrait_position_option_bottom
            )
        )
    )

    data object Rotation : LibretroSettingDef(
        key = "rotation",
        section = "display",
        title = R.string.settings_libretro_def_rotation_title,
        type = SettingType.Cycle(
            options = listOf("Auto", "0°", "90°", "180°", "270°"),
            labels = listOf(
                R.string.settings_libretro_def_rotation_option_auto,
                R.string.settings_libretro_def_rotation_option_0,
                R.string.settings_libretro_def_rotation_option_90,
                R.string.settings_libretro_def_rotation_option_180,
                R.string.settings_libretro_def_rotation_option_270
            )
        )
    )

    data object OverscanCrop : LibretroSettingDef(
        key = "overscanCrop",
        section = "display",
        title = R.string.settings_libretro_def_overscan_crop_title,
        type = SettingType.Cycle(
            options = listOf("Off", "4px", "8px", "12px", "16px"),
            labels = listOf(
                R.string.settings_libretro_def_overscan_crop_option_off,
                R.string.settings_libretro_def_overscan_crop_option_4px,
                R.string.settings_libretro_def_overscan_crop_option_8px,
                R.string.settings_libretro_def_overscan_crop_option_12px,
                R.string.settings_libretro_def_overscan_crop_option_16px
            )
        )
    )

    data object Frame : LibretroSettingDef(
        key = "frame",
        section = "shaders",
        title = R.string.settings_libretro_def_frame_title,
        subtitle = R.string.settings_libretro_def_frame_subtitle,
        type = SettingType.Cycle(
            options = listOf("None"),
            labels = listOf(R.string.settings_libretro_def_frame_option_none)
        )
    )

    data object BlackFrameInsertion : LibretroSettingDef(
        key = "blackFrameInsertion",
        section = "display",
        title = R.string.settings_libretro_def_black_frame_insertion_title,
        subtitle = R.string.settings_libretro_def_black_frame_insertion_subtitle,
        type = SettingType.Switch
    )

    data object FastForwardEnabled : LibretroSettingDef(
        key = "fastForwardEnabled",
        section = "performance",
        title = R.string.settings_libretro_def_fast_forward_enabled_title,
        type = SettingType.Switch
    )

    data object FastForwardSpeed : LibretroSettingDef(
        key = "fastForwardSpeed",
        section = "performance",
        title = R.string.settings_libretro_def_fast_forward_speed_title,
        type = SettingType.Cycle(
            options = listOf("2x", "4x", "8x"),
            labels = listOf(
                R.string.settings_libretro_def_fast_forward_speed_option_2x,
                R.string.settings_libretro_def_fast_forward_speed_option_4x,
                R.string.settings_libretro_def_fast_forward_speed_option_8x
            )
        )
    )

    data object RewindEnabled : LibretroSettingDef(
        key = "rewindEnabled",
        section = "performance",
        title = R.string.settings_libretro_def_rewind_enabled_title,
        subtitle = R.string.settings_libretro_def_rewind_enabled_subtitle,
        type = SettingType.Switch
    )

    data object RewindSpeed : LibretroSettingDef(
        key = "rewindSpeed",
        section = "performance",
        title = R.string.settings_libretro_def_rewind_speed_title,
        type = SettingType.Cycle(
            options = listOf("1x", "2x", "4x"),
            labels = listOf(
                R.string.settings_libretro_def_rewind_speed_option_1x,
                R.string.settings_libretro_def_rewind_speed_option_2x,
                R.string.settings_libretro_def_rewind_speed_option_4x
            )
        )
    )

    data object RewindBufferDuration : LibretroSettingDef(
        key = "rewindBufferDuration",
        section = "performance",
        title = R.string.settings_libretro_def_rewind_buffer_duration_title,
        subtitle = R.string.settings_libretro_def_rewind_buffer_duration_subtitle,
        type = SettingType.Cycle(
            options = listOf("5s", "15s", "30s", "60s"),
            labels = listOf(
                R.string.settings_libretro_def_rewind_buffer_duration_option_5s,
                R.string.settings_libretro_def_rewind_buffer_duration_option_15s,
                R.string.settings_libretro_def_rewind_buffer_duration_option_30s,
                R.string.settings_libretro_def_rewind_buffer_duration_option_60s
            )
        )
    )

    data object SkipDuplicateFrames : LibretroSettingDef(
        key = "skipDuplicateFrames",
        section = "performance",
        title = R.string.settings_libretro_def_skip_duplicate_frames_title,
        subtitle = R.string.settings_libretro_def_skip_duplicate_frames_subtitle,
        type = SettingType.Switch
    )

    data object LowLatencyAudio : LibretroSettingDef(
        key = "lowLatencyAudio",
        section = "performance",
        title = R.string.settings_libretro_def_low_latency_audio_title,
        subtitle = R.string.settings_libretro_def_low_latency_audio_subtitle,
        type = SettingType.Switch
    )

    data object AudioVolume : LibretroSettingDef(
        key = "audioVolume",
        section = "performance",
        title = R.string.settings_libretro_def_audio_volume_title,
        subtitle = R.string.settings_libretro_def_audio_volume_subtitle,
        type = SettingType.Cycle(
            options = listOf(
                "0%", "10%", "20%", "30%", "40%", "50%", "60%", "70%", "80%", "90%", "100%",
                "110%", "120%", "130%", "140%", "150%", "160%", "170%", "180%", "190%", "200%"
            ),
            labels = listOf(
                R.string.settings_libretro_def_audio_volume_option_0,
                R.string.settings_libretro_def_audio_volume_option_10,
                R.string.settings_libretro_def_audio_volume_option_20,
                R.string.settings_libretro_def_audio_volume_option_30,
                R.string.settings_libretro_def_audio_volume_option_40,
                R.string.settings_libretro_def_audio_volume_option_50,
                R.string.settings_libretro_def_audio_volume_option_60,
                R.string.settings_libretro_def_audio_volume_option_70,
                R.string.settings_libretro_def_audio_volume_option_80,
                R.string.settings_libretro_def_audio_volume_option_90,
                R.string.settings_libretro_def_audio_volume_option_100,
                R.string.settings_libretro_def_audio_volume_option_110,
                R.string.settings_libretro_def_audio_volume_option_120,
                R.string.settings_libretro_def_audio_volume_option_130,
                R.string.settings_libretro_def_audio_volume_option_140,
                R.string.settings_libretro_def_audio_volume_option_150,
                R.string.settings_libretro_def_audio_volume_option_160,
                R.string.settings_libretro_def_audio_volume_option_170,
                R.string.settings_libretro_def_audio_volume_option_180,
                R.string.settings_libretro_def_audio_volume_option_190,
                R.string.settings_libretro_def_audio_volume_option_200
            )
        )
    )

    data object VSync : LibretroSettingDef(
        key = "vsync",
        section = "display",
        title = R.string.settings_libretro_def_vsync_title,
        subtitle = R.string.settings_libretro_def_vsync_subtitle,
        type = SettingType.Switch
    )

    data object AutoSaveState : LibretroSettingDef(
        key = "autoSaveState",
        section = "saving",
        title = R.string.settings_libretro_def_auto_save_state_title,
        subtitle = R.string.settings_libretro_def_auto_save_state_subtitle,
        type = SettingType.Switch
    )

    data object AutoRestoreState : LibretroSettingDef(
        key = "autoRestoreState",
        section = "saving",
        title = R.string.settings_libretro_def_auto_restore_state_title,
        subtitle = R.string.settings_libretro_def_auto_restore_state_subtitle,
        type = SettingType.Switch
    )

    data object HwCoreSaveStates : LibretroSettingDef(
        key = "hwCoreSaveStates",
        section = "saving",
        title = R.string.settings_libretro_def_hw_core_save_states_title,
        subtitle = R.string.settings_libretro_def_hw_core_save_states_subtitle,
        type = SettingType.Switch
    )

    companion object {
        val ALL: List<LibretroSettingDef> by lazy {
            listOf(
                Shader,
                Filter,
                Frame,
                AspectRatio,
                PortraitPosition,
                Rotation,
                OverscanCrop,
                BlackFrameInsertion,
                VSync,
                FastForwardEnabled,
                FastForwardSpeed,
                RewindEnabled,
                RewindSpeed,
                RewindBufferDuration,
                SkipDuplicateFrames,
                LowLatencyAudio,
                AudioVolume,
                AutoSaveState,
                AutoRestoreState,
                HwCoreSaveStates
            )
        }

        val SECTIONS: Map<String, Int> = mapOf(
            "shaders" to R.string.settings_libretro_def_section_shaders,
            "display" to R.string.settings_libretro_def_section_display,
            "performance" to R.string.settings_libretro_def_section_performance,
            "saving" to R.string.settings_libretro_def_section_saving
        )

        val SECTION_ORDER: List<String> = listOf("shaders", "display", "performance", "saving")
    }
}
