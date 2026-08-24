package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object ParallelN64Manifest : CoreOptionManifest {
    override val coreId = "parallel_n64"
    override val options = listOf(
        CoreOptionDef(
            key = "parallel-n64-cpucore",
            displayName = "CPU Core",
            values = listOf("cached_interpreter", "pure_interpreter", "dynamic_recompiler"),
            defaultValue = "dynamic_recompiler",
            description = "Selects the CPU emulation method balancing speed and compatibility"
        ),
        CoreOptionDef(
            key = "parallel-n64-astick-deadzone",
            displayName = "Analog Deadzone (percent)",
            values = listOf("0", "5", "10", "15", "20", "25", "30"),
            defaultValue = "15",
            description = "Sets the size of the non-responsive area around the analog stick center"
        ),
        CoreOptionDef(
            key = "parallel-n64-astick-sensitivity",
            displayName = "Analog Sensitivity (percent)",
            values = listOf(
                "50", "55", "60", "65", "70", "75", "80", "85", "90", "95", "100",
                "105", "110", "115", "120", "125", "130", "135", "140", "145", "150", "200"
            ),
            defaultValue = "100",
            description = "Adjusts how far the stick must move to reach its maximum value"
        ),
        CoreOptionDef(
            key = "parallel-n64-mouse-mode",
            displayName = "Mouse to Analog Stick",
            values = listOf("False", "True"),
            defaultValue = "False",
            description = "Uses mouse input to control the Player 1 analog stick when centered",
            valueLabels = mapOf("False" to "Disabled", "True" to "Enabled")
        ),
        CoreOptionDef(
            key = "parallel-n64-mouse-sensitivity-x",
            displayName = "Mouse Sensitivity X (percent)",
            values = listOf(
                "-500", "-400", "-300", "-250", "-200", "-175", "-150", "-125",
                "-100", "-75", "-50", "0", "50", "75", "100", "125", "150",
                "175", "200", "250", "300", "400", "500"
            ),
            defaultValue = "100",
            description = "Sets horizontal mouse sensitivity; negative values invert the axis"
        ),
        CoreOptionDef(
            key = "parallel-n64-mouse-sensitivity-y",
            displayName = "Mouse Sensitivity Y (percent)",
            values = listOf(
                "-500", "-400", "-300", "-250", "-200", "-175", "-150", "-125",
                "-100", "-75", "-50", "0", "50", "75", "100", "125", "150",
                "175", "200", "250", "300", "400", "500"
            ),
            defaultValue = "-100",
            description = "Sets vertical mouse sensitivity; negative values invert the axis"
        ),
        CoreOptionDef(
            key = "parallel-n64-mouse-left",
            displayName = "Mouse Left Click",
            values = listOf("Z", "A", "B", "L", "R", "Start", "C-Up", "C-Down", "C-Left", "C-Right", "None"),
            defaultValue = "Z",
            description = "Maps mouse left click to an N64 button"
        ),
        CoreOptionDef(
            key = "parallel-n64-mouse-right",
            displayName = "Mouse Right Click",
            values = listOf("A", "B", "Z", "L", "R", "Start", "C-Up", "C-Down", "C-Left", "C-Right", "None"),
            defaultValue = "A",
            description = "Maps mouse right click to an N64 button"
        ),
        CoreOptionDef(
            key = "parallel-n64-mouse-middle",
            displayName = "Mouse Middle Click",
            values = listOf("None", "A", "B", "Z", "L", "R", "Start", "C-Up", "C-Down", "C-Left", "C-Right"),
            defaultValue = "None",
            description = "Maps mouse middle click to an N64 button"
        ),
        CoreOptionDef(
            key = "parallel-n64-mouse-wheel-up",
            displayName = "Mouse Wheel Up",
            values = listOf("L", "R", "A", "B", "Z", "Start", "C-Up", "C-Down", "C-Left", "C-Right", "None"),
            defaultValue = "L",
            description = "Maps mouse wheel up to an N64 button"
        ),
        CoreOptionDef(
            key = "parallel-n64-mouse-wheel-down",
            displayName = "Mouse Wheel Down",
            values = listOf("R", "L", "A", "B", "Z", "Start", "C-Up", "C-Down", "C-Left", "C-Right", "None"),
            defaultValue = "R",
            description = "Maps mouse wheel down to an N64 button"
        ),
        CoreOptionDef(
            key = "parallel-n64-astick-snap-angle-active",
            displayName = "Analog Stick Snap Angle Active",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Snaps the analog stick to cardinal/diagonal angles for precision"
        ),
        CoreOptionDef(
            key = "parallel-n64-astick-snap-max-angle",
            displayName = "Analog Stick Snap Max Angle",
            values = (1..21).map { it.toString() },
            defaultValue = "15",
            description = "Sets the maximum angle from cardinal directions for snapping"
        ),
        CoreOptionDef(
            key = "parallel-n64-astick-snap-min-displacement-percent",
            displayName = "Analog Stick Snap Min Displacement Percent",
            values = listOf("0", "10", "20", "30", "40", "50", "60", "65", "70", "75", "80", "85", "90", "95"),
            defaultValue = "70",
            description = "Sets the minimum stick displacement required to activate snapping"
        ),
        CoreOptionDef(
            key = "parallel-n64-pak1",
            displayName = "Player 1 Pak",
            values = listOf("none", "memory", "rumble", "biosensor"),
            defaultValue = "none",
            description = "Selects the controller pak type inserted in Player 1's controller"
        ),
        CoreOptionDef(
            key = "parallel-n64-pak2",
            displayName = "Player 2 Pak",
            values = listOf("none", "memory", "rumble", "biosensor"),
            defaultValue = "none",
            description = "Selects the controller pak type inserted in Player 2's controller"
        ),
        CoreOptionDef(
            key = "parallel-n64-pak3",
            displayName = "Player 3 Pak",
            values = listOf("none", "memory", "rumble", "biosensor"),
            defaultValue = "none",
            description = "Selects the controller pak type inserted in Player 3's controller"
        ),
        CoreOptionDef(
            key = "parallel-n64-pak4",
            displayName = "Player 4 Pak",
            values = listOf("none", "memory", "rumble", "biosensor"),
            defaultValue = "none",
            description = "Selects the controller pak type inserted in Player 4's controller"
        ),
        CoreOptionDef(
            key = "parallel-n64-disable_expmem",
            displayName = "Enable Expansion Pak",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Enables the 4MB Expansion Pak required by some games"
        ),
        CoreOptionDef(
            key = "parallel-n64-gfxplugin-accuracy",
            displayName = "GFX Accuracy",
            values = listOf("low", "medium", "high", "veryhigh"),
            defaultValue = "medium",
            description = "Sets the rendering accuracy level trading quality for performance",
            valueLabels = mapOf("veryhigh" to "Very High")
        ),
        CoreOptionDef(
            key = "parallel-n64-parallel-rdp-synchronous",
            displayName = "(ParaLLEl-RDP) Synchronous RDP",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Runs the RDP in sync with the CPU for accurate rendering"
        ),
        CoreOptionDef(
            key = "parallel-n64-parallel-rdp-overscan",
            displayName = "(ParaLLEl-RDP) Crop overscan",
            values = listOf("0") + (2..64 step 2).map { it.toString() },
            defaultValue = "0",
            description = "Removes border pixels from the edges of the display"
        ),
        CoreOptionDef(
            key = "parallel-n64-remove-vi-borders",
            displayName = "(ParaLLEl-RDP) Remove VI borders",
            values = listOf("enabled", "disabled"),
            defaultValue = "disabled",
            description = "Removes the black borders on the left and right of the video"
        ),
        CoreOptionDef(
            key = "parallel-n64-parallel-rdp-divot-filter",
            displayName = "(ParaLLEl-RDP) VI Divot filter",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Removes single-pixel holes between polygons in the video output"
        ),
        CoreOptionDef(
            key = "parallel-n64-parallel-rdp-gamma-dither",
            displayName = "(ParaLLEl-RDP) VI Gamma dither",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Applies dithering during the gamma correction step"
        ),
        CoreOptionDef(
            key = "parallel-n64-parallel-rdp-vi-aa",
            displayName = "(ParaLLEl-RDP) VI anti-aliasing",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Applies the N64 video interface anti-aliasing filter"
        ),
        CoreOptionDef(
            key = "parallel-n64-parallel-rdp-vi-bilinear",
            displayName = "(ParaLLEl-RDP) VI bilinear",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Applies bilinear filtering during video output"
        ),
        CoreOptionDef(
            key = "parallel-n64-parallel-rdp-dither-filter",
            displayName = "(ParaLLEl-RDP) VI dither filter",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Smooths out dithering patterns in the final video output"
        ),
        CoreOptionDef(
            key = "parallel-n64-parallel-rdp-upscaling",
            displayName = "(ParaLLEl-RDP) Upscaling factor",
            values = listOf("1x", "2x", "4x", "8x"),
            defaultValue = "1x",
            description = "Increases the internal 3D rendering resolution"
        ),
        CoreOptionDef(
            key = "parallel-n64-parallel-rdp-downscaling",
            displayName = "(ParaLLEl-RDP) Downsampling factor",
            values = listOf("disable", "1/2", "1/4", "1/8"),
            defaultValue = "disable",
            description = "Reduces the output resolution for improved performance"
        ),
        CoreOptionDef(
            key = "parallel-n64-parallel-rdp-native-texture-lod",
            displayName = "(ParaLLEl-RDP) Native texture LOD",
            values = listOf("enabled", "disabled"),
            defaultValue = "disabled",
            description = "Uses native resolution for texture LOD calculations when upscaling"
        ),
        CoreOptionDef(
            key = "parallel-n64-parallel-rdp-native-tex-rect",
            displayName = "(ParaLLEl-RDP) Native resolution TEX_RECT",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Renders 2D textured rectangles at native resolution when upscaling"
        ),
        CoreOptionDef(
            key = "parallel-n64-send_allist_to_hle_rsp",
            displayName = "Send Audio Lists to HLE RSP",
            values = listOf("enabled", "disabled"),
            defaultValue = "disabled",
            description = "Routes audio processing through HLE RSP for better performance",
            valueLabels = mapOf("enabled" to "Fast (HLE)", "disabled" to "Accurate (selected RSP)")
        ),
        CoreOptionDef(
            key = "parallel-n64-gfxplugin",
            displayName = "GFX Plugin",
            values = listOf("gliden64", "glide64", "gln64", "rice", "angrylion", "parallel"),
            defaultValue = "gliden64",
            description = "Selects the graphics rendering plugin"
        ),
        CoreOptionDef(
            key = "parallel-n64-rspplugin",
            displayName = "RSP Plugin",
            values = listOf("auto", "hle", "cxd4", "parallel"),
            defaultValue = "auto",
            description = "Selects the Reality Signal Processor plugin"
        ),
        CoreOptionDef(
            key = "parallel-n64-screensize",
            displayName = "Resolution",
            values = listOf(
                "320x240", "640x480", "960x720", "1280x960", "1440x1080",
                "1600x1200", "1920x1440", "2240x1680", "2880x2160", "5760x4320"
            ),
            defaultValue = "640x480",
            description = "Selects the internal rendering resolution (requires restart)"
        ),
        CoreOptionDef(
            key = "parallel-n64-aspectratiohint",
            displayName = "Aspect Ratio",
            values = listOf("normal", "widescreen"),
            defaultValue = "normal",
            description = "Selects between normal 4:3 or widescreen aspect ratio"
        ),
        CoreOptionDef(
            key = "parallel-n64-filtering",
            displayName = "(Glide64) Texture Filtering",
            values = listOf("automatic", "N64 3-point", "bilinear", "nearest"),
            defaultValue = "automatic",
            description = "Selects the texture filtering method for 3D rendering"
        ),
        CoreOptionDef(
            key = "parallel-n64-dithering",
            displayName = "(Angrylion) Dithering",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Applies dithering patterns to simulate more colors"
        ),
        CoreOptionDef(
            key = "parallel-n64-polyoffset-factor",
            displayName = "(Glide64) Polygon Offset Factor",
            values = listOf(
                "-5.0", "-4.5", "-4.0", "-3.5", "-3.0", "-2.5", "-2.0",
                "-1.5", "-1.0", "-0.5", "0.0", "0.5", "1.0", "1.5",
                "2.0", "2.5", "3.0", "3.5", "4.0", "4.5", "5.0"
            ),
            defaultValue = "-3.0",
            description = "Adjusts the depth offset factor to fix Z-fighting artifacts"
        ),
        CoreOptionDef(
            key = "parallel-n64-polyoffset-units",
            displayName = "(Glide64) Polygon Offset Units",
            values = listOf(
                "-5.0", "-4.5", "-4.0", "-3.5", "-3.0", "-2.5", "-2.0",
                "-1.5", "-1.0", "-0.5", "0.0", "0.5", "1.0", "1.5",
                "2.0", "2.5", "3.0", "3.5", "4.0", "4.5", "5.0"
            ),
            defaultValue = "-3.0",
            description = "Adjusts the depth offset units to fix Z-fighting artifacts"
        ),
        CoreOptionDef(
            key = "parallel-n64-angrylion-vioverlay",
            displayName = "(Angrylion) VI Overlay",
            values = listOf("Filtered", "AA+Blur", "AA+Dedither", "AA only", "Unfiltered", "Depth", "Coverage"),
            defaultValue = "Filtered",
            description = "Selects the video output filter mode for Angrylion renderer"
        ),
        CoreOptionDef(
            key = "parallel-n64-angrylion-sync",
            displayName = "(Angrylion) Thread sync level",
            values = listOf("Low", "Medium", "High"),
            defaultValue = "Low",
            description = "Controls thread synchronization accuracy for Angrylion rendering"
        ),
        CoreOptionDef(
            key = "parallel-n64-angrylion-multithread",
            displayName = "(Angrylion) Multi-threading",
            values = listOf("off", "all threads") + (1..63).map { it.toString() },
            defaultValue = "all threads",
            description = "Sets the number of CPU threads used for Angrylion rendering"
        ),
        CoreOptionDef(
            key = "parallel-n64-angrylion-overscan",
            displayName = "(Angrylion) Hide overscan",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Crops overscan borders from the Angrylion renderer output"
        ),
        CoreOptionDef(
            key = "parallel-n64-virefresh",
            displayName = "VI Refresh (Overclock)",
            values = listOf("auto", "1500", "2200"),
            defaultValue = "auto",
            description = "Overclocks the video refresh rate to reduce lag in some games",
            valueLabels = mapOf("1500" to "1500 VI/s", "2200" to "2200 VI/s")
        ),
        CoreOptionDef(
            key = "parallel-n64-framerate",
            displayName = "Frame Duplication (restart)",
            values = listOf("original", "fullspeed"),
            defaultValue = "original",
            description = "Sends a duplicate frame each VI for consistent pacing",
            valueLabels = mapOf("original" to "Disabled (Performance)", "fullspeed" to "Enabled (Consistent Pacing)")
        ),
        CoreOptionDef(
            key = "parallel-n64-alt-map",
            displayName = "Independent C-button Controls",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Puts C-right, C-left, C-down and C-up on R, L, A and X permanently, instead of behind held R2. The right stick sends the C directions either way"
        ),
        CoreOptionDef(
            key = "parallel-n64-vcache-vbo",
            displayName = "(Glide64) Vertex cache VBO (restart)",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Caches vertex data in GPU memory for potential performance gains"
        ),
        CoreOptionDef(
            key = "parallel-n64-boot-device",
            displayName = "Boot Device",
            values = listOf("Default", "64DD IPL"),
            defaultValue = "Default",
            description = "Selects whether to boot from cartridge or 64DD disk drive"
        ),
        CoreOptionDef(
            key = "parallel-n64-64dd-hardware",
            displayName = "64DD Hardware",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Enables emulation of the N64 Disk Drive add-on"
        ),
        CoreOptionDef(
            key = "parallel-n64-allow-unaligned-dma",
            displayName = "Unaligned DMA Behaviour",
            values = listOf("True", "False"),
            defaultValue = "True",
            description = "Allows unaligned DMA; alignment is accurate but breaks some romhacks",
            valueLabels = mapOf("True" to "Allow Unaligned", "False" to "Force Alignment")
        ),
        CoreOptionDef(
            key = "parallel-n64-allow-large-roms",
            displayName = "Allow Large Roms",
            values = listOf("True", "False"),
            defaultValue = "True",
            description = "Enables support for ROMs larger than 64 MiB",
            valueLabels = mapOf("True" to "Yes", "False" to "No")
        ),
        CoreOptionDef(
            key = "parallel-n64-OverrideSaveType",
            displayName = "Save type for unknown ROMs",
            values = listOf("IGNORE", "EEPROM_4KB", "EEPROM_16KB", "SRAM", "FLASH_RAM", "CONTROLLER_PACK", "NONE"),
            defaultValue = "NONE",
            description = "Sets the save type used by unknown ROMs",
            valueLabels = mapOf(
                "IGNORE" to "Do not force savetype", "EEPROM_4KB" to "EEPROM (4kB)",
                "EEPROM_16KB" to "EEPROM (16kB)", "SRAM" to "SRAM", "FLASH_RAM" to "FlashRAM",
                "CONTROLLER_PACK" to "MemPak", "NONE" to "Guess"
            )
        ),
        CoreOptionDef(
            key = "parallel-n64-sdcard",
            displayName = "Emulate flashcart SD drive",
            values = listOf("disabled", "SummerCart64"),
            defaultValue = "disabled",
            description = "Emulates a flashcart SD card drive"
        ),
        CoreOptionDef(
            key = "parallel-n64-rtc-savestate",
            displayName = "Rollback system clock on savestate load",
            values = listOf("enabled", "disabled"),
            defaultValue = "disabled",
            description = "Rolls back the N64 system clock when loading a savestate",
            valueLabels = mapOf("enabled" to "Yes", "disabled" to "No")
        ),
        CoreOptionDef(
            key = "parallel-n64-gliden64-viewport-hack",
            displayName = "(GLideN64) Widescreen Hack",
            values = listOf("enabled", "disabled", "steamdeck"),
            defaultValue = "disabled",
            description = "Adjusts the viewport to allow unstretched 16:9 rendering",
            valueLabels = mapOf("steamdeck" to "Steam Deck (16:10)")
        ),
        CoreOptionDef(
            key = "parallel-n64-gliden64-EnableNativeResFactor",
            displayName = "(GLideN64) Native Resolution Factor",
            values = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8"),
            defaultValue = "0",
            description = "Renders at N times the native resolution",
            valueLabels = mapOf(
                "0" to "Disabled", "1" to "1x", "2" to "2x", "3" to "3x", "4" to "4x",
                "5" to "5x", "6" to "6x", "7" to "7x", "8" to "8x"
            )
        ),
        CoreOptionDef(
            key = "parallel-n64-gliden64-BilinearMode",
            displayName = "(GLideN64) Bilinear filtering mode",
            values = listOf("3point", "standard"),
            defaultValue = "standard",
            description = "Selects the bilinear filtering method"
        ),
        CoreOptionDef(
            key = "parallel-n64-gliden64-MultiSampling",
            displayName = "(GLideN64) MSAA level",
            values = listOf("0", "2", "4", "8", "16"),
            defaultValue = "0",
            description = "Sets the anti-aliasing level (0 disables)"
        ),
        CoreOptionDef(
            key = "parallel-n64-gliden64-FXAA",
            displayName = "(GLideN64) FXAA",
            values = listOf("0", "1"),
            defaultValue = "0",
            description = "Applies fast approximate anti-aliasing"
        ),
        CoreOptionDef(
            key = "parallel-n64-gliden64-EnableLODEmulation",
            displayName = "(GLideN64) LOD Emulation",
            values = listOf("False", "True"),
            defaultValue = "True",
            description = "Calculates per-pixel level of detail for texture mipmaps"
        ),
        CoreOptionDef(
            key = "parallel-n64-gliden64-EnableFBEmulation",
            displayName = "(GLideN64) Framebuffer Emulation",
            values = listOf("False", "True"),
            defaultValue = "True",
            description = "Emulates frame and depth buffers; disabling can reduce input lag"
        ),
        CoreOptionDef(
            key = "parallel-n64-gliden64-EnableCopyAuxToRDRAM",
            displayName = "(GLideN64) Copy auxiliary buffers to RDRAM",
            values = listOf("False", "True"),
            defaultValue = "False",
            description = "Copies auxiliary buffers to RDRAM to fix some artifacts"
        ),
        CoreOptionDef(
            key = "parallel-n64-gliden64-EnableCopyColorToRDRAM",
            displayName = "(GLideN64) Color buffer to RDRAM",
            values = listOf("Off", "Sync", "Async", "TripleBuffer"),
            defaultValue = "Async",
            description = "Controls how the color buffer is copied to RDRAM",
            valueLabels = mapOf("Async" to "DoubleBuffer")
        ),
        CoreOptionDef(
            key = "parallel-n64-gliden64-EnableCopyColorFromRDRAM",
            displayName = "(GLideN64) Color buffer from RDRAM",
            values = listOf("False", "True"),
            defaultValue = "False",
            description = "Copies the color buffer back from RDRAM for a few titles"
        ),
        CoreOptionDef(
            key = "parallel-n64-gliden64-EnableCopyDepthToRDRAM",
            displayName = "(GLideN64) Depth buffer to RDRAM",
            values = listOf("Off", "Software", "FromMem"),
            defaultValue = "Software",
            description = "Controls how the depth buffer is copied to RDRAM"
        ),
        CoreOptionDef(
            key = "parallel-n64-gliden64-BackgroundMode",
            displayName = "(GLideN64) Background Mode",
            values = listOf("Stripped", "OnePiece"),
            defaultValue = "OnePiece",
            description = "Selects the HLE background rendering mode"
        ),
        CoreOptionDef(
            key = "parallel-n64-gliden64-EnableHWLighting",
            displayName = "(GLideN64) Hardware per-pixel lighting",
            values = listOf("False", "True"),
            defaultValue = "False",
            description = "Uses per-pixel lighting instead of per-vertex lighting"
        ),
        CoreOptionDef(
            key = "parallel-n64-gliden64-CorrectTexrectCoords",
            displayName = "(GLideN64) Continuous texrect coords",
            values = listOf("Off", "Auto", "Force"),
            defaultValue = "Off",
            description = "Makes texrect coordinates continuous to avoid black lines"
        ),
        CoreOptionDef(
            key = "parallel-n64-gliden64-EnableInaccurateTextureCoordinates",
            displayName = "(GLideN64) Inaccurate texture coordinates",
            values = listOf("False", "True"),
            defaultValue = "False",
            description = "Trades accuracy for performance and texture pack compatibility"
        ),
        CoreOptionDef(
            key = "parallel-n64-gliden64-EnableNativeResTexrects",
            displayName = "(GLideN64) Native res 2D texrects",
            values = listOf("Disabled", "Unoptimized", "Optimized"),
            defaultValue = "Disabled",
            description = "Renders 2D texrects at native resolution to fix misalignment"
        ),
        CoreOptionDef(
            key = "parallel-n64-gliden64-EnableLegacyBlending",
            displayName = "(GLideN64) Less accurate blending mode",
            values = listOf("False", "True"),
            defaultValue = "True",
            description = "Faster blending without shaders; can cause glitches"
        ),
        CoreOptionDef(
            key = "parallel-n64-gliden64-EnableFragmentDepthWrite",
            displayName = "(GLideN64) GPU shader depth write",
            values = listOf("False", "True"),
            defaultValue = "False",
            description = "Enables fragment depth writing; unsupported on some mobile GPUs"
        ),
        CoreOptionDef(
            key = "parallel-n64-gliden64-EnableTextureCache",
            displayName = "(GLideN64) Cache Textures",
            values = listOf("False", "True"),
            defaultValue = "True",
            description = "Saves the texture cache to disk"
        ),
        CoreOptionDef(
            key = "parallel-n64-gliden64-EnableOverscan",
            displayName = "(GLideN64) Overscan",
            values = listOf("Disabled", "Enabled"),
            defaultValue = "Enabled",
            description = "Crops black borders from the overscan region"
        ),
        CoreOptionDef(
            key = "parallel-n64-gliden64-OverscanTop",
            displayName = "(GLideN64) Overscan Offset (Top)",
            values = (0..50).map { it.toString() },
            defaultValue = "0",
            description = "Sets the top overscan crop offset"
        ),
        CoreOptionDef(
            key = "parallel-n64-gliden64-OverscanLeft",
            displayName = "(GLideN64) Overscan Offset (Left)",
            values = (0..50).map { it.toString() },
            defaultValue = "0",
            description = "Sets the left overscan crop offset"
        ),
        CoreOptionDef(
            key = "parallel-n64-gliden64-OverscanRight",
            displayName = "(GLideN64) Overscan Offset (Right)",
            values = (0..50).map { it.toString() },
            defaultValue = "0",
            description = "Sets the right overscan crop offset"
        ),
        CoreOptionDef(
            key = "parallel-n64-gliden64-OverscanBottom",
            displayName = "(GLideN64) Overscan Offset (Bottom)",
            values = (0..50).map { it.toString() },
            defaultValue = "0",
            description = "Sets the bottom overscan crop offset"
        ),
        CoreOptionDef(
            key = "parallel-n64-gliden64-txFilterMode",
            displayName = "(GLideN64) Texture filter",
            values = listOf(
                "None", "Smooth filtering 1", "Smooth filtering 2",
                "Smooth filtering 3", "Smooth filtering 4",
                "Sharp filtering 1", "Sharp filtering 2"
            ),
            defaultValue = "None",
            description = "Selects the texture filtering mode"
        ),
        CoreOptionDef(
            key = "parallel-n64-gliden64-txEnhancementMode",
            displayName = "(GLideN64) Texture Enhancement",
            values = listOf(
                "None", "As Is", "X2", "X2SAI", "HQ2X", "HQ2XS", "LQ2X", "LQ2XS",
                "HQ4X", "2xBRZ", "3xBRZ", "4xBRZ", "5xBRZ", "6xBRZ"
            ),
            defaultValue = "None",
            description = "Selects the texture enhancement filter"
        ),
        CoreOptionDef(
            key = "parallel-n64-gliden64-txFilterIgnoreBG",
            displayName = "(GLideN64) Don't filter background textures",
            values = listOf("False", "True"),
            defaultValue = "True",
            description = "Skips filtering for background textures"
        ),
        CoreOptionDef(
            key = "parallel-n64-gliden64-txHiresEnable",
            displayName = "(GLideN64) Use High-Res textures",
            values = listOf("False", "True"),
            defaultValue = "False",
            description = "Enables high-resolution texture packs when available"
        ),
        CoreOptionDef(
            key = "parallel-n64-gliden64-txCacheCompression",
            displayName = "(GLideN64) High-Res Texture Cache Compression",
            values = listOf("False", "True"),
            defaultValue = "True",
            description = "Compresses generated texture caches"
        ),
        CoreOptionDef(
            key = "parallel-n64-gliden64-txHiresFullAlphaChannel",
            displayName = "(GLideN64) High-Res Full Alpha Channel",
            values = listOf("False", "True"),
            defaultValue = "False",
            description = "Enables full alpha channel for high-res textures"
        ),
        CoreOptionDef(
            key = "parallel-n64-gliden64-EnableHiResAltCRC",
            displayName = "(GLideN64) Alternative High-Res Checksums",
            values = listOf("False", "True"),
            defaultValue = "False",
            description = "Uses an alternative CRC method for high-res paletted textures"
        ),
        CoreOptionDef(
            key = "parallel-n64-gliden64-IniBehaviour",
            displayName = "(GLideN64) INI Behaviour",
            values = listOf("late", "early", "disabled"),
            defaultValue = "late",
            description = "Controls how per-game INI settings interact with core options",
            valueLabels = mapOf(
                "late" to "Prioritize INI over Core Options",
                "early" to "Prioritize Core Options over INI",
                "disabled" to "Disable INI"
            )
        ),
        CoreOptionDef(
            key = "parallel-n64-gliden64-LegacySm64ToolsHacks",
            displayName = "(GLideN64) Patch SM64 Editor Hacks",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Fixes compatibility with legacy SM64 Editor hacks"
        ),
        CoreOptionDef(
            key = "parallel-n64-gliden64-RemoveFBBlackBars",
            displayName = "(GLideN64) Fix VI Resolution",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Removes black bars added by framebuffer VI emulation"
        ),
    )
}
