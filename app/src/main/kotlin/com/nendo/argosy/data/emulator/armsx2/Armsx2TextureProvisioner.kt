package com.nendo.argosy.data.emulator.armsx2

import com.nendo.argosy.data.emulator.TexturePackInstaller
import com.nendo.argosy.data.emulator.enhancements.EnhancementTarget
import com.nendo.argosy.data.emulator.enhancements.GameEnhancementProvisioner
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.data.storage.FileAccessLayer
import dagger.Binds
import dagger.Lazy
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Armsx2TextureProvisioner @Inject constructor(
    private val textureManager: Armsx2TextureManager,
    private val texturePackInstaller: Lazy<TexturePackInstaller>,
    private val fileAccessLayer: FileAccessLayer
) : GameEnhancementProvisioner {

    override suspend fun prepareForLaunch(
        game: GameEntity,
        romFile: File,
        target: EnhancementTarget
    ): Map<String, String> = withContext(Dispatchers.IO) {
        if (PlatformDefinitions.getCanonicalSlug(game.platformSlug) != PS2_SLUG) return@withContext emptyMap()
        if (!texturePackInstaller.get().hasInstalledTexturePack(game.id)) return@withContext emptyMap()
        when (target) {
            is EnhancementTarget.Builtin -> {
                if (target.coreId == BUILTIN_CORE_ID) textureManager.enableForBuiltinCore(target.systemDir)
            }
            is EnhancementTarget.External -> {
                val root = fileAccessLayer.externalStorageRoots().firstOrNull()
                if (root != null && isArmsx2App(target.emulatorPackage)) {
                    textureManager.enableForStandaloneApp(target.emulatorPackage, root, fileAccessLayer)
                }
            }
        }
        emptyMap()
    }

    private fun isArmsx2App(pkg: String): Boolean =
        pkg == ARMSX2_PACKAGE || pkg.startsWith("$ARMSX2_PACKAGE.")

    private companion object {
        const val PS2_SLUG = "ps2"
        const val BUILTIN_CORE_ID = "armsx2"
        const val ARMSX2_PACKAGE = "com.armsx2"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class Armsx2TextureModule {
    @Binds
    @IntoSet
    abstract fun bindArmsx2TextureProvisioner(impl: Armsx2TextureProvisioner): GameEnhancementProvisioner
}
