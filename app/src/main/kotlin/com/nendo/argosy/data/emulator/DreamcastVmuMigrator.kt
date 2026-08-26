package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.platform.DreamcastSaveHandler
import com.nendo.argosy.util.Logger
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DreamcastVmuMigrator"

/**
 * Hands an existing Dreamcast VMU to the first launch that runs with per-game cards.
 *
 * Every save written before per-game VMUs lives in one shared card under the system directory, and
 * flycast stops reading that card for port A1 the moment the setting is on, so the user's progress
 * looks erased. Flycast still reads a rom-named card at `<save dir>/<rom>.A1.bin` when the disc has
 * no per-game card yet, and writes whatever it loaded forward under the product number, so seeding
 * that name is enough for the emulator to complete the move itself.
 *
 * The shared card is copied and never moved or deleted: every other game's saves are still in it.
 * Nothing is written when a card already exists at either name, and a device with no shared card
 * has nothing to carry, which is the ordinary case for a new install.
 */
@Singleton
class DreamcastVmuMigrator @Inject constructor(
    private val fal: FileAccessLayer,
    private val dreamcastSaveHandler: DreamcastSaveHandler
) {
    fun seedLegacyCard(
        platformSlug: String,
        romFile: File,
        saveDir: File,
        systemDir: File,
        saveId: String?,
        perContentVmus: String?
    ) {
        if (PlatformDefinitions.getCanonicalSlug(platformSlug) != DREAMCAST_SLUG) return
        if (perContentVmus == null || perContentVmus !in PER_GAME_VALUES) return

        val extension = DreamcastSaveHandler.CARD_EXTENSION
        val legacyPath = "${saveDir.path}/${romFile.nameWithoutExtension}${DreamcastSaveHandler.PORT_A1_SUFFIX}.$extension"
        if (fal.exists(legacyPath)) return

        val perGamePaths = saveId
            ?.let { dreamcastSaveHandler.saveFileBaseNames(it) }
            .orEmpty()
            .map { "${saveDir.path}/$it.$extension" }
        if (perGamePaths.any { fal.exists(it) }) return

        val sharedCard = "${systemDir.path}/$SHARED_CARD_RELATIVE_PATH"
        if (!fal.exists(sharedCard) || !fal.isFile(sharedCard)) return

        fal.mkdirs(saveDir.path)
        val copied = fal.copyFile(sharedCard, legacyPath)
        Logger.info(
            TAG,
            "[SaveSync] MIGRATE | Seeded the shared VMU for the first per-game launch | " +
                "from=$sharedCard, to=$legacyPath, ok=$copied"
        )
    }

    companion object {
        const val PER_CONTENT_VMUS_KEY = "reicast_per_content_vmus"
        private const val DREAMCAST_SLUG = "dreamcast"
        private const val SHARED_CARD_RELATIVE_PATH = "dc/vmu_save_A1.bin"
        private val PER_GAME_VALUES = setOf("VMU A1", "All VMUs")
    }
}
