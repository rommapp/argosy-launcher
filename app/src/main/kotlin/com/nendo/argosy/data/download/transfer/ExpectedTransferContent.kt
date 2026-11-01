package com.nendo.argosy.data.download.transfer

import com.nendo.argosy.data.platform.PlatformDefinitions

enum class ExpectedTransferContent {
    ROM,
    PICO8_PNG;

    companion object {
        fun forDownload(fileName: String, platformSlug: String, isMultiFileRom: Boolean): ExpectedTransferContent {
            if (isMultiFileRom || fileName.endsWith(".zip", ignoreCase = true)) return ROM
            val pico8Png = fileName.endsWith(".p8.png", ignoreCase = true) ||
                (PlatformDefinitions.getCanonicalSlug(platformSlug) == "pico8" &&
                    fileName.endsWith(".png", ignoreCase = true))
            return if (pico8Png) PICO8_PNG else ROM
        }
    }
}
