package com.nendo.argosy.data.emulator

import com.nendo.argosy.libchdr.ChdReader as NativeChdReader
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.iso9660ExtractPspSerial
import com.nendo.argosy.util.iso9660ExtractSerial
import java.io.File

object ChdReader {
    private const val TAG = "ChdReader"

    private val PS2_BOOT_PATTERN =
        Regex("""BOOT2\s*=\s*cdrom0:\\?([A-Z]{4})[_.](\d{3})\.(\d{2});""")
    private val PSX_BOOT_PATTERN =
        Regex("""BOOT\s*=\s*cdrom:\\?([A-Z]{4})[_.](\d{3})\.(\d{2});""")

    fun extractPS2Serial(file: File): String? {
        return try {
            extractSerial(file, PS2_BOOT_PATTERN)
        } catch (e: Exception) {
            Logger.warn(TAG, "Failed to extract PS2 serial from CHD | file=${file.name}", e)
            null
        }
    }

    fun extractPSXSerial(file: File): String? {
        return try {
            extractSerial(file, PSX_BOOT_PATTERN)
        } catch (e: Exception) {
            Logger.warn(TAG, "Failed to extract PSX serial from CHD | file=${file.name}", e)
            null
        }
    }

    fun extractPSPSerial(file: File): String? {
        return try {
            val chd = NativeChdReader.open(file.absolutePath) ?: run {
                Logger.debug(TAG, "Failed to open CHD | file=${file.name}")
                return null
            }
            chd.use { reader ->
                val serial = iso9660ExtractPspSerial(reader::readSector)
                if (serial != null) {
                    Logger.debug(TAG, "Found PSP serial from CHD: $serial")
                } else {
                    Logger.debug(TAG, "PSP serial not found in CHD | file=${file.name}")
                }
                serial
            }
        } catch (e: Exception) {
            Logger.warn(TAG, "Failed to extract PSP serial from CHD | file=${file.name}", e)
            null
        }
    }

    private fun extractSerial(file: File, bootPattern: Regex): String? {
        val chd = NativeChdReader.open(file.absolutePath) ?: run {
            Logger.debug(TAG, "Failed to open CHD | file=${file.name}")
            return null
        }

        return chd.use { reader ->
            val serial = iso9660ExtractSerial(reader::readSector, bootPattern)
            if (serial != null) {
                Logger.debug(TAG, "Found serial from CHD: $serial")
            } else {
                Logger.debug(TAG, "Serial not found in CHD | file=${file.name}")
            }
            serial
        }
    }
}
