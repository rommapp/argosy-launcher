package com.nendo.argosy.domain.usecase.emulator

import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.emulator.EmulatorSettingScope
import com.nendo.argosy.data.emulator.SavePathConfig
import com.nendo.argosy.data.emulator.SavePathRegistry
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.data.repository.EmulatorSaveConfigRepository
import com.nendo.argosy.data.sync.platform.MemcardInfo
import com.nendo.argosy.data.sync.platform.PlatformSaveHandlerRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Which emulator a game will actually launch in, and the save facts that follow from it.
 *
 * Both game-detail surfaces need this and each had worked it out for itself, which is how the
 * companion screen came to resolve the emulator a third way: it read the configured package
 * directly, so a configured-but-uninstalled emulator produced save paths and memory cards for
 * something that could never run. Resolution now happens once, through [EmulatorResolver] -- the
 * same answer the launcher uses.
 */
class ResolveGameEmulatorContextUseCase @Inject constructor(
    private val emulatorResolver: EmulatorResolver,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val emulatorSaveConfigRepository: EmulatorSaveConfigRepository,
    private val saveHandlerRegistry: PlatformSaveHandlerRegistry
) {

    /**
     * [memcardCanonicalId] is what a memory-card selection is stored against. It is the canonical
     * id rather than the raw emulator id: a card chosen under one and read under the other is a
     * selection that silently does not apply.
     */
    data class Context(
        val effectivePackage: String?,
        val effectiveEmulatorId: String?,
        val saveConfig: SavePathConfig?,
        val supportsPerGameSavePath: Boolean,
        val memcardCanonicalId: String?,
        val memcardBaseOverride: String?,
        val memcards: List<MemcardInfo>,
        val showMemcardRow: Boolean
    )

    suspend operator fun invoke(
        gameId: Long,
        platformId: Long,
        platformSlug: String
    ): Context {
        val effectivePackage =
            emulatorResolver.getEmulatorPackageForGame(gameId, platformId, platformSlug)
        val effectiveEmulatorId = effectivePackage?.let { emulatorResolver.resolveEmulatorId(it) }

        val saveConfig = effectivePackage
            ?.let { SavePathRegistry.getConfigForPlatformByPackage(it, platformSlug) }
            ?: effectiveEmulatorId?.let { SavePathRegistry.getConfigForPlatform(it, platformSlug) }

        val isPs2 = PlatformDefinitions.getCanonicalSlug(platformSlug) == "ps2"
        val memcardCanonicalId =
            if (isPs2 && effectivePackage != null && effectiveEmulatorId != null) {
                SavePathRegistry.canonicalConfigId(effectiveEmulatorId, effectivePackage)
            } else {
                null
            }
        val memcardUserConfig = memcardCanonicalId?.let {
            emulatorSaveConfigRepository.getByEmulator(it)
        }
        val memcardBaseOverride = memcardUserConfig?.takeIf { it.isUserOverride }?.savePathPattern
        val memcards = if (memcardCanonicalId != null) {
            withContext(Dispatchers.IO) {
                saveHandlerRegistry.listPs2FolderMemcardsForEmulator(
                    emulatorId = memcardCanonicalId,
                    emulatorPackage = effectivePackage,
                    basePathOverride = memcardBaseOverride
                )
            }
        } else {
            emptyList()
        }

        return Context(
            effectivePackage = effectivePackage,
            effectiveEmulatorId = effectiveEmulatorId,
            saveConfig = saveConfig,
            supportsPerGameSavePath =
                EmulatorSettingScope.showsPerGameSavePath(saveConfig, platformSlug),
            memcardCanonicalId = memcardCanonicalId,
            memcardBaseOverride = memcardBaseOverride,
            memcards = memcards,
            showMemcardRow = EmulatorSettingScope.showsMemoryCard(platformSlug, memcards.size)
        )
    }
}
