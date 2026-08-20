package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

/**
 * Cartridge-side options only. The core's CD, netlink, diagnostic and input-remap options are
 * deliberately absent: Jaguar CD is not a supported platform yet, netplay is unsupported for this
 * core, and the remap toggle fights Argosy's own input layer.
 */
object VirtualjaguarManifest : CoreOptionManifest {
    override val coreId = "virtualjaguar"
    override val options = listOf(
        CoreOptionDef(
            key = "virtualjaguar_usefastblitter",
            displayName = "Blitter",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Accurate is NEON-accelerated and most compatible; Fast trades accuracy for speed",
            valueLabels = mapOf("disabled" to "Accurate", "enabled" to "Fast")
        ),
        CoreOptionDef(
            key = "virtualjaguar_true_color",
            displayName = "True Color",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Renders gouraud shading at full precision to reduce banding in 3D games"
        ),
        CoreOptionDef(
            key = "virtualjaguar_internal_resolution",
            displayName = "Internal Resolution",
            values = listOf("1x", "2x"),
            defaultValue = "1x",
            description = "Renders at a multiple of the Jaguar's native resolution; applied on load",
            valueLabels = mapOf("1x" to "1x (native)")
        ),
        CoreOptionDef(
            key = "virtualjaguar_pertitle_defaults",
            displayName = "Per-Title Enhancement Defaults",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Applies known-safe presets for recognised games; your own changes always win"
        ),
        CoreOptionDef(
            key = "virtualjaguar_blit_memo",
            displayName = "Blit Memoization",
            values = listOf("disabled", "enabled", "verify"),
            defaultValue = "disabled",
            description = "Skips blits whose inputs are provably unchanged since an identical earlier blit",
            valueLabels = mapOf("verify" to "Verify (no speedup)")
        ),
        CoreOptionDef(
            key = "virtualjaguar_bios",
            displayName = "Cartridge BIOS",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "HLE emulates the boot services and skips the boot animation; Real runs the boot ROM",
            valueLabels = mapOf("disabled" to "HLE", "enabled" to "Real")
        ),
        CoreOptionDef(
            key = "virtualjaguar_jgd",
            displayName = "Jaguar GameDrive",
            values = listOf("auto", "disabled", "enabled"),
            defaultValue = "auto",
            description = "Emulates the JagGD flash cartridge, which GD-locked homebrew needs to boot",
            valueLabels = mapOf("auto" to "Auto (images over 6 MB)", "enabled" to "Enabled (force)")
        ),
        CoreOptionDef(
            key = "virtualjaguar_pal",
            displayName = "PAL",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Emulates a PAL Jaguar instead of NTSC"
        ),
        CoreOptionDef(
            key = "virtualjaguar_m68k_clock_scale",
            displayName = "68000 Overclock",
            values = listOf("0.5x", "1x", "1.5x", "2x", "3x"),
            defaultValue = "1x",
            description = "Runs the 68000 above its stock 13.3 MHz; can break titles that rely on stock timing",
            valueLabels = mapOf("1x" to "1x (stock)")
        ),
        CoreOptionDef(
            key = "virtualjaguar_risc_clock_scale",
            displayName = "GPU/DSP Overclock",
            values = listOf("0.5x", "1x", "1.5x", "2x"),
            defaultValue = "1x",
            description = "Runs the GPU and DSP above their stock 26.6 MHz to lift GPU-bound framerates",
            valueLabels = mapOf("1x" to "1x (stock)")
        ),
        CoreOptionDef(
            key = "virtualjaguar_dram_timing",
            displayName = "DRAM Timing",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Charges realistic memory access time so hardware-timed games pace correctly"
        ),
        CoreOptionDef(
            key = "virtualjaguar_gpu_pipeline_timing",
            displayName = "GPU Pipeline Timing",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Models real GPU instruction costs so render-paced loops stop running too fast"
        ),
        CoreOptionDef(
            key = "virtualjaguar_blitter_timing",
            displayName = "Blitter Bus Timing",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Charges the 68000 the bus time each blit really takes, as the hardware does"
        ),
    )
}
