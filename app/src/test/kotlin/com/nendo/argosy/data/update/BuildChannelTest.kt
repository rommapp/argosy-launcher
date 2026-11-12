package com.nendo.argosy.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildChannelTest {

    @Test
    fun `a release build with a plain version is stable`() {
        assertFalse(BuildChannel.isPrerelease(isDebugBuild = false, versionName = "3.0.0"))
    }

    @Test
    fun `a release build with a prerelease suffix is a prerelease`() {
        assertTrue(BuildChannel.isPrerelease(isDebugBuild = false, versionName = "3.0.0-beta.1"))
        assertTrue(BuildChannel.isPrerelease(isDebugBuild = false, versionName = "3.1.0-rc.2"))
    }

    @Test
    fun `a debug build is a prerelease whatever its version`() {
        assertTrue(BuildChannel.isPrerelease(isDebugBuild = true, versionName = "3.0.0"))
        assertTrue(BuildChannel.isPrerelease(isDebugBuild = true, versionName = "3.0.0-beta.1"))
    }

    @Test
    fun `the running build answers through the same rule`() {
        val expected = BuildChannel.isPrerelease(
            com.nendo.argosy.BuildConfig.DEBUG,
            com.nendo.argosy.BuildConfig.VERSION_NAME
        )
        assertTrue(expected == BuildChannel.isPrereleaseBuild)
    }
}
