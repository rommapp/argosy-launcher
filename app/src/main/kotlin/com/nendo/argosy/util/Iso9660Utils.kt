package com.nendo.argosy.util

private const val SECTOR_SIZE = 2048

fun ByteArray.readLE32(offset: Int): Int {
    return (this[offset].toInt() and 0xFF) or
        ((this[offset + 1].toInt() and 0xFF) shl 8) or
        ((this[offset + 2].toInt() and 0xFF) shl 16) or
        ((this[offset + 3].toInt() and 0xFF) shl 24)
}

data class Iso9660FileLocation(val lba: Int, val length: Int)

fun iso9660FindFile(
    readSector: (Int) -> ByteArray?,
    dirLba: Int,
    dirLen: Int,
    targetName: String
): Iso9660FileLocation? {
    val sectorsToRead = ((dirLen + SECTOR_SIZE - 1) / SECTOR_SIZE).coerceAtMost(4)

    for (sectorIdx in 0 until sectorsToRead) {
        val data = readSector(dirLba + sectorIdx) ?: continue
        var pos = 0

        while (pos < SECTOR_SIZE) {
            val recordLen = data[pos].toInt() and 0xFF
            if (recordLen == 0) {
                pos = ((pos / SECTOR_SIZE) + 1) * SECTOR_SIZE
                continue
            }
            if (pos + recordLen > SECTOR_SIZE) break

            val nameLen = data[pos + 32].toInt() and 0xFF
            if (nameLen > 0 && pos + 33 + nameLen <= SECTOR_SIZE) {
                val name = String(data, pos + 33, nameLen, Charsets.US_ASCII)
                if (name.equals("${targetName};1", ignoreCase = true) ||
                    name.equals(targetName, ignoreCase = true)
                ) {
                    return Iso9660FileLocation(
                        lba = data.readLE32(pos + 2),
                        length = data.readLE32(pos + 10)
                    )
                }
            }
            pos += recordLen
        }
    }
    return null
}

fun iso9660ExtractSerial(
    readSector: (Int) -> ByteArray?,
    bootPattern: Regex
): String? {
    val pvd = readSector(16) ?: return null

    if (pvd[0].toInt() != 1 || String(pvd, 1, 5, Charsets.US_ASCII) != "CD001") {
        return null
    }

    val rootLba = pvd.readLE32(156 + 2)
    val rootLen = pvd.readLE32(156 + 10)

    val cnfLocation = iso9660FindFile(readSector, rootLba, rootLen, "SYSTEM.CNF")
        ?: return null

    val cnfData = readSector(cnfLocation.lba) ?: return null
    val cnfLen = cnfLocation.length.coerceAtMost(SECTOR_SIZE)
    val cnfText = String(cnfData, 0, cnfLen, Charsets.ISO_8859_1)

    val match = bootPattern.find(cnfText) ?: return null
    return "${match.groupValues[1]}-${match.groupValues[2]}${match.groupValues[3]}"
}

private val PSP_DISC_ID_PATTERN = Regex("""^([A-Z]{4})-?(\d{5})$""")

/**
 * Reads a PSP UMD disc id from `UMD_DATA.BIN` at the ISO9660 root and returns it in the
 * dashless 9-char form used by save folder names (`ULUS10064`). UMD_DATA.BIN itself encodes
 * the id with a dash (`ULUS-10064|...`), so we normalize on the way out.
 */
fun iso9660ExtractPspSerial(readSector: (Int) -> ByteArray?): String? {
    val pvd = readSector(16) ?: return null

    if (pvd[0].toInt() != 1 || String(pvd, 1, 5, Charsets.US_ASCII) != "CD001") {
        return null
    }

    val rootLba = pvd.readLE32(156 + 2)
    val rootLen = pvd.readLE32(156 + 10)

    val umdLocation = iso9660FindFile(readSector, rootLba, rootLen, "UMD_DATA.BIN")
        ?: return null

    val umdData = readSector(umdLocation.lba) ?: return null
    val umdLen = umdLocation.length.coerceAtMost(SECTOR_SIZE).coerceAtLeast(10)
    val text = String(umdData, 0, umdLen, Charsets.US_ASCII)

    val candidate = text.substringBefore('|').trim()
    val match = PSP_DISC_ID_PATTERN.matchEntire(candidate) ?: return null
    return "${match.groupValues[1]}${match.groupValues[2]}"
}
