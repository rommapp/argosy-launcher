package com.nendo.argosy.data.platform

import com.nendo.argosy.data.local.entity.PlatformEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlatformRomRootsTest {

    private val base = File("/storage/roms")

    private fun platform(
        id: Long,
        slug: String,
        fsSlug: String? = null,
        customRomPath: String? = null
    ) = PlatformEntity(
        id = id,
        slug = slug,
        fsSlug = fsSlug,
        name = slug,
        shortName = slug,
        romExtensions = "",
        customRomPath = customRomPath
    )

    @Test
    fun `the shared folder is always a root`() {
        val p = platform(1, "snes")
        assertEquals(listOf(File(base, "snes")), platformRomRoots(p, base, listOf(p)))
    }

    @Test
    fun `a custom folder takes precedence over the shared one`() {
        val p = platform(1, "snes", customRomPath = "/sd/snes")
        val roots = platformRomRoots(p, base, listOf(p))
        assertEquals(File("/sd/snes"), roots.first())
        assertTrue(roots.contains(File(base, "snes")))
    }

    @Test
    fun `the server folder name is a root when it differs from the slug`() {
        val p = platform(1, "psx", fsSlug = "ps")
        assertTrue(platformRomRoots(p, base, listOf(p)).contains(File(base, "ps")))
    }

    @Test
    fun `the server folder name is dropped when another platform owns it by slug`() {
        val famicom = platform(1, "famicom", fsSlug = "nes")
        val nes = platform(2, "nes")
        val roots = platformRomRoots(famicom, base, listOf(famicom, nes))
        assertTrue("must not reach into the nes folder", !roots.contains(File(base, "nes")))
    }

    @Test
    fun `the server folder name is dropped when another platform owns it by fs slug`() {
        val a = platform(1, "alpha", fsSlug = "shared")
        val b = platform(2, "beta", fsSlug = "shared")
        assertTrue(!platformRomRoots(a, base, listOf(a, b)).contains(File(base, "shared")))
    }

    @Test
    fun `downloads use the slug folder while server names are off`() {
        val p = platform(1, "psx", fsSlug = "ps")
        assertEquals(File(base, "psx"), platformDownloadDir(p, "psx", base, listOf(p), useFsSlug = false))
    }

    @Test
    fun `downloads use the server folder while server names are on`() {
        val p = platform(1, "psx", fsSlug = "ps")
        assertEquals(File(base, "ps"), platformDownloadDir(p, "psx", base, listOf(p), useFsSlug = true))
    }

    @Test
    fun `downloads keep the slug folder when another platform owns the server name`() {
        val famicom = platform(1, "famicom", fsSlug = "nes")
        val nes = platform(2, "nes")
        assertEquals(
            File(base, "famicom"),
            platformDownloadDir(famicom, "famicom", base, listOf(famicom, nes), useFsSlug = true)
        )
    }

    @Test
    fun `a custom folder wins over server names`() {
        val p = platform(1, "psx", fsSlug = "ps", customRomPath = "/sd/psx")
        assertEquals(File("/sd/psx"), platformDownloadDir(p, "psx", base, listOf(p), useFsSlug = true))
    }

    @Test
    fun `the folder downloads use is searched first`() {
        val p = platform(1, "psx", fsSlug = "ps")
        assertEquals(File(base, "ps"), platformRomRoots(p, base, listOf(p), useFsSlug = true).first())
        assertEquals(File(base, "psx"), platformRomRoots(p, base, listOf(p), useFsSlug = false).first())
    }

    @Test
    fun `a custom folder equal to the shared one is not listed twice`() {
        val p = platform(1, "snes", customRomPath = File(base, "snes").absolutePath)
        assertEquals(1, platformRomRoots(p, base, listOf(p)).size)
    }
}
