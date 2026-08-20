package com.nendo.argosy.libretro.coreoptions

import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.libretro.LibretroCoreRegistry
import com.nendo.argosy.libretro.coreoptions.manifests.BsnesManifest
import com.nendo.argosy.libretro.coreoptions.manifests.DolphinManifest
import com.nendo.argosy.libretro.coreoptions.manifests.FceummManifest
import com.nendo.argosy.libretro.coreoptions.manifests.FlycastManifest
import com.nendo.argosy.libretro.coreoptions.manifests.GambatteManifest
import com.nendo.argosy.libretro.coreoptions.manifests.HandyManifest
import com.nendo.argosy.libretro.coreoptions.manifests.MednafenLynxManifest
import com.nendo.argosy.libretro.coreoptions.manifests.MednafenNgpManifest
import com.nendo.argosy.libretro.coreoptions.manifests.MednafenPsxHwManifest
import com.nendo.argosy.libretro.coreoptions.manifests.MednafenSaturnManifest
import com.nendo.argosy.libretro.coreoptions.manifests.MednafenVbManifest
import com.nendo.argosy.libretro.coreoptions.manifests.MednafenWswanManifest
import com.nendo.argosy.libretro.coreoptions.manifests.MelondsDsManifest
import com.nendo.argosy.libretro.coreoptions.manifests.MelondsManifest
import com.nendo.argosy.libretro.coreoptions.manifests.MgbaManifest
import com.nendo.argosy.libretro.coreoptions.manifests.Mupen64PlusNextGles2Manifest
import com.nendo.argosy.libretro.coreoptions.manifests.Mupen64PlusNextGles3Manifest
import com.nendo.argosy.libretro.coreoptions.manifests.NestopiaManifest
import com.nendo.argosy.libretro.coreoptions.manifests.OperaManifest
import com.nendo.argosy.libretro.coreoptions.manifests.ParallelN64Manifest
import com.nendo.argosy.libretro.coreoptions.manifests.PcsxRearmedManifest
import com.nendo.argosy.libretro.coreoptions.manifests.PpssppManifest
import com.nendo.argosy.libretro.coreoptions.manifests.Snes9xManifest
import com.nendo.argosy.libretro.coreoptions.manifests.A5200Manifest
import com.nendo.argosy.libretro.coreoptions.manifests.AzaharManifest
import com.nendo.argosy.libretro.coreoptions.manifests.BluemxManifest
import com.nendo.argosy.libretro.coreoptions.manifests.Cap32Manifest
import com.nendo.argosy.libretro.coreoptions.manifests.DosboxPureManifest
import com.nendo.argosy.libretro.coreoptions.manifests.FbneoManifest
import com.nendo.argosy.libretro.coreoptions.manifests.FreechafManifest
import com.nendo.argosy.libretro.coreoptions.manifests.FreeintvManifest
import com.nendo.argosy.libretro.coreoptions.manifests.FuseManifest
import com.nendo.argosy.libretro.coreoptions.manifests.GearcolecoManifest
import com.nendo.argosy.libretro.coreoptions.manifests.GenesisPlusGxManifest
import com.nendo.argosy.libretro.coreoptions.manifests.GwManifest
import com.nendo.argosy.libretro.coreoptions.manifests.Mame2003PlusManifest
import com.nendo.argosy.libretro.coreoptions.manifests.MednafenPceManifest
import com.nendo.argosy.libretro.coreoptions.manifests.MednafenPcfxManifest
import com.nendo.argosy.libretro.coreoptions.manifests.MednafenSupergrafxManifest
import com.nendo.argosy.libretro.coreoptions.manifests.SameCdiManifest
import com.nendo.argosy.libretro.coreoptions.manifests.VirtualjaguarManifest
import com.nendo.argosy.libretro.coreoptions.manifests.Np2kaiManifest
import com.nendo.argosy.libretro.coreoptions.manifests.O2emManifest
import com.nendo.argosy.libretro.coreoptions.manifests.PicodriveManifest
import com.nendo.argosy.libretro.coreoptions.manifests.PokeminiManifest
import com.nendo.argosy.libretro.coreoptions.manifests.ProsystemManifest
import com.nendo.argosy.libretro.coreoptions.manifests.PuaeManifest
import com.nendo.argosy.libretro.coreoptions.manifests.StellaManifest
import com.nendo.argosy.libretro.coreoptions.manifests.VbamManifest
import com.nendo.argosy.libretro.coreoptions.manifests.VecxManifest
import com.nendo.argosy.libretro.coreoptions.manifests.ViceX64Manifest

