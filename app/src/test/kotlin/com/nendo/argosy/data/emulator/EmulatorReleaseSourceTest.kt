package com.nendo.argosy.data.emulator

import org.junit.Assert.assertEquals
import org.junit.Test

class EmulatorReleaseSourceTest {

    private val githubReleasesUrl = Regex("""^https://github\.com/([^/]+/[^/]+)/releases""")

    private val storeBuildsLinkedToAnotherPackage = setOf("armsx2")

    @Test
    fun `every emulator linking to GitHub releases downloads them in app`() {
        val linkOnly = EmulatorRegistry.getAll()
            .filter { it.id !in storeBuildsLinkedToAnotherPackage }
            .filter { def ->
                val repo = def.downloadUrl?.let { githubReleasesUrl.find(it) }?.groupValues?.get(1)
                repo != null && def.releaseSource != ReleaseSource.GitHub(repo)
            }
            .map { it.id }
        assertEquals(emptyList<String>(), linkOnly)
    }
}
