package com.nendo.argosy.data.sync

import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.emulator.savepath.Pcsx2CardBase
import com.nendo.argosy.data.emulator.savepath.Pcsx2DataFolder
import com.nendo.argosy.data.repository.EmulatorSaveConfigRepository
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.platform.EmulatorSaveRoots
import com.nendo.argosy.data.sync.platform.FolderSaveHandler
import com.nendo.argosy.data.sync.platform.Ps2FolderCardSuperblock
import com.nendo.argosy.data.sync.platform.SigilSaveHandler
import com.nendo.argosy.util.Logger
import java.io.File

internal class Pcsx2LegacyCards(
    private val fal: FileAccessLayer,
    private val emulatorSaveConfigRepository: EmulatorSaveConfigRepository,
    private val saveRoots: EmulatorSaveRoots
) {

    suspend fun baseFor(config: SavePathConfig, platformSlug: String, emulatorPackage: String?): Pcsx2CardBase? {
        if (!config.usesFolderBasedSaves) return null
        val layout = SigilSaveHandler.layoutFor(config.emulatorId, platformSlug)
            ?.takeIf { it == SigilSaveHandler.PCSX2_STANDALONE_LAYOUT }
            ?: return null
        val chosen = emulatorSaveConfigRepository.resolveUserSavePath(config.emulatorId, platformSlug)
            ?.takeIf { it.isNotBlank() }
            ?.trimEnd('/')
        if (chosen != null) {
            val chosenRoot = SigilSaveHandler.rootFor(chosen, layout)
                ?: SigilSaveHandler.rootAbove(chosen, layout, fal::isDirectory)
            val active = chosenRoot?.let { root ->
                Pcsx2DataFolder.read(root, fal)?.let { Pcsx2DataFolder.cardBase(root, it).activeCard }
            }
            return Pcsx2CardBase(chosen, active)
        }
        val root = saveRoots.rootFor(config, platformSlug, layout, emulatorPackage)
            ?: return null
        return Pcsx2DataFolder.cardBase(root, Pcsx2DataFolder.read(root, fal))
    }

    fun targetCard(base: Pcsx2CardBase, selectedCard: String?, saveId: String, handler: FolderSaveHandler?): String? {
        selectedCard?.takeIf { fal.isDirectory(it) }?.let { return it }
        val active = base.activeCard ?: return handler?.constructSavePath(base.cardsDir, saveId)
        return when {
            fal.isDirectory(active) -> active.takeIf { ensureFolderCard(it) }
            fal.exists(active) -> null.also {
                Logger.warn(TAG, "[SaveSync] PS2 | slot 1 is a file card, not writing a folder card beside it | card=$active")
            }
            Pcsx2DataFolder.isFolderCardName(File(active).name) -> active.takeIf { ensureFolderCard(it) }
            else -> null.also {
                Logger.warn(TAG, "[SaveSync] PS2 | slot 1 names a file card that is not on disk, not creating one | card=$active")
            }
        }
    }

    fun holds(base: Pcsx2CardBase, selectedCard: String?, path: String): Boolean {
        val target = path.trimEnd('/')
        return listOfNotNull(base.cardsDir, base.activeCard, selectedCard?.takeIf { it.isNotBlank() })
            .map { it.trimEnd('/') }
            .any { target == it || target.startsWith("$it/") }
    }

    private fun ensureFolderCard(card: String): Boolean {
        val superblock = "$card/${Ps2FolderCardSuperblock.FILE_NAME}"
        if (fal.exists(superblock)) return true
        if (!fal.isDirectory(card) && !fal.mkdirs(card)) {
            Logger.warn(TAG, "[SaveSync] PS2 | cannot create the slot 1 folder card | card=$card")
            return false
        }
        val written = fal.writeBytes(superblock, Ps2FolderCardSuperblock.formatted8Mb())
        Logger.info(TAG, "[SaveSync] PS2 | formatted the slot 1 folder card | card=$card, written=$written")
        return written
    }

    private companion object {
        const val TAG = "Pcsx2LegacyCards"
    }
}