object CoreOptionManifestRegistry {

    private val manifests: Map<String, CoreOptionManifest> = buildMap {
        put(OperaManifest.coreId, OperaManifest)
        put(FlycastManifest.coreId, FlycastManifest)
        put(FceummManifest.coreId, FceummManifest)
        put(NestopiaManifest.coreId, NestopiaManifest)
        put(Snes9xManifest.coreId, Snes9xManifest)
        put(BsnesManifest.coreId, BsnesManifest)
        put(MgbaManifest.coreId, MgbaManifest)
        put(GambatteManifest.coreId, GambatteManifest)
        put(VbamManifest.coreId, VbamManifest)
        put(MednafenPsxHwManifest.coreId, MednafenPsxHwManifest)
        put(PcsxRearmedManifest.coreId, PcsxRearmedManifest)
        put(MednafenSaturnManifest.coreId, MednafenSaturnManifest)
        put(MelondsManifest.coreId, MelondsManifest)
        put(MelondsDsManifest.coreId, MelondsDsManifest)
        put(AzaharManifest.coreId, AzaharManifest)
        put(PpssppManifest.coreId, PpssppManifest)
        put(MednafenVbManifest.coreId, MednafenVbManifest)
        put(MednafenWswanManifest.coreId, MednafenWswanManifest)
        put(MednafenNgpManifest.coreId, MednafenNgpManifest)
        put(HandyManifest.coreId, HandyManifest)
        put(MednafenLynxManifest.coreId, MednafenLynxManifest)
        put(Mupen64PlusNextGles2Manifest.coreId, Mupen64PlusNextGles2Manifest)
        put(Mupen64PlusNextGles3Manifest.coreId, Mupen64PlusNextGles3Manifest)
        put(ParallelN64Manifest.coreId, ParallelN64Manifest)
        put(DolphinManifest.coreId, DolphinManifest)
        put(VecxManifest.coreId, VecxManifest)
        put(O2emManifest.coreId, O2emManifest)
        put(ViceX64Manifest.coreId, ViceX64Manifest)
        put(PuaeManifest.coreId, PuaeManifest)
        put(DosboxPureManifest.coreId, DosboxPureManifest)
        put(FuseManifest.coreId, FuseManifest)
        put(Cap32Manifest.coreId, Cap32Manifest)
        put(FreechafManifest.coreId, FreechafManifest)
        put(PokeminiManifest.coreId, PokeminiManifest)
        put(GwManifest.coreId, GwManifest)
        put(Np2kaiManifest.coreId, Np2kaiManifest)
        put(GenesisPlusGxManifest.coreId, GenesisPlusGxManifest)
        put(PicodriveManifest.coreId, PicodriveManifest)
        put(StellaManifest.coreId, StellaManifest)
        put(MednafenPceManifest.coreId, MednafenPceManifest)
        put(MednafenSupergrafxManifest.coreId, MednafenSupergrafxManifest)
        put(MednafenPcfxManifest.coreId, MednafenPcfxManifest)
        put(SameCdiManifest.coreId, SameCdiManifest)
        put(VirtualjaguarManifest.coreId, VirtualjaguarManifest)
        put(A5200Manifest.coreId, A5200Manifest)
        put(ProsystemManifest.coreId, ProsystemManifest)
        put(BluemxManifest.coreId, BluemxManifest)
        put(GearcolecoManifest.coreId, GearcolecoManifest)
        put(FreeintvManifest.coreId, FreeintvManifest)
        put(FbneoManifest.coreId, FbneoManifest)
        put(Mame2003PlusManifest.coreId, Mame2003PlusManifest)
    }

    fun getManifest(coreId: String): CoreOptionManifest? = manifests[coreId]

    fun getManifestsForPlatform(platformSlug: String): List<CoreOptionManifest> {
        val canonical = PlatformDefinitions.getCanonicalSlug(platformSlug)
        return LibretroCoreRegistry.getCoresForPlatform(canonical).mapNotNull { core ->
            manifests[core.coreId]
        }
    }

    fun hasManifest(coreId: String): Boolean = coreId in manifests
}
